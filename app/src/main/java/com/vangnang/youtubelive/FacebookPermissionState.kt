package com.vangnang.youtubelive

internal object FacebookPermissionState {
    val required = listOf("pages_show_list", "pages_read_engagement", "pages_manage_posts")
    val profileRequired = listOf("publish_video")
    fun requiredFor(profile: Boolean) = if (profile) profileRequired else required
    fun missing(states: Map<String, String>, profile: Boolean = false): List<String> = requiredFor(profile).filter { states[it] != "granted" }
    fun loginPermissions(target: String) = when (target) {
        "profile" -> listOf("public_profile") + profileRequired
        "page" -> listOf("public_profile") + required
        else -> emptyList()
    }
    fun privacyValue(index: Int) = listOf("SELF", "ALL_FRIENDS", "EVERYONE").getOrElse(index) { "SELF" }
}
