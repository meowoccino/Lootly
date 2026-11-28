package com.lootly.app.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lootly.app.data.model.EligibleBadgeItem
import com.lootly.app.data.model.MinerRunState
import com.lootly.app.data.model.MinerStatusSnapshot
import com.lootly.app.ui.login.LinkAccountActivity
import com.lootly.app.ui.theme.MinerColors
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun ActiveBadgeCard(
    snapshot: MinerStatusSnapshot,
    onForceRefreshBadges: () -> Unit,
    onTogglePause: () -> Unit,
    onPinBadge: (String?) -> Unit = {},
    eligibleExpanded: Boolean = false,
    onToggleEligible: () -> Unit = {},
    badgesEnabled: Boolean = true,
    dropsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val active = snapshot.activeCampaign
    val pinned = snapshot.pinnedCampaign
    val isPaused = snapshot.runState.isPaused || !snapshot.runState.isServiceActive

    val context = LocalContext.current
    val linkAccountLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onForceRefreshBadges()
    }
    val refreshRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val sortedEligible = remember(snapshot.eligibleBadges, snapshot.activeCampaign, pinned, badgesEnabled, dropsEnabled) {
        val base = snapshot.eligibleBadges.ifEmpty {
            val c = snapshot.activeCampaign
            if (c != null) {
                listOf(
                    EligibleBadgeItem(
                        campaignName = c.campaignName,
                        badgeName = c.badgeName,
                        allTiers = c.badgeName,
                        gameName = c.category,
                        badgeImageUrl = c.badgeImageUrl,
                        requiredMinutes = c.requiredMinutes.takeIf { it > 0 } ?: 30
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

    val targetFraction = if (active != null) {
        (active.computedPercent / 100f).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fillFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 500),
        label = "liquidFill"
    )

    val wavePhase = if (!isPaused && fillFraction > 0.01f) {
        val infinite = rememberInfiniteTransition(label = "liquidWave")
        val anim by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        anim
    } else {
        0f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Badge icon with Liquid Wave Fill + Title + Auto/Pinned pill + Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MinerColors.PanelRaised),
                    contentAlignment = Alignment.Center
                ) {
                    if (fillFraction > 0.01f) {
                        val waveColor = MinerColors.TwitchViolet.copy(alpha = 0.48f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val baseY = h * (1f - fillFraction)
                            val amp = if (fillFraction >= 0.98f) 0f else 3.5.dp.toPx()
                            val path = Path().apply {
                                moveTo(0f, baseY)
                                val steps = 24
                                for (i in 0..steps) {
                                    val x = w * i / steps
                                    val angle = (i.toFloat() / steps) * (2.0 * PI).toFloat() + wavePhase
                                    val y = baseY + sin(angle) * amp
                                    lineTo(x, y)
                                }
                                lineTo(w, h)
                                lineTo(0f, h)
                                close()
                            }
                            drawPath(path = path, color = waveColor)
                        }
                    }

                    val heroImageUrl = active?.badgeImageUrl?.takeIf { it.isNotBlank() }
                        ?: active?.let { c ->
                            snapshot.eligibleBadges.firstOrNull { item ->
                                item.badgeName.equals(c.badgeName, ignoreCase = true) ||
                                    item.campaignName.equals(c.campaignName, ignoreCase = true)
                            }?.badgeImageUrl?.takeIf { it.isNotBlank() }
                        }

                    if (!heroImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = heroImageUrl,
                            contentDescription = active?.badgeName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MinerColors.TwitchVioletGlow
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val title = active?.badgeName ?: when {
                            snapshot.runState.isPaused -> "Paused"
                            snapshot.runState == MinerRunState.STANDBY_NO_BADGES -> "All Caught Up"
                            snapshot.runState == MinerRunState.STOPPED -> "Miner Stopped"
                            else -> "Scanning…"
                        }
                        Text(
                            text = title,
                            color = MinerColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(enabled = !pinned.isNullOrBlank()) {
                                    onPinBadge(null)
                                },
                            shape = RoundedCornerShape(999.dp),
                            color = MinerColors.PanelRaised,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (!pinned.isNullOrBlank()) {
                                    MinerColors.TwitchViolet.copy(alpha = 0.55f)
                                } else {
                                    MinerColors.Hairline
                                }
                            )
                        ) {
                            Text(
                                text = if (pinned.isNullOrBlank()) "Auto" else "Manual",
                                color = MinerColors.TwitchVioletGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    val subtitle = if (active != null) {
                        buildString {
                            if (active.channelLogin.isNotBlank()) append(active.channelLogin)
                            if (active.category.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(active.category)
                            }
                        }.ifBlank { active.campaignName }
                    } else {
                        when {
                            snapshot.runState.isPaused -> "Tap Resume to continue"
                            snapshot.runState == MinerRunState.STANDBY_NO_BADGES -> "Standby · checks hourly"
                            snapshot.runState == MinerRunState.STOPPED -> "Toggle switch above to start"
                            else -> "Checking badges & drops"
                        }
                    }
                    Text(
                        text = subtitle,
                        color = MinerColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Single balanced action row: X Eligible + Pause/Resume + 360° Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = sortedEligible.isNotEmpty(), onClick = onToggleEligible),
                    shape = RoundedCornerShape(999.dp),
                    color = MinerColors.PanelRaised,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (eligibleExpanded) {
                            MinerColors.TwitchViolet.copy(alpha = 0.55f)
                        } else {
                            MinerColors.Hairline
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val eligibleCount = sortedEligible.size
                        Text(
                            text = "$eligibleCount Eligible",
                            color = MinerColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = snapshot.runState.isServiceActive, onClick = onTogglePause),
                    shape = RoundedCornerShape(999.dp),
                    color = MinerColors.TwitchViolet.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, MinerColors.TwitchViolet.copy(alpha = 0.52f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (snapshot.runState.isPaused) "Resume" else "Pause",
                            color = MinerColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            onForceRefreshBadges()
                            scope.launch {
                                refreshRotation.snapTo(0f)
                                refreshRotation.animateTo(
                                    targetValue = 360f,
                                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = MinerColors.PanelRaised,
                    border = BorderStroke(1.dp, MinerColors.Hairline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Badges",
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(refreshRotation.value),
                            tint = MinerColors.TextSecondary
                        )
                    }
                }
            }

            // Design #8: Hero Accordion Dropdown
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

            AnimatedVisibility(visible = eligibleExpanded && sortedEligible.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color(0xFF262837)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
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
                                    .clip(RoundedCornerShape(10.dp))
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
                                            onToggleEligible()
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!item.badgeImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.badgeImageUrl,
                                        contentDescription = item.badgeName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MinerColors.TwitchViolet.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WorkspacePremium,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MinerColors.TwitchVioletGlow
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.badgeName,
                                        color = if (isHighlighted && !isUnlinked) Color(0xFFC4A1FF) else MinerColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val gameSubtitle = item.gameName.ifBlank { item.campaignName }
                                    if (gameSubtitle.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = gameSubtitle,
                                            color = MinerColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isUnlinked) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = "Account not linked",
                                        modifier = Modifier.size(15.dp),
                                        tint = amber
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    text = "${item.requiredMinutes}m",
                                    color = if (isHighlighted && !isUnlinked) Color(0xFFC4A1FF) else MinerColors.TextSecondary,
                                    fontSize = 11.sp,
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
}
