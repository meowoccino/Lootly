package com.lootly.app.engine

import com.lootly.app.data.model.TwitchSession
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MinerConfigGenerator(
    private val filesDir: File
) {
    data class GeneratedPaths(
        val primaryConfigYaml: File,
        val configsDir: File,
        val accountConfigYaml: File,
        val dataDir: File,
        val cookieFile: File
    )

    fun generate(
        session: TwitchSession,
        badgePollInterval: String = DEFAULT_BADGE_POLL_INTERVAL
    ): GeneratedPaths {
        val login = sanitizeLogin(session.login)
        require(login.isNotEmpty()) { "Twitch login username cannot be blank" }

        val configsDir = File(filesDir, "configs").apply { mkdirs() }
        val dataDir = File(filesDir, "miner_data").apply { mkdirs() }
        val cookiesDir = File(dataDir, "cookies").apply { mkdirs() }

        configsDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".yaml") || file.name.endsWith(".yml"))) {
                file.delete()
            }
        }

        val yaml = buildConfigYaml(login, badgePollInterval)
        val primaryConfigYaml = File(filesDir, "config.yaml")
        atomicWrite(primaryConfigYaml, yaml)

        val accountConfigYaml = File(configsDir, "$login.yaml")
        atomicWrite(accountConfigYaml, yaml)

        val cookieFile = File(cookiesDir, "$login.json")
        atomicWrite(cookieFile, buildCookieJarJson(session))

        return GeneratedPaths(
            primaryConfigYaml = primaryConfigYaml,
            configsDir = configsDir,
            accountConfigYaml = accountConfigYaml,
            dataDir = dataDir,
            cookieFile = cookieFile
        )
    }

    fun buildConfigYaml(
        login: String,
        badgePollInterval: String = DEFAULT_BADGE_POLL_INTERVAL
    ): String = buildString {
        appendLine("# Auto-generated runtime config for Twitch Badge Miner (account: $login)")
        appendLine("# Auth credentials are supplied exclusively via encrypted session env vars.")
        appendLine("enabled: true")
        appendLine()
        appendLine("features:")
        appendLine("  claim_drops_startup: true")
        appendLine("  enable_analytics: true")
        appendLine()
        appendLine("# Single-stream focus ensures Twitch credits watch-time badge drops reliably.")
        appendLine("max_watch_streams: 1")
        appendLine("streak_watch_streams: 0")
        appendLine()
        appendLine("priority:")
        appendLine("  - BADGES")
        appendLine("  - DROPS")
        appendLine("  - ORDER")
        appendLine()
        appendLine("# Automatic watch-time chat badge discovery")
        appendLine("badge_watcher:")
        appendLine("  enabled: true")
        appendLine("  poll_interval: $badgePollInterval")
        appendLine("  streamer_limit: 1")
        appendLine()
        appendLine("category_watcher:")
        appendLine("  enabled: false")
        appendLine()
        appendLine("team_watcher:")
        appendLine("  enabled: false")
        appendLine()
        appendLine("followers:")
        appendLine("  enabled: false")
        appendLine()
        appendLine("# Streamless HLS metadata/HEAD mining with automatic drop/badge claiming")
        appendLine("streamer_defaults:")
        appendLine("  make_predictions: false")
        appendLine("  follow_raid: false")
        appendLine("  claim_drops: true")
        appendLine("  claim_moments: false")
        appendLine("  watch_streak: false")
        appendLine("  community_goals: false")
        appendLine("  drops_only: false")
        appendLine("  chat: \"NEVER\"")
        appendLine()
        appendLine("streamers: []")
    }

    private fun buildCookieJarJson(session: TwitchSession): String {
        val arr = JSONArray()
        fun addCookie(name: String, value: String) {
            if (value.isBlank()) return
            arr.put(
                JSONObject().apply {
                    put("name", name)
                    put("value", value)
                    put("domain", ".twitch.tv")
                    put("path", "/")
                }
            )
        }
        addCookie("auth-token", session.authToken)
        addCookie("unique_id", session.deviceId)
        session.twilightUserJson?.let { addCookie("twilight-user", it) }
        return arr.toString(2)
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        tmp.setReadable(false, false)
        tmp.setReadable(true, true)
        tmp.setWritable(false, false)
        tmp.setWritable(true, true)
        if (!tmp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
    }

    companion object {
        const val DEFAULT_BADGE_POLL_INTERVAL = "1h"

        fun sanitizeLogin(login: String): String {
            return login.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "")
        }
    }
}
