package com.diniy.duit.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.components.CandyBubbleCard
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

@Composable
internal fun EditionUpdatePrompt() {
    if (BuildConfig.BETA_REPOSITORY.isEmpty()) return
    val model: BetaUpdateModel = viewModel()
    val whatsNew by model.whatsNew.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

    // The post-update welcome comes first; the update check waits until it is dismissed, so two
    // modals never stack. This composable only runs after App Lock and onboarding are done.
    val welcome = whatsNew
    if (welcome != null) {
        if (lifecycleState == Lifecycle.State.RESUMED) WhatsNewScreen(welcome, onDone = model::acknowledgeWhatsNew)
        return
    }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) model.check()
    }
    UpdateDialogs(model)
}

/** All update dialogs; shared by the app shell and the Settings card (which is shown on its own screen). */
@Composable
internal fun UpdateDialogs(model: BetaUpdateModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) model.refreshPermission()
    }

    // Android's own installer confirmation is always launched; nothing here can skip it.
    LaunchedEffect(state, lifecycleState) {
        val confirmation = state as? BetaUpdateState.Confirm ?: return@LaunchedEffect
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
        is BetaUpdateState.Offer -> UpdateOfferDialog(update, current, onUpdate = model::download, onLater = model::dismiss)
        is BetaUpdateState.Permission -> CandyUpdateDialog(onDismiss = model::dismiss) {
            StatusHeader(Icons.Rounded.Security, CandyTone.Sunny, "One quick permission")
            Body("To install updates, allow duit. to install apps in Android settings. You'll still confirm every install.")
            Actions(
                primary = "Open settings",
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
        is BetaUpdateState.Downloading -> CandyUpdateDialog(onDismiss = model::dismiss, dismissible = false) {
            DownloadContent(update, onCancel = model::dismiss)
        }
        is BetaUpdateState.AwaitingInstaller -> CandyUpdateDialog(onDismiss = {}, dismissible = false) {
            StatusHeader(Icons.Rounded.Inventory2, CandyTone.Mint, "Checking the update")
            Body("Verifying the download and its signature, then handing over to Android's installer.")
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = candyTone(CandyTone.Mint).fill,
                trackColor = SpatialTheme.colors.glassSurfaceElevated
            )
        }
        is BetaUpdateState.Error -> CandyUpdateDialog(onDismiss = model::dismiss) {
            val copy = problemCopy(update.problem)
            StatusHeader(copy.icon, CandyTone.Coral, copy.title)
            Body(copy.message)
            Actions(
                primary = if (update.problem.canRetry) "Try again" else "Close",
                onPrimary = if (update.problem.canRetry) model::download else model::dismiss,
                secondary = if (update.problem.canRetry) "Maybe later" else null,
                onSecondary = model::dismiss
            )
        }
        BetaUpdateState.Idle, is BetaUpdateState.Confirm, is BetaUpdateState.Installing -> Unit
    }
}

// ------------------------------------------------------------------------------------------------

/** Shared Candy card dialog (core); kept as a name so the update states read as before. */
@Composable
private fun CandyUpdateDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) = com.diniy.duit.core.ui.components.CandyDialog(onDismiss, dismissible, content)

/** The dialog the GitHub update checker shows for an available update. */
@Composable
internal fun UpdateOfferDialog(offer: BetaUpdateState.Offer, installedVersion: String, onUpdate: () -> Unit, onLater: () -> Unit) {
    CandyUpdateDialog(onDismiss = onLater) { OfferContent(offer, installedVersion, onUpdate, onLater) }
}

