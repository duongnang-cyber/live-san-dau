package com.vangnang.youtubelive

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class YouTubeChannel(val id: String, val title: String)
data class YouTubeBroadcast(val id: String, val title: String, val boundStreamId: String?)
data class YouTubeLiveSession(
    val broadcastId: String,
    val title: String,
    val serverUrl: String,
    val streamKey: String
) {
    val watchUrl: String get() = "https://youtu.be/$broadcastId"
}

/** OAuth authorization and the small subset of YouTube Live API used by the app. */
class YouTubeLiveController {
    private val youtubeScope = Scope("https://www.googleapis.com/auth/youtube.force-ssl")

    fun authorize(activity: Activity) = Identity.getAuthorizationClient(activity).authorize(
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(youtubeScope))
            .build()
    )

    fun result(activity: Activity, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)

    suspend fun channel(accessToken: String): YouTubeChannel {
        val root = request(
            "GET",
            "https://www.googleapis.com/youtube/v3/channels?part=id,snippet&mine=true&maxResults=1",
            accessToken
        )
        val item = root.optJSONArray("items")?.optJSONObject(0)
            ?: error("Tài khoản này chưa có kênh YouTube.")
        return YouTubeChannel(item.getString("id"), item.getJSONObject("snippet").getString("title"))
    }

    suspend fun upcomingBroadcasts(accessToken: String): List<YouTubeBroadcast> {
        val root = request(
            "GET",
            "https://www.googleapis.com/youtube/v3/liveBroadcasts" +
                "?part=id,snippet,contentDetails&broadcastStatus=upcoming&mine=true&maxResults=25",
            accessToken
        )
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    YouTubeBroadcast(
                        id = item.getString("id"),
                        title = item.getJSONObject("snippet").getString("title"),
                        boundStreamId = item.optJSONObject("contentDetails")
                            ?.optString("boundStreamId")?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun useBroadcast(accessToken: String, broadcast: YouTubeBroadcast): YouTubeLiveSession {
        val streamId = broadcast.boundStreamId ?: createStream(accessToken, broadcast.title)
            .also { bind(accessToken, broadcast.id, it) }
        return streamSession(accessToken, broadcast.id, broadcast.title, streamId)
    }

    suspend fun createBroadcast(
        accessToken: String,
        title: String,
        privacyStatus: String
    ): YouTubeLiveSession {
        val startTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(System.currentTimeMillis() + 90_000))
        val body = JSONObject()
            .put("snippet", JSONObject().put("title", title).put("scheduledStartTime", startTime))
            .put(
                "status",
                JSONObject().put("privacyStatus", privacyStatus).put("selfDeclaredMadeForKids", false)
            )
            .put(
                "contentDetails",
                JSONObject().put("enableAutoStart", true).put("enableAutoStop", true).put("enableDvr", true)
            )
        val broadcast = request(
            "POST",
            "https://www.googleapis.com/youtube/v3/liveBroadcasts?part=id,snippet,status,contentDetails",
            accessToken,
            body
        )
        val broadcastId = broadcast.getString("id")
        val streamId = createStream(accessToken, title)
        bind(accessToken, broadcastId, streamId)
        return streamSession(accessToken, broadcastId, title, streamId)
    }

    private suspend fun createStream(accessToken: String, title: String): String {
        val body = JSONObject()
            .put("snippet", JSONObject().put("title", "$title • Live Sân Đấu"))
            .put(
                "cdn",
                JSONObject().put("frameRate", "variable").put("ingestionType", "rtmp")
                    .put("resolution", "variable")
            )
            .put("contentDetails", JSONObject().put("isReusable", false))
        return request(
            "POST",
            "https://www.googleapis.com/youtube/v3/liveStreams?part=id,snippet,cdn,contentDetails",
            accessToken,
            body
        ).getString("id")
    }

    private suspend fun bind(accessToken: String, broadcastId: String, streamId: String) {
        request(
            "POST",
            "https://www.googleapis.com/youtube/v3/liveBroadcasts/bind" +
                "?id=${encode(broadcastId)}&streamId=${encode(streamId)}&part=id,contentDetails",
            accessToken,
            JSONObject()
        )
    }

    private suspend fun streamSession(
        accessToken: String,
        broadcastId: String,
        title: String,
        streamId: String
    ): YouTubeLiveSession {
        val root = request(
            "GET",
            "https://www.googleapis.com/youtube/v3/liveStreams" +
                // liveStreams.list accepts exactly one filter. `id` already identifies the
                // stream; adding `mine` makes YouTube return incompatibleParameters.
                "?part=id,cdn&id=${encode(streamId)}",
            accessToken
        )
        val info = root.optJSONArray("items")?.optJSONObject(0)
            ?.optJSONObject("cdn")?.optJSONObject("ingestionInfo")
            ?: error("YouTube chưa trả về thông tin máy chủ phát.")
        val server = info.optString("rtmpsIngestionAddress").takeIf { it.isNotBlank() }
            ?: info.getString("ingestionAddress")
        return YouTubeLiveSession(broadcastId, title, server, info.getString("streamName"))
    }

    private suspend fun request(
        method: String,
        url: String,
        accessToken: String,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault("YouTube API trả về lỗi $code")
                throw IOException(message)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
