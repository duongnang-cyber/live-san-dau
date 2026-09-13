package com.vangnang.youtubelive

import android.content.SharedPreferences
import org.json.JSONObject

class ScoreStore(private val prefs: SharedPreferences) {
    fun save(s: ScoreState, now: Long) {
        val o = JSONObject().apply {
            put("sport", s.sport.name); put("a", s.teamA); put("b", s.teamB); put("event", s.event)
            put("sa", s.scoreA); put("sb", s.scoreB); put("setsA", s.setsA); put("setsB", s.setsB)
            put("period", s.period); put("serving", s.serving); put("server", s.serverNumber)
            put("visible", s.visible); put("break", s.intermission); put("breakTitle", s.breakTitle)
            put("elapsed", s.elapsed(now)); put("tickerVisible", s.tickerVisible)
            put("tickerText", s.tickerText); put("tickerLabel", s.tickerLabel); put("speed", s.tickerSpeed)
            put("footballStyle", s.footballStyle.name); put("volleyballStyle", s.volleyballStyle.name)
            put("pickleballStyle", s.pickleballStyle.name); put("quickScores", s.quickScoreControls)
            listOf("footballLayout" to s.footballPlacement, "volleyballLayout" to s.volleyballPlacement,
                "pickleballLayout" to s.pickleballPlacement, "breakLayout" to s.breakPlacement).forEach { (key, value) ->
                value?.let { put(key, JSONObject().put("x", it.x).put("y", it.y).put("scale", it.scale)) }
            }
        }
        prefs.edit().putString("scoreboard_v1", o.toString()).apply()
    }
    fun load(): ScoreState = try {
        val o = JSONObject(prefs.getString("scoreboard_v1", "{}").orEmpty())
        ScoreState(
            sport = Sport.entries.firstOrNull { it.name == o.optString("sport") } ?: Sport.FOOTBALL,
            teamA = o.optString("a", "ĐỘI A").take(40), teamB = o.optString("b", "ĐỘI B").take(40),
            event = o.optString("event", "TRỰC TIẾP THỂ THAO").take(80),
            scoreA = o.optInt("sa").coerceIn(0, 999), scoreB = o.optInt("sb").coerceIn(0, 999),
            setsA = o.optInt("setsA").coerceIn(0, 99), setsB = o.optInt("setsB").coerceIn(0, 99),
            period = o.optString("period", "HIỆP 1").take(30), serving = o.optInt("serving").coerceIn(0, 2),
            serverNumber = o.optInt("server", 1).coerceIn(1, 2), visible = o.optBoolean("visible"),
            intermission = o.optBoolean("break"), breakTitle = o.optString("breakTitle", "NGHỈ GIỮA HIỆP").take(50),
            elapsedMs = o.optLong("elapsed").coerceIn(0, ScoreState.MAX_CLOCK_MS), runningSince = null,
            tickerVisible = o.optBoolean("tickerVisible"), tickerText = o.optString("tickerText").take(500),
            tickerLabel = o.optString("tickerLabel", "GIỚI THIỆU").take(20), tickerSpeed = o.optInt("speed", 90).coerceIn(30, 200),
            footballPlacement = readPlacement(o, "footballLayout"), volleyballPlacement = readPlacement(o, "volleyballLayout"),
            pickleballPlacement = readPlacement(o, "pickleballLayout"), breakPlacement = readPlacement(o, "breakLayout"),
            footballStyle = boardStyleFromName(o.optString("footballStyle")),
            volleyballStyle = boardStyleFromName(o.optString("volleyballStyle")),
            pickleballStyle = boardStyleFromName(o.optString("pickleballStyle")),
            quickScoreControls = o.optBoolean("quickScores", true)
        )
    } catch (_: Exception) { ScoreState() }
    private fun readPlacement(o: JSONObject, key: String): BoardPlacement? = o.optJSONObject(key)?.let {
        BoardPlacement(it.optDouble("x", 32.0).toFloat(), it.optDouble("y", 32.0).toFloat(), it.optDouble("scale", 0.65).toFloat())
    }
}
