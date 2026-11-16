package com.diniy.duit.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class ProtectedAction {
    CREATE_BACKUP,
    RESTORE_CONFIRM,
    EXPORT_TRANSACTIONS_CSV,
    EXPORT_ALL_CSV,
    DELETE_ALL_DATA
}

@Composable
internal fun BackupAndRestoreScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    viewModel: SettingsViewModel
) {
    val glass = LocalPrismaticGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCsvText by remember { mutableStateOf<String?>(null) }
    var showCreatePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCsvWarningFull by remember { mutableStateOf<Boolean?>(null) }
    var authChallengeAction by remember { mutableStateOf<ProtectedAction?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBackupBytes
        pendingBackupBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching { writeBytesToUri(context, uri, bytes) }
                    .onSuccess { viewModel.notifyBackupWritten(resolveDisplayName(context, uri)) }
                    .onFailure { viewModel.reportError(it.message ?: "Unable to write backup file.") }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readBytesFromUri(context, uri) }
                    .onSuccess { raw -> viewModel.inspectSelectedBackup(raw, resolveDisplayName(context, uri)) }
                    .onFailure { viewModel.reportError(it.message ?: "Unable to read selected file.") }
            }
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val csv = pendingCsvText
        pendingCsvText = null
        if (uri != null && csv != null) {
            scope.launch {
                runCatching { writeBytesToUri(context, uri, csv.toByteArray(StandardCharsets.UTF_8)) }
                    .onSuccess { viewModel.notifyCsvWritten(resolveDisplayName(context, uri)) }
                    .onFailure { viewModel.reportError(it.message ?: "Unable to save CSV file.") }
            }
        }
    }

    fun executeProtected(action: ProtectedAction) {
        when (action) {
            ProtectedAction.CREATE_BACKUP -> showCreatePasswordDialog = true
            ProtectedAction.RESTORE_CONFIRM -> viewModel.confirmRestoreBackup()
            ProtectedAction.EXPORT_TRANSACTIONS_CSV -> viewModel.buildCsvExport(includeAllRecords = false) { csv ->
                pendingCsvText = csv
                createCsvLauncher.launch("duit-transactions-${timestampSuffix()}.csv")
            }
            ProtectedAction.EXPORT_ALL_CSV -> viewModel.buildCsvExport(includeAllRecords = true) { csv ->
                pendingCsvText = csv
                createCsvLauncher.launch("duit-ledger-${timestampSuffix()}.csv")
            }
            ProtectedAction.DELETE_ALL_DATA -> Unit
        }
    }

    fun gateOrExecute(action: ProtectedAction) {
        if (uiState.securityState.isAppLockEnabled) {
            authChallengeAction = action
        } else {
            executeProtected(action)
        }
    }

    SettingsPageScaffold(
        title = "Backup & Restore",
        subtitle = "Encrypted local backups (.duitbackup) & plaintext CSV export",
        onBack = onBack,
        statusMessage = uiState.statusMessage,
        errorMessage = uiState.errorMessage,
        onClearFeedback = viewModel::clearFeedback
    ) {
        item(key = "encrypted_backup_card") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.aquaAccent) {
                Text("Encrypted Local Backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Uses PBKDF2WithHmacSHA256 (${BackupCryptoEngine.DEFAULT_ITERATIONS} iterations, unique random salt) + AES-256-GCM authenticated encryption. Portable across reinstalls and devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Create Encrypted Backup",
                    onClick = { gateOrExecute(ProtectedAction.CREATE_BACKUP) },
                    enabled = !uiState.isBusy,
                    variant = PrismaticButtonVariant.Primary
                )
            }
        }

        item(key = "restore_card") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.lavenderAccent) {
                Text("Restore from Backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Validates file structure, decrypts with your backup password, previews record counts, and replaces ledger records atomically inside a Room transaction. Your device PIN and biometrics stay untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Select Backup File",
                    onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                    enabled = !uiState.isBusy,
                    variant = PrismaticButtonVariant.Secondary
                )
            }
        }

        item(key = "csv_export_card") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.peachAccent) {
                Text("CSV Export (Unencrypted)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Warning: CSV exports are saved as unencrypted plaintext spreadsheets with exact MYR formatting and timestamps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismaticButton(
                        text = "Transactions CSV",
                        onClick = { pendingCsvWarningFull = false },
                        enabled = !uiState.isBusy,
                        variant = PrismaticButtonVariant.Secondary
                    )
                    PrismaticButton(
                        text = "Full Ledger CSV",
                        onClick = { pendingCsvWarningFull = true },
                        enabled = !uiState.isBusy,
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
            }
        }
    }

    if (showCreatePasswordDialog) {
        BackupPasswordCreateDialog(
            onDismiss = { showCreatePasswordDialog = false },
            onConfirm = { password ->
                showCreatePasswordDialog = false
                viewModel.buildEncryptedBackup(password) { bytes ->
                    pendingBackupBytes = bytes
                    createBackupLauncher.launch("duit-backup-${timestampSuffix()}.duitbackup")
                }
            }
        )
    }

    when (val stage = uiState.restoreStage) {
        RestoreStage.Idle -> Unit
        is RestoreStage.AwaitingPassword -> {
            BackupPasswordUnlockDialog(
                fileName = stage.fileName,
                createdAtLabel = DateTimeFormatters.formatTimestamp(stage.header.createdAtEpochMs),
                schemaVersion = stage.header.dbSchemaVersion,
                errorMessage = uiState.errorMessage,
                onDismiss = viewModel::cancelRestoreFlow,
                onDecrypt = { pwd -> viewModel.decryptSelectedBackup(pwd) }
            )
        }
        is RestoreStage.PreviewReady -> {
            val preview = stage.bundle.preview
            AlertDialog(
                onDismissRequest = viewModel::cancelRestoreFlow,
                title = { Text("Confirm Backup Restore") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "File: ${stage.fileName}\n" +
                                "Created: ${DateTimeFormatters.formatTimestamp(preview.createdAtEpochMs)}\n" +
                                "Format v${preview.formatVersion} · Schema v${preview.dbSchemaVersion}"
                        )
                        HorizontalDivider(color = glass.glassBorderSubtle)
                        Text(
                            "• Wallets: ${preview.walletCount}\n" +
                                "• Transactions: ${preview.transactionCount}\n" +
                                "• Commitments: ${preview.commitmentCount}\n" +
                                "• PayLater Plans: ${preview.payLaterPlanCount}\n" +
                                "• Debts: ${preview.debtCount}\n" +
                                "• Savings Goals: ${preview.savingsGoalCount}\n" +
                                "• Budgets: ${preview.budgetCount} · Commissions: ${preview.commissionCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Warning: Restoring replaces all current financial records atomically. Current device PIN and biometric settings remain preserved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { gateOrExecute(ProtectedAction.RESTORE_CONFIRM) }) {
                        Text("Restore & Replace", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelRestoreFlow) { Text("Cancel") }
                }
            )
        }
    }

    pendingCsvWarningFull?.let { fullExport ->
        AlertDialog(
            onDismissRequest = { pendingCsvWarningFull = null },
            title = { Text("Unencrypted CSV Export Warning") },
            text = {
                Text("CSV files are NOT encrypted or password-protected. Anyone with access to the exported file can read your financial records.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCsvWarningFull = null
                    gateOrExecute(if (fullExport) ProtectedAction.EXPORT_ALL_CSV else ProtectedAction.EXPORT_TRANSACTIONS_CSV)
                }) { Text("Continue Export") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCsvWarningFull = null }) { Text("Cancel") }
            }
        )
    }

    authChallengeAction?.let { action ->
        PinChallengeDialog(
            title = "Confirm Your App PIN",
            subtitle = "Enter your 6-digit App Lock PIN before accessing sensitive backup or export data.",
            onVerifyPin = onVerifyPin,
            onVerified = {
                authChallengeAction = null
                executeProtected(action)
            },
            onDismiss = { authChallengeAction = null }
        )
    }
}

