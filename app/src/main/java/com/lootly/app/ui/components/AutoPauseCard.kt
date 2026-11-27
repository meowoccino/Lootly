package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    twitchInForeground: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Twitch App Auto-Pause",
                    style = MaterialTheme.typography.titleMedium,
                    color = MinerColors.TextPrimary
                )
                Text(
                    text = if (twitchInForeground) {
                        "Twitch app detected — heartbeats paused"
                    } else {
                        "Pauses mining while official Twitch app is open"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (twitchInForeground) MinerColors.WarningAmber else MinerColors.TextSecondary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MinerColors.TextPrimary,
                    checkedTrackColor = MinerColors.TwitchViolet
                )
            )
        }
    }
}
