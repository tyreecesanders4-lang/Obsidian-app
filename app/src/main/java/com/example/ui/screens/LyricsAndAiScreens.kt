package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiChatMessage
import com.example.audio.PlayerEngineState
import com.example.data.LrcParser
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.MusicHubUiState
import com.example.ui.components.CoverArtImage
import com.example.ui.components.NeonBadge
import com.example.ui.components.formatDurationMs
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

/**
 * Synced LRC Lyrics Display Screen with artwork header, real-time active lyric line highlighting,
 * tap-any-line-to-seek, and an LRC Lyrics Editor/Importer modal.
 */
@Composable
fun LyricsScreen(
    playerState: PlayerEngineState,
    onSeekTo: (Long) -> Unit,
    onSaveLyrics: (TrackEntity, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack
    val parsedLines = remember(track?.lrcLyrics, playerState.durationMs) {
        LrcParser.parse(track?.lrcLyrics.orEmpty(), playerState.durationMs)
    }

    val activeLineIndex = remember(parsedLines, playerState.currentPositionMs) {
        val pos = playerState.currentPositionMs
        val idx = parsedLines.indexOfLast { it.timeMs <= pos + 250L }
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState()
    var showEditLyricsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeLineIndex, playerState.isPlaying) {
        if (parsedLines.isNotEmpty() && activeLineIndex in parsedLines.indices) {
            listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("lyrics_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Artwork + Track Info + Edit LRC Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 20.dp, highlightCyan = true)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArtImage(
                coverArtKey = track?.coverArtKey ?: "cyber_synth",
                contentDescription = track?.title ?: "Artwork",
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ElectricCyan, RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SYNCED LRC LYRICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = track?.title ?: "No Track Selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track?.artist ?: ""} • Tap any lyric line to seek",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = { showEditLyricsDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("edit_lrc_lyrics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = "Edit LRC Lyrics",
                    tint = NeonPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit LRC", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Synchronized Scrolling Lyric Lines
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .glassPanel(cornerRadius = 22.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("synced_lyrics_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            itemsIndexed(parsedLines) { idx, line ->
                val isCurrent = idx == activeLineIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isCurrent) {
                                Brush.horizontalGradient(
                                    listOf(
                                        NeonPurple.copy(alpha = 0.28f),
                                        ElectricCyan.copy(alpha = 0.16f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) ElectricCyan.copy(alpha = 0.55f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSeekTo(line.timeMs) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("lyric_line_$idx"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatDurationMs(line.timeMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) ElectricCyan else TextMuted,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = line.text,
                        style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) TextPrimary else TextSecondary.copy(alpha = 0.72f),
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showEditLyricsDialog && track != null) {
        var lrcText by remember(track) { mutableStateOf(track.lrcLyrics) }
        AlertDialog(
            onDismissRequest = { showEditLyricsDialog = false },
            containerColor = ObsidianSurfaceElevated,
            title = {
                Text("Edit Synced LRC Lyrics", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Use [mm:ss.xx] timestamps for frame-accurate sync, or plain lines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = lrcText,
                        onValueChange = { lrcText = it },
                        minLines = 7,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lrc_editor_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveLyrics(track, lrcText)
                        showEditLyricsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Save LRC")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLyricsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Embedded AI Voice Assistant & Natural Language Discovery Engine Screen / Panel.
 */
@Composable
fun AiVoiceAssistantScreen(
    uiState: MusicHubUiState,
    onTriggerMicVoice: () -> Unit,
    onStopMicVoice: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onToggleOfflinePrivacy: () -> Unit,
    onPlayTrackFromAi: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayPlaylistFromAi: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputQuery by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    val quickVoiceSuggestions = remember {
        listOf(
            "Create a late-night chill playlist",
            "Build a playlist with fast tempo songs for working out",
            "Make a mix of tracks from the 90s",
            "Turn up the bass",
            "Set equalizer to Vocal preset",
            "Reset EQ settings",
            "Find tracks with high BPM",
            "Play something acoustic",
            "Set volume to 80%",
            "Skip to 0 minutes 15 seconds",
            "Play next track"
        )
    }

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(uiState.chatMessages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("ai_voice_assistant_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Voice AI Header + Offline Privacy Toggle + Active Microphone Pulse
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 22.dp, highlightCyan = uiState.isListeningVoice)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            if (uiState.isListeningVoice) onStopMicVoice() else onTriggerMicVoice()
                        },
                        shape = CircleShape,
                        color = if (uiState.isListeningVoice) NeonMagenta else NeonPurple,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_mic_trigger_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isListeningVoice) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice Command Microphone",
                                tint = Color.White
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Pulse Voice AI & Discovery",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isListeningVoice) {
                                uiState.liveVoicePartialText.ifBlank { "Listening… speak a command" }
                            } else {
                                "Hands-free playback, EQ presets & smart playlist builder"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isListeningVoice) ElectricCyan else TextSecondary
                        )
                    }
                }

                FilterChip(
                    selected = uiState.offlinePrivacyMode,
                    onClick = onToggleOfflinePrivacy,
                    label = {
                        Text(
                            text = if (uiState.offlinePrivacyMode) "Offline NLP" else "Cloud + Local",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonEmerald.copy(alpha = 0.22f),
                        selectedLabelColor = NeonEmerald
                    ),
                    modifier = Modifier.testTag("offline_privacy_toggle_chip")
                )
            }

            // Quick Natural Language Command Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickVoiceSuggestions.forEachIndexed { idx, cmd ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ObsidianSurface)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .clickable { onSubmitCommand(cmd) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("quick_voice_chip_$idx")
                    ) {
                        Text(
                            text = "\"$cmd\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        // 2. Conversational Intent History & Action Confirmation Cards
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .glassPanel(cornerRadius = 20.dp)
                .padding(12.dp)
                .testTag("ai_chat_message_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(uiState.chatMessages, key = { _, item -> item.id }) { _, msg ->
                AiMessageBubble(
                    message = msg,
                    onPlayTrack = onPlayTrackFromAi,
                    onPlayPlaylist = onPlayPlaylistFromAi
                )
            }
        }

        // 3. Natural Language Command Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 82.dp)
                .glassPanel(cornerRadius = 18.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = {
                    Text(
                        "Say or type e.g. \"Create a late-night chill playlist\"…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = NeonPurple.copy(alpha = 0.35f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_command_input")
            )

            IconButton(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        onSubmitCommand(inputQuery)
                        inputQuery = ""
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonPurple, ElectricCyan)))
                    .testTag("ai_command_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Execute Voice/Text Intent",
                    tint = ObsidianVoid
                )
            }
        }
    }
}

@Composable
private fun AiMessageBubble(
    message: AiChatMessage,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayPlaylist: (PlaylistEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (message.isUser) {
                        Brush.horizontalGradient(
                            listOf(NeonPurple.copy(alpha = 0.45f), ElectricCyan.copy(alpha = 0.25f))
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(ObsidianVoid.copy(alpha = 0.85f), ObsidianSurface.copy(alpha = 0.90f))
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (message.isUser) ElectricCyan.copy(alpha = 0.5f) else NeonPurple.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            if (!message.isUser && message.intentBadge != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    NeonBadge(text = message.intentBadge, accentColor = ElectricCyan)
                    NeonBadge(text = message.engineUsed, accentColor = NeonPurple)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            // If AI created a playlist, show an interactive Play Mix card
            message.createdPlaylist?.let { pl ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurple.copy(alpha = 0.20f))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { onPlayPlaylist(pl) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pl.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pl.trackIds().size} tracks saved to Playlists",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${pl.name}",
                        tint = ElectricCyan
                    )
                }
            }

            // If AI matched tracks, show compact playable chips
            if (message.matchedTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                message.matchedTracks.take(4).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceElevated.copy(alpha = 0.75f))
                            .clickable { onPlayTrack(track, message.matchedTracks) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${track.title} — ${track.artist} (${track.bpm} BPM)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play ${track.title}",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
