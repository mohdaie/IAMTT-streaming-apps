package com.iamtt.streaming.data

import java.util.Locale

/** Finds which video each subtitle file belongs to, from where it sits and what it's called. */
object Subtitles {
    val EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
    private val subsFolder = Regex("""(?i)^(subs?|subtitles?)$""")
    private val extras = setOf("forced", "sdh", "cc", "hi", "full")

    fun isSubtitle(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** A subtitle file as the scan found it; [path] is its folder, like VideoFile.path. */
    data class Found(val id: String, val name: String, val path: String)

    fun attach(videos: List<VideoFile>, subtitles: List<Found>): List<VideoFile> {
        if (subtitles.isEmpty()) return videos
        val byDir = videos.groupBy { it.path.lowercase() }
        val attached = mutableMapOf<String, MutableList<SubtitleFile>>()
        for (sub in subtitles) {
            val (video, labelSource) = match(sub, byDir) ?: continue
            attached.getOrPut(video.id) { mutableListOf() } += SubtitleFile(sub.id, sub.name, label(labelSource).first, label(labelSource).second)
        }
        return videos.map { v -> attached[v.id]?.let { v.copy(subtitles = it.sortedBy { s -> s.label }) } ?: v }
    }

    /** The video [sub] belongs to, and the part of its name that describes it (language etc.). */
    private fun match(sub: Found, byDir: Map<String, List<VideoFile>>): Pair<VideoFile, String>? {
        val base = sub.name.substringBeforeLast('.')
        val folders = sub.path.split('/').filter { it.isNotBlank() }
        // Right next to the video, or one level down in "Subs".
        val dirs = buildList {
            add(sub.path)
            if (folders.isNotEmpty() && subsFolder.matches(folders.last())) add(folders.dropLast(1).joinToString("/"))
        }
        for (dir in dirs) {
            val videos = byDir[dir.lowercase()].orEmpty()
            videos.filter { base.lowercase().startsWith(stem(it).lowercase()) }.maxByOrNull { stem(it).length }?.let {
                return it to base.substring(stem(it).length)
            }
            if (videos.size == 1) return videos[0] to base
        }
        // "Subs/<episode file name>/2_English.srt", as many TV releases ship them.
        if (folders.size >= 2 && subsFolder.matches(folders[folders.size - 2])) {
            val dir = folders.dropLast(2).joinToString("/")
            byDir[dir.lowercase()].orEmpty().firstOrNull { stem(it).equals(folders.last(), ignoreCase = true) }?.let { return it to base }
        }
        return null
    }

    private fun stem(v: VideoFile) = v.name.substringBeforeLast('.')

    /** "en", ".English", "2_English", "eng.forced" -> a readable label and, when there is one, a language code. */
    fun label(rest: String): Pair<String, String?> {
        val tokens = rest.split('.', '_', '-', ' ', '[', ']', '(', ')')
            .map { it.trim() }.filter { it.isNotBlank() && !it.all(Char::isDigit) }
        var language: String? = null
        val words = mutableListOf<String>()
        val flags = mutableListOf<String>()
        for (t in tokens) {
            val lower = t.lowercase()
            when {
                lower in extras -> flags += lower.uppercase().takeIf { it.length <= 3 } ?: lower.replaceFirstChar { it.uppercase() }
                language == null && (lower.length in 2..3) && isLanguageCode(lower) -> language = twoLetter(lower)
                language == null && languageNamed(t) != null -> language = languageNamed(t)
                else -> words += t
            }
        }
        val name = language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).takeIf(String::isNotBlank) }
            ?: words.joinToString(" ").ifBlank { "Subtitles" }
        return (if (flags.isEmpty()) name else "$name (${flags.joinToString(", ")})") to language
    }

    private val codes: Set<String> by lazy {
        (Locale.getISOLanguages().toSet() + Locale.getISOLanguages().mapNotNull {
            runCatching { Locale.forLanguageTag(it).isO3Language }.getOrNull()
        }.toSet() + setOf("may", "chi", "fre", "ger", "dut", "per", "gre"))
    }

    private fun isLanguageCode(s: String) = s in codes

    /** "English", "eng", "EN" or "Bahasa" -> a two-letter code; null if it isn't a language. */
    fun normalizeLanguage(s: String): String? {
        val w = s.trim().lowercase()
        if (w.length in 2..3 && isLanguageCode(w)) return twoLetter(w)
        return languageNamed(w)
    }

    /** Players match two-letter codes, so "eng" -> "en", and old library codes like "may" -> "ms". */
    private val bibliographic = mapOf("may" to "ms", "chi" to "zh", "fre" to "fr", "ger" to "de", "dut" to "nl", "per" to "fa", "gre" to "el")

    fun twoLetter(code: String): String {
        if (code.length == 2) return code
        bibliographic[code]?.let { return it }
        return Locale.getISOLanguages().firstOrNull { runCatching { Locale.forLanguageTag(it).isO3Language }.getOrNull() == code } ?: code
    }

    /** "English" -> "en", "Malay" -> "ms", "Bahasa" -> "ms". */
    private fun languageNamed(word: String): String? {
        val w = word.lowercase()
        if (w == "bahasa" || w == "melayu") return "ms"
        return Locale.getISOLanguages().firstOrNull { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).equals(w, ignoreCase = true) }
    }
}
