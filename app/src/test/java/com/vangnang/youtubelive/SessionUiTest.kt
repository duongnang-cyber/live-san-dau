package com.vangnang.youtubelive

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class SessionUiTest {
    private fun layout(name: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/layout/$name.xml"))
    @Test fun startupHasOnlyTwoChoiceGroupsAndNoCredentialsOrReset() {
        val doc = layout("view_setup")
        assertEquals(2, doc.getElementsByTagName("com.google.android.material.button.MaterialButtonToggleGroup").length)
        assertEquals(0, doc.getElementsByTagName("com.google.android.material.textfield.TextInputEditText").length)
        assertEquals(0, doc.getElementsByTagName("com.google.android.material.checkbox.MaterialCheckBox").length)
    }
    @Test fun sessionContainsOneKeyFieldAndNoPlatformChooser() {
        val text = File("src/main/res/layout/activity_main.xml").readText()
        assertFalse(text.contains("@+id/platform\""))
        assertEquals(1, Regex("@\\+id/stream_key\"").findAll(text).count())
        assertTrue(text.contains("android:saveEnabled=\"false\""))
    }
    @Test fun footballDoesNotExposeCourtControls() {
        val c = sportControls(Sport.FOOTBALL)
        assertFalse(c.sets); assertFalse(c.serving); assertFalse(c.serverNumber)
        assertEquals("Hiệp", c.periodLabel)
    }
    @Test fun volleyballDoesNotExposePickleballServerNumber() {
        val c = sportControls(Sport.VOLLEYBALL)
        assertTrue(c.sets && c.serving); assertFalse(c.serverNumber); assertEquals("giữa set", c.breakLabel)
    }
    @Test fun pickleballShowsItsOwnControls() {
        val c = sportControls(Sport.PICKLEBALL)
        assertTrue(c.sets && c.serving && c.serverNumber)
    }
    @Test fun onlyCustomDestinationShowsServerByDefault() {
        assertFalse(sessionShowsServer(Destination.FACEBOOK)); assertFalse(sessionShowsServer(Destination.YOUTUBE))
        assertTrue(sessionShowsServer(Destination.CUSTOM))
    }
    @Test fun credentialLabelsAreDestinationSpecific() {
        assertEquals("Stream Key Facebook", sessionKeyLabel(Destination.FACEBOOK))
        assertEquals("Stream Key YouTube", sessionKeyLabel(Destination.YOUTUBE))
        assertEquals("Stream Key RTMP", sessionKeyLabel(Destination.CUSTOM))
    }
    @Test fun facebookPickerOmitsUnsupportedQualityAndIncludesDefault() {
        assertEquals(listOf(Quality.HD, Quality.FULL_HD), sessionQualities(Destination.FACEBOOK))
        for (d in Destination.entries) assertTrue(sessionQualities(d).contains(quickStartConfig(d).quality))
        assertTrue(Quality.QHD in sessionQualities(Destination.YOUTUBE))
    }
    @Test fun sportCannotBeChangedInsideScorePanel() {
        assertFalse(File("src/main/java/com/vangnang/youtubelive/ScorePanel.kt").readText().contains("Sport.entries"))
    }
    @Test fun pickleballServeControlsAreOnCameraPanelNotInSettings() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        for (id in listOf("quick_serve_a1", "quick_serve_a2", "quick_serve_b1", "quick_serve_b2")) {
            assertTrue(layout.contains("@+id/$id"))
        }
        assertFalse(File("src/main/java/com/vangnang/youtubelive/ScorePanel.kt").readText().contains("Pickleball: tay giao bóng"))
    }
}
