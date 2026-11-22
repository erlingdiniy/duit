package com.diniy.duit.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.theme.SpatialTheme
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/*
 * Candy Bubble 2.0 component kit (Home first). Built on Spatial tokens so light/dark,
 * the depth setting and Reduce Motion all keep working. Purpose-built pieces:
 * tones, bubble cards with layered decor, ticket shape, icon blobs, stickers, status badges,
 * section headers, date tiles, progress ring, split money typography and spring press.
 */

/** Semantic candy tones. Each resolves to a fill, a soft container and readable ink per theme. */
enum class CandyTone { Coral, Mint, Sunny, Peach, Sky, Neutral }

@Immutable
data class CandyToneColors(
    val fill: Color,
    val container: Color,
    val ink: Color,
    val onFill: Color
)

@Composable
fun candyTone(tone: CandyTone): CandyToneColors {
    val c = SpatialTheme.colors
    val surface = c.glassSurface
    val mix = if (c.isDark) 0.20f else 0.22f
    fun soft(accent: Color) = lerp(surface, accent, mix)
    return when (tone) {
        CandyTone.Coral -> CandyToneColors(c.coral, soft(c.coral), c.readableOn(c.coral), c.onCoral)
        CandyTone.Mint -> CandyToneColors(c.aqua, c.incomeContainer, c.onIncomeContainer, c.onCoral)
        CandyTone.Sunny -> CandyToneColors(c.sunny, c.savingsContainer, c.onSavingsContainer, c.onSunny)
        CandyTone.Peach -> CandyToneColors(c.peach, soft(c.peach), c.readableOn(c.peach), c.onCoral)
        CandyTone.Sky -> CandyToneColors(c.skyBlue, soft(c.skyBlue), c.readableOn(c.skyBlue), c.onCoral)
        CandyTone.Neutral -> CandyToneColors(c.baseSurface, c.glassSurface, c.primaryText, c.primaryText)
    }
}

/**
 * One-shot reveal progress (0 -> 1) for charts and entrances. Plays once per [key]; the
 * "played" flag is saveable, so scrolling an item off and back (LazyColumn) never replays it.
 * A new [key] (meaningful data change) plays again. Snaps to 1 under Reduce Motion.
 * Read `.value` inside draw/graphicsLayer lambdas to avoid recomposing every frame.
 */
@Composable
fun rememberCandyReveal(key: Any?, durationMs: Int = 650): Animatable<Float, AnimationVector1D> {
    val reduced = rememberReducedMotionEnabled()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val anim = remember(key) { Animatable(if (played || reduced) 1f else 0f) }
    LaunchedEffect(key, reduced) {
        if (reduced) {
            anim.snapTo(1f)
            played = true
        } else if (!played) {
            anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
            played = true
        }
    }
    return anim
}

/** Gentle fade + rise for high-priority cards, once per [key]. */
fun Modifier.candyEntrance(key: Any?): Modifier = composed {
    val reveal = rememberCandyReveal(key, durationMs = SpatialTheme.motion.durationStandardMs + 100)
    graphicsLayer {
        val p = reveal.value
        alpha = p
        translationY = (1f - p) * 18.dp.toPx()
    }
}

/** Persisted accent tokens (wallets, goals) mapped to candy tones. */
fun candyToneFor(token: com.diniy.duit.domain.model.PrismaticAccentToken): CandyTone = when (token) {
    com.diniy.duit.domain.model.PrismaticAccentToken.AQUA -> CandyTone.Mint
    com.diniy.duit.domain.model.PrismaticAccentToken.LAVENDER -> CandyTone.Coral
    com.diniy.duit.domain.model.PrismaticAccentToken.SKY_BLUE -> CandyTone.Sky
    com.diniy.duit.domain.model.PrismaticAccentToken.PEACH -> CandyTone.Peach
    com.diniy.duit.domain.model.PrismaticAccentToken.SLATE -> CandyTone.Sunny
}

