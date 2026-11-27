package com.lootly.app.data.model

data class TwitchSession(
    val authToken: String,
    val userId: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val deviceId: String,
    val twilightUserJson: String? = null,
    val rawCookies: String = "",
    val capturedAtEpochMs: Long = System.currentTimeMillis()
) {
    val effectiveDisplayName: String
        get() = displayName.ifBlank { login }
}
