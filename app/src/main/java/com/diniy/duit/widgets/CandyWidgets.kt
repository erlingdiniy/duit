package com.diniy.duit.widgets

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.ColumnScope
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.diniy.duit.R
import com.diniy.duit.domain.model.ThemeMode
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------
// Candy Bubble 2.0 widget kit (Phase 11N). Same recipe as the approved Safe to Spend card:
// tone background with bubbles, illustration layer, then a content column on top.
// ---------------------------------------------------------------------------------------------

enum class CandyWidgetTone { MINT, PEACH, YELLOW, CREAM, TEAL }

private fun byMode(mode: ThemeMode, light: Int, dark: Int, auto: Int) = when (mode) {
    ThemeMode.LIGHT -> light
    ThemeMode.DARK -> dark
    ThemeMode.SYSTEM -> auto
}

internal fun candyBackground(tone: CandyWidgetTone, mode: ThemeMode): Int = when (tone) {
    CandyWidgetTone.MINT -> byMode(mode, R.drawable.bg_candy_mint_light, R.drawable.bg_candy_mint_dark, R.drawable.bg_candy_mint_auto)
    CandyWidgetTone.PEACH -> byMode(mode, R.drawable.bg_candy_peach_light, R.drawable.bg_candy_peach_dark, R.drawable.bg_candy_peach_auto)
    CandyWidgetTone.YELLOW -> byMode(mode, R.drawable.bg_candy_yellow_light, R.drawable.bg_candy_yellow_dark, R.drawable.bg_candy_yellow_auto)
    CandyWidgetTone.CREAM -> byMode(mode, R.drawable.bg_candy_cream_light, R.drawable.bg_candy_cream_dark, R.drawable.bg_candy_cream_auto)
    CandyWidgetTone.TEAL -> R.drawable.bg_candy_teal
}

private fun trackRes(mode: ThemeMode) =
    byMode(mode, R.drawable.bg_sts_bar_track_light, R.drawable.bg_sts_bar_track_dark, R.drawable.bg_sts_bar_track_auto)

private fun rowRes(mode: ThemeMode) =
    byMode(mode, R.drawable.bg_candy_row, R.drawable.bg_candy_row_dark, R.drawable.bg_candy_row_auto)

internal val CandyHeavy = FontFamily("sans-serif-black")
internal val CandyMedium = FontFamily("sans-serif-medium")

/** Real measured size plus derived layout numbers, handed to each widget's content. */
data class CandyDims(val w: Float, val h: Float, val compact: Boolean, val artW: Float, val pad: Float) {
    val innerW: Float get() = w - pad * 2
    /** 2x2-style cell: content stacks at the top and the illustration sits below it. */
    val narrow: Boolean get() = !compact && w < 200f
}

/** Flexible gap on wide cards; a small fixed gap on narrow ones so content never runs into the art. */
@Composable
internal fun ColumnScope.CandyPush(d: CandyDims) {
    if (d.narrow) Spacer(GlanceModifier.height(6.dp)) else Spacer(GlanceModifier.defaultWeight())
}

private fun bubbleRes(tone: CandyWidgetTone) = when (tone) {
    CandyWidgetTone.MINT -> R.drawable.bg_bubble_mint
    CandyWidgetTone.PEACH -> R.drawable.bg_bubble_peach
    CandyWidgetTone.YELLOW -> R.drawable.bg_bubble_yellow
    CandyWidgetTone.CREAM -> R.drawable.bg_bubble_coral
    CandyWidgetTone.TEAL -> R.drawable.bg_bubble_teal
}

