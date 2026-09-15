package com.vangnang.youtubelive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YouTubeAuthorizationUiTest {
    @Test fun setupUsesYouTubeAuthorizationWithoutEmailAccounts() {
        val setup = File("src/main/res/layout/view_setup.xml").readText()
        val activity = File("src/main/res/layout/activity_main.xml").readText()
        val dialog = File("src/main/res/layout/dialog_youtube_live.xml").readText()
        val controller = File("src/main/java/com/vangnang/youtubelive/YouTubeLiveController.kt").readText()
        val gradle = File("build.gradle.kts").readText()

        assertTrue(setup.contains("youtube_connect"))
        assertTrue(setup.contains("youtube_account_status"))
        assertTrue(activity.contains("youtube_create_live"))
        assertTrue(dialog.contains("youtube_broadcast"))
        assertTrue(dialog.contains("youtube_title"))
        assertTrue(dialog.contains("youtube_privacy"))
        assertTrue(controller.contains("youtube.force-ssl"))
        assertTrue(controller.contains("liveBroadcasts"))
        assertTrue(controller.contains("liveStreams"))
        assertTrue(controller.contains("streamName"))
        assertTrue(gradle.contains("play-services-auth"))
        assertFalse(setup.contains("email_sign_in"))
        assertFalse(gradle.contains("firebase-auth"))
    }
}
