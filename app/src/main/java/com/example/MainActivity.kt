package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainDestination
import com.example.ui.MusicHubViewModel
import com.example.ui.components.GlassMiniPlayerBar
import com.example.ui.components.NeonBadge
import com.example.ui.screens.AiVoiceAssistantScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LyricsScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianPulseTheme
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassPanel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: MusicHubViewModel by viewModels()
    private var mediaSession: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupMediaSession()

        setContent {
            ObsidianPulseTheme {
                ObsidianPulseApp(
                    viewModel = viewModel,
                    onUpdateMediaSession = { title, artist, album, durationMs, positionMs, isPlaying ->
                        updateMediaSessionMetadata(title, artist, album, durationMs, positionMs, isPlaying)
                    }
                )
            }
        }
    }

    private fun setupMediaSession() {
        try {
            val session = MediaSession(this, "ObsidianPulseMediaSession")
            session.setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    viewModel.audioEngine.play()
                }

                override fun onPause() {
                    viewModel.audioEngine.pause()
                }

                override fun onSkipToNext() {
                    viewModel.audioEngine.nextTrack()
                }

                override fun onSkipToPrevious() {
                    viewModel.audioEngine.previousTrack()
                }

                override fun onSeekTo(pos: Long) {
                    viewModel.audioEngine.seekTo(pos)
                }
            })
            session.isActive = true
            mediaSession = session
        } catch (_: Exception) {
        }
    }

    private fun updateMediaSessionMetadata(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        try {
            val session = mediaSession ?: return
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build()
            session.setMetadata(metadata)

            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs,
                    1.0f
                )
                .build()
            session.setPlaybackState(state)
        } catch (_: Exception) {
        }
    }

    /**
     * Keyboard & Hardware Media Shortcuts:
     * - Space / Media Play-Pause: Toggle Play/Pause
     * - Left / Right Arrow: Seek -5s / +5s
     * - Media Next / Previous: Skip tracks
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                viewModel.audioEngine.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.audioEngine.skipRelativeMs(-5000L)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.audioEngine.skipRelativeMs(5000L)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                viewModel.audioEngine.nextTrack()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                viewModel.audioEngine.previousTrack()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        super.onDestroy()
    }
}

private data class NavItemSpec(
    val destination: MainDestination,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianPulseApp(
    viewModel: MusicHubViewModel,
    onUpdateMediaSession: (String, String, String, Long, Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val customPresets by viewModel.customEqPresets.collectAsStateWithLifecycle()

    // Sync MediaSession metadata when track or playback state changes
    LaunchedEffect(
        playerState.currentTrack?.id,
        playerState.isPlaying,
        playerState.durationMs
    ) {
        val t = playerState.currentTrack
        if (t != null) {
            onUpdateMediaSession(
                t.title,
                t.artist,
                t.album,
                playerState.durationMs,
                playerState.currentPositionMs,
                playerState.isPlaying
            )
        }
    }

    // Auto-dismiss status banner after 4.5 seconds
    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(4500L)
            viewModel.clearBanner()
        }
    }

    // BackHandler for secondary screens & slide-out AI Voice Assistant sheet
    BackHandler(
        enabled = uiState.isVoiceSheetOpen || uiState.currentDestination != MainDestination.LIBRARY
    ) {
        if (uiState.isVoiceSheetOpen) {
            viewModel.setVoiceSheetOpen(false)
        } else {
            viewModel.navigateTo(MainDestination.LIBRARY)
        }
    }

    // System File Picker (.mp3, .wav, .flac, .m4a, .ogg)
    val openAudioFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalAudioFiles(uris)
        }
    }

    // System Folder Scanner
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.scanFolderUri(treeUri)
        }
    }

    // Runtime Microphone Permission for Voice Command Assistant & Hardware Visualizer
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCapture()
        } else {
            viewModel.setVoiceSheetOpen(true)
            viewModel.showBanner("Voice panel opened. Tap any natural language command chip or type below.")
        }
    }

    val triggerVoiceAssistant = {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.startVoiceCapture()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val navItems = listOf(
        NavItemSpec(MainDestination.LIBRARY, "Library", Icons.Default.LibraryMusic, "nav_library"),
        NavItemSpec(MainDestination.NOW_PLAYING, "Player", Icons.Default.PlayCircle, "nav_player"),
        NavItemSpec(MainDestination.EQUALIZER, "10-Band EQ", Icons.Default.Equalizer, "nav_equalizer"),
        NavItemSpec(MainDestination.LYRICS, "Lyrics", Icons.Default.Lyrics, "nav_lyrics"),
        NavItemSpec(MainDestination.AI_ASSISTANT, "Voice AI", Icons.Default.AutoAwesome, "nav_voice_ai")
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianVoid, ObsidianBackground, ObsidianVoid)
                )
            )
    ) {
        val isWideScreen = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                ObsidianTopHeaderBar(
                    activePreset = playerState.activePresetName,
                    isPlaying = playerState.isPlaying,
                    onRestoreStudioTracks = { viewModel.restoreStudioTracks() },
                    onOpenVoiceSheet = { viewModel.setVoiceSheetOpen(true) }
                )
            },
            floatingActionButton = {
                // Floating Voice Command Microphone Button
                FloatingActionButton(
                    onClick = triggerVoiceAssistant,
                    shape = CircleShape,
                    containerColor = NeonPurple,
                    contentColor = Color.White,
                    modifier = Modifier
                        .border(2.dp, ElectricCyan, CircleShape)
                        .testTag("floating_voice_mic_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Command Assistant"
                    )
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Persistent Mini-Player when not on full Now Playing tab
                    if (uiState.currentDestination != MainDestination.NOW_PLAYING) {
                        GlassMiniPlayerBar(
                            playerState = playerState,
                            onOpenNowPlaying = { viewModel.navigateTo(MainDestination.NOW_PLAYING) },
                            onOpenEqualizer = { viewModel.navigateTo(MainDestination.EQUALIZER) },
                            onPlayPause = { viewModel.audioEngine.togglePlayPause() },
                            onPrevious = { viewModel.audioEngine.previousTrack() },
                            onNext = { viewModel.audioEngine.nextTrack() }
                        )
                    }

                    // Bottom Navigation Bar for Mobile / Compact screens
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = ObsidianSurface.copy(alpha = 0.95f),
                            tonalElevation = 8.dp
                        ) {
                            navItems.forEach { item ->
                                val selected = uiState.currentDestination == item.destination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.navigateTo(item.destination) },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ElectricCyan,
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = NeonPurple.copy(alpha = 0.28f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Side Navigation Rail on Desktop / Tablet viewports
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = ObsidianSurface.copy(alpha = 0.90f),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        navItems.forEach { item ->
                            val selected = uiState.currentDestination == item.destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.navigateTo(item.destination) },
                                icon = {
                                    Icon(imageVector = item.icon, contentDescription = item.label)
                                },
                                label = {
                                    Text(text = item.label, style = MaterialTheme.typography.labelSmall)
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = NeonPurple.copy(alpha = 0.28f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("${item.testTag}_rail")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (uiState.currentDestination) {
                        MainDestination.LIBRARY -> {
                            LibraryScreen(
                                uiState = uiState,
                                playerState = playerState,
                                allTracks = allTracks,
                                allPlaylists = allPlaylists,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onQuickFilterChange = viewModel::setQuickFilter,
                                onSubTabChange = viewModel::setLibrarySubTab,
                                onSelectAlbumFilter = viewModel::selectAlbumFilter,
                                onSelectArtistFilter = viewModel::selectArtistFilter,
                                onSelectPlaylist = viewModel::selectPlaylist,
                                onImportAudioClick = {
                                    openAudioFilesLauncher.launch(arrayOf("audio/*"))
                                },
                                onScanFolderClick = {
                                    openFolderLauncher.launch(null)
                                },
                                onCacheAllOfflineClick = viewModel::cacheAllTracksOffline,
                                onPlayTrack = { track, queue ->
                                    viewModel.audioEngine.playTrack(track, queue, autoStart = true)
                                },
                                onCacheTrackOffline = viewModel::cacheTrackForOffline,
                                onCreatePlaylist = viewModel::createNewPlaylist,
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onMoveTrackInPlaylist = viewModel::moveTrackInPlaylist,
                                onPlayPlaylist = viewModel::playPlaylist
                            )
                        }

                        MainDestination.NOW_PLAYING -> {
                            PlayerScreen(
                                playerState = playerState,
                                onPlayPause = { viewModel.audioEngine.togglePlayPause() },
                                onPrevious = { viewModel.audioEngine.previousTrack() },
                                onNext = { viewModel.audioEngine.nextTrack() },
                                onSeekTo = { viewModel.audioEngine.seekTo(it) },
                                onVolumeChange = { viewModel.audioEngine.setVolume(it) },
                                onToggleMute = { viewModel.audioEngine.toggleMute() },
                                onCycleLoopMode = { viewModel.audioEngine.cycleLoopMode() },
                                onToggleShuffle = { viewModel.audioEngine.toggleShuffle() },
                                onToggleAutoPlay = { viewModel.audioEngine.toggleContinuousAutoPlay() },
                                onCrossfadeChange = { viewModel.audioEngine.setCrossfadeSeconds(it) },
                                onVisualizerModeChange = { viewModel.setVisualizerMode(it) },
                                onOpenEqualizer = { viewModel.navigateTo(MainDestination.EQUALIZER) },
                                onOpenLyrics = { viewModel.navigateTo(MainDestination.LYRICS) }
                            )
                        }

                        MainDestination.EQUALIZER -> {
                            EqualizerScreen(
                                playerState = playerState,
                                customPresets = customPresets,
                                onEqEnabledChange = { viewModel.audioEngine.setEqEnabled(it) },
                                onBandGainChange = { idx, db -> viewModel.audioEngine.setEqBandGain(idx, db) },
                                onApplyPreset = { viewModel.audioEngine.applyEqPreset(it) },
                                onPreampChange = { viewModel.audioEngine.setPreampDb(it) },
                                onToggleBassBoost = { viewModel.audioEngine.toggleBassBoost() },
                                onBassBoostPercentChange = { viewModel.audioEngine.setBassBoostPercent(it) },
                                onToggleSpatialReverb = { viewModel.audioEngine.toggleSpatialReverb() },
                                onSaveCustomPreset = viewModel::saveCurrentEqAsCustomPreset,
                                onDeleteCustomPreset = viewModel::deleteCustomEqPreset
                            )
                        }

                        MainDestination.LYRICS -> {
                            LyricsScreen(
                                playerState = playerState,
                                onSeekTo = { viewModel.audioEngine.seekTo(it) },
                                onSaveLyrics = viewModel::updateLyricsForTrack
                            )
                        }

                        MainDestination.AI_ASSISTANT -> {
                            AiVoiceAssistantScreen(
                                uiState = uiState,
                                onTriggerMicVoice = triggerVoiceAssistant,
                                onStopMicVoice = viewModel::stopVoiceCapture,
                                onSubmitCommand = viewModel::submitNaturalLanguageCommand,
                                onToggleOfflinePrivacy = viewModel::toggleOfflinePrivacyMode,
                                onPlayTrackFromAi = { track, queue ->
                                    viewModel.audioEngine.playTrack(track, queue, autoStart = true)
                                },
                                onPlayPlaylistFromAi = viewModel::playPlaylist
                            )
                        }
                    }

                    // Floating Toast / Intent Confirmation Banner
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.statusBannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(cornerRadius = 14.dp, highlightCyan = true)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("status_feedback_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.statusBannerMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Banner",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Slide-out Conversational AI Voice Assistant Sheet (triggered from floating Mic FAB on any tab)
        if (uiState.isVoiceSheetOpen && uiState.currentDestination != MainDestination.AI_ASSISTANT) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setVoiceSheetOpen(false) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = ObsidianSurfaceElevated
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
                    AiVoiceAssistantScreen(
                        uiState = uiState,
                        onTriggerMicVoice = triggerVoiceAssistant,
                        onStopMicVoice = viewModel::stopVoiceCapture,
                        onSubmitCommand = viewModel::submitNaturalLanguageCommand,
                        onToggleOfflinePrivacy = viewModel::toggleOfflinePrivacyMode,
                        onPlayTrackFromAi = { track, queue ->
                            viewModel.audioEngine.playTrack(track, queue, autoStart = true)
                        },
                        onPlayPlaylistFromAi = viewModel::playPlaylist
                    )
                }
            }
        }
    }
}

@Composable
private fun ObsidianTopHeaderBar(
    activePreset: String,
    isPlaying: Boolean,
    onRestoreStudioTracks: () -> Unit,
    onOpenVoiceSheet: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(NeonPurple, ElectricCyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ObsidianVoid,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = "OBSIDIAN PULSE",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Local Media Hub • 10-Band DSP ($activePreset)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPlaying) ElectricCyan else TextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeonBadge(
                text = "AI VOICE",
                accentColor = ElectricCyan,
                modifier = Modifier
                    .clickable(onClick = onOpenVoiceSheet)
                    .testTag("top_open_voice_ai_badge")
            )
            IconButton(
                onClick = onRestoreStudioTracks,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(1.dp, NeonPurple.copy(alpha = 0.4f), CircleShape)
                    .testTag("restore_studio_tracks_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Verify / Rescan Studio Audio Library",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
