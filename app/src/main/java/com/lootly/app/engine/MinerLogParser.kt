package com.lootly.app.engine

import com.lootly.app.data.model.LogEntry
import com.lootly.app.data.model.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject

class MinerLogParser {

    data class ParsedProgress(
        val watched: Int,
        val required: Int,
        val percent: Int
    )

    fun parseLine(rawLine: String, id: Long = 0L): ParsedLogResult {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            return ParsedLogResult(null, ParsedMinerEvent.None)
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val json = runCatching { JSONObject(trimmed) }.getOrNull()
            if (json != null && json.has("msg")) {
                return parseJsonLog(json, trimmed, id)
            }
        }
        val level = when {
            trimmed.contains("ERROR", ignoreCase = true) || trimmed.contains("panic", ignoreCase = true) -> LogLevel.ERROR
            trimmed.contains("WARN", ignoreCase = true) -> LogLevel.WARN
            else -> LogLevel.INFO
        }
        return ParsedLogResult(
            logEntry = LogEntry(
                id = id,
                timestamp = CircularLogBuffer.currentTimeShort(),
                level = level,
                category = "Engine",
                message = trimmed,
                rawLine = trimmed
            ),
            event = ParsedMinerEvent.None
        )
    }

    private fun parseJsonLog(json: JSONObject, rawLine: String, id: Long): ParsedLogResult {
        val levelStr = json.optString("level", "INFO").uppercase(Locale.ROOT)
        val msg = json.optString("msg", "")
        val timestamp = formatIsoTimestamp(json.optString("time", ""))

        if (msg == "Sent minute watched event") {
            val streamer = json.optString("streamer")
            return ParsedLogResult(
                logEntry = LogEntry(
                    id = id,
                    timestamp = timestamp,
                    level = LogLevel.EVENT,
                    category = "Heartbeat",
                    message = "Watch heartbeat confirmed • @$streamer",
                    rawLine = rawLine
                ),
                event = ParsedMinerEvent.HeartbeatConfirmed(streamer)
            )
        }

        if (levelStr == "DEBUG" ||
            msg == "Badge catalog watch reward skipped" ||
            msg == "Badge campaign skipped" ||
            msg.contains("Drop is synthetic") ||
            msg.contains("Telemetry disabled") ||
            msg.contains("Config editor skipped") ||
            msg.contains("Using built-in default Twitch client identifiers") ||
            (msg.contains("Drop in progress") && json.optString("status").equals("CLAIMED", ignoreCase = true))
        ) {
            return ParsedLogResult(null, ParsedMinerEvent.None)
        }

        val event = extractEvent(msg, json)
        val (category, formattedMsg, logLevel) = formatForConsole(levelStr, msg, json, event)
        return ParsedLogResult(
            logEntry = LogEntry(
                id = id,
                timestamp = timestamp,
                level = logLevel,
                category = category,
                message = stripLeadingEmojis(formattedMsg),
                rawLine = rawLine
            ),
            event = event
        )
    }

    private fun extractEvent(msg: String, json: JSONObject): ParsedMinerEvent {
        if (msg.contains("Discovered badge campaign stream") || msg.contains("Using existing streamer for badge campaign")) {
            val streamer = json.optString("streamer")
            val campaign = json.optString("campaign")
            val category = json.optString("category")
            val firstBadge = json.optString("badges").split(",").firstOrNull()?.trim().orEmpty()
            val badgeName = firstBadge.ifBlank { campaign }
            val imageUrl = json.optString("badge_image_url").takeIf { it.isNotBlank() }
            return ParsedMinerEvent.BadgeDiscovered(
                badgeName = badgeName,
                campaignName = campaign.ifBlank { badgeName },
                channelLogin = streamer,
                category = category,
                badgeImageUrl = imageUrl
            )
        }

        if (msg == "Badge campaign eligible") {
            val campaign = json.optString("campaign")
            val badgeName = json.optString("badge_name").ifBlank { campaign }
            val badges = json.optString("badges").ifBlank { badgeName }
            val game = json.optString("game")
            val imageUrl = json.optString("badge_image_url").takeIf { it.isNotBlank() }
            val required = json.optInt("required_minutes", 30).let { if (it <= 0) 30 else it }
            val endsAt = json.optString("ends_at")
            return ParsedMinerEvent.BadgeCampaignEligible(
                campaignName = campaign,
                badgeName = badgeName,
                allTiers = badges,
                gameName = game,
                badgeImageUrl = imageUrl,
                requiredMinutes = required,
                endsAtIso = endsAt
            )
        }

        if (msg.contains("Badge campaigns evaluated")) {
            return ParsedMinerEvent.BadgeEvaluated(
                catalogCount = json.optInt("catalog", 0),
                eligibleCount = json.optInt("eligible", 0),
                activeCount = json.optInt("active", 0),
                ownedBadgesCount = json.optInt("owned_badges", 0)
            )
        }

        if (msg.contains("Watching") && !msg.contains("Stopped watching")) {
            val streamer = json.optString("streamer")
            if (streamer.isNotBlank()) return ParsedMinerEvent.WatchingChannel(streamer)
        }

        if (msg.contains("Stopped watching")) {
            val streamer = json.optString("streamer")
            if (streamer.isNotBlank()) return ParsedMinerEvent.StoppedWatchingChannel(streamer)
        }

        val eventField = json.optString("event")
        if (eventField == "DROP_CLAIM" || msg.contains("Claiming drop from inventory")) {
            val reward = json.optString("drop").ifBlank { json.optString("reward", "Badge Drop") }
            val category = json.optString("category")
            return ParsedMinerEvent.DropClaimed(reward, category)
        }

        if (msg.contains("Drop in progress") ||
            msg.contains("Drop progress") ||
            msg.contains("Drop milestone") ||
            eventField == "DROP_STATUS" ||
            eventField == "DROP_MILESTONE"
        ) {
            val reward = json.optString("reward").ifBlank {
                extractDropBenefitFromToString(json.optString("drop"))
            }
            val streamer = json.optString("streamer").takeIf { it.isNotBlank() }
            val progressRaw = json.optString("progress")
            val status = json.optString("status").uppercase(Locale.ROOT)
            val parsed = parseProgressText(progressRaw)
            if (parsed != null) {
                return ParsedMinerEvent.DropProgress(
                    rewardName = reward,
                    channelLogin = streamer,
                    watchedMinutes = parsed.watched,
                    requiredMinutes = parsed.required,
                    percent = parsed.percent,
                    isClaimable = status == "CLAIMABLE",
                    isClaimed = status == "CLAIMED"
                )
            }
        }

        if (msg.contains("Watch heartbeats paused")) {
            return ParsedMinerEvent.WatchPaused(json.optString("reason", "manual"))
        }

        if (msg.contains("Watch heartbeats resumed")) {
            return ParsedMinerEvent.WatchResumed
        }

        if (msg.contains("login failed", ignoreCase = true) ||
            msg.contains("No valid credentials found", ignoreCase = true) ||
            msg.contains("token validation failed", ignoreCase = true)
        ) {
            return ParsedMinerEvent.AuthError(json.optString("error").ifBlank { msg })
        }

        return ParsedMinerEvent.None
    }

    private fun formatForConsole(
        levelStr: String,
        msg: String,
        json: JSONObject,
        event: ParsedMinerEvent
    ): Triple<String, String, LogLevel> {
        val defaultLevel = when (levelStr) {
            "WARN", "WARNING" -> LogLevel.WARN
            "ERROR" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        return when (event) {
            is ParsedMinerEvent.BadgeDiscovered -> Triple(
                "Badge",
                "Target: ${event.badgeName} on @${event.channelLogin} (${event.campaignName})",
                LogLevel.EVENT
            )
            is ParsedMinerEvent.BadgeCampaignEligible -> Triple(
                "Badge",
                "Eligible: ${event.badgeName} (${event.campaignName} • ${event.requiredMinutes}m)",
                LogLevel.INFO
            )
            is ParsedMinerEvent.BadgeEvaluated -> Triple(
                "Badge",
                "Scan complete • ${event.eligibleCount} eligible, ${event.activeCount} active, ${event.ownedBadgesCount} owned",
                LogLevel.INFO
            )
            is ParsedMinerEvent.DropProgress -> Triple(
                "Drop",
                "${event.rewardName.ifBlank { "Badge" }} • ${event.percent}%",
                LogLevel.EVENT
            )
            is ParsedMinerEvent.DropClaimed -> Triple(
                "Claim",
                "Claimed badge/drop: ${event.rewardName}",
                LogLevel.EVENT
            )
            is ParsedMinerEvent.WatchPaused -> Triple(
                "Pause",
                "Watch heartbeats paused (${event.reason})",
                LogLevel.WARN
            )
            is ParsedMinerEvent.WatchResumed -> Triple(
                "Resume",
                "Watch heartbeats resumed",
                LogLevel.EVENT
            )
            is ParsedMinerEvent.WatchingChannel -> Triple(
                "Watch",
                "Watching @${event.channelLogin}",
                LogLevel.INFO
            )
            is ParsedMinerEvent.StoppedWatchingChannel -> Triple(
                "Watch",
                "Stopped watching @${event.channelLogin}",
                LogLevel.INFO
            )
            is ParsedMinerEvent.AuthError -> Triple(
                "Auth",
                "Authentication error: ${event.message}",
                LogLevel.ERROR
            )
            else -> {
                val extra = buildExtraSummary(json)
                val cat = when {
                    msg.contains("Integrity", ignoreCase = true) -> "Integrity"
                    msg.contains("Badge", ignoreCase = true) -> "Badge"
                    msg.contains("Drop", ignoreCase = true) -> "Drop"
                    else -> "Miner"
                }
                val text = if (extra.isEmpty()) msg else "$msg ($extra)"
                Triple(cat, text, defaultLevel)
            }
        }
    }

    private fun buildExtraSummary(json: JSONObject): String {
        val ignored = setOf("time", "level", "msg", "account", "event", "badge_image_url")
        val parts = mutableListOf<String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in ignored) {
                val value = json.opt(key)?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    parts.add("$key=$value")
                }
            }
        }
        return parts.take(4).joinToString(", ")
    }

    internal fun parseProgressText(raw: String): ParsedProgress? {
        if (raw.isBlank()) return null
        BAR_REGEX.find(raw)?.let { match ->
            val pct = match.groupValues[1].toIntOrNull() ?: 0
            val watched = match.groupValues[2].toIntOrNull() ?: 0
            val required = match.groupValues[3].toIntOrNull() ?: 0
            return ParsedProgress(watched, required, pct.coerceIn(0, 100))
        }
        FRACTION_REGEX.find(raw)?.let { match ->
            val watched = match.groupValues[1].toIntOrNull() ?: 0
            val required = match.groupValues[2].toIntOrNull() ?: 0
            val pct = if (required > 0) ((watched * 100) / required).coerceIn(0, 100) else 0
            return ParsedProgress(watched, required, pct)
        }
        return null
    }

    private fun extractDropBenefitFromToString(dropStr: String): String {
        if (dropStr.isBlank()) return ""
        Regex("benefit=([^,)]+)").find(dropStr)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        return Regex("name=([^,)]+)").find(dropStr)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun stripLeadingEmojis(text: String): String {
        return Regex("\\s{2,}").replace(EMOJI_REGEX.replace(text, ""), " ").trim()
    }

    private fun formatIsoTimestamp(iso: String): String {
        if (iso.isNotBlank()) {
            runCatching {
                val localTime = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalTime()
                return String.format(Locale.US, "%02d:%02d:%02d", localTime.hour, localTime.minute, localTime.second)
            }
        }
        return CircularLogBuffer.currentTimeShort()
    }

    companion object {
        private val BAR_REGEX = Regex("(\\d{1,3})%\\s*\\[(\\d+)/(\\d+)]")
        private val FRACTION_REGEX = Regex("(\\d+)\\s*/\\s*(\\d+)m?")
        private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cn}\\uFE0F\\u200D]+")
    }
}
