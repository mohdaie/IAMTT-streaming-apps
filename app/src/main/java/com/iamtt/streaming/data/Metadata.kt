package com.iamtt.streaming.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Artwork and details for a show or movie. [found] false means nothing matched (retried later). */
@Serializable
data class TitleInfo(
    val name: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    /** Poster for cards (about 300 px wide) and a bigger one for the featured spot and title pages. */
    val poster: String? = null,
    val posterLarge: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeInfo> = emptyList(),
    /** e.g. "tt1234567"; helps find the right subtitles for a show. */
    val imdbId: String? = null,
    val found: Boolean = true,
    val fetchedAt: Long = 0,
)

@Serializable
data class EpisodeInfo(
    val season: Int,
    val number: Int,
    val name: String? = null,
    val still: String? = null,
    val summary: String? = null,
    val runtime: Int? = null,
)

/**
 * Looks up posters, summaries and genres, with nothing to set up: TVmaze for shows (it also has
 * every episode's name and still), Wikipedia for movies. One title at a time, spaced out, backing
 * off when asked to, and saved to disk so each title is only looked up once.
 */
class MetadataRepository(context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "metadata.json")
    private val serializer = MapSerializer(String.serializer(), TitleInfo.serializer())
    private val _titles = MutableStateFlow(load())
    val titles: StateFlow<Map<String, TitleInfo>> = _titles.asStateFlow()

    private val queue = Channel<CatalogTitle>(Channel.UNLIMITED)
    private val queued = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch(Dispatchers.IO) {
            for (title in queue) {
                try {
                    val info = when (title) {
                        is ShowEntry -> fetchShow(title)
                        is MovieEntry -> fetchMovie(title)
                    }
                    _titles.update { it + (title.id to info) }
                    save()
                } catch (e: IOException) {
                    Log.w(TAG, "Couldn't look up ${title.name}", e) // offline or refused: try again next time
                } catch (e: Exception) {
                    Log.w(TAG, "Lookup of ${title.name} failed", e)
                } finally {
                    queued.remove(title.id)
                }
            }
        }
    }

    /** Queues lookups for titles with no details yet, or whose details are due a refresh. */
    fun request(catalog: Catalog) {
        val now = System.currentTimeMillis()
        val have = _titles.value
        // Newest first, so what's on screen fills in first.
        for (title in catalog.titles.sortedByDescending { it.addedAt }) {
            val info = have[title.id]
            val stale = when {
                info == null -> true
                !info.found -> now - info.fetchedAt > 7 * DAY          // not found: try again weekly
                title is ShowEntry -> now - info.fetchedAt > 3 * DAY   // shows get new episodes
                else -> now - info.fetchedAt > 60 * DAY
            }
            if (stale && queued.add(title.id)) queue.trySend(title)
        }
    }

    // ---------------------------------------------------------------- TVmaze (shows)

    private suspend fun fetchShow(show: ShowEntry): TitleInfo {
        val results = getJson("https://api.tvmaze.com/search/shows".toHttpUrl().newBuilder()
            .addQueryParameter("q", show.name).build().toString(), spacingMs = 600) as? JsonArray ?: JsonArray(emptyList())
        val candidates = results.mapNotNull { (it as? JsonObject)?.obj("show") }.filter { sameTitle(it.str("name"), show.name) }
        val pick = candidates.firstOrNull { show.year != null && it.str("premiered")?.startsWith("${show.year}") == true }
            ?: candidates.firstOrNull()
            ?: return TitleInfo(found = false, fetchedAt = System.currentTimeMillis())
        val id = pick["id"]?.jsonPrimitive?.intOrNull ?: return TitleInfo(found = false, fetchedAt = System.currentTimeMillis())
        val full = getJson("https://api.tvmaze.com/shows/$id?embed=episodes", spacingMs = 600) as? JsonObject ?: pick
        val episodes = full.obj("_embedded")?.get("episodes")?.jsonArray.orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val season = o["season"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val number = o["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            EpisodeInfo(season, number, o.str("name"), o.obj("image")?.str("medium"), plainText(o.str("summary")), o["runtime"]?.jsonPrimitive?.intOrNull)
        }
        return TitleInfo(
            name = full.str("name"),
            year = full.str("premiered")?.take(4)?.toIntOrNull(),
            overview = plainText(full.str("summary")),
            poster = full.obj("image")?.str("medium"),
            posterLarge = full.obj("image")?.str("original"),
            genres = full["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            episodes = episodes,
            imdbId = full.obj("externals")?.str("imdb"),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    // ---------------------------------------------------------------- Wikipedia (movies)

    private suspend fun fetchMovie(movie: MovieEntry): TitleInfo {
        // One request: search for the film and get each hit's poster, one-line description and intro.
        val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", listOfNotNull(movie.name, movie.year?.toString(), "film").joinToString(" "))
            .addQueryParameter("gsrlimit", "5").addQueryParameter("redirects", "1")
            .addQueryParameter("prop", "pageimages|description|extracts")
            // Film posters are fair-use images; without pilicense=any only freely licensed images come back.
            .addQueryParameter("piprop", "thumbnail").addQueryParameter("pithumbsize", "600").addQueryParameter("pilicense", "any")
            .addQueryParameter("exintro", "1").addQueryParameter("explaintext", "1").addQueryParameter("exsentences", "3")
            .build().toString()
        val pages = (getJson(url, spacingMs = 1_200) as? JsonObject)?.obj("query")?.get("pages")?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .sortedBy { it["index"]?.jsonPrimitive?.intOrNull ?: 99 }
        val films = pages.filter { p ->
            val description = p.str("description").orEmpty()
            val title = p.str("title").orEmpty().replace(Regex("""\s*\([^)]*\)$"""), "")
            filmWords.containsMatchIn(description) && !notFilmWords.containsMatchIn(description) && sameTitle(title, movie.name)
        }
        val pick = films.firstOrNull { p ->
            movie.year != null && ("${movie.year}" in p.str("description").orEmpty() || "${movie.year}" in p.str("extract").orEmpty())
        } ?: films.firstOrNull() ?: return TitleInfo(found = false, fetchedAt = System.currentTimeMillis())
        val extract = pick.str("extract")?.replace(Regex("""\s+"""), " ")?.trim()
        val image = pick.obj("thumbnail")?.str("source")
        return TitleInfo(
            name = pick.str("title")?.replace(Regex("""\s*\([^)]*\)$"""), ""),
            year = Regex("""(19|20)\d\d""").find(pick.str("description").orEmpty())?.value?.toInt() ?: movie.year,
            overview = extract,
            poster = image,
            posterLarge = image,
            genres = genresIn(extract.orEmpty()),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    // ---------------------------------------------------------------- plumbing

    private var lastRequestAt = 0L

    /** GETs JSON politely: at least [spacingMs] between requests, and waits out "too many requests". */
    private suspend fun getJson(url: String, spacingMs: Long): JsonElement? {
        repeat(4) {
            val wait = lastRequestAt + spacingMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { resp ->
                when {
                    resp.code == 429 -> {
                        val retry = resp.header("Retry-After")?.toLongOrNull() ?: 60
                        delay(retry.coerceIn(5, 300) * 1000)
                    }
                    resp.code == 404 -> return null
                    !resp.isSuccessful -> throw IOException("HTTP ${resp.code} from ${request.url.host}")
                    else -> return AppJson.parseToJsonElement(resp.body?.string().orEmpty())
                }
            }
        }
        throw IOException("Still rate-limited by ${url.toHttpUrl().host}")
    }

    private fun load(): Map<String, TitleInfo> = runCatching {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else emptyMap()
    }.getOrDefault(emptyMap())

    @Synchronized
    private fun save() {
        runCatching {
            val tmp = File(file.parentFile, "metadata.json.tmp")
            tmp.writeText(AppJson.encodeToString(serializer, _titles.value))
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val TAG = "Metadata"
        private const val DAY = 24 * 60 * 60 * 1000L
        const val USER_AGENT = "IAMTT/1.0 (Android personal media app; https://github.com/mohdaie/IAMTT-streaming-apps)"

        private val filmWords = Regex("""(?i)\b(film|movie)\b""")
        private val notFilmWords = Regex("""(?i)\b(soundtrack|album|song|novel|franchise|series|character|video game|book)\b""")

        /** Loose title match: "The Office" matches "The Office (US)", but "Inception" doesn't match "Inception Point". */
        fun sameTitle(a: String?, b: String): Boolean {
            val x = MediaNames.key(a ?: return false)
            val y = MediaNames.key(b)
            if (x.isEmpty() || y.isEmpty()) return false
            if (x == y) return true
            val (short, long) = if (x.length <= y.length) x to y else y to x
            return short.length >= 4 && long.startsWith(short) && long.length - short.length <= 4
        }

        /** Genre words in a film's intro, named like TVmaze's genres so shows and movies share rows. */
        private val genreWords = listOf(
            "science fiction" to "Science-Fiction", "sci-fi" to "Science-Fiction", "superhero" to "Action",
            "action" to "Action", "adventure" to "Adventure", "animated" to "Animation", "animation" to "Animation",
            "comedy" to "Comedy", "drama" to "Drama", "horror" to "Horror", "thriller" to "Thriller",
            "romantic" to "Romance", "romance" to "Romance", "fantasy" to "Fantasy", "crime" to "Crime",
            "mystery" to "Mystery", "documentary" to "Documentary", "family" to "Family", "musical" to "Music",
            "war film" to "War", "western" to "Western", "biographical" to "History", "historical" to "History",
            "heist" to "Crime", "spy" to "Espionage",
        )

        fun genresIn(text: String): List<String> {
            // Only the first sentence says what kind of film it is; later ones mention other films.
            val first = text.substringBefore(". ").lowercase()
            return genreWords.filter { (word, _) -> Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(first) }
                .map { it.second }.distinct()
        }

        fun plainText(html: String?): String? = html
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace("&nbsp;", " ")
            ?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonObject.obj(key: String) = this[key] as? JsonObject
        private fun JsonObject.str(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    }
}
