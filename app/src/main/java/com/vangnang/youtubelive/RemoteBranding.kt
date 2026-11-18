package com.vangnang.youtubelive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class RemoteUi(val raw: JSONObject)
data class RemoteBranding(
    val enabled: Boolean = false,
    val label: String = "",
    val licenseExpires: Long = 0L,
    val logo: JSONObject? = null,
    val match: JSONObject? = null,
    val replay: JSONObject? = null,
    val ui: JSONObject? = null,
    val about: JSONObject? = null
)
data class RemoteControl(val config: JSONObject, val licenseExpires: Long)

/** Read-only public switch. Repository write permission remains with the owner; no admin token is shipped in the APK. */
object RemoteBrandingClient {
    const val BASE_URL = "https://taode.online/api/live/v1"
    private const val PREFS = "remote_control"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun deviceId(c: Context) = prefs(c).getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs(c).edit().putString("device_id", it).apply() }
    private fun token(c: Context) = prefs(c).getString("token", null) ?: UUID.randomUUID().toString().replace("-", "").repeat(2).take(64).also { prefs(c).edit().putString("token", it).apply() }
    fun paired(c: Context) = prefs(c).getBoolean("paired", false)
    fun pair(c: Context, code: String, name: String): String? = request("/pair", c, JSONObject().put("device_id", deviceId(c)).put("token", token(c)).put("code", code).put("name", name)).also { if (it != null) prefs(c).edit().putBoolean("paired", true).apply() }
    fun activate(c: Context, code: String): String? = request("/activate", c, JSONObject().put("code", code))
    fun fetchControl(c: Context): RemoteControl? {
        if (!paired(c)) return null
        val t = request("/config?version=${BuildConfig.VERSION_NAME}", c, null) ?: return null
        val j = JSONObject(t)
        return RemoteControl(j.optJSONObject("config") ?: JSONObject(), j.optLong("license_expires"))
    }

    /** Setup text is public so a newly installed/unpaired APK can receive website changes. */
    fun fetchPublicUi(c: Context): JSONObject? {
        val t = request("/public-ui", c, null) ?: return null
        return JSONObject(t)
    }

    fun fetch(context: Context): RemoteBranding = fetchControl(context)?.let {
        val j = it.config
        RemoteBranding(
            enabled = j.optBoolean("ad_enabled", true),
            label = j.optJSONObject("logo")?.optString("text", "") ?: "",
            licenseExpires = it.licenseExpires,
            logo = j.optJSONObject("logo"),
            match = j.optJSONObject("match"),
            replay = j.optJSONObject("replay"),
            ui = j.optJSONObject("ui"),
            about = j.optJSONObject("about")
        )
    } ?: RemoteBranding()
    fun fetchAsset(context: Context, asset: String): Bitmap? {
        if (!asset.matches(Regex("[a-f0-9]{64}\\.png"))) return null
        val connection = URL("$BASE_URL/assets/$asset").openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "image/png")
        connection.setRequestProperty("Authorization", "Bearer ${token(context)}")
        try {
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 2 * 1024 * 1024) return null
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val maxSide = maxOf(options.outWidth, options.outHeight)
            if (options.outWidth <= 0 || options.outHeight <= 0 || maxSide > 4096) return null
            options.inJustDecodeBounds = false
            options.inSampleSize = when {
                maxSide > 2048 -> 4
                maxSide > 1024 -> 2
                else -> 1
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            connection.disconnect()
        }
    }

    private fun request(path: String, context: Context, body: JSONObject?): String? {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Authorization", "Bearer ${token(context)}")
        if (body != null) { connection.doOutput=true; connection.requestMethod="POST"; connection.setRequestProperty("Content-Type","application/json"); connection.outputStream.use { it.write(body.toString().toByteArray()) } }
        return try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
