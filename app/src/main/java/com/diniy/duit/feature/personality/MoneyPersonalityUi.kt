package com.diniy.duit.feature.personality

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyFullScreenForm
import com.diniy.duit.core.ui.components.CandyMintIconButton
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.SpatialButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.personality.MoneyPersonality
import com.diniy.duit.domain.personality.PersonalityResult
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Composable
private fun MoneyPersonality?.tint(): Color = with(SpatialTheme.colors) {
    when (this@tint) {
        MoneyPersonality.HAPPY_FOODIE -> peach
        MoneyPersonality.SAVINGS_SUPERSTAR -> sunny
        MoneyPersonality.CAREFUL_PLANNER -> aqua
        MoneyPersonality.LITTLE_EXPLORER -> lavender
        null -> skyBlue
    }
}

/**
 * Original mascot drawn with Compose shapes: a round candy buddy with a prop for each personality
 * (bowl, star, clipboard, compass). A neutral buddy is used when there is no personality.
 */
@Composable
fun PersonalityMascot(personality: MoneyPersonality?, size: Dp, modifier: Modifier = Modifier, muted: Boolean = false) {
    val colors = SpatialTheme.colors
    val body = personality.tint()
    val ink = colors.sticker
    val blush = colors.coral.copy(alpha = 0.45f)
    val accent = colors.coral
    val reduced = rememberReducedMotionEnabled()
    val bob by if (reduced) androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    else rememberInfiniteTransition(label = "mascot").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bob")
    Canvas(modifier.size(size).graphicsLayer { translationY = -bob * size.toPx() * 0.04f; alpha = if (muted) 0.3f else 1f }) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h * 0.58f)
        val r = w * 0.32f
        drawCircle(body, r, c)
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.28f, Offset(c.x - r * 0.45f, c.y - r * 0.45f))
        // Face: eyes, smile, blush.
        drawCircle(ink, r * 0.09f, Offset(c.x - r * 0.33f, c.y - r * 0.08f))
        drawCircle(ink, r * 0.09f, Offset(c.x + r * 0.33f, c.y - r * 0.08f))
        drawArc(ink, 20f, 140f, false, topLeft = Offset(c.x - r * 0.22f, c.y - r * 0.05f), size = Size(r * 0.44f, r * 0.34f),
            style = Stroke(w * 0.022f, cap = StrokeCap.Round))
        drawCircle(blush, r * 0.12f, Offset(c.x - r * 0.6f, c.y + r * 0.15f))
        drawCircle(blush, r * 0.12f, Offset(c.x + r * 0.6f, c.y + r * 0.15f))
        when (personality) {
            MoneyPersonality.HAPPY_FOODIE -> {
                // Little bowl in front.
                drawArc(accent, 0f, 180f, true, topLeft = Offset(c.x - r * 0.55f, c.y + r * 0.45f), size = Size(r * 1.1f, r * 0.7f))
                drawLine(ink, Offset(c.x + r * 0.2f, c.y + r * 0.2f), Offset(c.x + r * 0.55f, c.y + r * 0.85f), w * 0.02f, StrokeCap.Round)
            }
            MoneyPersonality.SAVINGS_SUPERSTAR -> {
                val star = Path()
                val sc = Offset(c.x, c.y - r * 1.35f)
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) r * 0.38f else r * 0.16f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val p = Offset(sc.x + (rad * kotlin.math.cos(a)).toFloat(), sc.y + (rad * kotlin.math.sin(a)).toFloat())
                    if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
                }
                star.close()
                drawPath(star, colors.sunny)
                drawPath(star, ink, style = Stroke(w * 0.012f))
            }
            MoneyPersonality.CAREFUL_PLANNER -> {
                val tl = Offset(c.x + r * 0.55f, c.y + r * 0.05f)
                drawRoundRect(Color.White, topLeft = tl, size = Size(r * 0.6f, r * 0.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.08f))
                drawRoundRect(ink, topLeft = tl, size = Size(r * 0.6f, r * 0.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.08f), style = Stroke(w * 0.012f))
                drawLine(colors.income, Offset(tl.x + r * 0.12f, tl.y + r * 0.42f), Offset(tl.x + r * 0.25f, tl.y + r * 0.56f), w * 0.022f, StrokeCap.Round)
                drawLine(colors.income, Offset(tl.x + r * 0.25f, tl.y + r * 0.56f), Offset(tl.x + r * 0.48f, tl.y + r * 0.22f), w * 0.022f, StrokeCap.Round)
            }
            MoneyPersonality.LITTLE_EXPLORER -> {
                val cc = Offset(c.x + r * 0.85f, c.y + r * 0.45f)
                drawCircle(Color.White, r * 0.32f, cc)
                drawCircle(ink, r * 0.32f, cc, style = Stroke(w * 0.012f))
                drawLine(accent, Offset(cc.x, cc.y + r * 0.2f), Offset(cc.x, cc.y - r * 0.2f), w * 0.022f, StrokeCap.Round)
            }
            null -> drawCircle(ink.copy(alpha = 0.35f), r * 0.07f, Offset(c.x + r * 0.55f, c.y - r * 1.1f))
        }
    }
}

