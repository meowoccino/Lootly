package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lootly.app.data.model.PermissionHealth
import com.lootly.app.ui.theme.MinerColors

@Composable
fun PermissionHealthBar(
    health: PermissionHealth,
    onOpenUsageSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (health.allHealthy) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MinerColors.WarningAmberSoft,
        border = BorderStroke(1.dp, MinerColors.WarningAmber.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Recommended Permissions",
                style = MaterialTheme.typography.labelMedium,
                color = MinerColors.WarningAmber,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!health.hasUsageAccess) {
                    PermissionPill("Usage Access", onOpenUsageSettings)
                }
                if (!health.isIgnoringBatteryOptimizations) {
                    PermissionPill("Battery", onRequestBatteryExemption)
                }
                if (!health.hasNotificationPermission) {
                    PermissionPill("Notifications", onRequestNotifications)
                }
            }
        }
    }
}

@Composable
private fun PermissionPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.WarningAmber.copy(alpha = 0.5f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MinerColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
