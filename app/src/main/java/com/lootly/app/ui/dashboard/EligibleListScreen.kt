package com.lootly.app.ui.dashboard

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lootly.app.data.model.EligibleBadgeItem
import com.lootly.app.data.model.MinerStatusSnapshot
import com.lootly.app.ui.login.LinkAccountActivity
import com.lootly.app.ui.theme.MinerColors

@Composable
fun EligibleListScreen(
    snapshot: MinerStatusSnapshot,
    badgesEnabled: Boolean,
    dropsEnabled: Boolean,
    onBack: () -> Unit,
    onPinBadge: (String?) -> Unit,
    onForceRefreshBadges: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val linkAccountLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onForceRefreshBadges()
    }

    val pinned = snapshot.pinnedCampaign
    val active = snapshot.activeCampaign

    val sortedEligible = remember(snapshot.eligibleBadges, active, pinned, badgesEnabled, dropsEnabled) {
        val base = snapshot.eligibleBadges.ifEmpty {
            if (active != null) {
                listOf(
                    EligibleBadgeItem(
                        campaignName = active.campaignName,
                        badgeName = active.badgeName,
                        allTiers = active.badgeName,
                        gameName = active.category,
                        badgeImageUrl = active.badgeImageUrl,
                        requiredMinutes = active.requiredMinutes.takeIf { it > 0 } ?: 30
                    )
                )
            } else {
                emptyList()
            }
        }.filter { item ->
            if (item.isDrop) dropsEnabled else badgesEnabled
        }
        base.sortedWith(
            compareByDescending<EligibleBadgeItem> {
                !pinned.isNullOrBlank() &&
                    (it.badgeName.equals(pinned, ignoreCase = true) ||
                        it.campaignName.equals(pinned, ignoreCase = true))
            }.thenByDescending { it.isLinked }
                .thenBy { it.isDrop }
                .thenBy { it.endsAtIso.ifBlank { "9999-12-31T23:59:59Z" } }
                .thenBy { it.requiredMinutes }
        )
    }

    // Highlight at most ONE single active/pinned tier (never multiple tiers of the same campaign)
    val highlightedKey = remember(sortedEligible, active, pinned) {
        val linked = sortedEligible.filter { it.isLinked }
        val matched = when {
            !pinned.isNullOrBlank() ->
                linked.firstOrNull { it.badgeName.equals(pinned, ignoreCase = true) }
                    ?: linked.firstOrNull { it.campaignName.equals(pinned, ignoreCase = true) }
            active != null ->
                linked.firstOrNull { it.badgeName.equals(active.badgeName, ignoreCase = true) }
                    ?: linked.firstOrNull { it.campaignName.equals(active.campaignName, ignoreCase = true) }
            else -> null
        }
        matched?.let { "${it.campaignName}::${it.badgeName}" }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = Color(0xFF14161F),
            border = BorderStroke(1.dp, Color(0xFF262837))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Centered drag pill only — no title, no close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clipToBounds()
                ) {
                    itemsIndexed(
                        items = sortedEligible,
                        key = { _, item -> "${item.campaignName}::${item.badgeName}" }
                    ) { index, item ->
                        val itemKey = "${item.campaignName}::${item.badgeName}"
                        val isHighlighted = itemKey == highlightedKey
                        val isPinned = !pinned.isNullOrBlank() &&
                            (item.badgeName.equals(pinned, ignoreCase = true) ||
                                (isHighlighted && item.campaignName.equals(pinned, ignoreCase = true)))
                        val isUnlinked = !item.isLinked
                        val amber = Color(0xFFF5A623)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isHighlighted && !isUnlinked) {
                                        Color(0xFF9146FF).copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .alpha(if (isUnlinked) 0.72f else 1f)
                                .clickable {
                                    if (isUnlinked) {
                                        val url = item.accountLinkUrl.ifBlank { "https://www.twitch.tv/drops/campaigns" }
                                        val intent = Intent(context, LinkAccountActivity::class.java).apply {
                                            putExtra(LinkAccountActivity.EXTRA_URL, url)
                                        }
                                        linkAccountLauncher.launch(intent)
                                    } else {
                                        onPinBadge(if (isPinned) null else item.badgeName)
                                        onBack()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.badgeImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.badgeImageUrl,
                                    contentDescription = item.badgeName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MinerColors.TwitchViolet.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WorkspacePremium,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MinerColors.TwitchVioletGlow
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.badgeName,
                                    color = if (isHighlighted && !isUnlinked) Color(0xFFC4A1FF) else MinerColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val gameSubtitle = item.gameName.ifBlank { item.campaignName }
                                if (gameSubtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = gameSubtitle,
                                        color = MinerColors.TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            if (isUnlinked) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = "Account not linked",
                                    modifier = Modifier.size(16.dp),
                                    tint = amber
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "${item.requiredMinutes}m",
                                color = if (isHighlighted && !isUnlinked) Color(0xFFC4A1FF) else MinerColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (index < sortedEligible.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = Color(0xFF202332)
                            )
                        }
                    }
                }
            }
        }
    }
}
