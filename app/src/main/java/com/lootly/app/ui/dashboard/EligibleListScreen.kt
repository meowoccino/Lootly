package com.lootly.app.ui.dashboard

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private enum class EligibleFilter { ALL, LINKED, UNLINKED }

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
    var filter by remember { mutableStateOf(EligibleFilter.ALL) }

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

    val linkedCount = remember(sortedEligible) { sortedEligible.count { it.isLinked } }
    val unlinkedCount = remember(sortedEligible) { sortedEligible.count { !it.isLinked } }
    val filteredItems = remember(sortedEligible, filter) {
        when (filter) {
            EligibleFilter.ALL -> sortedEligible
            EligibleFilter.LINKED -> sortedEligible.filter { it.isLinked }
            EligibleFilter.UNLINKED -> sortedEligible.filter { !it.isLinked }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MinerColors.Ground)
    ) {
        // Header: Back arrow + "Eligible" title + "X Linked" pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MinerColors.TextPrimary
                )
            }
            Text(
                text = "Eligible",
                color = MinerColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MinerColors.TwitchViolet.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, MinerColors.TwitchViolet.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "$linkedCount Linked",
                    color = MinerColors.TwitchVioletGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Filter chips: All · Linked (X) · Unlinked (Y)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EligibleFilterChip(
                label = "All",
                selected = filter == EligibleFilter.ALL,
                onClick = { filter = EligibleFilter.ALL }
            )
            EligibleFilterChip(
                label = "Linked ($linkedCount)",
                selected = filter == EligibleFilter.LINKED,
                onClick = { filter = EligibleFilter.LINKED }
            )
            EligibleFilterChip(
                label = "Unlinked ($unlinkedCount)",
                selected = filter == EligibleFilter.UNLINKED,
                onClick = { filter = EligibleFilter.UNLINKED }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scrollable list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filteredItems,
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
                    color = MinerColors.Panel,
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MinerColors.PanelRaised),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.badgeImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.badgeImageUrl,
                                    contentDescription = item.badgeName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
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

@Composable
private fun EligibleFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MinerColors.TwitchViolet.copy(alpha = 0.22f) else MinerColors.Panel,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MinerColors.TwitchViolet.copy(alpha = 0.6f) else MinerColors.Hairline
        )
    ) {
        Text(
            text = label,
            color = if (selected) MinerColors.TwitchVioletGlow else MinerColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
