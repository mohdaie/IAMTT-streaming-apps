package com.iamtt.streaming

import com.iamtt.streaming.data.Catalog
import com.iamtt.streaming.data.FolderScan
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.LibraryFolder
import com.iamtt.streaming.data.LibrarySnapshot
import com.iamtt.streaming.data.TitleInfo
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.data.WatchProgress
import com.iamtt.streaming.ui.buildHome
import com.iamtt.streaming.ui.nextUp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Episodes group into shows whatever folder they're in; the home screen is built from that. */
class CatalogTest {
    private val folder = LibraryFolder("f123456789", "IAMTT", FolderType.MOVIES) // added as "Movies"
    private val videos = listOf(
        VideoFile("e1", "Lanterns - S01E01 - Pilot.mkv", "", height = 1080, modifiedTime = "2026-09-01"),
        VideoFile("e1b", "Lanterns.S01E01.720p.mkv", "", height = 720, modifiedTime = "2026-09-01"),
        VideoFile("e2", "Lanterns - S01E02 - Second.mkv", "", height = 1080, modifiedTime = "2026-09-08"),
        VideoFile("m1", "Inception (2010).mkv", "", modifiedTime = "2026-08-01"),
        VideoFile("m2", "The.Dark.Knight.2008.1080p.mkv", "", modifiedTime = "2026-08-02"),
        VideoFile("m3", "Interstellar.2014.mkv", "", modifiedTime = "2026-08-03"),
    )
    private val catalog = Catalog.from(LibrarySnapshot(listOf(FolderScan(folder, videos, 0))))

    @Test
    fun episodesBecomeOneShowEvenInAMoviesFolder() {
        assertEquals(listOf("Lanterns"), catalog.shows.map { it.name })
        val show = catalog.shows.single()
        assertEquals(listOf("e1", "e2"), show.episodes.map { it.video.id }) // the 1080p copy wins
        assertEquals(listOf("Inception", "Interstellar", "The Dark Knight"), catalog.movies.map { it.name })
    }

    @Test
    fun homeRowsAndResume() {
        val history = listOf(WatchProgress("e2", 600_000, 3_000_000, 2), WatchProgress("e1", 60_000, 3_000_000, 1))
        val infos = catalog.movies.associate { it.id to TitleInfo(genres = listOf("Crime")) } +
            (catalog.shows.single().id to TitleInfo(genres = listOf("Crime")))
        val home = buildHome(catalog, history, infos, "Aisya")
        assertEquals(
            listOf("Continue Watching for Aisya", "TV Shows", "Movies", "Crime"),
            home.rows.map { it.title },
        )
        // One Continue Watching card per show, resuming the most recent episode.
        assertEquals(listOf("e2"), home.rows[0].cards.map { it.resume?.id })
        assertEquals("e2", catalog.shows.single().nextUp(history).first.video.id)
    }
}
