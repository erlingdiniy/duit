package com.diniy.duit.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyDialog
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.candyTone
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import java.util.Locale

/**
 * Automatic private developer update check & post-update welcome dialog for `betaDev`.
 * Checks only the configured private GitHub repository using the Keystore-sealed GitHub PAT;
 * public release repositories are rejected at compile and runtime.
 */
@Composable
internal fun EditionUpdatePrompt() {
    val model: PrivateDevUpdateModel = viewModel()
    val whatsNew by model.whatsNew.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

    val welcome = whatsNew
    if (welcome != null) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            PrivateWhatsNewScreen(welcome, onDone = model::acknowledgeWhatsNew)
        }
        return
    }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) model.check()
    }
    PrivateUpdateDialogs(model)
}

/** Settings > About card for checking private developer-enabled updates manually. */
@Composable
internal fun EditionUpdateSettingsCard() {
    val model: PrivateDevUpdateModel = viewModel()
    val (checkedAt, result) = model.lastCheck.collectAsStateWithLifecycle().value
    val checking by model.checking.collectAsStateWithLifecycle()
    val repo by model.repo.collectAsStateWithLifecycle()
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography

    CandyBubbleCard(
        tone = CandyTone.Mint,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(Icons.Rounded.Security, CandyTone.Mint, size = 44.dp, tilt = -6f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Private updates", style = type.cardTitle, color = colors.primaryText)
                Text(
                    "Private repo · Protected",
                    style = type.caption,
                    color = colors.secondaryText
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Current version: v${model.installedVersionName}", style = type.supportingInfo, color = colors.primaryText)
        Text(
            "Source: $repo",
            style = type.caption,
            color = colors.secondaryText,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            if (checkedAt == 0L) "Not checked yet"
            else "Last checked ${DateUtils.getRelativeTimeSpanString(checkedAt)} · $result",
            style = type.caption,
            color = colors.secondaryText,
            modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.height(12.dp))
        PrismaticButton(
            text = if (checking) "Checking…" else "Check Now",
            onClick = model::checkNow,
            isLoading = checking,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        )
    }
    PrivateUpdateDialogs(model)
}

@Composable
internal fun PrivateUpdateDialogs(model: PrivateDevUpdateModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) model.refreshPermission()
    }

    LaunchedEffect(state, lifecycleState) {
        val confirmation = state as? PrivateUpdateState.Confirm ?: return@LaunchedEffect
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        model.confirmationLaunched()
        try {
            context.startActivity(confirmation.intent)
        } catch (_: ActivityNotFoundException) {
            model.confirmationFailed()
        } catch (_: SecurityException) {
            model.confirmationFailed()
        }
    }

    if (lifecycleState != Lifecycle.State.RESUMED) return
    val current = model.installedVersionName
    when (val update = state) {
        is PrivateUpdateState.Offer -> PrivateOfferDialog(
            offer = update,
            installedVersion = current,
            onUpdate = model::download,
            onLater = model::dismiss
        )
        is PrivateUpdateState.Permission -> CandyDialog(onDismiss = model::dismiss) {
            StatusHeader(Icons.Rounded.Security, CandyTone.Sunny, "Allow app installs")
            Body("To install private updates, allow duit. to install apps in Android settings. Android still asks before installing.")
            Actions(
                primary = "Open Settings",
                onPrimary = {
                    val settings = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    try {
                        context.startActivity(settings)
                    } catch (_: ActivityNotFoundException) {
                        model.settingsUnavailable()
                    } catch (_: SecurityException) {
                        model.settingsUnavailable()
                    }
                },
                secondary = "Maybe Later",
                onSecondary = model::dismiss
            )
        }
        is PrivateUpdateState.Downloading -> CandyDialog(onDismiss = model::dismiss, dismissible = false) {
            DownloadContent(update, onCancel = model::dismiss)
        }
        is PrivateUpdateState.AwaitingInstaller -> CandyDialog(onDismiss = {}, dismissible = false) {
            StatusHeader(Icons.Rounded.Inventory2, CandyTone.Mint, "Verifying update")
            Body("Checking SHA-256 hash and signing certificate, then opening Android installer.")
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = candyTone(CandyTone.Mint).fill,
                trackColor = SpatialTheme.colors.glassSurfaceElevated
            )
        }
        is PrivateUpdateState.Error -> CandyDialog(onDismiss = model::dismiss) {
            val copy = problemCopy(update.problem)
            StatusHeader(copy.icon, CandyTone.Coral, copy.title)
            Body(copy.message)
            Actions(
                primary = if (update.problem.canRetry) "Try Again" else "Close",
                onPrimary = if (update.problem.canRetry) model::download else model::dismiss,
                secondary = if (update.problem.canRetry) "Maybe Later" else null,
                onSecondary = model::dismiss
            )
        }
        PrivateUpdateState.Idle, is PrivateUpdateState.Confirm, is PrivateUpdateState.Installing -> Unit
    }
}

