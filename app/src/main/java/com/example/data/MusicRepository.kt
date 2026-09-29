package com.example.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.audio.StudioAudioSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {
    val allTracks: Flow<List<TrackEntity>> = musicDao.getAllTracks()
    val allPlaylists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
    val allCustomPresets: Flow<List<CustomEqPresetEntity>> = musicDao.getAllCustomPresets()

    suspend fun initializeDefaultLibraryIfNeeded() = withContext(Dispatchers.IO) {
        val currentTracks = musicDao.getAllTracksSnapshot()
        if (currentTracks.isEmpty()) {
            val synthesizedTracks = StudioAudioSynthesizer.ensureStudioTracksGenerated(context)
            val insertedIds = musicDao.insertTracks(synthesizedTracks)

            // Also seed 3 curated starter playlists if none exist
            val existingPlaylists = musicDao.getAllPlaylistsSnapshot()
            if (existingPlaylists.isEmpty() && insertedIds.size >= 6) {
                val synthIds = listOfNotNull(insertedIds.getOrNull(0), insertedIds.getOrNull(2), insertedIds.getOrNull(6))
                val chillIds = listOfNotNull(insertedIds.getOrNull(1), insertedIds.getOrNull(3), insertedIds.getOrNull(7))
                val ninetiesIds = listOfNotNull(insertedIds.getOrNull(4), insertedIds.getOrNull(5), insertedIds.getOrNull(7))

                musicDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Neon Cybernetics",
                        description = "High-energy synthwave & deep sub-bass telemetry",
                        trackIdsCsv = PlaylistEntity.encodeTrackIds(synthIds),
                        isAiGenerated = false
                    )
                )
                musicDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Late-Night Obsidian Chill",
                        description = "Warm Rhodes, acoustic harmonics, and ambient space",
                        trackIdsCsv = PlaylistEntity.encodeTrackIds(chillIds),
                        isAiGenerated = false
                    )
                )
                musicDao.insertPlaylist(
                    PlaylistEntity(
                        name = "90s Golden Vault",
                        description = "Classic 1990s boom-bap, tube overdrive, and chillwave",
                        trackIdsCsv = PlaylistEntity.encodeTrackIds(ninetiesIds),
                        isAiGenerated = false
                    )
                )
            }
        } else {
            // Ensure studio WAV files still exist on disk
            StudioAudioSynthesizer.ensureStudioTracksGenerated(context)
        }
    }

    suspend fun rescanAndRestoreStudioTracks(): Int = withContext(Dispatchers.IO) {
        val studioTracks = StudioAudioSynthesizer.ensureStudioTracksGenerated(context)
        val existingTitles = musicDao.getAllTracksSnapshot().map { it.title.lowercase() }.toSet()
        val missing = studioTracks.filter { it.title.lowercase() !in existingTitles }
        if (missing.isNotEmpty()) {
            musicDao.insertTracks(missing)
        }
        missing.size
    }

    /**
     * Imports user-selected audio URIs (.mp3, .wav, .flac, .m4a, .ogg), extracts real ID3 metadata
     * via MediaMetadataRetriever, and optionally caches them locally for Offline Mode.
     */
    suspend fun importAudioUris(
        uris: List<Uri>,
        cacheOfflineImmediately: Boolean = true
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<TrackEntity>()
        val cacheDir = File(context.filesDir, "offline_cache").apply { if (!exists()) mkdirs() }

        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Persistable permission may not be supported by all providers
            }

            val displayName = resolveDisplayName(uri) ?: "Imported_Track_${System.currentTimeMillis()}.mp3"
            val ext = displayName.substringAfterLast('.', "mp3").uppercase()
            val cleanBaseTitle = displayName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Untitled Audio" }

            var cachedFile: File? = null
            if (cacheOfflineImmediately) {
                try {
                    val safeName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                    val target = File(cacheDir, safeName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (target.exists() && target.length() > 0L) {
                        cachedFile = target
                    }
                } catch (_: Exception) {
                }
            }

            val retriever = MediaMetadataRetriever()
            var title = cleanBaseTitle
            var artist = "Local Artist"
            var album = "Local Storage Imports"
            var year = 2025
            var genre = "Imported"
            var durationMs = 180_000L
            var bitrateKbps = 320

            try {
                if (cachedFile != null) {
                    retriever.setDataSource(cachedFile.absolutePath)
                } else {
                    retriever.setDataSource(context, uri)
                }

                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }?.let { artist = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }?.let { album = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.take(4)?.toIntOrNull()?.let { year = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    ?.takeIf { it.isNotBlank() }?.let { genre = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.takeIf { it > 0L }?.let { durationMs = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.takeIf { it > 0 }?.let { bitrateKbps = (it / 1000).coerceAtLeast(64) }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            val estimatedBpm = when {
                genre.contains("electro", ignoreCase = true) || genre.contains("dance", ignoreCase = true) -> 132
                genre.contains("hip", ignoreCase = true) || genre.contains("rap", ignoreCase = true) -> 94
                genre.contains("rock", ignoreCase = true) -> 126
                genre.contains("chill", ignoreCase = true) || genre.contains("acoustic", ignoreCase = true) -> 84
                else -> 118
            }

            val defaultLrc = """
                [00:00.00] ♫ Playing $title — $artist
                [00:04.00] Album: $album ($year) • Format: $ext ${bitrateKbps}kbps
                [00:09.00] Processed through Obsidian 10-Band Parametric DSP
                [00:15.00] Tap the Edit LRC button above to paste custom synced lyrics
                [00:22.00] Offline Local Media Hub active
            """.trimIndent()

            val entity = TrackEntity(
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre,
                durationMs = durationMs,
                bpm = estimatedBpm,
                filePathOrUri = cachedFile?.absolutePath ?: uri.toString(),
                isOfflineCached = cachedFile != null,
                cachedFilePath = cachedFile?.absolutePath,
                coverArtKey = if (estimatedBpm >= 120) "cyber_synth" else "midnight_bass",
                bitrateKbps = bitrateKbps,
                formatExt = ext,
                lrcLyrics = defaultLrc
            )
            val id = musicDao.insertTrack(entity)
            imported.add(entity.copy(id = id))
        }
        imported
    }

    /**
     * Scans a folder URI selected via OpenDocumentTree for audio files (.mp3, .wav, .flac, .m4a, .ogg)
     * using standard Android DocumentsContract.
     */
    suspend fun scanDirectoryTree(treeUri: Uri): List<TrackEntity> = withContext(Dispatchers.IO) {
        val audioUris = mutableListOf<Uri>()
        val supportedExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac")

        fun traverseDocument(docId: String) {
            if (audioUris.size >= 40) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && audioUris.size < 40) {
                        val childId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: ""
                        val mime = cursor.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            traverseDocument(childId)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in supportedExts || mime.startsWith("audio/")) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                audioUris.add(fileUri)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            traverseDocument(rootDocId)
        } catch (_: Exception) {
        }

        if (audioUris.isEmpty()) emptyList() else importAudioUris(audioUris, cacheOfflineImmediately = true)
    }

    suspend fun toggleOfflineCacheForTrack(track: TrackEntity): TrackEntity = withContext(Dispatchers.IO) {
        if (track.isOfflineCached) {
            // If it's a studio synthesized file, keep it cached so playback always works; otherwise toggle badge
            val updated = track.copy(isOfflineCached = true)
            musicDao.updateTrack(updated)
            return@withContext updated
        } else {
            val cacheDir = File(context.filesDir, "offline_cache").apply { if (!exists()) mkdirs() }
            val safeName = "cached_${track.id}_${System.currentTimeMillis()}.${track.formatExt.lowercase()}"
            val target = File(cacheDir, safeName)
            try {
                val uri = Uri.parse(track.filePathOrUri)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                val updated = if (target.exists() && target.length() > 0L) {
                    track.copy(
                        isOfflineCached = true,
                        cachedFilePath = target.absolutePath,
                        filePathOrUri = target.absolutePath
                    )
                } else {
                    track.copy(isOfflineCached = true)
                }
                musicDao.updateTrack(updated)
                updated
            } catch (_: Exception) {
                val updated = track.copy(isOfflineCached = true)
                musicDao.updateTrack(updated)
                updated
            }
        }
    }

    suspend fun updateTrackLyrics(track: TrackEntity, newLrc: String) = withContext(Dispatchers.IO) {
        musicDao.updateTrack(track.copy(lrcLyrics = newLrc))
    }

    suspend fun createPlaylist(
        name: String,
        description: String,
        trackIds: List<Long>,
        isAiGenerated: Boolean = false
    ): PlaylistEntity = withContext(Dispatchers.IO) {
        val entity = PlaylistEntity(
            name = name.trim().ifBlank { "Untitled Playlist" },
            description = description.trim(),
            trackIdsCsv = PlaylistEntity.encodeTrackIds(trackIds.distinct()),
            isAiGenerated = isAiGenerated
        )
        val id = musicDao.insertPlaylist(entity)
        entity.copy(id = id)
    }

    suspend fun renamePlaylist(playlist: PlaylistEntity, newName: String, newDescription: String) =
        withContext(Dispatchers.IO) {
            musicDao.updatePlaylist(
                playlist.copy(
                    name = newName.trim().ifBlank { playlist.name },
                    description = newDescription.trim()
                )
            )
        }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        musicDao.deletePlaylistById(playlistId)
    }

    suspend fun addTrackToPlaylist(playlist: PlaylistEntity, trackId: Long) = withContext(Dispatchers.IO) {
        val current = playlist.trackIds().toMutableList()
        if (trackId !in current) {
            current.add(trackId)
            musicDao.updatePlaylist(playlist.copy(trackIdsCsv = PlaylistEntity.encodeTrackIds(current)))
        }
    }

    suspend fun removeTrackFromPlaylist(playlist: PlaylistEntity, trackId: Long) = withContext(Dispatchers.IO) {
        val current = playlist.trackIds().toMutableList()
        if (current.remove(trackId)) {
            musicDao.updatePlaylist(playlist.copy(trackIdsCsv = PlaylistEntity.encodeTrackIds(current)))
        }
    }

    suspend fun moveTrackInPlaylist(playlist: PlaylistEntity, fromIndex: Int, toIndex: Int) =
        withContext(Dispatchers.IO) {
            val current = playlist.trackIds().toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                musicDao.updatePlaylist(playlist.copy(trackIdsCsv = PlaylistEntity.encodeTrackIds(current)))
            }
        }

    suspend fun saveCustomEqPreset(
        name: String,
        bandsDb: List<Float>,
        preampDb: Float,
        bassBoostPercent: Int,
        spatialReverb: Boolean
    ) = withContext(Dispatchers.IO) {
        musicDao.insertCustomPreset(
            CustomEqPresetEntity(
                name = name.trim().ifBlank { "Custom EQ" },
                gainsDbCsv = bandsDb.joinToString(",") { "%.1f".format(it) },
                preampDb = preampDb,
                bassBoostPercent = bassBoostPercent,
                spatialReverb = spatialReverb
            )
        )
    }

    suspend fun deleteCustomEqPreset(id: Long) = withContext(Dispatchers.IO) {
        musicDao.deleteCustomPreset(id)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
