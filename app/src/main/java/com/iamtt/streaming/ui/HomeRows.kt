package com.iamtt.streaming.ui

import com.iamtt.streaming.data.Catalog
import com.iamtt.streaming.data.CatalogTitle
import com.iamtt.streaming.data.EpisodeEntry
import com.iamtt.streaming.data.MovieEntry
import com.iamtt.streaming.data.ShowEntry
import com.iamtt.streaming.data.TitleInfo
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.data.WatchProgress

enum class Kind { SHOWS, MOVIES }

/**
 * One poster on the home screen. Continue Watching cards also carry the video to resume
 * (and, for a show, which episode it is).
 */
data class HomeCard(
    val title: CatalogTitle,
    val resume: VideoFile? = null,
    val episode: EpisodeEntry? = null,
    val progress: WatchProgress? = null,
)

data class HomeRow(val title: String, val cards: List<HomeCard>)

data class Home(val featured: HomeCard?, val rows: List<HomeRow>, val isEmpty: Boolean)

/**
 * Arranges the library like a streaming app: what this profile is part-way through, what's new,
 * all TV shows, all movies, then a row per genre once artwork details have arrived.
 * Shows and movies are told apart by their file names, not by folder. [pick] varies the featured title.
 */
fun buildHome(
    catalog: Catalog,
    history: List<WatchProgress>,
    infos: Map<String, TitleInfo>,
    profileName: String,
    only: Kind? = null,
    pick: Int = 0,
): Home {
    val shows = if (only == Kind.MOVIES) emptyList() else catalog.shows
    val movies = if (only == Kind.SHOWS) emptyList() else catalog.movies
    val titles: List<CatalogTitle> = shows + movies
    val allowed = titles.map { it.id }.toSet()

    // One card per show: the most recently watched episode is the one to resume.
    val continueWatching = history.mapNotNull { p ->
        val (title, episode) = catalog.locate(p.videoId) ?: return@mapNotNull null
        if (title.id !in allowed) return@mapNotNull null
        HomeCard(title, resume = episode?.video ?: (title as MovieEntry).video, episode = episode, progress = p)
    }.distinctBy { it.title.id }.take(20)
    val recent = titles.sortedByDescending { it.addedAt }

    val rows = buildList {
        if (continueWatching.isNotEmpty()) add(HomeRow("Continue Watching for $profileName", continueWatching))
        if (titles.size > 6) add(HomeRow("New on IAMTT", recent.take(20).map { HomeCard(it) }))
        if (shows.isNotEmpty()) add(HomeRow("TV Shows", shows.sortedByDescending { it.addedAt }.map { HomeCard(it) }))
        if (movies.isNotEmpty()) add(HomeRow("Movies", movies.sortedByDescending { it.addedAt }.map { HomeCard(it) }))
        titles.flatMap { t -> infos[t.id]?.genres.orEmpty().map { g -> g to t } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size >= 3 }
            .entries.sortedByDescending { it.value.size }
            .take(6)
            .forEach { (genre, ts) -> add(HomeRow(genre, ts.map { HomeCard(it) })) }
    }
    // Feature something recent, preferring titles that already have artwork.
    val pool = recent.filter { infos[it.id]?.posterLarge != null }.ifEmpty { recent }.take(8)
    val featured = pool.getOrNull(pick.mod(pool.size.coerceAtLeast(1)))?.let { t ->
        continueWatching.firstOrNull { it.title.id == t.id } ?: HomeCard(t)
    }
    return Home(featured, rows, titles.isEmpty())
}

/** Which episode "Play" starts: the one this profile is part-way through, else the first. */
fun ShowEntry.nextUp(history: List<WatchProgress>): Pair<EpisodeEntry, WatchProgress?> {
    val ids = episodes.associateBy { it.video.id }
    history.firstOrNull { it.videoId in ids }?.let { return ids.getValue(it.videoId) to it }
    return episodes.first() to null
}

/** "S1:E3", as streaming apps label episodes. */
fun EpisodeEntry.label() = "S$season:E$episode"

/** "2026 · TV Series · Action, Adventure" or "2010 · Movie · Science-Fiction". */
fun CatalogTitle.metaLine(info: TitleInfo?): String {
    val kind = when (this) {
        is ShowEntry -> if (seasons.size > 1) "${seasons.size} Seasons" else "TV Series"
        is MovieEntry -> "Movie"
    }
    val genres = info?.genres.orEmpty().take(2).joinToString(", ")
    return listOfNotNull((info?.year ?: year)?.toString(), kind, genres.ifBlank { null }).joinToString(" · ")
}

/** How far into the resumable video this card is, 0..1, when known. */
fun HomeCard.watchedFraction(): Float? {
    val p = progress ?: return null
    val total = p.durationMs.takeIf { it > 0 } ?: resume?.durationMs ?: return null
    return if (total > 0) (p.positionMs.toFloat() / total).coerceIn(0f, 1f) else null
}
