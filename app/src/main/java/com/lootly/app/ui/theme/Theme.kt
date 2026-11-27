package com.lootly.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MinerDarkColorScheme = darkColorScheme(
    primary = MinerColors.TwitchViolet,
    onPrimary = MinerColors.TextPrimary,
    secondary = MinerColors.LiveMint,
    onSecondary = MinerColors.Ground,
    background = MinerColors.Ground,
    onBackground = MinerColors.TextPrimary,
    surface = MinerColors.Panel,
    onSurface = MinerColors.TextPrimary,
    surfaceVariant = MinerColors.PanelRaised,
    onSurfaceVariant = MinerColors.TextSecondary,
    outline = MinerColors.Hairline,
    error = MinerColors.DangerRed,
    onError = MinerColors.TextPrimary
)

@Composable
fun TwitchBadgeMinerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MinerDarkColorScheme,
        typography = MinerTypography,
        content = content
    )
}
