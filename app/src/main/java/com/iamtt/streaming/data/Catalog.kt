package com.iamtt.streaming.data

/** One episode file, with its place in the show. */
data class EpisodeEntry(val video: VideoFile, val season: Int, val episode: Int, val title: String?)

/** A show or a movie: what the home screen shows as one poster. */
sealed interface CatalogTitle {
    /** Stable id for navigation and the artwork cache, e.g. "show:lanterns" or "movie:inception|2010". */
    val id: String
    val name: String
    val year: Int?
    /** When something in it was last added (Drive's modified time), for "New on IAMTT". */
    val addedAt: String
}

data class ShowEntry(
    override val id: String,
    override val name: String,
    override val year: Int?,
    /** Sorted by season, then episode. */
    val episodes: List<EpisodeEntry>,
) : CatalogTitle {
    override val addedAt: String get() = episodes.maxOf { it.video.modifiedTime.orEmpty() }
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
}

data class MovieEntry(
    override val id: String,
    override val name: String,
    override val year: Int?,
    val video: VideoFile,
) : CatalogTitle {
    override val addedAt: String get() = video.modifiedTime.orEmpty()
}

/**
 * The library as shows and movies, worked out from file names (see [MediaNames]) rather than from
 * which folder a file is in. Copies of the same episode or movie collapse to the best-quality one.
 */
class Catalog(val shows: List<ShowEntry>, val movies: List<MovieEntry>) {
    val titles: List<CatalogTitle> get() = shows + movies
    private val byId: Map<String, CatalogTitle> = titles.associateBy { it.id }
    private val byVideo: Map<String, Pair<CatalogTitle, EpisodeEntry?>> = buildMap {
        shows.forEach { s -> s.episodes.forEach { e -> put(e.video.id, s to e) } }
        movies.forEach { m -> put(m.video.id, m to null) }
    }

    fun title(id: String): CatalogTitle? = byId[id]

    /** The show (and episode) or movie a video file belongs to. */
    fun locate(videoId: String): Pair<CatalogTitle, EpisodeEntry?>? = byVideo[videoId]

    val isEmpty: Boolean get() = shows.isEmpty() && movies.isEmpty()

    companion object {
        fun from(snapshot: LibrarySnapshot): Catalog {
            val videos = snapshot.scans.flatMap { it.videos }.distinctBy { it.id }
            val episodes = mutableMapOf<String, MutableList<Pair<EpisodeName, VideoFile>>>()
            val movies = mutableMapOf<String, MutableList<Pair<MovieName, VideoFile>>>()
            for (v in videos) {
                when (val parsed = MediaNames.parse(v.name, v.path)) {
                    is EpisodeName -> episodes.getOrPut(MediaNames.key(parsed.show)) { mutableListOf() } += parsed to v
                    is MovieName -> movies.getOrPut(MediaNames.key(parsed.title) + "|" + (parsed.year ?: "")) { mutableListOf() } += parsed to v
                }
            }
            val showEntries = episodes.map { (key, items) ->
                val name = items.groupingBy { it.first.show }.eachCount().maxBy { it.value }.key
                val eps = items
                    .groupBy { it.first.season to it.first.episode }
                    .map { (_, copies) -> copies.maxWith(compareBy({ it.second.height ?: 0 }, { it.second.size })) }
                    .map { (n, v) -> EpisodeEntry(v, n.season, n.episode, n.title) }
                    .sortedWith(compareBy({ it.season }, { it.episode }))
                ShowEntry("show:$key", name, items.firstNotNullOfOrNull { it.first.showYear }, eps)
            }
            val movieEntries = movies.map { (key, copies) ->
                val (name, video) = copies.maxWith(compareBy({ it.second.height ?: 0 }, { it.second.size }))
                MovieEntry("movie:$key", name.title, name.year, video)
            }
            return Catalog(showEntries.sortedBy { it.name.lowercase() }, movieEntries.sortedBy { it.name.lowercase() })
        }
    }
}
