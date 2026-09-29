package com.iamtt.streaming

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
