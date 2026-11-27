package com.lootly.app.bridge

import org.json.JSONObject

data class IntegrityTokenData(
    val token: String,
    val expirationEpochMs: Long,
    val deviceId: String,
    val clientId: String,
    val clientVersion: String,
    val clientSessionId: String,
    val userAgent: String,
    val capturedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis(), safetyMarginMs: Long = 300_000L): Boolean {
        return token.isNotBlank() && (expirationEpochMs - nowMs) > safetyMarginMs
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("token", token)
            put("expiration", expirationEpochMs)
            put("device_id", deviceId)
            put("client_id", clientId)
            put("client_version", clientVersion)
            put("client_session_id", clientSessionId)
            put("user_agent", userAgent)
        }.toString()
    }
}
