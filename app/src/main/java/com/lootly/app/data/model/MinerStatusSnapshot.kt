package com.lootly.app.data.model

data class MinerStatusSnapshot(
    val runState: MinerRunState = MinerRunState.STOPPED,
    val activeCampaign: ActiveBadgeCampaign? = null,
    val eligibleBadges: List<EligibleBadgeItem> = emptyList(),
    val claimedBadges: List<ClaimedBadgeEvent> = emptyList(),
    val eligibleCampaignCount: Int = 0,
    val ownedBadgeCount: Int = 0,
    val heartbeatCount: Int = 0,
    val lastHeartbeatAtMs: Long? = null,
    val integrityBridgeReady: Boolean = false,
    val integrityExpiresAtMs: Long? = null,
    val twitchAppInForeground: Boolean = false,
    val pinnedCampaign: String? = null,
    val lastError: String? = null
)
