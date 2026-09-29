package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.LoopMode
import com.example.audio.PlayerEngineState
import com.example.audio.VisualizerMode
import com.example.ui.components.CoverArtImage
import com.example.ui.components.LiveAudioVisualizerPanel
import com.example.ui.components.NeonBadge
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassPanel

@Composable
fun PlayerScreen(
    playerState: PlayerEngineState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onCycleLoopMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onCrossfadeChange: (Float) -> Unit,
    onVisualizerModeChange: (VisualizerMode) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("player_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Track Artwork & Studio Telemetry Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 24.dp, highlightCyan = playerState.isPlaying)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonBadge(
                    text = "${track?.formatExt ?: "WAV"} • ${track?.bitrateKbps ?: 1411} kbps",
                    accentColor = ElectricCyan
                )
                NeonBadge(
                    text = "EQ: ${playerState.activePresetName.uppercase()}",
                    accentColor = NeonPurple
                )
                NeonBadge(
                    text = if (track?.isOfflineCached == true) "OFFLINE CACHED" else "STREAM",
                    accentColor = NeonEmerald
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(ElectricCyan, NeonPurple, NeonMagenta)
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(
                    coverArtKey = track?.coverArtKey ?: "cyber_synth",
                    contentDescription = track?.title ?: "Now Playing Artwork",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = track?.title ?: "No Track Loaded",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${track?.artist ?: "Unknown Artist"} — ${track?.album ?: "Studio Vault"} (${track?.year ?: 2025})",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonBadge(text = "${track?.bpm ?: 128} BPM", accentColor = ElectricCyan)
                NeonBadge(text = track?.genre ?: "Synthwave", accentColor = NeonPurple)
                if (playerState.crossfadeSeconds > 0f) {
                    NeonBadge(
                        text = "XFADE ${"%.1f".format(playerState.crossfadeSeconds)}s",
                        accentColor = NeonMagenta
                    )
                }
            }
        }

        // 2. Interactive 32-Bar Live Canvas Audio Visualizer
        LiveAudioVisualizerPanel(
            playerState = playerState,
            onModeChange = onVisualizerModeChange
        )

        // 3. Transport Deck: Seek Bar, Timestamps, Play/Pause/Skip, Shuffle, Loop
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 22.dp)
                .padding(16.dp)
        ) {
            val safeDur = playerState.durationMs.coerceAtLeast(1L)
            val sliderVal = (playerState.currentPositionMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)

            Slider(
                value = sliderVal,
                onValueChange = { frac ->
                    onSeekTo((frac * safeDur).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = NeonPurple.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_seek_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMs(playerState.currentPositionMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    modifier = Modifier.testTag("player_current_time")
                )
                Text(
                    text = formatDurationMs(playerState.durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.testTag("player_total_duration")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Transport Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (playerState.isShuffle) ElectricCyan else TextMuted
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("player_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Surface(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonPurple, ElectricCyan))
                        )
                        .testTag("player_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = ObsidianVoid,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onCycleLoopMode,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_loop_button")
                ) {
                    val loopIcon = if (playerState.loopMode == LoopMode.ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    }
                    val loopTint = when (playerState.loopMode) {
                        LoopMode.OFF -> TextMuted
                        LoopMode.ALL -> ElectricCyan
                        LoopMode.ONE -> NeonMagenta
                    }
                    Icon(
                        imageVector = loopIcon,
                        contentDescription = "Loop Mode ${playerState.loopMode.name}",
                        tint = loopTint
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Volume Slider + Mute Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_mute_button")
                ) {
                    Icon(
                        imageVector = if (playerState.isMuted || playerState.volume == 0f) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (playerState.isMuted) "Unmute" else "Mute",
                        tint = if (playerState.isMuted) NeonMagenta else ElectricCyan
                    )
                }

                Slider(
                    value = if (playerState.isMuted) 0f else playerState.volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurple,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_volume_slider")
                )

                Text(
                    text = if (playerState.isMuted) "MUTE" else "${(playerState.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.width(44.dp)
                )
            }
        }

        // 4. Advanced Playback Modes: Gapless, Crossfade & Quick Studio Links
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 20.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gapless & Crossfade Engine",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (playerState.crossfadeSeconds <= 0.1f) {
                            "0.0s — True Gapless Transition"
                        } else {
                            "Smooth ${"%.1f".format(playerState.crossfadeSeconds)}s Equal-Power Gain Crossfade"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }

                FilterChip(
                    selected = playerState.isContinuousAutoPlay,
                    onClick = onToggleAutoPlay,
                    label = { Text("Auto-Play") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonPurple.copy(alpha = 0.25f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("toggle_autoplay_chip")
                )
            }

            Slider(
                value = playerState.crossfadeSeconds,
                onValueChange = onCrossfadeChange,
                valueRange = 0f..12f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = NeonMagenta,
                    activeTrackColor = NeonMagenta,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crossfade_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = true,
                    onClick = onOpenEqualizer,
                    label = { Text("10-Band EQ (${playerState.activePresetName})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_open_eq_chip")
                )

                FilterChip(
                    selected = true,
                    onClick = onOpenLyrics,
                    label = { Text("Synced LRC Lyrics") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_open_lyrics_chip")
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}
