package com.iamtt.streaming

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import com.iamtt.streaming.drive.DriveException
import com.iamtt.streaming.drive.GoogleAccountAuth
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What Google's consent screen hands back must turn into a message that says what to fix. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SignInErrorTest {
    private val auth = GoogleAccountAuth(ApplicationProvider.getApplicationContext(), accountProvider = { null })

    @Test
    fun setupProblemBehindACancelledScreenIsReported() {
        val data = Intent()
        SafeParcelableSerializer.serializeToIntentExtra(
            Status(CommonStatusCodes.DEVELOPER_ERROR, "[28444] Developer console is not set up correctly."), data, "status",
        )
        val message = assertThrows(DriveException::class.java) { auth.resultFromIntent(data) }.message.orEmpty()
        assertTrue(message, "isn't set up for this app" in message)
        assertTrue(message, "Google code 10: [28444] Developer console is not set up correctly." in message)
    }

    @Test
    fun realCancelPointsAtTheSetupGuide() {
        val message = assertThrows(DriveException::class.java) { auth.resultFromIntent(Intent()) }.message.orEmpty()
        assertTrue(message, message.startsWith("Sign-in was cancelled. If you didn't cancel it"))
    }
}
