package com.lootly.app.data.model

data class PermissionHealth(
    val hasUsageAccess: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val hasNotificationPermission: Boolean = true
) {
    val allHealthy: Boolean
        get() = hasUsageAccess && isIgnoringBatteryOptimizations && hasNotificationPermission
}
