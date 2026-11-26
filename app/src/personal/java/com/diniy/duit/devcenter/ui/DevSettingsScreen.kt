package com.diniy.duit.devcenter.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.candySwitchColors
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevAuthUiState
import com.diniy.duit.devcenter.DevAutoLockTimeout
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.KillSwitchTarget

@Composable
fun DevSettingsScreen(
    uiState: DevCenterUiState,
    authState: DevAuthUiState,
    viewModel: DevCenterViewModel,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val vault = uiState.vault
    val ks = uiState.dashboard.killSwitches

    // Sensitive fields use remember (never rememberSaveable) so Binder saved state never stores credentials.
    var patInput by remember { mutableStateOf("") }
    var distRepoInput by remember(vault.distributionRepo) { mutableStateOf(vault.distributionRepo) }
    var consoleRepoInput by remember(vault.consoleRepo) { mutableStateOf(vault.consoleRepo) }
    var privateUpdateRepoInput by remember(vault.privateUpdateRepo) { mutableStateOf(vault.privateUpdateRepo) }
    var sheetIdInput by remember(vault.feedbackSheetId) { mutableStateOf(vault.feedbackSheetId) }
    var sheetRangeInput by remember(vault.feedbackSheetRange) { mutableStateOf(vault.feedbackSheetRange) }
    var sheetsTokenInput by remember { mutableStateOf("") }

    var githubExpanded by remember { mutableStateOf(vault.encryptedGithubPat.isNullOrBlank()) }
    var feedbackExpanded by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showWipeCredsDialog by remember { mutableStateOf(false) }

    if (showChangePinDialog) {
        ChangeDeveloperPinDialog(
            onDismiss = { showChangePinDialog = false },
            onConfirm = { curPin, newPin, confirmPin, onErr ->
                viewModel.authManager.changePin(curPin, newPin, confirmPin) { result ->
                    result.onSuccess { showChangePinDialog = false }
                    result.onFailure { onErr(it.message ?: "Failed to update PIN.") }
                }
            }
        )
    }

    if (showWipeCredsDialog) {
        AlertDialog(
            onDismissRequest = { showWipeCredsDialog = false },
            title = { Text("Clear Credentials") },
            text = {
                Text(
                    "Removes saved GitHub key and Sheets access from Developer Center storage. Financial data stays untouched.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeCredsDialog = false
                        viewModel.clearAllCredentials()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Clear Keys", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeCredsDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val hasPat = !vault.encryptedGithubPat.isNullOrBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "kill_switches") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                DevSectionTitle("Emergency Switches")
                Spacer(Modifier.height(8.dp))

                KillSwitchRow(
                    title = "Custom Broadcasts",
                    variableName = KillSwitchTarget.CUSTOM_BROADCAST.variableName,
                    enabled = ks.customBroadcastEnabled,
                    canMutate = hasPat && !uiState.isUpdatingKillSwitch,
                    onToggleRequest = { next ->
                        viewModel.requestKillSwitchToggle(KillSwitchTarget.CUSTOM_BROADCAST, next)
                    }
                )

                Spacer(Modifier.height(10.dp))

                KillSwitchRow(
                    title = "Release Automation",
                    variableName = KillSwitchTarget.RELEASE_AUTOMATION.variableName,
                    enabled = ks.releaseAutomationEnabled,
                    canMutate = hasPat && !uiState.isUpdatingKillSwitch,
                    onToggleRequest = { next ->
                        viewModel.requestKillSwitchToggle(KillSwitchTarget.RELEASE_AUTOMATION, next)
                    }
                )
            }
        }

        item(key = "github_connection") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .candyPressable(
                            onClick = { githubExpanded = !githubExpanded },
                            onClickLabel = "Toggle GitHub settings"
                        )
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        DevSectionTitle("GitHub Connection")
                        Text(
                            text = vault.githubPatHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DevToneBadge(
                            label = if (hasPat) "Saved" else "Unset",
                            tone = if (hasPat) "ok" else "warning"
                        )
                        Icon(
                            imageVector = if (githubExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = colors.secondaryText
                        )
                    }
                }

                AnimatedVisibility(visible = githubExpanded) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Keystore-encrypted GitHub key with Actions read/write and Contents read access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(8.dp))
                        SecretInputField(
                            value = patInput,
                            onValueChange = { patInput = it },
                            label = "GitHub PAT",
                            placeholder = if (hasPat) "Configured (${vault.githubPatHint})" else "github_pat_..."
                        )
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = distRepoInput,
                            onValueChange = { distRepoInput = it },
                            label = "Distribution Repo",
                            placeholder = "erlingdiniy/duit-beta"
                        )
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = consoleRepoInput,
                            onValueChange = { consoleRepoInput = it },
                            label = "Console Relay Repo",
                            placeholder = "erlingdiniy/duit-developer-console"
                        )
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = privateUpdateRepoInput,
                            onValueChange = { privateUpdateRepoInput = it },
                            label = "Private Updates Repo",
                            placeholder = "erlingdiniy/duit-developer-console"
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Save PAT",
                                onClick = {
                                    viewModel.saveGithubCredentials(
                                        patInput,
                                        distRepoInput,
                                        consoleRepoInput,
                                        privateUpdateRepoInput
                                    )
                                    patInput = ""
                                },
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Test Link",
                                onClick = viewModel::runConnectionHealthCheck,
                                enabled = hasPat && !uiState.isRefreshing,
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hasPat) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrismaticButton(
                                    text = "Remove PAT",
                                    onClick = viewModel::removeGithubPat,
                                    variant = PrismaticButtonVariant.Ghost,
                                    modifier = Modifier.weight(1f)
                                )
                                PrismaticButton(
                                    text = "Clear Keys",
                                    onClick = { showWipeCredsDialog = true },
                                    variant = PrismaticButtonVariant.Ghost,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "feedback_source") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .candyPressable(
                            onClick = { feedbackExpanded = !feedbackExpanded },
                            onClickLabel = "Toggle Feedback source settings"
                        )
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        DevSectionTitle("Feedback Source")
                        Text(
                            text = if (vault.feedbackSheetId.isBlank()) "CSV import or Sheets read-only" else vault.sheetsTokenHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                    Icon(
                        imageVector = if (feedbackExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.secondaryText
                    )
                }

                AnimatedVisibility(visible = feedbackExpanded) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = sheetIdInput,
                            onValueChange = { sheetIdInput = it },
                            label = "Google Sheet ID",
                            placeholder = "1AbC..."
                        )
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = sheetRangeInput,
                            onValueChange = { sheetRangeInput = it },
                            label = "Sheet Range",
                            placeholder = "Feedback!A1:F200"
                        )
                        Spacer(Modifier.height(8.dp))
                        SecretInputField(
                            value = sheetsTokenInput,
                            onValueChange = { sheetsTokenInput = it },
                            label = "Read-Only Sheets Access Key",
                            placeholder = "Optional ya29... bearer key"
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Save Source",
                                onClick = {
                                    viewModel.saveFeedbackSheetConfig(sheetIdInput, sheetRangeInput, sheetsTokenInput)
                                    sheetsTokenInput = ""
                                },
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Clear Source",
                                onClick = viewModel::clearFeedbackSource,
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item(key = "dev_auth_settings") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                DevSectionTitle("Developer Security")
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = authState.isBiometricEnabled,
                            role = Role.Switch,
                            onValueChange = viewModel.authManager::setBiometricEnabled
                        )
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Biometric Unlock", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                        Text("Quick fingerprint unlock", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    }
                    Switch(
                        checked = authState.isBiometricEnabled,
                        onCheckedChange = null,
                        colors = candySwitchColors(
                            personal = SwitchDefaults.colors(
                                checkedTrackColor = colors.coral,
                                checkedThumbColor = Color.White,
                                checkedBorderColor = colors.sticker,
                                uncheckedTrackColor = colors.baseSurface,
                                uncheckedThumbColor = colors.secondaryText,
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("Inactivity Auto-Lock", style = MaterialTheme.typography.labelLarge, color = colors.secondaryText)
                Spacer(Modifier.height(4.dp))
                DevChoiceChipRow(
                    items = DevAutoLockTimeout.entries,
                    selected = authState.autoLockTimeout,
                    labelOf = { it.label },
                    onSelect = viewModel.authManager::setAutoLockTimeout
                )

                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Change PIN",
                    onClick = { showChangePinDialog = true },
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun KillSwitchRow(
    title: String,
    variableName: String,
    enabled: Boolean?,
    canMutate: Boolean,
    onToggleRequest: (Boolean) -> Unit
) {
    val colors = SpatialTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
            Text(variableName, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        }
        val label = when (enabled) {
            true -> "Enabled"
            false -> "Paused"
            null -> "Unknown"
        }
        val tone = when (enabled) {
            true -> "enabled"
            false -> "paused"
            null -> "warning"
        }
        DevToneBadge(label = label, tone = tone)
        Spacer(Modifier.padding(horizontal = 4.dp))
        PrismaticButton(
            text = if (enabled == true) "Pause" else "Enable",
            onClick = { onToggleRequest(enabled != true) },
            enabled = canMutate && enabled != null,
            variant = PrismaticButtonVariant.Secondary
        )
    }
}

@Composable
private fun SecretInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    )
}

@Composable
private fun ChangeDeveloperPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String, confirmPin: String, onError: (String) -> Unit) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Developer PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it },
                    label = { Text("Current 6-digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("New 6-digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirm new PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = currentPin.length == 6 && newPin.length == 6 && confirmPin.length == 6,
                onClick = { onConfirm(currentPin, newPin, confirmPin) { errorMsg = it } },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Update")
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
