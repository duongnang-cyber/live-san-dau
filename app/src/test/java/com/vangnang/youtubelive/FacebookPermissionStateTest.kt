package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class FacebookPermissionStateTest {
    @Test fun pageGrantsDoNotRequirePublicProfileEntry() {
        assertTrue(FacebookPermissionState.missing(FacebookPermissionState.required.associateWith { "granted" }).isEmpty())
    }
    @Test fun basicLoginDoesNotAuthorizePagePublishing() {
        assertEquals(FacebookPermissionState.required, FacebookPermissionState.missing(mapOf("public_profile" to "granted")))
    }
    @Test fun declinedAndExpiredGrantsRemainMissing() {
        assertEquals(listOf("pages_read_engagement", "pages_manage_posts"), FacebookPermissionState.missing(mapOf(
            "pages_show_list" to "granted", "pages_read_engagement" to "expired", "pages_manage_posts" to "declined")))
    }
    @Test fun emptyServerGrantsDoNotAuthorizePages() {
        assertEquals(FacebookPermissionState.required, FacebookPermissionState.missing(emptyMap()))
    }

    @Test fun pagePermissionsNeverAuthorizeProfilePublishing() {
        val states = FacebookPermissionState.required.associateWith { "granted" }
        assertEquals(listOf("publish_video"), FacebookPermissionState.missing(states, true))
    }
    @Test fun profilePublishingDoesNotDependOnPageAccess() {
        val states = mapOf("publish_video" to "granted", "pages_manage_posts" to "declined")
        assertTrue(FacebookPermissionState.missing(states, true).isEmpty())
        assertFalse(FacebookPermissionState.missing(states).isEmpty())
        for (status in listOf("declined", "expired", "unknown")) {
            assertFalse(FacebookPermissionState.missing(mapOf("publish_video" to status), true).isEmpty())
        }
    }
    @Test fun loginRequestsOnlySelectedDestinationPermissions() {
        assertTrue(FacebookPermissionState.loginPermissions("choose").isEmpty())
        assertTrue(FacebookPermissionState.loginPermissions("profile").contains("publish_video"))
        assertTrue(FacebookPermissionState.loginPermissions("page").containsAll(FacebookPermissionState.required))
        assertFalse(FacebookPermissionState.loginPermissions("page").contains("publish_video"))
        assertFalse(FacebookPermissionState.loginPermissions("profile").any { it.startsWith("pages_") })
    }
    @Test fun profilePrivacyDefaultsToPrivateAndRequiresExplicitPublicChoice() {
        assertEquals("SELF", FacebookPermissionState.privacyValue(0))
        assertEquals("SELF", FacebookPermissionState.privacyValue(-1))
        assertEquals("SELF", FacebookPermissionState.privacyValue(99))
        assertEquals("ALL_FRIENDS", FacebookPermissionState.privacyValue(1))
        assertEquals("EVERYONE", FacebookPermissionState.privacyValue(2))
    }
}
