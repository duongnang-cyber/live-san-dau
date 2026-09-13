package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class BoardPlacementTest {
    private val football = ScoreState(visible = true)
    @Test fun footballStartsAtSixtyPercent() { assertEquals(0.6f, football.placement().scale, 0.001f) }
    @Test fun defaultFootballUsesUnderFortyPercentOfVideoWidth() {
        assertTrue(football.placement().scale * football.boardBounds().width < 1280 * 0.4f)
    }
    @Test fun courtStartsAtEightyPercent() { assertEquals(0.8f, football.copy(sport = Sport.PICKLEBALL).placement().scale, 0.001f) }
    @Test fun moveKeepsScale() {
        val p = football.placement().move(150f, 120f, football.boardBounds())
        assertEquals(182f, p.x, 0.001f); assertEquals(152f, p.y, 0.001f); assertEquals(0.6f, p.scale, 0.001f)
    }
    @Test fun resizeKeepsTopLeftWhenSpaceAvailable() {
        val p = football.placement().resize(1.5f, football.boardBounds())
        assertEquals(32f, p.x, 0.001f); assertEquals(32f, p.y, 0.001f); assertEquals(0.9f, p.scale, 0.001f)
    }
    @Test fun minimumSizeIsReadableAndNonzero() {
        assertEquals(0.3f, football.placement().resize(0f, football.boardBounds()).scale, 0.001f)
    }
    @Test fun diagonalCornerDragChangesScaleWithoutDistortingBoard() {
        val b = football.boardBounds()
        val p = football.placement().resizeByDrag(b.width * 0.4f, b.height * 0.4f, b)
        assertEquals(1f, p.scale, 0.001f); assertEquals(32f, p.x, 0.001f); assertEquals(32f, p.y, 0.001f)
    }
    @Test fun draggingCornerPastOppositeEdgeDoesNotInvertBoard() {
        val p = football.placement().resizeByDrag(-9999f, -9999f, football.boardBounds())
        assertEquals(0.3f, p.scale, 0.001f)
    }
    @Test fun allBoardsStayInsideVideoAtExtremeSizesAndPositions() {
        for (sport in Sport.entries) for (big in listOf(false, true)) {
            val s = football.copy(sport = sport, intermission = big)
            val b = s.boardBounds()
            for (x in listOf(-10000f, 600f, 10000f)) for (y in listOf(-10000f, 400f, 10000f)) for (scale in listOf(-1f, 0.4f, 1f, 100f)) {
                val p = BoardPlacement(x, y, scale).constrained(b)
                assertTrue(p.x >= 8); assertTrue(p.y >= 8)
                assertTrue(p.x + b.width * p.scale <= 1272.01f)
                assertTrue(p.y + b.height * p.scale <= 712.01f)
            }
        }
    }
    @Test fun corruptNonFiniteValuesAreSanitized() {
        val p = BoardPlacement(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).constrained(football.boardBounds())
        assertTrue(p.x.isFinite() && p.y.isFinite() && p.scale.isFinite())
    }
    @Test fun eachSportRetainsItsOwnPlacement() {
        val moved = football.withPlacement(BoardPlacement(100f, 150f, 0.7f))
        val volley = moved.copy(sport = Sport.VOLLEYBALL).withPlacement(BoardPlacement(250f, 90f, 1.1f))
        val pickle = volley.copy(sport = Sport.PICKLEBALL).withPlacement(BoardPlacement(550f, 70f, 0.5f))
        assertEquals(100f, pickle.copy(sport = Sport.FOOTBALL).placement().x, 0.001f)
        assertEquals(250f, pickle.copy(sport = Sport.VOLLEYBALL).placement().x, 0.001f)
        assertEquals(550f, pickle.placement().x, 0.001f)
    }
    @Test fun intermissionRetainsIndependentPlacement() {
        val s = football.withPlacement(BoardPlacement(80f, 100f, 0.5f)).copy(intermission = true)
            .withPlacement(BoardPlacement(200f, 200f, 0.8f))
        assertEquals(200f, s.placement().x, 0.001f)
        assertEquals(80f, s.copy(intermission = false).placement().x, 0.001f)
    }
    @Test fun resettingLayoutDoesNotResetScoreOrClockOrTicker() {
        val s = football.copy(scoreA = 5, elapsedMs = 43000, tickerText = "Quảng cáo")
            .withPlacement(BoardPlacement(500f, 200f, 0.4f))
        val reset = s.withPlacement(s.defaultPlacement())
        assertEquals(5, reset.scoreA); assertEquals(43000L, reset.elapsedMs); assertEquals("Quảng cáo", reset.tickerText)
        assertEquals(football.placement(), reset.placement())
    }
    @Test fun newMatchPreservesLayout() {
        val s = football.withPlacement(BoardPlacement(400f, 200f, 0.4f))
        assertEquals(s.placement(), s.resetMatch().placement())
    }
    @Test fun widePhonePreviewIsLetterboxedAndSixteenByNine() { assertEquals(PreviewSize(1280, 720), fitVideoPreview(1600, 720)) }
    @Test fun tallWindowPreviewIsLetterboxedAndSixteenByNine() { assertEquals(PreviewSize(1280, 720), fitVideoPreview(1280, 900)) }
    @Test fun invalidPreviewSizesAreSafe() { assertEquals(PreviewSize(0, 0), fitVideoPreview(0, 720)) }
}
