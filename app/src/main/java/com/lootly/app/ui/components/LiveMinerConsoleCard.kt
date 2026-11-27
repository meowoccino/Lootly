package com.lootly.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lootly.app.data.model.LogEntry
import com.lootly.app.data.model.LogLevel
import com.lootly.app.ui.theme.MinerColors

@Composable
fun LiveMinerConsoleCard(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(logs.size, expanded, autoScroll) {
        if (expanded && autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MinerColors.Panel,
        border = BorderStroke(1.dp, MinerColors.Hairline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MinerColors.TwitchVioletGlow
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Console",
                    color = MinerColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MinerColors.PanelRaised,
                    border = BorderStroke(1.dp, MinerColors.Hairline)
                ) {
                    Text(
                        text = logs.size.toString(),
                        color = MinerColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (expanded) {
                    IconButton(
                        onClick = { autoScroll = !autoScroll },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignBottom,
                            contentDescription = "Toggle auto-scroll",
                            modifier = Modifier.size(16.dp),
                            tint = if (autoScroll) MinerColors.LiveMint else MinerColors.TextMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") {
                                "${it.timestamp} [${it.category}] ${it.message}"
                            }
                            clipboard.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy logs",
                            modifier = Modifier.size(15.dp),
                            tint = MinerColors.TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear logs",
                            modifier = Modifier.size(16.dp),
                            tint = MinerColors.TextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 136.dp, max = 250.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MinerColors.Ground)
                            .border(1.dp, MinerColors.Hairline, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (logs.isEmpty()) {
                            item {
                                Text(
                                    text = "Waiting for miner engine logs…",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MinerColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            items(logs, key = { it.id }) { entry ->
                                val (tag, tagColor) = when (entry.level) {
                                    LogLevel.ERROR -> "ERR" to MinerColors.DangerRed
                                    LogLevel.WARN -> "WRN" to MinerColors.WarningAmber
                                    LogLevel.EVENT -> "DRP" to MinerColors.LiveMint
                                    LogLevel.INFO -> "INF" to MinerColors.InfoCyan
                                    LogLevel.DEBUG -> "DBG" to MinerColors.TextMuted
                                }
                                val msgColor = when (entry.level) {
                                    LogLevel.ERROR -> MinerColors.DangerRed
                                    LogLevel.WARN -> MinerColors.WarningAmber
                                    LogLevel.EVENT -> MinerColors.TextPrimary
                                    else -> MinerColors.TextSecondary
                                }
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = MinerColors.TextMuted)) {
                                            append(entry.timestamp)
                                            append(" ")
                                        }
                                        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold)) {
                                            append(tag)
                                            append(" ")
                                        }
                                        withStyle(SpanStyle(color = msgColor)) {
                                            append(entry.message)
                                        }
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
