package com.lootly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.data.model.TwitchSession
import com.lootly.app.ui.theme.MinerColors
import java.util.Locale

@Composable
fun AccountStatusCard(
    session: TwitchSession?,
    runState: MinerRunState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MinerColors.TwitchVioletSoft)
                        .border(
                            width = 1.5.dp,
                            color = if (session != null) MinerColors.TwitchViolet else MinerColors.HairlineBright,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = session?.avatarUrl
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "${session.effectiveDisplayName} avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    } else if (session != null) {
                        Text(
                            text = session.effectiveDisplayName.take(1).uppercase(Locale.ROOT),
                            color = MinerColors.TwitchVioletGlow,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MinerColors.TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (session != null) {
                        Text(
                            text = session.effectiveDisplayName,
                            color = MinerColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@${session.login}",
                            color = MinerColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Not Signed In",
                            color = MinerColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Sign in via Twitch WebView — zero tokens needed",
                            color = MinerColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (session == null) {
                    Button(
                        onClick = onLoginClick,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinerColors.TwitchViolet,
                            contentColor = MinerColors.TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Sign In", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = onLogoutClick,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MinerColors.TextSecondary
                        ),
                        border = BorderStroke(1.dp, MinerColors.HairlineBright),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Log Out", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = MinerColors.Hairline)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic Badge Miner",
                        color = MinerColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    val subtitle = when {
                        session == null -> "Sign in above to enable automatic mining"
                        runState == MinerRunState.STANDBY_NO_BADGES -> "Standby (0% battery drain) • scans every 1h"
                        runState.isServiceActive -> "Running in persistent background service"
                        else -> "Streamless watch-time mining • scans every 1h"
                    }
                    Text(
                        text = subtitle,
                        color = MinerColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = runState.isServiceActive,
                    onCheckedChange = onMasterToggle,
                    enabled = session != null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MinerColors.Ground,
                        checkedTrackColor = MinerColors.LiveMint,
                        uncheckedThumbColor = MinerColors.TextSecondary,
                        uncheckedTrackColor = MinerColors.PanelRaised,
                        uncheckedBorderColor = MinerColors.HairlineBright
                    )
                )
            }
        }
    }
}
