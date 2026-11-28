package com.lootly.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lootly.app.TwitchMinerApp
import com.lootly.app.bridge.IntegrityHttpBridgeServer
import com.lootly.app.bridge.WebViewIntegrityBridge
import com.lootly.app.data.model.ActiveBadgeCampaign
import com.lootly.app.data.model.EligibleBadgeItem
import com.lootly.app.data.model.LogLevel
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.engine.MinerControlClient
import com.lootly.app.engine.MinerLogParser
import com.lootly.app.engine.MinerProcessManager
import com.lootly.app.engine.ParsedMinerEvent
import com.lootly.app.monitor.AppUsageMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MinerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationManager: MinerNotificationManager
    private lateinit var logParser: MinerLogParser
    private lateinit var controlClient: MinerControlClient

    private var integrityBridge: WebViewIntegrityBridge? = null
    private var integrityHttpServer: IntegrityHttpBridgeServer? = null
    private var processManager: MinerProcessManager? = null
    private var appUsageMonitor: AppUsageMonitor? = null

    private var notificationObserverJob: Job? = null
    private var autoPausePrefObserverJob: Job? = null
    private var apiPollJob: Job? = null
    private var restartJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var manualPaused = false
    @Volatile private var twitchInForeground = false
    @Volatile private var shuttingDown = false

    private val app: TwitchMinerApp
        get() = application as TwitchMinerApp

    override fun onCreate() {
        super.onCreate()
        notificationManager = MinerNotificationManager(this)
        notificationManager.ensureChannels()
        logParser = MinerLogParser()
        controlClient = MinerControlClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownAndStop()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                serviceScope.launch { handleToggleManualPause() }
                return START_STICKY
            }
            ACTION_FORCE_REFRESH -> {
                serviceScope.launch { handleForceRefreshBadges() }
                return START_STICKY
            }
            ACTION_PIN_BADGE -> {
                val campaign = intent.getStringExtra(EXTRA_CAMPAIGN).orEmpty()
                serviceScope.launch { handlePinBadge(campaign) }
                return START_STICKY
            }
            ACTION_START, null -> {
                startMiningSession()
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        app.minerRepository.appendAppLog(
            LogLevel.WARN,
            "Service",
            "Android dataSync foreground time limit reached; stopping service cleanly"
        )
        shutdownAndStop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMiningSession() {
        val session = app.sessionStore.session.value
        if (session == null) {
            app.minerRepository.appendAppLog(LogLevel.WARN, "Auth", "Cannot start miner: not logged in")
            stopSelf()
            return
        }

        if (processManager?.isRunning == true) {
            promoteToForeground()
            return
        }

        shuttingDown = false
        manualPaused = false
        app.minerRepository.updateRunState(MinerRunState.STARTING)
        promoteToForeground()
        acquireWakeLock()

        notificationObserverJob?.cancel()
        notificationObserverJob = serviceScope.launch {
            app.minerRepository.status.collectLatest { snapshot ->
                notificationManager.updateStatusNotification(snapshot)
                if (snapshot.runState == MinerRunState.STANDBY_NO_BADGES || snapshot.runState.isPaused) {
                    releaseWakeLock()
                } else if (snapshot.runState == MinerRunState.MINING_ACTIVE || snapshot.runState == MinerRunState.SCANNING_BADGES) {
                    acquireWakeLock()
                }
            }
        }

        val bridge = WebViewIntegrityBridge(
            context = applicationContext,
            onLog = { level, cat, msg -> app.minerRepository.appendAppLog(level, cat, msg) },
            onTokenUpdated = { token ->
                app.minerRepository.updateIntegrityStatus(true, token.expirationEpochMs)
            }
        )
        integrityBridge = bridge
        bridge.start(session)

        val httpServer = IntegrityHttpBridgeServer(serviceScope, bridge)
        integrityHttpServer = httpServer
        httpServer.start()

        val pm = MinerProcessManager(
            context = applicationContext,
            scope = serviceScope,
            controlClient = controlClient,
            onLine = { line -> handleGoLogLine(line) },
            onAppLog = { level, cat, msg -> app.minerRepository.appendAppLog(level, cat, msg) },
            onProcessExited = { code -> handleGoProcessExited(code) }
        )
        processManager = pm

        startUsageMonitor()

        serviceScope.launch {
            val result = pm.start(session, httpServer.endpointUrl, httpServer.secret)
            if (result.isSuccess) {
                app.minerRepository.updateRunState(MinerRunState.SCANNING_BADGES)
                startApiSyncLoop()
            } else {
                app.minerRepository.updateRunState(
                    MinerRunState.ERROR,
                    result.exceptionOrNull()?.message ?: "Failed to start miner"
                )
            }
        }
    }

    private fun promoteToForeground() {
        val notification = notificationManager.buildStatusNotification(app.minerRepository.status.value)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, MinerNotificationManager.NOTIFICATION_ID_STATUS, notification, fgsType)
    }

    private fun startUsageMonitor() {
        autoPausePrefObserverJob?.cancel()
        autoPausePrefObserverJob = serviceScope.launch {
            app.sessionStore.autoPauseEnabled.collectLatest { enabled ->
                if (enabled) {
                    val monitor = appUsageMonitor ?: AppUsageMonitor(
                        context = applicationContext,
                        scope = serviceScope,
                        onTwitchForegroundChanged = { inForeground ->
                            twitchInForeground = inForeground
                            app.minerRepository.updateTwitchAppForeground(inForeground)
                            reconcilePauseState()
                        }
                    ).also { appUsageMonitor = it }
                    monitor.start()
                } else {
                    appUsageMonitor?.stop()
                    twitchInForeground = false
                    app.minerRepository.updateTwitchAppForeground(false)
                    reconcilePauseState()
                }
            }
        }
    }

    private suspend fun handleToggleManualPause() {
        manualPaused = !manualPaused
        reconcilePauseState()
    }

    private suspend fun reconcilePauseState() {
        val pm = processManager ?: return
        if (!pm.isRunning) return

        val autoPause = app.sessionStore.autoPauseEnabled.value
        when {
            autoPause && twitchInForeground -> {
                pm.pauseWatch("twitch_app_open")
                app.minerRepository.updateRunState(MinerRunState.PAUSED_TWITCH_OPEN)
            }
            manualPaused -> {
                pm.pauseWatch("manual")
                app.minerRepository.updateRunState(MinerRunState.PAUSED_MANUAL)
            }
            else -> {
                pm.resumeWatch()
                val snap = app.minerRepository.status.value
                val next = when {
                    snap.activeCampaign != null -> MinerRunState.MINING_ACTIVE
                    snap.eligibleCampaignCount == 0 && snap.runState == MinerRunState.STANDBY_NO_BADGES -> MinerRunState.STANDBY_NO_BADGES
                    else -> MinerRunState.SCANNING_BADGES
                }
                app.minerRepository.updateRunState(next)
            }
        }
    }

    private suspend fun handleForceRefreshBadges() {
        val ok = processManager?.forceRefreshBadges() == true
        if (ok) {
            val badges = app.sessionStore.badgesEnabled.value
            val drops = app.sessionStore.dropsEnabled.value
            val msg = when {
                badges && drops -> "Manual refresh triggered (badges & drops)"
                badges -> "Manual refresh triggered (badges)"
                drops -> "Manual refresh triggered (drops)"
                else -> "Manual refresh triggered"
            }
            app.minerRepository.appendAppLog(LogLevel.INFO, "Badge", msg)
        }
    }

    private suspend fun handlePinBadge(campaign: String) {
        val clean = campaign.trim()
        val ok = processManager?.pinBadge(clean) == true
        if (ok) {
            app.minerRepository.updatePinnedCampaign(clean.ifBlank { null })
            val msg = if (clean.isBlank()) "Reset target mode to Auto" else "Pinned badge campaign: $clean"
            app.minerRepository.appendAppLog(LogLevel.EVENT, "Badge", msg)
        }
    }

    private fun startApiSyncLoop() {
        apiPollJob?.cancel()
        apiPollJob = serviceScope.launch {
            while (isActive && !shuttingDown) {
                delay(API_POLL_INTERVAL_MS)
                val pm = processManager ?: continue
                if (!pm.isRunning) continue
                val campaign = controlClient.fetchActiveBadgeCampaign(pm.analyticsPort, pm.apiKey)
                if (campaign != null) {
                    app.minerRepository.updateActiveCampaign(campaign)
                }
            }
        }
    }

    private fun handleGoLogLine(rawLine: String) {
        val parsed = logParser.parseLine(rawLine)
        parsed.logEntry?.let { app.minerRepository.appendParsedEntry(it) }

        when (val event = parsed.event) {
            is ParsedMinerEvent.BadgeDiscovered -> {
                val existing = app.minerRepository.status.value.activeCampaign
                val same = existing != null && existing.badgeName.equals(event.badgeName, ignoreCase = true)
                app.minerRepository.updateActiveCampaign(
                    ActiveBadgeCampaign(
                        badgeName = event.badgeName,
                        campaignName = event.campaignName,
                        channelLogin = event.channelLogin,
                        channelDisplayName = event.channelLogin,
                        category = event.category,
                        badgeImageUrl = event.badgeImageUrl ?: existing?.badgeImageUrl,
                        watchedMinutes = if (same) existing!!.watchedMinutes else 0,
                        requiredMinutes = if (same) existing!!.requiredMinutes else 0,
                        progressPercent = if (same) existing!!.progressPercent else 0
                    )
                )
            }
            is ParsedMinerEvent.BadgeCampaignEligible -> {
                app.minerRepository.recordEligibleBadgeCampaign(
                    EligibleBadgeItem(
                        campaignName = event.campaignName,
                        badgeName = event.badgeName,
                        allTiers = event.allTiers,
                        gameName = event.gameName,
                        badgeImageUrl = event.badgeImageUrl,
                        requiredMinutes = event.requiredMinutes,
                        endsAtIso = event.endsAtIso,
                        isDrop = event.isDrop,
                        isLinked = event.isLinked,
                        accountLinkUrl = event.accountLinkUrl
                    )
                )
            }
            is ParsedMinerEvent.BadgeEvaluated -> {
                app.minerRepository.recordBadgeEvaluation(
                    eligibleCount = event.eligibleCount,
                    activeCount = event.activeCount,
                    ownedCount = event.ownedBadgesCount
                )
                if (event.eligibleCount == 0 && event.activeCount == 0) {
                    serviceScope.launch { processManager?.pauseWatch("standby_no_badges") }
                } else if (!manualPaused && !twitchInForeground) {
                    serviceScope.launch { processManager?.resumeWatch() }
                }
            }
            is ParsedMinerEvent.HeartbeatConfirmed -> {
                app.minerRepository.recordHeartbeat(event.channelLogin)
            }
            is ParsedMinerEvent.DropProgress -> {
                app.minerRepository.updateCampaignProgress(
                    rewardName = event.rewardName,
                    channelLogin = event.channelLogin,
                    watchedMinutes = event.watchedMinutes,
                    requiredMinutes = event.requiredMinutes,
                    percent = event.percent,
                    isClaimable = event.isClaimable,
                    isClaimed = event.isClaimed
                )
            }
            is ParsedMinerEvent.DropClaimed -> {
                app.minerRepository.recordClaimedBadge(event.rewardName, event.category)
                notificationManager.notifyBadgeClaimed(event.rewardName, event.category)
                serviceScope.launch { processManager?.forceRefreshBadges() }
            }
            is ParsedMinerEvent.AuthError -> {
                app.minerRepository.updateRunState(MinerRunState.ERROR, event.message)
            }
            else -> Unit
        }
    }

    private fun handleGoProcessExited(exitCode: Int) {
        if (shuttingDown) return
        app.minerRepository.appendAppLog(
            LogLevel.WARN,
            "Engine",
            "Go miner exited unexpectedly (code=$exitCode); scheduling restart"
        )
        restartJob?.cancel()
        restartJob = serviceScope.launch {
            delay(5_000L)
            if (!shuttingDown && app.sessionStore.session.value != null) {
                startMiningSession()
            }
        }
    }

    private fun shutdownAndStop() {
        shuttingDown = true
        releaseResources()
        app.minerRepository.resetOnStop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        restartJob?.cancel()
        restartJob = null
        apiPollJob?.cancel()
        apiPollJob = null
        autoPausePrefObserverJob?.cancel()
        autoPausePrefObserverJob = null
        notificationObserverJob?.cancel()
        notificationObserverJob = null

        appUsageMonitor?.stop()
        appUsageMonitor = null
        processManager?.stop()
        processManager = null
        integrityHttpServer?.stop()
        integrityHttpServer = null
        integrityBridge?.destroy()
        integrityBridge = null
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lootly::MinerWakeLock").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.lootly.app.action.START"
        const val ACTION_STOP = "com.lootly.app.action.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.lootly.app.action.TOGGLE_PAUSE"
        const val ACTION_FORCE_REFRESH = "com.lootly.app.action.FORCE_REFRESH"
        const val ACTION_PIN_BADGE = "com.lootly.app.action.PIN_BADGE"
        const val EXTRA_CAMPAIGN = "extra_campaign"
        private const val API_POLL_INTERVAL_MS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, MinerForegroundService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MinerForegroundService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }

        fun togglePause(context: Context) {
            val intent = Intent(context, MinerForegroundService::class.java).apply { action = ACTION_TOGGLE_PAUSE }
            runCatching { context.startService(intent) }
        }

        fun forceRefreshBadges(context: Context) {
            val intent = Intent(context, MinerForegroundService::class.java).apply { action = ACTION_FORCE_REFRESH }
            runCatching { context.startService(intent) }
        }

        fun pinBadge(context: Context, campaign: String) {
            val intent = Intent(context, MinerForegroundService::class.java).apply {
                action = ACTION_PIN_BADGE
                putExtra(EXTRA_CAMPAIGN, campaign)
            }
            runCatching { context.startService(intent) }
        }
    }
}
