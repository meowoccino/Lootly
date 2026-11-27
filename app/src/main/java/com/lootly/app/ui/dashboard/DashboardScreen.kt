package com.lootly.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lootly.app.ui.components.AccountStatusCard
import com.lootly.app.ui.components.ActiveBadgeCard
import com.lootly.app.ui.components.AutoPauseCard
import com.lootly.app.ui.components.LiveMinerConsoleCard
import com.lootly.app.ui.components.PermissionHealthBar
import com.lootly.app.ui.theme.MinerColors

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchAccountClick: () -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onForceRefreshBadges: () -> Unit,
    onPinBadge: (String?) -> Unit,
    onAutoPauseToggle: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestNotifications: () -> Unit,
    onDismissUsageOnboarding: () -> Unit,
    onClearLogs: () -> Unit
) {
    Scaffold(
        containerColor = MinerColors.Ground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MinerColors.Ground)
                .statusBarsPadding()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Lootly",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MinerColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.minerStatus.runState.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.minerStatus.runState.isServiceActive) {
                            MinerColors.LiveMint
                        } else {
                            MinerColors.TextSecondary
                        }
                    )
                }

                if (uiState.session != null) {
                    Switch(
                        checked = uiState.minerStatus.runState.isServiceActive,
                        onCheckedChange = onMasterToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MinerColors.TextPrimary,
                            checkedTrackColor = MinerColors.TwitchViolet
                        )
                    )
                }
            }

            AccountStatusCard(
                session = uiState.session,
                onLoginClick = onLoginClick,
                onLogoutClick = onLogoutClick
            )

            PermissionHealthBar(
                health = uiState.permissionHealth,
                onOpenUsageSettings = onOpenUsageSettings,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onRequestNotifications = onRequestNotifications
            )

            ActiveBadgeCard(
                status = uiState.minerStatus,
                onTogglePause = onTogglePause,
                onForceRefreshBadges = onForceRefreshBadges,
                onPinBadge = onPinBadge
            )

            AutoPauseCard(
                enabled = uiState.autoPauseEnabled,
                twitchInForeground = uiState.minerStatus.twitchAppInForeground,
                onToggle = onAutoPauseToggle
            )

            LiveMinerConsoleCard(
                logs = uiState.logs,
                onClearLogs = onClearLogs
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
