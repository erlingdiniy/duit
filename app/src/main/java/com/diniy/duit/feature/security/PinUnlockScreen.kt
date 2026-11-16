package com.diniy.duit.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.security.AppLockUiState
import com.diniy.duit.security.PinHashEngine
import kotlinx.coroutines.launch

@Composable
fun PinUnlockScreen(
    appLockState: AppLockUiState,
    onSubmitPin: (String) -> Unit,
    onTriggerBiometric: () -> Unit,
    onDismissError: () -> Unit,
    onConfirmFactoryReset: suspend () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val scope = rememberCoroutineScope()
    var enteredDigits by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetConfirmInput by remember { mutableStateOf("") }
    var isResetting by remember { mutableStateOf(false) }

    // Clear partial input whenever an authentication attempt completes with error or throttle
    LaunchedEffect(appLockState.authErrorMessage, appLockState.failedAttempts) {
        if (appLockState.authErrorMessage != null && !appLockState.isVerifyingPin) {
            enteredDigits = ""
        }
    }

    val inputsEnabled = !appLockState.isThrottled && !appLockState.isVerifyingPin

    PrismaticBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(glass.lavenderContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Locked",
                        tint = glass.onLavenderContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "duit. is locked",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (appLockState.isThrottled) {
                            "Authentication temporarily paused for ${appLockState.lockoutRemainingSeconds}s"
                        } else {
                            "Enter your 6-digit PIN to access your ledger"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                PinDotsRow(
                    filledCount = enteredDigits.length,
                    totalLength = PinHashEngine.PIN_LENGTH
                )

                if (appLockState.authErrorMessage != null) {
                    PrismaticErrorBanner(
                        message = appLockState.authErrorMessage,
                        onDismiss = onDismissError
                    )
                }

                if (appLockState.isVerifyingPin) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                }

                NumericPinKeypad(
                    enabled = inputsEnabled,
                    showBiometricKey = appLockState.canUseBiometricNow,
                    onDigit = { digit ->
                        if (enteredDigits.length < PinHashEngine.PIN_LENGTH) {
                            onDismissError()
                            val next = enteredDigits + digit
                            enteredDigits = next
                            if (next.length == PinHashEngine.PIN_LENGTH) {
                                onSubmitPin(next)
                            }
                        }
                    },
                    onBackspace = {
                        if (enteredDigits.isNotEmpty()) {
                            enteredDigits = enteredDigits.dropLast(1)
                        }
                    },
                    onBiometric = onTriggerBiometric
                )

                if (appLockState.canUseBiometricNow) {
                    PrismaticButton(
                        text = "Unlock with Biometrics",
                        onClick = onTriggerBiometric,
                        variant = PrismaticButtonVariant.Secondary,
                        enabled = !appLockState.isThrottled && !appLockState.isVerifyingPin
                    )
                }

                TextButton(
                    onClick = {
                        resetConfirmInput = ""
                        showResetDialog = true
                    }
                ) {
                    Text(
                        text = "Forgot PIN? Reset App Access",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        val canConfirmReset = resetConfirmInput.trim().equals("RESET", ignoreCase = false) && !isResetting
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { Text("Reset Protected App Access?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Because your 6-digit PIN is hashed and hardware-protected with no bypass or cloud recovery, resetting app access must permanently erase all local financial data (wallets, transactions, salary cycles, commissions, savings goals, budgets, commitments, PayLater plans, and debts).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "This action cannot be undone. Type RESET below to confirm.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    PrismaticTextField(
                        value = resetConfirmInput,
                        onValueChange = { resetConfirmInput = it },
                        label = "Type RESET to confirm",
                        placeholder = "RESET",
                        enabled = !isResetting
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirmReset,
                    onClick = {
                        isResetting = true
                        scope.launch {
                            onConfirmFactoryReset()
                            isResetting = false
                            showResetDialog = false
                        }
                    }
                ) {
                    Text(
                        text = if (isResetting) "Resetting..." else "Erase & Reset",
                        color = if (canConfirmReset) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isResetting,
                    onClick = { showResetDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PinDotsRow(
    filledCount: Int,
    totalLength: Int = PinHashEngine.PIN_LENGTH
) {
    val glass = LocalPrismaticGlass.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "$filledCount of $totalLength PIN digits entered"
        }
    ) {
        for (i in 0 until totalLength) {
            val isFilled = i < filledCount
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) glass.lavenderAccent else glass.glassSurfaceMuted
                    )
                    .border(
                        width = 1.dp,
                        color = if (isFilled) glass.lavenderAccent else glass.glassBorderSubtle,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun NumericPinKeypad(
    enabled: Boolean,
    showBiometricKey: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)? = null
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { digit ->
                    KeypadDigitButton(
                        digit = digit,
                        enabled = enabled,
                        onClick = { onDigit(digit) }
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBiometricKey && onBiometric != null) {
                KeypadIconButton(
                    contentDescription = "Unlock with biometrics",
                    enabled = enabled,
                    onClick = onBiometric
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(68.dp))
            }

            KeypadDigitButton(
                digit = '0',
                enabled = enabled,
                onClick = { onDigit('0') }
            )

            KeypadIconButton(
                contentDescription = "Delete last digit",
                enabled = enabled,
                onClick = onBackspace
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    PrismaticGlassCard(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Digit $digit",
                onClick = onClick
            ),
        tier = GlassCardTier.Standard,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else glass.glassBorderSubtle
            )
        }
    }
}

@Composable
private fun KeypadIconButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(glass.glassSurfaceMuted)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
