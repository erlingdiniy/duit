package com.diniy.duit.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.diniy.duit.R
import com.diniy.duit.domain.model.ThemeMode

/**
 * Widget 01: Safe to Spend (Candy Bubble 2.0 reference card).
 * The bar shows a real metric: the share of total balance that is Safe to Spend.
 */
class DuitBalanceWidget : DuitGlanceWidgetBase() {

    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val context = LocalContext.current
        val size = LocalSize.current
        val w = size.width.value
        val h = size.height.value
        val balance = snapshot.balance
        val compact = h < 100f
        val narrow = !compact && w < 200f
        val pad = if (compact) 12.dp else 16.dp
        val (bgRes, trackRes) = when (snapshot.themeMode) {
            ThemeMode.LIGHT -> R.drawable.bg_sts_light to R.drawable.bg_sts_bar_track_light
            ThemeMode.DARK -> R.drawable.bg_sts_dark to R.drawable.bg_sts_bar_track_dark
            ThemeMode.SYSTEM -> R.drawable.bg_sts_auto to R.drawable.bg_sts_bar_track_auto
        }

        val safeText = snapshot.formatMoney(balance.safeToSpendSen)
        val fraction = if (balance.totalBalanceSen > 0L) {
            (balance.safeToSpendSen.coerceAtLeast(0L).toFloat() / balance.totalBalanceSen).coerceIn(0f, 1f)
        } else 0f
        val percent = (fraction * 100).toInt()
        val caption = when {
            balance.isEmptyLedger -> "Your wallet story starts here."
            snapshot.shouldMaskAmounts -> "Amounts hidden for privacy"
            else -> "$percent% of your balance is free to spend"
        }

        // Illustration takes a real share of the card but never squeezes the amount.
        val artSize = when {
            compact && w < 250f -> 0f
            compact -> (h - 8f).coerceIn(56f, 80f)
            narrow -> minOf(w * 0.66f, (h - pad.value * 2 - 118f) / 0.86f).let { if (it < 50f) 0f else it }
            else -> minOf(h * 1.0f, w * 0.43f, 200f)
        }
        val amountSp = when {
            compact -> 24f
            narrow -> if (safeText.length > 11) 20f else 24f
            safeText.length > 13 -> 26f
            h >= 165f -> 40f
            else -> 32f
        }
        val barWidth = if (narrow) w - pad.value * 2 else (w - pad.value * 2 - artSize * 0.80f).coerceAtLeast(80f)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(bgRes))
                .clickable(actionStartActivity(WidgetDeepLink.createLaunchIntent(context, WidgetDeepLink.HOME)))
                .semantics {
                    contentDescription = "Safe to Spend ${snapshot.accessibleMoney(balance.safeToSpendSen)}. $caption. Tap to open duit."
                }
        ) {
            // Layer 1: coin buddy, bottom-right, sitting on the bubble.
            if (artSize > 0f) {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(end = 8.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = GlanceModifier.size(artSize.dp, (artSize * 0.86f).dp).background(ImageProvider(R.drawable.bg_bubble_coral)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(ImageProvider(R.drawable.ic_candy_coin_buddy), null, GlanceModifier.size(artSize.dp, (artSize * 0.86f).dp))
                    }
                }
            }
            // Layer 2: text and bar.
            Column(modifier = GlanceModifier.fillMaxSize().padding(pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_candy_face_badge), null, GlanceModifier.size(if (compact) 18.dp else 22.dp))
                    Spacer(GlanceModifier.width(7.dp))
                    Text(
                        "Safe to Spend",
                        maxLines = 1,
                        style = TextStyle(color = theme.textPrimary, fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Image(ImageProvider(R.drawable.ic_candy_sparkle), null, GlanceModifier.size(14.dp))
                    if (snapshot.isAppLockEnabled) {
                        Spacer(GlanceModifier.width(4.dp))
                        Image(ImageProvider(R.drawable.ic_solar_lock), "Protected by App Lock", GlanceModifier.size(12.dp))
                    }
                }
                Spacer(if (compact) GlanceModifier.defaultWeight() else GlanceModifier.height(if (h >= 165f) 18.dp else 8.dp))
                Text(
                    text = if (balance.isEmptyLedger) "Add a wallet" else safeText,
                    maxLines = 1,
                    // HyperOS ignores synthetic bold on its default font; the black family gives a true heavy weight.
                    style = TextStyle(color = theme.textPrimary, fontSize = amountSp.sp, fontWeight = FontWeight.Bold, fontFamily = HeavyFont)
                )
                if (!compact) {
                    if (narrow) Spacer(GlanceModifier.height(8.dp)) else Spacer(GlanceModifier.defaultWeight())
                    Box(modifier = GlanceModifier.width(barWidth.dp).height(12.dp).background(ImageProvider(trackRes))) {
                        val fillDp = barWidth * if (snapshot.shouldMaskAmounts) 0f else fraction
                        if (fillDp >= 12f) {
                            Spacer(GlanceModifier.width(fillDp.dp).fillMaxHeight().background(ImageProvider(R.drawable.bg_sts_bar_fill)))
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Text(caption, maxLines = if (narrow) 2 else 1, style = TextStyle(color = theme.textSecondary, fontSize = if (narrow) 11.sp else 12.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

private val HeavyFont = androidx.glance.text.FontFamily("sans-serif-black")
private val BoldFont = androidx.glance.text.FontFamily("sans-serif-medium")

class DuitBalanceWidgetReceiver : DuitGlanceWidgetReceiver(DuitBalanceWidget())