/** Teaser on Insights → Overview. */
@Composable
fun MoneyPersonalityTeaserCard(state: MoneyPersonalityUiState, onOpen: () -> Unit) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val assigned = state.result as? PersonalityResult.Assigned
    CandyBubbleCard(tone = CandyTone.Peach, onClick = onOpen, onClickLabel = "Open Money Personality") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonalityMascot(assigned?.personality, 64.dp,
                Modifier.semantics { contentDescription = assigned?.personality?.title?.let { "$it mascot" } ?: "Money buddy mascot" })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Money Personality", style = type.cardTitle, color = colors.primaryText)
                Text(
                    when {
                        state.isLoading -> "Looking at your month…"
                        assigned != null -> "${state.month.format(MONTH)}: ${assigned.personality.title}"
                        else -> "See your monthly money habits"
                    },
                    style = type.supportingInfo, color = colors.secondaryText
                )
            }
        }
    }
}

/** Full Money Personality page: result, reasons, habits, stickers and a share card without amounts. */
@Composable
fun MoneyPersonalityScreen(
    state: MoneyPersonalityUiState,
    onClose: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    CandyFullScreenForm(title = "Money Personality", subtitle = "Worked out on your phone from your own records", onDismissRequest = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyMintIconButton(icon = Icons.Rounded.ChevronLeft, contentDescription = "Previous month", onClick = onPreviousMonth)
            Text(state.month.format(MONTH), style = type.sectionHeading, color = colors.primaryText, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f))
            if (state.canGoForward) CandyMintIconButton(icon = Icons.Rounded.ChevronRight, contentDescription = "Next month", onClick = onNextMonth)
            else Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        val result = state.result
        when {
            state.isLoading -> Text("Looking at your month…", style = type.supportingInfo, color = colors.secondaryText)
            state.errorMessage != null -> {
                Text(state.errorMessage, style = type.supportingInfo, color = colors.error)
                Spacer(Modifier.height(8.dp))
                SpatialGlassButton(text = "Retry", onClick = onRetry, variant = SpatialButtonVariant.SecondaryGlass, modifier = Modifier.fillMaxWidth())
            }
            state.isSamplePreview -> Text("Money Personality uses your real records, so it's hidden in Sample Preview.",
                style = type.supportingInfo, color = colors.secondaryText)
            result is PersonalityResult.Assigned -> {
                PersonalityCard(result.personality, state.month)
                Spacer(Modifier.height(16.dp))
                Text("Why this fits", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
                result.reasons.forEach { Text("• $it", style = type.supportingInfo, color = colors.secondaryText) }
                Spacer(Modifier.height(12.dp))
                SpatialGlassButton(
                    text = "Share My Card",
                    enabled = !isSharing,
                    onClick = {
                        if (isSharing) return@SpatialGlassButton
                        isSharing = true
                        scope.launch {
                            try {
                                val chooser = withContext(Dispatchers.IO) {
                                    MoneyPersonalityShareCard.createShareChooserIntent(context, result.personality, state.month)
                                }
                                runCatching { context.startActivity(chooser) }
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            result is PersonalityResult.NoClearPattern -> {
                PersonalityCard(null, state.month, title = "Still Finding Your Style", tagline = "Your month didn't lean strongly one way, and that's perfectly fine.")
            }
            result is PersonalityResult.NotEnoughData -> {
                PersonalityCard(null, state.month, title = "Not Enough Data Yet", tagline = "Keep logging and your personality will appear.")
                Spacer(Modifier.height(8.dp))
                Text("We need:", style = type.inputLabel, color = colors.secondaryText)
                result.missing.forEach { Text("• $it", style = type.supportingInfo, color = colors.secondaryText) }
            }
        }
        result?.habits?.takeIf { !state.isLoading && state.errorMessage == null && !state.isSamplePreview }?.let { h ->
            Spacer(Modifier.height(16.dp))
            Text("Your habits", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitChip("Spending days", h.activeDays.toString(), Modifier.weight(1f))
                HabitChip("No-spend days", h.noSpendDays.toString(), Modifier.weight(1f))
                HabitChip("Categories", h.distinctCategories.toString(), Modifier.weight(1f))
            }
            h.topCategory?.let {
                Spacer(Modifier.height(8.dp))
                Text("Top category: $it (${h.topCategorySharePercent}% of spending)", style = type.supportingInfo, color = colors.secondaryText)
            }
        }
        if (!state.isSamplePreview) {
            Spacer(Modifier.height(20.dp))
            Text("Sticker Collection", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
            Text("Personalities from the last 12 months", style = type.caption, color = colors.secondaryText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyPersonality.entries.forEach { p ->
                    val count = state.stickers[p] ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)
                        .semantics(mergeDescendants = true) { contentDescription = "${p.title} sticker, " + if (count > 0) "earned $count times" else "not earned yet" }) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(if (count > 0) p.tint().copy(alpha = 0.3f) else colors.glassSurfaceSubtle),
                            contentAlignment = Alignment.Center) { PersonalityMascot(p, 48.dp, muted = count == 0) }
                        Text(if (count > 0) "×$count" else "—", style = type.caption, color = colors.secondaryText)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("This is a playful summary of your records, not financial advice. Refunds logged as income can make savings look a little lower.",
            style = type.caption, color = colors.secondaryText)
    }
}

@Composable
private fun PersonalityCard(personality: MoneyPersonality?, month: YearMonth, title: String = personality?.title.orEmpty(), tagline: String = personality?.tagline.orEmpty()) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    CandyBubbleCard(tone = CandyTone.Peach, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PersonalityMascot(personality, 120.dp, Modifier.semantics { contentDescription = "$title mascot" })
            Text(month.format(MONTH), style = type.caption, color = colors.secondaryText, textAlign = TextAlign.Center)
            Text(title, style = type.screenHeading, color = colors.primaryText, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Text(tagline, style = type.supportingInfo, color = colors.secondaryText, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HabitChip(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(colors.glassSurfaceSubtle)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = type.cardBalance, color = colors.primaryText)
        Text(label, style = type.caption, color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}
