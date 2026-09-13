package com.vangnang.youtubelive

import java.net.URI

val FPS_OPTIONS = listOf(30, 40, 45, 50, 60)

enum class Destination(val title: String, val defaultUrl: String) {
    YOUTUBE("YouTube", "rtmps://a.rtmps.youtube.com/live2"),
    FACEBOOK("Facebook", "rtmps://live-api-s.facebook.com:443/rtmp"),
    CUSTOM("RTMP tùy chỉnh", "")
}
enum class Quality(val title: String, val width: Int, val height: Int) {
    HD("720p", 1280, 720), FULL_HD("1080p", 1920, 1080), QHD("2K / 1440p", 2560, 1440)
}
data class StreamConfig(val destination: Destination, val quality: Quality, val fps: Int) {
    val bitrate: Int get() = when {
        destination == Destination.FACEBOOK -> if (quality == Quality.HD) 4_000_000 else if (fps > 30) 9_000_000 else 6_000_000
        quality == Quality.QHD -> if (fps > 30) 24_000_000 else 15_000_000
        quality == Quality.FULL_HD -> if (fps > 30) 12_000_000 else 10_000_000
        else -> if (fps > 30) 6_000_000 else 4_000_000
    }
    val label: String get() = "${quality.title} • ${fps} FPS mục tiêu • ${bitrate / 1_000_000} Mb/s"
    fun validate(): String? = when {
        fps !in FPS_OPTIONS -> "Chỉ hỗ trợ chọn ${FPS_OPTIONS.joinToString()} FPS."
        destination == Destination.FACEBOOK && quality == Quality.QHD -> "Facebook dùng tối đa 1080p. Hãy chọn 1080p hoặc 720p."
        else -> null
    }
}
/** Never log the result: it contains the stream key. */
fun buildStreamUrl(server: String, key: String): String {
    val base = server.trim().trimEnd('/')
    val secret = key.trim().trimStart('/')
    val uri = try { URI(base) } catch (_: Exception) { throw IllegalArgumentException("Server URL không hợp lệ.") }
    require(uri.scheme in listOf("rtmp", "rtmps") && !uri.host.isNullOrBlank()) { "Server URL phải là rtmp:// hoặc rtmps:// kèm tên máy chủ." }
    require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Dán riêng Server URL và Stream Key vào đúng ô." }
    require(secret.isNotBlank() && secret.none { it.isWhitespace() || it == '#' } && !secret.contains("://")) { "Stream Key trống hoặc không hợp lệ. Không dán link xem video vào đây." }
    return "$base/$secret"
}
