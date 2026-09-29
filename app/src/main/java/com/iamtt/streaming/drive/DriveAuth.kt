package com.iamtt.streaming.drive

import com.iamtt.streaming.data.ConfigStore

/**
 * Picks where Drive access tokens come from: the Google account signed in on this TV,
 * or a service account key uploaded from the phone (the advanced option).
 */
class DriveAuth(
    private val config: ConfigStore,
    val serviceAccount: ServiceAccountAuth,
    val google: GoogleAccountAuth,
) {
    val usesServiceAccount: Boolean get() = !config.current.usesGoogleAccount

    /** Blocking. Call from a background thread (OkHttp interceptors already are). */
    fun token(forceRefresh: Boolean = false): String =
        if (usesServiceAccount) serviceAccount.token(forceRefresh) else google.token(forceRefresh)
}
