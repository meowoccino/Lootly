package com.lootly.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lootly.app.data.model.EligibleBadgeItem
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.data.model.MinerStatusSnapshot
import com.lootly.app.ui.theme.MinerColors

@Composable
fun ActiveBadgeCard(
    snapshot: MinerStatusSnapshot,
    onForceRefreshBadges: () -> Unit,
    onTogglePause: () -> Unit,
    onPinBadge: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (statusColor, statusBg) = when (snapshot.runState) {
        MinerRunState.MINING_ACTIVE -> MinerColors.LiveMint to MinerColors.LiveMintSoft
        MinerRunState.SCANNING_BADGES, MinerRunState.STARTING -> MinerColors.TwitchVioletGlow to MinerColors.TwitchVioletSoft
        MinerRunState.PAUSED_TWITCH_OPEN, MinerRunState.PAUSED_MANUAL -> MinerColors.WarningAmber to MinerColors.WarningAmberSoft
        MinerRunState.ERROR -> MinerColors.DangerRed to MinerColors.DangerRedSoft
        MinerRunState.STANDBY_NO_BADGES -> MinerColors.InfoCyan to MinerColors.PanelRaised
        MinerRunState.STOPPED -> MinerColors.TextMuted to MinerColors.PanelRaised
    }

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var queueExpanded by remember { mutableStateOf(false) }
    val pinned = snapshot.pinnedCampaign

    val sortedEligible = remember(snapshot.eligibleBadges, pinned) {
        snapshot.eligibleBadges.sortedWith(
            compareByDescending<EligibleBadgeItem> {
                !pinned.isNullOrBlank() &&
                    (it.badgeName.equals(pinned, ignoreCase = true) ||
                        it.campaignName.equals(pinned, ignoreCase = true))
            }.thenBy { it.endsAtIso.ifBlank { "9999-12-31T23:59:59Z" } }
                .thenBy { it.requiredMinutes }
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ACTIVE BADGE CAMPAIGN",
                    color = MinerColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val shouldPulse = snapshot.runState == MinerRunState.MINING_ACTIVE ||
                            snapshot.runState == MinerRunState.SCANNING_BADGES
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(if (shouldPulse) pulseAlpha else 1f)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = snapshot.runState.label,
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val active = snapshot.activeCampaign
            if (active != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinerColors.TwitchVioletSoft)
                            .border(1.dp, MinerColors.TwitchViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!active.badgeImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = active.badgeImageUrl,
                                contentDescription = active.badgeName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MinerColors.TwitchVioletGlow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active.badgeName,
                            color = MinerColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                            if (active.channelLogin.isNotBlank()) append("@${active.channelLogin}")
                            if (active.category.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(active.category)
                            }
                        }.ifBlank { active.campaignName }
                        Text(
                            text = meta,
                            color = MinerColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val animatedProgress by animateFloatAsState(
                    targetValue = (active.computedPercent / 100f).coerceIn(0f, 1f),
                    label = "animatedProgress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = MinerColors.TwitchViolet,
                    trackColor = MinerColors.PanelRaised,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${active.computedPercent}%",
                        color = MinerColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = active.etaLabel ?: if (active.isClaimed) "Claimed" else "In progress",
                        color = MinerColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinerColors.PanelRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MilitaryTech,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MinerColors.TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (snapshot.runState) {
                                MinerRunState.STANDBY_NO_BADGES -> "All Available Badges Earned"
                                MinerRunState.STOPPED -> "Miner Stopped"
                                else -> "Scanning Badge Campaigns…"
                            },
                            color = MinerColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = when (snapshot.runState) {
                                MinerRunState.STANDBY_NO_BADGES -> "Low-power standby until next 1h catalog scan"
                                MinerRunState.STOPPED -> "Enable the Automatic Badge Miner toggle above"
                                else -> "Checking Twitch GQL & StreamDatabase for live watch-time badges"
                            },
                            color = MinerColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "ELIGIBLE",
                    value = sortedEligible.size.toString(),
                    accent = if (sortedEligible.isNotEmpty()) MinerColors.TwitchVioletGlow else MinerColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = if (sortedEligible.isNotEmpty()) ({ queueExpanded = !queueExpanded }) else null
                )
                MetricTile(
                    label = "OWNED",
                    value = snapshot.ownedBadgeCount.toString(),
                    accent = MinerColors.LiveMint,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "TARGET",
                    value = if (pinned.isNullOrBlank()) "Auto" else "Pinned",
                    accent = if (pinned.isNullOrBlank()) MinerColors.InfoCyan else MinerColors.TwitchVioletGlow,
                    modifier = Modifier.weight(1f),
                    onClick = if (!pinned.isNullOrBlank()) ({ onPinBadge(null) }) else if (sortedEligible.isNotEmpty()) ({ queueExpanded = !queueExpanded }) else null
                )
            }

            if (queueExpanded && sortedEligible.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ELIGIBLE QUEUE (TAP TO PIN)",
                        color = MinerColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (!pinned.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.clickable { onPinBadge(null) },
                            shape = RoundedCornerShape(999.dp),
                            color = MinerColors.TwitchVioletSoft,
                            border = BorderStroke(1.dp, MinerColors.TwitchViolet.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Reset Auto",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MinerColors.TwitchVioletGlow,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sortedEligible.forEach { item ->
                        val isPinned = !pinned.isNullOrBlank() &&
                            (item.badgeName.equals(pinned, ignoreCase = true) ||
                                item.campaignName.equals(pinned, ignoreCase = true))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPinBadge(if (isPinned) null else item.badgeName)
                                    queueExpanded = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPinned) MinerColors.TwitchVioletSoft else MinerColors.PanelRaised,
                            border = BorderStroke(
                                1.dp,
                                if (isPinned) MinerColors.TwitchViolet else MinerColors.Hairline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!item.badgeImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.badgeImageUrl,
                                        contentDescription = item.badgeName,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.badgeName,
                                        color = MinerColors.TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.campaignName} • ${item.requiredMinutes}m",
                                        color = MinerColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = if (isPinned) "Unpin" else "Pin",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isPinned) MinerColors.TwitchVioletGlow else MinerColors.TextMuted
                                )
                            }
                        }
                    }
                }
            }

            if (snapshot.runState.isServiceActive) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 1.dp, color = MinerColors.Hairline)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTogglePause,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, MinerColors.HairlineBright),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MinerColors.TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (snapshot.runState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (snapshot.runState.isPaused) "Resume" else "Pause",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onForceRefreshBadges,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, MinerColors.HairlineBright),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MinerColors.TextSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Force Refresh Badges",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color = MinerColors.TextPrimary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(12.dp),
        color = MinerColors.PanelRaised,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                color = MinerColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
