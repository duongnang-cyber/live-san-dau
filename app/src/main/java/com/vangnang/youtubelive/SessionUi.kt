package com.vangnang.youtubelive

fun sessionQualities(destination: Destination): List<Quality> = if (destination == Destination.FACEBOOK)
    listOf(Quality.HD, Quality.FULL_HD) else Quality.entries.toList()
fun sessionKeyLabel(destination: Destination) = when (destination) {
    Destination.FACEBOOK -> "Stream Key Facebook"
    Destination.YOUTUBE -> "Stream Key YouTube"
    Destination.CUSTOM -> "Stream Key RTMP"
}
fun sessionShowsServer(destination: Destination) = destination == Destination.CUSTOM
data class SportControls(val sets: Boolean, val serving: Boolean, val serverNumber: Boolean,
    val periodLabel: String, val breakLabel: String)
fun sportControls(sport: Sport): SportControls = when (sport) {
    Sport.FOOTBALL -> SportControls(false, false, false, "Hiệp", "giữa hiệp")
    Sport.VOLLEYBALL -> SportControls(true, true, false, "Set", "giữa set")
    Sport.PICKLEBALL -> SportControls(true, true, true, "Set", "giữa set")
}
