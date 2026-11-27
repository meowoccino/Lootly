package com.lootly.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lootly.app.MainActivity
import com.lootly.app.R
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.data.model.MinerStatusSnapshot

class MinerNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        val statusChannel = NotificationChannel(
            CHANNEL_ID_STATUS,
            context.getString(R.string.notification_channel_miner_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_miner_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val claimsChannel = NotificationChannel(
            CHANNEL_ID_CLAIMS,
            context.getString(R.string.notification_channel_claims_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_claims_desc)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(statusChannel)
        notificationManager.createNotificationChannel(claimsChannel)
    }

    fun buildStatusNotification(snapshot: MinerStatusSnapshot): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(context, MinerForegroundService::class.java).apply {
            action = MinerForegroundService.ACTION_TOGGLE_PAUSE
        }
        val togglePending = PendingIntent.getService(
            context,
            REQ_TOGGLE_PAUSE,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, MinerForegroundService::class.java).apply {
            action = MinerForegroundService.ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            context,
            REQ_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val active = snapshot.activeCampaign
        val title = snapshot.runState.label
        val text = when {
            snapshot.runState == MinerRunState.PAUSED_TWITCH_OPEN ->
                active?.let { "Waiting for Twitch app to close • ${it.badgeName}" }
                    ?: "Watch heartbeats paused while Twitch app is open"
            snapshot.runState == MinerRunState.PAUSED_MANUAL ->
                active?.let { "${it.badgeName} • @${it.channelLogin}" }
                    ?: "Tap Resume to continue automatic badge mining"
            active != null -> buildString {
                append(active.badgeName)
                if (active.channelLogin.isNotBlank()) {
                    append(" • @").append(active.channelLogin)
                }
                append(" • ").append(active.progressWithEtaSummary)
            }
            snapshot.runState == MinerRunState.STANDBY_NO_BADGES ->
                "All current badges earned • low-power standby"
            snapshot.runState == MinerRunState.SCANNING_BADGES ->
                if (snapshot.eligibleCampaignCount > 0) {
                    "Finding live stream • ${snapshot.eligibleCampaignCount} eligible"
                } else {
                    "Checking for live watch-time badge streams…"
                }
            snapshot.runState == MinerRunState.STARTING ->
                "Connecting Go miner & WebView integrity bridge…"
            else -> "Ready for automatic watch-time badge mining"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(R.drawable.ic_stat_miner)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, if (snapshot.runState.isPaused) "Resume" else "Pause", togglePending)
            .addAction(0, "Stop", stopPending)

        if (active != null) {
            builder.setSubText(active.progressSummary)
            builder.setProgress(100, active.computedPercent, false)
        } else if (snapshot.runState == MinerRunState.STARTING) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun updateStatusNotification(snapshot: MinerStatusSnapshot) {
        runCatching {
            notificationManager.notify(NOTIFICATION_ID_STATUS, buildStatusNotification(snapshot))
        }
    }

    fun notifyBadgeClaimed(badgeName: String, category: String) {
        runCatching {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                REQ_OPEN_APP,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val contentText = if (category.isNotBlank()) "$badgeName ($category)" else badgeName
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_CLAIMS)
                .setSmallIcon(R.drawable.ic_stat_miner)
                .setContentTitle("Watch-Time Badge Claimed")
                .setContentText(contentText)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            notificationManager.notify((badgeName.hashCode() and 0x0FFF) + NOTIFICATION_ID_CLAIM_BASE, notification)
        }
    }

    companion object {
        const val CHANNEL_ID_STATUS = "twitch_miner_status"
        const val CHANNEL_ID_CLAIMS = "twitch_miner_claims"
        const val NOTIFICATION_ID_STATUS = 4101
        private const val NOTIFICATION_ID_CLAIM_BASE = 4200
        private const val REQ_OPEN_APP = 101
        private const val REQ_TOGGLE_PAUSE = 102
        private const val REQ_STOP = 103
    }
}
