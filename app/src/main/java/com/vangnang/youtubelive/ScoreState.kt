package com.vangnang.youtubelive

import java.util.Locale

enum class Sport(val title: String) { FOOTBALL("Bóng đá"), VOLLEYBALL("Bóng chuyền"), PICKLEBALL("Pickleball") }

/** Manual broadcast scoreboard, not a referee/rules engine. All values are immutable across UI/GL threads. */
data class ScoreState(
    val sport: Sport = Sport.FOOTBALL,
    val teamA: String = "ĐỘI A", val teamB: String = "ĐỘI B",
    val event: String = "TRỰC TIẾP THỂ THAO",
    val scoreA: Int = 0, val scoreB: Int = 0,
    val setsA: Int = 0, val setsB: Int = 0,
    val period: String = "HIỆP 1",
    val serving: Int = 0, val serverNumber: Int = 1,
    val visible: Boolean = false, val intermission: Boolean = false,
    val breakTitle: String = "NGHỈ GIỮA HIỆP",
    val elapsedMs: Long = 0, val runningSince: Long? = null,
    val tickerVisible: Boolean = false, val tickerText: String = "",
    val tickerLabel: String = "GIỚI THIỆU", val tickerSpeed: Int = 90,
    val footballPlacement: BoardPlacement? = null,
    val volleyballPlacement: BoardPlacement? = null,
    val pickleballPlacement: BoardPlacement? = null,
    val breakPlacement: BoardPlacement? = null,
    val footballStyle: BoardStyle = BoardStyle.ARENA,
    val volleyballStyle: BoardStyle = BoardStyle.ARENA,
    val pickleballStyle: BoardStyle = BoardStyle.ARENA,
    val quickScoreControls: Boolean = true
) {
    fun elapsed(now: Long): Long = (elapsedMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)).coerceIn(0, MAX_CLOCK_MS)
    fun clock(now: Long): String {
        val seconds = elapsed(now) / 1000
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
    fun toggleClock(now: Long): ScoreState = if (runningSince == null && !intermission) copy(runningSince = now) else pause(now)
    fun pause(now: Long) = copy(elapsedMs = elapsed(now), runningSince = null)
    fun setClock(minutes: Int, seconds: Int, now: Long): ScoreState = copy(
        elapsedMs = (minutes.coerceIn(0, 999) * 60L + seconds.coerceIn(0, 59)) * 1000,
        runningSince = if (runningSince != null && !intermission) now else null
    )
    fun changeScore(team: Int, delta: Int) = if (team == 0) copy(scoreA = (scoreA + delta).coerceIn(0, 999)) else copy(scoreB = (scoreB + delta).coerceIn(0, 999))
    fun changeSets(team: Int, delta: Int) = if (team == 0) copy(setsA = (setsA + delta).coerceIn(0, 99)) else copy(setsB = (setsB + delta).coerceIn(0, 99))
    fun showBreak(show: Boolean, now: Long): ScoreState = pause(now).copy(intermission = show, visible = if (show) true else visible)
    fun swapTeams() = copy(teamA = teamB, teamB = teamA, scoreA = scoreB, scoreB = scoreA, setsA = setsB, setsB = setsA, serving = when (serving) { 1 -> 2; 2 -> 1; else -> 0 })
    fun resetMatch() = copy(scoreA = 0, scoreB = 0, setsA = 0, setsB = 0, elapsedMs = 0, runningSince = null, intermission = false, serving = 0, serverNumber = 1, period = if (sport == Sport.FOOTBALL) "HIỆP 1" else "SET 1")
    companion object { const val MAX_CLOCK_MS = 59_999_000L }
}

/** A time-based ticker remains the same speed at 30/60 video FPS. */
fun tickerX(elapsedMs: Long, speed: Int, textWidth: Float, viewport: Float): Float {
    val distance = elapsedMs.coerceAtLeast(0) / 1000.0 * speed.coerceIn(30, 200)
    return (viewport - distance % (viewport + textWidth.coerceAtLeast(1f) + 90f)).toFloat()
}
