package com.diniy.duit.debug

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.R
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyDecor
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyMoneyText
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticNavItem
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.candyTone
import com.diniy.duit.core.ui.glass.LiquidGlassCapabilities
import com.diniy.duit.core.ui.glass.LiquidGlassFloatingNavBar
import com.diniy.duit.core.ui.glass.LiquidGlassHeroCard
import com.diniy.duit.core.ui.glass.LiquidGlassQualityMode
import com.diniy.duit.core.ui.glass.liquidGlassBackdropSource
import com.diniy.duit.core.ui.glass.rememberLiquidGlassBackdropState
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.ThemeMode

/**
 * Isolated, debug-only Phase 10A Liquid Glass Research Prototype.
 *
 * Not wired into production navigation, does not touch Room/DataStore/financial state,
 * and is excluded from beta/release builds (lives strictly under `src/debug/`).
 */
class LiquidGlassPrototypeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialDark = intent.getStringExtra("theme").equals("dark", ignoreCase = true)
        val initialQuality = when (intent.getStringExtra("quality")?.lowercase()) {
            "frosted" -> LiquidGlassQualityMode.FROSTED
            "fallback" -> LiquidGlassQualityMode.FALLBACK
            "full" -> LiquidGlassQualityMode.FULL
            else -> LiquidGlassQualityMode.AUTO
        }
        val initialTone = when (intent.getStringExtra("tone")?.lowercase()) {
            "mint" -> CandyTone.Mint
            "sunny" -> CandyTone.Sunny
            else -> CandyTone.Coral
        }
        val initialLifted = intent.getBooleanExtra("lifted", false)
        val initialTab = intent.getIntExtra("tab", 0).coerceIn(0, 3)

        setContent {
            var isDark by remember { mutableStateOf(initialDark) }
            var qualityMode by remember { mutableStateOf(initialQuality) }
            var heroTone by remember { mutableStateOf(initialTone) }
            var selectedTab by remember { mutableIntStateOf(initialTab) }
            var previewLifted by remember { mutableStateOf(initialLifted) }

            DisposableEffect(isDark) {
                val style = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            DuitTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                LiquidGlassPrototypeScreen(
                    isDark = isDark,
                    onToggleTheme = { isDark = !isDark },
                    qualityMode = qualityMode,
                    onSelectQuality = { qualityMode = it },
                    heroTone = heroTone,
                    onCycleTone = {
                        heroTone = when (heroTone) {
                            CandyTone.Coral -> CandyTone.Mint
                            CandyTone.Mint -> CandyTone.Sunny
                            else -> CandyTone.Coral
                        }
                    },
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    previewLifted = previewLifted
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassPrototypeScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    qualityMode: LiquidGlassQualityMode,
    onSelectQuality: (LiquidGlassQualityMode) -> Unit,
    heroTone: CandyTone,
    onCycleTone: () -> Unit,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    previewLifted: Boolean
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val listState = rememberLazyListState()
    val backdropState = rememberLiquidGlassBackdropState(initialQualityMode = qualityMode)

    LaunchedEffect(qualityMode) {
        backdropState.qualityMode = qualityMode
    }
    LaunchedEffect(listState.isScrollInProgress) {
        backdropState.isScrolling = listState.isScrollInProgress
    }

    val activeTier = LiquidGlassCapabilities.resolveTier(
        sdkInt = Build.VERSION.SDK_INT,
        qualityMode = qualityMode
    )

    val navItems = remember {
        listOf(
            PrismaticNavItem("home", "Home", R.drawable.ic_solar_home, "Home tab"),
            PrismaticNavItem("activity", "Activity", R.drawable.ic_solar_activity, "Activity tab"),
            PrismaticNavItem("planning", "Planning", R.drawable.ic_solar_planning, "Planning tab"),
            PrismaticNavItem("insights", "Insights", R.drawable.ic_solar_insights, "Insights tab")
        )
    }

    PrismaticBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Live Backdrop Source: playful Candy Bubble blobs + standard opaque cards scrolling underneath
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropSource(backdropState)
            ) {
                // Soft playful Candy Bubble spheres behind the top hero card so refraction & blur are immediately visible
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val coral = colors.coral
                    val mint = colors.aqua
                    val sunny = colors.sunny
                    val sky = colors.skyBlue
                    val alphaHi = if (isDark) 0.52f else 0.72f
                    val alphaMid = if (isDark) 0.42f else 0.62f

                    drawCircle(
                        color = coral.copy(alpha = alphaHi),
                        radius = size.width * 0.34f,
                        center = Offset(size.width * 0.86f, size.height * 0.20f)
                    )
                    drawCircle(
                        color = mint.copy(alpha = alphaHi),
                        radius = size.width * 0.30f,
                        center = Offset(size.width * 0.14f, size.height * 0.28f)
                    )
                    drawCircle(
                        color = sunny.copy(alpha = alphaMid),
                        radius = size.width * 0.24f,
                        center = Offset(size.width * 0.54f, size.height * 0.33f)
                    )
                    drawCircle(
                        color = sky.copy(alpha = alphaMid),
                        radius = size.width * 0.32f,
                        center = Offset(size.width * 0.24f, size.height * 0.88f)
                    )
                    drawCircle(
                        color = coral.copy(alpha = alphaMid),
                        radius = size.width * 0.28f,
                        center = Offset(size.width * 0.80f, size.height * 0.90f)
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 278.dp,
                        bottom = 132.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "tier_label") {
                        Text(
                            text = "Candy cards below glass",
                            style = typography.sectionHeading,
                            color = colors.primaryText
                        )
                    }

                    item(key = "sample_bento") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SampleCandyTile(
                                title = "Total balance",
                                amount = "RM 2,638.00",
                                subtitle = "3 wallets",
                                icon = Icons.Rounded.AccountBalanceWallet,
                                tone = CandyTone.Sky,
                                modifier = Modifier.weight(1f)
                            )
                            SampleCandyTile(
                                title = "Protected",
                                amount = "RM 900.00",
                                subtitle = "2 goals",
                                icon = Icons.Rounded.Savings,
                                tone = CandyTone.Sunny,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val rows = listOf(
                        Triple("Salary", "Maybank · 18 Nov", "+RM 2,600.00" to CandyTone.Mint),
                        Triple("Maxis Fibre", "Bills · Due 25 Nov", "-RM 100.00" to CandyTone.Coral),
                        Triple("Kopi & Lunch", "Food · 16 Nov", "-RM 24.50" to CandyTone.Peach),
                        Triple("Emergency Fund", "Transfer · Protected", "RM 500.00" to CandyTone.Sunny),
                        Triple("Bookstore Receipt", "Tax Pocket · Ready", "-RM 89.00" to CandyTone.Sky),
                        Triple("Grab Ride", "Transport · 14 Nov", "-RM 18.00" to CandyTone.Coral),
                        Triple("Groceries", "Jaya Grocer · 12 Nov", "-RM 142.30" to CandyTone.Mint)
                    )

                    items(rows.size, key = { "row_$it" }) { idx ->
                        val (title, subtitle, pair) = rows[idx]
                        val (amount, tone) = pair
                        val icon = when (idx % 4) {
                            0 -> Icons.Rounded.Payments
                            1 -> Icons.Rounded.ReceiptLong
                            2 -> Icons.Rounded.LocalCafe
                            else -> Icons.Rounded.Savings
                        }
                        SampleCandyRow(
                            title = title,
                            subtitle = subtitle,
                            amount = amount,
                            icon = icon,
                            tone = tone
                        )
                    }
                }
            }

            // 2. Top Header (subtle canvas fade so scrolled titles never collide with header) + ONE Floating Liquid Glass Hero Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to colors.appBackground,
                            0.36f to colors.appBackground.copy(alpha = 0.94f),
                            0.52f to Color.Transparent
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Glass Lab",
                            style = typography.screenHeading,
                            color = colors.primaryText,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Sample preview · ${activeTier.name}",
                            style = typography.supportingInfo,
                            color = colors.secondaryText
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tierLabel = when (qualityMode) {
                            LiquidGlassQualityMode.FROSTED -> "Frost"
                            LiquidGlassQualityMode.FALLBACK -> "Basic"
                            else -> "Full"
                        }
                        SpatialGlassPill(
                            label = tierLabel,
                            selected = true,
                            accentColor = colors.aqua,
                            onClick = {
                                val next = when (qualityMode) {
                                    LiquidGlassQualityMode.AUTO, LiquidGlassQualityMode.FULL -> LiquidGlassQualityMode.FROSTED
                                    LiquidGlassQualityMode.FROSTED -> LiquidGlassQualityMode.FALLBACK
                                    LiquidGlassQualityMode.FALLBACK -> LiquidGlassQualityMode.FULL
                                }
                                onSelectQuality(next)
                            }
                        )
                        SpatialGlassPill(
                            label = if (isDark) "Light" else "Dark",
                            selected = true,
                            accentColor = colors.coral,
                            onClick = onToggleTheme
                        )
                    }
                }

                // ONE Liquid Glass Hero Card (tap cycles playful Coral / Mint / Sunny tint + shows touch bloom)
                LiquidGlassHeroCard(
                    backdropState = backdropState,
                    tone = heroTone,
                    onClick = onCycleTone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Safe to spend",
                                style = typography.cardTitle,
                                color = colors.primaryText
                            )
                            Text(
                                text = "November 2026 Cycle",
                                style = typography.caption,
                                color = colors.secondaryText
                            )
                        }
                        CandyStatusBadge(
                            text = heroTone.name,
                            tone = heroTone
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    CandyMoneyText(
                        text = "RM 1,538.00",
                        style = typography.heroBalance,
                        color = colors.primaryText
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap card for tint · Drag bottom bar",
                        style = typography.caption,
                        color = colors.secondaryText
                    )
                }
            }

            // 3. Floating Liquid Glass Bottom Navigation Bar (4 Phase 9 tabs + draggable refracting lens)
            LiquidGlassFloatingNavBar(
                backdropState = backdropState,
                items = navItems,
                selectedIndex = selectedTab,
                onSelectIndex = onSelectTab,
                previewLiftedLens = previewLifted,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SampleCandyTile(
    title: String,
    amount: String,
    subtitle: String,
    icon: ImageVector,
    tone: CandyTone,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    CandyBubbleCard(
        tone = tone,
        decor = CandyDecor.Bubbles,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp)
    ) {
        CandyIconBlob(icon = icon, tone = tone, size = 38.dp, tilt = -4f, solid = true)
        Spacer(Modifier.height(10.dp))
        Text(title, style = typography.caption, color = colors.secondaryText)
        CandyMoneyText(amount, typography.cardTitle, colors.primaryText, minorScale = 0.72f)
        Text(subtitle, style = typography.caption, color = candyTone(tone).ink)
    }
}

@Composable
private fun SampleCandyRow(
    title: String,
    subtitle: String,
    amount: String,
    icon: ImageVector,
    tone: CandyTone
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    CandyBubbleCard(
        tone = CandyTone.Neutral,
        decor = CandyDecor.None,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(candyTone(tone).container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = candyTone(tone).ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = typography.transactionDescription, color = colors.primaryText)
                Text(subtitle, style = typography.caption, color = colors.secondaryText)
            }
            CandyMoneyText(
                text = amount,
                style = typography.transactionAmount,
                color = if (amount.startsWith("+")) colors.income else colors.primaryText,
                minorScale = 0.72f
            )
        }
    }
}
