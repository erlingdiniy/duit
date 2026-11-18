package com.diniy.duit.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.util.lerp
import com.diniy.duit.core.ui.theme.CandyDisplayFamily
import com.diniy.duit.core.ui.theme.PrismaticPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// Phase 10B timeline (ms). Coin jump -> drop -> wallet squish -> zoom-out -> wordmark -> hold -> exit.
private const val T_COIN_END = 660f
private const val T_DROP_END = 900f
private const val T_SQUISH_END = 1260f // bounce fully settles before the zoom starts
private const val T_ZOOM_START = 1280f
private const val T_ZOOM_END = 1700f
private const val T_WORD_START = 1480f
private const val T_WORD_END = 1880f
private const val T_DOT_START = 1820f
private const val T_DOT_END = 2020f
private const val T_HOLD_END = 2200f // hold the finished lockup
private const val T_EXIT_END = 2420f
private const val T_DECOR_END = 800f

// Smooth, symmetric ease for the coin arc (no sharp start).
private val CoinEasing = androidx.compose.animation.core.CubicBezierEasing(0.37f, 0f, 0.25f, 1f)

private val Cream = PrismaticPalette.Cream
// Phase 10B.3 dark splash background (matches values-night splash_cream)
private val NightBackground = Color(0xFF17262B)
private val Coral = PrismaticPalette.Coral
private val Mint = PrismaticPalette.Mint
private val Sunny = PrismaticPalette.Sunny
private val Teal = PrismaticPalette.DeepTeal

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

/**
 * Candy Bubble animated splash (Phase 10B): an RM coin jumps into a mint wallet, the wallet
 * squishes, zooms out left, and the "duit." wordmark slides in with a popping coral dot.
 * Drawn on one Canvas from a single clock (read only in draw/layer lambdas, so no per-frame
 * recomposition). Blocks touches while visible, then fades out and calls [onFinished].
 * Under Reduce Motion it shows the final lockup briefly and fades.
 */
@Composable
fun CandySplash(onFinished: () -> Unit, modifier: Modifier = Modifier, isDark: Boolean = false) {
    val reduced = rememberReducedMotionEnabled()
    val clock = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        if (reduced) {
            clock.snapTo(T_DOT_END)
            delay(350)
            clock.animateTo(T_EXIT_END, tween((T_EXIT_END - T_DOT_END).toInt(), easing = LinearEasing))
        } else {
            clock.animateTo(T_EXIT_END, tween(T_EXIT_END.toInt(), easing = LinearEasing))
        }
        finish()
    }

    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - seg(clock.value, T_HOLD_END, T_EXIT_END) }
            .pointerInput(Unit) {
                // Swallow touches so nothing underneath is tapped during the splash.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .semantics { contentDescription = "duit." }
    ) {
        val density = LocalDensity.current
        val minPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val walletW = minPx * 0.46f
        val wordStyle = TextStyle(
            fontFamily = CandyDisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { (walletW * 0.40f).toSp() },
            // Dark splash: cream wordmark on #17262B; light splash: deep-teal wordmark on cream.
            color = if (isDark) Cream else Teal
        )
        val word = remember(walletW, isDark) { measurer.measure("duit", wordStyle) }
        val dot = remember(walletW) { measurer.measure(".", wordStyle.copy(color = Coral)) }
        val rm = remember(walletW) {
            measurer.measure(
                "RM",
                wordStyle.copy(fontSize = with(density) { (walletW * 0.11f).toSp() }, color = Teal)
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawSplash(clock.value, walletW, word, dot, rm, if (isDark) NightBackground else Cream)
        }
    }
}

