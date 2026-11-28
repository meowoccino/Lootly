package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lootly.app.ui.theme.MinerColors

@Composable
fun BadgesDropsCard(
    badgesEnabled: Boolean,
    dropsEnabled: Boolean,
    onBadgesToggle: (Boolean) -> Unit,
    onDropsToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MinerColors.Ground,
        checkedTrackColor = MinerColors.TwitchViolet,
        uncheckedThumbColor = MinerColors.TextSecondary,
        uncheckedTrackColor = MinerColors.Hairline,
        uncheckedBorderColor = MinerColors.Hairline
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Badges",
                    color = MinerColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = badgesEnabled,
                    onCheckedChange = onBadgesToggle,
                    colors = switchColors
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFF202230)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Drops",
                    color = MinerColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = dropsEnabled,
                    onCheckedChange = onDropsToggle,
                    colors = switchColors
                )
            }
        }
    }
}
