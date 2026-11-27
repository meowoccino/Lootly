package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lootly.app.data.model.PermissionHealth
import com.lootly.app.ui.theme.MinerColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionHealthBar(
    health: PermissionHealth,
    onRequestUsageAccess: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!health.hasUsageAccess) {
            HealthStatusPill(
                label = "Enable Usage Access",
                granted = false,
                leadingIcon = Icons.Rounded.Visibility,
                onClick = onRequestUsageAccess
            )
        }
        if (!health.isIgnoringBatteryOptimizations) {
            HealthStatusPill(
                label = "Fix Battery Optimization",
                granted = false,
                leadingIcon = Icons.Rounded.BatteryChargingFull,
                onClick = onRequestBatteryUnrestricted
            )
        }
        if (!health.hasNotificationPermission) {
            HealthStatusPill(
                label = "Allow Notifications",
                granted = false,
                leadingIcon = Icons.Rounded.NotificationsActive,
                onClick = onRequestNotifications
            )
        }
    }
}

@Composable
private fun HealthStatusPill(
    label: String,
    granted: Boolean,
    leadingIcon: ImageVector,
    onClick: () -> Unit
) {
    val bgColor = if (granted) MinerColors.LiveMintSoft else MinerColors.WarningAmberSoft
    val fgColor = if (granted) MinerColors.LiveMint else MinerColors.WarningAmber

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = fgColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = fgColor.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = fgColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
