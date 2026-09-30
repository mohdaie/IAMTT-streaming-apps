package com.iamtt.streaming.ui

import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.LibraryFolder
import com.iamtt.streaming.data.LibrarySnapshot
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.data.WatchProgress

data class HomeItem(val video: VideoFile, val folder: LibraryFolder, val progress: WatchProgress? = null)

data class HomeRow(val title: String, val items: List<HomeItem>)

data class Home(val featured: HomeItem?, val rows: List<HomeRow>, val isEmpty: Boolean)

/**
 * Arranges the library the way streaming apps do: what this profile is part-way through,
 * what was added lately, then one row per chosen folder. [only] limits it to Movies or TV Shows.
 * [pick] chooses which recent title gets the big featured spot, so it varies between visits.
 */
fun buildHome(
    snapshot: LibrarySnapshot,
    history: List<WatchProgress>,
    profileName: String,
    only: FolderType? = null,
    pick: Int = 0,
): Home {
    val scans = snapshot.scans.filter { only == null || it.folder.type == only }
    val all = scans.flatMap { scan -> scan.videos.map { HomeItem(it, scan.folder) } }
    val byId = all.associateBy { it.video.id }
    val continueWatching = history.mapNotNull { p -> byId[p.videoId]?.copy(progress = p) }.take(20)
    val recent = all.sortedByDescending { it.video.modifiedTime.orEmpty() }.take(20)

    val rows = buildList {
        if (continueWatching.isNotEmpty()) add(HomeRow("Continue Watching for $profileName", continueWatching))
        if (recent.isNotEmpty() && scans.size > 1) add(HomeRow("Recently Added", recent))
        scans.filter { it.videos.isNotEmpty() }.forEach { scan ->
            add(HomeRow(scan.folder.name, scan.videos.map { HomeItem(it, scan.folder) }))
        }
    }
    val candidates = recent.take(8)
    val featured = candidates.getOrNull(pick.mod(candidates.size.coerceAtLeast(1)))?.let { f ->
        continueWatching.firstOrNull { it.video.id == f.video.id } ?: f
    }
    return Home(featured, rows, all.isEmpty())
}

/** e.g. "Movies", "Movies · Hindi" or "TV Shows · Breaking Bad · Season 01" (no repeated names). */
fun HomeItem.subtitle(): String {
    val type = if (folder.type == FolderType.TV_SHOWS) "TV Shows" else "Movies"
    val parts = listOf(type, folder.name) + video.path.split('/').filter { it.isNotBlank() }
    return parts.distinctBy { it.lowercase() }.joinToString(" · ")
}
