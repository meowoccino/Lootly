package com.lootly.app.data.auth

import com.lootly.app.data.model.TwitchSession
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TwitchAccountRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    data class TwitchProfile(
        val userId: String,
        val login: String,
        val displayName: String,
        val avatarUrl: String?
    )

    suspend fun buildSessionFromCookies(
        rawCookieHeader: String,
        cookieMap: Map<String, String>
    ): Result<TwitchSession> = withContext(Dispatchers.IO) {
        runCatching {
            val authToken = cookieMap["auth-token"]?.trim().orEmpty()
            require(authToken.isNotBlank()) { "Missing auth-token cookie" }

            val deviceId = cookieMap["unique_id"]?.trim()?.takeIf { it.isNotBlank() }
                ?: TwitchSessionStore.generateFallbackDeviceId()
            val twilightRaw = cookieMap["twilight-user"]

            val gqlProfile = fetchCurrentUserViaGql(authToken, deviceId)
            val cookieProfile = parseTwilightUserCookie(twilightRaw)
            val validated = if (gqlProfile == null && cookieProfile == null) {
                validateOAuthToken(authToken)
            } else {
                null
            }

            val login = gqlProfile?.login
                ?: cookieProfile?.login
                ?: validated?.second
                ?: error("Unable to resolve Twitch username from session")
            val userId = gqlProfile?.userId
                ?: cookieProfile?.userId
                ?: validated?.first
                ?: ""
            val displayName = gqlProfile?.displayName
                ?: cookieProfile?.displayName
                ?: login
            val avatarUrl = gqlProfile?.avatarUrl

            TwitchSession(
                authToken = authToken,
                userId = userId,
                login = login,
                displayName = displayName,
                avatarUrl = avatarUrl,
                deviceId = deviceId,
                twilightUserJson = twilightRaw,
                rawCookies = rawCookieHeader,
                capturedAtEpochMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun refreshProfile(authToken: String, deviceId: String): TwitchProfile? =
        withContext(Dispatchers.IO) {
            fetchCurrentUserViaGql(authToken, deviceId)
        }

    private fun fetchCurrentUserViaGql(authToken: String, deviceId: String): TwitchProfile? =
        runCatching {
            val payload = JSONObject().apply {
                put("operationName", "AndroidMinerCurrentUser")
                put("query", "query AndroidMinerCurrentUser { currentUser { id login displayName profileImageURL(width: 150) } }")
            }
            val request = Request.Builder()
                .url(TWITCH_GQL_URL)
                .header("Authorization", "OAuth $authToken")
                .header("Client-Id", TWITCH_BROWSER_CLIENT_ID)
                .header("X-Device-Id", deviceId)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val user = JSONObject(body).optJSONObject("data")?.optJSONObject("currentUser")
                    ?: return@runCatching null
                val login = user.optString("login")
                if (login.isBlank()) return@runCatching null
                TwitchProfile(
                    userId = user.optString("id"),
                    login = login,
                    displayName = user.optString("displayName", login),
                    avatarUrl = user.optString("profileImageURL").takeIf { it.isNotBlank() }
                )
            }
        }.getOrNull()

    private fun parseTwilightUserCookie(rawValue: String?): TwitchProfile? {
        if (rawValue.isNullOrBlank()) return null
        return runCatching {
            val decoded = URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
            val obj = JSONObject(decoded)
            val login = obj.optString("login")
            if (login.isBlank()) return@runCatching null
            TwitchProfile(
                userId = obj.optString("id"),
                login = login,
                displayName = obj.optString("displayName", login),
                avatarUrl = null
            )
        }.getOrNull()
    }

    private fun validateOAuthToken(authToken: String): Pair<String, String>? =
        runCatching {
            val request = Request.Builder()
                .url(TWITCH_VALIDATE_URL)
                .header("Authorization", "OAuth $authToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val obj = JSONObject(response.body?.string().orEmpty())
                val login = obj.optString("login")
                if (login.isBlank()) null else obj.optString("user_id") to login
            }
        }.getOrNull()

    companion object {
        const val TWITCH_BROWSER_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val TWITCH_VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
        const val TWITCH_GQL_URL = "https://gql.twitch.tv/gql"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseCookieString(cookieHeader: String?): Map<String, String> {
            if (cookieHeader.isNullOrBlank()) return emptyMap()
            val result = LinkedHashMap<String, String>()
            for (part in cookieHeader.split(';')) {
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val key = part.substring(0, idx).trim()
                    val value = part.substring(idx + 1).trim()
                    if (key.isNotEmpty()) {
                        result[key] = value
                    }
                }
            }
            return result
        }
    }
}
