package com.vangnang.youtubelive

import org.json.JSONArray
import java.util.UUID

fun readSetupSports(data: JSONArray?): List<SetupSport> = buildList {
    if (data == null) return@buildList
    val seen = mutableSetOf<String>()
    for (index in 0 until data.length().coerceAtMost(50)) {
        val value = data.optJSONObject(index) ?: continue
        if (!value.optBoolean("visible", false)) continue
        val id = value.optString("id")
        val validId = runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)
        if (!validId) continue
        val title = value.optString("text").trim().take(60)
        val profile = Sport.entries.firstOrNull { it.name == value.optString("profile") } ?: continue
        if (title.isNotBlank() && seen.add(id)) add(SetupSport(id, title, profile))
    }
}
