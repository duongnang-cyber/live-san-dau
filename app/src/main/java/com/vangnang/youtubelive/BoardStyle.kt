package com.vangnang.youtubelive

enum class BoardStyle(val title: String) {
    CLASSIC("Contour • tím ngọc"),
    ARENA("Truyền hình • xanh đêm"),
    MINIMAL("Minimal • trắng tinh gọn"),
    CHAMPION("Champion • đen vàng")
}

fun boardStyleFromName(name: String?) = BoardStyle.entries.firstOrNull { it.name == name } ?: BoardStyle.ARENA
fun ScoreState.boardStyle(): BoardStyle = when (sport) {
    Sport.FOOTBALL -> footballStyle
    Sport.VOLLEYBALL -> volleyballStyle
    Sport.PICKLEBALL -> pickleballStyle
}
fun ScoreState.withBoardStyle(style: BoardStyle): ScoreState = when (sport) {
    Sport.FOOTBALL -> copy(footballStyle = style)
    Sport.VOLLEYBALL -> copy(volleyballStyle = style)
    Sport.PICKLEBALL -> copy(pickleballStyle = style)
}
fun ScoreState.quickScoresAvailable() = quickScoreControls && visible && !intermission && sport != Sport.FOOTBALL

/** One pinch controls only the camera. Ignore corrupt factors and respect the camera's range. */
fun nextCameraZoom(current: Float, factor: Float, lower: Float, upper: Float): Float {
    val min = if (lower.isFinite() && lower > 0f) lower else 1f
    val max = if (upper.isFinite() && upper >= min) upper else min
    val value = if (current.isFinite() && current > 0f) current else 1f
    val step = if (factor.isFinite() && factor > 0f) factor else 1f
    return (value * step).coerceIn(min, max)
}
