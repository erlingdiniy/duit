package com.diniy.duit.feature.personality

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.diniy.duit.R
import com.diniy.duit.domain.personality.MoneyPersonality
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates a privacy-safe Candy Bubble 2.0 PNG card for Android Sharesheet.
 * Accepts only [MoneyPersonality] and [YearMonth], so no financial amounts,
 * account names, categories, or private notes can ever appear on the card.
 */
internal object MoneyPersonalityShareCard {
    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350
    private const val SHARE_DIR = "shared"
    private const val SHARE_FILE_NAME = "duit-money-personality.png"
    private const val FILE_PROVIDER_SUFFIX = ".supportqr"

    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    // Candy Bubble 2.0 palette (PrismaticPalette)
    private const val CREAM = 0xFFFFF4E9.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DEEP_TEAL = 0xFF263B43.toInt()
    private const val INK_MUTED = 0xFF4F6670.toInt()
    private const val CORAL = 0xFFFF836B.toInt()
    private const val SUNNY = 0xFFFFB84D.toInt()
    private const val MINT = 0xFF79DCC6.toInt()
    private const val PEACH = 0xFFFFB199.toInt()
    private const val INCOME_GREEN = 0xFF0B6B5C.toInt()

    fun shareCaption(personality: MoneyPersonality, month: YearMonth): String =
        "My duit. money personality for ${month.format(MONTH_FORMAT)}: ${personality.title}! ${personality.tagline}"

