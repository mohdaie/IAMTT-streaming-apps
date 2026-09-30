package com.iamtt.streaming.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.drive.DriveException
import com.iamtt.streaming.drive.GoogleAccountAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Sign in with Google" on the TV; call [start] from a button. */
class GoogleSignIn internal constructor() {
    var busy by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    internal var launch: (switching: Boolean) -> Unit = {}
    internal var pendingAccount: String? = null

    /** [switching] always shows the account list, so another account can be picked or added. */
    fun start(switching: Boolean = false) {
        if (!busy) launch(switching)
    }
}

/**
 * Signs in with a Google account that's on this TV: Android's account picker (with an option
 * to add another account), then Google's consent screen for read-only Drive access.
 * On success the account becomes the app's way into Drive and the library rescans.
 */
@Composable
fun rememberGoogleSignIn(app: IamttApp): GoogleSignIn {
    val state = remember { GoogleSignIn() }
    val scope = rememberCoroutineScope()
    val google = app.auth.google

    fun fail(e: Throwable) {
        state.busy = false
        state.error = e.message ?: "Sign-in failed."
    }

    fun finish(account: String?, result: AuthorizationResult) {
        scope.launch {
            try {
                val token = result.accessToken ?: throw IllegalStateException("Google didn't return an access token.")
                // Proves Drive access actually works before switching the app over to it.
                val user = app.drive.whoAmI(token)
                val email = account ?: user.emailAddress
                    ?: throw IllegalStateException("Couldn't tell which Google account was used.")
                withContext(Dispatchers.IO) {
                    google.invalidate()
                    app.config.setGoogleAccount(email)
                }
                app.library.rescan()
                state.error = null
            } catch (e: Exception) {
                state.error = e.message ?: "Sign-in failed."
            }
            state.busy = false
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        // Google's screen also closes as "cancelled" when the Cloud setup is wrong; the returned
        // intent carries the real status, so read it whatever the result code says.
        val data = res.data
        if (data == null) {
            fail(DriveException(GoogleAccountAuth.CANCELLED))
        } else {
            try {
                finish(state.pendingAccount, google.resultFromIntent(data))
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun authorize(account: String?) {
        state.pendingAccount = account
        scope.launch {
            try {
                val result = google.authorize(account)
                val intent = result.pendingIntent
                if (result.hasResolution() && intent != null) {
                    consent.launch(IntentSenderRequest.Builder(intent).build())
                } else {
                    finish(account, result)
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val name = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (res.resultCode != Activity.RESULT_OK || name == null) fail(Exception("No account was chosen."))
        else authorize(name)
    }

    state.launch = { switching ->
        state.busy = true
        state.error = null
        val current = app.config.current.googleAccount?.let { Account(it, GoogleAccountAuth.ACCOUNT_TYPE) }
        @Suppress("DEPRECATION") // The newer overload can't force the list to show when there's one account.
        val intent = AccountManager.newChooseAccountIntent(
            current, null, arrayOf(GoogleAccountAuth.ACCOUNT_TYPE), switching, null, null, null, null,
        )
        try {
            picker.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // No system account picker on this TV: let Google's own screen ask which account.
            authorize(null)
        }
    }
    return state
}
