package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlayerEngineState
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.LibrarySubTab
import com.example.ui.MusicHubUiState
import com.example.ui.QuickLibraryFilter
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

@Composable
fun LibraryScreen(
    uiState: MusicHubUiState,
    playerState: PlayerEngineState,
    allTracks: List<TrackEntity>,
    allPlaylists: List<PlaylistEntity>,
    onSearchQueryChange: (String) -> Unit,
    onQuickFilterChange: (QuickLibraryFilter) -> Unit,
    onSubTabChange: (LibrarySubTab) -> Unit,
    onSelectAlbumFilter: (String?) -> Unit,
    onSelectArtistFilter: (String?) -> Unit,
    onSelectPlaylist: (Long?) -> Unit,
    onImportAudioClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onCacheAllOfflineClick: () -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onCacheTrackOffline: (TrackEntity) -> Unit,
    onCreatePlaylist: (String, String, List<Long>) -> Unit,
    onRenamePlaylist: (PlaylistEntity, String, String) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onAddTrackToPlaylist: (PlaylistEntity, TrackEntity) -> Unit,
    onRemoveTrackFromPlaylist: (PlaylistEntity, Long) -> Unit,
    onMoveTrackInPlaylist: (PlaylistEntity, Int, Int) -> Unit,
    onPlayPlaylist: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var trackToAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }

    // Filter tracks by search query + quick filter + album/artist drilldown
    val filteredTracks = remember(
        allTracks,
        uiState.searchQuery,
        uiState.quickFilter,
        uiState.selectedAlbumFilter,
        uiState.selectedArtistFilter
    ) {
        allTracks.filter { track ->
            val q = uiState.searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                track.album.lowercase().contains(q) ||
                track.genre.lowercase().contains(q) ||
                track.bpm.toString().contains(q) ||
                track.year.toString().contains(q)

            val matchesQuick = when (uiState.quickFilter) {
                QuickLibraryFilter.ALL -> true
                QuickLibraryFilter.HIGH_BPM -> track.bpm >= 125
                QuickLibraryFilter.CHILL_ACOUSTIC -> track.bpm <= 105 ||
                    track.genre.contains("chill", ignoreCase = true) ||
                    track.genre.contains("acoustic", ignoreCase = true)
                QuickLibraryFilter.NINETIES -> track.year in 1990..1999
                QuickLibraryFilter.OFFLINE_CACHED -> track.isOfflineCached
            }

            val matchesAlbum = uiState.selectedAlbumFilter == null ||
                track.album.equals(uiState.selectedAlbumFilter, ignoreCase = true)
            val matchesArtist = uiState.selectedArtistFilter == null ||
                track.artist.equals(uiState.selectedArtistFilter, ignoreCase = true)

            matchesQuery && matchesQuick && matchesAlbum && matchesArtist
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Dark Glassmorphism Hero Banner + Local Scanner Actions
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(NeonPurple, ElectricCyan)),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_obsidian),
                    contentDescription = "Obsidian Pulse Studio Hub",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianVoid.copy(alpha = 0.45f),
                                    ObsidianVoid.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeonBadge(text = "AD-FREE STUDIO HUB", accentColor = ElectricCyan)
                            NeonBadge(
                                text = "${allTracks.count { it.isOfflineCached }}/${allTracks.size} OFFLINE READY",
                                accentColor = NeonEmerald
                            )
                        }

                        Column {
                            Text(
                                text = "Local Media & ID3 Scanner",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supports .MP3 • .WAV • .FLAC • .M4A with instant ID3 metadata & offline caching",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onImportAudioClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonPurple,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_audio_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Files", style = MaterialTheme.typography.labelLarge)
                            }

                            FilledTonalButton(
                                onClick = onScanFolderClick,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = ElectricCyan.copy(alpha = 0.18f),
                                    contentColor = ElectricCyan
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Folder", style = MaterialTheme.typography.labelLarge)
                            }

                            IconButton(
                                onClick = onCacheAllOfflineClick,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianSurfaceElevated)
                                    .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .testTag("cache_all_offline_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Cache All Tracks for Offline Playback",
                                    tint = NeonEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Fast Client-Side Search & Smart Filter Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 18.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Search by title, artist, album, genre, year, or BPM…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Library",
                            tint = ElectricCyan
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = NeonPurple.copy(alpha = 0.35f),
                        focusedContainerColor = ObsidianVoid.copy(alpha = 0.65f),
                        unfocusedContainerColor = ObsidianVoid.copy(alpha = 0.45f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                // Quick filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickFilterChipItem(
                        label = "All Tracks (${allTracks.size})",
                        selected = uiState.quickFilter == QuickLibraryFilter.ALL,
                        onClick = { onQuickFilterChange(QuickLibraryFilter.ALL) },
                        tag = "filter_all"
                    )
                    QuickFilterChipItem(
                        label = "High BPM (125+)",
                        selected = uiState.quickFilter == QuickLibraryFilter.HIGH_BPM,
                        onClick = { onQuickFilterChange(QuickLibraryFilter.HIGH_BPM) },
                        tag = "filter_high_bpm"
                    )
                    QuickFilterChipItem(
                        label = "Chill & Acoustic",
                        selected = uiState.quickFilter == QuickLibraryFilter.CHILL_ACOUSTIC,
                        onClick = { onQuickFilterChange(QuickLibraryFilter.CHILL_ACOUSTIC) },
                        tag = "filter_chill"
                    )
                    QuickFilterChipItem(
                        label = "90s Classics",
                        selected = uiState.quickFilter == QuickLibraryFilter.NINETIES,
                        onClick = { onQuickFilterChange(QuickLibraryFilter.NINETIES) },
                        tag = "filter_90s"
                    )
                    QuickFilterChipItem(
                        label = "Offline Cached",
                        selected = uiState.quickFilter == QuickLibraryFilter.OFFLINE_CACHED,
                        onClick = { onQuickFilterChange(QuickLibraryFilter.OFFLINE_CACHED) },
                        tag = "filter_offline"
                    )
                }
            }
        }

        // 3. Categorized Audio Library View Tabs: All Songs | Albums | Artists | Playlists
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 16.dp)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LibraryCategoryTabButton(
                    title = "All Songs",
                    selected = uiState.librarySubTab == LibrarySubTab.ALL_SONGS,
                    onClick = { onSubTabChange(LibrarySubTab.ALL_SONGS) },
                    modifier = Modifier.weight(1f),
                    tag = "tab_all_songs"
                )
                LibraryCategoryTabButton(
                    title = "Albums",
                    selected = uiState.librarySubTab == LibrarySubTab.ALBUMS,
                    onClick = { onSubTabChange(LibrarySubTab.ALBUMS) },
                    modifier = Modifier.weight(1f),
                    tag = "tab_albums"
                )
                LibraryCategoryTabButton(
                    title = "Artists",
                    selected = uiState.librarySubTab == LibrarySubTab.ARTISTS,
                    onClick = { onSubTabChange(LibrarySubTab.ARTISTS) },
                    modifier = Modifier.weight(1f),
                    tag = "tab_artists"
                )
                LibraryCategoryTabButton(
                    title = "Playlists",
                    selected = uiState.librarySubTab == LibrarySubTab.PLAYLISTS,
                    onClick = { onSubTabChange(LibrarySubTab.PLAYLISTS) },
                    modifier = Modifier.weight(1f),
                    tag = "tab_playlists"
                )
            }
        }

        // Active Drilldown Breadcrumb if Album or Artist filter is selected
        if (uiState.selectedAlbumFilter != null || uiState.selectedArtistFilter != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(cornerRadius = 14.dp, highlightCyan = true)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.selectedAlbumFilter != null) {
                            "Album: ${uiState.selectedAlbumFilter} (${filteredTracks.size} tracks)"
                        } else {
                            "Artist: ${uiState.selectedArtistFilter} (${filteredTracks.size} tracks)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            onSelectAlbumFilter(null)
                            onSelectArtistFilter(null)
                        }
                    ) {
                        Text("Show All", color = NeonPurple)
                    }
                }
            }
        }

        // 4. Content for Selected Library View
        when (uiState.librarySubTab) {
            LibrarySubTab.ALL_SONGS -> {
                if (filteredTracks.isEmpty()) {
                    item {
                        EmptyLibraryStateCard(
                            message = "No tracks match your current filter. Clear the search bar or import local audio files.",
                            onResetFilter = {
                                onSearchQueryChange("")
                                onQuickFilterChange(QuickLibraryFilter.ALL)
                            }
                        )
                    }
                } else {
                    items(filteredTracks, key = { it.id }) { track ->
                        TrackGlassRowCard(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                            onPlayClick = { onPlayTrack(track, filteredTracks) },
                            onAddToPlaylistClick = { trackToAddToPlaylist = track },
                            onOfflineCacheClick = { onCacheTrackOffline(track) }
                        )
                    }
                }
            }

            LibrarySubTab.ALBUMS -> {
                if (uiState.selectedAlbumFilter != null) {
                    items(filteredTracks, key = { it.id }) { track ->
                        TrackGlassRowCard(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                            onPlayClick = { onPlayTrack(track, filteredTracks) },
                            onAddToPlaylistClick = { trackToAddToPlaylist = track },
                            onOfflineCacheClick = { onCacheTrackOffline(track) }
                        )
                    }
                } else {
                    val albumsGrouped = allTracks.groupBy { it.album }
                    items(albumsGrouped.entries.toList(), key = { it.key }) { (albumName, tracksInAlbum) ->
                        val rep = tracksInAlbum.first()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(cornerRadius = 18.dp)
                                .clickable { onSelectAlbumFilter(albumName) }
                                .padding(14.dp)
                                .testTag("album_card_${albumName.lowercase().replace(' ', '_')}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(
                                coverArtKey = rep.coverArtKey,
                                contentDescription = albumName,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = albumName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${rep.artist} • ${rep.year} • ${tracksInAlbum.size} Track(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                NeonBadge(text = rep.genre, accentColor = ElectricCyan)
                            }
                            IconButton(
                                onClick = { onPlayTrack(tracksInAlbum.first(), tracksInAlbum) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Album $albumName",
                                    tint = ElectricCyan
                                )
                            }
                        }
                    }
                }
            }

            LibrarySubTab.ARTISTS -> {
                if (uiState.selectedArtistFilter != null) {
                    items(filteredTracks, key = { it.id }) { track ->
                        TrackGlassRowCard(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                            onPlayClick = { onPlayTrack(track, filteredTracks) },
                            onAddToPlaylistClick = { trackToAddToPlaylist = track },
                            onOfflineCacheClick = { onCacheTrackOffline(track) }
                        )
                    }
                } else {
                    val artistsGrouped = allTracks.groupBy { it.artist }
                    items(artistsGrouped.entries.toList(), key = { it.key }) { (artistName, artistTracks) ->
                        val avgBpm = artistTracks.map { it.bpm }.average().toInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(cornerRadius = 18.dp)
                                .clickable { onSelectArtistFilter(artistName) }
                                .padding(14.dp)
                                .testTag("artist_card_${artistName.lowercase().replace(' ', '_')}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(NeonPurple, ElectricCyan))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = artistName,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${artistTracks.size} Track(s) • Avg ${avgBpm} BPM • ${artistTracks.map { it.genre }.distinct().joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onPlayTrack(artistTracks.first(), artistTracks) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Artist $artistName",
                                    tint = ElectricCyan
                                )
                            }
                        }
                    }
                }
            }

            LibrarySubTab.PLAYLISTS -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom & AI Playlists (${allPlaylists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showCreatePlaylistDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            modifier = Modifier.testTag("create_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Playlist", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                items(allPlaylists, key = { it.id }) { playlist ->
                    val isExpanded = uiState.selectedPlaylistId == playlist.id
                    val trackMap = allTracks.associateBy { it.id }
                    val playlistTracks = playlist.trackIds().mapNotNull { trackMap[it] }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassPanel(cornerRadius = 18.dp, highlightCyan = isExpanded)
                            .padding(14.dp)
                            .testTag("playlist_card_${playlist.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPlaylist(if (isExpanded) null else playlist.id)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            if (playlist.isAiGenerated) {
                                                listOf(ElectricCyan, NeonPurple)
                                            } else {
                                                listOf(NeonPurple, NeonMagenta)
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = playlist.name,
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (playlist.isAiGenerated) {
                                        NeonBadge(text = "AI MIX", accentColor = ElectricCyan)
                                    }
                                }
                                Text(
                                    text = "${playlistTracks.size} tracks • ${playlist.description.ifBlank { "Custom studio playlist" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 2
                                )
                            }

                            IconButton(
                                onClick = { onPlayPlaylist(playlist) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("play_playlist_${playlist.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play ${playlist.name}",
                                    tint = ElectricCyan
                                )
                            }

                            IconButton(
                                onClick = { playlistToRename = playlist },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename ${playlist.name}",
                                    tint = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { onDeletePlaylist(playlist) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete ${playlist.name}",
                                    tint = NeonMagenta
                                )
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (playlistTracks.isEmpty()) {
                                Text(
                                    text = "No tracks in this playlist yet. Tap the '+' icon on any song in All Songs to add it here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {
                                playlistTracks.forEachIndexed { idx, track ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ObsidianVoid.copy(alpha = 0.55f))
                                            .clickable { onPlayTrack(track, playlistTracks) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ElectricCyan,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${track.artist} • ${track.bpm} BPM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (idx > 0) onMoveTrackInPlaylist(playlist, idx, idx - 1)
                                            },
                                            enabled = idx > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Move Track Up",
                                                tint = if (idx > 0) TextSecondary else TextMuted.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (idx < playlistTracks.lastIndex) {
                                                    onMoveTrackInPlaylist(playlist, idx, idx + 1)
                                                }
                                            },
                                            enabled = idx < playlistTracks.lastIndex
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Move Track Down",
                                                tint = if (idx < playlistTracks.lastIndex) TextSecondary else TextMuted.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRemoveTrackFromPlaylist(playlist, track.id) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Remove from Playlist",
                                                tint = NeonMagenta,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var nameInput by remember { mutableStateOf("") }
        var descInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = ObsidianSurfaceElevated,
            title = {
                Text("Create Custom Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Description / Vibe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(
                            nameInput.ifBlank { "Obsidian Mix" },
                            descInput.ifBlank { "Custom curated playlist" },
                            allTracks.take(3).map { it.id }
                        )
                        showCreatePlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    modifier = Modifier.testTag("confirm_create_playlist_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Rename Playlist Dialog
    playlistToRename?.let { target ->
        var nameInput by remember(target) { mutableStateOf(target.name) }
        var descInput by remember(target) { mutableStateOf(target.description) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            containerColor = ObsidianSurfaceElevated,
            title = {
                Text("Rename Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenamePlaylist(target, nameInput, descInput)
                        playlistToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Single-click Add Track to Playlist Modal
    trackToAddToPlaylist?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToAddToPlaylist = null },
            containerColor = ObsidianSurfaceElevated,
            title = {
                Text(
                    "Add \"${track.title}\" to Playlist",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allPlaylists.isEmpty()) {
                        Text(
                            "No playlists found. Create one below!",
                            color = TextSecondary
                        )
                    } else {
                        allPlaylists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianSurface)
                                    .clickable {
                                        onAddTrackToPlaylist(pl, track)
                                        trackToAddToPlaylist = null
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pl.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                NeonBadge(text = "${pl.trackIds().size} tracks", accentColor = ElectricCyan)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        trackToAddToPlaylist = null
                        showCreatePlaylistDialog = true
                    }
                ) {
                    Text("+ New Playlist", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToAddToPlaylist = null }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TrackGlassRowCard(
    track: TrackEntity,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onOfflineCacheClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(
                cornerRadius = 18.dp,
                highlightCyan = isCurrentTrack
            )
            .clickable(onClick = onPlayClick)
            .padding(12.dp)
            .testTag("track_item_card_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            CoverArtImage(
                coverArtKey = track.coverArtKey,
                contentDescription = track.title,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (isCurrentTrack) ElectricCyan else NeonPurple.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianVoid.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Active Track",
                        tint = ElectricCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentTrack) ElectricCyan else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${track.artist} • ${track.album} (${track.year})",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonBadge(text = track.genre, accentColor = NeonPurple)
                NeonBadge(text = "${track.bpm} BPM", accentColor = ElectricCyan)
                Text(
                    text = "${track.formatExt} • ${formatDurationMs(track.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        IconButton(
            onClick = onOfflineCacheClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("cache_track_button_${track.id}")
        ) {
            Icon(
                imageVector = if (track.isOfflineCached) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                contentDescription = if (track.isOfflineCached) "Cached Offline" else "Cache for Offline",
                tint = if (track.isOfflineCached) NeonEmerald else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onAddToPlaylistClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("add_to_playlist_button_${track.id}")
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistAdd,
                contentDescription = "Add ${track.title} to Playlist",
                tint = ElectricCyan
            )
        }
    }
}

@Composable
private fun QuickFilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) NeonPurple.copy(alpha = 0.25f) else ObsidianSurface
            )
            .border(
                width = 1.dp,
                color = if (selected) ElectricCyan else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ElectricCyan else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun LibraryCategoryTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(NeonPurple, ElectricCyan.copy(alpha = 0.85f)))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyLibraryStateCard(
    message: String,
    onResetFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 18.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        OutlinedButton(onClick = onResetFilter) {
            Text("Reset Filters", color = ElectricCyan)
        }
    }
}
