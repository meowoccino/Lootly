package com.lootly.app.engine

sealed class ParsedMinerEvent {
    data object None : ParsedMinerEvent()

    data class BadgeDiscovered(
        val badgeName: String,
        val campaignName: String,
        val channelLogin: String,
        val category: String,
        val badgeImageUrl: String?
    ) : ParsedMinerEvent()

    data class BadgeCampaignEligible(
        val campaignName: String,
        val badgeName: String,
        val allTiers: String,
        val gameName: String,
        val badgeImageUrl: String?,
        val requiredMinutes: Int,
        val endsAtIso: String,
        val isDrop: Boolean = false,
        val isLinked: Boolean = true,
        val accountLinkUrl: String = ""
    ) : ParsedMinerEvent()

    data class BadgeEvaluated(
        val catalogCount: Int,
        val eligibleCount: Int,
        val activeCount: Int,
        val ownedBadgesCount: Int
    ) : ParsedMinerEvent()

    data class HeartbeatConfirmed(val channelLogin: String) : ParsedMinerEvent()
    data class WatchingChannel(val channelLogin: String) : ParsedMinerEvent()
    data class StoppedWatchingChannel(val channelLogin: String) : ParsedMinerEvent()

    data class DropProgress(
        val rewardName: String,
        val channelLogin: String?,
        val watchedMinutes: Int,
        val requiredMinutes: Int,
        val percent: Int,
        val isClaimable: Boolean,
        val isClaimed: Boolean
    ) : ParsedMinerEvent()

    data class DropClaimed(
        val rewardName: String,
        val category: String
    ) : ParsedMinerEvent()

    data class WatchPaused(val reason: String) : ParsedMinerEvent()
    data object WatchResumed : ParsedMinerEvent()
    data class AuthError(val message: String) : ParsedMinerEvent()
}
