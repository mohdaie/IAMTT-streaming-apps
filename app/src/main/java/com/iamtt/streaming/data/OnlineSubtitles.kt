package com.iamtt.streaming.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Locale

/** A subtitle file fetched from OpenSubtitles and kept on the device. */
data class DownloadedSubtitle(val file: File, val language: String, val label: String)

/** One search result from OpenSubtitles, reduced to what choosing between them needs. */
data class SubtitleCandidate(
    val fileId: Long,
    val language: String,
    val hashMatch: Boolean,
    val machineMade: Boolean,
    val downloads: Int,
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * Fetches subtitles from OpenSubtitles.com when a video has none in your language. Needs the
 * user's own free API key (and optionally their login, for more downloads a day). Each download
 * is kept on the device, so a video only ever costs one download.
 */
class OnlineSubtitles(
    context: Context,
    private val http: OkHttpClient,
    private val drive: com.iamtt.streaming.drive.DriveClient,
    private val config: ConfigStore,
) {
    private val dir = File(context.filesDir, "online-subtitles")
    private val lock = Mutex()
    private var token: String? = null
    private var tokenFor: String? = null
    private var tokenAt = 0L
    private var baseUrl = DEFAULT_BASE

    val enabled: Boolean get() = !config.current.openSubtitles?.apiKey.isNullOrBlank()

    /** Subtitle languages, best first: the ones chosen on the setup page, or the phone's language then English. */
    fun languages(): List<String> =
        config.current.openSubtitles?.languages?.takeIf { it.isNotEmpty() }
            ?: listOf(Locale.getDefault().language, "en").distinct()

    /** Subtitles already fetched for [videoId]. */
    fun downloaded(videoId: String): List<DownloadedSubtitle> =
        dir.listFiles { f -> f.name.startsWith("$videoId.") && f.name.endsWith(".srt") }.orEmpty().map { f ->
            val lang = f.name.removePrefix("$videoId.").removeSuffix(".srt")
            DownloadedSubtitle(f, lang, "${languageName(lang)} (online)")
        }

    sealed interface Result {
        data class Found(val subtitle: DownloadedSubtitle) : Result
        data object NoneFound : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Finds and downloads the best subtitle for [video]: movie by name and year, episode by show
     * (its IMDb id when known) and number, plus the file's own hash so timing matches when possible.
     */
    suspend fun fetch(video: VideoFile, title: CatalogTitle?, episode: EpisodeEntry?, imdbId: String?): Result = withContext(Dispatchers.IO) {
        lock.withLock {
            downloaded(video.id).firstOrNull()?.let { return@withLock Result.Found(it) }
            val settings = config.current.openSubtitles ?: return@withLock Result.Failed("Online subtitles aren't set up.")
            val noneMarker = File(dir, "${video.id}.none")
            if (noneMarker.exists() && System.currentTimeMillis() - noneMarker.lastModified() < 3 * DAY) return@withLock Result.NoneFound
            try {
                val langs = languages()
                val hash = runCatching { movieHash(video) }.getOrNull()
                // The API wants params (and the languages in them) sorted.
                val params = sortedMapOf("languages" to langs.flatMap { REGIONAL[it] ?: listOf(it) }.sorted().joinToString(","))
                hash?.let { params["moviehash"] = it }
                if (episode != null) {
                    params["type"] = "episode"
                    params["season_number"] = episode.season.toString()
                    params["episode_number"] = episode.episode.toString()
                    val imdb = imdbId?.removePrefix("tt")?.trimStart('0')
                    if (!imdb.isNullOrEmpty()) params["parent_imdb_id"] = imdb else params["query"] = title?.name.orEmpty().lowercase()
                } else {
                    params["type"] = "movie"
                    params["query"] = (title?.name ?: video.displayName).lowercase()
                    title?.year?.let { params["year"] = it.toString() }
                }
                val url = "$baseUrl/subtitles".toHttpUrl().newBuilder().apply {
                    // Language codes keep their commas, as the API documents them.
                    params.forEach { (k, v) -> if (k == "languages") addEncodedQueryParameter(k, v) else addQueryParameter(k, v) }
                }.build()
                val search = call(settings, Request.Builder().url(url).get())
                val candidates = parseCandidates(search).filter { c ->
                    episode == null || ((c.season == null || c.season == episode.season) && (c.episode == null || c.episode == episode.episode))
                }
                val best = pick(candidates, langs) ?: run {
                    dir.mkdirs(); noneMarker.writeText("")
                    return@withLock Result.NoneFound
                }
                val body = buildJsonObject { put("file_id", best.fileId) }.toString().toRequestBody(JSON)
                val download = call(settings, Request.Builder().url("$baseUrl/download").post(body))
                val link = (download["link"] as? JsonPrimitive)?.contentOrNull ?: return@withLock Result.Failed("OpenSubtitles didn't give a download link.")
                val text = http.newCall(Request.Builder().url(link).header("User-Agent", USER_AGENT).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Subtitle download failed (HTTP ${resp.code})")
                    resp.body?.string().orEmpty()
                }
                dir.mkdirs()
                val file = File(dir, "${video.id}.${best.language}.srt")
                file.writeText(text)
                Result.Found(DownloadedSubtitle(file, best.language, "${languageName(best.language)} (online)"))
            } catch (e: OpenSubtitlesException) {
                Result.Failed(e.message ?: "OpenSubtitles refused the request.")
            } catch (e: IOException) {
                Log.w(TAG, "Online subtitles failed", e)
                Result.Failed("Couldn't reach OpenSubtitles.")
            }
        }
    }

    /** Checks new settings work before they're saved. Returns an error message, or null if fine. */
    suspend fun check(settings: OpenSubtitlesSettings): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            token = null
            try {
                // A tiny search proves the key; logging in (when a username is given) proves the account.
                call(settings, Request.Builder().url("$DEFAULT_BASE/subtitles?query=test".toHttpUrl()).get())
                null
            } catch (e: OpenSubtitlesException) {
                e.message
            } catch (e: IOException) {
                "Couldn't reach OpenSubtitles. Check the internet connection."
            }
        }
    }

    private class OpenSubtitlesException(message: String) : Exception(message)

    /** Sends an API request with the key (and login token when there's an account); returns the JSON. */
    private fun call(settings: OpenSubtitlesSettings, builder: Request.Builder): JsonObject {
        login(settings)
        val request = builder
            .header("Api-Key", settings.apiKey.trim())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { AppJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
            val message = (json["message"] as? JsonPrimitive)?.contentOrNull
            return when {
                resp.isSuccessful -> json
                resp.code == 401 || resp.code == 403 -> throw OpenSubtitlesException("OpenSubtitles didn't accept the API key${message?.let { " ($it)" }.orEmpty()}.")
                resp.code == 406 -> throw OpenSubtitlesException(message ?: "Today's subtitle downloads are used up. Try again tomorrow.")
                resp.code == 429 -> throw OpenSubtitlesException("OpenSubtitles is busy. Try again in a minute.")
                else -> throw IOException("OpenSubtitles error ${resp.code}: ${message ?: text.take(120)}")
            }
        }
    }

    /** Logs in when the settings have an account; the token is reused for 12 hours. */
    private fun login(settings: OpenSubtitlesSettings) {
        val user = settings.username?.trim().orEmpty()
        if (user.isEmpty() || settings.password.isNullOrEmpty()) {
            token = null
            baseUrl = DEFAULT_BASE
            return
        }
        if (token != null && tokenFor == user && System.currentTimeMillis() - tokenAt < 12 * 60 * 60 * 1000L) return
        val body = buildJsonObject { put("username", user); put("password", settings.password) }.toString().toRequestBody(JSON)
        val request = Request.Builder().url("$DEFAULT_BASE/login").post(body)
            .header("Api-Key", settings.apiKey.trim()).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
        http.newCall(request).execute().use { resp ->
            val json = runCatching { AppJson.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject }.getOrNull()
            if (!resp.isSuccessful) {
                throw OpenSubtitlesException(
                    if (resp.code == 401) "OpenSubtitles username or password is wrong."
                    else "OpenSubtitles didn't accept the login (${(json?.get("message") as? JsonPrimitive)?.contentOrNull ?: resp.code})."
                )
            }
            token = (json?.get("token") as? JsonPrimitive)?.contentOrNull
            tokenFor = user
            tokenAt = System.currentTimeMillis()
            // Paying (VIP) accounts are served from their own host.
            baseUrl = (json?.get("base_url") as? JsonPrimitive)?.contentOrNull?.let { "https://$it/api/v1" } ?: DEFAULT_BASE
        }
    }

    /**
     * OpenSubtitles' file fingerprint: the size plus the first and last 64 KB summed as 64-bit
     * little-endian numbers. Read with two small range requests, so nothing big is downloaded.
     */
    private fun movieHash(video: VideoFile): String? {
        val size = video.size
        if (size < 2 * CHUNK) return null
        val head = drive.readRange(video.id, 0, CHUNK)
        val tail = drive.readRange(video.id, size - CHUNK, CHUNK)
        return hash(size, head, tail)
    }

    companion object {
        private const val TAG = "OnlineSubtitles"
        private const val DEFAULT_BASE = "https://api.opensubtitles.com/api/v1"
        private const val USER_AGENT = "IAMTT v1.0"
        private const val CHUNK = 65536L
        private const val DAY = 24 * 60 * 60 * 1000L
        private val JSON = "application/json".toMediaType()

        /** Languages OpenSubtitles only has by region. Results come back as the plain code. */
        private val REGIONAL = mapOf("zh" to listOf("zh-cn", "zh-tw"), "pt" to listOf("pt-br", "pt-pt"))

        fun hash(size: Long, head: ByteArray, tail: ByteArray): String {
            var h = size
            for (chunk in listOf(head, tail)) {
                val buf = java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                while (buf.remaining() >= 8) h += buf.long
            }
            return String.format(Locale.US, "%016x", h)
        }

        fun parseCandidates(json: JsonObject): List<SubtitleCandidate> =
            (json["data"] as? JsonArray).orEmpty().mapNotNull { item ->
                val a = (item as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
                val file = ((a["files"] as? JsonArray)?.firstOrNull() as? JsonObject) ?: return@mapNotNull null
                val fileId = (file["file_id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
                val details = a["feature_details"] as? JsonObject
                SubtitleCandidate(
                    fileId = fileId,
                    language = Subtitles.normalizeLanguage((a["language"] as? JsonPrimitive)?.contentOrNull.orEmpty().substringBefore('-'))
                        ?: (a["language"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    hashMatch = (a["moviehash_match"] as? JsonPrimitive)?.booleanOrNull == true,
                    machineMade = (a["machine_translated"] as? JsonPrimitive)?.booleanOrNull == true ||
                        (a["ai_translated"] as? JsonPrimitive)?.booleanOrNull == true,
                    downloads = (a["download_count"] as? JsonPrimitive)?.intOrNull ?: 0,
                    season = (details?.get("season_number") as? JsonPrimitive)?.intOrNull,
                    episode = (details?.get("episode_number") as? JsonPrimitive)?.intOrNull,
                )
            }

        /**
         * Best first: your languages in order, then human-made (machine translations read badly),
         * then made for this exact file (timing matches), then the most downloaded.
         */
        fun pick(candidates: List<SubtitleCandidate>, languages: List<String>): SubtitleCandidate? =
            candidates.filter { it.language in languages }.minWithOrNull(
                compareBy<SubtitleCandidate>({ languages.indexOf(it.language) }, { it.machineMade }, { !it.hashMatch }, { -it.downloads })
            )

        fun languageName(code: String): String =
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
    }
}
