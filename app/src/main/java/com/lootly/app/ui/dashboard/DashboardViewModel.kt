package com.lootly.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lootly.app.TwitchMinerApp
import com.lootly.app.data.model.LogEntry
import com.lootly.app.data.model.LogLevel
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.data.model.MinerStatusSnapshot
import com.lootly.app.data.model.PermissionHealth
import com.lootly.app.data.model.TwitchSession
import com.lootly.app.monitor.PermissionHelper
import com.lootly.app.service.MinerForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val session: TwitchSession? = null,
    val minerStatus: MinerStatusSnapshot = MinerStatusSnapshot(),
    val logs: List<LogEntry> = emptyList(),
    val permissionHealth: PermissionHealth = PermissionHealth(),
    val autoPauseEnabled: Boolean = true,
    val usageCardDismissed: Boolean = false,
    val badgesEnabled: Boolean = true,
    val dropsEnabled: Boolean = true
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TwitchMinerApp
    private val sessionStore = app.sessionStore
    private val accountRepository = app.accountRepository
    private val minerRepository = app.minerRepository

    private val _permissionHealth = MutableStateFlow(PermissionHelper.getPermissionHealth(application))

    val uiState: StateFlow<DashboardUiState> = combine(
        sessionStore.session,
        minerRepository.status,
        minerRepository.logs,
        _permissionHealth,
        sessionStore.autoPauseEnabled,
        sessionStore.usageCardDismissed,
        sessionStore.badgesEnabled,
        sessionStore.dropsEnabled
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DashboardUiState(
            session = values[0] as TwitchSession?,
            minerStatus = values[1] as MinerStatusSnapshot,
            logs = values[2] as List<LogEntry>,
            permissionHealth = values[3] as PermissionHealth,
            autoPauseEnabled = values[4] as Boolean,
            usageCardDismissed = values[5] as Boolean,
            badgesEnabled = values[6] as Boolean,
            dropsEnabled = values[7] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(
            session = sessionStore.session.value,
            minerStatus = minerRepository.status.value,
            logs = minerRepository.logs.value,
            permissionHealth = _permissionHealth.value,
            autoPauseEnabled = sessionStore.autoPauseEnabled.value,
            usageCardDismissed = sessionStore.usageCardDismissed.value,
            badgesEnabled = sessionStore.badgesEnabled.value,
            dropsEnabled = sessionStore.dropsEnabled.value
        )
    )

    init {
        refreshStoredAccountProfile()
        if (sessionStore.session.value != null && minerRepository.status.value.runState == MinerRunState.STOPPED) {
            MinerForegroundService.start(getApplication())
        }
    }

    private fun refreshStoredAccountProfile() {
        val current = sessionStore.session.value ?: return
        viewModelScope.launch {
            val profile = accountRepository.refreshProfile(current.authToken, current.deviceId)
            if (profile != null) {
                sessionStore.updateProfile(profile.displayName, profile.avatarUrl)
            }
        }
    }

    fun refreshPermissions() {
        _permissionHealth.value = PermissionHelper.getPermissionHealth(getApplication())
    }

    fun onLoginSucceeded(session: TwitchSession) {
        sessionStore.saveSession(session)
        minerRepository.appendAppLog(
            LogLevel.EVENT,
            "Auth",
            "Authenticated as ${session.effectiveDisplayName} (@${session.login}); credentials encrypted"
        )
        MinerForegroundService.start(getApplication())
    }

    fun onLogout() {
        MinerForegroundService.stop(getApplication())
        sessionStore.clearSessionAndCookies()
        minerRepository.resetOnStop()
        minerRepository.appendAppLog(
            LogLevel.INFO,
            "Auth",
            "Logged out — cleared CookieManager and EncryptedSharedPreferences"
        )
    }

    fun onMasterToggle(enabled: Boolean) {
        if (!enabled) {
            MinerForegroundService.stop(getApplication())
        } else if (sessionStore.session.value != null) {
            MinerForegroundService.start(getApplication())
        }
    }

    fun onTogglePause() {
        MinerForegroundService.togglePause(getApplication())
    }

    fun onForceRefreshBadges() {
        MinerForegroundService.forceRefreshBadges(getApplication())
    }

    fun onPinBadge(campaign: String?) {
        val clean = campaign?.trim()?.takeIf { it.isNotEmpty() }
        minerRepository.updatePinnedCampaign(clean)
        MinerForegroundService.pinBadge(getApplication(), clean.orEmpty())
    }

    fun onAutoPauseToggle(enabled: Boolean) {
        sessionStore.setAutoPauseEnabled(enabled)
        minerRepository.appendAppLog(
            LogLevel.INFO,
            "Auto-Pause",
            if (enabled) "Auto-Pause on Twitch app enabled" else "Auto-Pause on Twitch app disabled"
        )
    }

    fun onBadgesToggle(enabled: Boolean) {
        sessionStore.setBadgesEnabled(enabled)
        MinerForegroundService.setFilters(getApplication(), enabled, sessionStore.dropsEnabled.value)
    }

    fun onDropsToggle(enabled: Boolean) {
        sessionStore.setDropsEnabled(enabled)
        MinerForegroundService.setFilters(getApplication(), sessionStore.badgesEnabled.value, enabled)
    }

    fun onDismissUsageOnboarding() {
        sessionStore.setUsageCardDismissed(true)
    }

    fun onClearLogs() {
        minerRepository.clearLogs()
    }
}
