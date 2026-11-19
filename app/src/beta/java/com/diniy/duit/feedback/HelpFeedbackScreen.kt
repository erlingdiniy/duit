package com.diniy.duit.feedback

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface FeedbackUiState {
    data object Editing : FeedbackUiState
    data object Sending : FeedbackUiState
    data object Sent : FeedbackUiState
    data class Error(val title: String, val message: String, val canRetry: Boolean) : FeedbackUiState
}

internal class FeedbackModel(
    app: Application,
    private val sender: FeedbackSender
) : AndroidViewModel(app) {
    // viewModel() uses AndroidViewModelFactory, which needs exactly this (Application) constructor.
    constructor(app: Application) : this(app, AppsScriptFeedbackSender(app))

    private val mutableState = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Editing)
    val state = mutableState.asStateFlow()

    /** Ignored while a request is running, so repeated taps can never send twice. */
    fun submit(category: FeedbackCategory, message: String, email: String) {
        if (mutableState.value == FeedbackUiState.Sending) return
        if (FeedbackRules.messageError(message) != null || FeedbackRules.emailError(email) != null) return
        mutableState.value = FeedbackUiState.Sending
        viewModelScope.launch {
            mutableState.value = when (val result = sender.send(category, message, email)) {
                FeedbackResult.Sent -> FeedbackUiState.Sent
                is FeedbackResult.Failed -> if (result.offline) {
                    FeedbackUiState.Error("You're offline", "We couldn't reach the server. Check your connection and try again.", canRetry = true)
                } else {
                    FeedbackUiState.Error("Couldn't send", "Something went wrong on the way. Please try again in a moment.", canRetry = true)
                }
                is FeedbackResult.Rejected -> rejection(result.code)
            }
        }
    }

    fun backToForm() {
        if (mutableState.value is FeedbackUiState.Error) mutableState.value = FeedbackUiState.Editing
    }

    private fun rejection(code: String): FeedbackUiState.Error = when (code) {
        "DUPLICATE" -> FeedbackUiState.Error("Already received", "Looks like you sent this message a moment ago. Thank you!", canRetry = false)
        "RATE_LIMITED", "DAILY_LIMIT_REACHED", "BUSY_TRY_LATER" ->
            FeedbackUiState.Error("Lots of messages right now", "Please try again a little later.", canRetry = true)
        "MESSAGE_TOO_SHORT", "MESSAGE_TOO_LONG", "INVALID_EMAIL" ->
            FeedbackUiState.Error("Please check your message", "Something in the form needs a quick fix.", canRetry = false)
        else -> FeedbackUiState.Error("Couldn't send", "The server couldn't accept your feedback. Please try again later.", canRetry = true)
    }
}

