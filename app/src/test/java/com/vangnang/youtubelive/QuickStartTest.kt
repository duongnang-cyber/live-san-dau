package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class QuickStartTest {
    @Test fun youtubeStartsAt720p30() { assertEquals(StreamConfig(Destination.YOUTUBE, Quality.HD, 30), quickStartConfig(Destination.YOUTUBE)) }
    @Test fun facebookStartsAt1080p30() { assertEquals(StreamConfig(Destination.FACEBOOK, Quality.FULL_HD, 30), quickStartConfig(Destination.FACEBOOK)) }
    @Test fun customStartsAt720p30() { assertEquals(StreamConfig(Destination.CUSTOM, Quality.HD, 30), quickStartConfig(Destination.CUSTOM)) }
    @Test fun allDefaultsAreValidAndUnderSevenMbps() {
        Destination.entries.forEach { val c = quickStartConfig(it); assertNull(c.validate()); assertTrue(c.bitrate < 7_000_000) }
    }
    @Test fun setupSelectsEverySportAndEnablesBoard() {
        Sport.entries.forEach { val s = setupScore(ScoreState(), it, false, 0); assertEquals(it, s.sport); assertTrue(s.visible) }
    }
    @Test fun sportChangeUsesAppropriatePeriodAndBreakLabel() {
        val volley = setupScore(ScoreState(), Sport.VOLLEYBALL, false, 0)
        assertEquals("SET 1", volley.period); assertEquals("NGHỈ GIỮA SET", volley.breakTitle)
        val foot = setupScore(volley, Sport.FOOTBALL, false, 0)
        assertEquals("HIỆP 1", foot.period); assertEquals("NGHỈ GIỮA HIỆP", foot.breakTitle)
    }
    @Test fun setupDoesNotResetSavedScoresUnlessRequested() {
        val old = ScoreState(scoreA = 5, scoreB = 7, setsA = 1, elapsedMs = 50000)
        val s = setupScore(old, Sport.PICKLEBALL, false, 100000)
        assertEquals(5, s.scoreA); assertEquals(7, s.scoreB); assertEquals(1, s.setsA); assertEquals(50000L, s.elapsedMs)
    }
    @Test fun explicitNewMatchResetsPointsAndTimer() {
        val s = setupScore(ScoreState(scoreA = 5, setsA = 2, elapsedMs = 40000), Sport.VOLLEYBALL, true, 100000)
        assertEquals(0, s.scoreA); assertEquals(0, s.setsA); assertEquals(0L, s.elapsedMs); assertEquals("SET 1", s.period)
    }
    @Test fun setupPausesClockAndClosesIntermission() {
        val s = setupScore(ScoreState(runningSince = 1000, elapsedMs = 5000, intermission = true), Sport.FOOTBALL, false, 4000)
        assertNull(s.runningSince); assertFalse(s.intermission); assertEquals(8000L, s.elapsedMs)
    }
    @Test fun setupPreservesNamesTickerAndIndependentLayouts() {
        val old = ScoreState(teamA = "CẨM LÝ", tickerText = "Giới thiệu", footballPlacement = BoardPlacement(60f, 90f, 0.4f))
        val s = setupScore(old, Sport.FOOTBALL, true, 0)
        assertEquals(old.teamA, s.teamA); assertEquals(old.tickerText, s.tickerText); assertEquals(old.footballPlacement, s.footballPlacement)
    }
    @Test fun continueSameSportPreservesPeriod() {
        assertEquals("HIỆP 2", setupScore(ScoreState(period = "HIỆP 2"), Sport.FOOTBALL, false, 0).period)
    }
}
