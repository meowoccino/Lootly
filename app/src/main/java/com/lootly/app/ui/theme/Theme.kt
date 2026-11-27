package com.lootly.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MinerDarkColorScheme = darkColorScheme(
    primary = MinerColors.TwitchViolet,
    onPrimary = MinerColors.TextPrimary,
    primaryContainer = MinerColors.TwitchVioletSoft,
    onPrimaryContainer = MinerColors.TwitchVioletGlow,
    secondary = MinerColors.LiveMint,
    onSecondary = MinerColors.Ground,
    secondaryContainer = MinerColors.LiveMintSoft,
    onSecondaryContainer = MinerColors.LiveMint,
    tertiary = MinerColors.WarningAmber,
    onTertiary = MinerColors.Ground,
    background = MinerColors.Ground,
    onBackground = MinerColors.TextPrimary,
    surface = MinerColors.Panel,
    onSurface = MinerColors.TextPrimary,
    surfaceVariant = MinerColors.PanelRaised,
    onSurfaceVariant = MinerColors.TextSecondary,
    error = MinerColors.DangerRed,
    onError = MinerColors.Ground,
    errorContainer = MinerColors.DangerRedSoft,
    onErrorContainer = MinerColors.DangerRed,
    outline = MinerColors.Hairline,
    outlineVariant = MinerColors.HairlineBright
)

@Composable
fun TwitchBadgeMinerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MinerDarkColorScheme,
        typography = MinerTypography,
        content = content
    )
}
