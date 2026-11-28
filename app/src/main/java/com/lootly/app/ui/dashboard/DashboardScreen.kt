package com.lootly.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lootly.app.ui.components.ActiveBadgeCard
import com.lootly.app.ui.components.AutoPauseCard
import com.lootly.app.ui.components.BadgesDropsCard
import com.lootly.app.ui.components.LiveMinerConsoleCard
import com.lootly.app.ui.components.PermissionHealthBar
import com.lootly.app.ui.theme.MinerColors
import java.util.Locale

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
    onBadgesToggle: (Boolean) -> Unit,
    onDropsToggle: (Boolean) -> Unit,
    onAutoPauseToggle: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestNotifications: () -> Unit,
    onDismissUsageOnboarding: () -> Unit,
    onClearLogs: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEligibleScreen by remember { mutableStateOf(false) }
    val session = uiState.session

    Scaffold(
        containerColor = MinerColors.Ground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Top Bar #4: Circular avatar + username on left; Master Switch on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (session != null) {
                                showLogoutDialog = true
                            } else {
                                onLoginClick()
                            }
                        }
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MinerColors.PanelRaised)
                            .border(
                                width = 2.dp,
                                color = if (session != null) MinerColors.TwitchViolet else MinerColors.HairlineBright,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUrl = session?.avatarUrl
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = session.effectiveDisplayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else if (session != null) {
                            Text(
                                text = session.effectiveDisplayName.take(1).uppercase(Locale.ROOT),
                                color = MinerColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Sign In",
                                modifier = Modifier.size(18.dp),
                                tint = MinerColors.TextSecondary
                            )
                        }
                    }

                    Text(
                        text = session?.effectiveDisplayName ?: "Sign In",
                        color = MinerColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }

                Switch(
                    checked = uiState.minerStatus.runState.isServiceActive,
                    onCheckedChange = { enabled ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (session == null && enabled) {
                            onLoginClick()
                        } else {
                            onMasterToggle(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MinerColors.Ground,
                        checkedTrackColor = MinerColors.TwitchViolet,
                        uncheckedThumbColor = MinerColors.TextSecondary,
                        uncheckedTrackColor = MinerColors.Hairline,
                        uncheckedBorderColor = MinerColors.Hairline
                    )
                )
            }

            if (!uiState.permissionHealth.allHealthy) {
                PermissionHealthBar(
                    health = uiState.permissionHealth,
                    onRequestUsageAccess = onOpenUsageSettings,
                    onRequestBatteryUnrestricted = onRequestBatteryExemption,
                    onRequestNotifications = onRequestNotifications
                )
            }

            ActiveBadgeCard(
                snapshot = uiState.minerStatus,
                onForceRefreshBadges = onForceRefreshBadges,
                onTogglePause = onTogglePause,
                onPinBadge = onPinBadge,
                onOpenEligible = { showEligibleScreen = true },
                badgesEnabled = uiState.badgesEnabled,
                dropsEnabled = uiState.dropsEnabled
            )

            BadgesDropsCard(
                badgesEnabled = uiState.badgesEnabled,
                dropsEnabled = uiState.dropsEnabled,
                autoPauseEnabled = uiState.autoPauseEnabled,
                onBadgesToggle = onBadgesToggle,
                onDropsToggle = onDropsToggle,
                onAutoPauseToggle = onAutoPauseToggle
            )

            LiveMinerConsoleCard(
                logs = uiState.logs,
                onClearLogs = onClearLogs,
                modifier = Modifier.weight(1f)
            )
        }

        if (showEligibleScreen) {
            EligibleListScreen(
                snapshot = uiState.minerStatus,
                badgesEnabled = uiState.badgesEnabled,
                dropsEnabled = uiState.dropsEnabled,
                onBack = { showEligibleScreen = false },
                onPinBadge = onPinBadge,
                onForceRefreshBadges = onForceRefreshBadges,
                modifier = Modifier.padding(innerPadding)
            )
        }
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