/** Spring "squish" on press for any clickable element. No motion under Reduce Motion. */
fun Modifier.candyPressable(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    val motion = SpatialTheme.motion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = prismaticSpring(
            reducedMotion = reduced,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "candy_press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClickLabel = onClickLabel
        ) {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Ticket outline: rounded rect with two half-circle notches cut at [notchFraction] of the width. */
class CandyTicketShape(
    private val cornerRadius: Dp = 26.dp,
    private val notchRadius: Dp = 11.dp,
    private val notchFraction: Float = 0.64f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val n = with(density) { notchRadius.toPx() }
        val cx = size.width * notchFraction
        val body = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), r, r))
        }
        val notches = Path().apply {
            addOval(Rect(Offset(cx, 0f), n))
            addOval(Rect(Offset(cx, size.height), n))
        }
        return Outline.Generic(Path().apply { op(body, notches, PathOperation.Difference) })
    }
}

/** Decorative candy bubbles painted behind content (static, cheap, clipped by the card). */
enum class CandyDecor { None, Bubbles, Corner }

private fun Modifier.candyDecor(decor: CandyDecor, tint: Color, isDark: Boolean): Modifier =
    if (decor == CandyDecor.None) this else drawBehind {
        val a1 = if (isDark) 0.22f else 0.30f
        val a2 = if (isDark) 0.14f else 0.20f
        when (decor) {
            CandyDecor.Bubbles -> {
                drawCircle(tint.copy(alpha = a1), radius = size.height * 0.55f, center = Offset(size.width * 1.02f, -size.height * 0.05f))
                drawCircle(tint.copy(alpha = a2), radius = size.height * 0.22f, center = Offset(size.width * 0.80f, size.height * 0.92f))
            }
            CandyDecor.Corner -> {
                drawCircle(tint.copy(alpha = a1), radius = size.minDimension * 0.42f, center = Offset(size.width, size.height))
            }
            CandyDecor.None -> Unit
        }
    }

/**
 * Bubble card: soft candy container with optional layered decor. Built on [SpatialGlassSurface]
 * so press squash, haptics, depth and content colour come for free.
 */
@Composable
fun CandyBubbleCard(
    tone: CandyTone,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SpatialTheme.geometry.radiusLg),
    decor: CandyDecor = CandyDecor.Bubbles,
    containerColor: Color? = null,
    sticker: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val t = candyTone(tone)
    val effectiveFill = if (isLiquidGlass && containerColor == null && tone != CandyTone.Neutral) {
        lerp(SpatialTheme.colors.glassSurface, t.fill, if (SpatialTheme.colors.isDark) 0.14f else 0.10f)
    } else {
        containerColor ?: t.container
    }
    SpatialGlassSurface(
        modifier = modifier,
        shape = shape,
        containerColor = effectiveFill,
        sticker = sticker && !isLiquidGlass,
        onClick = onClick,
        onClickLabel = onClickLabel
    ) {
        if (!isLiquidGlass) {
            Box(
                Modifier
                    .matchParentSize()
                    .candyDecor(decor, t.fill, SpatialTheme.colors.isDark)
            )
        }
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Squircle icon container, optionally tilted like a sticker (flat optical squircle in Liquid Glass). */
@Composable
fun CandyIconBlob(
    icon: ImageVector,
    tone: CandyTone,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tilt: Float = 0f,
    solid: Boolean = false
) {
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val t = candyTone(tone)
    val effectiveTilt = if (isLiquidGlass) 0f else tilt
    Box(
        modifier = modifier
            .size(size)
            .rotate(effectiveTilt)
            .clip(RoundedCornerShape(size * 0.36f))
            .background(if (solid) t.fill else t.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (solid) t.onFill else t.ink,
            modifier = Modifier
                .size(size * 0.5f)
                .rotate(-effectiveTilt)
        )
    }
}

/** Sticker label: bold outline + small hard offset shadow in Candy Bubble; crisp frosted pill in Liquid Glass. */
@Composable
fun CandySticker(
    text: String,
    tone: CandyTone,
    modifier: Modifier = Modifier,
    tilt: Float = -4f,
    leadingIcon: ImageVector? = null
) {
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val t = candyTone(tone)
    val colors = SpatialTheme.colors
    val ink = colors.sticker
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .then(
                if (isLiquidGlass) {
                    Modifier
                        .clip(shape)
                        .background(colors.glassSurfaceElevated.copy(alpha = if (colors.isDark) 0.72f else 0.85f))
                        .border(BorderStroke(1.dp, colors.glassBorder), shape)
                } else {
                    Modifier
                        .rotate(tilt)
                        .drawBehind {
                            val o = 2.dp.toPx()
                            val outline = shape.createOutline(size, layoutDirection, this)
                            translate(left = o, top = o) { drawOutline(outline, ink) }
                        }
                        .clip(shape)
                        .background(t.fill)
                        .border(BorderStroke(2.dp, ink), shape)
                }
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val labelColor = if (isLiquidGlass) colors.primaryText else t.onFill
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = labelColor, modifier = Modifier.size(14.dp))
        }
        Text(text = text, style = SpatialTheme.typography.caption.copy(fontFamily = SpatialTheme.typography.cardTitle.fontFamily), color = labelColor, maxLines = 1)
    }
}

