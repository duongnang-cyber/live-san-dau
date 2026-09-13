package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class ReplaySettingsTest {
    @Test fun defaultsAreOneTapBroadcastPreset() {
        assertEquals(ReplaySettings(5, 0.5, ReplayTransition.SPORTS), ReplaySettings())
    }
    @Test fun allSupportedChoicesAreAccepted() {
        for (seconds in listOf(3, 5, 8)) for (speed in listOf(0.5, 0.25))
            for (style in ReplayTransition.entries) assertEquals(style, ReplaySettings(seconds, speed, style).transition)
    }
    @Test fun corruptStoredValuesReturnDefaults() {
        assertEquals(ReplaySettings(), ReplaySettings.safe(60, 1.0, "MISSING"))
    }
    @Test fun validStoredValuesRoundTrip() {
        assertEquals(ReplaySettings(8, 0.25, ReplayTransition.FADE), ReplaySettings.safe(8, 0.25, "FADE"))
    }
    @Test fun transitionDurationsMatchBehavior() {
        assertEquals(0L, ReplayTransition.CUT.enterDurationMs)
        assertTrue(ReplayTransition.FADE.enterDurationMs in 250..500)
        assertTrue(ReplayTransition.SPORTS.enterDurationMs >= 900)
        assertTrue(ReplayTransition.SPORTS.exitDurationMs < ReplayTransition.SPORTS.enterDurationMs)
    }
}
