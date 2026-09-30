package com.iamtt.streaming

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamtt.streaming.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app starts by asking who's watching; each profile gets its own Continue Watching. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xxhdpi") // a phone held upright
class ProfilesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun firstRunCreatesAProfileThenShowsTheHomeScreen() {
        val app = SampleLibrary.install()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Create your profile").assertExists()
            compose.onNodeWithText("Name").performTextInput("Izreen")
            compose.onNodeWithText("Save").performClick()
            // Saving finishes on a background thread (it tidies up photo files first).
            compose.waitUntil(timeoutMillis = 5_000) { app.config.current.profiles.isNotEmpty() }
            compose.waitForIdle()

            assertEquals(listOf("Izreen"), app.config.current.profiles.map { it.name })
            compose.onNodeWithText("Play").assertExists() // the featured title's button
            compose.onNodeWithText("Recently Added").assertExists()
        }
    }

    @Test
    fun pickingAProfileShowsTheirContinueWatching() {
        val daddy = Profile("profileDaddy", "Daddy", avatar = 1)
        val aisya = Profile("profileAisya", "Aisya", avatar = 5)
        val app = SampleLibrary.install(listOf(daddy, aisya))
        app.history.save(aisya.id, SampleLibrary.inception.id, positionMs = 1_800_000, durationMs = 8_400_000)

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Who's watching?").assertExists()
            compose.onNodeWithText("Aisya").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Continue Watching for Aisya").assertExists()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Daddy").performClick()
            compose.waitForIdle()
            assertEquals(0, compose.onAllNodesWithText("Continue Watching", substring = true).fetchSemanticsNodes().size)
        }
    }
}
