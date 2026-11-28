package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.diniy.duit.config.RemoteAppConfigPolicy
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterValidators
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.RemoteConfigSourceState
import com.diniy.duit.devcenter.ValidationResult
import kotlin.math.roundToInt

@Composable
fun DevRemoteConfigScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    var showPublishDialog by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var rollbackVersionTarget by remember { mutableStateOf<String?>(null) }

    if (showPublishDialog) {
        RemoteConfigConfirmDialog(
            title = "Publish Remote Config",
            expectedPhrase = DevCenterValidators.REMOTE_CONFIG_PUBLISH_PHRASE,
            detail = "Publishes allowlisted UI flags with If-Match ETag verification.",
            requireReason = true,
            confirmLabel = "Publish",
            onDismiss = { showPublishDialog = false },
            onConfirm = { phrase, reason ->
                showPublishDialog = false
                viewModel.publishRemoteConfig(phrase, reason)
            }
        )
    }

    if (showEmergencyDialog) {
        RemoteConfigConfirmDialog(
            title = "Emergency Disable Flags",
            expectedPhrase = DevCenterValidators.REMOTE_CONFIG_PUBLISH_PHRASE,
            detail = "Turns off maintenance banner, experimental tips, and rollout notices immediately.",
            requireReason = false,
            confirmLabel = "Disable Flags",
            onDismiss = { showEmergencyDialog = false },
            onConfirm = { phrase, _ ->
                showEmergencyDialog = false
                viewModel.emergencyDisableRemoteConfig(phrase)
            }
        )
    }

    rollbackVersionTarget?.let { ver ->
        RemoteConfigConfirmDialog(
            title = "Rollback to v$ver",
            expectedPhrase = DevCenterValidators.REMOTE_CONFIG_ROLLBACK_PHRASE,
            detail = "Restores Firebase Remote Config template version v$ver.",
            requireReason = false,
            confirmLabel = "Rollback",
            onDismiss = { rollbackVersionTarget = null },
            onConfirm = { phrase, _ ->
                rollbackVersionTarget = null
                viewModel.rollbackRemoteConfig(ver, phrase)
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "rc_safety_guard") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Standard,
                accentTint = tokens.healthyTint
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Remote Flags")
                    DevToneBadge(label = "Safety Guard", tone = "healthy")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Only safe display flags can change here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
            }
        }

        when (val state = uiState.remoteConfigState) {
            RemoteConfigSourceState.Loading -> {
                item(key = "rc_loading") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Loading Remote Config")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            is RemoteConfigSourceState.Unavailable -> {
                item(key = "rc_unavailable") {
                    DevUnavailableCard(
                        title = "Remote Config Unavailable",
                        summary = state.reason,
                        actionLabel = "Open Settings",
                        onAction = onOpenSettings
                    )
                }
            }

            is RemoteConfigSourceState.Error -> {
                item(key = "rc_error") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Standard,
                        accentTint = colors.error
                    ) {
                        DevSectionTitle("Remote Config Error")
                        Spacer(Modifier.height(6.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Retry",
                                onClick = viewModel::refreshRemoteConfig,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Open Settings",
                                onClick = onOpenSettings,
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            is RemoteConfigSourceState.Ready -> {
                val edited = uiState.editedRemoteParams
                fun boolParam(key: String): Boolean =
                    (edited[key] ?: state.parameters.firstOrNull { it.key == key }?.defaultValue ?: "false")
                        .equals("true", ignoreCase = true)
                fun strParam(key: String): String =
                    edited[key] ?: state.parameters.firstOrNull { it.key == key }?.defaultValue.orEmpty()

                val bannerOn = boolParam(RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED)
                val bannerTitle = strParam(RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_TITLE)
                val bannerMsg = strParam(RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_MESSAGE)
                val tipsOn = boolParam(RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS)
                val rolloutOn = boolParam(RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED)
                val rolloutPct = strParam(RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT).toIntOrNull()?.coerceIn(0, 100) ?: 0
                val minVer = strParam(RemoteAppConfigPolicy.KEY_MIN_RECOMMENDED_VERSION_CODE)

                item(key = "rc_template_header") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Elevated) {
                        DevSectionTitle("Template v${state.versionNumber.ifBlank { "1" }}")
                        Spacer(Modifier.height(4.dp))
                        DevKeyValueRow(label = "ETag", value = state.etag.take(22))
                        DevKeyValueRow(label = "Updated", value = formatIsoShort(state.syncedAtIso))
                    }
                }

                item(key = "rc_controls") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Safe Product Flags")
                        Spacer(Modifier.height(8.dp))

                        RemoteSwitchRow(
                            title = "Maintenance Banner",
                            subtitle = RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED,
                            checked = bannerOn,
                            onCheckedChange = {
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED,
                                    it.toString()
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = bannerTitle,
                            onValueChange = {
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_TITLE,
                                    it.take(80)
                                )
                            },
                            label = "Banner Title (${bannerTitle.length}/80)",
                            placeholder = "Service Notice"
                        )
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = bannerMsg,
                            onValueChange = {
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_MESSAGE,
                                    it.take(240)
                                )
                            },
                            label = "Banner Message (${bannerMsg.length}/240)",
                            placeholder = "Wallets work normally offline.",
                            singleLine = false
                        )

                        Spacer(Modifier.height(10.dp))
                        RemoteSwitchRow(
                            title = "Experimental Insights Tips",
                            subtitle = RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS,
                            checked = tipsOn,
                            onCheckedChange = {
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS,
                                    it.toString()
                                )
                            }
                        )

                        Spacer(Modifier.height(8.dp))
                        RemoteSwitchRow(
                            title = "Gradual Rollout Notice",
                            subtitle = RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED,
                            checked = rolloutOn,
                            onCheckedChange = {
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED,
                                    it.toString()
                                )
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Rollout Target: $rolloutPct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primaryText
                        )
                        Slider(
                            value = rolloutPct.toFloat(),
                            onValueChange = {
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT,
                                    it.roundToInt().coerceIn(0, 100).toString()
                                )
                            },
                            valueRange = 0f..100f
                        )

                        Spacer(Modifier.height(6.dp))
                        PrismaticTextField(
                            value = minVer,
                            onValueChange = { raw ->
                                viewModel.updatePendingRemoteParam(
                                    RemoteAppConfigPolicy.KEY_MIN_RECOMMENDED_VERSION_CODE,
                                    raw.filter(Char::isDigit).take(12)
                                )
                            },
                            label = "Min Recommended Version Code",
                            placeholder = "1004000"
                        )
                    }
                }

                val diffs = state.parameters.mapNotNull { orig ->
                    val next = edited[orig.key]
                    if (next != null && next != orig.defaultValue) {
                        Triple(orig.key, orig.defaultValue, next)
                    } else null
                }

                item(key = "rc_preview_publish") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Pending Changes & Preview")
                        Spacer(Modifier.height(6.dp))
                        if (diffs.isEmpty()) {
                            Text(
                                text = "No unpublished edits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        } else {
                            diffs.forEach { (key, oldVal, newVal) ->
                                DevKeyValueRow(
                                    label = key,
                                    value = "${oldVal.ifBlank { "\"\"" }} -> ${newVal.ifBlank { "\"\"" }}"
                                )
                            }
                        }
                        if (bannerOn && bannerMsg.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            SpatialGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                tier = SpatialGlassTier.Elevated,
                                accentTint = colors.warning,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Text(
                                    text = bannerTitle.ifBlank { "Service Notice" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = bannerMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Publish",
                                onClick = { showPublishDialog = true },
                                enabled = !uiState.isPublishingRemoteConfig,
                                isLoading = uiState.isPublishingRemoteConfig,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Disable All",
                                onClick = { showEmergencyDialog = true },
                                enabled = !uiState.isPublishingRemoteConfig,
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (state.versions.isNotEmpty()) {
                    item(key = "rc_versions_header") {
                        DevSectionTitle("Version History", modifier = Modifier.padding(top = 4.dp))
                    }
                    items(state.versions.take(10), key = { it.versionNumber }) { ver ->
                        val canRollback = ver.versionNumber != state.versionNumber
                        SpatialGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { base ->
                                    if (canRollback && !uiState.isPublishingRemoteConfig) {
                                        base
                                            .clip(RoundedCornerShape(24.dp))
                                            .candyPressable(
                                                onClick = { rollbackVersionTarget = ver.versionNumber },
                                                onClickLabel = "Rollback to v${ver.versionNumber}"
                                            )
                                    } else base
                                },
                            tier = SpatialGlassTier.Standard,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Version v${ver.versionNumber}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "${ver.updateType.ifBlank { "UPDATE" }}, ${formatIsoShort(ver.updateTimeIso)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                                DevToneBadge(
                                    label = if (canRollback) "Tap to Restore" else "Active",
                                    tone = if (canRollback) "neutral" else "healthy"
                                )
                            }
                            if (ver.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = ver.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = SpatialTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RemoteConfigConfirmDialog(
    title: String,
    expectedPhrase: String,
    detail: String,
    requireReason: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (phrase: String, reason: String) -> Unit
) {
    var phraseInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    val valid = DevCenterValidators.validateRemoteConfigConfirmation(
        isRollback = expectedPhrase == DevCenterValidators.REMOTE_CONFIG_ROLLBACK_PHRASE,
        confirmationPhrase = phraseInput,
        rawReason = if (requireReason) reasonInput else "Emergency flag disable"
    ) is ValidationResult.Valid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
                PrismaticTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    label = "Type $expectedPhrase",
                    placeholder = expectedPhrase
                )
                if (requireReason) {
                    PrismaticTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = "Change summary",
                        placeholder = "Maintenance window update"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(phraseInput, reasonInput) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
