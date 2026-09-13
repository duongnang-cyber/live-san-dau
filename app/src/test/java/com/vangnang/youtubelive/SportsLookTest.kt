package com.vangnang.youtubelive

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SportsLookTest {
    private val main = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
    private val shader = File("src/main/res/raw/sports_look_fragment.glsl").readText()

    @Test fun newOrUnknownPreferenceUsesLight() {
        for (value in listOf(null, "", "invalid")) assertEquals(SportsLook.LIGHT, SportsLook.fromStored(value))
    }
    @Test fun everyChoiceRoundTripsIncludingOff() {
        SportsLook.entries.forEach { assertEquals(it, SportsLook.fromStored(it.name)) }
    }
    @Test fun offHasIdentityParameters() {
        val off = SportsLook.OFF
        assertEquals(0f, off.denoise, 0f); assertEquals(0f, off.sharpen, 0f)
        assertEquals(1f, off.contrast, 0f); assertEquals(1f, off.saturation, 0f)
        assertEquals(0f, off.detailLimit, 0f)
    }
    @Test fun strengthsStayConservativeAndOrdered() {
        val light = SportsLook.LIGHT; val medium = SportsLook.MEDIUM
        assertTrue(light.denoise < medium.denoise); assertTrue(light.sharpen < medium.sharpen)
        assertTrue(light.detailLimit < medium.detailLimit)
        SportsLook.entries.forEach {
            assertTrue(it.denoise in 0f..0.3f); assertTrue(it.sharpen in 0f..0.4f)
            assertTrue(it.contrast in 1f..1.04f); assertTrue(it.saturation in 1f..1.055f)
            assertTrue(it.detailLimit in 0f..0.025f)
        }
    }
    @Test fun cameraTreatmentPrecedesGraphicsAndHistoricalReplay() {
        val image = main.indexOf("addFilter(SportsLookFilter")
        val score = main.indexOf("addFilter(ScoreOverlayFilter")
        val replay = main.indexOf("addFilter(ReplayFilter")
        assertTrue(image >= 0 && image < score && score < replay)
    }
    @Test fun selectingLookDoesNotRestartCameraOrReplay() {
        val setter = main.substringAfter("private fun setSportsLook(").substringBefore("private fun showSportsLook(")
        for (call in listOf("prepare(", "dispose(", "startPreview(", "replayEngine", "invalidateConfig("))
            assertFalse(call, setter.contains(call))
        assertTrue(setter.contains("putString(\"sports_look\", look.name)"))
        assertTrue(main.contains("@Volatile private var sportsLook"))
    }
    @Test fun filterRemainsAdjustableWhileLive() {
        val lock = main.substringAfter("private fun setLive(").substringBefore("private fun ")
        assertFalse(lock.contains("binding.sportsLook"))
        assertTrue(main.contains("setSingleChoiceItems(SportsLook.entries"))
    }
    @Test fun bypassIsBeforeNeighbourSamplesAndPreservesAlpha() {
        assertTrue(shader.contains("if (uEnabled == 0) { gl_FragColor = source; return; }"))
        assertTrue(shader.indexOf("uEnabled == 0") < shader.indexOf("vec3 n = sampleAt"))
        assertTrue(shader.contains("source.a"))
    }
    @Test fun spatialFilterHasNoTemporalInputsOrCpuReadback() {
        assertEquals(1, Regex("uniform sampler2D").findAll(shader).count())
        assertEquals(4, Regex("= sampleAt").findAll(shader).count())
        val renderer = File("src/main/java/com/vangnang/youtubelive/SportsLookFilter.kt").readText()
        assertFalse(renderer.contains("glReadPixels")); assertFalse(renderer.contains("Bitmap"))
        assertTrue(shader.contains("clamp(detail * uAmounts.y"))
        assertTrue(shader.contains("clamp(vTextureCoord + offset"))
    }
}
