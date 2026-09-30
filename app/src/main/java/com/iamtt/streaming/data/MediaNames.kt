package com.iamtt.streaming.data

/** What a video file is, worked out from its name and the folders it sits in. */
sealed interface ParsedName

data class EpisodeName(
    val show: String,
    val showYear: Int?,
    val season: Int,
    val episode: Int,
    /** The episode's own title when the file name has one, e.g. "Pilot". */
    val title: String?,
) : ParsedName

data class MovieName(val title: String, val year: Int?) : ParsedName

/**
 * Tells episodes from movies by their names, the way people and release groups usually name them:
 * "Lanterns - S01E01 - Pilot.mkv", "Lanterns.2026.S01E01.1080p.WEB.h264.mkv", "Show 1x01.mp4",
 * "Show/Season 1/Episode 3.mkv", "Inception (2010).mkv", "The.Dark.Knight.2008.1080p.BluRay.x264.mkv".
 */
object MediaNames {
    private val sxe = Regex("""(?i)(?<![a-z0-9])s(\d{1,2})[ ._-]*e(\d{1,3})(?:[ ._-]*-?[ ._-]*e?\d{1,3}(?!\d))?(?![0-9])""")
    private val nxn = Regex("""(?i)(?<![a-z0-9])(\d{1,2})x(\d{2,3})(?![0-9p])""")
    private val seasonEpisodeWords = Regex("""(?i)\bseason[ ._-]*(\d{1,2})[ ._-]*(?:episode|ep)[ ._-]*(\d{1,3})\b""")
    private val episodeWord = Regex("""(?i)(?:^|[ ._-])(?:episode|ep|e)[ ._-]*(\d{1,3})(?![0-9])""")
    private val leadingNumber = Regex("""^(\d{1,3})(?:[ ._-]+|$)""")
    private val seasonFolder = Regex("""(?i)^(?:season|series|s)[ ._-]*(\d{1,2})$""")
    private val year = Regex("""(?<![0-9])(19[2-9]\d|20[0-4]\d)(?![0-9])""")

    /** Release tags that end a title: resolution, source, codecs, audio, services. */
    private val junk = Regex(
        """(?i)(?<![a-z0-9])(2160p|1080p|1080i|720p|576p|480p|4k|uhd|hdr10?\+?|hdr|dv|dolby[ ._-]?vision|web[ ._-]?dl|webrip|web|""" +
            """bluray|blu[ ._-]?ray|brrip|bdrip|dvdrip|hdtv|hdrip|remux|x264|x265|h[ ._]?264|h[ ._]?265|hevc|avc|10bit|""" +
            """aac\d?(?:[ ._]\d)?|ac3|eac3|ddp?\d?(?:[ ._]\d)?|dts(?:-hd)?|atmos|truehd|nf|amzn|hmax|max|dsnp|atvp|hulu|""" +
            """proper|repack|extended|unrated|remastered|imax|multi|dual[ ._-]?audio|subbed|dubbed|esub|msubs?)(?![a-z0-9])"""
    )

    fun parse(fileName: String, path: String): ParsedName {
        val base = fileName.substringBeforeLast('.', fileName)
        val folders = path.split('/').filter { it.isNotBlank() }
        val seasonFromFolder = folders.lastOrNull()?.let { seasonFolder.find(it.trim())?.groupValues?.get(1)?.toInt() }

        val match = sxe.find(base) ?: nxn.find(base) ?: seasonEpisodeWords.find(base)
        if (match != null) {
            val (s, e) = match.destructured
            return episode(base.substring(0, match.range.first), base.substring(match.range.last + 1), s.toInt(), e.toInt(), folders)
        }
        // "Show/Season 2/Episode 5.mkv", "Show/Season 2/05 - Title.mkv"
        if (seasonFromFolder != null) {
            val m = episodeWord.find(base) ?: leadingNumber.find(base)
            if (m != null) {
                return episode(base.substring(0, m.range.first), base.substring(m.range.last + 1), seasonFromFolder, m.groupValues[1].toInt(), folders)
            }
        }
        return movie(base, folders)
    }

    private fun episode(before: String, after: String, season: Int, number: Int, folders: List<String>): EpisodeName {
        var show = clean(before)
        var showYear: Int? = null
        year.findAll(show).lastOrNull()?.let { y ->
            // "Lanterns 2026" or "Lanterns (2026)": the year belongs to the show, not its name.
            if (y.range.first > 0) {
                showYear = y.value.toInt()
                show = clean(show.substring(0, y.range.first))
            }
        }
        if (show.isBlank()) {
            // "Breaking Bad/Season 01/S01E01.mkv": the show is the nearest folder that isn't a season.
            show = folders.lastOrNull { seasonFolder.find(it.trim()) == null }?.let { clean(stripYear(it)) }.orEmpty()
        }
        val title = clean(cutJunk(after)).takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
        return EpisodeName(show.ifBlank { "Unknown show" }, showYear, season, number, title)
    }

    private fun movie(base: String, folders: List<String>): MovieName {
        val (title, y) = titleAndYear(base)
        if (y == null) {
            // "The Dark Knight (2008)/movie.mkv": the folder has the better name.
            val folder = folders.lastOrNull()?.let { titleAndYear(it) }
            if (folder?.second != null) return MovieName(folder.first, folder.second)
        }
        return MovieName(title.ifBlank { clean(base) }, y)
    }

    private fun titleAndYear(raw: String): Pair<String, Int?> {
        val text = stripBrackets(raw)
        // The last plausible year that has some title before it: "2001 A Space Odyssey 1968" -> 1968.
        val y = year.findAll(text).lastOrNull { it.range.first > 0 && clean(text.substring(0, it.range.first)).isNotBlank() }
        if (y != null) return clean(text.substring(0, y.range.first)) to y.value.toInt()
        return clean(cutJunk(text)) to null
    }

    /** Drops "[Group]" and "(1080p)"-style bracket blocks that don't contain a year. */
    private fun stripBrackets(s: String) = s
        .replace(Regex("""\[[^\]]*]"""), " ")
        .replace(Regex("""\((?![^)]*(19|20)\d\d)[^)]*\)"""), " ")

    private fun stripYear(s: String) = s.replace(Regex("""[(\[]?(19|20)\d\d[)\]]?"""), " ")

    private fun cutJunk(s: String): String {
        val m = junk.find(s) ?: return s
        return s.substring(0, m.range.first)
    }

    /**
     * Dots and underscores become spaces; separators at the ends go, as do an opening bracket left
     * at the end ("Inception (" before a year) or a closing one at the start. "(US)" stays whole.
     */
    fun clean(s: String): String = s
        .replace('_', ' ')
        .replace(Regex("""(?<![0-9])\.|\.(?![0-9])"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trimEnd { it.isWhitespace() || it in "-–—.:,([{" }
        .trimStart { it.isWhitespace() || it in "-–—.:,)]}" }

    /** A spelling-insensitive key for grouping and caching: "The Office (US)" -> "theofficeus". */
    fun key(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
}
