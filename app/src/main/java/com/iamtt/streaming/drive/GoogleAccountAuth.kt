package com.iamtt.streaming.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Gets Drive access tokens for a Google account on this TV through Google Play services.
 * The first time, the setup screen shows the account picker and Google's consent screen;
 * after that, tokens are fetched silently in the background.
 *
 * Scope is read-only: the app can never change or delete anything in Drive.
 */
class GoogleAccountAuth(
    context: Context,
    private val accountProvider: () -> String?,
) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    private data class Cached(val account: String, val token: String, val fetchedAtMs: Long)

    @Volatile private var cached: Cached? = null
    private val lock = Any()

    /** Blocking. Call from a background thread (OkHttp interceptors already are). */
    fun token(forceRefresh: Boolean = false): String {
        val account = accountProvider() ?: throw IllegalStateException("Not signed in to Google yet.")
        synchronized(lock) {
            val c = cached
            val now = System.currentTimeMillis()
            if (c != null && c.account == account) {
                if (!forceRefresh && now - c.fetchedAtMs < CACHE_MS) return c.token
                // Play services keeps handing back its cached token until told it's stale.
                if (forceRefresh) runCatching { await(client.clearToken(ClearTokenRequest.builder().setToken(c.token).build())) }
            }
            val result = await(client.authorize(request(account)))
            if (result.hasResolution()) {
                throw DriveException("Google sign-in for $account has expired. Open Settings and press Switch account to sign in again.")
            }
            val token = result.accessToken ?: throw DriveException("Google didn't return an access token.")
            cached = Cached(account, token, now)
            return token
        }
    }

    fun invalidate() {
        synchronized(lock) { cached = null }
    }

    /**
     * First step of signing in. The result either carries a token already (access was granted
     * before) or a consent screen to show. With no [account], Google asks which one to use.
     */
    suspend fun authorize(account: String?): AuthorizationResult = withContext(Dispatchers.IO) {
        await(client.authorize(request(account)))
    }

    /** Reads the outcome of the consent screen started from [authorize]. */
    fun resultFromIntent(data: Intent?): AuthorizationResult = try {
        client.getAuthorizationResultFromIntent(data)
    } catch (e: ApiException) {
        throw describe(e)
    }

    private fun <T> await(task: Task<T>): T = try {
        Tasks.await(task, 30, TimeUnit.SECONDS)
    } catch (e: Exception) {
        throw describe(e)
    }

    companion object {
        const val ACCOUNT_TYPE = "com.google"
        private val SCOPES = listOf(Scope(ServiceAccountAuth.DRIVE_READONLY_SCOPE))

        /** Play services tokens last an hour; re-asking it every few minutes is cheap and never stale. */
        private const val CACHE_MS = 10 * 60_000L

        private fun request(account: String?): AuthorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES)
            .apply { if (account != null) setAccount(Account(account, ACCOUNT_TYPE)) }
            .build()

        /** Turns Play services errors into messages that say what to do about them. */
        fun describe(e: Throwable): Exception {
            val cause = (e as? ExecutionException)?.cause ?: e
            if (cause is TimeoutException) return DriveException("Google Play services didn't answer in time. Try again.")
            val api = cause as? ApiException ?: return cause as? Exception ?: Exception(cause)
            return DriveException(
                when (api.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR ->
                        "Google sign-in isn't set up for this app yet. Create the Android OAuth client " +
                            "in Google Cloud (see the setup guide), wait a few minutes, then try again."
                    CommonStatusCodes.NETWORK_ERROR -> "Couldn't reach Google. Check the TV's internet connection."
                    CommonStatusCodes.CANCELED -> "Sign-in was cancelled."
                    else -> "Google sign-in failed (${CommonStatusCodes.getStatusCodeString(api.statusCode)})."
                }
            )
        }
    }
}
