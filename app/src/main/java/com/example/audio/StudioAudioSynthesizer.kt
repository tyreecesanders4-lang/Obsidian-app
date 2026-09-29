package com.example.audio

import android.content.Context
import com.example.data.TrackEntity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates real playable stereo 16-bit PCM WAV files in internal storage so that
 * MediaPlayer, hardware Equalizer, BassBoost, PresetReverb, and FFT Visualizer operate
 * on authentic local audio files right out of the box alongside user-imported MP3/WAV/FLAC/M4A files.
 */
object StudioAudioSynthesizer {

    private const val SAMPLE_RATE = 22050
    private const val NUM_CHANNELS = 2
    private const val BITS_PER_SAMPLE = 16

    data class StudioSpec(
        val fileName: String,
        val title: String,
        val artist: String,
        val album: String,
        val year: Int,
        val genre: String,
        val bpm: Int,
        val durationSec: Int,
        val coverArtKey: String,
        val chordFreqs: List<Double>,
        val bassFreqs: List<Double>,
        val leadScale: List<Double>,
        val driveStyle: Int, // 0 = synthwave, 1 = chill/lofi, 2 = workout/electro, 3 = acoustic, 4 = 90s hiphop, 5 = rock
        val lrcLyrics: String
    )

    val STUDIO_CATALOG = listOf(
        StudioSpec(
            fileName = "neon_horizon.wav",
            title = "Neon Horizon",
            artist = "Klangwerk X",
            album = "Obsidian Prism",
            year = 2025,
            genre = "Synthwave",
            bpm = 128,
            durationSec = 28,
            coverArtKey = "cyber_synth",
            chordFreqs = listOf(220.0, 261.63, 329.63, 196.0),
            bassFreqs = listOf(55.0, 65.41, 82.41, 49.0),
            leadScale = listOf(440.0, 523.25, 659.25, 587.33, 493.88, 659.25, 783.99, 523.25),
            driveStyle = 0,
            lrcLyrics = """
                [00:00.00] ♫ Initializing Obsidian Synth Deck — 128 BPM
                [00:03.50] Violet reflections across the rain-slicked glass
                [00:07.50] Sub-bass frequencies locking into phase
                [00:11.50] Chasing the cyan horizon through the midnight grid
                [00:15.50] Analog warmth meets digital precision
                [00:19.50] Every harmonic resonating in pure stereo space
                [00:23.50] Fade into the neon stratosphere...
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "obsidian_velvet.wav",
            title = "Obsidian Velvet",
            artist = "Luna Glass",
            album = "Midnight Lounge",
            year = 2024,
            genre = "Chill / Lo-Fi",
            bpm = 84,
            durationSec = 26,
            coverArtKey = "midnight_bass",
            chordFreqs = listOf(174.61, 220.0, 261.63, 329.63),
            bassFreqs = listOf(43.65, 55.0, 65.41, 49.0),
            leadScale = listOf(349.23, 440.0, 523.25, 659.25, 587.33, 523.25, 440.0, 392.0),
            driveStyle = 1,
            lrcLyrics = """
                [00:00.00] ♫ Late-Night Chill Session — 84 BPM
                [00:04.00] Soft velvet shadows drift across the studio floor
                [00:08.50] Warm Rhodes chords floating on a slow sub tide
                [00:13.00] Breathe in the quiet between the downbeats
                [00:17.50] No static, no rush, just pure nocturnal calm
                [00:22.00] Drifting weightless into the obsidian night
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "hyperdrive_protocol.wav",
            title = "Hyperdrive Protocol",
            artist = "Vortex 99",
            album = "Kinetic Overclock",
            year = 2025,
            genre = "Electronic / Workout",
            bpm = 146,
            durationSec = 26,
            coverArtKey = "cyber_synth",
            chordFreqs = listOf(246.94, 293.66, 369.99, 329.63),
            bassFreqs = listOf(61.74, 73.42, 92.50, 82.41),
            leadScale = listOf(493.88, 587.33, 739.99, 659.25, 739.99, 880.0, 739.99, 587.33),
            driveStyle = 2,
            lrcLyrics = """
                [00:00.00] ♫ High-BPM Kinetic Engine — 146 BPM
                [00:03.20] Heart rate rising with the four-on-the-floor kick
                [00:07.00] Adrenaline locked to the 16kHz hi-hat pulse
                [00:11.00] Push past the threshold into overdrive
                [00:15.00] Unstoppable momentum through the laser tunnel
                [00:19.00] Peak output achieved — keep moving forward
                [00:23.00] Hyperdrive engaged at maximum gain
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "2am_echoes.wav",
            title = "2 AM Echoes",
            artist = "Sora & The Pines",
            album = "Timber & Glass",
            year = 2023,
            genre = "Acoustic",
            bpm = 78,
            durationSec = 25,
            coverArtKey = "hero_obsidian",
            chordFreqs = listOf(196.0, 246.94, 293.66, 261.63),
            bassFreqs = listOf(98.0, 61.74, 73.42, 65.41),
            leadScale = listOf(392.0, 493.88, 587.33, 659.25, 587.33, 493.88, 440.0, 392.0),
            driveStyle = 3,
            lrcLyrics = """
                [00:00.00] ♫ Acoustic Intimate Session — 78 BPM
                [00:04.00] Plucked harmonic strings resonating in birchwood
                [00:08.50] Quiet footsteps underneath the amber streetlights
                [00:13.00] Every note lingers in the cathedral reverb
                [00:17.50] Simple melodies carved into memory
                [00:21.50] Gently fading into morning mist
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "boom_bap_1996.wav",
            title = "Cypher 1996",
            artist = "Metro Cipher",
            album = "Concrete Tapes '96",
            year = 1996,
            genre = "Hip-Hop",
            bpm = 94,
            durationSec = 26,
            coverArtKey = "midnight_bass",
            chordFreqs = listOf(164.81, 196.0, 246.94, 220.0),
            bassFreqs = listOf(41.20, 49.0, 61.74, 55.0),
            leadScale = listOf(329.63, 392.0, 440.0, 493.88, 392.0, 329.63, 293.66, 329.63),
            driveStyle = 4,
            lrcLyrics = """
                [00:00.00] ♫ Golden Era 1996 Tape Deck — 94 BPM
                [00:03.80] Dusty SP-1200 drums knocking in the trunk
                [00:08.00] 32Hz sub-bass rattling the subway platform
                [00:12.20] Jazz chords chopped on the 16-pad sampler
                [00:16.50] Raw nineties cadence from dusk till dawn
                [00:21.00] Timeless head-nod groove on heavy wax
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "static_overdrive_98.wav",
            title = "Static Overdrive",
            artist = "Voltage Union",
            album = "Amplifier Riot",
            year = 1998,
            genre = "Rock",
            bpm = 136,
            durationSec = 25,
            coverArtKey = "cyber_synth",
            chordFreqs = listOf(164.81, 220.0, 246.94, 196.0),
            bassFreqs = listOf(82.41, 110.0, 123.47, 98.0),
            leadScale = listOf(329.63, 440.0, 493.88, 587.33, 659.25, 587.33, 493.88, 440.0),
            driveStyle = 5,
            lrcLyrics = """
                [00:00.00] ♫ 1998 Tube Amp Overdrive — 136 BPM
                [00:03.50] Crank the gain stage until the tubes glow crimson
                [00:07.50] Power chords cutting through the midnight static
                [00:11.50] Snare drums cracking like summer lightning
                [00:15.50] Turn the 2kHz and 4kHz sliders to the sky
                [00:20.00] Full feedback resonance into the final bar
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "subzero_resonance.wav",
            title = "Subzero Resonance",
            artist = "Klangwerk X",
            album = "Obsidian Prism",
            year = 2025,
            genre = "Electronic",
            bpm = 140,
            durationSec = 26,
            coverArtKey = "midnight_bass",
            chordFreqs = listOf(146.83, 174.61, 220.0, 196.0),
            bassFreqs = listOf(36.71, 43.65, 55.0, 49.0),
            leadScale = listOf(293.66, 349.23, 440.0, 523.25, 587.33, 440.0, 349.23, 293.66),
            driveStyle = 2,
            lrcLyrics = """
                [00:00.00] ♫ Sub-Bass Calibration Track — 140 BPM
                [00:03.80] Deep 36Hz pressure waves testing your woofer cone
                [00:08.00] Crystalline high-frequency arpeggios above the abyss
                [00:12.50] Toggle Bass Boost to feel the tectonic shift
                [00:17.00] Precision sculpted DSP soundstage
                [00:21.50] Pure subzero acoustic architecture
            """.trimIndent()
        ),
        StudioSpec(
            fileName = "solaris_1994.wav",
            title = "Solaris 1994",
            artist = "Luna Glass",
            album = "90s Retrograde",
            year = 1994,
            genre = "Chill / Acoustic",
            bpm = 98,
            durationSec = 25,
            coverArtKey = "hero_obsidian",
            chordFreqs = listOf(261.63, 220.0, 174.61, 196.0),
            bassFreqs = listOf(65.41, 55.0, 43.65, 49.0),
            leadScale = listOf(523.25, 659.25, 587.33, 523.25, 440.0, 392.0, 440.0, 523.25),
            driveStyle = 3,
            lrcLyrics = """
                [00:00.00] ♫ 1994 Nostalgic Horizon — 98 BPM
                [00:04.00] Cassette tape memories warming in the sun
                [00:08.50] Acoustic harmonics weaving through analog pads
                [00:13.00] Golden hour light reflected in dark glass
                [00:17.50] Where nineties warmth meets future clarity
                [00:21.50] Endless summer loop in stereo
            """.trimIndent()
        )
    )

