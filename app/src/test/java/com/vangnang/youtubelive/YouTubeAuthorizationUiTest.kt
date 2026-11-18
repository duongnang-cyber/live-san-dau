package com.vangnang.youtubelive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FacebookLoginUiTest {
    @Test fun setupContainsFacebookLoginButNoGoogleLogin() {
        val setup = File("src/main/res/layout/view_setup.xml").readText()
        val activity = File("src/main/res/layout/activity_main.xml").readText()
        val main = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
        val gradle = File("build.gradle.kts").readText()

        assertFalse(setup.contains("youtube_connect"))
        assertFalse(setup.contains("youtube_account_status"))
        assertFalse(activity.contains("youtube_create_live"))
        assertFalse(main.contains("AuthorizationResult"))
        assertFalse(main.contains("requestYouTubeAuthorization"))
        assertFalse(main.contains("prepareYouTubeLiveSession"))
        assertFalse(gradle.contains("play-services-auth"))
        assertFalse(gradle.contains("firebase-auth"))
        assertTrue(gradle.contains("facebook-login"))
        assertTrue(setup.contains("facebook_login"))
        assertTrue(main.contains("LoginManager"))
        assertTrue(activity.contains("@+id/stream_key"))
        assertFalse(setup.contains("email_sign_in"))
    }
}