@Composable
private fun ColumnScope.OfferContent(
    offer: BetaUpdateState.Offer,
    installedVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val release = offer.release
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val haptic = rememberDuitHaptic()
    StatusHeader(Icons.Rounded.RocketLaunch, CandyTone.Coral, "A little update for you!", bounce = true)
    Text(
        "Fresh improvements for duit.",
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
        CandyStatusBadge("Install cancelled. You can try again anytime.", CandyTone.Sunny, Modifier.align(Alignment.CenterHorizontally))
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

@Composable
private fun ColumnScope.DownloadContent(download: BetaUpdateState.Downloading, onCancel: () -> Unit) {
    val reduced = rememberReducedMotionEnabled()
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    StatusHeader(Icons.Rounded.Download, CandyTone.Mint, "Downloading update")
    Spacer(Modifier.height(18.dp))
    val known = download.progress >= 0f
    val shown by animateFloatAsState(
        targetValue = download.progress.coerceIn(0f, 1f),
        animationSpec = if (reduced) tween(0) else tween(320),
        label = "update_progress"
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
            "Connecting…"
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

/** Release notes as plain text inside a scrollable panel; the surrounding buttons always stay visible. */
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
        val sections = remember(notes) { parseReleaseNotes(notes.orEmpty()) }
        when {
            notes == null && !unavailable -> Text("Loading release notes…", style = type.supportingInfo, color = colors.secondaryText)
            sections.isEmpty() -> {
                NotesHeading("✨", "What's New")
                Text(
                    if (unavailable) "We couldn't load the release notes right now. Everything else is ready to go."
                    else "This update doesn't include release notes.",
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

/** Post-update welcome: shown once per newly installed build, after App Lock. */
@Composable
private fun WhatsNewScreen(state: WhatsNewState, onDone: () -> Unit) {
    val haptic = rememberDuitHaptic()
    CandyUpdateDialog(onDismiss = onDone, dismissible = false) {
        StatusHeader(Icons.Rounded.Celebration, CandyTone.Mint, "Welcome to the new duit.!", bounce = true)
        Text(
            "Here's what's fresh in your money world.",
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
            text = "Let's Go!",
            onClick = {
                haptic(HapticFeedbackType.Confirm)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Settings > About: channel, current version, last check and a manual check. Beta builds only. */
@Composable
internal fun EditionUpdateSettingsCard() {
    if (BuildConfig.BETA_REPOSITORY.isEmpty()) return
    val model: BetaUpdateModel = viewModel()
    val (checkedAt, result) = model.lastCheck.collectAsStateWithLifecycle().value
    val checking by model.checking.collectAsStateWithLifecycle()
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    CandyBubbleCard(tone = CandyTone.Mint, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(Icons.Rounded.SystemUpdate, CandyTone.Mint, size = 44.dp, tilt = -6f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App updates", style = type.cardTitle, color = colors.primaryText)
                Text(
                    if ("beta" in model.installedVersionName) "Beta channel · GitHub releases" else "Stable channel · GitHub releases",
                    style = type.caption,
                    color = colors.secondaryText
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Current version: v${model.installedVersionName}", style = type.supportingInfo, color = colors.primaryText)
        Text(
            if (checkedAt == 0L) "Not checked yet"
            else "Last checked ${android.text.format.DateUtils.getRelativeTimeSpanString(checkedAt)} · $result",
            style = type.caption,
            color = colors.secondaryText,
            modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.height(12.dp))
        PrismaticButton(
            text = if (checking) "Checking…" else "Check for updates",
            onClick = model::checkNow,
            isLoading = checking,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        )
    }
    // The app-shell prompt is not on screen inside Settings, so offer any result right here.
    UpdateDialogs(model)
}

// ------------------------------------------------------------------------------------------------

@Composable
private fun StatusHeader(icon: ImageVector, tone: CandyTone, title: String, bounce: Boolean = false) {
    val reduced = rememberReducedMotionEnabled()
    // One gentle bounce when the dialog appears; no looping.
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

private data class ProblemCopy(val icon: ImageVector, val title: String, val message: String)

private fun problemCopy(problem: UpdateProblem): ProblemCopy = when (problem) {
    UpdateProblem.NETWORK -> ProblemCopy(Icons.Rounded.CloudOff, "You're offline",
        "We couldn't reach GitHub. Check your internet connection and try again.")
    UpdateProblem.TIMEOUT -> ProblemCopy(Icons.Rounded.CloudOff, "The connection timed out",
        "GitHub took too long to respond. Check your connection and try again.")
    UpdateProblem.SERVER -> ProblemCopy(Icons.Rounded.CloudOff, "GitHub is unavailable",
        "The update server didn't respond as expected. Please try again in a little while.")
    UpdateProblem.INVALID_APK -> ProblemCopy(Icons.Rounded.GppBad, "Download didn't check out",
        "The downloaded file failed its safety check, so it wasn't installed. Trying again downloads a fresh copy.")
    UpdateProblem.SIGNATURE -> ProblemCopy(Icons.Rounded.GppBad, "This update can't be trusted",
        "Its signature doesn't match duit., so it was blocked to keep your data safe. Nothing was changed.")
    UpdateProblem.STORAGE -> ProblemCopy(Icons.Rounded.SdStorage, "Not enough space",
        "Free up some storage on your phone, then try again.")
    UpdateProblem.INSTALL -> ProblemCopy(Icons.Rounded.SentimentDissatisfied, "Install didn't finish",
        "Android couldn't complete the install. Your current app and data are unchanged.")
    UpdateProblem.GENERIC -> ProblemCopy(Icons.Rounded.SentimentDissatisfied, "Update couldn't continue",
        "Something went wrong. Your current app and data are unchanged.")
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    else String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
