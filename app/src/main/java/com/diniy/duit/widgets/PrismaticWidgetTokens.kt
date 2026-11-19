package com.diniy.duit.widgets

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import androidx.glance.unit.ColorProvider
import com.diniy.duit.DuitApplication
import com.diniy.duit.R
import com.diniy.duit.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

// Candy Bubble 2.0: Aqua=Mint, Lavender=Coral, Sky=Yellow, Peach=Peach. Names kept for existing call sites.
object PrismaticWidgetPalette {
    val Aqua = Color(0xFF79DCC6)
    val Lavender = Color(0xFFFF836B)
    val SkyBlue = Color(0xFFFFB84D)
    val Peach = Color(0xFFFFB88D)
    val Slate = Color(0xFF263B43)

    val TextPrimaryLight = Color(0xFF263B43)
    val TextPrimaryDark = Color(0xFFFFF4E9)
    val TextSecondaryLight = Color(0xFF55696F)
    val TextSecondaryDark = Color(0xFFBCCACD)
    val AquaTextLight = Color(0xFF16806D)
    val AquaTextDark = Color(0xFF79DCC6)
    val LavenderTextLight = Color(0xFFD14B33)
    val LavenderTextDark = Color(0xFFFF9D88)
    val PeachTextLight = Color(0xFFB85D2B)
    val PeachTextDark = Color(0xFFF7C39D)
    val SkyTextLight = Color(0xFF9A5B00)
    val SkyTextDark = Color(0xFFFFC76E)
    val TrackLight = Color(0xFFF3DCC6)
    val TrackDark = Color(0xFF3A545C)
}

data class ResolvedWidgetTheme(
    @param:DrawableRes val surfaceBgRes: Int,
    @param:DrawableRes val tileBgRes: Int,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val aquaAccent: ColorProvider,
    val lavenderAccent: ColorProvider,
    val skyAccent: ColorProvider,
    val peachAccent: ColorProvider,
    val slateAccent: ColorProvider,
    val progressTrack: ColorProvider
) {
    companion object {
        private fun dual(light: Color, dark: Color, mode: ThemeMode): ColorProvider = when (mode) {
            ThemeMode.LIGHT -> ColorProvider(light)
            ThemeMode.DARK -> ColorProvider(dark)
            ThemeMode.SYSTEM -> DayNightColorProvider(day = light, night = dark)
        }

        fun forThemeMode(mode: ThemeMode): ResolvedWidgetTheme {
            val surfaceRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.bg_widget_surface_light
                ThemeMode.DARK -> R.drawable.bg_widget_surface_dark
                ThemeMode.SYSTEM -> R.drawable.bg_widget_surface_auto
            }
            val tileRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.bg_widget_tile_light
                ThemeMode.DARK -> R.drawable.bg_widget_tile_dark
                ThemeMode.SYSTEM -> R.drawable.bg_widget_tile_auto
            }
            return ResolvedWidgetTheme(
                surfaceBgRes = surfaceRes,
                tileBgRes = tileRes,
                textPrimary = dual(PrismaticWidgetPalette.TextPrimaryLight, PrismaticWidgetPalette.TextPrimaryDark, mode),
                textSecondary = dual(PrismaticWidgetPalette.TextSecondaryLight, PrismaticWidgetPalette.TextSecondaryDark, mode),
                aquaAccent = dual(PrismaticWidgetPalette.AquaTextLight, PrismaticWidgetPalette.AquaTextDark, mode),
                lavenderAccent = dual(PrismaticWidgetPalette.LavenderTextLight, PrismaticWidgetPalette.LavenderTextDark, mode),
                skyAccent = dual(PrismaticWidgetPalette.SkyTextLight, PrismaticWidgetPalette.SkyTextDark, mode),
                peachAccent = dual(PrismaticWidgetPalette.PeachTextLight, PrismaticWidgetPalette.PeachTextDark, mode),
                slateAccent = dual(PrismaticWidgetPalette.Slate, PrismaticWidgetPalette.TextPrimaryDark, mode),
                progressTrack = dual(PrismaticWidgetPalette.TrackLight, PrismaticWidgetPalette.TrackDark, mode)
            )
        }
    }
}

abstract class DuitGlanceWidgetBase : GlanceAppWidget() {

    // Candy cards lay out against the real launcher cell size (Phase 11N).
    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    protected abstract fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as DuitApplication).container
        val initialSnapshot = withContext(Dispatchers.IO) {
            container.widgetDataProvider.loadSnapshot()
        }

        provideContent {
            val snapshot by container.widgetDataProvider
                .observeSnapshot()
                .catch { emit(initialSnapshot) }
                .collectAsState(initial = initialSnapshot)
            val theme = ResolvedWidgetTheme.forThemeMode(snapshot.themeMode)
            Content(snapshot = snapshot, theme = theme)
        }
    }
}

abstract class DuitGlanceWidgetReceiver(
    override val glanceAppWidget: GlanceAppWidget
) : GlanceAppWidgetReceiver()

@Composable
internal fun PrismaticWidgetFrame(
    theme: ResolvedWidgetTheme,
    deepLink: WidgetDeepLink,
    accessibilityLabel: String,
    compactPadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val pad = if (compactPadding) 10.dp else 14.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(theme.surfaceBgRes))
            .clickable(actionStartActivity(WidgetDeepLink.createLaunchIntent(context, deepLink)))
            .semantics { contentDescription = accessibilityLabel }
            .padding(pad)
    ) {
        content()
    }
}

@Composable
internal fun WidgetHeaderBar(
    title: String,
    theme: ResolvedWidgetTheme,
    accentColor: ColorProvider,
    @DrawableRes iconRes: Int,
    badgeText: String? = null,
    isAppLockEnabled: Boolean = false
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(accentColor),
            modifier = GlanceModifier.size(16.dp)
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = theme.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        if (isAppLockEnabled) {
            Spacer(GlanceModifier.width(5.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_solar_lock),
                contentDescription = "Protected by App Lock",
                colorFilter = ColorFilter.tint(theme.textSecondary),
                modifier = GlanceModifier.size(12.dp)
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.bg_widget_pill_sky))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    maxLines = 1,
                    style = TextStyle(
                        color = theme.skyAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
internal fun WidgetMetricTile(
    label: String,
    value: String,
    theme: ResolvedWidgetTheme,
    valueColor: ColorProvider = theme.textPrimary,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .background(ImageProvider(theme.tileBgRes))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = theme.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                color = valueColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
internal fun WidgetEmptyBlock(
    title: String,
    subtitle: String,
    theme: ResolvedWidgetTheme
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(theme.tileBgRes))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = theme.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            maxLines = 2,
            style = TextStyle(
                color = theme.textSecondary,
                fontSize = 11.sp
            )
        )
    }
}
