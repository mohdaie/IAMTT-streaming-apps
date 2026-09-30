package com.iamtt.streaming

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamtt.streaming.data.AppJson
import com.iamtt.streaming.data.Catalog
import com.iamtt.streaming.data.FolderScan
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.LibraryFolder
import com.iamtt.streaming.data.LibrarySnapshot
import com.iamtt.streaming.data.OnlineSubtitles
import com.iamtt.streaming.data.OpenSubtitlesSettings
import com.iamtt.streaming.data.Subtitles
import com.iamtt.streaming.data.VideoFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OnlineSubtitlesTest {
    @Test
    fun fileFingerprintMatchesOpenSubtitlesHash() {
        val head = ByteArray(65536) { ((it * 7 + 3) % 256).toByte() }
        val tail = ByteArray(65536) { ((it * 13 + 5) % 256).toByte() }
        assertEquals("20a01fa05a7e68b1", OnlineSubtitles.hash(987654321, head, tail)) // computed independently
    }

    @Test
    fun picksYourLanguageThenHumanMadeThenExactFile() {
        val json = AppJson.parseToJsonElement(
            """{"data":[
              {"attributes":{"language":"en","download_count":900,"moviehash_match":false,"files":[{"file_id":1}]}},
              {"attributes":{"language":"en","download_count":10,"moviehash_match":true,"files":[{"file_id":2}]}},
              {"attributes":{"language":"ms","download_count":5000,"moviehash_match":true,"machine_translated":true,"files":[{"file_id":3}]}},
              {"attributes":{"language":"ms","download_count":50,"moviehash_match":false,"files":[{"file_id":4}]}},
              {"attributes":{"language":"fr","download_count":99999,"files":[{"file_id":5}]}}
            ]}"""
        ) as JsonObject
        val candidates = OnlineSubtitles.parseCandidates(json)
        assertEquals(2L, OnlineSubtitles.pick(candidates, listOf("en", "ms"))?.fileId)
        assertEquals(4L, OnlineSubtitles.pick(candidates, listOf("ms", "en"))?.fileId)
        assertEquals(null, OnlineSubtitles.pick(candidates, listOf("de")))
    }

    @Test
    fun languagesTypedByPeople() {
        assertEquals(listOf("en", "ms", "ms", "en", "zh"), listOf("EN", "Bahasa", "malay", "eng", "chi").map { Subtitles.normalizeLanguage(it) })
        assertEquals(null, Subtitles.normalizeLanguage("xyzzy"))
    }

    /** Answers OpenSubtitles calls with canned responses, remembering what was asked. */
    private class FakeOpenSubtitles(private val downloadCode: Int = 200) {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request(); requests += r
            val (code, body) = when {
                r.url.encodedPath.endsWith("/subtitles") -> 200 to """{"data":[
                    {"attributes":{"language":"en","download_count":5,"files":[{"file_id":77}],
                     "feature_details":{"season_number":1,"episode_number":2}}},
                    {"attributes":{"language":"en","download_count":999,"files":[{"file_id":88}],
                     "feature_details":{"season_number":1,"episode_number":3}}}]}"""
                r.url.encodedPath.endsWith("/download") ->
                    if (downloadCode == 200) 200 to """{"link":"https://dl.example/sub.srt","remaining":19}"""
                    else downloadCode to """{"message":"You have downloaded your allowed 20 subtitles for 24h."}"""
                r.url.host == "dl.example" -> 200 to "1\n00:00:01,000 --> 00:00:02,000\nIn brightest day\n"
                else -> 404 to "{}"
            }
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    private fun episodeSetup(fake: FakeOpenSubtitles): Triple<OnlineSubtitles, VideoFile, Catalog> {
        val app = ApplicationProvider.getApplicationContext<IamttApp>()
        app.config.setOpenSubtitles(OpenSubtitlesSettings(apiKey = "k3y", languages = listOf("en")))
        val video = VideoFile("vidLantern2", "Lanterns - S01E02.mkv", "", size = 1000) // too small to hash: no Drive reads
        val catalog = Catalog.from(LibrarySnapshot(listOf(FolderScan(LibraryFolder("f123456789", "IAMTT", FolderType.MOVIES), listOf(video), 0))))
        return Triple(OnlineSubtitles(app, fake.client, app.drive, app.config), video, catalog)
    }

    @Test
    fun fetchesTheRightEpisodeAndKeepsIt() = runBlocking {
        val fake = FakeOpenSubtitles()
        val (online, video, catalog) = episodeSetup(fake)
        val (show, episode) = catalog.locate(video.id)!!
        val result = online.fetch(video, show, episode, imdbId = "tt0012345")

        val search = fake.requests.first { it.url.encodedPath.endsWith("/subtitles") }
        assertEquals("episode_number=2&languages=en&parent_imdb_id=12345&season_number=1&type=episode", search.url.encodedQuery)
        assertEquals("k3y", search.header("Api-Key"))
        val download = fake.requests.first { it.url.encodedPath.endsWith("/download") }
        val buffer = okio.Buffer().also { download.body!!.writeTo(it) }
        assertEquals("""{"file_id":77}""", buffer.readUtf8()) // episode 3's subtitle isn't used for episode 2
        assertTrue(result is OnlineSubtitles.Result.Found)
        val file = (result as OnlineSubtitles.Result.Found).subtitle.file
        assertTrue(file.readText().contains("In brightest day"))
        assertEquals("English (online)", result.subtitle.label)
        // Kept on the device: asking again doesn't call OpenSubtitles.
        val calls = fake.requests.size
        online.fetch(video, show, episode, imdbId = "tt0012345")
        assertEquals(calls, fake.requests.size)
        assertEquals(1, online.downloaded(video.id).size)
    }

    @Test
    fun moviesAreSearchedByNameAndYearInYourLanguages() = runBlocking {
        val fake = FakeOpenSubtitles()
        val app = ApplicationProvider.getApplicationContext<IamttApp>()
        app.config.setOpenSubtitles(OpenSubtitlesSettings(apiKey = "k3y", languages = listOf("ms", "zh")))
        val video = VideoFile("vidHarbour", "The Quiet Harbour (2019) 1080p.mkv", "", size = 1000)
        val catalog = Catalog.from(LibrarySnapshot(listOf(FolderScan(LibraryFolder("f123456789", "IAMTT", FolderType.MOVIES), listOf(video), 0))))
        val online = OnlineSubtitles(app, fake.client, app.drive, app.config)
        val (movie, _) = catalog.locate(video.id)!!

        // Only English comes back, which isn't wanted here.
        assertEquals(OnlineSubtitles.Result.NoneFound, online.fetch(video, movie, null, imdbId = null))
        val search = fake.requests.single()
        // Chinese is listed by region on OpenSubtitles.
        assertEquals("languages=ms,zh-cn,zh-tw&query=the%20quiet%20harbour&type=movie&year=2019", search.url.encodedQuery)
        // Not asked again straight away.
        assertEquals(OnlineSubtitles.Result.NoneFound, online.fetch(video, movie, null, imdbId = null))
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun dailyLimitIsExplained() = runBlocking {
        val (online, video, catalog) = episodeSetup(FakeOpenSubtitles(downloadCode = 406))
        val (show, episode) = catalog.locate(video.id)!!
        val result = online.fetch(video, show, episode, imdbId = null)
        assertEquals(OnlineSubtitles.Result.Failed("You have downloaded your allowed 20 subtitles for 24h."), result)
    }
}
