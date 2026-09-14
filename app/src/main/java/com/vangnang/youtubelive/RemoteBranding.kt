package com.vangnang.youtubelive

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteBranding(val enabled: Boolean = false, val label: String = "")

/** Read-only public switch. Repository write permission remains with the owner; no admin token is shipped in the APK. */
object RemoteBrandingClient {
    const val CONFIG_URL = "https://raw.githubusercontent.com/duongnang-cyber/live-san-dau/main/remote-branding.json"

    fun fetch(): RemoteBranding {
        val connection = URL(CONFIG_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        return try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val enabled = json.optBoolean("enabled", false)
            val label = json.optString("label", "").replace(Regex("\\s+"), " ").trim().take(18)
            RemoteBranding(enabled, if (enabled) label else "")
        } finally {
            connection.disconnect()
        }
    }
}
