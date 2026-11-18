package com.vangnang.youtubelive

import android.os.Bundle
import com.facebook.AccessToken
import com.facebook.GraphRequest
import com.facebook.HttpMethod

/** Page credentials and ingest URLs stay in memory; never include them in diagnostics. */
object FacebookLive {
    val pagePermissions = FacebookPermissionState.required
    val permissions = listOf("public_profile") + pagePermissions

    /** Verify the current server grant, not only the SDK cache. Never expose token values. */
    fun checkPermissions(token: AccessToken, done: (Map<String, String>?, String?) -> Unit) {
        GraphRequest(token, "me/permissions", Bundle(), HttpMethod.GET, GraphRequest.Callback { response ->
            val data = response.jsonObject?.optJSONArray("data")
            if (response.error != null || data == null) {
                done(null, "Không kiểm tra được quyền Facebook (mã ${response.error?.errorCode ?: -1}). Hãy thử lại; đây chưa phải kết luận thiếu quyền.")
            } else {
                val states = mutableMapOf<String, String>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    states[item.optString("permission")] = item.optString("status")
                }
                done(states, null)
            }
        }).executeAsync()
    }
    class Page(val id: String, val name: String, internal val token: String, val isProfile: Boolean = false)
    class Broadcast(val id: String, val url: String, internal val page: Page)

    fun pages(token: AccessToken, done: (List<Page>, String?) -> Unit) {
        val result = mutableListOf<Page>()
        val cursors = mutableSetOf<String>()
        fun load(after: String?) {
            val params = Bundle().apply {
                putString("fields", "id,name,access_token,tasks")
                putString("limit", "100")
                after?.let { putString("after", it) }
            }
            GraphRequest(token, "me/accounts", params, HttpMethod.GET, GraphRequest.Callback { response ->
                if (response.error != null) {
                    done(emptyList(), "Facebook chưa cho phép đọc Trang (mã ${response.error?.errorCode}). Kiểm tra quyền Trang của ứng dụng và tài khoản.")
                } else {
                    val body = response.jsonObject
                    val data = body?.optJSONArray("data")
                    if (data != null) for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.optString("id")
                        val key = item.optString("access_token")
                        if (id.isNotBlank() && key.isNotBlank() && result.none { it.id == id }) {
                            result.add(Page(id, item.optString("name", "Fanpage"), key))
                        }
                    }
                    val paging = body?.optJSONObject("paging")
                    val cursor = paging?.optJSONObject("cursors")?.optString("after").orEmpty()
                    if (!paging?.optString("next").isNullOrBlank() && cursor.isNotBlank() && cursors.add(cursor)) load(cursor)
                    else done(result, null)
                }
            }).executeAsync()
        }
        load(null)
    }

    fun profile(token: AccessToken, done: (Page?, String?) -> Unit) {
        GraphRequest(token, "me", Bundle().apply { putString("fields", "id,name") }, HttpMethod.GET, GraphRequest.Callback { response ->
            val id = response.jsonObject?.optString("id").orEmpty()
            if (response.error != null || id.isBlank() || id != token.userId) {
                done(null, "Không đọc được trang cá nhân Facebook. Hãy đăng nhập lại.")
            } else done(Page(id, response.jsonObject?.optString("name").orEmpty().ifBlank { "Trang cá nhân" }, token.token, true), null)
        }).executeAsync()
    }

    fun create(page: Page, title: String, privacy: String = "SELF", done: (Broadcast?, String?) -> Unit) {
        if (!page.isProfile) { createAuthorized(page, title, privacy, done); return }
        val current = AccessToken.getCurrentAccessToken()
        if (current == null || current.isExpired || current.userId != page.id) {
            done(null, "Phiên đăng nhập trang cá nhân đã hết hạn hoặc đổi tài khoản. Hãy chọn lại nơi phát."); return
        }
        checkPermissions(current) { states, error ->
            if (states == null || error != null) done(null, error ?: "Chưa kiểm tra được quyền.")
            else if (FacebookPermissionState.missing(states, true).isNotEmpty())
                done(null, "Chưa có quyền publish_video. Về màn hình đầu, chọn Trang cá nhân và cấp quyền; ứng dụng Meta cần được phép dùng quyền này.")
            else createAuthorized(Page(page.id, page.name, current.token, true), title, privacy, done)
        }
    }
    private fun createAuthorized(page: Page, title: String, privacy: String, done: (Broadcast?, String?) -> Unit) {
        val params = Bundle().apply {
            putString("access_token", page.token)
            putString("title", title.take(254))
            if (page.isProfile) putString("privacy", org.json.JSONObject().put("value", if (privacy in listOf("SELF", "ALL_FRIENDS", "EVERYONE")) privacy else "SELF").toString())
            putString("status", "LIVE_NOW")
        }
        GraphRequest(null, "${page.id}/live_videos", params, HttpMethod.POST, GraphRequest.Callback { response ->
            val body = response.jsonObject
            val id = body?.optString("id").orEmpty()
            val url = body?.optString("secure_stream_url").orEmpty()
            if (response.error != null) {
                done(null, if (page.isProfile)
                    "Meta từ chối tạo live trang cá nhân (mã ${response.error?.errorCode}, mã phụ ${response.error?.subErrorCode}). Kiểm tra quyền publish_video, quyền truy cập Live Video API của ứng dụng và điều kiện livestream tài khoản. Chưa có luồng nào được gửi."
                    else "Không tạo được Facebook Live (mã ${response.error?.errorCode}). Kiểm tra quyền đăng video và điều kiện livestream của Fanpage.")
            } else if (id.isBlank() || !url.startsWith("rtmps://")) {
                if (id.isNotBlank()) end(Broadcast(id, "", page)) {}
                done(null, "Facebook không trả về địa chỉ phát an toàn. Phiên chưa được bắt đầu.")
            } else done(Broadcast(id, url, page), null)
        }).executeAsync()
    }

    fun end(broadcast: Broadcast, done: (String?) -> Unit) {
        val params = Bundle().apply {
            putString("access_token", broadcast.page.token)
            putBoolean("end_live_video", true)
        }
        GraphRequest(null, broadcast.id, params, HttpMethod.POST, GraphRequest.Callback { response ->
            done(if (response.error == null) null else "Facebook chưa xác nhận kết thúc phiên.")
        }).executeAsync()
    }
}
