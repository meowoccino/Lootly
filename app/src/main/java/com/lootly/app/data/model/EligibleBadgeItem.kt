package com.lootly.app.data.model

data class EligibleBadgeItem(
    val campaignName: String,
    val badgeName: String,
    val allTiers: String,
    val gameName: String,
    val badgeImageUrl: String? = null,
    val requiredMinutes: Int = 30,
    val endsAtIso: String = "",
    val isDrop: Boolean = false,
    val isLinked: Boolean = true,
    val accountLinkUrl: String = ""
)
