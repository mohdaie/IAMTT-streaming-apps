package com.iamtt.streaming.setup

import android.content.Context
import android.util.Log
import com.iamtt.streaming.data.AppJson
import com.iamtt.streaming.data.ConfigStore
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.LibraryFolder
import com.iamtt.streaming.data.LibraryRepository
import com.iamtt.streaming.drive.DriveAuth
import com.iamtt.streaming.drive.DriveClient
import com.iamtt.streaming.drive.DriveFile
import com.iamtt.streaming.drive.ServiceAccountAuth
import com.iamtt.streaming.drive.ServiceAccountKey
import com.iamtt.streaming.drive.parseFolderId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * A tiny web server that runs on the TV only while the setup screen is open.
 * Your phone opens it (via the QR code) to pick which Drive folders to use, or to upload
 * a service account key instead of signing in on the TV. Every API call needs the 4-digit
 * PIN shown on the TV, so someone else on the Wi-Fi can't change your setup.
 */
class SetupServer(
    private val context: Context,
    private val config: ConfigStore,
    private val drive: DriveClient,
    private val auth: DriveAuth,
    private val library: LibraryRepository,
) {
    val pin: String = Random.nextInt(1000, 10000).toString()
    var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)
    @Volatile private var running = false

    /** A 4-digit PIN is guessable, so this setup session stops accepting any after too many misses. */
    private val wrongPins = AtomicInteger(0)

    fun start(): Int {
        if (running) return port
        val socket = (listOf(8765, 8766, 8767, 0)).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull()
        } ?: throw IllegalStateException("Could not open a network port for setup")
        server = socket
        port = socket.localPort
        running = true
        Thread({
            while (running) {
                val client = try { socket.accept() } catch (e: Exception) { break }
                pool.execute { handle(client) }
            }
        }, "iamtt-setup").apply { isDaemon = true }.start()
        return port
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        pool.shutdownNow()
    }

    // ---------------------------------------------------------------- HTTP plumbing

    private class HttpRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private class HttpResponse(val status: Int, val contentType: String, val body: ByteArray)

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            val response = try {
                val req = readRequest(BufferedInputStream(s.getInputStream())) ?: return
                route(req)
            } catch (e: Exception) {
                Log.w(TAG, "setup request failed", e)
                error(500, e.message ?: "Something went wrong")
            }
            writeResponse(s.getOutputStream(), response)
        }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val head = ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (matched < 4) {
            val b = input.read()
            if (b == -1) return null
            head.write(b)
            matched = if (b.toByte() == terminator[matched]) matched + 1 else if (b.toByte() == terminator[0]) 1 else 0
            if (head.size() > 16 * 1024) throw IllegalArgumentException("Headers too large")
        }
        val lines = head.toString(Charsets.UTF_8.name()).split("\r\n").filter { it.isNotEmpty() }
        val (method, target) = lines.first().split(" ").let { it[0] to it.getOrElse(1) { "/" } }
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length > 256 * 1024) throw IllegalArgumentException("Upload too large")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n == -1) break
            read += n
        }
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return HttpRequest(method.uppercase(), path, query, headers, String(body, 0, read, Charsets.UTF_8))
    }

    private fun writeResponse(out: OutputStream, r: HttpResponse) {
        val reason = when (r.status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error" }
        val header = "HTTP/1.1 ${r.status} $reason\r\n" +
            "Content-Type: ${r.contentType}\r\n" +
            "Content-Length: ${r.body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(r.body)
        out.flush()
    }

    private fun json(status: Int = 200, element: JsonElement) =
        HttpResponse(status, "application/json; charset=utf-8", element.toString().toByteArray(Charsets.UTF_8))

    private fun error(status: Int, message: String) = json(status, buildJsonObject { put("error", message) })

    // ---------------------------------------------------------------- routes

    private fun route(req: HttpRequest): HttpResponse {
        if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) {
            val html = context.assets.open("setup.html").use { it.readBytes() }
            return HttpResponse(200, "text/html; charset=utf-8", html)
        }
        if (!req.path.startsWith("/api/")) return error(404, "Not found")

        if (wrongPins.get() >= MAX_WRONG_PINS) {
            return error(403, "Too many wrong PINs. Press Back on the TV and open setup again for a new PIN.")
        }
        val given = req.headers["x-pin"] ?: req.query["pin"]
        if (given != pin) {
            wrongPins.incrementAndGet()
            return error(403, "Wrong PIN. Use the 4-digit PIN shown on the TV.")
        }

        return when ("${req.method} ${req.path}") {
            "GET /api/state" -> json(element = stateJson())
            "POST /api/key" -> saveKey(req.body)
            "GET /api/shared" -> sharedFolders()
            "GET /api/browse" -> browse(req.query["parent"])
            "POST /api/folders" -> addFolder(req.body)
            "POST /api/folders/remove" -> removeFolder(req.body)
            "POST /api/rescan" -> { library.rescan(); json(element = stateJson()) }
            else -> error(404, "Unknown API call")
        }
    }

    private fun stateJson(): JsonObject {
        val cfg = config.current
        val lib = library.state.value
        return buildJsonObject {
            put("mode", when {
                cfg.usesGoogleAccount -> "google"
                cfg.hasKey -> "serviceAccount"
                else -> "none"
            })
            cfg.googleAccount?.let { put("account", it) }
            put("hasKey", cfg.hasKey)
            cfg.serviceAccountEmail?.let { put("email", it) }
            put("scanning", lib.scanning)
            lib.lastError?.let { put("lastError", it) }
            put("folders", buildJsonArray {
                cfg.folders.forEach { f ->
                    val scan = lib.snapshot.scans.firstOrNull { it.folder.id == f.id }
                    add(buildJsonObject {
                        put("id", f.id)
                        put("name", f.name)
                        put("type", f.type.name)
                        scan?.let { put("videos", it.videos.size) }
                        scan?.error?.let { put("error", it) }
                    })
                }
            })
        }
    }

    private fun saveKey(body: String): HttpResponse {
        val key = try {
            ServiceAccountKey.parse(body)
        } catch (e: IllegalArgumentException) {
            return error(400, e.message ?: "Invalid key file")
        }
        // Prove the key works before saving it.
        val probe = ServiceAccountAuth(keyProvider = { body }, http = OkHttpClient())
        try {
            probe.token()
        } catch (e: Exception) {
            return error(400, "Google didn't accept this key: ${e.message}")
        }
        config.setKey(body.trim(), key.clientEmail)
        auth.serviceAccount.invalidate()
        return json(element = stateJson())
    }

    private fun sharedFolders(): HttpResponse {
        if (!config.current.hasAccess) return error(400, NOT_CONNECTED)
        return folderList(runBlocking { drive.sharedFolders() })
    }

    private fun browse(parent: String?): HttpResponse {
        if (!config.current.hasAccess) return error(400, NOT_CONNECTED)
        val folders = try {
            runBlocking { drive.childFolders(parent?.takeIf { it.isNotBlank() } ?: "root") }
        } catch (e: IllegalArgumentException) {
            return error(400, e.message ?: "Bad folder")
        }
        return folderList(folders)
    }

    private fun folderList(folders: List<DriveFile>): HttpResponse {
        val chosen = config.current.folders.associateBy { it.id }
        return json(element = buildJsonObject {
            put("folders", JsonArray(folders.map { f ->
                buildJsonObject {
                    put("id", f.id)
                    put("name", f.name)
                    chosen[f.id]?.let { put("type", it.type.name) }
                }
            }))
        })
    }

    private fun addFolder(body: String): HttpResponse {
        if (!config.current.hasAccess) return error(400, NOT_CONNECTED)
        val obj = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return error(400, "Bad request")
        val input = obj["input"]?.jsonPrimitive?.content.orEmpty()
        val type = runCatching { FolderType.valueOf(obj["type"]?.jsonPrimitive?.content.orEmpty()) }.getOrNull()
            ?: return error(400, "Choose Movies or TV Shows")
        val id = parseFolderId(input) ?: return error(400, "That doesn't look like a Google Drive folder link.")
        val folder = try {
            runBlocking { drive.getFolder(id) }
        } catch (e: Exception) {
            return error(400, e.message ?: "Couldn't open that folder")
        }
        config.addFolder(LibraryFolder(id = folder.id, name = folder.name, type = type))
        library.syncWithConfig()
        library.rescan(folder.id)
        return json(element = stateJson())
    }

    private fun removeFolder(body: String): HttpResponse {
        val id = runCatching {
            (AppJson.parseToJsonElement(body).jsonObject["id"] as JsonPrimitive).content
        }.getOrNull() ?: return error(400, "Bad request")
        config.removeFolder(id)
        library.syncWithConfig()
        return json(element = stateJson())
    }

    companion object {
        private const val TAG = "SetupServer"
        private const val MAX_WRONG_PINS = 10
        private const val NOT_CONNECTED = "Sign in with Google on the TV first (or upload a service account key)."

        /** The TV's address on the home network, e.g. 192.168.1.23. */
        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .sortedBy { if (it.hostAddress?.startsWith("192.168.") == true) 0 else 1 }
                .firstOrNull()?.hostAddress
        }.getOrNull()
    }
}
