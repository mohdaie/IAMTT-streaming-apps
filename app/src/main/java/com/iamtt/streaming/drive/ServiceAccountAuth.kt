package com.iamtt.streaming.drive

import com.iamtt.streaming.data.AppJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

@Serializable
data class ServiceAccountKey(
    val type: String? = null,
    @SerialName("client_email") val clientEmail: String,
    @SerialName("private_key") val privateKey: String,
    @SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
    @SerialName("project_id") val projectId: String? = null,
) {
    companion object {
        /** Parses and sanity-checks a key file; throws IllegalArgumentException with a readable message. */
        fun parse(json: String): ServiceAccountKey {
            val key = try {
                AppJson.decodeFromString(serializer(), json)
            } catch (e: Exception) {
                throw IllegalArgumentException("That file isn't a Google service account key (JSON).")
            }
            require(key.type == null || key.type == "service_account") {
                "This JSON is a '${key.type}' file, not a service account key."
            }
            require(key.privateKey.contains("PRIVATE KEY")) { "The key file has no private key in it." }
            return key
        }
    }
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

/**
 * Gets Drive access tokens for a service account by signing a JWT with its private key
 * (Google's "two-legged" OAuth flow). No Google sign-in on the TV is needed.
 *
 * Scope is read-only: the app can never change or delete anything in Drive.
 */
class ServiceAccountAuth(
    private val keyProvider: () -> String?,
    private val http: OkHttpClient,
) {
    private data class Cached(val email: String, val token: String, val expiresAtMs: Long)

    @Volatile private var cached: Cached? = null
    private val lock = Any()

    /** Blocking. Call from a background thread (OkHttp interceptors already are). */
    fun token(forceRefresh: Boolean = false): String {
        val json = keyProvider() ?: throw IllegalStateException("No service account key set up yet.")
        val key = ServiceAccountKey.parse(json)
        synchronized(lock) {
            val c = cached
            val now = System.currentTimeMillis()
            if (!forceRefresh && c != null && c.email == key.clientEmail && c.expiresAtMs - now > 60_000) {
                return c.token
            }
            val fresh = fetchToken(key)
            cached = fresh
            return fresh.token
        }
    }

    fun invalidate() {
        synchronized(lock) { cached = null }
    }

    private fun fetchToken(key: ServiceAccountKey): Cached {
        val nowSec = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = buildJsonObject {
            put("iss", key.clientEmail)
            put("scope", DRIVE_READONLY_SCOPE)
            put("aud", key.tokenUri)
            put("iat", nowSec)
            put("exp", nowSec + 3600)
        }.toString()
        val unsigned = b64(header.toByteArray()) + "." + b64(claims.toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(loadPrivateKey(key.privateKey))
            update(unsigned.toByteArray())
            sign()
        }
        val jwt = unsigned + "." + b64(signature)

        val request = Request.Builder()
            .url(key.tokenUri)
            .post(
                FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    .add("assertion", jwt)
                    .build()
            )
            .build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw DriveException("Google refused the key (HTTP ${resp.code}): ${body.take(300)}", resp.code)
            }
            val token = AppJson.decodeFromString(TokenResponse.serializer(), body)
            return Cached(
                email = key.clientEmail,
                token = token.accessToken,
                expiresAtMs = System.currentTimeMillis() + token.expiresIn * 1000,
            )
        }
    }

    companion object {
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

        private fun b64(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun loadPrivateKey(pem: String): RSAPrivateKey {
            val base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replace(Regex("\\s"), "")
            val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
            return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
        }
    }
}
