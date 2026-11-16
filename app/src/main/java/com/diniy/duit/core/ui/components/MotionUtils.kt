package com.diniy.duit.core.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.diniy.duit.domain.model.AnimationIntensity

data class MotionPreferences(
    val reduceMotion: Boolean = false,
    val animationIntensity: AnimationIntensity = AnimationIntensity.BALANCED,
    val hapticFeedbackEnabled: Boolean = true
)

val LocalMotionPreferences = staticCompositionLocalOf { MotionPreferences() }

/**
 * Checks whether reduced motion is active via system animator scale OR app Reduce Motion preference.
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val appMotion = LocalMotionPreferences.current
    val systemReduced = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    return systemReduced || appMotion.reduceMotion
}

@Composable
fun rememberDuitHaptic(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalMotionPreferences.current.hapticFeedbackEnabled
    return remember(haptic, enabled) {
        { type ->
            if (enabled) {
                haptic.performHapticFeedback(type)
            }
        }
    }
}

fun <T> prismaticSpring(
    reducedMotion: Boolean,
    dampingRatio: Float = 0.82f,
    stiffness: Float = Spring.StiffnessMediumLow
): FiniteAnimationSpec<T> {
    return if (reducedMotion) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

fun <T> prismaticTween(
    reducedMotion: Boolean,
    durationMillis: Int = 220,
    intensity: AnimationIntensity = AnimationIntensity.BALANCED
): FiniteAnimationSpec<T> {
    if (reducedMotion) return snap()
    val scaled = (durationMillis * intensity.durationScale).toInt().coerceIn(80, 450)
    return tween(durationMillis = scaled)
}
