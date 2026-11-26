package com.diniy.duit.devcenter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevAuthUiState
import com.diniy.duit.devcenter.DevLockStage
import com.diniy.duit.feature.security.NumericPinKeypad
import com.diniy.duit.feature.security.PinDotsRow
import com.diniy.duit.security.PinHashEngine

@Composable
fun DevCenterAuthScreen(
    authState: DevAuthUiState,
    onBack: () -> Unit,
    onSetupPin: (String, String) -> Unit,
    onSubmitPin: (String) -> Unit,
    onTriggerBiometric: () -> Unit,
    onDismissError: () -> Unit
) {
    BackHandler { onBack() }

    val glass = LocalPrismaticGlass.current
    val colors = SpatialTheme.colors
    var digits by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState.errorMessage, authState.failedAttempts, authState.stage) {
        if (authState.errorMessage != null && !authState.isVerifying) {
            digits = ""
        }
    }

    val isSetup = authState.stage == DevLockStage.NEEDS_PIN_SETUP
    val inputsEnabled = !authState.isVerifying && (!authState.isThrottled || isSetup)

    PrismaticBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isSetup && firstSetupPin != null) {
                            firstSetupPin = null
                            digits = ""
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryText
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 380.dp)
                    .padding(top = 48.dp, bottom = 20.dp),
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
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = glass.onLavenderContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val title = when {
                        isSetup && firstSetupPin == null -> "Set Developer PIN"
                        isSetup -> "Confirm Developer PIN"
                        else -> "Developer Center"
                    }
                    val subtitle = when {
                        isSetup && firstSetupPin == null -> "Separate 6-digit PIN from your app lock"
                        isSetup -> "Re-enter 6-digit Developer PIN"
                        authState.isThrottled -> "Try again in ${authState.lockoutRemainingSeconds}s"
                        else -> "Enter 6-digit Developer PIN"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.primaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                }

                PinDotsRow(filledCount = digits.length)

                if (authState.isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }

                authState.errorMessage?.let { msg ->
                    PrismaticErrorBanner(
                        message = msg,
                        onDismiss = onDismissError
                    )
                }

                NumericPinKeypad(
                    enabled = inputsEnabled,
                    showBiometricKey = !isSetup && authState.canUseBiometricNow,
                    onDigit = { ch ->
                        if (inputsEnabled && digits.length < PinHashEngine.PIN_LENGTH) {
                            val next = digits + ch
                            digits = next
                            if (next.length == PinHashEngine.PIN_LENGTH) {
                                if (isSetup) {
                                    val first = firstSetupPin
                                    if (first == null) {
                                        firstSetupPin = next
                                        digits = ""
                                    } else if (first == next) {
                                        onSetupPin(first, next)
                                    } else {
                                        firstSetupPin = null
                                        digits = ""
                                        onSetupPin(first, next)
                                    }
                                } else {
                                    onSubmitPin(next)
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (digits.isNotEmpty()) digits = digits.dropLast(1)
                    },
                    onBiometric = {
                        if (!isSetup && authState.canUseBiometricNow) onTriggerBiometric()
                    }
                )

                if (!isSetup && authState.canUseBiometricNow) {
                    PrismaticButton(
                        text = "Biometric Unlock",
                        onClick = onTriggerBiometric,
                        variant = PrismaticButtonVariant.Ghost,
                        enabled = inputsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}
