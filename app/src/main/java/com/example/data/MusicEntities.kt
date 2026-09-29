package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int,
    val genre: String,
    val durationMs: Long,
    val bpm: Int,
    val filePathOrUri: String,
    val isOfflineCached: Boolean = true,
    val cachedFilePath: String? = null,
    val coverArtKey: String = "cyber_synth",
    val bitrateKbps: Int = 1411,
    val formatExt: String = "WAV",
    val lrcLyrics: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val trackIdsCsv: String = "",
    val isAiGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun trackIds(): List<Long> =
        trackIdsCsv.split(",")
            .mapNotNull { it.trim().toLongOrNull() }

    companion object {
        fun encodeTrackIds(ids: List<Long>): String = ids.joinToString(",")
    }
}

@Entity(tableName = "eq_presets")
data class CustomEqPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gainsDbCsv: String,
    val preampDb: Float = 0f,
    val bassBoostPercent: Int = 0,
    val spatialReverb: Boolean = false
) {
    fun gainsList(): List<Float> {
        val parsed = gainsDbCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
        return if (parsed.size == 10) parsed else List(10) { 0f }
    }
}

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    private val timeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")

    fun parse(rawLrc: String, totalDurationMs: Long = 180_000L): List<LrcLine> {
        if (rawLrc.isBlank()) return emptyList()
        val lines = rawLrc.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val timedLines = mutableListOf<LrcLine>()
        val untimedLines = mutableListOf<String>()

        for (line in lines) {
            val match = timeRegex.matchEntire(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val fracMs = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> (fracStr.toLongOrNull() ?: 0L)
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    timedLines.add(LrcLine(min * 60_000L + sec * 1000L + fracMs, text))
                }
            } else if (!line.startsWith("[")) {
                untimedLines.add(line)
            }
        }

        if (timedLines.isNotEmpty()) {
            return timedLines.sortedBy { it.timeMs }
        }

        // Distribute untimed lines evenly across the track duration
        val safeDuration = totalDurationMs.coerceAtLeast(15_000L)
        val step = safeDuration / (untimedLines.size + 1).coerceAtLeast(2)
        return untimedLines.mapIndexed { idx, txt ->
            LrcLine(timeMs = idx * step, text = txt)
        }
    }
}

data class EqPresetProfile(
    val name: String,
    val bandsDb: List<Float>, // 10 bands: 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16k
    val preampDb: Float = 0f,
    val bassBoostPercent: Int = 0,
    val spatialReverb: Boolean = false
)

object BuiltInEqPresets {
    val FREQUENCY_LABELS = listOf(
        "32Hz", "64Hz", "125Hz", "250Hz", "500Hz",
        "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
    )
    val FREQUENCY_HZ = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val ALL_PRESETS = listOf(
        EqPresetProfile(
            name = "Flat",
            bandsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            preampDb = 0f,
            bassBoostPercent = 0,
            spatialReverb = false
        ),
        EqPresetProfile(
            name = "Bass Boost",
            bandsDb = listOf(8.5f, 7.5f, 5.5f, 3.0f, 0.5f, 0f, 0f, 0.5f, 1.0f, 1.5f),
            preampDb = 2.0f,
            bassBoostPercent = 75,
            spatialReverb = false
        ),
        EqPresetProfile(
            name = "Treble Boost",
            bandsDb = listOf(0f, 0f, 0f, 0.5f, 1.0f, 2.5f, 4.5f, 6.5f, 8.0f, 9.0f),
            preampDb = 1.5f,
            bassBoostPercent = 10,
            spatialReverb = false
        ),
        EqPresetProfile(
            name = "Vocal",
            bandsDb = listOf(-2.5f, -1.5f, -0.5f, 2.0f, 4.5f, 6.0f, 5.5f, 3.5f, 1.0f, -0.5f),
            preampDb = 1.5f,
            bassBoostPercent = 15,
            spatialReverb = true
        ),
        EqPresetProfile(
            name = "Rock",
            bandsDb = listOf(5.5f, 4.5f, 2.5f, -1.0f, -2.0f, 0.5f, 3.5f, 5.5f, 6.5f, 6.5f),
            preampDb = 2.0f,
            bassBoostPercent = 50,
            spatialReverb = false
        ),
        EqPresetProfile(
            name = "Hip-Hop",
            bandsDb = listOf(7.5f, 6.5f, 4.0f, 1.5f, -1.0f, -0.5f, 1.5f, 3.0f, 4.5f, 5.5f),
            preampDb = 2.5f,
            bassBoostPercent = 80,
            spatialReverb = false
        ),
        EqPresetProfile(
            name = "Electronic",
            bandsDb = listOf(6.5f, 6.0f, 3.0f, 0f, -2.0f, 1.0f, 3.5f, 5.5f, 7.0f, 8.0f),
            preampDb = 2.5f,
            bassBoostPercent = 65,
            spatialReverb = true
        )
    )
}
