package com.lootly.app.ui.dashboard

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14161F),
            border = BorderStroke(1.dp, MinerColors.TwitchViolet.copy(alpha = 0.35f)),
            shadowElevation = 16.dp
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clipToBounds(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = sortedEligible,
                    key = { "${it.campaignName}::${it.badgeName}" }
                ) { item ->
                    val isPinned = !pinned.isNullOrBlank() &&
                        (item.badgeName.equals(pinned, ignoreCase = true) ||
                            item.campaignName.equals(pinned, ignoreCase = true))
                    val isActive = active != null &&
                        (item.badgeName.equals(active.badgeName, ignoreCase = true) ||
                            item.campaignName.equals(active.campaignName, ignoreCase = true))
                    val isUnlinked = !item.isLinked
                    val amber = Color(0xFFF5A623)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isUnlinked) 0.75f else 1f)
                            .clip(RoundedCornerShape(16.dp))
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
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MinerColors.PanelRaised,
                        border = BorderStroke(
                            width = 1.dp,
                            color = when {
                                isUnlinked -> amber.copy(alpha = 0.28f)
                                isPinned || isActive -> MinerColors.TwitchViolet.copy(alpha = 0.65f)
                                else -> MinerColors.Hairline
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    color = MinerColors.TextPrimary,
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
                                color = MinerColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
