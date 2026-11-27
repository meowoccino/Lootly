package com.lootly.app.data.model

data class ClaimedBadgeEvent(
    val badgeName: String,
    val category: String,
    val timestampMs: Long = System.currentTimeMillis()
)