    fun renderCardBitmap(
        personality: MoneyPersonality,
        month: YearMonth,
        headingTypeface: Typeface? = null,
        bodyTypeface: Typeface? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val headingFace = headingTypeface ?: Typeface.DEFAULT_BOLD
        val bodyFace = bodyTypeface ?: Typeface.DEFAULT
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = DEEP_TEAL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Cream background with soft decorative candy bubbles.
        canvas.drawColor(CREAM)
        fillPaint.color = withAlpha(MINT, 72)
        canvas.drawCircle(150f, 160f, 190f, fillPaint)
        fillPaint.color = withAlpha(SUNNY, 68)
        canvas.drawCircle(950f, 1180f, 220f, fillPaint)
        fillPaint.color = withAlpha(PEACH, 64)
        canvas.drawCircle(930f, 210f, 120f, fillPaint)

        // Centered Candy Bubble card with sticker offset shadow.
        val cardRect = RectF(96f, 118f, CARD_WIDTH - 96f, CARD_HEIGHT - 134f)
        val shadowRect = RectF(cardRect.left + 16f, cardRect.top + 16f, cardRect.right + 16f, cardRect.bottom + 16f)
        fillPaint.color = DEEP_TEAL
        canvas.drawRoundRect(shadowRect, 72f, 72f, fillPaint)
        fillPaint.color = WHITE
        canvas.drawRoundRect(cardRect, 72f, 72f, fillPaint)
        strokePaint.strokeWidth = 7f
        canvas.drawRoundRect(cardRect, 72f, 72f, strokePaint)

        // Top brand pill ("duit. • Money Personality").
        val pillRect = RectF(260f, 186f, CARD_WIDTH - 260f, 266f)
        fillPaint.color = MINT
        canvas.drawRoundRect(pillRect, 40f, 40f, fillPaint)
        strokePaint.strokeWidth = 5f
        canvas.drawRoundRect(pillRect, 40f, 40f, strokePaint)

        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DEEP_TEAL
            textSize = 34f
            typeface = Typeface.create(headingFace, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("duit.  •  Money Personality", CARD_WIDTH / 2f, 238f, pillTextPaint)

        // Month label.
        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 38f
            typeface = Typeface.create(bodyFace, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(month.format(MONTH_FORMAT), CARD_WIDTH / 2f, 344f, monthPaint)

        // Soft backdrop circle + original vector mascot.
        val mascotTint = personality.cardTint()
        fillPaint.color = withAlpha(mascotTint, 56)
        canvas.drawCircle(CARD_WIDTH / 2f, 595f, 195f, fillPaint)
        drawMascot(canvas, personality, cx = CARD_WIDTH / 2f, cy = 610f, size = 340f)

        // Personality title.
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DEEP_TEAL
            textSize = 70f
            typeface = Typeface.create(headingFace, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(personality.title, CARD_WIDTH / 2f, 895f, titlePaint)

        // Wrapped tagline (centered, no financial amounts).
        val taglinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 40f
            typeface = bodyFace
        }
        val taglineWidth = (cardRect.width() - 140f).toInt()
        val taglineLayout = StaticLayout.Builder
            .obtain(personality.tagline, 0, personality.tagline.length, taglinePaint, taglineWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(10f, 1f)
            .build()
        canvas.save()
        canvas.translate(cardRect.left + 70f, 940f)
        taglineLayout.draw(canvas)
        canvas.restore()

        // Privacy-first footer note.
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 28f
            typeface = bodyFace
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Offline monthly habit card • No amounts shared", CARD_WIDTH / 2f, cardRect.bottom - 52f, footerPaint)

        return bitmap
    }

    fun writeCardToShareCache(context: Context, personality: MoneyPersonality, month: YearMonth): File {
        val headingFace = runCatching { ResourcesCompat.getFont(context, R.font.fredoka) }.getOrNull()
        val bodyFace = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull()
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, SHARE_FILE_NAME)
        val bitmap = renderCardBitmap(personality, month, headingFace, bodyFace)
        try {
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compression failed" }
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    fun buildImageShareIntent(uri: Uri, caption: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        clipData = ClipData.newRawUri("duit. Money Personality", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun buildTextFallbackIntent(caption: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, caption)
    }

    /**
     * Prepares a Sharesheet chooser intent using the app's private FileProvider cache URI.
     * Falls back to text-only share if the FileProvider authority is unavailable in non-beta builds.
     */
    fun createShareChooserIntent(context: Context, personality: MoneyPersonality, month: YearMonth): Intent {
        val caption = shareCaption(personality, month)
        val sendIntent = runCatching {
            val file = writeCardToShareCache(context, personality, month)
            val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
            buildImageShareIntent(uri, caption)
        }.getOrElse {
            buildTextFallbackIntent(caption)
        }
        return Intent.createChooser(sendIntent, "Share your Money Personality")
    }

    private fun drawMascot(canvas: Canvas, personality: MoneyPersonality, cx: Float, cy: Float, size: Float) {
        val r = size * 0.32f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = DEEP_TEAL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Body + gloss highlight.
        fill.color = personality.cardTint()
        canvas.drawCircle(cx, cy, r, fill)
        fill.color = withAlpha(WHITE, 90)
        canvas.drawCircle(cx - r * 0.45f, cy - r * 0.45f, r * 0.28f, fill)

        // Face: eyes, smile, blush.
        fill.color = DEEP_TEAL
        canvas.drawCircle(cx - r * 0.33f, cy - r * 0.08f, r * 0.09f, fill)
        canvas.drawCircle(cx + r * 0.33f, cy - r * 0.08f, r * 0.09f, fill)
        stroke.strokeWidth = size * 0.022f
        canvas.drawArc(
            RectF(cx - r * 0.22f, cy - r * 0.05f, cx + r * 0.22f, cy + r * 0.29f),
            20f, 140f, false, stroke
        )
        fill.color = withAlpha(CORAL, 115)
        canvas.drawCircle(cx - r * 0.6f, cy + r * 0.15f, r * 0.12f, fill)
        canvas.drawCircle(cx + r * 0.6f, cy + r * 0.15f, r * 0.12f, fill)

        // Personality prop matching PersonalityMascot.
        when (personality) {
            MoneyPersonality.HAPPY_FOODIE -> {
                fill.color = CORAL
                canvas.drawArc(
                    RectF(cx - r * 0.55f, cy + r * 0.45f, cx + r * 0.55f, cy + r * 1.15f),
                    0f, 180f, true, fill
                )
                stroke.color = DEEP_TEAL
                stroke.strokeWidth = size * 0.02f
                canvas.drawLine(cx + r * 0.2f, cy + r * 0.2f, cx + r * 0.55f, cy + r * 0.85f, stroke)
            }
            MoneyPersonality.SAVINGS_SUPERSTAR -> {
                val star = Path()
                val sy = cy - r * 1.35f
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) r * 0.38f else r * 0.16f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = cx + (rad * cos(a)).toFloat()
                    val y = sy + (rad * sin(a)).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                fill.color = SUNNY
                canvas.drawPath(star, fill)
                stroke.color = DEEP_TEAL
                stroke.strokeWidth = size * 0.012f
                canvas.drawPath(star, stroke)
            }
            MoneyPersonality.CAREFUL_PLANNER -> {
                val rect = RectF(cx + r * 0.55f, cy + r * 0.05f, cx + r * 1.15f, cy + r * 0.85f)
                fill.color = WHITE
                canvas.drawRoundRect(rect, r * 0.08f, r * 0.08f, fill)
                stroke.color = DEEP_TEAL
                stroke.strokeWidth = size * 0.012f
                canvas.drawRoundRect(rect, r * 0.08f, r * 0.08f, stroke)
                stroke.color = INCOME_GREEN
                stroke.strokeWidth = size * 0.022f
                canvas.drawLine(rect.left + r * 0.12f, rect.top + r * 0.42f, rect.left + r * 0.25f, rect.top + r * 0.56f, stroke)
                canvas.drawLine(rect.left + r * 0.25f, rect.top + r * 0.56f, rect.left + r * 0.48f, rect.top + r * 0.22f, stroke)
            }
            MoneyPersonality.LITTLE_EXPLORER -> {
                val ccx = cx + r * 0.85f
                val ccy = cy + r * 0.45f
                fill.color = WHITE
                canvas.drawCircle(ccx, ccy, r * 0.32f, fill)
                stroke.color = DEEP_TEAL
                stroke.strokeWidth = size * 0.012f
                canvas.drawCircle(ccx, ccy, r * 0.32f, stroke)
                stroke.color = CORAL
                stroke.strokeWidth = size * 0.022f
                canvas.drawLine(ccx, ccy + r * 0.2f, ccx, ccy - r * 0.2f, stroke)
            }
        }
    }

    private fun MoneyPersonality.cardTint(): Int = when (this) {
        MoneyPersonality.HAPPY_FOODIE -> PEACH
        MoneyPersonality.SAVINGS_SUPERSTAR -> SUNNY
        MoneyPersonality.CAREFUL_PLANNER -> MINT
        MoneyPersonality.LITTLE_EXPLORER -> CORAL
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
