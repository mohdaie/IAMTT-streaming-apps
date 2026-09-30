package com.iamtt.streaming

import com.iamtt.streaming.data.EpisodeName
import com.iamtt.streaming.data.MediaNames
import com.iamtt.streaming.data.MovieName
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaNamesTest {
    private fun ep(file: String, path: String = "") = MediaNames.parse(file, path) as? EpisodeName
        ?: throw AssertionError("$path/$file should be an episode but was ${MediaNames.parse(file, path)}")

    private fun movie(file: String, path: String = "") = MediaNames.parse(file, path) as? MovieName
        ?: throw AssertionError("$path/$file should be a movie but was ${MediaNames.parse(file, path)}")

    @Test
    fun episodes() {
        assertEquals(EpisodeName("Lanterns", null, 1, 1, "Pilot"), ep("Lanterns - S01E01 - Pilot.mkv"))
        assertEquals(EpisodeName("Lanterns", 2026, 1, 2, null), ep("Lanterns.2026.S01E02.1080p.WEB.h264-ETHEL.mkv"))
        assertEquals(EpisodeName("Lanterns", null, 1, 1, "Pilot"), ep("Lanterns - S01E01 - Pilot 1080p WEB-DL.mkv"))
        assertEquals(EpisodeName("Breaking Bad", null, 1, 1, null), ep("Breaking Bad S01E01.mkv", "Breaking Bad/Season 01"))
        assertEquals(EpisodeName("The Office (US)", null, 2, 5, "Halloween"), ep("S02E05 - Halloween.mkv", "The Office (US)/Season 2"))
        assertEquals(EpisodeName("Show Name", null, 1, 3, "Title"), ep("Show.Name.1x03.Title.mkv"))
        assertEquals(EpisodeName("Show", null, 2, 5, null), ep("Episode 5.mkv", "Show/Season 2"))
        assertEquals(EpisodeName("Show", null, 2, 5, "Some Title"), ep("05 - Some Title.mkv", "Show/Season 2"))
        assertEquals(EpisodeName("Friends", null, 1, 1, null), ep("Friends.S01E01E02.mkv"))
        assertEquals(EpisodeName("Show", null, 1, 3, null), ep("Show S01 E03.mkv"))
        assertEquals(EpisodeName("Upin & Ipin", null, 3, 12, null), ep("Upin & Ipin Season 3 Episode 12.mp4"))
    }

    @Test
    fun movies() {
        assertEquals(MovieName("Inception", 2010), movie("Inception (2010).mkv"))
        assertEquals(MovieName("The Dark Knight", 2008), movie("The.Dark.Knight.2008.1080p.BluRay.x264-GROUP.mkv"))
        assertEquals(MovieName("Spirited Away", 2001), movie("Spirited Away (2001) [1080p] [BluRay].mkv"))
        assertEquals(MovieName("2001 A Space Odyssey", 1968), movie("2001.A.Space.Odyssey.1968.mkv"))
        assertEquals(MovieName("Blade Runner 2049", 2017), movie("Blade Runner 2049 (2017).mkv"))
        assertEquals(MovieName("The Dark Knight", 2008), movie("movie.mkv", "Movies/The Dark Knight (2008)"))
        assertEquals(MovieName("Avatar", null), movie("Avatar.mkv"))
        assertEquals(MovieName("Ne Zha 2", 2025), movie("Ne Zha 2 (2025) 1080p.mkv"))
        assertEquals(MovieName("Dune Part Two", 2024), movie("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265.mkv"))
        assertEquals(MovieName("Mission Impossible - Dead Reckoning Part One", 2023), movie("Mission Impossible - Dead Reckoning Part One (2023).mkv"))
        assertEquals(MovieName("Spider-Man No Way Home", 2021), movie("Spider-Man.No.Way.Home.2021.1080p.mkv"))
    }

    @Test
    fun keysIgnoreSpellingDetails() {
        assertEquals(MediaNames.key("The Office (US)"), MediaNames.key("the.office.us"))
    }
}
