package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.data.BuiltInEqPresets
import com.example.data.EqPresetProfile
import com.example.data.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class LoopMode {
    OFF, ALL, ONE
}

enum class VisualizerMode {
    SPECTRUM_32_BAR,
    NEON_WAVEFORM,
    PEAK_METERS
}

data class PlayerEngineState(
    val currentTrack: TrackEntity? = null,
    val queue: List<TrackEntity> = emptyList(),
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 1L,
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val loopMode: LoopMode = LoopMode.ALL,
    val isShuffle: Boolean = false,
    val isContinuousAutoPlay: Boolean = true,
    val crossfadeSeconds: Float = 3.0f,
    val isCrossfadingNow: Boolean = false,
    // 10-Band EQ State
    val eqEnabled: Boolean = true,
    val activePresetName: String = "Flat",
    val eqBandsDb: List<Float> = List(10) { 0f },
    val preampDb: Float = 0.0f,
    val bassBoostEnabled: Boolean = false,
    val bassBoostPercent: Int = 50,
    val spatialReverbEnabled: Boolean = false,
    // Visualizer State (32 bars + 64 waveform points)
    val visualizerMode: VisualizerMode = VisualizerMode.SPECTRUM_32_BAR,
    val fftBars32: List<Float> = List(32) { 0.06f },
    val peakCaps32: List<Float> = List(32) { 0.08f },
    val waveformPoints: List<Float> = List(64) { 0f },
    val leftPeakLevel: Float = 0f,
    val rightPeakLevel: Float = 0f,
    val isHardwareVisualizerActive: Boolean = false
)

class PulseAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PlayerEngineState())
    val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

    private var primaryPlayer: MediaPlayer? = null
    private var outgoingCrossfadePlayer: MediaPlayer? = null
    private var crossfadeJob: Job? = null
    private var tickerJob: Job? = null

    // Hardware AudioFX
    private var hwEqualizer: Equalizer? = null
    private var hwBassBoost: BassBoost? = null
    private var hwReverb: PresetReverb? = null
    private var hwLoudness: LoudnessEnhancer? = null
    private var hwVisualizer: Visualizer? = null

    @Volatile
    private var latestHwFftBins: FloatArray? = null

    @Volatile
    private var latestHwWaveform: FloatArray? = null

    private var autoCrossfadeTriggeredForTrackId: Long = -1L

    init {
        startTelemetryAndVisualizerLoop()
    }

    fun setQueueIfEmpty(tracks: List<TrackEntity>) {
        if (_state.value.queue.isEmpty() && tracks.isNotEmpty()) {
            _state.update {
                it.copy(
                    queue = tracks,
                    currentTrack = it.currentTrack ?: tracks.first(),
                    durationMs = (it.currentTrack ?: tracks.first()).durationMs.coerceAtLeast(1000L)
                )
            }
        } else if (tracks.isNotEmpty()) {
            val currentId = _state.value.currentTrack?.id
            val refreshedCurrent = tracks.find { it.id == currentId } ?: _state.value.currentTrack
            _state.update {
                it.copy(
                    queue = tracks,
                    currentTrack = refreshedCurrent
                )
            }
        }
    }

    fun playTrack(
        track: TrackEntity,
        customQueue: List<TrackEntity>? = null,
        autoStart: Boolean = true
    ) {
        val activeQueue = customQueue?.takeIf { it.isNotEmpty() } ?: _state.value.queue.ifEmpty { listOf(track) }
        val crossfadeSec = _state.value.crossfadeSeconds
        val wasPlaying = _state.value.isPlaying && primaryPlayer != null

        autoCrossfadeTriggeredForTrackId = -1L

        // If crossfade is enabled and a track is currently playing, hand off old player to outgoingCrossfadePlayer
        if (wasPlaying && crossfadeSec > 0.2f && autoStart) {
            crossfadeJob?.cancel()
            releaseOutgoingPlayer()
            outgoingCrossfadePlayer = primaryPlayer
            primaryPlayer = null
        } else {
            crossfadeJob?.cancel()
            releaseOutgoingPlayer()
            releasePrimaryPlayer()
        }

        val newPlayer = MediaPlayer()
        try {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            val pathOrUri = track.cachedFilePath?.takeIf { File(it).exists() } ?: track.filePathOrUri
            val localFile = File(pathOrUri)
            if (localFile.exists()) {
                newPlayer.setDataSource(localFile.absolutePath)
            } else {
                newPlayer.setDataSource(context, Uri.parse(pathOrUri))
            }
            newPlayer.prepare()
        } catch (_: Exception) {
            // Fallback to first synthesized studio file if external URI is inaccessible
            try {
                val fallbackList = StudioAudioSynthesizer.ensureStudioTracksGenerated(context)
                val fallback = fallbackList.firstOrNull()
                if (fallback != null) {
                    newPlayer.reset()
                    newPlayer.setDataSource(fallback.filePathOrUri)
                    newPlayer.prepare()
                }
            } catch (_: Exception) {
            }
        }

        val realDuration = try {
            newPlayer.duration.toLong().takeIf { it > 500L } ?: track.durationMs
        } catch (_: Exception) {
            track.durationMs
        }

        newPlayer.setOnCompletionListener {
            onTrackCompleted()
        }

        primaryPlayer = newPlayer
        attachAudioEffectsAndVisualizer(newPlayer.audioSessionId)

        _state.update {
            it.copy(
                currentTrack = track,
                queue = activeQueue,
                currentPositionMs = 0L,
                durationMs = realDuration.coerceAtLeast(1000L),
                isPlaying = autoStart
            )
        }

        if (autoStart) {
            if (outgoingCrossfadePlayer != null && crossfadeSec > 0.2f) {
                startCrossfadeTransition(crossfadeSec)
            } else {
                applyEffectiveVolume(newPlayer, 1.0f)
                try {
                    newPlayer.start()
                } catch (_: Exception) {
                }
            }
        } else {
            applyEffectiveVolume(newPlayer, 1.0f)
        }
    }

    private fun startCrossfadeTransition(durationSeconds: Float) {
        val incoming = primaryPlayer ?: return
        val outgoing = outgoingCrossfadePlayer
        try {
            applyEffectiveVolume(incoming, 0.05f)
            incoming.start()
        } catch (_: Exception) {
        }

        _state.update { it.copy(isCrossfadingNow = true) }

        crossfadeJob = scope.launch(Dispatchers.Main) {
            val steps = 24
            val stepDelayMs = ((durationSeconds * 1000f) / steps).toLong().coerceIn(25L, 400L)
            for (step in 1..steps) {
                val progress = step.toFloat() / steps.toFloat()
                // Equal-power crossfade curves
                val inGain = sin(progress * (PI / 2.0)).toFloat()
                val outGain = cos(progress * (PI / 2.0)).toFloat()
                primaryPlayer?.let { applyEffectiveVolume(it, inGain) }
                outgoing?.let { applyEffectiveVolume(it, outGain) }
                delay(stepDelayMs)
            }
            releaseOutgoingPlayer()
            primaryPlayer?.let { applyEffectiveVolume(it, 1.0f) }
            _state.update { it.copy(isCrossfadingNow = false) }
        }
    }

    fun togglePlayPause() {
        val current = _state.value.currentTrack
        if (primaryPlayer == null && current != null) {
            playTrack(current, autoStart = true)
            return
        }
        val player = primaryPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                outgoingCrossfadePlayer?.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                applyEffectiveVolume(player, 1.0f)
                player.start()
                _state.update { it.copy(isPlaying = true) }
            }
        } catch (_: Exception) {
        }
    }

    fun play() {
        if (!_state.value.isPlaying) {
            togglePlayPause()
        }
    }

    fun pause() {
        if (_state.value.isPlaying) {
            togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs)
        try {
            primaryPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    fun skipRelativeMs(deltaMs: Long) {
        seekTo(_state.value.currentPositionMs + deltaMs)
    }

    fun nextTrack(fromAutoAdvance: Boolean = false) {
        val s = _state.value
        val queue = s.queue
        if (queue.isEmpty()) return

        if (fromAutoAdvance && s.loopMode == LoopMode.ONE && s.currentTrack != null) {
            playTrack(s.currentTrack, autoStart = true)
            return
        }

        val currentIdx = queue.indexOfFirst { it.id == s.currentTrack?.id }
        val nextTrack = when {
            s.isShuffle && queue.size > 1 -> {
                val candidates = queue.filter { it.id != s.currentTrack?.id }
                candidates.randomOrNull() ?: queue.first()
            }
            currentIdx in 0 until (queue.size - 1) -> queue[currentIdx + 1]
            s.loopMode == LoopMode.ALL || !fromAutoAdvance -> queue.first()
            else -> null
        }

        if (nextTrack != null) {
            val shouldStart = if (fromAutoAdvance) s.isContinuousAutoPlay else true
            playTrack(nextTrack, autoStart = shouldStart)
        } else {
            _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
        }
    }

    fun previousTrack() {
        val s = _state.value
        if (s.currentPositionMs > 4000L) {
            seekTo(0L)
            return
        }
        val queue = s.queue
        if (queue.isEmpty()) return
        val currentIdx = queue.indexOfFirst { it.id == s.currentTrack?.id }
        val prev = if (currentIdx > 0) queue[currentIdx - 1] else queue.last()
        playTrack(prev, autoStart = true)
    }

    private fun onTrackCompleted() {
        val s = _state.value
        if (s.currentTrack != null && autoCrossfadeTriggeredForTrackId == s.currentTrack.id) {
            // Already advanced via early crossfade
            return
        }
        if (s.loopMode == LoopMode.ONE && s.currentTrack != null) {
            playTrack(s.currentTrack, autoStart = true)
        } else if (s.isContinuousAutoPlay) {
            nextTrack(fromAutoAdvance = true)
        } else {
            _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = v, isMuted = if (v > 0f) false else it.isMuted) }
        primaryPlayer?.let { applyEffectiveVolume(it, 1.0f) }
    }

    fun toggleMute() {
        _state.update { it.copy(isMuted = !it.isMuted) }
        primaryPlayer?.let { applyEffectiveVolume(it, 1.0f) }
    }

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(isMuted = muted) }
        primaryPlayer?.let { applyEffectiveVolume(it, 1.0f) }
    }

    fun cycleLoopMode() {
        val nextMode = when (_state.value.loopMode) {
            LoopMode.OFF -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
        }
        _state.update { it.copy(loopMode = nextMode) }
    }

    fun setLoopMode(mode: LoopMode) {
        _state.update { it.copy(loopMode = mode) }
    }

    fun toggleShuffle() {
        _state.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(isShuffle = enabled) }
    }

    fun toggleContinuousAutoPlay() {
        _state.update { it.copy(isContinuousAutoPlay = !it.isContinuousAutoPlay) }
    }

    fun setCrossfadeSeconds(seconds: Float) {
        _state.update { it.copy(crossfadeSeconds = seconds.coerceIn(0f, 12f)) }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        _state.update { it.copy(visualizerMode = mode) }
    }

    // --- 10-Band Graphic Equalizer & DSP Methods ---

    fun setEqEnabled(enabled: Boolean) {
        _state.update { it.copy(eqEnabled = enabled) }
        syncHardwareDspParameters()
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..9) return
        val clamped = gainDb.coerceIn(-12f, 12f)
        val updatedBands = _state.value.eqBandsDb.toMutableList().apply {
            this[bandIndex] = clamped
        }
        _state.update {
            it.copy(
                eqBandsDb = updatedBands,
                activePresetName = "Custom"
            )
        }
        syncHardwareDspParameters()
    }

    fun applyEqPreset(preset: EqPresetProfile) {
        _state.update {
            it.copy(
                activePresetName = preset.name,
                eqBandsDb = preset.bandsDb.take(10),
                preampDb = preset.preampDb,
                bassBoostEnabled = preset.bassBoostPercent > 0,
                bassBoostPercent = preset.bassBoostPercent.coerceIn(0, 100),
                spatialReverbEnabled = preset.spatialReverb
            )
        }
        syncHardwareDspParameters()
    }

    fun applyPresetByName(presetName: String): Boolean {
        val match = BuiltInEqPresets.ALL_PRESETS.firstOrNull {
            it.name.equals(presetName, ignoreCase = true) ||
                it.name.lowercase().contains(presetName.lowercase())
        } ?: return false
        applyEqPreset(match)
        return true
    }

    fun setPreampDb(preampDb: Float) {
        _state.update { it.copy(preampDb = preampDb.coerceIn(-6f, 12f)) }
        syncHardwareDspParameters()
        primaryPlayer?.let { applyEffectiveVolume(it, 1.0f) }
    }

    fun toggleBassBoost() {
        val next = !_state.value.bassBoostEnabled
        _state.update {
            it.copy(
                bassBoostEnabled = next,
                bassBoostPercent = if (next && it.bassBoostPercent == 0) 65 else it.bassBoostPercent
            )
        }
        syncHardwareDspParameters()
    }

    fun setBassBoostPercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        _state.update {
            it.copy(
                bassBoostPercent = p,
                bassBoostEnabled = p > 0
            )
        }
        syncHardwareDspParameters()
    }

    fun toggleSpatialReverb() {
        _state.update { it.copy(spatialReverbEnabled = !it.spatialReverbEnabled) }
        syncHardwareDspParameters()
    }

    private fun applyEffectiveVolume(player: MediaPlayer, crossfadeMultiplier: Float) {
        val s = _state.value
        if (s.isMuted) {
            try {
                player.setVolume(0f, 0f)
            } catch (_: Exception) {
            }
            return
        }
        // Combine master volume, preamp factor, and crossfade envelope
        val preampLinear = 10f.pow(s.preampDb / 20f).coerceIn(0.4f, 1.35f)
        val effective = (s.volume * preampLinear * crossfadeMultiplier).coerceIn(0f, 1f)
        try {
            player.setVolume(effective, effective)
        } catch (_: Exception) {
        }
    }

    private fun attachAudioEffectsAndVisualizer(audioSessionId: Int) {
        releaseAudioEffects()
        if (audioSessionId <= 0) return

        try {
            hwEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = _state.value.eqEnabled
            }
        } catch (_: Exception) {
        }

        try {
            hwBassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _state.value.bassBoostEnabled
            }
        } catch (_: Exception) {
        }

        try {
            hwReverb = PresetReverb(0, audioSessionId).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = _state.value.spatialReverbEnabled
            }
        } catch (_: Exception) {
        }

        try {
            hwLoudness = LoudnessEnhancer(audioSessionId).apply {
                enabled = _state.value.eqEnabled
            }
        } catch (_: Exception) {
        }

        syncHardwareDspParameters()

        // Attach Android Visualizer if RECORD_AUDIO permission is granted
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPerm) {
            try {
                val vis = Visualizer(audioSessionId)
                val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                vis.captureSize = captureSize
                vis.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform != null && waveform.isNotEmpty()) {
                                val out = FloatArray(64)
                                val step = (waveform.size / 64).coerceAtLeast(1)
                                for (i in 0 until 64) {
                                    val raw = (waveform[(i * step) % waveform.size].toInt() and 0xFF) - 128
                                    out[i] = (raw / 128f).coerceIn(-1f, 1f)
                                }
                                latestHwWaveform = out
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft != null && fft.size >= 64) {
                                val bins = FloatArray(32)
                                for (i in 0 until 32) {
                                    val r = fft[2 * i].toFloat()
                                    val im = fft[2 * i + 1].toFloat()
                                    val mag = (hypot(r, im) / 96f).coerceIn(0f, 1f)
                                    bins[i] = mag
                                }
                                latestHwFftBins = bins
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                vis.enabled = true
                hwVisualizer = vis
                _state.update { it.copy(isHardwareVisualizerActive = true) }
            } catch (_: Exception) {
                _state.update { it.copy(isHardwareVisualizerActive = false) }
            }
        } else {
            _state.update { it.copy(isHardwareVisualizerActive = false) }
        }
    }

    private fun syncHardwareDspParameters() {
        val s = _state.value
        try {
            hwEqualizer?.let { eq ->
                eq.enabled = s.eqEnabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (b in 0 until numBands) {
                    // Map hardware band b onto our 10-band curve
                    val mapped10Idx = ((b.toFloat() / numBands.coerceAtLeast(1)) * 9f).toInt().coerceIn(0, 9)
                    val next10Idx = (mapped10Idx + 1).coerceAtMost(9)
                    val avgDb = (s.eqBandsDb[mapped10Idx] + s.eqBandsDb[next10Idx]) / 2f
                    val targetMb = (avgDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(b.toShort(), if (s.eqEnabled) targetMb else 0)
                }
            }
        } catch (_: Exception) {
        }

        try {
            hwBassBoost?.let { bb ->
                bb.enabled = s.eqEnabled && s.bassBoostEnabled
                if (bb.strengthSupported) {
                    bb.setStrength((s.bassBoostPercent * 10).coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {
        }

        try {
            hwReverb?.let { rev ->
                rev.enabled = s.eqEnabled && s.spatialReverbEnabled
            }
        } catch (_: Exception) {
        }

        try {
            hwLoudness?.let { loud ->
                loud.enabled = s.eqEnabled
                val targetGainMb = if (s.eqEnabled) (s.preampDb * 100f).toInt().coerceIn(-600, 1200) else 0
                loud.setTargetGain(targetGainMb)
            }
        } catch (_: Exception) {
        }
    }

    private fun startTelemetryAndVisualizerLoop() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            var frameTick = 0L
            while (isActive) {
                frameTick++
                val s = _state.value
                val player = primaryPlayer
                var posMs = s.currentPositionMs
                var durMs = s.durationMs

                if (player != null && s.isPlaying) {
                    try {
                        if (player.isPlaying) {
                            posMs = player.currentPosition.toLong().coerceAtLeast(0L)
                            val d = player.duration.toLong()
                            if (d > 500L) durMs = d
                        }
                    } catch (_: Exception) {
                    }

                    // Check if early auto-crossfade should trigger
                    val cfWindowMs = (s.crossfadeSeconds * 1000f).toLong()
                    val currentTrackId = s.currentTrack?.id ?: -1L
                    if (s.isContinuousAutoPlay &&
                        cfWindowMs >= 500L &&
                        durMs > cfWindowMs + 2000L &&
                        posMs >= durMs - cfWindowMs &&
                        autoCrossfadeTriggeredForTrackId != currentTrackId
                    ) {
                        autoCrossfadeTriggeredForTrackId = currentTrackId
                        scope.launch(Dispatchers.Main) {
                            nextTrack(fromAutoAdvance = true)
                        }
                    }
                }

                // Compute 32-bar visualizer and waveform reactive to audio + 10-band EQ + BPM
                val newBars = MutableList(32) { 0.05f }
                val newPeaks = s.peakCaps32.toMutableList()
                val newWave = MutableList(64) { 0f }

                if (s.isPlaying && !s.isMuted && s.volume > 0.01f) {
                    val bpm = (s.currentTrack?.bpm ?: 120).coerceIn(60, 180)
                    val tSec = (posMs / 1000.0) + (frameTick * 0.035)
                    val beatPhase = (tSec * (bpm / 60.0)) % 1.0
                    val beatPulse = (1.0 - beatPhase).pow(2.2).toFloat()

                    val hwBins = latestHwFftBins
                    val hasHwEnergy = hwBins != null && hwBins.any { it > 0.04f }

                    for (i in 0 until 32) {
                        // Map 32 visualizer bars across the 10 EQ bands
                        val eqIdx = ((i / 31f) * 9f).toInt().coerceIn(0, 9)
                        val eqDb = if (s.eqEnabled) s.eqBandsDb[eqIdx] else 0f
                        val bassExtra = if (s.bassBoostEnabled && i < 8) (s.bassBoostPercent / 100f) * 0.28f else 0f
                        val eqFactor = (1f + (eqDb / 18f) + bassExtra).coerceIn(0.35f, 1.85f)

                        val baseSpectral = if (hasHwEnergy && hwBins != null) {
                            hwBins[i] * 0.65f
                        } else {
                            0f
                        }

                        // Harmonic spectral synthesis tied to beatPulse and frequency band
                        val freqRatio = i / 31.0
                        val subKick = if (i < 6) beatPulse * (0.72f - i * 0.07f) else 0f
                        val midHarmonic = (abs(sin(tSec * (3.2 + i * 0.45) + i * 0.6)) *
                                (0.55 - 0.25 * abs(freqRatio - 0.4))).toFloat()
                        val highHat = if (i > 20) {
                            abs(cos(tSec * 14.0 + i)).toFloat() * 0.32f
                        } else 0f

                        val combined = ((baseSpectral + subKick + midHarmonic + highHat) *
                                eqFactor * s.volume).coerceIn(0.06f, 0.98f)

                        // Smooth interpolation with previous bar height
                        val prev = s.fftBars32.getOrElse(i) { 0.06f }
                        val smoothed = prev * 0.35f + combined * 0.65f
                        newBars[i] = smoothed

                        // Peak-hold cap decay
                        val prevPeak = newPeaks.getOrElse(i) { 0.08f }
                        newPeaks[i] = if (smoothed >= prevPeak) {
                            smoothed
                        } else {
                            (prevPeak - 0.018f).coerceAtLeast(smoothed)
                        }
                    }

                    val hwWave = latestHwWaveform
                    val hasHwWave = hwWave != null && hwWave.any { abs(it) > 0.03f }
                    for (w in 0 until 64) {
                        val x = w / 63.0
                        val synthWave = (sin(2 * PI * (2.0 * x + tSec * 3.0)) * 0.45 +
                                sin(2 * PI * (5.0 * x - tSec * 5.5)) * 0.30 * beatPulse +
                                cos(2 * PI * (11.0 * x + tSec * 8.0)) * 0.18).toFloat() * s.volume
                        newWave[w] = if (hasHwWave && hwWave != null) {
                            (hwWave[w] * 0.6f + synthWave * 0.4f).coerceIn(-1f, 1f)
                        } else {
                            synthWave.coerceIn(-1f, 1f)
                        }
                    }
                } else {
                    // Gently decay bars and peaks when paused
                    for (i in 0 until 32) {
                        val prev = s.fftBars32.getOrElse(i) { 0.05f }
                        newBars[i] = (prev * 0.80f).coerceAtLeast(0.04f)
                        val prevPeak = newPeaks.getOrElse(i) { 0.06f }
                        newPeaks[i] = (prevPeak * 0.86f).coerceAtLeast(0.05f)
                    }
                }

                val leftPeak = newBars.take(16).average().toFloat().coerceIn(0f, 1f)
                val rightPeak = newBars.takeLast(16).average().toFloat().coerceIn(0f, 1f)

                _state.update {
                    it.copy(
                        currentPositionMs = posMs,
                        durationMs = durMs,
                        fftBars32 = newBars,
                        peakCaps32 = newPeaks,
                        waveformPoints = newWave,
                        leftPeakLevel = leftPeak,
                        rightPeakLevel = rightPeak
                    )
                }

                delay(45L)
            }
        }
    }

    private fun releaseOutgoingPlayer() {
        try {
            outgoingCrossfadePlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            outgoingCrossfadePlayer?.release()
        } catch (_: Exception) {
        }
        outgoingCrossfadePlayer = null
    }

    private fun releasePrimaryPlayer() {
        releaseAudioEffects()
        try {
            primaryPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            primaryPlayer?.release()
        } catch (_: Exception) {
        }
        primaryPlayer = null
    }

    private fun releaseAudioEffects() {
        try {
            hwVisualizer?.enabled = false
            hwVisualizer?.release()
        } catch (_: Exception) {
        }
        hwVisualizer = null

        try {
            hwEqualizer?.release()
        } catch (_: Exception) {
        }
        hwEqualizer = null

        try {
            hwBassBoost?.release()
        } catch (_: Exception) {
        }
        hwBassBoost = null

        try {
            hwReverb?.release()
        } catch (_: Exception) {
        }
        hwReverb = null

        try {
            hwLoudness?.release()
        } catch (_: Exception) {
        }
        hwLoudness = null
    }

    fun releaseAll() {
        tickerJob?.cancel()
        crossfadeJob?.cancel()
        releaseOutgoingPlayer()
        releasePrimaryPlayer()
    }
}
