package com.diniy.duit.feature.onboarding

import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import androidx.compose.ui.semantics.role
import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.theme.CandyDisplayFamily
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.feature.notifications.ObservePermissionRefreshOnResume
import com.diniy.duit.feature.notifications.rememberNotificationPermissionRequester

/**
 * First-Time Setup screen for genuine new users.
 * Guides the user to create their primary wallet and configure optional preferences.
 */
@Composable
fun FirstTimeSetupScreen(
    hasOsNotificationPermission: Boolean,
    onPermissionUpdated: () -> Unit,
    onCompleteSetup: (
        walletName: String,
        walletType: WalletType,
        openingBalanceSen: Long,
        enableNotifications: Boolean,
        accentToken: PrismaticAccentToken
    ) -> Unit,
    modifier: Modifier = Modifier,
    /** Lets a new user who wants to restore a backup skip wallet creation. */
    onSkipSetup: (() -> Unit)? = null,
    errorMessage: String? = null
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val haptic = rememberDuitHaptic()
    val scrollState = rememberScrollState()

    var walletName by rememberSaveable { mutableStateOf("Cash") }
    var selectedWalletType by rememberSaveable { mutableStateOf(WalletType.CASH) }
    var openingBalanceInput by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(false) }

    val permissionRequester = rememberNotificationPermissionRequester(
        hasOsPermission = hasOsNotificationPermission,
        onPermissionUpdated = onPermissionUpdated
    )
    ObservePermissionRefreshOnResume(onResumeAfterPause = onPermissionUpdated)

    PrismaticBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Hero Header
                CandyIconBlob(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    tone = CandyTone.Mint,
                    size = 72.dp,
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add Your First Wallet",
                        style = typography.screenHeading.copy(
                            fontFamily = CandyDisplayFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start tracking your spending by adding where you keep your money.",
                        style = typography.supportingInfo.copy(fontSize = 14.sp),
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                }

                // Currency Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.floatingNavSurface)
                        .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Currency: Malaysian Ringgit (MYR / RM)",
                        style = typography.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                }

                // Wallet Configuration Card
                CandyBubbleCard(
                    tone = CandyTone.Neutral,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Account Details",
                            style = typography.cardTitle.copy(fontSize = 16.sp),
                            color = colors.primaryText
                        )

                        // Wallet Name Input
                        PrismaticTextField(
                            value = walletName,
                            onValueChange = {
                                walletName = it
                                validationError = null
                            },
                            label = "Wallet Name",
                            placeholder = "e.g. Cash, Maybank, Touch 'n Go",
                            errorText = validationError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // Account Type Selector
                        Text(
                            text = "Account Type",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.secondaryText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WalletType.entries.forEach { type ->
                                val isSelected = selectedWalletType == type
                                val pillShape = RoundedCornerShape(16.dp)
                                val (icon, label) = when (type) {
                                    WalletType.CASH -> Icons.Rounded.Payments to "Cash"
                                    WalletType.BANK -> Icons.Rounded.AccountBalance to "Bank"
                                    WalletType.EWALLET -> Icons.Rounded.AccountBalanceWallet to "e-Wallet"
                                    WalletType.SAVINGS -> Icons.Rounded.Savings to "Vault"
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(pillShape)
                                        .background(if (isSelected) colors.coral else colors.floatingNavSurface)
                                        .border(
                                            BorderStroke(1.5.dp, if (isSelected) colors.sticker else colors.secondaryText.copy(alpha = 0.3f)),
                                            pillShape
                                        )
                                        .candyPressable(
                                            onClick = {
                                                haptic(HapticFeedbackType.TextHandleMove)
                                                selectedWalletType = type
                                                if (walletName.isBlank() || walletName in listOf("Cash", "Bank Account", "e-Wallet", "Savings Vault")) {
                                                    walletName = type.displayName
                                                }
                                            },
                                            onClickLabel = label
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) colors.onCoral else colors.primaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = label,
                                            style = typography.caption.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) colors.onCoral else colors.primaryText
                                        )
                                    }
                                }
                            }
                        }

                        // Opening Balance Input
                        PrismaticTextField(
                            value = openingBalanceInput,
                            onValueChange = {
                                openingBalanceInput = it
                                validationError = null
                            },
                            label = "Opening Balance (Optional)",
                            placeholder = "0.00",
                            prefixText = "RM ",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                }

                // Optional Preferences Card
                CandyBubbleCard(
                    tone = CandyTone.Neutral,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Preferences",
                            style = typography.cardTitle.copy(fontSize = 16.sp),
                            color = colors.primaryText
                        )

                        // Notification Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.floatingNavSurface)
                                        .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Notifications,
                                        contentDescription = null,
                                        tint = colors.sunny,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Payment Reminders",
                                        style = typography.cardTitle.copy(fontSize = 14.sp),
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "Get alerts before bills and commitments are due.",
                                        style = typography.caption,
                                        color = colors.secondaryText
                                    )
                                }
                            }

                            // Shows the real Android permission state, never an assumed "on".
                            CandyStatusBadge(
                                text = if (hasOsNotificationPermission) "Allowed" else "Not allowed",
                                tone = if (hasOsNotificationPermission) CandyTone.Mint else CandyTone.Neutral
                            )
                        }
                        if (!hasOsNotificationPermission) {
                            // Asked once, only when the user chooses it; "Not Now" is respected.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrismaticButton(
                                    text = "Enable Notifications",
                                    onClick = {
                                        notificationsEnabled = true
                                        permissionRequester.requestOrOpenSettings()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                PrismaticButton(
                                    text = "Not Now",
                                    onClick = { notificationsEnabled = false },
                                    variant = PrismaticButtonVariant.Ghost
                                )
                            }
                        }
                        // App Lock Notice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.floatingNavSurface)
                                    .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = colors.aqua,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Security & PIN Lock",
                                    style = typography.cardTitle.copy(fontSize = 14.sp),
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "You can set up a 6-digit PIN anytime in Settings → Security.",
                                    style = typography.caption,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Finish Setup Button
            val primaryShape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .drawBehind {
                        val o = 3.dp.toPx()
                        val outline = primaryShape.createOutline(size, layoutDirection, this)
                        translate(left = 0f, top = o) { drawOutline(outline, colors.sticker) }
                    }
                    .clip(primaryShape)
                    .background(colors.coral)
                    .border(BorderStroke(2.dp, colors.sticker), primaryShape)
                    .candyPressable(
                        onClick = {
                            val trimmedName = walletName.trim()
                            if (trimmedName.isEmpty()) {
                                validationError = "Please enter a wallet name."
                                return@candyPressable
                            }
                            val balanceSen = if (openingBalanceInput.isBlank()) {
                                0L
                            } else {
                                MyrFormatter.parseToSen(openingBalanceInput) ?: run {
                                    validationError = "Enter a valid amount (e.g. 100 or 50.50)."
                                    return@candyPressable
                                }
                            }
                            haptic(HapticFeedbackType.TextHandleMove)
                            onCompleteSetup(
                                trimmedName,
                                selectedWalletType,
                                balanceSen,
                                notificationsEnabled,
                                PrismaticAccentToken.AQUA
                            )
                        },
                        onClickLabel = "Finish Setup & Start"
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = "Finish Setup & Start"
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Finish Setup & Start",
                        style = typography.buttonLabel.copy(
                            fontFamily = CandyDisplayFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.onCoral
                    )
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.onCoral,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = typography.caption,
                    color = colors.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            if (onSkipSetup != null) {
                androidx.compose.material3.TextButton(onClick = onSkipSetup, modifier = Modifier.fillMaxWidth()) {
                    Text("Set up later (e.g. to restore a backup)", style = typography.caption, color = colors.secondaryText)
                }
            }
        }
    }
}
