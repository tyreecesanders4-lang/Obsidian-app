package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.BuildConfig
import com.example.audio.LoopMode
import com.example.audio.PulseAudioEngine
import com.example.data.BuiltInEqPresets
import com.example.data.MusicRepository
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiChatMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String,
    val intentBadge: String? = null,
    val engineUsed: String = "Local NLP",
    val matchedTracks: List<TrackEntity> = emptyList(),
    val createdPlaylist: PlaylistEntity? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class IntentExecutionOutcome(
    val intentBadge: String,
    val responseText: String,
    val engineUsed: String,
    val matchedTracks: List<TrackEntity> = emptyList(),
    val createdPlaylist: PlaylistEntity? = null,
    val filterQueryToApply: String? = null,
    val navigateToTab: String? = null
)

// --- Gemini 3.5 Flash REST Data Models ---
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = GeminiGenerationConfig(temperature = 0.2f)
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Float = 0.2f
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiRestService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClientHolder {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    val service: GeminiRestService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiRestService::class.java)
    }
}

class VoiceAssistantEngine(
    private val context: Context,
    private val audioEngine: PulseAudioEngine,
    private val repository: MusicRepository
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startVoiceListening(
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onRmsChanged: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            stopVoiceListening()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Hardware speech recognizer unavailable in this environment. Tap any quick voice chip or type a command below.")
                return
            }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    onRmsChanged(((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking again or tap a quick command chip."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required for live voice capture."
                        else -> "Voice capture ended (code $error). You can also tap a quick voice command below."
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        onFinalTranscript(best)
                    } else {
                        onError("Could not transcribe voice input.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        onPartialTranscript(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer = recognizer
            recognizer.startListening(intent)
        } catch (e: Exception) {
            onError("Voice recognition fallback active: ${e.localizedMessage ?: "Use quick voice commands"}")
        }
    }

    fun stopVoiceListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    /**
     * Parses and executes natural language voice/text commands using either the deterministic
     * Offline Local Intent Parser (instant, privacy-first) or enhanced with Gemini 3.5 Flash when online.
     */
    suspend fun processNaturalCommand(
        rawCommand: String,
        allTracks: List<TrackEntity>,
        offlinePrivacyMode: Boolean
    ): IntentExecutionOutcome {
        val text = rawCommand.trim()
        val lower = text.lowercase(Locale.ROOT)
        val engineLabel = if (offlinePrivacyMode || !isGeminiKeyConfigured()) {
            "Offline Local NLP"
        } else {
            "Hybrid AI + Local DSP"
        }

        // 1. Check Dynamic AI Playlist Creation intents first
        if (lower.contains("playlist") || lower.contains("make a mix") || lower.contains("build a mix") || lower.contains("create a mix")) {
            return handlePlaylistGenerationIntent(text, lower, allTracks, engineLabel)
        }

        // 2. Check Equalizer & Audio Preset intents
        if (lower.contains("equalizer") || lower.contains("eq") || lower.contains("preset") ||
            lower.contains("bass") || lower.contains("treble") || lower.contains("reverb") ||
            lower.contains("vocal")
        ) {
            return handleEqualizerIntent(lower, engineLabel)
        }

        // 3. Check Seek / Skip to timestamp intents ("Skip to 1 minute 30 seconds", "Seek to 0:45")
        val seekSec = parseSeekTimestampSeconds(lower)
        if (seekSec != null) {
            val targetMs = seekSec * 1000L
            audioEngine.seekTo(targetMs)
            val mm = seekSec / 60
            val ss = seekSec % 60
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_SEEK",
                responseText = "Skipped playback position to %d:%02d.".format(mm, ss),
                engineUsed = engineLabel
            )
        }

        // 4. Check Volume & Mute Controls ("Set volume to 80%", "Mute", "Unmute")
        if (lower.contains("volume") || lower.contains("mute") || lower.contains("louder") || lower.contains("quieter")) {
            return handleVolumeIntent(lower, engineLabel)
        }

        // 5. Check Next / Previous / Pause / Resume / Shuffle / Loop Controls
        if (lower.contains("next track") || lower.contains("next song") || lower == "next" || lower == "skip" || lower == "play next") {
            audioEngine.nextTrack()
            val nowPlaying = audioEngine.state.value.currentTrack
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_NEXT",
                responseText = "Skipped to next track: \"${nowPlaying?.title ?: "Track"}\" by ${nowPlaying?.artist ?: "Artist"}.",
                engineUsed = engineLabel
            )
        }

        if (lower.contains("previous") || lower.contains("prev track") || lower.contains("go back") || lower.contains("last song")) {
            audioEngine.previousTrack()
            val nowPlaying = audioEngine.state.value.currentTrack
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_PREVIOUS",
                responseText = "Returned to previous track: \"${nowPlaying?.title ?: "Track"}\".",
                engineUsed = engineLabel
            )
        }

        if (lower == "pause" || lower.contains("pause playback") || lower.contains("stop playing") || lower == "stop") {
            audioEngine.pause()
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_PAUSE",
                responseText = "Paused audio playback.",
                engineUsed = engineLabel
            )
        }

        if (lower == "play" || lower == "resume" || lower.contains("resume playback")) {
            audioEngine.play()
            val current = audioEngine.state.value.currentTrack
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_PLAY",
                responseText = "Resumed playing \"${current?.title ?: "Active Track"}\".",
                engineUsed = engineLabel
            )
        }

        if (lower.contains("shuffle")) {
            val enable = !lower.contains("off") && !lower.contains("disable")
            audioEngine.setShuffle(enable)
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_SHUFFLE",
                responseText = if (enable) "Shuffle mode enabled across your queue." else "Shuffle mode disabled.",
                engineUsed = engineLabel
            )
        }

        if (lower.contains("loop")) {
            return when {
                lower.contains("one") || lower.contains("single") -> {
                    audioEngine.setLoopMode(LoopMode.ONE)
                    IntentExecutionOutcome("PLAYBACK_LOOP_ONE", "Loop One enabled — repeating current track.", engineLabel)
                }
                lower.contains("off") || lower.contains("disable") -> {
                    audioEngine.setLoopMode(LoopMode.OFF)
                    IntentExecutionOutcome("PLAYBACK_LOOP_OFF", "Loop mode turned off.", engineLabel)
                }
                else -> {
                    audioEngine.setLoopMode(LoopMode.ALL)
                    IntentExecutionOutcome("PLAYBACK_LOOP_ALL", "Loop All enabled for the current queue.", engineLabel)
                }
            }
        }

        // 6. Natural Search & Smart Filtering ("Find tracks with high BPM", "Play something acoustic", "Search songs by [Artist]")
        val discoveryOutcome = handleNaturalDiscoveryIntent(text, lower, allTracks, engineLabel)
        if (discoveryOutcome != null) {
            return discoveryOutcome
        }

        // 7. If Cloud Gemini is enabled & key is configured, query Gemini 3.5 Flash for conversational music assistant reply
        if (!offlinePrivacyMode && isGeminiKeyConfigured()) {
            val geminiReply = queryGeminiForMusicIntent(text, allTracks)
            if (geminiReply != null) {
                return IntentExecutionOutcome(
                    intentBadge = "AI_CONVERSATIONAL_DISCOVERY",
                    responseText = geminiReply,
                    engineUsed = "Gemini 3.5 Flash"
                )
            }
        }

        // Fallback helpful response matching library
        val defaultMatches = allTracks.filter {
            it.title.lowercase().contains(lower) ||
                it.artist.lowercase().contains(lower) ||
                it.genre.lowercase().contains(lower)
        }
        if (defaultMatches.isNotEmpty()) {
            val first = defaultMatches.first()
            audioEngine.playTrack(first, defaultMatches, autoStart = true)
            return IntentExecutionOutcome(
                intentBadge = "SMART_SEARCH_PLAY",
                responseText = "Found ${defaultMatches.size} matching track(s). Now playing \"${first.title}\" by ${first.artist} (${first.bpm} BPM).",
                engineUsed = engineLabel,
                matchedTracks = defaultMatches
            )
        }

        return IntentExecutionOutcome(
            intentBadge = "ASSISTANT_READY",
            responseText = "I can control playback (\"Set volume to 80%\", \"Skip to 1 minute 30 seconds\"), sculpt DSP (\"Turn up the bass\", \"Set equalizer to Vocal preset\"), search (\"Find tracks with high BPM\", \"Play something acoustic\"), or build playlists (\"Create a late-night chill playlist\", \"Make a mix of tracks from the 90s\").",
            engineUsed = engineLabel
        )
    }

    private suspend fun handlePlaylistGenerationIntent(
        originalText: String,
        lower: String,
        allTracks: List<TrackEntity>,
        engineLabel: String
    ): IntentExecutionOutcome {
        val (playlistName, description, selectedTracks) = when {
            lower.contains("chill") || lower.contains("late-night") || lower.contains("late night") || lower.contains("relax") || lower.contains("lofi") -> {
                val matches = allTracks.filter {
                    it.bpm <= 105 ||
                        it.genre.contains("chill", ignoreCase = true) ||
                        it.genre.contains("acoustic", ignoreCase = true) ||
                        it.genre.contains("lo-fi", ignoreCase = true)
                }.ifEmpty { allTracks.sortedBy { it.bpm }.take(4) }
                Triple(
                    "Late-Night Chill Mix",
                    "AI-curated atmospheric, acoustic & low-BPM nocturnal tracks",
                    matches
                )
            }
            lower.contains("workout") || lower.contains("working out") || lower.contains("fast") || lower.contains("high bpm") || lower.contains("gym") || lower.contains("energy") -> {
                val matches = allTracks.filter {
                    it.bpm >= 125 ||
                        it.genre.contains("workout", ignoreCase = true) ||
                        it.genre.contains("electronic", ignoreCase = true) ||
                        it.genre.contains("rock", ignoreCase = true) ||
                        it.genre.contains("synthwave", ignoreCase = true)
                }.ifEmpty { allTracks.sortedByDescending { it.bpm }.take(4) }
                Triple(
                    "Workout High-BPM Mix",
                    "AI-curated fast tempo & kinetic overdrive tracks (128–146 BPM)",
                    matches
                )
            }
            lower.contains("90s") || lower.contains("nineties") || lower.contains("1990") || lower.contains("retro") -> {
                val matches = allTracks.filter {
                    it.year in 1990..1999 || it.album.contains("90s", ignoreCase = true) || it.album.contains("96", ignoreCase = true)
                }.ifEmpty { allTracks.sortedBy { it.year }.take(3) }
                Triple(
                    "90s Golden Era Mix",
                    "AI-curated 1990s hip-hop, tube rock, and retro chillwave",
                    matches
                )
            }
            else -> {
                val customTitle = originalText
                    .replace(Regex("(?i)(create|build|make|generate)\\s+(a|an)?\\s*"), "")
                    .replace(Regex("(?i)playlist.*"), "")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    .ifBlank { "Pulse AI Custom" } + " Mix"
                val matches = allTracks.shuffled().take(4.coerceAtMost(allTracks.size))
                Triple(
                    customTitle,
                    "Dynamic AI playlist curated from your local library",
                    matches
                )
            }
        }

        val created = repository.createPlaylist(
            name = playlistName,
            description = description,
            trackIds = selectedTracks.map { it.id },
            isAiGenerated = true
        )

        if (selectedTracks.isNotEmpty()) {
            audioEngine.playTrack(selectedTracks.first(), selectedTracks, autoStart = true)
        }

        return IntentExecutionOutcome(
            intentBadge = "AI_PLAYLIST_CREATED",
            responseText = "Generated '$playlistName' with ${selectedTracks.size} tracks from your local library and started playback.",
            engineUsed = engineLabel,
            matchedTracks = selectedTracks,
            createdPlaylist = created
        )
    }

    private fun handleEqualizerIntent(lower: String, engineLabel: String): IntentExecutionOutcome {
        return when {
            lower.contains("reset") || lower.contains("flat") || lower.contains("normal") -> {
                audioEngine.applyPresetByName("Flat")
                IntentExecutionOutcome(
                    intentBadge = "DSP_EQ_RESET",
                    responseText = "Reset 10-band equalizer to Flat (0.0 dB across 32Hz–16kHz) and disabled extra bass/reverb.",
                    engineUsed = engineLabel
                )
            }
            lower.contains("vocal") || lower.contains("voice") -> {
                audioEngine.applyPresetByName("Vocal")
                IntentExecutionOutcome(
                    intentBadge = "DSP_PRESET_VOCAL",
                    responseText = "Applied 'Vocal' 10-band EQ preset (+6.0 dB midrange presence at 1kHz–2kHz with Spatial Reverb).",
                    engineUsed = engineLabel
                )
            }
            lower.contains("rock") -> {
                audioEngine.applyPresetByName("Rock")
                IntentExecutionOutcome(
                    intentBadge = "DSP_PRESET_ROCK",
                    responseText = "Applied 'Rock' 10-band EQ preset (boosted low-end punch and 4kHz–16kHz guitar presence).",
                    engineUsed = engineLabel
                )
            }
            lower.contains("hip-hop") || lower.contains("hip hop") || lower.contains("rap") -> {
                audioEngine.applyPresetByName("Hip-Hop")
                IntentExecutionOutcome(
                    intentBadge = "DSP_PRESET_HIPHOP",
                    responseText = "Applied 'Hip-Hop' 10-band EQ preset (+7.5 dB @ 32Hz and 80% Sub-Bass Boost).",
                    engineUsed = engineLabel
                )
            }
            lower.contains("electronic") || lower.contains("edm") || lower.contains("synth") -> {
                audioEngine.applyPresetByName("Electronic")
                IntentExecutionOutcome(
                    intentBadge = "DSP_PRESET_ELECTRONIC",
                    responseText = "Applied 'Electronic' 10-band EQ preset with deep sub-bass and spatial hall reverb.",
                    engineUsed = engineLabel
                )
            }
            lower.contains("treble") || lower.contains("bright") || lower.contains("highs") -> {
                audioEngine.applyPresetByName("Treble Boost")
                IntentExecutionOutcome(
                    intentBadge = "DSP_PRESET_TREBLE",
                    responseText = "Applied 'Treble Boost' preset (+8.0 dB @ 8kHz, +9.0 dB @ 16kHz for crystalline highs).",
                    engineUsed = engineLabel
                )
            }
            lower.contains("bass") || lower.contains("sub") || lower.contains("lows") -> {
                audioEngine.applyPresetByName("Bass Boost")
                IntentExecutionOutcome(
                    intentBadge = "DSP_BASS_BOOST",
                    responseText = "Boosted low frequencies (+8.5 dB @ 32Hz, +7.5 dB @ 64Hz) and enabled 75% Sub-Bass DSP.",
                    engineUsed = engineLabel
                )
            }
            lower.contains("reverb") || lower.contains("spatial") -> {
                audioEngine.toggleSpatialReverb()
                val on = audioEngine.state.value.spatialReverbEnabled
                IntentExecutionOutcome(
                    intentBadge = "DSP_SPATIAL_REVERB",
                    responseText = if (on) "Enabled Spatial Hall Reverb DSP." else "Disabled Spatial Reverb DSP.",
                    engineUsed = engineLabel
                )
            }
            else -> {
                val presetList = BuiltInEqPresets.ALL_PRESETS.joinToString(", ") { it.name }
                IntentExecutionOutcome(
                    intentBadge = "DSP_EQ_STATUS",
                    responseText = "10-Band EQ is active on '${audioEngine.state.value.activePresetName}'. Available presets: $presetList.",
                    engineUsed = engineLabel
                )
            }
        }
    }

    private fun handleVolumeIntent(lower: String, engineLabel: String): IntentExecutionOutcome {
        if (lower.contains("unmute")) {
            audioEngine.setMuted(false)
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_UNMUTE",
                responseText = "Audio unmuted (Volume at ${(audioEngine.state.value.volume * 100).toInt()}%).",
                engineUsed = engineLabel
            )
        }
        if (lower.contains("mute")) {
            audioEngine.setMuted(true)
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_MUTE",
                responseText = "Audio output muted.",
                engineUsed = engineLabel
            )
        }

        val numMatch = Regex("""(\d{1,3})\s*%?""").find(lower)
        if (numMatch != null) {
            val pct = (numMatch.groupValues[1].toIntOrNull() ?: 80).coerceIn(0, 100)
            audioEngine.setVolume(pct / 100f)
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_VOLUME",
                responseText = "Set master playback volume to $pct%.",
                engineUsed = engineLabel
            )
        }

        if (lower.contains("up") || lower.contains("louder") || lower.contains("increase")) {
            val nextVol = (audioEngine.state.value.volume + 0.15f).coerceIn(0f, 1f)
            audioEngine.setVolume(nextVol)
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_VOLUME_UP",
                responseText = "Increased volume to ${(nextVol * 100).toInt()}%.",
                engineUsed = engineLabel
            )
        }

        if (lower.contains("down") || lower.contains("quieter") || lower.contains("lower")) {
            val nextVol = (audioEngine.state.value.volume - 0.15f).coerceIn(0f, 1f)
            audioEngine.setVolume(nextVol)
            return IntentExecutionOutcome(
                intentBadge = "PLAYBACK_VOLUME_DOWN",
                responseText = "Lowered volume to ${(nextVol * 100).toInt()}%.",
                engineUsed = engineLabel
            )
        }

        return IntentExecutionOutcome(
            intentBadge = "PLAYBACK_VOLUME",
            responseText = "Master volume is currently at ${(audioEngine.state.value.volume * 100).toInt()}%.",
            engineUsed = engineLabel
        )
    }

    private fun handleNaturalDiscoveryIntent(
        originalText: String,
        lower: String,
        allTracks: List<TrackEntity>,
        engineLabel: String
    ): IntentExecutionOutcome? {
        // High BPM / Fast tempo query
        if (lower.contains("high bpm") || lower.contains("fast tempo") || lower.contains("fast songs") || lower.contains("upbeat")) {
            val matches = allTracks.filter { it.bpm >= 125 }.sortedByDescending { it.bpm }
            if (matches.isNotEmpty()) {
                audioEngine.playTrack(matches.first(), matches, autoStart = true)
            }
            return IntentExecutionOutcome(
                intentBadge = "DISCOVERY_HIGH_BPM",
                responseText = "Found ${matches.size} high-BPM tracks (128–146 BPM). Playing \"${matches.firstOrNull()?.title ?: "Hyperdrive Protocol"}\" (${matches.firstOrNull()?.bpm ?: 146} BPM).",
                engineUsed = engineLabel,
                matchedTracks = matches
            )
        }

        // Low BPM / Slow / Chill
        if (lower.contains("low bpm") || lower.contains("slow tempo") || lower.contains("chill") || lower.contains("relaxing")) {
            val matches = allTracks.filter {
                it.bpm <= 105 || it.genre.contains("chill", ignoreCase = true)
            }.sortedBy { it.bpm }
            if (matches.isNotEmpty()) {
                audioEngine.playTrack(matches.first(), matches, autoStart = true)
            }
            return IntentExecutionOutcome(
                intentBadge = "DISCOVERY_CHILL",
                responseText = "Found ${matches.size} chill / low-tempo tracks. Playing \"${matches.firstOrNull()?.title ?: "Obsidian Velvet"}\" (${matches.firstOrNull()?.bpm ?: 84} BPM).",
                engineUsed = engineLabel,
                matchedTracks = matches
            )
        }

        // Genre specific: Acoustic, Electronic, Synthwave, Rock, Hip-Hop
        val genreKeywords = listOf("acoustic", "electronic", "synthwave", "rock", "hip-hop", "hip hop", "lo-fi")
        for (g in genreKeywords) {
            if (lower.contains(g)) {
                val normalized = if (g == "hip hop") "hip-hop" else g
                val matches = allTracks.filter {
                    it.genre.lowercase().contains(normalized) ||
                        it.title.lowercase().contains(normalized)
                }
                if (matches.isNotEmpty()) {
                    val first = matches.first()
                    audioEngine.playTrack(first, matches, autoStart = true)
                    return IntentExecutionOutcome(
                        intentBadge = "DISCOVERY_GENRE_${normalized.uppercase()}",
                        responseText = "Playing ${normalized.replaceFirstChar { it.uppercase() }} selection: \"${first.title}\" by ${first.artist} (${matches.size} track(s) matched).",
                        engineUsed = engineLabel,
                        matchedTracks = matches
                    )
                }
            }
        }

        // "Search songs by [Artist]" / "Songs by [Artist]" / "Play [Track or Artist]"
        val byArtistRegex = Regex("""(?i)(?:songs?\s+by|tracks?\s+by|music\s+by|search\s+by|artist)\s+(.+)""")
        val artistMatch = byArtistRegex.find(originalText)
        if (artistMatch != null) {
            val queryArtist = artistMatch.groupValues[1].trim().lowercase()
            val matches = allTracks.filter {
                it.artist.lowercase().contains(queryArtist) ||
                    it.title.lowercase().contains(queryArtist)
            }
            if (matches.isNotEmpty()) {
                val first = matches.first()
                audioEngine.playTrack(first, matches, autoStart = true)
                return IntentExecutionOutcome(
                    intentBadge = "DISCOVERY_ARTIST",
                    responseText = "Found ${matches.size} track(s) by ${first.artist}. Now playing \"${first.title}\".",
                    engineUsed = engineLabel,
                    matchedTracks = matches,
                    filterQueryToApply = first.artist
                )
            }
        }

        if (lower.startsWith("play ") || lower.startsWith("search ") || lower.startsWith("find ")) {
            val cleaned = lower
                .removePrefix("play ")
                .removePrefix("search ")
                .removePrefix("find ")
                .replace("songs", "")
                .replace("tracks", "")
                .replace("something", "")
                .trim()
            if (cleaned.isNotEmpty()) {
                val matches = allTracks.filter {
                    it.title.lowercase().contains(cleaned) ||
                        it.artist.lowercase().contains(cleaned) ||
                        it.album.lowercase().contains(cleaned) ||
                        it.genre.lowercase().contains(cleaned)
                }
                if (matches.isNotEmpty()) {
                    val first = matches.first()
                    audioEngine.playTrack(first, matches, autoStart = true)
                    return IntentExecutionOutcome(
                        intentBadge = "DISCOVERY_MATCH",
                        responseText = "Matched ${matches.size} track(s) for \"$cleaned\". Playing \"${first.title}\" by ${first.artist}.",
                        engineUsed = engineLabel,
                        matchedTracks = matches,
                        filterQueryToApply = cleaned
                    )
                }
            }
        }

        return null
    }

    private fun parseSeekTimestampSeconds(lower: String): Long? {
        if (!lower.contains("skip to") && !lower.contains("seek to") && !lower.contains("jump to") && !lower.contains("go to")) {
            return null
        }
        // Check mm:ss format e.g. "1:30"
        val colonMatch = Regex("""(\d{1,2}):(\d{2})""").find(lower)
        if (colonMatch != null) {
            val m = colonMatch.groupValues[1].toLongOrNull() ?: 0L
            val s = colonMatch.groupValues[2].toLongOrNull() ?: 0L
            return m * 60L + s
        }

        // Check "X minute(s) Y second(s)" or "X seconds"
        var totalSec = 0L
        var foundUnit = false
        val minMatch = Regex("""(\d+)\s*(?:minute|minutes|min|mins)""").find(lower)
        if (minMatch != null) {
            totalSec += (minMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
            foundUnit = true
        }
        val secMatch = Regex("""(\d+)\s*(?:second|seconds|sec|secs)""").find(lower)
        if (secMatch != null) {
            totalSec += (secMatch.groupValues[1].toLongOrNull() ?: 0L)
            foundUnit = true
        }
        return if (foundUnit) totalSec else null
    }

    private fun isGeminiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.startsWith("YOUR_")
    }

    private suspend fun queryGeminiForMusicIntent(
        userQuery: String,
        allTracks: List<TrackEntity>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val catalogSummary = allTracks.joinToString("\n") {
                "- \"${it.title}\" by ${it.artist} (${it.genre}, ${it.bpm} BPM, ${it.year})"
            }
            val prompt = """
                You are the embedded AI Music Concierge inside Obsidian Pulse, an ad-free dark glassmorphism music player.
                Here is the user's current local library:
                $catalogSummary
                
                User request: "$userQuery"
                Reply in 1-2 concise sentences recommending tracks from their local library or explaining what audio setting fits best.
            """.trimIndent()
            val response = GeminiClientHolder.service.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
