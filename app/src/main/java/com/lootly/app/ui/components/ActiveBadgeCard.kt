package com.lootly.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lootly.app.data.model.EligibleBadgeItem
import com.lootly.app.data.model.MinerStatusSnapshot
import com.lootly.app.ui.theme.MinerColors

@Composable
fun ActiveBadgeCard(
    status: MinerStatusSnapshot,
    onTogglePause: () -> Unit,
    onForceRefreshBadges: () -> Unit,
    onPinBadge: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = status.activeCampaign
    val pinned = status.pinnedCampaign
    var queueExpanded by remember { mutableStateOf(false) }

    val sortedEligible = remember(status.eligibleBadges, pinned) {
        status.eligibleBadges.sortedWith(
            compareByDescending<EligibleBadgeItem> {
                !pinned.isNullOrBlank() && it.campaignName.equals(pinned, ignoreCase = true)
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!active?.badgeImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = active?.badgeImageUrl,
                            contentDescription = active?.badgeName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MinerColors.PanelRaised)
                                .padding(4.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MinerColors.TwitchVioletSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = MinerColors.TwitchVioletGlow
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active?.badgeName ?: status.runState.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MinerColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = if (active != null) {
                            buildString {
                                if (active.channelLogin.isNotBlank()) append("@${active.channelLogin}")
                                if (active.category.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(active.category)
                                }
                            }.ifBlank { active.campaignName }
                        } else {
                            "${status.eligibleCampaignCount} eligible • ${status.ownedBadgeCount} owned"
                        }
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelMedium,
                            color = MinerColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.runState.isServiceActive) {
                        IconButton(onClick = onTogglePause) {
                            Icon(
                                imageVector = if (status.runState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (status.runState.isPaused) "Resume" else "Pause",
                                tint = MinerColors.TextPrimary
                            )
                        }
                        IconButton(onClick = onForceRefreshBadges) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh badges",
                                tint = MinerColors.TextSecondary
                            )
                        }
                    }
                }
            }

            if (active != null) {
                Spacer(modifier = Modifier.height(14.dp))
                val targetProgress = (active.computedPercent / 100f).coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(targetValue = targetProgress, label = "badgeProgress")
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MinerColors.TwitchViolet,
                    trackColor = MinerColors.PanelRaised
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${active.computedPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MinerColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = active.etaLabel ?: if (active.isClaimed) "Claimed" else "In progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MinerColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !pinned.isNullOrBlank()) { onPinBadge(null) },
                    shape = RoundedCornerShape(10.dp),
                    color = MinerColors.PanelRaised,
                    border = BorderStroke(
                        1.dp,
                        if (pinned.isNullOrBlank()) MinerColors.Hairline else MinerColors.TwitchViolet
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("TARGET", style = MaterialTheme.typography.labelSmall, color = MinerColors.TextMuted)
                        Text(
                            text = if (pinned.isNullOrBlank()) "Auto" else "Pinned (Reset)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pinned.isNullOrBlank()) MinerColors.LiveMint else MinerColors.TwitchVioletGlow,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { queueExpanded = !queueExpanded },
                    shape = RoundedCornerShape(10.dp),
                    color = MinerColors.PanelRaised,
                    border = BorderStroke(1.dp, MinerColors.Hairline)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ELIGIBLE", style = MaterialTheme.typography.labelSmall, color = MinerColors.TextMuted)
                            Text(
                                text = "${sortedEligible.size} queued",
                                style = MaterialTheme.typography.labelMedium,
                                color = MinerColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (queueExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MinerColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (queueExpanded && sortedEligible.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MinerColors.Hairline)
                Spacer(modifier = Modifier.height(8.dp))
                sortedEligible.forEach { item ->
                    val isPinned = !pinned.isNullOrBlank() && item.campaignName.equals(pinned, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPinBadge(if (isPinned) null else item.campaignName) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MinerColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.campaignName} • ${item.requiredMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MinerColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) MinerColors.TwitchVioletGlow else MinerColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
