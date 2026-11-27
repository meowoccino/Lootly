package com.lootly.app.data.state

import com.lootly.app.data.model.ActiveBadgeCampaign
import com.lootly.app.data.model.ClaimedBadgeEvent
import com.lootly.app.data.model.EligibleBadgeItem
import com.lootly.app.data.model.LogEntry
import com.lootly.app.data.model.LogLevel
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.data.model.MinerStatusSnapshot
import com.lootly.app.engine.CircularLogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MinerRepository(
    val logBuffer: CircularLogBuffer = CircularLogBuffer(1000)
) {
    private val _status = MutableStateFlow(MinerStatusSnapshot())
    val status: StateFlow<MinerStatusSnapshot> = _status.asStateFlow()

    val logs: StateFlow<List<LogEntry>> = logBuffer.entries

    private val pendingEligibleBadges = mutableListOf<EligibleBadgeItem>()

    fun appendAppLog(level: LogLevel, category: String, message: String) {
        logBuffer.appendFormatted(level, category, message)
    }

    fun appendParsedEntry(entry: LogEntry) {
        logBuffer.append(entry)
    }

    fun clearLogs() {
        logBuffer.clear()
    }

    fun updateRunState(state: MinerRunState, error: String? = null) {
        _status.update { current ->
            current.copy(
                runState = state,
                lastError = error ?: if (state != MinerRunState.ERROR) null else current.lastError
            )
        }
    }

    fun updateActiveCampaign(campaign: ActiveBadgeCampaign?) {
        _status.update { current ->
            val nextState = if (current.runState in setOf(
                    MinerRunState.PAUSED_TWITCH_OPEN,
                    MinerRunState.PAUSED_MANUAL,
                    MinerRunState.STOPPED,
                    MinerRunState.ERROR
                )
            ) {
                current.runState
            } else if (campaign != null) {
                MinerRunState.MINING_ACTIVE
            } else {
                MinerRunState.SCANNING_BADGES
            }
            current.copy(runState = nextState, activeCampaign = campaign)
        }
    }

    fun updateCampaignProgress(
        rewardName: String?,
        channelLogin: String?,
        watchedMinutes: Int,
        requiredMinutes: Int,
        percent: Int,
        isClaimable: Boolean,
        isClaimed: Boolean
    ) {
        val cleanReward = rewardName?.trim().orEmpty()
        _status.update { current ->
            val active = current.activeCampaign
            if (cleanReward.isEmpty() && active == null) return@update current

            val updatedCampaign = if (active != null) {
                val matchesActive = cleanReward.isEmpty() ||
                    active.badgeName.isBlank() ||
                    active.badgeName.equals("Watch-Time Badge", ignoreCase = true) ||
                    active.badgeName.equals(cleanReward, ignoreCase = true) ||
                    active.badgeName.contains(cleanReward, ignoreCase = true) ||
                    cleanReward.contains(active.badgeName, ignoreCase = true)

                val canSwitchAfterComplete = !matchesActive && (active.progressPercent >= 100 || active.isClaimed)
                if (!matchesActive && !canSwitchAfterComplete) {
                    return@update current
                }

                if (canSwitchAfterComplete) {
                    val matchedEligible = current.eligibleBadges.firstOrNull { item ->
                        item.badgeName.equals(cleanReward, ignoreCase = true) ||
                            item.badgeName.contains(cleanReward, ignoreCase = true) ||
                            cleanReward.contains(item.badgeName, ignoreCase = true)
                    }
                    active.copy(
                        badgeName = cleanReward,
                        campaignName = matchedEligible?.campaignName ?: active.campaignName,
                        channelLogin = channelLogin?.takeIf { it.isNotBlank() } ?: active.channelLogin,
                        category = matchedEligible?.gameName?.takeIf { it.isNotBlank() } ?: active.category,
                        badgeImageUrl = matchedEligible?.badgeImageUrl ?: active.badgeImageUrl,
                        watchedMinutes = watchedMinutes,
                        requiredMinutes = when {
                            requiredMinutes > 0 -> requiredMinutes
                            matchedEligible != null -> matchedEligible.requiredMinutes
                            else -> active.requiredMinutes
                        },
                        progressPercent = percent.coerceIn(0, 100),
                        isClaimable = isClaimable,
                        isClaimed = isClaimed,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                } else {
                    active.copy(
                        badgeName = active.badgeName.ifBlank { cleanReward },
                        channelLogin = channelLogin?.takeIf { it.isNotBlank() } ?: active.channelLogin,
                        watchedMinutes = maxOf(watchedMinutes, active.watchedMinutes),
                        requiredMinutes = if (requiredMinutes > 0) requiredMinutes else active.requiredMinutes,
                        progressPercent = maxOf(percent.coerceIn(0, 100), active.progressPercent),
                        isClaimable = isClaimable || active.isClaimable,
                        isClaimed = isClaimed || active.isClaimed,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                }
            } else {
                ActiveBadgeCampaign(
                    badgeName = cleanReward,
                    campaignName = cleanReward,
                    channelLogin = channelLogin.orEmpty(),
                    watchedMinutes = watchedMinutes,
                    requiredMinutes = requiredMinutes,
                    progressPercent = percent.coerceIn(0, 100),
                    isClaimable = isClaimable,
                    isClaimed = isClaimed
                )
            }
            current.copy(activeCampaign = updatedCampaign)
        }
    }

    @Synchronized
    fun recordEligibleBadgeCampaign(item: EligibleBadgeItem) {
        pendingEligibleBadges.removeAll {
            it.badgeName.equals(item.badgeName, ignoreCase = true) &&
                it.campaignName.equals(item.campaignName, ignoreCase = true)
        }
        pendingEligibleBadges.add(item)
        val snapshotList = pendingEligibleBadges.toList()
        _status.update { current ->
            val active = current.activeCampaign
            val updatedActive = if (active != null && active.badgeName.equals(item.badgeName, ignoreCase = true)) {
                active.copy(
                    badgeImageUrl = active.badgeImageUrl ?: item.badgeImageUrl,
                    requiredMinutes = item.requiredMinutes
                )
            } else {
                active
            }
            current.copy(activeCampaign = updatedActive, eligibleBadges = snapshotList)
        }
    }

    @Synchronized
    fun recordBadgeEvaluation(eligibleCount: Int, activeCount: Int, ownedCount: Int) {
        val list = if (eligibleCount == 0) {
            pendingEligibleBadges.clear()
            emptyList()
        } else {
            val copy = pendingEligibleBadges.toList()
            pendingEligibleBadges.clear()
            copy
        }
        _status.update { current ->
            val shouldClearActive = (activeCount == 0 && eligibleCount == 0) ||
                (activeCount == 0 && current.activeCampaign != null &&
                    (current.activeCampaign.computedPercent >= 100 || current.activeCampaign.isClaimed))

            val updatedClaimed = if (current.ownedBadgeCount > 0 && ownedCount > current.ownedBadgeCount && current.activeCampaign != null) {
                val badgeName = current.activeCampaign.badgeName
                val alreadyRecorded = current.claimedBadges.any {
                    it.badgeName.equals(badgeName, ignoreCase = true) &&
                        System.currentTimeMillis() - it.timestampMs < 120_000L
                }
                if (alreadyRecorded) {
                    current.claimedBadges
                } else {
                    (listOf(ClaimedBadgeEvent(badgeName, current.activeCampaign.category)) + current.claimedBadges).take(50)
                }
            } else {
                current.claimedBadges
            }

            val nextActive = if (shouldClearActive) null else current.activeCampaign
            val nextState = when {
                current.runState in setOf(
                    MinerRunState.PAUSED_TWITCH_OPEN,
                    MinerRunState.PAUSED_MANUAL,
                    MinerRunState.STOPPED,
                    MinerRunState.ERROR
                ) -> current.runState
                activeCount == 0 && eligibleCount == 0 -> MinerRunState.STANDBY_NO_BADGES
                activeCount > 0 || nextActive != null -> MinerRunState.MINING_ACTIVE
                else -> MinerRunState.SCANNING_BADGES
            }

            current.copy(
                runState = nextState,
                activeCampaign = nextActive,
                eligibleBadges = if (list.isNotEmpty() || eligibleCount == 0) list else current.eligibleBadges,
                claimedBadges = updatedClaimed,
                eligibleCampaignCount = eligibleCount,
                ownedBadgeCount = ownedCount
            )
        }
    }

    @Synchronized
    fun recordClaimedBadge(badgeName: String, category: String) {
        val clean = badgeName.trim()
        pendingEligibleBadges.removeAll {
            it.badgeName.equals(clean, ignoreCase = true) ||
                it.badgeName.contains(clean, ignoreCase = true) ||
                clean.contains(it.badgeName, ignoreCase = true)
        }
        _status.update { current ->
            val recentlyClaimed = current.claimedBadges.any {
                (it.badgeName.equals(clean, ignoreCase = true) ||
                    it.badgeName.contains(clean, ignoreCase = true) ||
                    clean.contains(it.badgeName, ignoreCase = true)) &&
                    System.currentTimeMillis() - it.timestampMs < 60_000L
            }
            if (recentlyClaimed) return@update current

            val nextClaimed = (listOf(ClaimedBadgeEvent(clean, category)) + current.claimedBadges).take(50)
            val remainingEligible = current.eligibleBadges.filterNot {
                it.badgeName.equals(clean, ignoreCase = true) ||
                    it.badgeName.contains(clean, ignoreCase = true) ||
                    clean.contains(it.badgeName, ignoreCase = true)
            }
            val active = current.activeCampaign
            val activeMatchesClaimed = active == null ||
                active.badgeName.equals(clean, ignoreCase = true) ||
                active.badgeName.contains(clean, ignoreCase = true) ||
                clean.contains(active.badgeName, ignoreCase = true) ||
                active.isClaimed ||
                active.progressPercent >= 100

            val nextActive = if (activeMatchesClaimed) {
                remainingEligible.firstOrNull()?.let { next ->
                    ActiveBadgeCampaign(
                        badgeName = next.badgeName,
                        campaignName = next.campaignName,
                        channelLogin = active?.channelLogin.orEmpty(),
                        category = next.gameName.ifBlank { active?.category.orEmpty() },
                        badgeImageUrl = next.badgeImageUrl,
                        requiredMinutes = next.requiredMinutes
                    )
                }
            } else {
                active
            }

            current.copy(
                activeCampaign = nextActive,
                eligibleBadges = remainingEligible,
                claimedBadges = nextClaimed,
                eligibleCampaignCount = maxOf(remainingEligible.size, (current.eligibleCampaignCount - 1).coerceAtLeast(0)),
                ownedBadgeCount = current.ownedBadgeCount + 1
            )
        }
    }

    fun recordHeartbeat(channelLogin: String?) {
        _status.update { current ->
            val count = current.heartbeatCount + 1
            val active = current.activeCampaign
            val updatedActive = active?.let { c ->
                val login = if (!channelLogin.isNullOrBlank() && c.channelLogin.isBlank()) channelLogin else c.channelLogin
                if (count % 3 == 0 && c.requiredMinutes > 0 && c.progressPercent < 100 && !c.isClaimed && !c.isClaimable) {
                    val watched = (c.watchedMinutes + 1).coerceAtMost(c.requiredMinutes)
                    val pct = maxOf(((watched * 100) / c.requiredMinutes).coerceIn(0, 100), c.progressPercent)
                    c.copy(
                        channelLogin = login,
                        watchedMinutes = watched,
                        progressPercent = pct,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                } else {
                    c.copy(channelLogin = login, lastUpdatedMs = System.currentTimeMillis())
                }
            }
            current.copy(
                activeCampaign = updatedActive,
                heartbeatCount = count,
                lastHeartbeatAtMs = System.currentTimeMillis()
            )
        }
    }

    fun updateIntegrityStatus(ready: Boolean, expiresAtMs: Long?) {
        _status.update { it.copy(integrityBridgeReady = ready, integrityExpiresAtMs = expiresAtMs) }
    }

    fun updateTwitchAppForeground(inForeground: Boolean) {
        _status.update { it.copy(twitchAppInForeground = inForeground) }
    }

    fun updatePinnedCampaign(campaign: String?) {
        val clean = campaign?.trim()?.takeIf { it.isNotBlank() }
        _status.update { it.copy(pinnedCampaign = clean) }
    }

    @Synchronized
    fun resetOnStop() {
        pendingEligibleBadges.clear()
        _status.update { current ->
            current.copy(
                runState = MinerRunState.STOPPED,
                activeCampaign = null,
                eligibleBadges = emptyList(),
                eligibleCampaignCount = 0,
                heartbeatCount = 0,
                lastHeartbeatAtMs = null,
                integrityBridgeReady = false,
                integrityExpiresAtMs = null,
                pinnedCampaign = null
            )
        }
    }
}
