package com.diniy.duit.support

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import java.io.File

/**
 * The developer's official TNG DuitNow QR, supplied as app/src/beta/res/raw/support_duitnow_qr.png.
 * Kept in res/raw so its bytes are never re-encoded and stay scannable. 0 means it isn't configured.
 */
internal fun supportQrResource(context: Context): Int =
    context.resources.getIdentifier(QR_RESOURCE_NAME, "raw", context.packageName)

/** Hidden unless a real QR is bundled (or a local QA build asks to preview the placeholder). */
internal fun isSupportPageAvailable(context: Context): Boolean =
    supportQrResource(context) != 0 || BuildConfig.SUPPORT_PREVIEW

/** Settings > Support duit. A plain page: no payments, tracking or confirmation happen in the app. */
@Composable
internal fun SupportDuitScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val qrRes = remember { supportQrResource(context) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SupportContent(
            qrRes = qrRes,
            onSave = { saveSupportQr(context, qrRes) },
            onShare = { shareSupportQr(context, qrRes) },
            onClose = onClose
        )
    }
}

@Composable
internal fun SupportContent(qrRes: Int, onSave: () -> Boolean, onShare: () -> Unit, onClose: () -> Unit) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val context = LocalContext.current
    val reduced = rememberReducedMotionEnabled()
    val pop = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) { if (!reduced) pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val qrBitmap = remember(qrRes) {
        if (qrRes == 0) null
        else runCatching { context.resources.openRawResource(qrRes).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    val hasQr = qrBitmap != null

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.Center) {
        CandyBubbleCard(
            tone = CandyTone.Peach,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                CandyIconBlob(Icons.Rounded.LocalCafe, CandyTone.Coral, size = 68.dp, tilt = -8f, solid = true,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                Spacer(Modifier.height(12.dp))
                Text("Support duit. 💛", style = type.screenHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
                Text("Buy me a coffee!", style = type.cardTitle, color = colors.secondaryText)
                Spacer(Modifier.height(10.dp))
                Text(
                    "duit. is made by an independent developer. If it helps you, a small contribution keeps updates and new features coming. Any amount is welcome.",
                    style = type.supportingInfo,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                // QR stays on white with a quiet border in both themes so payment apps can scan it.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(236.dp)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .padding(14.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "DuitNow QR code for supporting duit.",
                            // Smooth downscaling keeps the modules clean; verified to decode at this size.
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "QR code coming soon" }
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = Color(0xFFB8C2C6), modifier = Modifier.size(96.dp))
                            Text("QR code coming soon", style = type.caption, color = Color(0xFF55696F), textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Scan with TNG eWallet or any DuitNow-enabled banking app. The recipient name shown before you pay is the developer's.",
                    style = type.caption,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
                saveMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = type.caption, color = colors.primaryText, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrismaticButton(
                        text = "Save QR",
                        onClick = { saveMessage = if (onSave()) "Saved to Pictures/duit." else "Couldn't save the QR. Please try again." },
                        enabled = hasQr,
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(text = "Share", onClick = onShare, enabled = hasQr, variant = PrismaticButtonVariant.Secondary, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                CandyStatusBadge("100% optional. duit. stays free!", CandyTone.Mint)
                Spacer(Modifier.height(8.dp))
                PrismaticButton(text = "Close", onClick = onClose, variant = PrismaticButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Copies the original QR bytes into Pictures/duit. via MediaStore; no storage permission is needed. */
internal fun saveSupportQr(context: Context, qrRes: Int): Boolean {
    if (qrRes == 0) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "duit-support-qr.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/duit.")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        resolver.openOutputStream(uri)!!.use { out -> context.resources.openRawResource(qrRes).use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    }.getOrElse {
        resolver.delete(uri, null, null)
        false
    }
}

/** Shares the original QR image through Android's share sheet using a private FileProvider URI. */
internal fun shareSupportQr(context: Context, qrRes: Int) {
    if (qrRes == 0) return
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "duit-support-qr.png")
        file.outputStream().use { out -> context.resources.openRawResource(qrRes).use { it.copyTo(out) } }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".supportqr", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Support duit. with a coffee via DuitNow QR 💛")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private const val QR_RESOURCE_NAME = "support_duitnow_qr"
