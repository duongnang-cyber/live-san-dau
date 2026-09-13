package com.vangnang.youtubelive

enum class ReplayTransition(val title: String, val enterDurationMs: Long, val exitDurationMs: Long) {
    CUT("Cắt nhanh", 0, 0),
    FADE("Hòa mờ điện ảnh", 350, 350),
    SPORTS("Đồ họa thể thao • quét chéo", 1050, 500)
}

data class ReplaySettings(
    val seconds: Int = 5,
    val speed: Double = 0.5,
    val transition: ReplayTransition = ReplayTransition.SPORTS
) {
    init {
        require(seconds in listOf(3, 5, 8))
        require(speed in listOf(0.5, 0.25))
    }

    companion object {
        fun safe(seconds: Int, speed: Double, transitionName: String?): ReplaySettings {
            val duration = seconds.takeIf { it in listOf(3, 5, 8) } ?: 5
            val rate = speed.takeIf { it in listOf(0.5, 0.25) } ?: 0.5
            val style = ReplayTransition.entries.firstOrNull { it.name == transitionName } ?: ReplayTransition.SPORTS
            return ReplaySettings(duration, rate, style)
        }
    }
}
