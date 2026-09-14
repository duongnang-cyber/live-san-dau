package com.vangnang.youtubelive

/** Coordinates in the encoded 1280 x 720 design space, independent of screen size. */
data class BoardBounds(val left: Float, val top: Float, val width: Float, val height: Float)
enum class OverlayTarget(val title: String) {
    SCOREBOARD("Bảng tỉ số"), TICKER("Chữ chạy"), QUICK_CONTROLS("Nút thay đổi điểm")
}
data class EditableOverlay(val placement: BoardPlacement, val bounds: BoardBounds, val title: String)
data class BoardPlacement(val x: Float, val y: Float, val scale: Float) {
    fun constrained(bounds: BoardBounds): BoardPlacement {
        val limit = minOf(2f, 1264f / bounds.width, 704f / bounds.height)
        val safeScale = (if (scale.isFinite()) scale else 0.65f).coerceIn(0.3f, limit)
        return BoardPlacement(
            (if (x.isFinite()) x else 32f).coerceIn(8f, 1272f - bounds.width * safeScale),
            (if (y.isFinite()) y else 32f).coerceIn(8f, 712f - bounds.height * safeScale), safeScale
        )
    }
    fun move(dx: Float, dy: Float, bounds: BoardBounds) = copy(x = x + dx, y = y + dy).constrained(bounds)
    fun resize(factor: Float, bounds: BoardBounds) = copy(scale = scale * factor).constrained(bounds)
    fun resizeByDrag(dx: Float, dy: Float, bounds: BoardBounds): BoardPlacement {
        val change = (dx * bounds.width + dy * bounds.height) / (bounds.width * bounds.width + bounds.height * bounds.height)
        return copy(scale = scale + change).constrained(bounds)
    }
}

fun ScoreState.boardBounds(): BoardBounds = when {
    intermission -> BoardBounds(155f, 178f, 970f, 323f)
    sport == Sport.FOOTBALL -> BoardBounds(38f, 38f, 748f, 123f)
    sport == Sport.PICKLEBALL -> BoardBounds(39f, 34f, 526f, 187f)
    else -> BoardBounds(39f, 34f, 436f, 231f)
}

fun tickerBounds() = BoardBounds(0f, 4f, 1280f, 48f)
fun defaultTickerPlacement() = BoardPlacement(16f, 650f, 0.975f).constrained(tickerBounds())
fun ScoreState.tickerPlacementValue(): BoardPlacement = (tickerPlacement ?: defaultTickerPlacement()).constrained(tickerBounds())
fun ScoreState.withTickerPlacement(value: BoardPlacement) = copy(tickerPlacement = value.constrained(tickerBounds()))

fun defaultQuickControlsPlacement(bounds: BoardBounds) = BoardPlacement(
    (1280f - bounds.width) / 2f,
    720f - bounds.height - 58f,
    1f
).constrained(bounds)
fun ScoreState.defaultPlacement(): BoardPlacement = when {
    intermission -> BoardPlacement(227.75f, 222.725f, 0.85f)
    sport == Sport.FOOTBALL -> BoardPlacement(32f, 32f, 0.68f)
    else -> BoardPlacement(32f, 32f, 0.8f)
}
fun ScoreState.placement(): BoardPlacement = (when {
    intermission -> breakPlacement
    sport == Sport.FOOTBALL -> footballPlacement
    sport == Sport.VOLLEYBALL -> volleyballPlacement
    else -> pickleballPlacement
} ?: defaultPlacement()).constrained(boardBounds())

fun ScoreState.withPlacement(value: BoardPlacement): ScoreState {
    val safe = value.constrained(boardBounds())
    return when {
        intermission -> copy(breakPlacement = safe)
        sport == Sport.FOOTBALL -> copy(footballPlacement = safe)
        sport == Sport.VOLLEYBALL -> copy(volleyballPlacement = safe)
        else -> copy(pickleballPlacement = safe)
    }
}

data class PreviewSize(val width: Int, val height: Int)
fun fitVideoPreview(width: Int, height: Int): PreviewSize {
    if (width <= 0 || height <= 0) return PreviewSize(0, 0)
    return if (width.toLong() * 9 > height.toLong() * 16) PreviewSize(height * 16 / 9, height)
    else PreviewSize(width, width * 9 / 16)
}
