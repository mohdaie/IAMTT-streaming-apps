package com.iamtt.streaming.data

import kotlinx.serialization.Serializable

@Serializable
enum class FolderType { MOVIES, TV_SHOWS }

/** A Google Drive folder the user chose to include. Only these folders are ever scanned. */
@Serializable
data class LibraryFolder(
    val id: String,
    val name: String,
    val type: FolderType,
)

/** Someone who watches: their own name, picture and Continue Watching row. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** Which built-in avatar to show when there's no photo. */
    val avatar: Int = 0,
    /** Set when the picture is a photo; changes whenever the photo does, so screens reload it. */
    val photoVersion: Long? = null,
)

/** OpenSubtitles.com account for fetching subtitles online. Stored only on this device. */
@Serializable
data class OpenSubtitlesSettings(
    val apiKey: String,
    /** Optional; logged-in users get more downloads a day. */
    val username: String? = null,
    val password: String? = null,
    /** Two-letter codes in order of preference; empty means the phone's language, then English. */
    val languages: List<String> = emptyList(),
)

@Serializable
data class AppConfig(
    /** Email of the Google account on this TV that the app signed in with. */
    val googleAccount: String? = null,
    /** Full service-account JSON key. Stored only on this device, never shown back. */
    val serviceAccountJson: String? = null,
    val serviceAccountEmail: String? = null,
    val folders: List<LibraryFolder> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val openSubtitles: OpenSubtitlesSettings? = null,
) {
    /** Only one way of reaching Drive is active at a time; signing in with Google takes precedence. */
    val usesGoogleAccount: Boolean get() = !googleAccount.isNullOrBlank()
    val hasKey: Boolean get() = !serviceAccountJson.isNullOrBlank()
    val hasAccess: Boolean get() = usesGoogleAccount || hasKey
    val isReady: Boolean get() = hasAccess && folders.isNotEmpty()
}

/** One playable video found inside a library folder. */
@Serializable
data class VideoFile(
    val id: String,
    val name: String,
    /** Path of sub-folders below the library folder, e.g. "Breaking Bad/Season 01". Empty at the top. */
    val path: String,
    val size: Long = 0,
    val modifiedTime: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Subtitle files found next to this video (or in a "Subs" folder beside it). */
    val subtitles: List<SubtitleFile> = emptyList(),
) {
    val displayName: String
        get() = name.substringBeforeLast('.', name)
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}

/** A subtitle file in Drive; [language] is an ISO code when the file name gives one ("en", "ms"). */
@Serializable
data class SubtitleFile(val id: String, val name: String, val label: String, val language: String? = null)

@Serializable
data class FolderScan(
    val folder: LibraryFolder,
    val videos: List<VideoFile>,
    val scannedAt: Long,
    val error: String? = null,
)

@Serializable
data class LibrarySnapshot(val scans: List<FolderScan> = emptyList())
