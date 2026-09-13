package com.vangnang.youtubelive

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class VisualIdentityTest {
    private val main = File("src/main")
    private fun color(name: String): Int {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(main, "res/values/colors.xml"))
        val nodes = xml.getElementsByTagName("color")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.attributes.getNamedItem("name").nodeValue == name)
                return node.textContent.trim().removePrefix("#").toInt(16)
        }
        error("Missing color $name")
    }
    private fun luminance(value: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((value shr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    @Test fun inputAndGuidanceHaveDistinctColors() {
        assertNotEquals(color("field_value"), color("field_label"))
        assertNotEquals(color("field_value"), color("field_placeholder"))
    }
    @Test fun smallFormTextMeetsFourPointFiveContrastOnBothDarkSurfaces() {
        for (surface in listOf("field_surface", "panel_surface"))
            for (ink in listOf("field_value", "field_label", "field_placeholder", "field_disabled")) {
                val ratio = (luminance(color(ink)) + 0.05) / (luminance(color(surface)) + 0.05)
                assertTrue("$ink on $surface: $ratio", ratio >= 4.5)
            }
    }
    @Test fun bothCredentialInputsUseValueColorAndSeparateLabelColor() {
        val xml = File(main, "res/layout/activity_main.xml").readText()
        assertEquals(2, Regex("android:textColor=\"@color/field_value_state\"").findAll(xml).count())
        assertEquals(2, Regex("app:hintTextColor=\"@color/field_label\"").findAll(xml).count())
    }
    @Test fun numericAndTextEditorsShareTheSameStyledFactory() {
        val code = File(main, "java/com/vangnang/youtubelive/ScorePanel.kt").readText()
        assertTrue(code.contains("R.color.field_value_state"))
        assertTrue(code.contains("R.color.field_label"))
        assertTrue(code.contains("R.color.field_surface"))
        assertTrue(code.contains("R.layout.spinner_dropdown_item"))
    }
    @Test fun launcherHasNeutralNameAndNoRedPlayTriangle() {
        assertTrue(File(main, "AndroidManifest.xml").readText().contains("android:label=\"Live Sân Đấu\""))
        val icon = File(main, "res/drawable/ic_live.xml").readText()
        assertFalse(icon.contains("#FF0033")); assertFalse(icon.contains("M46,39 L72,54 L46,69"))
    }
    @Test fun legacyPreferenceKeepsItsKeyButUsesNewContourArtwork() {
        assertEquals(BoardStyle.CLASSIC, boardStyleFromName("CLASSIC"))
        assertTrue(BoardStyle.CLASSIC.title.startsWith("Contour"))
        for (sport in Sport.entries) {
            val marks = BroadcastDesign.board(ScoreState(sport = sport, visible = true).withBoardStyle(BoardStyle.CLASSIC), 0)
            assertFalse(marks.any { it is Mark.Shape })
        }
    }
    @Test fun noBundledReferenceImagesMusicVideoOrExternalFonts() {
        val forbidden = setOf("png", "jpg", "jpeg", "webp", "gif", "mp3", "mp4", "ttf", "otf")
        assertFalse(main.walkTopDown().any { it.isFile && it.extension.lowercase() in forbidden })
    }
    @Test fun licenseAndNonAffiliationInformationShipsInsideApp() {
        assertTrue(File(main, "assets/about.txt").readText().contains("Không phải ứng dụng chính thức"))
        assertTrue(File(main, "assets/licenses/Apache-2.0.txt").readText().contains("Version 2.0"))
        assertTrue(File(main, "assets/licenses/SLF4J-MIT.txt").readText().contains("QOS.ch"))
        assertTrue(File("build/generated/runtimeNotices/licenses/RUNTIME_NOTICES.txt").readText().contains("RootEncoder"))
    }
}