@Composable
internal fun CandyCard(
    snapshot: DuitWidgetsSnapshot,
    tone: CandyWidgetTone,
    deepLink: WidgetDeepLink?,
    a11y: String,
    @DrawableRes art: Int?,
    artAspect: Float = 100f / 120f,
    artShare: Float = 0.42f,
    artTop: Boolean = false,
    /** Height (dp) the content needs above the illustration on narrow 2x2 cells. */
    narrowReserve: Float = 112f,
    content: @Composable ColumnScope.(CandyDims) -> Unit
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val w = size.width.value
    val h = size.height.value
    val compact = h < 100f
    val pad = if (compact || w < 200f) 12f else 16f
    // Illustration is part of the composition, but steps aside on cells too small to hold it.
    val artW = when {
        art == null -> 0f
        compact -> if (w >= 250f) ((h - 10f) / artAspect).coerceAtMost(w * 0.3f) else 0f
        artTop -> minOf(h * 0.42f / artAspect, w * artShare)
        w < 200f -> minOf(w * 0.62f, (h - pad * 2 - narrowReserve) / artAspect).let { if (it < 48f) 0f else it }
        else -> minOf(h * 0.95f / artAspect, w * artShare, 200f)
    }
    var modifier = GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(candyBackground(tone, snapshot.themeMode)))
        .semantics { contentDescription = a11y }
    if (deepLink != null) {
        modifier = modifier.clickable(actionStartActivity(WidgetDeepLink.createLaunchIntent(context, deepLink)))
    }
    Box(modifier = modifier) {
        if (artW > 0f && art != null) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(end = 8.dp, top = if (artTop) 8.dp else 0.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.End,
                verticalAlignment = if (artTop) Alignment.Top else Alignment.Bottom
            ) {
                // The bubble scales with the art, so small cells are not swamped by a fixed-size blob.
                Box(
                    modifier = GlanceModifier.size(artW.dp, (artW * artAspect).dp).background(ImageProvider(bubbleRes(tone))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(ImageProvider(art), null, GlanceModifier.size(artW.dp, (artW * artAspect).dp))
                }
            }
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(pad.dp)) {
            content(CandyDims(w, h, compact, artW, pad))
        }
    }
}

@Composable
internal fun CandyHeader(
    title: String,
    color: ColorProvider,
    @DrawableRes icon: Int? = null,
    locked: Boolean = false,
    sparkle: Boolean = false,
    small: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(ImageProvider(icon), null, GlanceModifier.size(if (small) 18.dp else 22.dp))
            Spacer(GlanceModifier.width(7.dp))
        }
        Text(
            title,
            maxLines = 1,
            style = TextStyle(color = color, fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Bold, fontFamily = CandyMedium)
        )
        if (sparkle) {
            Spacer(GlanceModifier.width(4.dp))
            Image(ImageProvider(R.drawable.ic_candy_sparkle), null, GlanceModifier.size(13.dp))
        }
        if (locked) {
            Spacer(GlanceModifier.width(4.dp))
            Image(ImageProvider(R.drawable.ic_solar_lock), "Protected by App Lock", GlanceModifier.size(12.dp))
        }
    }
}

@Composable
internal fun CandyBig(text: String, color: ColorProvider, sizeSp: Float) {
    Text(text, maxLines = 1, style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, fontFamily = CandyHeavy))
}

@Composable
internal fun CandySmall(text: String, color: ColorProvider, sizeSp: Float = 12f, maxLines: Int = 1) {
    Text(text, maxLines = maxLines, style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Medium))
}

@Composable
internal fun CandyBar(mode: ThemeMode, widthDp: Float, fraction: Float, @DrawableRes fill: Int, heightDp: Float = 12f) {
    Box(modifier = GlanceModifier.width(widthDp.dp).height(heightDp.dp).background(ImageProvider(trackRes(mode)))) {
        val fillW = widthDp * fraction.coerceIn(0f, 1f)
        if (fillW >= heightDp) {
            Spacer(GlanceModifier.width(fillW.dp).fillMaxHeight().background(ImageProvider(fill)))
        }
    }
}

private fun bigSize(d: CandyDims, text: String): Float = when {
    d.compact -> 22f
    d.w < 200f -> if (text.length > 10) 20f else 24f
    text.length > 13 -> 26f
    d.h >= 165f -> 36f
    else -> 30f
}

private fun dueLabel(dueEpochDay: Long, overdue: Boolean): String {
    val days = dueEpochDay - LocalDate.now().toEpochDay()
    return when {
        overdue || days < 0 -> "Overdue"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due in $days days"
    }
}

