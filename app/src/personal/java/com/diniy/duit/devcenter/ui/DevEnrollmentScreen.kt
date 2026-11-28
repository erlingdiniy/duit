package com.diniy.duit.devcenter.ui

import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState

@Composable
fun DevEnrollmentScreen(
    uiState: DevCenterUiState,
    onEnroll: (backendUrl: String, code: String, label: String) -> Unit,
    onUseManualMode: (() -> Unit)?,
    onBack: () -> Unit,
    onLockNow: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val initialBackend = uiState.vault.backendUrlOverride.ifBlank { BuildConfig.DEV_BACKEND_URL }
    val defaultDeviceLabel = remember {
        val model = Build.MODEL.orEmpty().trim()
        val mfr = Build.MANUFACTURER.orEmpty().trim()
        when {
            model.isNotBlank() && mfr.isNotBlank() && !model.startsWith(mfr, ignoreCase = true) -> "$mfr $model"
            model.isNotBlank() -> model
            else -> "Owner Device"
        }.take(48)
    }

    var backendUrl by remember(initialBackend) { mutableStateOf(initialBackend) }
    var enrollmentInput by remember { mutableStateOf("") }
    var deviceLabel by remember(defaultDeviceLabel) { mutableStateOf(defaultDeviceLabel) }

    val applyInput: (String) -> Unit = { raw ->
        val trimmed = raw.trim()
        if (trimmed.startsWith("duit-dev-enroll://", ignoreCase = true)) {
            runCatching { Uri.parse(trimmed) }.getOrNull()?.let { uri ->
                uri.getQueryParameter("url")?.trim()?.takeIf { it.isNotEmpty() }?.let { backendUrl = it }
                uri.getQueryParameter("code")?.trim()?.takeIf { it.isNotEmpty() }?.let { enrollmentInput = it }
                uri.getQueryParameter("label")?.trim()?.takeIf { it.isNotEmpty() }?.let { deviceLabel = it }
            } ?: run { enrollmentInput = raw }
        } else {
            enrollmentInput = raw
        }
    }

    PrismaticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryText
                    )
                }
                Text(
                    text = "Owner Verification",
                    style = typography.screenHeading,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onLockNow,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Lock",
                        tint = colors.primaryText
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.primaryAction
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = colors.primaryAction,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "One-Time Device Enrollment",
                                style = typography.cardTitle,
                                color = colors.primaryText
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Connect this device once using a short-lived code from your Mission Control CLI. Your phone's Android Keystore generates an ECDSA P-256 hardware key that cannot be exported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(10.dp))
                        PrismaticTextField(
                            value = backendUrl,
                            onValueChange = { backendUrl = it },
                            label = "Mission Control Worker URL",
                            placeholder = "https://duit-mission-control.<account>.workers.dev"
                        )
                        Spacer(Modifier.height(10.dp))
                        PrismaticTextField(
                            value = enrollmentInput,
                            onValueChange = applyInput,
                            label = "One-Time Enrollment Code or Setup Link",
                            placeholder = "DUIT-XXXX-XXXX or duit-dev-enroll://..."
                        )
                        Spacer(Modifier.height(10.dp))
                        PrismaticTextField(
                            value = deviceLabel,
                            onValueChange = { deviceLabel = it.take(48) },
                            label = "Device Label",
                            placeholder = "Owner Phone"
                        )
                    }

                    uiState.enrollmentError?.let { err ->
                        DevStatusBanner(
                            message = err,
                            isSuccess = false,
                            onDismiss = {}
                        )
                    }

                    PrismaticButton(
                        text = "Connect Device",
                        onClick = { onEnroll(backendUrl, enrollmentInput, deviceLabel) },
                        enabled = !uiState.isEnrolling &&
                            backendUrl.trim().isNotBlank() &&
                            enrollmentInput.trim().length >= 6,
                        isLoading = uiState.isEnrolling,
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (onUseManualMode != null) {
                        PrismaticButton(
                            text = "Debug Local / Manual Mode",
                            onClick = onUseManualMode,
                            variant = PrismaticButtonVariant.Ghost,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
