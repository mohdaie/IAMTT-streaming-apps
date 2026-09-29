package com.iamtt.streaming

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** TV Material buttons ignore touch on their own; on a phone or tablet, tapping must still work. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w780dp-h360dp-land-xxhdpi") // a phone held sideways
class TouchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingSignInOpensTheGoogleAccountPicker() {
        compose.onNodeWithText("Sign in with Google").performTouchInput { click() }
        compose.waitForIdle()

        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull("Tapping Sign in with Google didn't open anything", started)
        assertEquals(listOf("com.google"), started.intent.getStringArrayExtra("allowableAccountTypes")?.toList())
    }

    /** Leaving for a browser app would background IAMTT, and Android then cuts its internet access. */
    @Test
    fun choosingFoldersOnAPhoneStaysInsideTheApp() {
        compose.runOnUiThread { compose.activity.application.let { it as IamttApp }.config.setGoogleAccount("me@gmail.com") }
        compose.onNodeWithText("Choose folders here").performTouchInput { click() }
        compose.waitForIdle()

        assertNull("Opened another app", shadowOf(compose.activity).nextStartedActivity)
        val webView = findWebView(compose.activity.window.decorView)
        assertNotNull("The folder picker didn't open", webView)
        assertTrue(shadowOf(webView).lastLoadedUrl.orEmpty().startsWith("http://127.0.0.1:"))
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }
}