@Composable
internal fun DataManagementScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    viewModel: SettingsViewModel
) {
    val glass = LocalPrismaticGlass.current
    val summary = uiState.storageSummary
    var showConfirmResetPrefs by rememberSaveable { mutableStateOf(false) }
    var showConfirmClearTemp by rememberSaveable { mutableStateOf(false) }
    var showConfirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var showPinForDelete by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(
        title = "Data Management",
        subtitle = "Local storage summary, preference resets & permanent ledger wipe",
        onBack = onBack,
        statusMessage = uiState.statusMessage,
        errorMessage = uiState.errorMessage,
        onClearFeedback = viewModel::clearFeedback
    ) {
        item(key = "storage_info") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.skyBlueAccent) {
                Text("Local Storage Summary", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "• Wallets: ${summary.walletCount}\n" +
                        "• Transactions: ${summary.transactionCount}\n" +
                        "• Commitments: ${summary.commitmentCount}\n" +
                        "• PayLater Plans: ${summary.payLaterPlanCount} · Debts: ${summary.debtCount}\n" +
                        "• Savings Goals: ${summary.savingsGoalCount} · Budgets: ${summary.budgetCount}\n" +
                        "• Commissions: ${summary.commissionCount} · Salary Cycles: ${summary.salaryCycleCount}\n" +
                        "• Notification History Entries: ${summary.notificationHistoryCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "preferences_reset_card") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.aquaAccent) {
                Text("Non-Destructive Resets", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Reset visual, dashboard, financial, and notification preferences or clear notification history without touching wallets or transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismaticButton(
                        text = "Restore Default Preferences",
                        onClick = { showConfirmResetPrefs = true },
                        enabled = !uiState.isBusy,
                        variant = PrismaticButtonVariant.Secondary
                    )
                    PrismaticButton(
                        text = "Clear History Cache",
                        onClick = { showConfirmClearTemp = true },
                        enabled = !uiState.isBusy,
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
            }
        }

        item(key = "delete_financial_card") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.peachAccent) {
                Text("Delete All Financial Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Permanently erases all wallets, transactions, commitments, PayLater plans, debts, savings goals, budgets, salary cycles, and commissions. Requires explicit confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Delete All Financial Data",
                    onClick = { showConfirmDeleteAll = true },
                    enabled = !uiState.isBusy,
                    variant = PrismaticButtonVariant.Secondary
                )
            }
        }
    }

    if (showConfirmResetPrefs) {
        AlertDialog(
            onDismissRequest = { showConfirmResetPrefs = false },
            title = { Text("Restore Default Preferences?") },
            text = { Text("Resets theme, glass, motion, dashboard layout, notification, and non-credential privacy preferences to defaults. Financial records and your App PIN will NOT be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmResetPrefs = false
                    viewModel.restoreDefaultPreferences()
                }) { Text("Restore Defaults") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmResetPrefs = false }) { Text("Cancel") }
            }
        )
    }

    if (showConfirmClearTemp) {
        AlertDialog(
            onDismissRequest = { showConfirmClearTemp = false },
            title = { Text("Clear Notification History?") },
            text = { Text("Removes delivered reminder logs from Notification Centre. Financial records and schedules remain intact.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmClearTemp = false
                    viewModel.clearNonEssentialData()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearTemp = false }) { Text("Cancel") }
            }
        )
    }

    if (showConfirmDeleteAll) {
        var acknowledged by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAll = false },
            title = { Text("Delete All Financial Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently wipes all wallets, transactions, budgets, commitments, savings goals, PayLater plans, debts, and commissions. This cannot be undone unless you have an encrypted backup.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text("I understand all financial data will be permanently erased.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = acknowledged,
                    onClick = {
                        showConfirmDeleteAll = false
                        if (uiState.securityState.isAppLockEnabled) {
                            showPinForDelete = true
                        } else {
                            viewModel.deleteAllFinancialData()
                        }
                    }
                ) { Text("Delete Everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }

    if (showPinForDelete) {
        PinChallengeDialog(
            title = "Authorize Financial Data Wipe",
            subtitle = "Enter your 6-digit App Lock PIN to confirm deleting all financial records.",
            onVerifyPin = onVerifyPin,
            onVerified = {
                showPinForDelete = false
                viewModel.deleteAllFinancialData()
            },
            onDismiss = { showPinForDelete = false }
        )
    }
}

@Composable
private fun BackupPasswordCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypt Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose a strong password (minimum ${BackupCryptoEngine.MIN_PASSWORD_LENGTH} characters). Without this password, the backup cannot be decrypted.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it; error = null },
                    label = { Text("Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pwd.length < BackupCryptoEngine.MIN_PASSWORD_LENGTH ->
                        error = "Use at least ${BackupCryptoEngine.MIN_PASSWORD_LENGTH} characters."
                    pwd != confirm ->
                        error = "Passwords do not match."
                    else -> onConfirm(pwd.toCharArray())
                }
            }) { Text("Choose Destination") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BackupPasswordUnlockDialog(
    fileName: String,
    createdAtLabel: String,
    schemaVersion: Int,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onDecrypt: (CharArray) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decrypt Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$fileName\nCreated $createdAtLabel · Schema v$schemaVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (errorMessage != null) {
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecrypt(pwd.toCharArray()) }, enabled = pwd.isNotEmpty()) {
                Text("Unlock & Preview")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PinChallengeDialog(
    title: String,
    subtitle: String,
    onVerifyPin: suspend (String) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text("6-digit App PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 6 && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = onVerifyPin(pin)
                        busy = false
                        if (ok) onVerified() else error = "Incorrect PIN. Try again."
                    }
                }
            ) { Text("Verify") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private suspend fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(bytes)
        out.flush()
    } ?: error("Unable to open destination file for writing.")
}

private suspend fun readBytesFromUri(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes()
    } ?: error("Unable to read selected file.")
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return it.getString(idx) ?: uri.lastPathSegment ?: "backup"
        }
    }
    return uri.lastPathSegment ?: "backup"
}

private fun timestampSuffix(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
