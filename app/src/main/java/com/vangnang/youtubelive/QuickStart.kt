package com.vangnang.youtubelive

/** Conservative quick-start presets; advanced settings remain explicit and hardware-checked. */
fun quickStartConfig(destination: Destination): StreamConfig = StreamConfig(
    destination, if (destination == Destination.FACEBOOK) Quality.FULL_HD else Quality.HD, 30
)

fun setupScore(previous: ScoreState, sport: Sport, newMatch: Boolean, now: Long): ScoreState {
    val selected = previous.pause(now).copy(
        sport = sport, visible = true, intermission = false,
        period = if (sport == previous.sport) previous.period else if (sport == Sport.FOOTBALL) "HIỆP 1" else "SET 1",
        breakTitle = if (sport == Sport.FOOTBALL) "NGHỈ GIỮA HIỆP" else "NGHỈ GIỮA SET"
    )
    return if (newMatch) selected.resetMatch() else selected
}