// ------------------------------------- 02 Wallet Snapshot -------------------------------------
class DuitWalletsWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val data = snapshot.walletsData
        CandyCard(
            snapshot, CandyWidgetTone.MINT, WidgetDeepLink.HOME,
            "My Wallets. Total ${snapshot.accessibleMoney(data.totalBalanceSen)}. Tap to open duit.",
            R.drawable.ic_candy_art_wallet, artShare = 0.30f, artTop = true
        ) { d ->
            CandyHeader("My Wallets", theme.textPrimary, R.drawable.ic_candy_art_wallet.takeIf { d.artW == 0f }, snapshot.isAppLockEnabled, small = d.compact)
            Spacer(GlanceModifier.height(if (d.compact) 2.dp else 8.dp))
            if (data.wallets.isEmpty()) {
                CandyBig("Add a wallet", theme.textPrimary, 20f)
                CandySmall("Your wallet story starts here.", theme.textSecondary)
                return@CandyCard
            }
            val total = snapshot.formatMoney(data.totalBalanceSen)
            CandyBig(total, theme.textPrimary, bigSize(d, total))
            val rows = when {
                d.compact -> 0
                d.h >= 230f -> 3
                d.h >= 150f -> 2
                else -> 0
            }
            if (rows > 0) {
                Spacer(GlanceModifier.defaultWeight())
                data.wallets.take(rows).forEach { wallet ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().background(ImageProvider(rowRes(snapshot.themeMode)))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(wallet.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        Text(snapshot.formatMoney(wallet.balanceSen), maxLines = 1,
                            style = TextStyle(color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
            } else if (!d.compact) {
                Spacer(GlanceModifier.defaultWeight())
                CandySmall("${data.wallets.size} wallet${if (data.wallets.size == 1) "" else "s"}", theme.textSecondary)
            }
        }
    }
}

class DuitWalletsWidgetReceiver : DuitGlanceWidgetReceiver(DuitWalletsWidget())

// ---------------------------------------- 03 Quick Add ----------------------------------------
class DuitQuickAddWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val context = LocalContext.current
        // No root tap target: each bubble opens its own editor (after App Lock); nothing is saved from here.
        CandyCard(snapshot, CandyWidgetTone.PEACH, null, "Quick Add. Expense, Income or Transfer.", null) { d ->
            if (!d.compact) {
                CandyHeader("Quick Add", theme.textPrimary, R.drawable.ic_candy_plus_badge, snapshot.isAppLockEnabled, small = d.w < 200f)
                Spacer(GlanceModifier.defaultWeight())
            }
            val btn = minOf(d.innerW / 3f - 10f, d.h - (if (d.compact) 40f else 78f), 64f).coerceAtLeast(30f)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Triple("Expense", R.drawable.ic_qa_expense, WidgetDeepLink.QUICK_ADD_EXPENSE),
                    Triple("Income", R.drawable.ic_qa_income, WidgetDeepLink.QUICK_ADD_INCOME),
                    Triple("Transfer", R.drawable.ic_qa_transfer, WidgetDeepLink.QUICK_ADD_TRANSFER)
                ).forEach { (label, icon, link) ->
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                            .clickable(actionStartActivity(WidgetDeepLink.createLaunchIntent(context, link)))
                            .semantics { contentDescription = "Add $label" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(ImageProvider(icon), null, GlanceModifier.size(btn.dp))
                        Spacer(GlanceModifier.height(4.dp))
                        Text(label, maxLines = 1, style = TextStyle(color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
            if (!d.compact) Spacer(GlanceModifier.defaultWeight())
        }
    }
}

class DuitQuickAddWidgetReceiver : DuitGlanceWidgetReceiver(DuitQuickAddWidget())

// --------------------------------------- 04 Savings Goal --------------------------------------
class DuitSavingsGoalsWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val goal = snapshot.savingsGoals.goals.firstOrNull()
        CandyCard(
            snapshot, CandyWidgetTone.MINT, WidgetDeepLink.PLANNING_GOALS,
            if (goal == null) "Savings Goal. No goal yet. Tap to create one."
            else "Savings Goal ${goal.name}, ${snapshot.formatPercent(goal.progressPercentDisplay)} saved. Tap to open goals.",
            R.drawable.ic_candy_art_palm
        ) { d ->
            CandyHeader(goal?.name ?: "Savings Goal", theme.textPrimary, R.drawable.ic_candy_art_palm.takeIf { d.artW == 0f }, snapshot.isAppLockEnabled, small = d.compact)
            if (goal == null) {
                CandyPush(d)
                CandyBig("Big dreams", theme.textPrimary, 20f)
                CandySmall("start small! Tap to add a goal.", theme.textSecondary)
                return@CandyCard
            }
            Spacer(GlanceModifier.height(if (d.compact) 2.dp else 6.dp))
            val pct = snapshot.formatPercent(goal.progressPercentDisplay)
            CandyBig(pct, theme.textPrimary, if (d.compact) 24f else if (d.h >= 165f) 40f else 34f)
            if (!d.compact) {
                CandyPush(d)
                CandyBar(snapshot.themeMode, (d.innerW - d.artW * 0.8f).coerceAtLeast(70f),
                    if (snapshot.shouldMaskAmounts) 0f else goal.progressFraction, R.drawable.bg_candy_fill_mint)
                Spacer(GlanceModifier.height(6.dp))
                CandySmall("${snapshot.formatMoney(goal.savedSen)} / ${snapshot.formatMoney(goal.targetSen)}", theme.textSecondary)
            }
        }
    }
}

class DuitSavingsGoalsWidgetReceiver : DuitGlanceWidgetReceiver(DuitSavingsGoalsWidget())

// -------------------------------------- 05 Bills Countdown ------------------------------------
class DuitUpcomingPaymentsWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        // Items are unpaid only (paid bills never reach the snapshot); overdue sorts first.
        val bill = snapshot.upcomingPayments.items.firstOrNull()
        val due = bill?.let { dueLabel(it.dueDateEpochDay, it.isOverdue) }
        CandyCard(
            snapshot, CandyWidgetTone.PEACH, WidgetDeepLink.PLANNING_COMMITMENTS,
            if (bill == null) "Bills Countdown. No upcoming bills."
            else "Next bill ${bill.title}, ${snapshot.accessibleMoney(bill.amountSen)}, $due. Tap to open bills.",
            R.drawable.ic_candy_art_calendar_bell
        ) { d ->
            CandyHeader("Next Bill", theme.textPrimary, R.drawable.ic_candy_art_calendar_bell.takeIf { d.artW == 0f }, snapshot.isAppLockEnabled, small = d.compact)
            if (bill == null) {
                CandyPush(d)
                CandyBig("All clear!", theme.textPrimary, 22f)
                CandySmall("No upcoming bills. Nice!", theme.textSecondary)
                return@CandyCard
            }
            if (!d.compact) CandySmall(bill.title, theme.textSecondary)
            CandyPush(d)
            val amount = snapshot.formatMoney(bill.amountSen)
            CandyBig(amount, theme.textPrimary, bigSize(d, amount))
            if (!d.compact) {
                Spacer(GlanceModifier.height(6.dp))
                Box(modifier = GlanceModifier.background(ImageProvider(R.drawable.bg_candy_chip_yellow)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(due.orEmpty(), maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFF263B43)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class DuitUpcomingPaymentsWidgetReceiver : DuitGlanceWidgetReceiver(DuitUpcomingPaymentsWidget())

// --------------------------------------- 06 Budget Tracker ------------------------------------
class DuitBudgetWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val b = snapshot.budgets.firstOrNull()
        CandyCard(
            snapshot, CandyWidgetTone.YELLOW, WidgetDeepLink.PLANNING_BUDGETS_LINK,
            if (b == null) "Budget Tracker. No budget yet."
            else "Budget ${b.name}: ${snapshot.accessibleMoney(b.remainingSen)} left, ${snapshot.formatPercent(b.usedPercentDisplay)} used.",
            R.drawable.ic_candy_art_bag
        ) { d ->
            CandyHeader(b?.name ?: "Budget", theme.textPrimary, R.drawable.ic_candy_art_bag.takeIf { d.artW == 0f }, snapshot.isAppLockEnabled, small = d.compact)
            if (b == null) {
                CandyPush(d)
                CandyBig("Set a budget", theme.textPrimary, 20f)
                CandySmall("Big plans start small.", theme.textSecondary)
                return@CandyCard
            }
            Spacer(GlanceModifier.height(if (d.compact) 2.dp else 8.dp))
            val over = b.overspentSen > 0L
            val amount = snapshot.formatMoney(if (over) b.overspentSen else b.remainingSen)
            Row(verticalAlignment = Alignment.Bottom) {
                CandyBig(amount, if (over) theme.lavenderAccent else theme.textPrimary, bigSize(d, amount))
                Spacer(GlanceModifier.width(4.dp))
                CandySmall(if (over) "over" else "left", theme.textPrimary, 14f)
            }
            if (!d.compact) {
                CandyPush(d)
                CandyBar(snapshot.themeMode, (d.innerW - d.artW * 0.8f).coerceAtLeast(70f),
                    if (snapshot.shouldMaskAmounts) 0f else if (over) 1f else b.progressFraction,
                    if (over) R.drawable.bg_sts_bar_fill else R.drawable.bg_candy_fill_yellow)
                Spacer(GlanceModifier.height(6.dp))
                CandySmall("${snapshot.formatPercent(b.usedPercentDisplay)} used", theme.textSecondary)
            }
        }
    }
}

class DuitBudgetWidgetReceiver : DuitGlanceWidgetReceiver(DuitBudgetWidget())

// --------------------------------------- 07 Spending Pulse ------------------------------------
class DuitPulseWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val days = snapshot.spendingLast7DaysSen.ifEmpty { List(7) { 0L } }
        val total = days.sum()
        val totalText = snapshot.formatMoney(total)
        CandyCard(snapshot, CandyWidgetTone.MINT, WidgetDeepLink.INSIGHTS,
            "Spending Pulse. Spent ${snapshot.accessibleMoney(total)} in the last 7 days. Tap to open Insights.", null) { d ->
            CandyHeader("Spending", theme.textPrimary, R.drawable.ic_candy_down, snapshot.isAppLockEnabled, small = d.compact)
            Spacer(GlanceModifier.height(6.dp))
            val chartH = (d.h - d.pad * 2 - (if (d.compact) 26f else 58f)).coerceAtLeast(16f)
            val gap = 6f
            val barW = ((d.innerW - gap * 6) / 7f).coerceIn(6f, 22f)
            val max = days.maxOrNull()?.takeIf { it > 0L } ?: 1L
            // Bars use real totals; under privacy they flatten so no spending pattern leaks.
            val fills = listOf(R.drawable.bg_candy_fill_yellow_v, R.drawable.bg_candy_fill_coral, R.drawable.bg_candy_fill_mint_v)
            Row(modifier = GlanceModifier.fillMaxWidth().height(chartH.dp), verticalAlignment = Alignment.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                days.forEachIndexed { i, v ->
                    val bh = if (snapshot.shouldMaskAmounts || v <= 0L) 6f else (chartH * v / max).coerceAtLeast(8f)
                    val fill = if (snapshot.shouldMaskAmounts || v <= 0L) trackRes(snapshot.themeMode) else fills[i % 3]
                    Spacer(GlanceModifier.width(barW.dp).height(bh.dp).background(ImageProvider(fill)))
                    if (i < 6) Spacer(GlanceModifier.width(gap.dp))
                }
            }
            if (!d.compact) {
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (total == 0L) "No spending this week" else "This week", maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    CandyBig(totalText, theme.textPrimary, 18f)
                }
            }
        }
    }
}

