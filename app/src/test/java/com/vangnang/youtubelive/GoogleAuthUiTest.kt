package com.vangnang.youtubelive

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoogleAuthUiTest {
    @Test fun setupKeepsGoogleLoginOptionalAndVisible() {
        val xml = File("src/main/res/layout/view_setup.xml").readText()
        val activity = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
        assertTrue(xml.contains("google_sign_in"))
        assertTrue(xml.contains("google_sign_out"))
        assertTrue(activity.contains("enterCamera.setOnClickListener"))
    }
}