/** Calm status badge: soft container + dot. For states that should not shout. */
@Composable
fun CandyStatusBadge(text: String, tone: CandyTone, modifier: Modifier = Modifier) {
    val t = candyTone(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(t.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(t.fill))
        Text(text = text, style = SpatialTheme.typography.caption, color = t.ink, maxLines = 1)
    }
}

/** Section header: display title, optional count bubble and a pill action with arrow. */
@Composable
fun CandySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = SpatialTheme.colors
    val type = SpatialTheme.typography
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = type.sectionHeading, color = c.primaryText, modifier = Modifier.semantics { heading() })
                if (count != null && count > 0) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.coral)
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) { Text("$count", style = type.caption, color = c.onCoral) }
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = type.supportingInfo, color = c.secondaryText)
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.glassSurfaceElevated)
                    .candyPressable(onClick = onAction, onClickLabel = actionLabel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(actionLabel, style = type.caption, color = c.primaryAction)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = c.primaryAction, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Mini calendar tile: coloured month band on top, big day number below. */
@Composable
fun CandyDateTile(epochDay: Long, tone: CandyTone, modifier: Modifier = Modifier) {
    val t = candyTone(tone)
    val c = SpatialTheme.colors
    val date = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
    val month = remember(date) { date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH) }
    Column(
        modifier = modifier
            .width(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.glassSurfaceElevated)
            .border(BorderStroke(1.5.dp, t.fill), RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().background(t.fill).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(month, style = SpatialTheme.typography.caption, color = t.onFill, maxLines = 1)
        }
        Text(
            "${date.dayOfMonth}",
            style = SpatialTheme.typography.cardBalance,
            color = c.primaryText,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

/** Chunky progress ring with rounded caps, drawn around [content]. */
@Composable
fun CandyProgressRing(
    fraction: Float,
    tone: CandyTone,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    stroke: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val t = candyTone(tone)
    val track = SpatialTheme.colors.glassSurfaceElevated
    val motion = SpatialTheme.motion
    // Springs only when the value changes (e.g. funds added); first display shows the real value.
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = prismaticSpring(rememberReducedMotionEnabled(), motion.spatialDampingRatio, Spring.StiffnessLow),
        label = "candy_ring"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (shown > 0f) drawArc(t.fill, -90f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Vertical dashed perforation line (used by ticket cards). */
@Composable
fun CandyPerforation(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(2.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        )
    }
}

private val MoneyPattern = Regex("""^([+\-−]?RM\s?)(.*?)(\.\d{2})?$""")

/**
 * Splits a formatted MYR string ("RM 1,838.00", "-RM 5.00", masked "RM •••••") so the
 * currency and sen render smaller than the whole amount. Pure formatting; the text is unchanged.
 */
fun candyMoneyAnnotated(text: String, minorScale: Float = 0.5f, baseSize: TextUnit): AnnotatedString {
    val m = MoneyPattern.matchEntire(text) ?: return AnnotatedString(text)
    val (prefix, whole, cents) = m.destructured
    val small = SpanStyle(fontSize = baseSize * minorScale)
    return buildAnnotatedString {
        withStyle(small) { append(prefix) }
        append(whole)
        if (cents.isNotEmpty()) withStyle(SpanStyle(fontSize = baseSize * (minorScale + 0.12f))) { append(cents) }
    }
}

/** Money text with candy split typography (small "RM" and sen, big whole amount). */
@Composable
fun CandyMoneyText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minorScale: Float = 0.6f
) {
    Text(
        text = candyMoneyAnnotated(text, minorScale, style.fontSize),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}


/** Shows a brief success sticker. Call only after an operation has actually succeeded. */
val LocalCandyCelebrate = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun CandySuccessHost(content: @Composable () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var showToken by remember { mutableIntStateOf(0) }
    val reduced = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    val celebrate: (String) -> Unit = remember {
        { text ->
            message = text
            showToken++
        }
    }
    LaunchedEffect(showToken) {
        if (message != null) {
            haptic(HapticFeedbackType.Confirm)
            delay(1800)
            message = null
        }
    }
    CompositionLocalProvider(LocalCandyCelebrate provides celebrate) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = message != null,
                enter = if (reduced) EnterTransition.None else slideInVertically(tween(SpatialTheme.motion.durationStandardMs)) { -it } +
                    fadeIn(tween(SpatialTheme.motion.durationFastMs)) + scaleIn(initialScale = 0.9f),
                exit = if (reduced) ExitTransition.None else slideOutVertically(tween(SpatialTheme.motion.durationStandardMs)) { -it } +
                    fadeOut(tween(SpatialTheme.motion.durationFastMs)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                CandySticker(
                    text = message.orEmpty(),
                    tone = CandyTone.Mint,
                    tilt = -2f,
                    leadingIcon = Icons.Rounded.CheckCircle,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}


/**
 * Mint Bubble header button (Phase 11E/11F): 48dp mint squircle with a deep-teal outline.
 * Squishes and tilts its icon while pressed, springs back. No motion under Reduce Motion.
 */
@Composable
fun CandyMintIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressedTilt: Float = 30f
) {
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val colors = SpatialTheme.colors
    val motion = SpatialTheme.motion
    val reduced = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec = prismaticSpring<Float>(reduced, motion.pressDampingRatio, motion.pressStiffness)
    val scale by animateFloatAsState(
        if (pressed && !reduced) (if (isLiquidGlass) 1.06f else 0.92f) else 1f,
        spec,
        label = "mint_btn_scale"
    )
    val tilt by animateFloatAsState(
        if (pressed && !reduced && !isLiquidGlass) pressedTilt else 0f,
        spec,
        label = "mint_btn_tilt"
    )
    val shape = if (isLiquidGlass) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(16.dp)
    val bg = if (isLiquidGlass) {
        colors.glassSurfaceElevated.copy(alpha = if (colors.isDark) 0.72f else 0.84f)
    } else {
        colors.aqua
    }
    val borderStroke = if (isLiquidGlass) {
        BorderStroke(1.dp, colors.glassBorder)
    } else {
        BorderStroke(2.dp, colors.sticker)
    }
    val iconTint = if (isLiquidGlass) colors.primaryText else colors.onCoral

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .border(borderStroke, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription
            ) {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = tilt }
        )
    }
}
