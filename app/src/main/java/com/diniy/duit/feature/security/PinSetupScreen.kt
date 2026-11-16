package com.diniy.duit.feature.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.security.PinHashEngine
import kotlinx.coroutines.launch

enum class PinFlowMode {
    SETUP_NEW,
    CHANGE_EXISTING,
    DISABLE_EXISTING
}

private enum class PinStep {
    VERIFY_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

@Composable
fun PinSetupScreen(
    mode: PinFlowMode,
    onVerifyCurrentPin: suspend (String) -> Boolean,
    onSetupNewPin: suspend (String) -> Result<Unit>,
    onChangePin: suspend (currentPin: String, newPin: String) -> Result<Unit>,
    onDisableWithPin: suspend (currentPin: String) -> Result<Unit>,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember(mode) {
        mutableStateOf(
            when (mode) {
                PinFlowMode.SETUP_NEW -> PinStep.ENTER_NEW
                PinFlowMode.CHANGE_EXISTING,
                PinFlowMode.DISABLE_EXISTING -> PinStep.VERIFY_CURRENT
            }
        )
    }
    var verifiedCurrentPin by remember { mutableStateOf("") }
    var firstNewPin by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    BackHandler(enabled = !isBusy) {
        verifiedCurrentPin = ""
        firstNewPin = ""
        digits = ""
        onCancel()
    }

    val (title, subtitle) = when (step) {
        PinStep.VERIFY_CURRENT -> when (mode) {
            PinFlowMode.DISABLE_EXISTING -> "Turn Off App Lock" to "Enter your current 6-digit PIN to disable protection."
            else -> "Change 6-Digit PIN" to "Enter your current 6-digit PIN first."
        }
        PinStep.ENTER_NEW -> "Create 6-Digit PIN" to "Choose a 6-digit PIN you will remember. It is salted and Keystore-protected on this device."
        PinStep.CONFIRM_NEW -> "Confirm 6-Digit PIN" to "Re-enter your 6-digit PIN to confirm."
    }

    fun handleCompleteDigits(input: String) {
        when (step) {
            PinStep.VERIFY_CURRENT -> {
                isBusy = true
                errorText = null
                scope.launch {
                    if (mode == PinFlowMode.DISABLE_EXISTING) {
                        val res = onDisableWithPin(input)
                        isBusy = false
                        if (res.isSuccess) {
                            digits = ""
                            onSuccess()
                        } else {
                            digits = ""
                            errorText = res.exceptionOrNull()?.message ?: "Current PIN is incorrect."
                        }
                    } else {
                        val ok = onVerifyCurrentPin(input)
                        isBusy = false
                        if (ok) {
                            verifiedCurrentPin = input
                            digits = ""
                            step = PinStep.ENTER_NEW
                        } else {
                            digits = ""
                            errorText = "Current PIN is incorrect."
                        }
                    }
                }
            }

            PinStep.ENTER_NEW -> {
                firstNewPin = input
                digits = ""
                errorText = null
                step = PinStep.CONFIRM_NEW
            }

            PinStep.CONFIRM_NEW -> {
                if (input != firstNewPin) {
                    digits = ""
                    firstNewPin = ""
                    step = PinStep.ENTER_NEW
                    errorText = "PINs did not match. Please enter your new 6-digit PIN again."
                    return
                }
                isBusy = true
                errorText = null
                scope.launch {
                    val result = when (mode) {
                        PinFlowMode.SETUP_NEW -> onSetupNewPin(input)
                        PinFlowMode.CHANGE_EXISTING -> onChangePin(verifiedCurrentPin, input)
                        PinFlowMode.DISABLE_EXISTING -> Result.success(Unit)
                    }
                    isBusy = false
                    verifiedCurrentPin = ""
                    firstNewPin = ""
                    digits = ""
                    if (result.isSuccess) {
                        onSuccess()
                    } else {
                        step = PinStep.ENTER_NEW
                        errorText = result.exceptionOrNull()?.message ?: "Could not save PIN."
                    }
                }
            }
        }
    }

    PrismaticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        verifiedCurrentPin = ""
                        firstNewPin = ""
                        digits = ""
                        onCancel()
                    },
                    enabled = !isBusy
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 380.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    PinDotsRow(
                        filledCount = digits.length,
                        totalLength = PinHashEngine.PIN_LENGTH
                    )

                    if (errorText != null) {
                        PrismaticErrorBanner(
                            message = errorText!!,
                            onDismiss = { errorText = null }
                        )
                    }

                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }

                    NumericPinKeypad(
                        enabled = !isBusy,
                        showBiometricKey = false,
                        onDigit = { digit ->
                            if (digits.length < PinHashEngine.PIN_LENGTH) {
                                errorText = null
                                val next = digits + digit
                                digits = next
                                if (next.length == PinHashEngine.PIN_LENGTH) {
                                    handleCompleteDigits(next)
                                }
                            }
                        },
                        onBackspace = {
                            if (digits.isNotEmpty()) {
                                digits = digits.dropLast(1)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
