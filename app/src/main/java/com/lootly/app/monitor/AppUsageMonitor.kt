package com.lootly.app.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppUsageMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val onTwitchForegroundChanged: suspend (Boolean) -> Unit
) {
    private var monitorJob: Job? = null
    @Volatile private var lastKnownForegroundPackage: String? = null
    @Volatile private var lastReportedTwitchForeground: Boolean = false
    @Volatile private var lastQueryTimestampMs: Long = 0L

    fun start() {
        if (monitorJob?.isActive == true) return
        lastKnownForegroundPackage = null
        lastReportedTwitchForeground = false
        lastQueryTimestampMs = 0L
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val isForeground = runCatching { evaluateTwitchForeground() }.getOrDefault(false)
                if (isForeground != lastReportedTwitchForeground) {
                    lastReportedTwitchForeground = isForeground
                    onTwitchForegroundChanged(isForeground)
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        lastKnownForegroundPackage = null
        lastReportedTwitchForeground = false
        lastQueryTimestampMs = 0L
    }

    private fun evaluateTwitchForeground(): Boolean {
        if (!PermissionHelper.hasUsageStatsPermission(context)) return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isInteractive) {
            if (lastKnownForegroundPackage == TWITCH_PACKAGE) {
                lastKnownForegroundPackage = null
            }
            return false
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false

        val now = System.currentTimeMillis()
        val start = if (lastQueryTimestampMs == 0L) {
            now - INITIAL_LOOKBACK_MS
        } else {
            (lastQueryTimestampMs - OVERLAP_WINDOW_MS).coerceAtLeast(now - INITIAL_LOOKBACK_MS)
        }

        val events = usm.queryEvents(start, now)
        val event = UsageEvents.Event()
        var currentPkg = lastKnownForegroundPackage
        val resumedType = resumedEventType()
        val pausedType = pausedEventType()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                resumedType, UsageEvents.Event.MOVE_TO_FOREGROUND -> currentPkg = pkg
                pausedType, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPkg == pkg) currentPkg = null
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (currentPkg == TWITCH_PACKAGE) currentPkg = null
                }
            }
        }
        lastKnownForegroundPackage = currentPkg
        lastQueryTimestampMs = now
        return currentPkg == TWITCH_PACKAGE
    }

    private fun resumedEventType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun pausedEventType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }
    }

    companion object {
        const val TWITCH_PACKAGE = "tv.twitch.android.app"
        const val POLL_INTERVAL_MS = 3_000L
        private const val INITIAL_LOOKBACK_MS = 300_000L
        private const val OVERLAP_WINDOW_MS = 2_000L
    }
}
