package com.diniy.duit.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.privacySafeAmountSemantics
import com.diniy.duit.core.ui.theme.SpatialTheme
import kotlin.math.roundToLong

/**
 * Reusable spring-based press scale feedback modifier (Phase 6A Section 9).
 * Automatically disables scale transforms when [rememberReducedMotionEnabled] is active.
 */
fun Modifier.spatialPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.985f
): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val motion = SpatialTheme.motion
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = motion.resolvePressScale(
            isPressed = isPressed,
            enabled = enabled,
            reduceMotion = reducedMotion,
            targetScale = pressedScale
        ),
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "spatial_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Motion-ready financial counter composable (Phase 6A Sections 9 & 10).
 * - Uses tabular numerals (`tnum`) to prevent horizontal jitter.
 * - Animates smoothly between monetary values when Reduce Motion is OFF, and snaps when ON.
 * - Strictly respects privacy masking: when hidden, it NEVER animates or exposes intermediate
 *   numeric values visually or in TalkBack semantics.
 */
@Composable
fun SpatialAnimatedCounterText(
    amountSen: Long,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = "",
    style: TextStyle = SpatialTheme.typography.heroBalance,
    color: Color = SpatialTheme.colors.primaryText,
    isBalanceMask: Boolean = true,
    includePlusSign: Boolean = false,
    /** When set, renders "RM" and sen at this fraction of the size (Candy Bubble money type). */
    splitMinorScale: Float? = null
) {
    val privacySettings = LocalPrivacySettings.current
    val reducedMotion = rememberReducedMotionEnabled()
    val motionPrefs = LocalMotionPreferences.current
    val motionTokens = SpatialTheme.motion

    val isMasked = if (isBalanceMask) {
        privacySettings.isAnyMaskActive
    } else {
        privacySettings.privacyModeEnabled
    }

    val animatedSen = remember { Animatable(amountSen.toFloat()) }

    LaunchedEffect(amountSen, isMasked, reducedMotion, motionPrefs.animationIntensity) {
        if (isMasked || reducedMotion) {
            animatedSen.snapTo(amountSen.toFloat())
        } else {
            val duration = motionTokens.resolveDurationMs(
                baseDurationMs = motionTokens.durationCounterMs,
                reduceMotion = false,
                intensity = motionPrefs.animationIntensity
            )
            animatedSen.animateTo(
                targetValue = amountSen.toFloat(),
                animationSpec = prismaticTween(
                    reducedMotion = false,
                    durationMillis = duration,
                    intensity = motionPrefs.animationIntensity
                )
            )
        }
    }

    // Phase 8B: money is shown at its real value immediately (no counting through
    // intermediate, misleading amounts). The entrance motion lives on the card instead.
    val displaySen = amountSen
    val visibleFormatted = MyrFormatter.format(displaySen, includePlusSign = includePlusSign)
    val finalFormattedTarget = MyrFormatter.format(amountSen, includePlusSign = includePlusSign)
    val renderedText = if (isMasked) PrivacyMask.MASKED_MYR else visibleFormatted

    Text(
        text = if (splitMinorScale != null) {
            candyMoneyAnnotated(renderedText, splitMinorScale, style.fontSize)
        } else {
            androidx.compose.ui.text.AnnotatedString(renderedText)
        },
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.privacySafeAmountSemantics(
            label = accessibilityLabel,
            isMasked = isMasked,
            visibleFormattedAmount = finalFormattedTarget
        )
    )
}

/**
 * Motion-ready morphing container prepared for Phase 6C card/sheet morph transitions.
 */
@Composable
fun SpatialMorphingContainer(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    collapsedRadius: Dp = SpatialTheme.geometry.radiusLg,
    expandedRadius: Dp = SpatialTheme.geometry.radiusXl,
    accentTint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val motion = SpatialTheme.motion
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) expandedRadius else collapsedRadius,
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.morphDampingRatio,
            stiffness = motion.morphStiffness
        ),
        label = "spatial_morph_radius"
    )

    val sizeModifier = if (reducedMotion) {
        modifier
    } else {
        modifier.animateContentSize(
            animationSpec = prismaticSpring(
                reducedMotion = false,
                dampingRatio = motion.morphDampingRatio,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    SpatialGlassSurface(
        modifier = sizeModifier,
        tier = if (expanded) SpatialGlassTier.Elevated else SpatialGlassTier.Standard,
        shape = RoundedCornerShape(cornerRadius),
        accentTint = accentTint,
        selected = expanded,
        onClick = onClick,
        content = content
    )
}

val SpatialSharedTransitionKeyProperty = SemanticsPropertyKey<String>("SpatialSharedTransitionKey")
var SemanticsPropertyReceiver.spatialSharedTransitionKey by SpatialSharedTransitionKeyProperty

/**
 * Tags a composable with a stable shared-element key in preparation for Phase 6C spatial transitions.
 */
fun Modifier.spatialSharedTransitionReady(key: String): Modifier =
    this.semantics { spatialSharedTransitionKey = key }
