package com.lootly.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.lootly.app.ui.components.AccountStatusCard
import com.lootly.app.ui.components.ActiveBadgeCard
import com.lootly.app.ui.components.AutoPauseCard
import com.lootly.app.ui.components.LiveMinerConsoleCard
import com.lootly.app.ui.components.PermissionHealthBar
import com.lootly.app.ui.components.UsageAccessOnboardingCard
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
    val haptics = LocalHapticFeedback.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MinerColors.Ground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountStatusCard(
                session = uiState.session,
                runState = uiState.minerStatus.runState,
                onLoginClick = onLoginClick,
                onLogoutClick = { showLogoutDialog = true },
                onMasterToggle = { enabled ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMasterToggle(enabled)
                }
            )

            if (!uiState.permissionHealth.allHealthy) {
                PermissionHealthBar(
                    health = uiState.permissionHealth,
                    onRequestUsageAccess = onOpenUsageSettings,
                    onRequestBatteryUnrestricted = onRequestBatteryExemption,
                    onRequestNotifications = onRequestNotifications
                )
            }

            if (!uiState.permissionHealth.hasUsageAccess && !uiState.usageCardDismissed) {
                UsageAccessOnboardingCard(
                    onGrantClick = onOpenUsageSettings,
                    onDismiss = onDismissUsageOnboarding
                )
            }

            ActiveBadgeCard(
                snapshot = uiState.minerStatus,
                onForceRefreshBadges = onForceRefreshBadges,
                onTogglePause = onTogglePause,
                onPinBadge = onPinBadge
            )

            AutoPauseCard(
                enabled = uiState.autoPauseEnabled,
                hasUsagePermission = uiState.permissionHealth.hasUsageAccess,
                twitchAppOpen = uiState.minerStatus.twitchAppInForeground,
                onToggle = onAutoPauseToggle
            )

            LiveMinerConsoleCard(
                logs = uiState.logs,
                onClearLogs = onClearLogs
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MinerColors.PanelRaised,
            titleContentColor = MinerColors.TextPrimary,
            textContentColor = MinerColors.TextSecondary,
            title = { Text("Log Out or Re-authenticate") },
            text = {
                Text("This stops the background miner and cleanly clears all WebView cookies and EncryptedSharedPreferences tokens.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onSwitchAccountClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinerColors.TwitchViolet,
                        contentColor = MinerColors.TextPrimary
                    )
                ) {
                    Text("Re-authenticate")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = MinerColors.TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            onLogoutClick()
                        }
                    ) {
                        Text("Log Out", color = MinerColors.DangerRed)
                    }
                }
            }
        )
    }
}
