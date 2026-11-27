package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lootly.app.ui.theme.MinerColors

@Composable
fun AutoPauseCard(
    enabled: Boolean,
    hasUsagePermission: Boolean,
    twitchAppOpen: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePause = twitchAppOpen && enabled

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MinerColors.Panel,
        border = BorderStroke(
            width = 1.dp,
            color = if (activePause) MinerColors.WarningAmber.copy(alpha = 0.55f) else MinerColors.Hairline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = when {
                    activePause -> MinerColors.WarningAmber
                    enabled -> MinerColors.TwitchVioletGlow
                    else -> MinerColors.TextMuted
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Pause on Twitch App",
                    color = MinerColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                val subtitle = when {
                    !enabled -> "Disabled — miner keeps heartbeats active even if Twitch opens"
                    !hasUsagePermission -> "Grant Usage Access above to detect tv.twitch.android.app"
                    twitchAppOpen -> "Twitch app is in foreground — heartbeats paused"
                    else -> "Watching for tv.twitch.android.app every 3s"
                }
                Text(
                    text = subtitle,
                    color = if (activePause) MinerColors.WarningAmber else MinerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MinerColors.Ground,
                    checkedTrackColor = MinerColors.TwitchViolet,
                    uncheckedThumbColor = MinerColors.TextSecondary,
                    uncheckedTrackColor = MinerColors.PanelRaised,
                    uncheckedBorderColor = MinerColors.HairlineBright
                )
            )
        }
    }
}