class DuitPulseWidgetReceiver : DuitGlanceWidgetReceiver(DuitPulseWidget())

// -------------------------------------- 08 Payday Countdown ------------------------------------
class DuitPaydayWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val p = snapshot.payday
        val big = when {
            p == null -> "Set up"
            p.daysUntil == 0L -> "Today!"
            p.daysUntil == 1L -> "1 day"
            else -> "${p.daysUntil} days"
        }
        CandyCard(snapshot, CandyWidgetTone.YELLOW, WidgetDeepLink.PLANNING_GOALS,
            if (p == null) "Payday Countdown. Payday not set up yet." else "Payday Countdown. $big. ${p.label}.",
            R.drawable.ic_candy_art_calendar, narrowReserve = 96f
        ) { d ->
            CandyHeader("Payday", theme.textPrimary, R.drawable.ic_candy_art_calendar.takeIf { d.artW == 0f }, small = d.compact)
            CandyPush(d)
            if (p == null) {
                // Setup state: a clear prompt, not a fake countdown.
                CandyBig("Not set yet", theme.textPrimary, if (d.compact) 18f else 22f)
                CandySmall("Tap to add your payday", theme.textSecondary, maxLines = 2)
            } else {
                CandyBig(big, theme.textPrimary, if (d.compact) 22f else if (d.h >= 165f) 36f else 30f)
                CandySmall(p.label, theme.textSecondary, maxLines = 2)
            }

        }
    }
}

