package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlayerEngineState
import com.example.audio.VisualizerMode
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassPanel

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

@Composable
fun CoverArtImage(
    coverArtKey: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val resId = when (coverArtKey) {
        "midnight_bass" -> R.drawable.img_cover_midnight_bass
        "hero_obsidian" -> R.drawable.img_hero_obsidian
        else -> R.drawable.img_cover_cyber_synth
    }
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun NeonBadge(
    text: String,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Interactive 32-Bar Live Canvas Audio Visualizer with mode switcher:
 * - 32-Bar FFT Frequency Spectrum (with peak-hold floating caps & mirrored sub-reflection)
 * - Neon Oscilloscope Waveform
 * - Stereo Peak Level Meters Fallback
 */
@Composable
fun LiveAudioVisualizerPanel(
    playerState: PlayerEngineState,
    onModeChange: (VisualizerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 20.dp, highlightCyan = true)
            .padding(14.dp)
            .testTag("live_visualizer_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Audio Visualizer",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when (playerState.visualizerMode) {
                        VisualizerMode.SPECTRUM_32_BAR -> "32-BAR FFT SPECTRUM"
                        VisualizerMode.NEON_WAVEFORM -> "NEON OSCILLOSCOPE"
                        VisualizerMode.PEAK_METERS -> "STEREO PEAK METERS"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VisualizerModeChip(
                    label = "32-Bar",
                    selected = playerState.visualizerMode == VisualizerMode.SPECTRUM_32_BAR,
                    onClick = { onModeChange(VisualizerMode.SPECTRUM_32_BAR) },
                    tag = "vis_mode_32bar"
                )
                VisualizerModeChip(
                    label = "Wave",
                    selected = playerState.visualizerMode == VisualizerMode.NEON_WAVEFORM,
                    onClick = { onModeChange(VisualizerMode.NEON_WAVEFORM) },
                    tag = "vis_mode_wave"
                )
                VisualizerModeChip(
                    label = "Peaks",
                    selected = playerState.visualizerMode == VisualizerMode.PEAK_METERS,
                    onClick = { onModeChange(VisualizerMode.PEAK_METERS) },
                    tag = "vis_mode_peaks"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianVoid.copy(alpha = 0.75f))
                .border(1.dp, NeonPurple.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            when (playerState.visualizerMode) {
                VisualizerMode.SPECTRUM_32_BAR -> {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("visualizer_32_bar_canvas")
                    ) {
                        val bars = playerState.fftBars32
                        val peaks = playerState.peakCaps32
                        val count = 32
                        val w = size.width
                        val h = size.height
                        val mainFloorY = h * 0.80f
                        val totalGap = w * 0.24f
                        val gap = totalGap / (count + 1)
                        val barWidth = ((w - totalGap) / count).coerceAtLeast(3f)

                        // Subtle horizontal decibel grid lines
                        for (g in 1..3) {
                            val gy = mainFloorY * (g / 4f)
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, gy),
                                end = Offset(w, gy),
                                strokeWidth = 1f
                            )
                        }

                        for (i in 0 until count) {
                            val norm = bars.getOrElse(i) { 0.06f }.coerceIn(0.04f, 1f)
                            val peakNorm = peaks.getOrElse(i) { 0.08f }.coerceIn(norm, 1f)
                            val barHeight = (norm * (mainFloorY - 6f)).coerceAtLeast(4f)
                            val x = gap + i * (barWidth + gap)
                            val topY = mainFloorY - barHeight

                            val barBrush = Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan,
                                    NeonPurple,
                                    NeonMagenta.copy(alpha = 0.85f)
                                ),
                                startY = topY,
                                endY = mainFloorY
                            )

                            // Main frequency bar
                            drawRoundRect(
                                brush = barBrush,
                                topLeft = Offset(x, topY),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )

                            // Floating Peak-Hold Indicator Cap
                            val peakY = (mainFloorY - peakNorm * (mainFloorY - 6f) - 4f).coerceAtLeast(2f)
                            drawRoundRect(
                                color = ElectricCyan,
                                topLeft = Offset(x, peakY),
                                size = Size(barWidth, 3.5f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )

                            // Mirrored translucent reflection below floor
                            val reflectionHeight = (barHeight * 0.22f).coerceAtMost(h - mainFloorY - 2f)
                            if (reflectionHeight > 1f) {
                                drawRoundRect(
                                    color = NeonPurple.copy(alpha = 0.22f),
                                    topLeft = Offset(x, mainFloorY + 3f),
                                    size = Size(barWidth, reflectionHeight),
                                    cornerRadius = CornerRadius(3f, 3f)
                                )
                            }
                        }
                    }
                }

                VisualizerMode.NEON_WAVEFORM -> {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("visualizer_waveform_canvas")
                    ) {
                        val pts = playerState.waveformPoints
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1f
                        )

                        if (pts.size >= 2) {
                            val primaryPath = Path()
                            val mirrorPath = Path()
                            val stepX = w / (pts.size - 1).coerceAtLeast(1)

                            pts.forEachIndexed { idx, sample ->
                                val px = idx * stepX
                                val py = (midY + sample * (midY * 0.82f)).coerceIn(4f, h - 4f)
                                val my = (midY - sample * (midY * 0.50f)).coerceIn(4f, h - 4f)
                                if (idx == 0) {
                                    primaryPath.moveTo(px, py)
                                    mirrorPath.moveTo(px, my)
                                } else {
                                    primaryPath.lineTo(px, py)
                                    mirrorPath.lineTo(px, my)
                                }
                            }

                            drawPath(
                                path = mirrorPath,
                                color = NeonPurple.copy(alpha = 0.45f),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawPath(
                                path = primaryPath,
                                brush = Brush.horizontalGradient(
                                    listOf(NeonPurple, ElectricCyan, NeonMagenta)
                                ),
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                VisualizerMode.PEAK_METERS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PeakMeterRow(label = "L CH", level = playerState.leftPeakLevel)
                        PeakMeterRow(label = "R CH", level = playerState.rightPeakLevel)
                        PeakMeterRow(
                            label = "SUB",
                            level = playerState.fftBars32.take(6).average().toFloat().coerceIn(0f, 1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "32Hz SUB",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = "250Hz",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = "1kHz MID",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = "4kHz",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = "16kHz AIR",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun VisualizerModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val bg = if (selected) ElectricCyan.copy(alpha = 0.20f) else ObsidianSurface
    val border = if (selected) ElectricCyan else Color.White.copy(alpha = 0.14f)
    val textColor = if (selected) ElectricCyan else TextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PeakMeterRow(
    label: String,
    level: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan,
            modifier = Modifier.width(38.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val segments = 24
            val activeSegments = (level.coerceIn(0f, 1f) * segments).toInt()
            for (s in 0 until segments) {
                val segColor = when {
                    s >= 20 -> NeonMagenta
                    s >= 15 -> AmberWarning
                    s >= 8 -> ElectricCyan
                    else -> NeonEmerald
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (s <= activeSegments) segColor else Color.White.copy(alpha = 0.07f)
                        )
                )
            }
        }
        val dbText = if (level <= 0.02f) "-∞ dB" else "%+.1f dB".format((level - 0.75f) * 16f)
        Text(
            text = dbText,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.width(52.dp)
        )
    }
}

/**
 * Persistent Dark Glassmorphism Mini-Player Bar shown above the bottom navigation bar.
 */
@Composable
fun GlassMiniPlayerBar(
    playerState: PlayerEngineState,
    onOpenNowPlaying: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val progress = (playerState.currentPositionMs.toFloat() / playerState.durationMs.coerceAtLeast(1L).toFloat())
        .coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glassPanel(cornerRadius = 18.dp, highlightCyan = playerState.isPlaying)
            .clickable(onClick = onOpenNowPlaying)
            .testTag("mini_player_bar")
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = ElectricCyan,
            trackColor = NeonPurple.copy(alpha = 0.22f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArtImage(
                coverArtKey = track.coverArtKey,
                contentDescription = track.title,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    if (playerState.isCrossfadingNow) {
                        NeonBadge(text = "XFADE", accentColor = NeonMagenta)
                    }
                }
                Text(
                    text = "${track.artist} • ${track.bpm} BPM • ${formatDurationMs(playerState.currentPositionMs)} / ${formatDurationMs(playerState.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = TextPrimary
                )
            }

            Surface(
                onClick = onPlayPause,
                shape = CircleShape,
                color = NeonPurple,
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("mini_play_pause_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_next_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = TextPrimary
                )
            }

            IconButton(
                onClick = onOpenEqualizer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_eq_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Open 10-Band Equalizer",
                    tint = ElectricCyan
                )
            }
        }
    }
}
