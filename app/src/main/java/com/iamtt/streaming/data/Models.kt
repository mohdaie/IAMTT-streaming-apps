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

@Serializable
data class AppConfig(
    /** Full service-account JSON key. Stored only on this device, never shown back. */
    val serviceAccountJson: String? = null,
    val serviceAccountEmail: String? = null,
    val folders: List<LibraryFolder> = emptyList(),
) {
    val hasKey: Boolean get() = !serviceAccountJson.isNullOrBlank()
    val isReady: Boolean get() = hasKey && folders.isNotEmpty()
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
) {
    val displayName: String
        get() = name.substringBeforeLast('.', name)
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}

@Serializable
data class FolderScan(
    val folder: LibraryFolder,
    val videos: List<VideoFile>,
    val scannedAt: Long,
    val error: String? = null,
)

@Serializable
data class LibrarySnapshot(val scans: List<FolderScan> = emptyList())
