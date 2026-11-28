package com.lootly.app.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
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
                        overflow = TextOverflow.Ellipsis
                    )
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

            // Clean bare icon action bar
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color(0xFF222534)
            )

            val isManual = !pinned.isNullOrBlank()
            val activeTint = Color(0xFFC4A1FF)
            val idleTint = Color(0xFF8B90A5)

            val eligibleTint by animateColorAsState(
                targetValue = if (eligibleExpanded) activeTint else idleTint,
                label = "eligibleTint"
            )
            val pauseTint by animateColorAsState(
                targetValue = if (snapshot.runState.isPaused) activeTint else idleTint,
                label = "pauseTint"
            )
            val refreshTint by animateColorAsState(
                targetValue = if (refreshRotation.isRunning) activeTint else idleTint,
                label = "refreshTint"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = sortedEligible.isNotEmpty(), onClick = onToggleEligible),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatListBulleted,
                        contentDescription = "Eligible",
                        modifier = Modifier.size(21.dp),
                        tint = eligibleTint
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = isManual) { onPinBadge(null) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isManual) Icons.Rounded.PushPin else Icons.Rounded.AutoAwesome,
                        contentDescription = if (isManual) "Manual Mode" else "Auto Mode",
                        modifier = Modifier.size(20.dp),
                        tint = activeTint
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = snapshot.runState.isServiceActive, onClick = onTogglePause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (snapshot.runState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (snapshot.runState.isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(22.dp),
                        tint = pauseTint
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
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
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier
                            .size(21.dp)
                            .rotate(refreshRotation.value),
                        tint = refreshTint
                    )
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

            val targetListHeight = if (eligibleExpanded && sortedEligible.isNotEmpty()) {
                minOf(sortedEligible.size * 58, 220).dp
            } else {
                0.dp
            }
            val animatedListHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = targetListHeight,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                label = "eligibleListHeight"
            )

            if (animatedListHeight > 0.dp && sortedEligible.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedListHeight)
                        .clipToBounds()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(minOf(sortedEligible.size * 58, 220).dp)
                            .padding(top = 6.dp)
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
                                val next = sortedEligible[index + 1]
                                val isGroupBoundary = item.isDrop != next.isDrop || item.isLinked != next.isLinked
                                if (isGroupBoundary) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        thickness = 1.dp,
                                        color = Color(0xFF32364A)
                                    )
                                } else {
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
}
