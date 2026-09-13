package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class ScoreStateTest {
    @Test fun overlaysAreOffInitially() { assertFalse(ScoreState().visible); assertFalse(ScoreState().tickerVisible) }
    @Test fun pointsDoNotGoBelowZero() { assertEquals(0, ScoreState().changeScore(0, -1).scoreA); assertEquals(0, ScoreState().changeScore(1, -1).scoreB) }
    @Test fun pointsAreIndependent() { val s = ScoreState().changeScore(0, 3).changeScore(1, 5); assertEquals(3, s.scoreA); assertEquals(5, s.scoreB) }
    @Test fun capPoints() { assertEquals(999, ScoreState(scoreA = 999).changeScore(0, 1).scoreA) }
    @Test fun setChangesDoNotChangePoints() { val s = ScoreState(scoreA = 25).changeSets(0, 1); assertEquals(25, s.scoreA); assertEquals(1, s.setsA); assertEquals(0, s.setsB) }
    @Test fun setBounds() { assertEquals(0, ScoreState().changeSets(1, -1).setsB); assertEquals(99, ScoreState(setsA = 99).changeSets(0, 1).setsA) }
    @Test fun timerUsesMonotonicElapsedTime() { val s = ScoreState().toggleClock(1000); assertEquals("01:01", s.clock(62000)) }
    @Test fun pausedClockDoesNotAdvance() { val s = ScoreState().toggleClock(1000).pause(62000); assertEquals("01:01", s.clock(999999)) }
    @Test fun resumeKeepsElapsed() { val s = ScoreState().toggleClock(1000).pause(62000).toggleClock(80000); assertEquals("01:11", s.clock(90000)) }
    @Test fun breakPausesAndShowsBoard() { val s = ScoreState().toggleClock(1000).showBreak(true, 46000); assertTrue(s.visible); assertTrue(s.intermission); assertNull(s.runningSince); assertEquals("00:45", s.clock(100000)) }
    @Test fun leavingBreakDoesNotResumeClock() { assertNull(ScoreState().showBreak(true, 100).showBreak(false, 200).runningSince) }
    @Test fun cannotStartTimerDuringBreak() { assertNull(ScoreState(intermission = true).toggleClock(100).runningSince) }
    @Test fun canSetSecondHalfClock() { assertEquals("45:00", ScoreState().setClock(45, 0, 0).clock(0)) }
    @Test fun replacingClockWhileRunning() { val s = ScoreState(runningSince = 1000).setClock(45, 0, 6000); assertEquals("45:10", s.clock(16000)) }
    @Test fun capClock() { assertEquals("999:59", ScoreState().setClock(9999, 99, 0).clock(0)) }
    @Test fun swapKeepsAssociatedScoresSetsAndServer() {
        val s = ScoreState(teamA = "A", teamB = "B", scoreA = 4, scoreB = 9, setsA = 1, setsB = 2, serving = 1).swapTeams()
        assertEquals("B", s.teamA); assertEquals(9, s.scoreA); assertEquals(2, s.setsA); assertEquals(2, s.serving)
        assertEquals(4, s.scoreB); assertEquals(1, s.setsB)
    }
    @Test fun doubleSwapRestoresEverything() { val s = ScoreState(scoreA = 5, serving = 2, serverNumber = 2); assertEquals(s, s.swapTeams().swapTeams()) }
    @Test fun resetKeepsNamesAndAdButClearsMatch() {
        val s = ScoreState(teamA = "Tên đội", scoreA = 9, setsA = 2, elapsedMs = 10000, runningSince = 100, tickerText = "Quảng cáo", tickerVisible = true).resetMatch()
        assertEquals("Tên đội", s.teamA); assertEquals("Quảng cáo", s.tickerText); assertTrue(s.tickerVisible)
        assertEquals(0, s.scoreA); assertEquals(0, s.setsA); assertEquals(0L, s.elapsedMs); assertNull(s.runningSince)
    }
    @Test fun newVolleyballMatchStartsSetOne() { assertEquals("SET 1", ScoreState(sport = Sport.VOLLEYBALL).resetMatch().period) }
    @Test fun noAutomaticWinOrSideOut() { val s = ScoreState(sport = Sport.PICKLEBALL, scoreA = 10, serving = 1).changeScore(0, 1); assertEquals(11, s.scoreA); assertEquals(0, s.setsA); assertEquals(1, s.serving) }
    @Test fun pickleballQuickServeSelectsTeamAndHandTogether() {
        val s = ScoreState(sport = Sport.PICKLEBALL).selectPickleballServe(2, 2)
        assertEquals(2, s.serving); assertEquals(2, s.serverNumber)
    }
    @Test fun tappingActivePickleballServeAgainClearsIndicator() {
        val s = ScoreState(sport = Sport.PICKLEBALL, serving = 1, serverNumber = 1).selectPickleballServe(1, 1)
        assertEquals(0, s.serving); assertEquals(1, s.serverNumber)
    }
    @Test fun tickerStartsAtRightAndMovesLeft() { assertEquals(1080f, tickerX(0, 90, 200f, 1080f), 0.01f); assertEquals(990f, tickerX(1000, 90, 200f, 1080f), 0.01f) }
    @Test fun tickerLoopsAfterEntireTextClears() { assertEquals(1080f, tickerX(13700, 100, 200f, 1080f), 0.01f) }
    @Test fun tickerSpeedIsNotDependentOnFrameCount() { assertEquals(780f, tickerX(2000, 150, 200f, 1080f), 0.01f) }
}
