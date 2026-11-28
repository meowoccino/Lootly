package com.lootly.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lootly.app.monitor.PermissionHelper
import com.lootly.app.ui.dashboard.DashboardScreen
import com.lootly.app.ui.dashboard.DashboardViewModel
import com.lootly.app.ui.login.LoginActivity
import com.lootly.app.ui.theme.TwitchBadgeMinerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            TwitchBadgeMinerTheme {
                val viewModel: DashboardViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val loginLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { }

                val openLogin = {
                    loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                }

                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.refreshPermissions()
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, viewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshPermissions()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                DashboardScreen(
                    uiState = uiState,
                    onLoginClick = openLogin,
                    onLogoutClick = { viewModel.onLogout() },
                    onSwitchAccountClick = {
                        viewModel.onLogout()
                        openLogin()
                    },
                    onMasterToggle = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !PermissionHelper.hasNotificationPermission(this@MainActivity)
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.onMasterToggle(enabled)
                    },
                    onTogglePause = { viewModel.onTogglePause() },
                    onForceRefreshBadges = { viewModel.onForceRefreshBadges() },
                    onPinBadge = { campaign -> viewModel.onPinBadge(campaign) },
                    onBadgesToggle = { enabled -> viewModel.onBadgesToggle(enabled) },
                    onDropsToggle = { enabled -> viewModel.onDropsToggle(enabled) },
                    onAutoPauseToggle = { enabled -> viewModel.onAutoPauseToggle(enabled) },
                    onOpenUsageSettings = { PermissionHelper.openUsageAccessSettings(this@MainActivity) },
                    onRequestBatteryExemption = { PermissionHelper.requestIgnoreBatteryOptimizations(this@MainActivity) },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDismissUsageOnboarding = { viewModel.onDismissUsageOnboarding() },
                    onClearLogs = { viewModel.onClearLogs() }
                )
            }
        }
    }
}
