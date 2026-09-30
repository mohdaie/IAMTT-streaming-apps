package com.iamtt.streaming

import androidx.test.core.app.ApplicationProvider
import com.iamtt.streaming.data.AppConfig
import com.iamtt.streaming.data.AppJson
import com.iamtt.streaming.data.FolderScan
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.LibraryFolder
import com.iamtt.streaming.data.LibrarySnapshot
import com.iamtt.streaming.data.Profile
import com.iamtt.streaming.data.VideoFile
import java.io.File

/** A signed-in setup with a few movies and episodes, as if a scan had already run. */
object SampleLibrary {
    val movies = LibraryFolder("moviesFolder01", "Movies", FolderType.MOVIES)
    val shows = LibraryFolder("showsFolder001", "TV Shows", FolderType.TV_SHOWS)

    private fun video(id: String, name: String, path: String = "", day: Int) = VideoFile(
        id = id, name = name, path = path, size = 2_400_000_000, modifiedTime = "2026-09-%02dT10:00:00Z".format(day),
        durationMs = 8_400_000, width = 1920, height = 1080,
    )

    val inception = video("videoInception1", "Inception (2010).mkv", day = 20)

    /** Writes the setup to the app's files and reloads the app from them, before any screen opens. */
    fun install(profiles: List<Profile> = emptyList()): IamttApp {
        val app = ApplicationProvider.getApplicationContext<IamttApp>()
        val config = AppConfig(googleAccount = "me@gmail.com", folders = listOf(movies, shows), profiles = profiles)
        File(app.filesDir, "config.json").writeText(AppJson.encodeToString(AppConfig.serializer(), config))
        val snapshot = LibrarySnapshot(
            listOf(
                FolderScan(
                    movies,
                    listOf(
                        inception,
                        video("videoDarkKnight", "The Dark Knight (2008).mkv", day = 18),
                        video("videoInterstell", "Interstellar (2014).mp4", day = 25),
                        video("videoSpiritedAw", "Spirited Away (2001).mkv", day = 12),
                    ),
                    scannedAt = 0,
                ),
                FolderScan(
                    shows,
                    listOf(
                        video("videoBreakingB1", "Breaking Bad S01E01.mkv", "Breaking Bad/Season 01", day = 27),
                        video("videoBreakingB2", "Breaking Bad S01E02.mkv", "Breaking Bad/Season 01", day = 27),
                    ),
                    scannedAt = 0,
                ),
            )
        )
        File(app.filesDir, "library.json").writeText(AppJson.encodeToString(LibrarySnapshot.serializer(), snapshot))
        app.onCreate() // reload everything from the files just written
        return app
    }
}