    fun ensureStudioTracksGenerated(context: Context): List<TrackEntity> {
        val dir = File(context.filesDir, "studio_tracks")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        return STUDIO_CATALOG.map { spec ->
            val targetFile = File(dir, spec.fileName)
            if (!targetFile.exists() || targetFile.length() < 10_000L) {
                writeSynthesizedWavFile(targetFile, spec)
            }
            TrackEntity(
                title = spec.title,
                artist = spec.artist,
                album = spec.album,
                year = spec.year,
                genre = spec.genre,
                durationMs = spec.durationSec * 1000L,
                bpm = spec.bpm,
                filePathOrUri = targetFile.absolutePath,
                isOfflineCached = true,
                cachedFilePath = targetFile.absolutePath,
                coverArtKey = spec.coverArtKey,
                bitrateKbps = (SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE) / 1000,
                formatExt = "WAV",
                lrcLyrics = spec.lrcLyrics
            )
        }
    }

    private fun writeSynthesizedWavFile(file: File, spec: StudioSpec) {
        val totalSamples = SAMPLE_RATE * spec.durationSec
        val dataSize = totalSamples * NUM_CHANNELS * (BITS_PER_SAMPLE / 8)
        val totalFileSize = 36 + dataSize

        val beatDurationSec = 60.0 / spec.bpm.toDouble()
        val eighthNoteSec = beatDurationSec / 2.0
        val barDurationSec = beatDurationSec * 4.0

        BufferedOutputStream(FileOutputStream(file), 65536).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF chunk
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalFileSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            // fmt sub-chunk
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM chunk size
            header.putShort(1) // AudioFormat 1 = PCM
            header.putShort(NUM_CHANNELS.toShort())
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * NUM_CHANNELS * (BITS_PER_SAMPLE / 8))
            header.putShort((NUM_CHANNELS * (BITS_PER_SAMPLE / 8)).toShort())
            header.putShort(BITS_PER_SAMPLE.toShort())
            // data sub-chunk
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())

            val chunkFrames = 2048
            val pcmBuffer = ByteBuffer.allocate(chunkFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
            var frameIndex = 0

            while (frameIndex < totalSamples) {
                pcmBuffer.clear()
                val framesToWrite = minOf(chunkFrames, totalSamples - frameIndex)
                for (i in 0 until framesToWrite) {
                    val sampleIdx = frameIndex + i
                    val t = sampleIdx.toDouble() / SAMPLE_RATE

                    val barIdx = ((t / barDurationSec).toInt()) % spec.chordFreqs.size
                    val beatPhase = (t % beatDurationSec) / beatDurationSec
                    val eighthIdx = ((t / eighthNoteSec).toInt()) % spec.leadScale.size
                    val eighthPhase = (t % eighthNoteSec) / eighthNoteSec

                    val rootFreq = spec.chordFreqs[barIdx]
                    val bassFreq = spec.bassFreqs[barIdx]
                    val leadFreq = spec.leadScale[eighthIdx]

                    // 1. Sub & Mid Bass (32Hz - 150Hz rich for EQ bands 1-3)
                    val bassEnv = exp(-2.8 * beatPhase)
                    val bassWave = (sin(2.0 * PI * bassFreq * t) * 0.42 +
                            sin(2.0 * PI * bassFreq * 2.0 * t) * 0.18) * bassEnv

                    // 2. Kick drum punch on beats
                    val kickFreq = 115.0 * exp(-22.0 * beatPhase) + 38.0
                    val kickEnv = exp(-9.5 * beatPhase)
                    val kickWave = sin(2.0 * PI * kickFreq * t) * kickEnv * 0.36

                    // 3. Harmonic Pad / Chord (250Hz - 1kHz for Vocal/Mid EQ bands)
                    val padL = (sin(2.0 * PI * rootFreq * t) +
                            sin(2.0 * PI * (rootFreq * 1.25) * t) * 0.7 +
                            sin(2.0 * PI * (rootFreq * 1.5) * t) * 0.6) * 0.14
                    val padR = (sin(2.0 * PI * (rootFreq * 1.003) * t) +
                            sin(2.0 * PI * (rootFreq * 1.253) * t) * 0.7 +
                            sin(2.0 * PI * (rootFreq * 1.498) * t) * 0.6) * 0.14

                    // 4. Arpeggio / Pluck Lead (1kHz - 4kHz)
                    val pluckDecay = if (spec.driveStyle == 3) 6.5 else 4.0
                    val leadEnv = exp(-pluckDecay * eighthPhase)
                    val leadHarmonic = sin(2.0 * PI * leadFreq * t) +
                            0.45 * sin(2.0 * PI * leadFreq * 2.0 * t) +
                            0.20 * sin(2.0 * PI * leadFreq * 3.0 * t)
                    val leadWave = leadHarmonic * leadEnv * 0.18

                    // 5. Shimmer / Hi-Hat Crispness (8kHz - 16kHz for Treble EQ bands)
                    val hatEnv = exp(-28.0 * eighthPhase)
                    val pseudoNoise = sin(2.0 * PI * 6430.0 * t + sin(2.0 * PI * 9120.0 * t) * 3.5)
                    val hatWave = pseudoNoise * hatEnv * 0.10

                    // Master fade-in / fade-out envelope
                    val masterEnv = when {
                        t < 0.8 -> t / 0.8
                        t > spec.durationSec - 1.2 -> ((spec.durationSec - t) / 1.2).coerceAtLeast(0.0)
                        else -> 1.0
                    }

                    // Stereo pan on lead arpeggio
                    val panL = 0.75 + 0.25 * sin(2.0 * PI * 0.5 * t)
                    val panR = 0.75 - 0.25 * sin(2.0 * PI * 0.5 * t)

                    val leftRaw = (bassWave + kickWave + padL + leadWave * panL + hatWave) * masterEnv
                    val rightRaw = (bassWave + kickWave + padR + leadWave * panR + hatWave) * masterEnv

                    val leftPcm = (leftRaw.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                    val rightPcm = (rightRaw.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()

                    pcmBuffer.putShort(leftPcm)
                    pcmBuffer.putShort(rightPcm)
                }
                out.write(pcmBuffer.array(), 0, framesToWrite * 4)
                frameIndex += framesToWrite
            }
            out.flush()
        }
    }
}