class DuitPaydayWidgetReceiver : DuitGlanceWidgetReceiver(DuitPaydayWidget())

// -------------------------------------- 09 Monthly Summary -------------------------------------
class DuitSummaryWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val b = snapshot.balance
        CandyCard(snapshot, CandyWidgetTone.MINT, WidgetDeepLink.INSIGHTS,
            "Money Summary, ${b.cycleLabel}. Income ${snapshot.accessibleMoney(b.cycleIncomeSen)}, expense ${snapshot.accessibleMoney(b.cycleExpenseSen)}.", null) { d ->
            CandyHeader("Monthly Summary", theme.textPrimary, R.drawable.ic_candy_up, snapshot.isAppLockEnabled, small = d.compact)
            if (!d.compact) CandySmall(b.cycleLabel, theme.textSecondary, 11f)
            Spacer(GlanceModifier.defaultWeight())
            listOf(
                Triple(R.drawable.ic_candy_up, "Income", b.cycleIncomeSen),
                Triple(R.drawable.ic_candy_down, "Expense", b.cycleExpenseSen)
            ).forEach { (icon, label, value) ->
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(icon), null, GlanceModifier.size(18.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(label, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    CandyBig(snapshot.formatMoney(value), theme.textPrimary, if (d.compact) 14f else 17f)
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            val sum = b.cycleIncomeSen + b.cycleExpenseSen
            if (!d.compact && d.h >= 130f) {
                Spacer(GlanceModifier.defaultWeight())
                // Split bar = real income vs expense share; neutral track when hidden or empty.
                if (snapshot.shouldMaskAmounts || sum <= 0L) {
                    CandyBar(snapshot.themeMode, d.innerW, 0f, R.drawable.bg_candy_fill_mint, 10f)
                } else {
                    val incomeW = (d.innerW - 4f) * b.cycleIncomeSen / sum
                    Row(modifier = GlanceModifier.fillMaxWidth().height(10.dp)) {
                        if (incomeW >= 10f) Spacer(GlanceModifier.width(incomeW.dp).fillMaxHeight().background(ImageProvider(R.drawable.bg_candy_fill_mint)))
                        Spacer(GlanceModifier.width(4.dp))
                        if (d.innerW - 4f - incomeW >= 10f) Spacer(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.bg_sts_bar_fill)))
                    }
                }
            }
        }
    }
}

