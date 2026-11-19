package com.diniy.duit.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diniy.duit.BuildConfig
import com.diniy.duit.R

@Composable
internal fun EditionUpdatePrompt() {
    if (BuildConfig.BETA_REPOSITORY.isEmpty()) return
    val model: BetaUpdateModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            model.refreshPermission()
            model.check()
        }
    }

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
    when (val update = state) {
        is BetaUpdateState.Offer -> UpdateDialog(
            title = R.string.beta_update_available,
            message = stringResource(R.string.beta_update_build, update.release.versionCode),
            action = R.string.beta_update_download,
            onAction = model::download,
            onDismiss = model::dismiss
        )
        is BetaUpdateState.Permission -> UpdateDialog(
            title = R.string.beta_update_allow,
            message = stringResource(R.string.beta_update_permission),
            action = R.string.beta_update_settings,
            onAction = {
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
            onDismiss = model::dismiss
        )
        is BetaUpdateState.Downloading -> ProgressDialog(
            title = R.string.beta_update_downloading,
            onCancel = model::dismiss
        )
        is BetaUpdateState.AwaitingInstaller -> ProgressDialog(
            title = R.string.beta_update_preparing
        )
        is BetaUpdateState.Error -> UpdateDialog(
            title = R.string.beta_update_failed,
            message = null,
            action = R.string.beta_update_retry,
            onAction = model::download,
            onDismiss = model::dismiss
        )
        BetaUpdateState.Idle, is BetaUpdateState.Confirm, is BetaUpdateState.Installing -> Unit
    }
}

@Composable
private fun UpdateDialog(
    @StringRes title: Int,
    message: String?,
    @StringRes action: Int,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = if (message == null) null else ({ Text(message) }),
        confirmButton = { Button(onClick = onAction) { Text(stringResource(action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.beta_update_later)) } }
    )
}

@Composable
private fun ProgressDialog(@StringRes title: Int, onCancel: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = { onCancel?.invoke() },
        properties = DialogProperties(dismissOnBackPress = onCancel != null, dismissOnClickOutside = false),
        title = { DialogTitle(title) },
        text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.beta_update_cancel)) }
            }
        }
    )
}

@Composable
private fun DialogTitle(@StringRes title: Int) {
    Text(stringResource(title), modifier = Modifier.semantics { heading() })
}
