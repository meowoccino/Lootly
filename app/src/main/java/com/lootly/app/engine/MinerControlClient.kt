package com.lootly.app.engine

import com.lootly.app.data.model.ActiveBadgeCampaign
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MinerControlClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    suspend fun pause(port: Int, apiKey: String, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(reason, StandardCharsets.UTF_8.name())
            postControl(port, apiKey, "/api/control/pause?reason=$encoded")
        }

    suspend fun resume(port: Int, apiKey: String): Boolean =
        withContext(Dispatchers.IO) {
            postControl(port, apiKey, "/api/control/resume")
        }

    suspend fun forceRefreshBadges(port: Int, apiKey: String): Boolean =
        withContext(Dispatchers.IO) {
            postControl(port, apiKey, "/api/control/refresh-badges")
        }

    suspend fun pinBadge(port: Int, apiKey: String, campaign: String): Boolean =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(campaign, StandardCharsets.UTF_8.name())
            postControl(port, apiKey, "/api/control/pin-badge?campaign=$encoded")
        }

    suspend fun setFilters(port: Int, apiKey: String, badges: Boolean, drops: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            postControl(port, apiKey, "/api/control/set-filters?badges=$badges&drops=$drops")
        }

    suspend fun fetchActiveBadgeCampaign(port: Int, apiKey: String): ActiveBadgeCampaign? =
        withContext(Dispatchers.IO) {
            if (port <= 0) return@withContext null
            runCatching {
                val request = Request.Builder()
                    .url("http://127.0.0.1:$port/api/streamers")
                    .header("X-API-Key", apiKey)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    val arr = parseStreamersArray(body) ?: return@use null

                    var fallbackOnline: JSONObject? = null
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optBoolean("is_badge_watched", false)) {
                            return@use toActiveBadgeCampaign(item)
                        }
                        if (fallbackOnline == null && item.optBoolean("is_online", false)) {
                            fallbackOnline = item
                        }
                    }
                    val drops = fallbackOnline?.optJSONArray("active_drops")
                    if (fallbackOnline != null && drops != null && drops.length() > 0) {
                        toActiveBadgeCampaign(fallbackOnline)
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }

    private fun parseStreamersArray(body: String): JSONArray? {
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("data")
            else -> null
        }
    }

    private fun toActiveBadgeCampaign(item: JSONObject): ActiveBadgeCampaign {
        val username = item.optString("username")
        val displayName = item.optString("display_name").ifBlank { username }
        val badgeCampaign = item.optString("badge_campaign")
        val game = item.optString("game")
        val imageUrl = item.optString("badge_image_url").takeIf { it.isNotBlank() }

        val badgeNames = item.optJSONArray("badge_names")
        val preferredBadgeName = if (badgeNames != null && badgeNames.length() > 0) {
            badgeNames.optString(0).ifBlank { badgeCampaign }
        } else {
            badgeCampaign
        }

        val activeDrops = item.optJSONArray("active_drops")
        val selectedDrop = selectBestDrop(activeDrops, preferredBadgeName)

        val dropReward = selectedDrop?.optString("reward").orEmpty()
        val finalBadgeName = when {
            dropReward.isNotBlank() && (preferredBadgeName.isBlank() || preferredBadgeName.equals(badgeCampaign, ignoreCase = true)) -> dropReward
            preferredBadgeName.isNotBlank() -> preferredBadgeName
            dropReward.isNotBlank() -> dropReward
            else -> "Watch-Time Badge"
        }

        val finalCampaignName = badgeCampaign.ifBlank {
            selectedDrop?.optString("game").orEmpty().ifBlank {
                game.ifBlank { finalBadgeName }
            }
        }

        return ActiveBadgeCampaign(
            badgeName = finalBadgeName,
            campaignName = finalCampaignName,
            channelLogin = username,
            channelDisplayName = displayName,
            category = game,
            badgeImageUrl = imageUrl,
            watchedMinutes = selectedDrop?.optInt("watched_minutes", 0) ?: 0,
            requiredMinutes = selectedDrop?.optInt("required_minutes", 0) ?: 0,
            progressPercent = selectedDrop?.optInt("progress_percent", 0) ?: 0,
            isClaimable = selectedDrop?.optBoolean("is_claimable", false) ?: false,
            isClaimed = selectedDrop?.optBoolean("is_claimed", false) ?: false,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    private fun selectBestDrop(activeDrops: JSONArray?, targetName: String): JSONObject? {
        if (activeDrops == null || activeDrops.length() == 0) return null

        if (targetName.isNotBlank()) {
            for (i in 0 until activeDrops.length()) {
                val d = activeDrops.optJSONObject(i) ?: continue
                val reward = d.optString("reward")
                val claimed = d.optBoolean("is_claimed", false)
                val pct = d.optInt("progress_percent", 0)
                if (!claimed && pct < 100 && matchesName(reward, targetName)) {
                    return d
                }
            }
        }

        var bestInProgress: JSONObject? = null
        var bestPct = -1
        for (i in 0 until activeDrops.length()) {
            val d = activeDrops.optJSONObject(i) ?: continue
            val claimed = d.optBoolean("is_claimed", false)
            val pct = d.optInt("progress_percent", 0)
            if (!claimed && pct < 100 && pct > bestPct) {
                bestPct = pct
                bestInProgress = d
            }
        }
        if (bestInProgress != null) return bestInProgress

        if (targetName.isNotBlank()) {
            for (i in 0 until activeDrops.length()) {
                val d = activeDrops.optJSONObject(i) ?: continue
                if (matchesName(d.optString("reward"), targetName)) return d
            }
        }
        return activeDrops.optJSONObject(0)
    }

    private fun matchesName(a: String, b: String): Boolean {
        return a.equals(b, ignoreCase = true) ||
            a.contains(b, ignoreCase = true) ||
            b.contains(a, ignoreCase = true)
    }

    private fun postControl(port: Int, apiKey: String, path: String): Boolean {
        if (port <= 0) return false
        return runCatching {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port$path")
                .header("X-API-Key", apiKey)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
