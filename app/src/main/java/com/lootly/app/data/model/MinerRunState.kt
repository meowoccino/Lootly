package com.lootly.app.data.model

enum class MinerRunState(val label: String) {
    STOPPED("Stopped"),
    STARTING("Starting Engine"),
    SCANNING_BADGES("Scanning for New Badges"),
    MINING_ACTIVE("Mining Active"),
    STANDBY_NO_BADGES("Standby — All Badges Earned"),
    PAUSED_TWITCH_OPEN("Paused — Twitch App Open"),
    PAUSED_MANUAL("Paused — Manual"),
    ERROR("Error");

    val isPaused: Boolean
        get() = this == PAUSED_TWITCH_OPEN || this == PAUSED_MANUAL

    val isServiceActive: Boolean
        get() = this != STOPPED && this != ERROR
}