/** Settings > Help & Feedback. Full-screen Candy sheet; sends only the documented fields. */
@Composable
internal fun HelpFeedbackScreen(onClose: () -> Unit) {
    val model: FeedbackModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = { if (state != FeedbackUiState.Sending) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(enabled = state == FeedbackUiState.Sending) { /* stay until the request finishes */ }
        val colors = SpatialTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(12.dp)
        ) {
            CandyBubbleCard(
                tone = CandyTone.Peach,
                shape = RoundedCornerShape(32.dp),
                containerColor = colors.appBackground,
                modifier = Modifier.fillMaxSize()
            ) {
                when (val current = state) {
                    FeedbackUiState.Sent -> SentContent(onClose)
                    is FeedbackUiState.Error -> ErrorContent(current, onRetry = model::backToForm, onClose = onClose)
                    else -> FormContent(sending = current == FeedbackUiState.Sending, onSubmit = model::submit, onClose = onClose)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FormContent(
    sending: Boolean,
    onSubmit: (FeedbackCategory, String, String) -> Unit,
    onClose: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    var category by rememberSaveable { mutableStateOf(FeedbackCategory.BUG) }
    var message by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var triedSubmit by rememberSaveable { mutableStateOf(false) }
    val messageError = FeedbackRules.messageError(message)
    val emailError = FeedbackRules.emailError(email)

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Header(Icons.Rounded.Forum, CandyTone.Coral, "Help & Feedback", "Tell us what's working and what isn't. We read every message.")
        Spacer(Modifier.height(18.dp))
        Text("What's this about?", style = type.cardTitle, color = colors.primaryText)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackCategory.entries.forEach { option ->
                SpatialGlassPill(
                    label = "${option.emoji}  ${option.label}",
                    selected = category == option,
                    onClick = { if (!sending) category = option },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = option.label }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrismaticTextField(
            value = message,
            onValueChange = { if (it.length <= FeedbackRules.MESSAGE_MAX + 200) message = it },
            label = "Your message",
            placeholder = when (category) {
                FeedbackCategory.BUG -> "What happened, and what did you expect?"
                FeedbackCategory.FEATURE -> "What would you like duit. to do?"
                FeedbackCategory.GENERAL -> "Anything you'd like to share"
            },
            singleLine = false,
            enabled = !sending,
            errorText = if (triedSubmit) messageError else null,
            supportingText = "${message.trim().length} / ${FeedbackRules.MESSAGE_MAX}",
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
        )
        Spacer(Modifier.height(12.dp))
        PrismaticTextField(
            value = email,
            onValueChange = { if (it.length <= 254) email = it },
            label = "Email (optional)",
            placeholder = "Only if you'd like a reply",
            singleLine = true,
            enabled = !sending,
            errorText = if (triedSubmit) emailError else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Your message, category, app version and optional email are sent to the developer through Google services (Google Sheets). " +
                "Please don't include passwords, PINs or financial details. duit. never sends your balances, transactions or backups.",
            style = type.caption,
            color = colors.secondaryText
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PrismaticButton(text = "Close", onClick = onClose, variant = PrismaticButtonVariant.Ghost, enabled = !sending, modifier = Modifier.weight(1f))
        PrismaticButton(
            text = if (sending) "Sending…" else "Send",
            onClick = {
                triedSubmit = true
                if (messageError == null && emailError == null) onSubmit(category, message, email)
            },
            isLoading = sending,
            enabled = !sending,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun ColumnScope.SentContent(onClose: () -> Unit) {
    val haptic = rememberDuitHaptic()
    LaunchedEffect(Unit) { haptic(HapticFeedbackType.Confirm) }
    Spacer(Modifier.weight(1f))
    Header(Icons.Rounded.Favorite, CandyTone.Mint, "Thank you!", "Your feedback is on its way. It helps make duit. better for everyone.", bounce = true)
    Spacer(Modifier.weight(1f))
    PrismaticButton(text = "Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.ErrorContent(error: FeedbackUiState.Error, onRetry: () -> Unit, onClose: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Header(
        if (error.title == "You're offline") Icons.Rounded.CloudOff else Icons.Rounded.SentimentDissatisfied,
        CandyTone.Sunny,
        error.title,
        error.message
    )
    Spacer(Modifier.weight(1f))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PrismaticButton(text = "Close", onClick = onClose, variant = PrismaticButtonVariant.Ghost, modifier = Modifier.weight(1f))
        // Retry returns to the filled form, so nothing is resent without the user pressing Send.
        PrismaticButton(text = if (error.canRetry) "Try again" else "Edit message", onClick = onRetry, modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun Header(icon: ImageVector, tone: CandyTone, title: String, subtitle: String, bounce: Boolean = false) {
    val reduced = rememberReducedMotionEnabled()
    val pop = remember { Animatable(if (bounce && !reduced) 0.5f else 1f) }
    LaunchedEffect(Unit) {
        if (bounce && !reduced) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CandyIconBlob(icon, tone, size = 64.dp, tilt = -8f, solid = true, modifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        })
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = SpatialTheme.typography.screenHeading,
            color = SpatialTheme.colors.primaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }
        )
        Text(
            subtitle,
            style = SpatialTheme.typography.supportingInfo,
            color = SpatialTheme.colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
