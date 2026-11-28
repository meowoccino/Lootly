package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lootly.app.ui.theme.MinerColors

@Composable
fun BadgesDropsCard(
    badgesEnabled: Boolean,
    dropsEnabled: Boolean,
    autoPauseEnabled: Boolean,
    onBadgesToggle: (Boolean) -> Unit,
    onDropsToggle: (Boolean) -> Unit,
    onAutoPauseToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BareControlIcon(
                    icon = Icons.Rounded.WorkspacePremium,
                    contentDescription = "Badges",
                    enabled = badgesEnabled,
                    onClick = { onBadgesToggle(!badgesEnabled) }
                )
                BareControlIcon(
                    icon = Icons.Rounded.CardGiftcard,
                    contentDescription = "Drops",
                    enabled = dropsEnabled,
                    onClick = { onDropsToggle(!dropsEnabled) }
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MinerColors.Hairline)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BareControlIcon(
                    icon = Icons.Rounded.Tv,
                    contentDescription = "Auto-Pause on Twitch",
                    enabled = autoPauseEnabled,
                    onClick = { onAutoPauseToggle(!autoPauseEnabled) }
                )
                Switch(
                    checked = autoPauseEnabled,
                    onCheckedChange = onAutoPauseToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MinerColors.Ground,
                        checkedTrackColor = MinerColors.TwitchViolet,
                        uncheckedThumbColor = MinerColors.TextSecondary,
                        uncheckedTrackColor = MinerColors.Hairline,
                        uncheckedBorderColor = MinerColors.Hairline
                    )
                )
            }
        }
    }
}

@Composable
private fun BareControlIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = if (enabled) Color(0xFFE6E8F0) else Color(0xFF4B4F63)
        )
    }
}