class DuitSummaryWidgetReceiver : DuitGlanceWidgetReceiver(DuitSummaryWidget())

// ------------------------------------ 10 Commitment Tracker ------------------------------------
class DuitCommitmentsWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val items = snapshot.upcomingPayments.items
        CandyCard(snapshot, CandyWidgetTone.MINT, WidgetDeepLink.PLANNING_COMMITMENTS,
            "Commitments. ${items.size} pending. Tap to open bills.", R.drawable.ic_candy_art_clipboard, artAspect = 100f / 120f, artShare = 0.38f, narrowReserve = 116f
        ) { d ->
            CandyHeader("Commitments", theme.textPrimary, R.drawable.ic_candy_check, snapshot.isAppLockEnabled, small = d.compact)
            Spacer(GlanceModifier.height(if (d.compact) 2.dp else 6.dp))
            if (items.isEmpty()) {
                CandyBig("All clear!", theme.textPrimary, 22f)
                CandySmall("Nothing pending.", theme.textSecondary)
                return@CandyCard
            }
            Row(verticalAlignment = Alignment.Bottom) {
                CandyBig("${items.size}", theme.aquaAccent, if (d.compact) 24f else 30f)
                Spacer(GlanceModifier.width(6.dp))
                CandySmall("pending", theme.textPrimary, 15f)
            }
            if (!d.compact) {
                CandyPush(d)
                // Text may run under the art only on wide cards, where the art is beside it.
                val textW = if (d.narrow) d.innerW - 22f else (d.innerW - d.artW * 0.9f - 22f).coerceAtLeast(90f)
                items.take(if (d.narrow || d.h < 150f) 1 else 2).forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Image(ImageProvider(R.drawable.ic_candy_box), null, GlanceModifier.size(16.dp).padding(top = 1.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Column(modifier = GlanceModifier.width(textW.dp)) {
                            Text(item.title, maxLines = 1,
                                style = TextStyle(color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            Text(dueLabel(item.dueDateEpochDay, item.isOverdue), maxLines = 1,
                                style = TextStyle(color = theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

class DuitCommitmentsWidgetReceiver : DuitGlanceWidgetReceiver(DuitCommitmentsWidget())

// ---------------------------------------- 11 Money Buddy ---------------------------------------
private val BuddyMessages = listOf(
    "You're doing great! Keep it up!",
    "You got this! 🌼",
    "Little steps, big progress.",
    "Your money journey matters.",
    "Keep going, money buddy!"
)

class DuitBuddyWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        // Rotates daily; encouragement only, never a claim about the user's numbers.
        val message = BuddyMessages[(LocalDate.now().toEpochDay() % BuddyMessages.size).toInt()]
        CandyCard(snapshot, CandyWidgetTone.CREAM, WidgetDeepLink.HOME, "Money Buddy says: $message", R.drawable.ic_candy_art_cat, artShare = 0.5f, narrowReserve = 64f) { d ->
            Text(
                message,
                maxLines = 3,
                modifier = GlanceModifier.width((if (d.narrow) d.innerW else d.innerW - d.artW * 0.6f).coerceAtLeast(90f).dp),
                style = TextStyle(color = theme.textPrimary, fontSize = if (d.compact) 14.sp else 19.sp, fontWeight = FontWeight.Bold, fontFamily = CandyHeavy)
            )
        }
    }
}

class DuitBuddyWidgetReceiver : DuitGlanceWidgetReceiver(DuitBuddyWidget())

// --------------------------------------- 12 Privacy Balance ------------------------------------
class DuitPrivacyWidget : DuitGlanceWidgetBase() {
    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val cream = ColorProvider(Color(0xFFFFF4E9))
        // Always masked; no reveal action exists. Tapping opens duit., which applies App Lock.
        CandyCard(snapshot, CandyWidgetTone.TEAL, WidgetDeepLink.HOME, "Privacy Balance. Amount hidden. Tap to open duit.",
            R.drawable.ic_candy_art_shield, artShare = 0.36f, narrowReserve = 96f) { d ->
            Image(ImageProvider(R.drawable.ic_candy_eye_off), null, GlanceModifier.size(if (d.compact) 20.dp else 26.dp))
            CandyPush(d)
            CandyBig("RM ••••••", cream, if (d.compact) 22f else 28f)
            if (!d.compact) {
                Spacer(GlanceModifier.height(2.dp))
                CandySmall("Tap to open duit.", ColorProvider(Color(0xCCFFF4E9)))
            }
        }
    }
}

class DuitPrivacyWidgetReceiver : DuitGlanceWidgetReceiver(DuitPrivacyWidget())
