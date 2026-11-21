package com.diniy.duit.feature.wins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyMintIconButton
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.wins.LittleWinItem
import com.diniy.duit.domain.wins.LittleWinsCalculator
import com.diniy.duit.domain.wins.WinCategory
import com.diniy.duit.domain.wins.WinIconStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Teaser card in Insights -> Overview for Little Wins Club Sticker Collection.
 */
@Composable
fun LittleWinsTeaserCard(
    state: LittleWinsUiState,
    onOpen: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val newCount = state.uncelebratedItems.size

    CandyBubbleCard(
        tone = CandyTone.Sunny,
        onClick = onOpen,
        onClickLabel = "Open Little Wins Club",
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WinStickerBadge(
                style = WinIconStyle.CROWN,
                category = WinCategory.SAVINGS,
                earned = state.earnedCount > 0,
                size = 58.dp,
                contentDesc = "Little Wins Club sticker badge"
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Little Wins Club", style = type.cardTitle, color = colors.primaryText)
                Text(
                    text = when {
                        state.isLoading -> "Checking your stickers…"
                        state.isSamplePreview -> "Collect stickers from real records"
                        else -> "${state.earnedCount} of ${state.totalCount} stickers collected"
                    },
                    style = type.supportingInfo,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (newCount > 0 && !state.isSamplePreview) {
                Spacer(Modifier.width(8.dp))
                CandyStatusBadge(text = if (newCount == 1) "New win" else "$newCount new", tone = CandyTone.Mint)
            }
        }
    }
}

/**
 * Full-screen Little Wins Club Sticker Collection (Candy Bubble 2.0, Reduce Motion aware, soft haptics).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LittleWinsClubScreen(
    state: LittleWinsUiState,
    onSelectCategory: (WinCategory?) -> Unit,
    onMarkCelebrated: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val reducedMotion = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()

    val listState = rememberLazyListState()
    var inspectedWin by remember { mutableStateOf<LittleWinItem?>(null) }
    var acknowledgedKeys by remember { mutableStateOf(emptySet<String>()) }
    var celebrationKeys by remember { mutableStateOf(state.uncelebratedItems.map { it.type.key }) }
    var hapticFired by remember { mutableStateOf(false) }

    val uncelebratedKeys = state.uncelebratedItems.map { it.type.key }.filterNot { it in acknowledgedKeys }
    LaunchedEffect(uncelebratedKeys) {
        if (uncelebratedKeys.isNotEmpty()) {
            val wasEmpty = celebrationKeys.isEmpty()
            if (wasEmpty) {
                celebrationKeys = uncelebratedKeys
            }
            if (!hapticFired) {
                hapticFired = true
                haptic(HapticFeedbackType.Confirm)
            }
            if (wasEmpty) {
                listState.scrollToItem(0)
            }
        }
    }

    val acknowledgeCelebration: () -> Unit = {
        if (celebrationKeys.isNotEmpty()) {
            acknowledgedKeys = acknowledgedKeys + celebrationKeys
            onMarkCelebrated(celebrationKeys)
            celebrationKeys = emptyList()
        }
    }

    val handleClose: () -> Unit = {
        acknowledgeCelebration()
        onClose()
    }

    Dialog(
        onDismissRequest = handleClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        val lightSystemBars = !colors.isDark
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightSystemBars
            controller.isAppearanceLightNavigationBars = lightSystemBars
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .semantics {
                    testTagsAsResourceId = true
                    paneTitle = "Little Wins Club"
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                CandyMintIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = handleClose
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Little Wins Club",
                    style = type.screenHeading,
                    color = colors.primaryText,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isSamplePreview) {
                    item(key = "preview") {
                        CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Stickers unlock from your real records and stay hidden in Sample Preview.",
                                style = type.supportingInfo,
                                color = colors.secondaryText
                            )
                        }
                    }
                } else {
                    if (celebrationKeys.isNotEmpty()) {
                        val celebratingItems = state.items.filter { it.type.key in celebrationKeys }
                        if (celebratingItems.isNotEmpty()) {
                            item(key = "celebration") {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(240)) + scaleIn(initialScale = 0.94f),
                                    exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(180))
                                ) {
                                    CandyBubbleCard(
                                        tone = CandyTone.Mint,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics { liveRegion = LiveRegionMode.Polite }
                                            .testTag("wins_celebration_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            WinStickerBadge(
                                                style = celebratingItems.first().type.iconStyle,
                                                category = celebratingItems.first().type.category,
                                                earned = true,
                                                size = 56.dp,
                                                contentDesc = "${celebratingItems.first().type.title} new sticker"
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = if (celebratingItems.size == 1) "New Sticker Unlocked" else "${celebratingItems.size} New Stickers",
                                                    style = type.cardTitle,
                                                    color = colors.primaryText
                                                )
                                                Text(
                                                    text = celebratingItems.joinToString(", ") { it.type.title },
                                                    style = type.supportingInfo,
                                                    color = colors.secondaryText,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        SpatialGlassButton(
                                            text = "Nice!",
                                            onClick = {
                                                haptic(HapticFeedbackType.Confirm)
                                                acknowledgeCelebration()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "collection_summary") {
                        val targetRatio = if (state.totalCount > 0) state.earnedCount.toFloat() / state.totalCount.toFloat() else 0f
                        val animatedRatio by animateFloatAsState(
                            targetValue = targetRatio.coerceIn(0f, 1f),
                            animationSpec = if (reducedMotion) tween(0) else tween(450),
                            label = "wins_progress"
                        )
                        CandyBubbleCard(tone = CandyTone.Peach, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Sticker Collection", style = type.sectionHeading, color = colors.primaryText)
                                    Text("Every steady step counts.", style = type.caption, color = colors.secondaryText)
                                }
                                Text(
                                    text = "${state.earnedCount}/${state.totalCount}",
                                    style = type.cardBalance,
                                    color = colors.primaryText
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(colors.glassSurfaceSubtle)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedRatio)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(colors.coral)
                                )
                            }
                        }
                    }

                    item(key = "category_pills") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpatialGlassPill(
                                label = "All",
                                selected = state.selectedCategory == null,
                                onClick = { onSelectCategory(null) }
                            )
                            WinCategory.entries.forEach { cat ->
                                SpatialGlassPill(
                                    label = cat.label,
                                    selected = state.selectedCategory == cat,
                                    onClick = { onSelectCategory(cat) }
                                )
                            }
                        }
                    }

                    items(state.filteredItems, key = { it.type.key }, contentType = { "win_card" }) { item ->
                        LittleWinStickerCard(
                            item = item,
                            reducedMotion = reducedMotion,
                            onClick = {
                                if (item.isEarned) haptic(HapticFeedbackType.TextHandleMove)
                                inspectedWin = item
                            }
                        )
                    }

                    item(key = "wins_footer") {
                        Text(
                            text = "Earned from verified records. Past stickers stay saved.",
                            style = type.caption,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }

    inspectedWin?.let { win ->
        AlertDialog(
            onDismissRequest = { inspectedWin = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WinStickerBadge(
                        style = win.type.iconStyle,
                        category = win.type.category,
                        earned = win.isEarned,
                        size = 44.dp,
                        contentDesc = "${win.type.title} sticker"
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(win.type.title, style = type.sectionHeading)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(win.type.description, style = type.supportingInfo, color = colors.primaryText)
                    if (win.isEarned && win.earnedAtEpochMs != null) {
                        Text(
                            text = "Collected ${LittleWinsCalculator.formatEarnedDate(win.earnedAtEpochMs)}",
                            style = type.caption,
                            color = colors.secondaryText
                        )
                    }
                    Text(
                        text = "Current progress: ${win.progressLabel} (${win.progressPercent}%)",
                        style = type.caption,
                        color = colors.secondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedWin = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun LittleWinStickerCard(
    item: LittleWinItem,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val tone = when (item.type.category) {
        WinCategory.SAVINGS -> CandyTone.Sunny
        WinCategory.BUDGET -> CandyTone.Mint
        WinCategory.COMMITMENT -> CandyTone.Sky
        WinCategory.CHALLENGE -> CandyTone.Peach
    }
    val animatedProgress by animateFloatAsState(
        targetValue = (item.progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else tween(350),
        label = "win_item_prog"
    )

    CandyBubbleCard(
        tone = tone,
        onClick = onClick,
        onClickLabel = "Inspect ${item.type.title}",
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WinStickerBadge(
                style = item.type.iconStyle,
                category = item.type.category,
                earned = item.isEarned,
                size = 52.dp,
                contentDesc = "${item.type.title} sticker, ${if (item.isEarned) "collected" else "in progress"}"
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.type.title,
                        style = type.cardTitle,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    CandyStatusBadge(
                        text = if (item.isEarned) "Collected" else "${item.progressPercent}%",
                        tone = if (item.isEarned) CandyTone.Mint else CandyTone.Sky
                    )
                }
                Text(
                    text = item.type.description,
                    style = type.caption,
                    color = colors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                if (item.isEarned) {
                    val dateText = item.earnedAtEpochMs?.let { "Saved ${LittleWinsCalculator.formatEarnedDate(it)}" } ?: "Verified win"
                    Text(
                        text = "$dateText · ${item.progressLabel}",
                        style = type.caption,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.glassSurfaceSubtle)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(colors.coral)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.progressLabel,
                        style = type.caption,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Original Candy Bubble 2.0 Canvas vector sticker badge (zero emojis).
 * Vibrant when earned; softly muted when still in progress.
 */
@Composable
fun WinStickerBadge(
    style: WinIconStyle,
    category: WinCategory,
    earned: Boolean,
    size: Dp,
    contentDesc: String,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val fillTint = when (category) {
        WinCategory.SAVINGS -> colors.sunny
        WinCategory.BUDGET -> colors.aqua
        WinCategory.COMMITMENT -> colors.skyBlue
        WinCategory.CHALLENGE -> colors.peach
    }
    val bgColor = if (earned) fillTint else colors.glassSurfaceSubtle
    val ink = if (earned) colors.sticker else colors.secondaryText.copy(alpha = 0.55f)
    val accent = if (earned) colors.coral else colors.secondaryText.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * 0.7f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val sw = w * 0.09f
            val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // Gloss dot on earned stickers
            if (earned) {
                drawCircle(Color.White.copy(alpha = 0.35f), w * 0.12f, Offset(w * 0.25f, h * 0.22f))
            }

            when (style) {
                WinIconStyle.SEED, WinIconStyle.SPROUT, WinIconStyle.BUD -> {
                    // Cute sprouting leaf
                    drawLine(ink, Offset(cx, h * 0.82f), Offset(cx, h * 0.38f), sw, StrokeCap.Round)
                    drawOval(accent, Offset(cx - w * 0.34f, h * 0.28f), Size(w * 0.34f, h * 0.24f))
                    drawOval(ink, Offset(cx, h * 0.22f), Size(w * 0.32f, h * 0.24f), style = stroke)
                }
                WinIconStyle.CLIMB, WinIconStyle.SPARK, WinIconStyle.CALENDAR_STAR -> {
                    // 5-point star sticker
                    val star = Path()
                    for (i in 0 until 10) {
                        val rad = if (i % 2 == 0) w * 0.42f else w * 0.19f
                        val a = Math.toRadians(-90.0 + i * 36.0)
                        val px = cx + (rad * cos(a)).toFloat()
                        val py = cy + (rad * sin(a)).toFloat()
                        if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                    }
                    star.close()
                    drawPath(star, if (earned) Color.White.copy(alpha = 0.8f) else accent)
                    drawPath(star, ink, style = stroke)
                }
                WinIconStyle.CROWN, WinIconStyle.HARVEST -> {
                    // Little crown
                    val crown = Path().apply {
                        moveTo(w * 0.16f, h * 0.72f)
                        lineTo(w * 0.16f, h * 0.32f)
                        lineTo(w * 0.36f, h * 0.50f)
                        lineTo(cx, h * 0.22f)
                        lineTo(w * 0.64f, h * 0.50f)
                        lineTo(w * 0.84f, h * 0.32f)
                        lineTo(w * 0.84f, h * 0.72f)
                        close()
                    }
                    drawPath(crown, if (earned) Color.White.copy(alpha = 0.75f) else accent)
                    drawPath(crown, ink, style = stroke)
                }
                WinIconStyle.CUSHION, WinIconStyle.BLOOM -> {
                    // Cozy 4-petal bloom / cushion
                    val r = w * 0.18f
                    drawCircle(accent, r, Offset(cx, cy - r))
                    drawCircle(accent, r, Offset(cx, cy + r))
                    drawCircle(accent, r, Offset(cx - r, cy))
                    drawCircle(accent, r, Offset(cx + r, cy))
                    drawCircle(Color.White, r * 0.85f, Offset(cx, cy))
                    drawCircle(ink, r * 0.85f, Offset(cx, cy), style = stroke)
                }
                WinIconStyle.PLAN, WinIconStyle.CALM -> {
                    // Little checklist card
                    drawRoundRect(
                        Color.White.copy(alpha = if (earned) 0.85f else 0.35f),
                        topLeft = Offset(w * 0.2f, h * 0.16f),
                        size = Size(w * 0.6f, h * 0.68f),
                        cornerRadius = CornerRadius(w * 0.12f)
                    )
                    drawRoundRect(
                        ink,
                        topLeft = Offset(w * 0.2f, h * 0.16f),
                        size = Size(w * 0.6f, h * 0.68f),
                        cornerRadius = CornerRadius(w * 0.12f),
                        style = stroke
                    )
                    drawLine(ink, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.45f, h * 0.62f), sw, StrokeCap.Round)
                    drawLine(ink, Offset(w * 0.45f, h * 0.62f), Offset(w * 0.68f, h * 0.38f), sw, StrokeCap.Round)
                }
                WinIconStyle.SHIELD, WinIconStyle.CHECK_RIBBON, WinIconStyle.FEATHER -> {
                    // Shield with checkmark
                    val shield = Path().apply {
                        moveTo(cx, h * 0.14f)
                        lineTo(w * 0.82f, h * 0.28f)
                        lineTo(w * 0.76f, h * 0.64f)
                        lineTo(cx, h * 0.86f)
                        lineTo(w * 0.24f, h * 0.64f)
                        lineTo(w * 0.18f, h * 0.28f)
                        close()
                    }
                    drawPath(shield, if (earned) Color.White.copy(alpha = 0.8f) else accent)
                    drawPath(shield, ink, style = stroke)
                    drawLine(ink, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.47f, h * 0.61f), sw, StrokeCap.Round)
                    drawLine(ink, Offset(w * 0.47f, h * 0.61f), Offset(w * 0.66f, h * 0.40f), sw, StrokeCap.Round)
                }
            }
        }
    }
}
