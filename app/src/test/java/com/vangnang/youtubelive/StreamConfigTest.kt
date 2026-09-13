package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class StreamConfigTest {
    @Test fun youtubeAndCustomSupportAllProfiles() {
        for (d in listOf(Destination.YOUTUBE, Destination.CUSTOM)) for (q in Quality.entries) for (fps in FPS_OPTIONS) {
            assertNull(StreamConfig(d, q, fps).validate())
        }
    }
    @Test fun facebookRejects2k() {
        for (fps in FPS_OPTIONS) assertNotNull(StreamConfig(Destination.FACEBOOK, Quality.QHD, fps).validate())
    }
    @Test fun facebookAccepts1080At30And60() {
        for (fps in FPS_OPTIONS) assertNull(StreamConfig(Destination.FACEBOOK, Quality.FULL_HD, fps).validate())
    }
    @Test fun bitrateProfiles() {
        assertEquals(24_000_000, StreamConfig(Destination.YOUTUBE, Quality.QHD, 60).bitrate)
        assertEquals(9_000_000, StreamConfig(Destination.FACEBOOK, Quality.FULL_HD, 60).bitrate)
    }
    @Test fun intermediateProfilesKeepRequestedFpsAndUseHighRateBitrate() {
        for (fps in listOf(40, 45, 50)) {
            val config = StreamConfig(Destination.YOUTUBE, Quality.FULL_HD, fps)
            assertNull(config.validate())
            assertEquals(fps, config.fps)
            assertTrue(config.label.contains("$fps FPS"))
            assertEquals(12_000_000, config.bitrate)
        }
    }
    @Test fun unsupportedFpsStillRejected() {
        for (fps in listOf(0, 29, 41, 120))
            assertNotNull(StreamConfig(Destination.CUSTOM, Quality.HD, fps).validate())
    }
    @Test fun dimensionsAreQhdNotDci2k() { assertEquals(2560, Quality.QHD.width); assertEquals(1440, Quality.QHD.height) }
    @Test fun youtubeUrl() { assertEquals("rtmps://a.rtmps.youtube.com/live2/test-key", buildStreamUrl(" rtmps://a.rtmps.youtube.com/live2/ ", " test-key ")) }
    @Test fun facebookKeyWithQueryIsPreserved() {
        assertEquals("rtmps://live-api-s.facebook.com:443/rtmp/123?s_bl=1&s_psm=1", buildStreamUrl(Destination.FACEBOOK.defaultUrl, "123?s_bl=1&s_psm=1"))
    }
    @Test fun rejectedInputsDoNotLeakSecrets() {
        for ((server, key) in listOf("https://youtube.com/watch?v=x" to "secret", "rtmps://" to "secret", "rtmps://host/path?secret=1" to "secret", "rtmps://host/path" to "", "rtmps://host/path" to "///", "rtmps://host/path" to "secret key")) {
            try { buildStreamUrl(server, key); fail("Expected invalid input") }
            catch (e: IllegalArgumentException) { assertFalse(e.message.orEmpty().contains("secret")) }
        }
    }
}
