package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class BoardStyleTest {
    @Test fun unknownOrMissingStyleUsesArena() {
        assertEquals(BoardStyle.ARENA, boardStyleFromName(null))
        assertEquals(BoardStyle.ARENA, boardStyleFromName("invalid"))
    }
    @Test fun validStyleNamesRoundTrip() {
        for (s in BoardStyle.entries) assertEquals(s, boardStyleFromName(s.name))
    }
    @Test fun stylesSavedIndependentlyBySport() {
        val s = ScoreState().withBoardStyle(BoardStyle.MINIMAL).copy(sport = Sport.VOLLEYBALL)
            .withBoardStyle(BoardStyle.CHAMPION).copy(sport = Sport.PICKLEBALL).withBoardStyle(BoardStyle.CLASSIC)
        assertEquals(BoardStyle.MINIMAL, s.footballStyle)
        assertEquals(BoardStyle.CHAMPION, s.volleyballStyle)
        assertEquals(BoardStyle.CLASSIC, s.pickleballStyle)
    }
    @Test fun styleDoesNotResetPointsOrPlacement() {
        val s = ScoreState(scoreA = 21, setsA = 2, elapsedMs = 43000, footballPlacement = BoardPlacement(50f, 40f, 0.7f))
        assertEquals(s, s.withBoardStyle(BoardStyle.CHAMPION).withBoardStyle(s.boardStyle()))
    }
    @Test fun eachSportHasThreeDistinctNewDesigns() {
        for (sport in Sport.entries) {
            val scenes = BoardStyle.entries.drop(1).map { BroadcastDesign.board(ScoreState(sport = sport, visible = true).withBoardStyle(it), 0) }
            assertEquals(3, scenes.toSet().size)
        }
    }
    @Test fun footballPointsAreInsideNamesAndCloseTogether() {
        for (style in BoardStyle.entries) {
            val labels = BroadcastDesign.board(ScoreState(visible = true, teamA = "HOME", teamB = "AWAY", scoreA = 2, scoreB = 1).withBoardStyle(style), 0).filterIsInstance<Mark.Caption>()
            val a = labels.single { it.value == "2" }; val b = labels.single { it.value == "1" }
            assertTrue(labels.single { it.value == "HOME" }.x < a.x)
            assertTrue(a.x < b.x && b.x - a.x <= 80f)
            assertTrue(b.x < labels.single { it.value == "AWAY" }.x)
        }
    }
    @Test fun courtStylesHaveSetAndPointColumns() {
        for (sport in listOf(Sport.VOLLEYBALL, Sport.PICKLEBALL)) for (style in BoardStyle.entries) {
            val labels = BroadcastDesign.board(ScoreState(sport = sport, visible = true).withBoardStyle(style), 0).filterIsInstance<Mark.Caption>().map { it.value }
            assertTrue("SET" in labels && "ĐIỂM" in labels)
        }
    }
    @Test fun newPanelsStayInsideExistingResizeBounds() {
        for (sport in Sport.entries) for (style in BoardStyle.entries) {
            val s = ScoreState(sport = sport, visible = true).withBoardStyle(style); val b = s.boardBounds()
            for (m in BroadcastDesign.board(s, 0)) when (m) {
                is Mark.Panel -> assertTrue(m.x >= b.left && m.y >= b.top && m.x + m.w <= b.left + b.width && m.y + m.h <= b.top + b.height)
                is Mark.Shape -> m.points.forEach { assertTrue(it.first in b.left..b.left + b.width && it.second in b.top..b.top + b.height) }
                else -> Unit
            }
        }
    }
    @Test fun quickControlsAvailableForCourtOnly() {
        assertFalse(ScoreState(visible = true).quickScoresAvailable())
        assertTrue(ScoreState(sport = Sport.VOLLEYBALL, visible = true).quickScoresAvailable())
        assertTrue(ScoreState(sport = Sport.PICKLEBALL, visible = true).quickScoresAvailable())
    }
    @Test fun hiddenBoardBreakOrToggleHidesControls() {
        val s = ScoreState(sport = Sport.PICKLEBALL, visible = true)
        assertFalse(s.copy(visible = false).quickScoresAvailable())
        assertFalse(s.copy(intermission = true).quickScoresAvailable())
        assertFalse(s.copy(quickScoreControls = false).quickScoresAvailable())
    }
    @Test fun quickPointsPreserveSetsAndClampAtZero() {
        val s = ScoreState(scoreA = 0, scoreB = 21, setsA = 2)
        assertEquals(s, s.changeScore(0, -1))
        assertEquals(22, s.changeScore(1, 1).scoreB)
        assertEquals(2, s.changeScore(1, 1).setsA)
    }
    @Test fun pinchMultipliesAndClampsZoom() {
        assertEquals(3f, nextCameraZoom(2f, 1.5f, 1f, 8f), 0.001f)
        assertEquals(1f, nextCameraZoom(1f, 0.5f, 1f, 8f), 0.001f)
        assertEquals(8f, nextCameraZoom(7f, 2f, 1f, 8f), 0.001f)
    }
    @Test fun supportsSubOneZoomOnlyWhenCameraAdvertisesIt() {
        assertEquals(0.5f, nextCameraZoom(1f, 0.5f, 0.5f, 4f), 0.001f)
    }
    @Test fun invalidZoomDataIsSafe() {
        assertEquals(1f, nextCameraZoom(Float.NaN, Float.NaN, Float.NaN, Float.NaN), 0.001f)
        assertEquals(2f, nextCameraZoom(2f, -4f, 1f, 4f), 0.001f)
    }
}
