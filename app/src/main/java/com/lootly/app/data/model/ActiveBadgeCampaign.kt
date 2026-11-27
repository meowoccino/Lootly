package com.lootly.app.data.model

data class ActiveBadgeCampaign(
    val badgeName: String,
    val campaignName: String,
    val channelLogin: String,
    val channelDisplayName: String = channelLogin,
    val category: String = "",
    val badgeImageUrl: String? = null,
    val watchedMinutes: Int = 0,
    val requiredMinutes: Int = 0,
    val progressPercent: Int = 0,
    val isClaimable: Boolean = false,
    val isClaimed: Boolean = false,
    val lastUpdatedMs: Long = System.currentTimeMillis()
) {
    val computedPercent: Int
        get() = when {
            progressPercent in 1..100 -> progressPercent
            requiredMinutes > 0 -> ((watchedMinutes * 100) / requiredMinutes).coerceIn(0, 100)
            else -> 0
        }

    val remainingMinutes: Int
        get() {
            if (isClaimed || isClaimable || computedPercent >= 100) return 0
            if (requiredMinutes > 0 && watchedMinutes in 1 until requiredMinutes) {
                return (requiredMinutes - watchedMinutes).coerceAtLeast(1)
            }
            if (requiredMinutes > 0 && computedPercent in 1..99) {
                return (((requiredMinutes * (100 - computedPercent)) + 99) / 100).coerceAtLeast(1)
            }
            return if (requiredMinutes > 0) requiredMinutes else 0
        }

    val etaLabel: String?
        get() {
            val rem = remainingMinutes
            if (rem <= 0) return null
            if (rem < 60) return "~${rem}m left"
            val hours = rem / 60
            val mins = rem % 60
            return if (mins > 0) "~${hours}h ${mins}m left" else "~${hours}h left"
        }

    val progressSummary: String
        get() = "$computedPercent%"

    val progressWithEtaSummary: String
        get() = etaLabel?.let { "$computedPercent% • $it" } ?: "$computedPercent%"
}
