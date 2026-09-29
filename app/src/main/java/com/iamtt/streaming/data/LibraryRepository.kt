package com.iamtt.streaming.data

import android.content.Context
import com.iamtt.streaming.drive.DriveClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class LibraryState(
    val snapshot: LibrarySnapshot = LibrarySnapshot(),
    val scanning: Boolean = false,
    val scanningFolder: String? = null,
    val foundSoFar: Int = 0,
    val lastError: String? = null,
)

/**
 * Holds the scanned library and caches it on disk so the TV shows your titles
 * instantly on the next launch while a fresh scan runs in the background.
 */
class LibraryRepository(
    context: Context,
    private val drive: DriveClient,
    private val config: ConfigStore,
    private val scope: CoroutineScope,
) {
    private val cacheFile = File(context.filesDir, "library.json")
    private val _state = MutableStateFlow(LibraryState(snapshot = loadCache()))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private fun loadCache(): LibrarySnapshot = runCatching {
        if (cacheFile.exists()) AppJson.decodeFromString(LibrarySnapshot.serializer(), cacheFile.readText())
        else LibrarySnapshot()
    }.getOrDefault(LibrarySnapshot())

    private fun saveCache(snapshot: LibrarySnapshot) {
        runCatching { cacheFile.writeText(AppJson.encodeToString(LibrarySnapshot.serializer(), snapshot)) }
    }

    /**
     * Scans every chosen folder (or just [onlyFolderId]). Folders not chosen are never touched.
     * Requests queue up one after another; a request already waiting in the queue is not added twice.
     */
    fun rescan(onlyFolderId: String? = null) {
        val key = onlyFolderId ?: "*"
        if (!queued.add(key)) return
        scope.launch {
            scanMutex.withLock {
                queued.remove(key)
                runScan(onlyFolderId)
            }
        }
    }

    private val scanMutex = Mutex()
    private val queued = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun runScan(onlyFolderId: String?) {
        run {
            val cfg = config.current
            if (!cfg.hasKey) return
            val targets = cfg.folders.filter { onlyFolderId == null || it.id == onlyFolderId }
            _state.update { it.copy(scanning = true, lastError = null, foundSoFar = 0) }
            var scans = _state.value.snapshot.scans
            for (folder in targets) {
                _state.update { it.copy(scanningFolder = folder.name, foundSoFar = 0) }
                val result = try {
                    val videos = drive.scan(folder) { n -> _state.update { it.copy(foundSoFar = n) } }
                    FolderScan(folder, videos, System.currentTimeMillis())
                } catch (e: Exception) {
                    val previous = scans.firstOrNull { it.folder.id == folder.id }
                    _state.update { it.copy(lastError = "${folder.name}: ${e.message}") }
                    FolderScan(folder, previous?.videos.orEmpty(), System.currentTimeMillis(), e.message)
                }
                scans = scans.filterNot { it.folder.id == folder.id } + result
                publish(scans)
            }
            publish(scans)
            _state.update { it.copy(scanning = false, scanningFolder = null) }
        }
    }

    /** Keeps the cached library in step with the folder list (e.g. after one is removed). */
    fun syncWithConfig() {
        publish(_state.value.snapshot.scans)
    }

    private fun publish(scans: List<FolderScan>) {
        val chosen = config.current.folders
        val ordered = chosen.mapNotNull { f ->
            scans.firstOrNull { it.folder.id == f.id }?.copy(folder = f)
        }
        val snapshot = LibrarySnapshot(ordered)
        _state.update { it.copy(snapshot = snapshot) }
        saveCache(snapshot)
    }
}
