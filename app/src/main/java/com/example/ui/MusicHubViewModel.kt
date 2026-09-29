package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiChatMessage
import com.example.ai.VoiceAssistantEngine
import com.example.audio.PulseAudioEngine
import com.example.audio.VisualizerMode
import com.example.data.AppDatabase
import com.example.data.CustomEqPresetEntity
import com.example.data.EqPresetProfile
import com.example.data.MusicRepository
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainDestination {
    LIBRARY,
    NOW_PLAYING,
    EQUALIZER,
    LYRICS,
    AI_ASSISTANT
}

enum class LibrarySubTab {
    ALL_SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}

enum class QuickLibraryFilter {
    ALL,
    HIGH_BPM,
    CHILL_ACOUSTIC,
    NINETIES,
    OFFLINE_CACHED
}

data class MusicHubUiState(
    val currentDestination: MainDestination = MainDestination.LIBRARY,
    val librarySubTab: LibrarySubTab = LibrarySubTab.ALL_SONGS,
    val searchQuery: String = "",
    val quickFilter: QuickLibraryFilter = QuickLibraryFilter.ALL,
    val selectedAlbumFilter: String? = null,
    val selectedArtistFilter: String? = null,
    val selectedPlaylistId: Long? = null,
    val statusBannerMessage: String? = null,
    // AI Voice Assistant & Discovery state
    val isVoiceSheetOpen: Boolean = false,
    val isListeningVoice: Boolean = false,
    val liveVoicePartialText: String = "",
    val voiceRmsLevel: Float = 0.1f,
    val offlinePrivacyMode: Boolean = true,
    val chatMessages: List<AiChatMessage> = listOf(
        AiChatMessage(
            isUser = false,
            text = "Welcome to Obsidian Pulse AI. Tap the microphone or select any natural language command to control playback, sculpt the 10-band DSP equalizer, filter by BPM/mood, or auto-generate custom playlists from your local library.",
            intentBadge = "SYSTEM_READY",
            engineUsed = "Offline Local NLP"
        )
    )
)

class MusicHubViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository = MusicRepository(application, database.musicDao())
    val audioEngine = PulseAudioEngine(application, viewModelScope)
    private val voiceAssistant = VoiceAssistantEngine(application, audioEngine, repository)

    val allTracks: StateFlow<List<TrackEntity>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customEqPresets: StateFlow<List<CustomEqPresetEntity>> = repository.allCustomPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState = audioEngine.state

    private val _uiState = MutableStateFlow(MusicHubUiState())
    val uiState: StateFlow<MusicHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDefaultLibraryIfNeeded()
        }
        viewModelScope.launch {
            allTracks.collect { tracks ->
                if (tracks.isNotEmpty()) {
                    audioEngine.setQueueIfEmpty(tracks)
                }
            }
        }
    }

    fun navigateTo(destination: MainDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
    }

    fun setLibrarySubTab(subTab: LibrarySubTab) {
        _uiState.update {
            it.copy(
                librarySubTab = subTab,
                selectedAlbumFilter = null,
                selectedArtistFilter = null
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setQuickFilter(filter: QuickLibraryFilter) {
        _uiState.update { it.copy(quickFilter = filter) }
    }

    fun selectAlbumFilter(album: String?) {
        _uiState.update { it.copy(selectedAlbumFilter = album) }
    }

    fun selectArtistFilter(artist: String?) {
        _uiState.update { it.copy(selectedArtistFilter = artist) }
    }

    fun selectPlaylist(playlistId: Long?) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId) }
    }

    fun showBanner(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // --- Local File Scanning & Import Actions ---

    fun importLocalAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importAudioUris(uris, cacheOfflineImmediately = true)
            if (imported.isNotEmpty()) {
                audioEngine.playTrack(imported.first(), allTracks.value.ifEmpty { imported }, autoStart = true)
                showBanner("Imported & cached ${imported.size} local audio file(s) with ID3 metadata.")
            }
        }
    }

    fun scanFolderUri(treeUri: Uri) {
        viewModelScope.launch {
            val scanned = repository.scanDirectoryTree(treeUri)
            if (scanned.isNotEmpty()) {
                audioEngine.playTrack(scanned.first(), allTracks.value.ifEmpty { scanned }, autoStart = true)
                showBanner("Scanned folder: added ${scanned.size} audio track(s) to your local library.")
            } else {
                showBanner("No supported audio files (.mp3, .wav, .flac, .m4a) found in selected folder.")
            }
        }
    }

    fun restoreStudioTracks() {
        viewModelScope.launch {
            val restored = repository.rescanAndRestoreStudioTracks()
            if (restored > 0) {
                showBanner("Restored $restored studio master WAV track(s).")
            } else {
                showBanner("All 8 studio master WAV tracks are verified and cached locally.")
            }
        }
    }

    fun cacheTrackForOffline(track: TrackEntity) {
        viewModelScope.launch {
            repository.toggleOfflineCacheForTrack(track)
            showBanner("Track \"${track.title}\" cached in local storage for offline playback.")
        }
    }

    fun cacheAllTracksOffline() {
        viewModelScope.launch {
            val list = allTracks.value
            list.forEach { repository.toggleOfflineCacheForTrack(it) }
            showBanner("All ${list.size} tracks verified in local offline cache.")
        }
    }

    // --- Playlist Actions ---

    fun createNewPlaylist(name: String, description: String, initialTrackIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            val created = repository.createPlaylist(name, description, initialTrackIds, isAiGenerated = false)
            _uiState.update {
                it.copy(
                    librarySubTab = LibrarySubTab.PLAYLISTS,
                    selectedPlaylistId = created.id,
                    statusBannerMessage = "Created playlist \"${created.name}\"."
                )
            }
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String, newDescription: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlist, newName, newDescription)
            showBanner("Updated playlist \"${newName.trim()}\".")
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            _uiState.update {
                it.copy(
                    selectedPlaylistId = if (it.selectedPlaylistId == playlist.id) null else it.selectedPlaylistId,
                    statusBannerMessage = "Deleted playlist \"${playlist.name}\"."
                )
            }
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistEntity, track: TrackEntity) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlist, track.id)
            showBanner("Added \"${track.title}\" to ${playlist.name}.")
        }
    }

    fun removeTrackFromPlaylist(playlist: PlaylistEntity, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlist, trackId)
        }
    }

    fun moveTrackInPlaylist(playlist: PlaylistEntity, fromIdx: Int, toIdx: Int) {
        viewModelScope.launch {
            repository.moveTrackInPlaylist(playlist, fromIdx, toIdx)
        }
    }

    fun playPlaylist(playlist: PlaylistEntity) {
        val trackMap = allTracks.value.associateBy { it.id }
        val orderedTracks = playlist.trackIds().mapNotNull { trackMap[it] }
        if (orderedTracks.isNotEmpty()) {
            audioEngine.playTrack(orderedTracks.first(), orderedTracks, autoStart = true)
            showBanner("Playing playlist \"${playlist.name}\" (${orderedTracks.size} tracks).")
        } else {
            showBanner("Playlist \"${playlist.name}\" has no tracks yet. Add songs first!")
        }
    }

    // --- Lyrics & Custom EQ Actions ---

    fun updateLyricsForTrack(track: TrackEntity, newLrc: String) {
        viewModelScope.launch {
            repository.updateTrackLyrics(track, newLrc)
            showBanner("Saved synchronized LRC lyrics for \"${track.title}\".")
        }
    }

    fun saveCurrentEqAsCustomPreset(name: String) {
        val s = playerState.value
        viewModelScope.launch {
            repository.saveCustomEqPreset(
                name = name,
                bandsDb = s.eqBandsDb,
                preampDb = s.preampDb,
                bassBoostPercent = if (s.bassBoostEnabled) s.bassBoostPercent else 0,
                spatialReverb = s.spatialReverbEnabled
            )
            audioEngine.applyEqPreset(
                EqPresetProfile(
                    name = name.trim().ifBlank { "Custom EQ" },
                    bandsDb = s.eqBandsDb,
                    preampDb = s.preampDb,
                    bassBoostPercent = if (s.bassBoostEnabled) s.bassBoostPercent else 0,
                    spatialReverb = s.spatialReverbEnabled
                )
            )
            showBanner("Saved custom 10-band EQ preset \"$name\".")
        }
    }

    fun deleteCustomEqPreset(preset: CustomEqPresetEntity) {
        viewModelScope.launch {
            repository.deleteCustomEqPreset(preset.id)
            showBanner("Deleted custom preset \"${preset.name}\".")
        }
    }

    // --- AI Voice Assistant & Natural Language Discovery ---

    fun setVoiceSheetOpen(open: Boolean) {
        if (!open) {
            stopVoiceCapture()
        }
        _uiState.update { it.copy(isVoiceSheetOpen = open) }
    }

    fun toggleOfflinePrivacyMode() {
        _uiState.update {
            val next = !it.offlinePrivacyMode
            it.copy(
                offlinePrivacyMode = next,
                statusBannerMessage = if (next) {
                    "Offline Privacy Mode enabled: all commands processed strictly on-device."
                } else {
                    "Hybrid Cloud AI + Local NLP enabled."
                }
            )
        }
    }

    fun startVoiceCapture() {
        _uiState.update {
            it.copy(
                isListeningVoice = true,
                liveVoicePartialText = "Listening for voice command…",
                isVoiceSheetOpen = true
            )
        }
        voiceAssistant.startVoiceListening(
            onPartialTranscript = { partial ->
                _uiState.update { it.copy(liveVoicePartialText = partial) }
            },
            onFinalTranscript = { finalTranscript ->
                _uiState.update {
                    it.copy(
                        isListeningVoice = false,
                        liveVoicePartialText = finalTranscript
                    )
                }
                submitNaturalLanguageCommand(finalTranscript)
            },
            onRmsChanged = { rms ->
                _uiState.update { it.copy(voiceRmsLevel = rms) }
            },
            onError = { errMsg ->
                _uiState.update {
                    it.copy(
                        isListeningVoice = false,
                        liveVoicePartialText = "",
                        statusBannerMessage = errMsg
                    )
                }
            }
        )
    }

    fun stopVoiceCapture() {
        voiceAssistant.stopVoiceListening()
        _uiState.update { it.copy(isListeningVoice = false, liveVoicePartialText = "") }
    }

    fun submitNaturalLanguageCommand(commandText: String) {
        val cleaned = commandText.trim()
        if (cleaned.isEmpty()) return

        val userMsg = AiChatMessage(
            isUser = true,
            text = cleaned
        )
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                liveVoicePartialText = ""
            )
        }

        viewModelScope.launch {
            val outcome = voiceAssistant.processNaturalCommand(
                rawCommand = cleaned,
                allTracks = allTracks.value,
                offlinePrivacyMode = _uiState.value.offlinePrivacyMode
            )
            val assistantMsg = AiChatMessage(
                isUser = false,
                text = outcome.responseText,
                intentBadge = outcome.intentBadge,
                engineUsed = outcome.engineUsed,
                matchedTracks = outcome.matchedTracks,
                createdPlaylist = outcome.createdPlaylist
            )
            _uiState.update { state ->
                state.copy(
                    chatMessages = state.chatMessages + assistantMsg,
                    searchQuery = outcome.filterQueryToApply ?: state.searchQuery,
                    statusBannerMessage = outcome.responseText
                )
            }
        }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        audioEngine.setVisualizerMode(mode)
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistant.stopVoiceListening()
        audioEngine.releaseAll()
    }
}