private fun DrawScope.drawSplash(t: Float, wW: Float, word: TextLayoutResult, dot: TextLayoutResult, rm: TextLayoutResult, background: Color) {
    val wH = wW * 0.7f
    val sw = wW * 0.045f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val minDim = size.minDimension

    drawRect(background)
    // Soft corner bubbles: always drawn, eased from 0 opacity while sliding in from off-screen.
    val decor = FastOutSlowInEasing.transform(seg(t, 0f, T_DECOR_END))
    val blobA = decor * 0.9f
    val slide = (1f - decor) * minDim * 0.08f
    drawCircle(Coral.copy(alpha = blobA), radius = minDim * 0.26f, center = Offset(-slide, -slide))
    drawCircle(Sunny.copy(alpha = blobA), radius = minDim * 0.20f, center = Offset(-slide, size.height + slide))
    drawCircle(Mint.copy(alpha = blobA), radius = minDim * 0.22f, center = Offset(size.width + slide, size.height + slide))

    // Zoom-out + lockup geometry
    val finalScale = 0.5f
    val gap = wW * 0.10f
    val textW = word.size.width + dot.size.width
    val lockW = wW * finalScale + gap + textW
    val finalWalletCx = cx - lockW / 2f + wW * finalScale / 2f
    val zoom = FastOutSlowInEasing.transform(seg(t, T_ZOOM_START, T_ZOOM_END))
    val walletCx = lerp(cx, finalWalletCx, zoom)
    val walletScale = lerp(1f, finalScale, zoom)

    // Wallet squish after the coin lands (damped), pivot at the wallet base
    val sq = seg(t, T_DROP_END - 60f, T_SQUISH_END)
    val d = sin(sq * PI.toFloat() * 2.5f) * (1f - sq) * (1f - sq)
    val squishX = 1f + 0.09f * d
    val squishY = 1f - 0.11f * d

    translate(walletCx, cy) {
        scale(walletScale * squishX, walletScale * squishY, pivot = Offset(0f, wH * 0.5f)) {
            rotate(-6f, pivot = Offset.Zero) {
                // Back card (coral), peeking out of the wallet
                rotate(-14f, pivot = Offset.Zero) {
                    val tl = Offset(-0.44f * wW, -0.62f * wH)
                    val sz = Size(0.86f * wW, 0.75f * wH)
                    drawRoundRect(Coral, tl, sz, CornerRadius(0.12f * wW))
                    drawRoundRect(Teal, tl, sz, CornerRadius(0.12f * wW), style = Stroke(sw))
                }

                // Coin: curved jump, spin + squash/stretch, then drop behind the wallet front
                if (t < T_DROP_END) {
                    val p = CoinEasing.transform(seg(t, 0f, T_COIN_END))
                    val p0 = Offset(-1.9f * wW, -2.0f * wH)
                    val c = Offset(-0.7f * wW, -2.9f * wH)
                    val p1 = Offset(0f, -0.62f * wH)
                    val u = 1f - p
                    var pos = Offset(
                        u * u * p0.x + 2 * u * p * c.x + p * p * p1.x,
                        u * u * p0.y + 2 * u * p * c.y + p * p * p1.y
                    )
                    val q = seg(t, T_COIN_END, T_DROP_END)
                    pos = pos.copy(y = pos.y + q * q * 0.75f * wH)
                    val stretch = sin(p * PI.toFloat()) * (1f - q)
                    val land = if (q in 0f..0.3f) sin(q / 0.3f * PI.toFloat()) else 0f
                    val sx = 1f - 0.10f * stretch + 0.12f * land
                    val sy = 1f + 0.10f * stretch - 0.12f * land
                    val r = 0.16f * wW
                    translate(pos.x, pos.y) {
                        rotate(360f * p, pivot = Offset.Zero) {
                            scale(sx, sy, pivot = Offset.Zero) {
                                drawCircle(Sunny, r, Offset.Zero)
                                drawCircle(Teal, r, Offset.Zero, style = Stroke(sw * 0.8f))
                                drawCircle(Teal.copy(alpha = 0.25f), r * 0.72f, Offset.Zero, style = Stroke(sw * 0.4f))
                                drawText(rm, topLeft = Offset(-rm.size.width / 2f, -rm.size.height / 2f))
                            }
                        }
                    }
                }

                // Wallet front (mint) hides the coin as it drops in
                val ftl = Offset(-0.5f * wW, -0.33f * wH)
                val fsz = Size(wW, 0.83f * wH)
                drawRoundRect(Mint, ftl, fsz, CornerRadius(0.16f * wW))
                drawRoundRect(Teal, ftl, fsz, CornerRadius(0.16f * wW), style = Stroke(sw))
                val clasp = Offset(0.12f * wW, 0.10f * wH)
                drawCircle(Sunny, 0.095f * wW, clasp)
                drawCircle(Teal, 0.095f * wW, clasp, style = Stroke(sw * 0.7f))

                // Sparkle accents pop when the coin lands
                val sp = seg(t, T_DROP_END, T_DROP_END + 240f)
                if (sp > 0f) {
                    val s = easeOutBack(sp)
                    val base = Offset(0.56f * wW, -0.62f * wH)
                    listOf(Offset(-0.08f, -0.22f), Offset(0.12f, -0.10f), Offset(0.16f, 0.08f)).forEach { dir ->
                        val start = base + Offset(dir.x * wW * 0.5f, dir.y * wW * 0.5f)
                        val end = start + Offset(dir.x * wW * 0.5f * s, dir.y * wW * 0.5f * s)
                        drawLine(Sunny.copy(alpha = sp), start, end, strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }

    // Wordmark: slide + fade in to the right, coral dot pops last
    val wp = FastOutSlowInEasing.transform(seg(t, T_WORD_START, T_WORD_END))
    if (wp > 0f) {
        val textLeft = finalWalletCx + wW * finalScale / 2f + gap + (1f - wp) * wW * 0.25f
        val top = cy - word.size.height / 2f
        drawText(word, topLeft = Offset(textLeft, top), alpha = wp)
        val dp = seg(t, T_DOT_START, T_DOT_END)
        if (dp > 0f) {
            val dotLeft = textLeft + word.size.width
            val pivot = Offset(dotLeft + dot.size.width / 2f, top + dot.size.height * 0.78f)
            scale(easeOutBack(dp), pivot = pivot) {
                drawText(dot, topLeft = Offset(dotLeft, top), alpha = dp.coerceAtMost(1f))
            }
        }
    }
}
