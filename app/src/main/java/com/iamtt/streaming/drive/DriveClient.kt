package com.iamtt.streaming.drive

import com.iamtt.streaming.data.AppJson
import com.iamtt.streaming.data.LibraryFolder
import com.iamtt.streaming.data.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

class DriveException(message: String, val code: Int = 0) : Exception(message)

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val modifiedTime: String? = null,
    val videoMediaMetadata: VideoMeta? = null,
    val shortcutDetails: ShortcutDetails? = null,
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME
}

@Serializable
data class VideoMeta(val durationMillis: Long? = null, val width: Int? = null, val height: Int? = null)

@Serializable
data class ShortcutDetails(val targetId: String? = null, val targetMimeType: String? = null)

@Serializable
private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

const val FOLDER_MIME = "application/vnd.google-apps.folder"
private const val SHORTCUT_MIME = "application/vnd.google-apps.shortcut"
private const val API = "https://www.googleapis.com/drive/v3"

private val VIDEO_EXTENSIONS = setOf(
    "mkv", "mp4", "m4v", "avi", "mov", "ts", "m2ts", "webm", "wmv", "mpg", "mpeg", "flv", "3gp",
)

fun isVideo(file: DriveFile): Boolean {
    if (file.isFolder) return false
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return ext in VIDEO_EXTENSIONS || file.mimeType.startsWith("video/")
}

/** Accepts a Drive folder link in any of its usual shapes, or a bare folder ID. */
fun parseFolderId(input: String): String? {
    val s = input.trim()
    Regex("/folders/([A-Za-z0-9_-]{10,})").find(s)?.let { return it.groupValues[1] }
    Regex("[?&]id=([A-Za-z0-9_-]{10,})").find(s)?.let { return it.groupValues[1] }
    if (Regex("^[A-Za-z0-9_-]{10,}$").matches(s)) return s
    return null
}

/**
 * Minimal Google Drive v3 client. Every call is read-only, and scanning is limited to
 * the folders passed in — nothing outside them is ever listed.
 */
class DriveClient(auth: ServiceAccountAuth, base: OkHttpClient) {

    /** Adds the access token to googleapis.com requests and retries once on 401. Also used by the player. */
    val http: OkHttpClient = base.newBuilder()
        .addInterceptor(AuthInterceptor(auth))
        .authenticator { _: Route?, response: Response ->
            if (response.priorResponse != null) return@authenticator null
            if (response.request.url.host != "www.googleapis.com") return@authenticator null
            val fresh = runCatching { auth.token(forceRefresh = true) }.getOrNull() ?: return@authenticator null
            response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun mediaUrl(fileId: String): String = "$API/files/$fileId?alt=media&supportsAllDrives=true"

    suspend fun getFolder(id: String): DriveFile = withContext(Dispatchers.IO) {
        val url = "$API/files/$id".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,name,mimeType")
            .addQueryParameter("supportsAllDrives", "true")
            .build()
        val file = get(url, DriveFile.serializer())
        if (!file.isFolder) throw DriveException("\"${file.name}\" is a file, not a folder.")
        file
    }

    /** Folders someone shared with the service account — i.e. the ones the app is allowed to see. */
    suspend fun sharedFolders(): List<DriveFile> = withContext(Dispatchers.IO) {
        listAll("sharedWithMe = true and mimeType = '$FOLDER_MIME' and trashed = false", "name")
    }

    /** Recursively lists every video under [folder] (and only under it). */
    suspend fun scan(folder: LibraryFolder, onProgress: (Int) -> Unit = {}): List<VideoFile> = coroutineScope {
        val permits = Semaphore(6)
        val found = java.util.concurrent.ConcurrentLinkedQueue<VideoFile>()
        val visited = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        suspend fun walk(folderId: String, path: String, depth: Int) {
            if (depth > 12 || !visited.add(folderId)) return
            val children = permits.withPermit {
                withContext(Dispatchers.IO) {
                    listAll("'$folderId' in parents and trashed = false", "name")
                }
            }
            val subfolders = mutableListOf<Pair<String, String>>()
            for (f in children) {
                val childPath = if (path.isEmpty()) f.name else "$path/${f.name}"
                when {
                    f.isFolder -> subfolders += f.id to childPath
                    f.mimeType == SHORTCUT_MIME && f.shortcutDetails?.targetMimeType == FOLDER_MIME ->
                        f.shortcutDetails?.targetId?.let { subfolders += it to childPath }
                    isVideo(f) -> found += VideoFile(
                        id = f.id,
                        name = f.name,
                        path = path,
                        size = f.size ?: 0,
                        modifiedTime = f.modifiedTime,
                        durationMs = f.videoMediaMetadata?.durationMillis,
                        width = f.videoMediaMetadata?.width,
                        height = f.videoMediaMetadata?.height,
                    )
                }
            }
            onProgress(found.size)
            subfolders.map { (id, p) -> async { walk(id, p, depth + 1) } }.awaitAll()
        }

        walk(folder.id, "", 0)
        found.sortedWith(compareBy<VideoFile>({ it.path.lowercase() }, { it.name.lowercase() }))
    }

    private fun listAll(query: String, orderBy: String): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("orderBy", orderBy)
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter(
                    "fields",
                    "nextPageToken,files(id,name,mimeType,size,modifiedTime," +
                        "videoMediaMetadata(durationMillis,width,height),shortcutDetails(targetId,targetMimeType))"
                )
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = get(url, FileList.serializer())
            out += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return out
    }

    private fun <T> get(url: HttpUrl, serializer: kotlinx.serialization.KSerializer<T>): T {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw DriveException(explain(resp.code, body), resp.code)
            return AppJson.decodeFromString(serializer, body)
        }
    }

    private fun explain(code: Int, body: String): String = when (code) {
        404 -> "Folder not found. Share it with the app's email address (as Viewer) first."
        403 -> if ("accessNotConfigured" in body || "has not been used" in body)
            "Google Drive API isn't turned on for your Google Cloud project yet."
        else "Google Drive refused access (403). ${body.take(200)}"
        401 -> "The service account key was rejected."
        else -> "Google Drive error $code: ${body.take(200)}"
    }

    private class AuthInterceptor(private val auth: ServiceAccountAuth) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (req.url.host != "www.googleapis.com" || req.header("Authorization") != null) {
                return chain.proceed(req)
            }
            // OkHttp only tolerates IOExceptions from interceptors; anything else can crash
            // the player's network thread, so wrap key/token problems.
            val token = try {
                auth.token()
            } catch (e: java.io.IOException) {
                throw e
            } catch (e: Exception) {
                throw java.io.IOException(e.message ?: "Could not get a Google access token", e)
            }
            return chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }
}
