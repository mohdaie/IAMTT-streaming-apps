package com.iamtt.streaming

import com.iamtt.streaming.data.Subtitles
import com.iamtt.streaming.data.VideoFile
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitlesTest {
    private fun v(id: String, name: String, path: String = "") = VideoFile(id, name, path)
    private fun s(id: String, name: String, path: String = "") = Subtitles.Found(id, name, path)

    private fun labels(videos: List<VideoFile>, id: String) =
        videos.first { it.id == id }.subtitles.map { it.label to it.language }

    @Test
    fun matchesByNameNextToTheVideo() {
        val out = Subtitles.attach(
            listOf(v("a", "Inception (2010).mkv"), v("b", "Interstellar (2014).mkv")),
            listOf(s("1", "Inception (2010).en.srt"), s("2", "Inception (2010).ms.srt"), s("3", "Interstellar (2014).srt")),
        )
        assertEquals(listOf("English" to "en", "Malay" to "ms"), labels(out, "a"))
        assertEquals(listOf("Subtitles" to null), labels(out, "b"))
    }

    @Test
    fun loneVideoGetsEveryFileInItsFolderAndSubsFolder() {
        val out = Subtitles.attach(
            listOf(v("a", "movie.mkv", "Dune (2021)")),
            listOf(s("1", "English.srt", "Dune (2021)/Subs"), s("2", "eng.forced.srt", "Dune (2021)")),
        )
        assertEquals(listOf("English" to "en", "English (Forced)" to "en"), labels(out, "a").sortedBy { it.first })
    }

    @Test
    fun tvReleaseSubsFolderPerEpisode() {
        val out = Subtitles.attach(
            listOf(v("e1", "Lanterns.S01E01.1080p.mkv", "Lanterns"), v("e2", "Lanterns.S01E02.1080p.mkv", "Lanterns")),
            listOf(s("1", "2_English.srt", "Lanterns/Subs/Lanterns.S01E02.1080p"), s("2", "Lanterns.S01E01.1080p.eng.srt", "Lanterns")),
        )
        assertEquals(listOf("English" to "en"), labels(out, "e1"))
        assertEquals(listOf("English" to "en"), labels(out, "e2"))
    }
}