@Composable
private fun PrivateOfferDialog(
    offer: PrivateUpdateState.Offer,
    installedVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val release = offer.release
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val haptic = rememberDuitHaptic()

    CandyDialog(onDismiss = onLater) {
        StatusHeader(Icons.Rounded.RocketLaunch, CandyTone.Coral, "Private update ready", bounce = true)
        Text(
            "Developer Center update from private repo.",
            style = type.supportingInfo,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        VersionRow(
            current = installedVersion.ifBlank { "Installed" },
            available = release.versionName.ifBlank { "Build ${release.versionCode}" }
        )
        val meta = listOfNotNull(
            release.publishedDate.takeIf { it.isNotBlank() }?.let { "Released $it" },
            formatSize(release.size)
        ).joinToString("  ·  ")
        Text(
            meta,
            style = type.caption,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (offer.cancelled) {
            Spacer(Modifier.height(10.dp))
            CandyStatusBadge("Install cancelled. Try again anytime.", CandyTone.Sunny, Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(14.dp))
        Text("What's New?", style = type.cardTitle, color = colors.primaryText, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        NotesPanel(notes = release.notes, modifier = Modifier.weight(1f, fill = false))
        Actions(
            primary = "Update Now",
            onPrimary = {
                haptic(HapticFeedbackType.Confirm)
                onUpdate()
            },
            secondary = "Maybe Later",
            onSecondary = onLater
        )
    }
}

@Composable
private fun ColumnScope.DownloadContent(download: PrivateUpdateState.Downloading, onCancel: () -> Unit) {
    val reduced = rememberReducedMotionEnabled()
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    StatusHeader(Icons.Rounded.Download, CandyTone.Mint, "Downloading update")
    Spacer(Modifier.height(18.dp))
    val known = download.progress >= 0f
    val shown by animateFloatAsState(
        targetValue = download.progress.coerceIn(0f, 1f),
        animationSpec = if (reduced) tween(0) else tween(320),
        label = "private_update_progress"
    )
    if (known) {
        LinearProgressIndicator(
            progress = { shown },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = candyTone(CandyTone.Mint).fill,
            trackColor = colors.glassSurfaceElevated,
            drawStopIndicator = {}
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = candyTone(CandyTone.Mint).fill,
            trackColor = colors.glassSurfaceElevated
        )
    }
    val size = download.release.size
    Text(
        text = if (known) {
            "${(download.progress * 100).toInt()}%  ·  ${formatSize((size * download.progress).toLong())} of ${formatSize(size)}"
        } else {
            "Authenticating…"
        },
        style = type.caption,
        color = colors.secondaryText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
    Actions(primary = null, onPrimary = {}, secondary = "Cancel", onSecondary = onCancel)
}

@Composable
private fun NotesPanel(notes: String?, modifier: Modifier = Modifier, unavailable: Boolean = false) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.glassSurface.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        val sections = remember(notes) { parsePrivateReleaseNotes(notes.orEmpty()) }
        when {
            notes == null && !unavailable -> Text("Loading release notes…", style = type.supportingInfo, color = colors.secondaryText)
            sections.isEmpty() -> {
                NotesHeading("✨", "What's New")
                Text(
                    if (unavailable) "Release notes unavailable right now."
                    else "This release doesn't include notes.",
                    style = type.supportingInfo,
                    color = colors.secondaryText
                )
            }
        }
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            if (section.title.isNotBlank()) NotesHeading(section.icon, section.title)
            section.items.forEach { item ->
                Text(
                    text = if (item.bullet) "•  ${item.text}" else item.text,
                    style = type.supportingInfo,
                    color = colors.primaryText,
                    modifier = Modifier.padding(top = 4.dp, start = if (item.bullet) 4.dp else 0.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivateWhatsNewScreen(state: PrivateWhatsNewState, onDone: () -> Unit) {
    val haptic = rememberDuitHaptic()
    CandyDialog(onDismiss = onDone, dismissible = false) {
        StatusHeader(Icons.Rounded.Celebration, CandyTone.Mint, "Private build updated", bounce = true)
        Text(
            "Developer Center & your financial data remain intact.",
            style = SpatialTheme.typography.supportingInfo,
            color = SpatialTheme.colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        CandyStatusBadge(
            "v${state.versionName.ifBlank { "build ${state.versionCode}" }}",
            CandyTone.Mint,
            Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(14.dp))
        NotesPanel(notes = state.notes, unavailable = state.unavailable, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(18.dp))
        PrismaticButton(
            text = "Done",
            onClick = {
                haptic(HapticFeedbackType.Confirm)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusHeader(icon: ImageVector, tone: CandyTone, title: String, bounce: Boolean = false) {
    val reduced = rememberReducedMotionEnabled()
    val pop = remember { Animatable(if (bounce && !reduced) 0.6f else 1f) }
    LaunchedEffect(Unit) {
        if (bounce && !reduced) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CandyIconBlob(
            icon = icon,
            tone = tone,
            size = 64.dp,
            tilt = -8f,
            solid = true,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = SpatialTheme.typography.screenHeading,
            color = SpatialTheme.colors.primaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun VersionRow(current: String, available: String) {
    val reduced = rememberReducedMotionEnabled()
    val shown = remember { MutableTransitionState(reduced).apply { targetState = true } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersionChip("Current", "v$current", CandyTone.Neutral)
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = SpatialTheme.colors.secondaryText,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(240, delayMillis = 140)) + slideInHorizontally(tween(240, delayMillis = 140)) { it / 3 }
        ) {
            VersionChip("New", "v$available", CandyTone.Mint)
        }
    }
}

@Composable
private fun VersionChip(label: String, version: String, tone: CandyTone) {
    val t = candyTone(tone)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(t.container, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(label, style = SpatialTheme.typography.caption, color = SpatialTheme.colors.secondaryText)
        Text(version, style = SpatialTheme.typography.cardTitle, color = SpatialTheme.colors.primaryText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotesHeading(icon: String, title: String) {
    Text(
        "$icon  $title",
        style = SpatialTheme.typography.cardTitle,
        color = SpatialTheme.colors.primaryText,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = SpatialTheme.typography.supportingInfo,
        color = SpatialTheme.colors.secondaryText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )
}

@Composable
private fun Actions(primary: String?, onPrimary: () -> Unit, secondary: String?, onSecondary: () -> Unit) {
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (secondary != null) {
            PrismaticButton(text = secondary, onClick = onSecondary, variant = PrismaticButtonVariant.Ghost, modifier = Modifier.weight(1f))
        }
        if (primary != null) {
            PrismaticButton(text = primary, onClick = onPrimary, variant = PrismaticButtonVariant.Primary, modifier = Modifier.weight(1.3f))
        }
    }
}

private data class PrivateProblemCopy(val icon: ImageVector, val title: String, val message: String)

private fun problemCopy(problem: PrivateUpdateProblem): PrivateProblemCopy = when (problem) {
    PrivateUpdateProblem.AUTH -> PrivateProblemCopy(
        Icons.Rounded.Security,
        "GitHub authorization needed",
        "Check that your GitHub PAT has Contents read access to your private update repository."
    )
    PrivateUpdateProblem.NOT_PRIVATE_REPO -> PrivateProblemCopy(
        Icons.Rounded.GppBad,
        "Public repository blocked",
        "Private updates must come from a private GitHub repository, not a public repo."
    )
    PrivateUpdateProblem.NETWORK -> PrivateProblemCopy(
        Icons.Rounded.CloudOff,
        "You're offline",
        "We couldn't reach GitHub. Check your internet connection and try again."
    )
    PrivateUpdateProblem.TIMEOUT -> PrivateProblemCopy(
        Icons.Rounded.CloudOff,
        "The connection timed out",
        "GitHub took too long to respond. Check your connection and try again."
    )
    PrivateUpdateProblem.INVALID_APK -> PrivateProblemCopy(
        Icons.Rounded.GppBad,
        "Verification failed",
        "The downloaded APK failed its SHA-256 or private metadata check, so nothing was changed."
    )
    PrivateUpdateProblem.SIGNATURE -> PrivateProblemCopy(
        Icons.Rounded.GppBad,
        "Signer mismatch",
        "The downloaded APK does not match your duit. signing certificate, so it was blocked."
    )
    PrivateUpdateProblem.STORAGE -> PrivateProblemCopy(
        Icons.Rounded.SdStorage,
        "Not enough space",
        "Free up some storage on your phone, then try again."
    )
    PrivateUpdateProblem.INSTALL -> PrivateProblemCopy(
        Icons.Rounded.SentimentDissatisfied,
        "Install didn't finish",
        "Android couldn't complete the install. Your data and current build are unchanged."
    )
    PrivateUpdateProblem.GENERIC -> PrivateProblemCopy(
        Icons.Rounded.SentimentDissatisfied,
        "Update couldn't continue",
        "Something went wrong. Your current app and data are unchanged."
    )
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    else String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
