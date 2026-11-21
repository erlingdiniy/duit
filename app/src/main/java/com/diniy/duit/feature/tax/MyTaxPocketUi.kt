package com.diniy.duit.feature.tax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyFullScreenForm
import com.diniy.duit.core.ui.components.CandyMintIconButton
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.maskedAmount
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.tax.TaxAccentGroup
import com.diniy.duit.domain.tax.TaxCategorySummary
import com.diniy.duit.domain.tax.TaxReliefCatalog
import com.diniy.duit.domain.tax.TaxReliefEntry
import com.diniy.duit.domain.tax.TaxReliefReviewStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TaxCategoryFilter(val label: String) {
    ALL("All"),
    TRACKED("Tracked"),
    REVIEW("Needs review")
}

private data class TaxEntryDraft(
    val existingId: Long = 0L,
    val assessmentYear: Int,
    val categoryKey: String,
    val title: String = "",
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val totalSpentSen: Long? = null,
    val eligibleAmountSen: Long? = null,
    val receiptId: Long? = null,
    val reviewStatus: TaxReliefReviewStatus = TaxReliefReviewStatus.NEEDS_REVIEW,
    val note: String = "",
    val createdAtEpochMs: Long = 0L
)

private val TAX_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyTaxPocketScreen(
    binding: TaxPocketBinding,
    onClose: () -> Unit
) {
    val state = binding.state
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current
    val context = LocalContext.current
    val haptic = rememberDuitHaptic()
    val listState = rememberLazyListState()

    var filter by rememberSaveable { mutableStateOf(TaxCategoryFilter.ALL) }
    var inspectedCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf<TaxEntryDraft?>(null) }
    var showPrivacyExportConfirm by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    val receiptsById = remember(state.receipts) { state.receipts.associateBy { it.id } }

    val visibleCategories = remember(state.summary.categories, filter) {
        when (filter) {
            TaxCategoryFilter.ALL -> state.summary.categories
            TaxCategoryFilter.TRACKED -> state.summary.categories.filter { it.entryCount > 0 }
            TaxCategoryFilter.REVIEW -> state.summary.categories.filter { it.pendingRawSen > 0L }
        }
    }

    val doExport: () -> Unit = {
        val ok = TaxSummaryShareHelper.shareCsvSummary(context, state.summary, state.receipts)
        exportFeedback = if (ok) null else "Couldn't open export sheet."
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        val lightSystemBars = !colors.isDark
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightSystemBars
            controller.isAppearanceLightNavigationBars = lightSystemBars
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .semantics {
                    testTagsAsResourceId = true
                    paneTitle = "My Tax Pocket"
                }
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                CandyMintIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = onClose
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "My Tax Pocket",
                    style = type.screenHeading,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                if (!state.isSamplePreview) {
                    Spacer(Modifier.width(8.dp))
                    CandyMintIconButton(
                        icon = Icons.Rounded.IosShare,
                        contentDescription = "Export",
                        onClick = {
                            if (privacy.privacyModeEnabled) {
                                showPrivacyExportConfirm = true
                            } else {
                                doExport()
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    CandyMintIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Add",
                        onClick = {
                            val firstKey = state.summary.categories.firstOrNull()?.spec?.key ?: "LIFESTYLE"
                            editingDraft = TaxEntryDraft(
                                assessmentYear = state.selectedYear,
                                categoryKey = firstKey,
                                dateEpochDay = defaultDateForYear(state.selectedYear).toEpochDay()
                            )
                        }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isSamplePreview) {
                    item(key = "preview", contentType = "banner") {
                        CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Unavailable in Sample Preview.",
                                style = type.supportingInfo,
                                color = colors.secondaryText
                            )
                        }
                    }
                } else {
                    // Year of Assessment selector
                    item(key = "ya_selector", contentType = "pills") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.supportedYears.forEach { ya ->
                                val selected = ya == state.selectedYear
                                SpatialGlassPill(
                                    label = "YA $ya",
                                    selected = selected,
                                    accentColor = colors.primaryAction,
                                    onClick = {
                                        if (!selected) {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            binding.onSelectYear(ya)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Summary Hero Card (No Tax Savings Estimator — deferred)
                    item(key = "summary_hero", contentType = "hero") {
                        CandyBubbleCard(
                            tone = CandyTone.Mint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tax_summary_hero")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ready (capped)",
                                    style = type.supportingInfo,
                                    color = colors.secondaryText
                                )
                                CandyStatusBadge(
                                    text = "YA ${state.selectedYear}",
                                    tone = CandyTone.Sunny
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = maskedAmount(state.summary.totalReadyCappedSen),
                                style = type.heroBalance,
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CandyStatusBadge(
                                    text = if (state.summary.needsReviewCount == 0) {
                                        "0 pending"
                                    } else {
                                        "${state.summary.needsReviewCount} pending · ${maskedAmount(state.summary.totalPendingSen)}"
                                    },
                                    tone = if (state.summary.needsReviewCount > 0) CandyTone.Coral else CandyTone.Sky
                                )
                                CandyStatusBadge(
                                    text = if (state.summary.totalEntryCount == 1) "1 item" else "${state.summary.totalEntryCount} items",
                                    tone = CandyTone.Sky
                                )
                            }
                        }
                    }

                    // Error or export feedback
                    val err = state.errorMessage ?: exportFeedback
                    if (err != null) {
                        item(key = "error_banner", contentType = "banner") {
                            CandyBubbleCard(tone = CandyTone.Coral, modifier = Modifier.fillMaxWidth()) {
                                Text(text = err, style = type.supportingInfo, color = colors.primaryText)
                            }
                        }
                    }

                    // Optional Receipt Vault suggestions (tax-tagged receipts not yet linked for this YA)
                    if (state.summary.unlinkedTaggedReceipts.isNotEmpty()) {
                        item(key = "vault_suggestions_header", contentType = "section_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "From Receipt Vault",
                                    style = type.cardTitle,
                                    color = colors.primaryText,
                                    modifier = Modifier.semantics { heading() }
                                )
                                CandyStatusBadge(
                                    text = "${state.summary.unlinkedTaggedReceipts.size} tagged",
                                    tone = CandyTone.Sunny
                                )
                            }
                        }
                        items(
                            items = state.summary.unlinkedTaggedReceipts.take(3),
                            key = { "suggest_${it.id}" },
                            contentType = { "suggestion" }
                        ) { receipt ->
                            ReceiptSuggestionCard(
                                receipt = receipt,
                                onReview = {
                                    editingDraft = TaxEntryDraft(
                                        assessmentYear = state.selectedYear,
                                        categoryKey = TaxReliefCatalog.suggestedCategoryKeyForTag(state.selectedYear, receipt.taxTag),
                                        title = receipt.merchant.ifBlank { receipt.taxTag.label },
                                        dateEpochDay = receipt.dateEpochDay,
                                        totalSpentSen = receipt.amountSen,
                                        eligibleAmountSen = receipt.amountSen,
                                        receiptId = receipt.id,
                                        reviewStatus = TaxReliefReviewStatus.NEEDS_REVIEW,
                                        note = receipt.note
                                    )
                                }
                            )
                        }
                    }

                    // Category filter pills
                    item(key = "category_filters", contentType = "pills") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaxCategoryFilter.entries.forEach { tab ->
                                val selected = tab == filter
                                SpatialGlassPill(
                                    label = tab.label,
                                    selected = selected,
                                    accentColor = colors.primaryAction,
                                    onClick = {
                                        if (!selected) {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            filter = tab
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (visibleCategories.isEmpty()) {
                        item(key = "empty_filter", contentType = "empty") {
                            CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "No matching categories yet.",
                                    style = type.supportingInfo,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    } else {
                        items(
                            items = visibleCategories,
                            key = { "cat_${state.selectedYear}_${it.spec.key}" },
                            contentType = { "category_card" }
                        ) { cat ->
                            TaxCategoryCard(
                                summary = cat,
                                onClick = { inspectedCategoryKey = cat.spec.key }
                            )
                        }
                    }

                    // Compact disclaimer footer
                    item(key = "footer_note", contentType = "footer") {
                        Text(
                            text = "Records only. Verify in LHDN MyTax.",
                            style = type.supportingInfo,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }

    // Category detail dialog
    val inspectedCategory = inspectedCategoryKey?.let { key ->
        state.summary.categories.firstOrNull { it.spec.key == key }
    }
    if (inspectedCategory != null && editingDraft == null) {
        TaxCategoryDetailDialog(
            assessmentYear = state.selectedYear,
            category = inspectedCategory,
            receiptsById = receiptsById,
            onAddItem = {
                editingDraft = TaxEntryDraft(
                    assessmentYear = state.selectedYear,
                    categoryKey = inspectedCategory.spec.key,
                    dateEpochDay = defaultDateForYear(state.selectedYear).toEpochDay()
                )
            },
            onEditItem = { entry ->
                editingDraft = TaxEntryDraft(
                    existingId = entry.id,
                    assessmentYear = entry.assessmentYear,
                    categoryKey = entry.categoryKey,
                    title = entry.title,
                    dateEpochDay = entry.dateEpochDay,
                    totalSpentSen = entry.totalSpentSen,
                    eligibleAmountSen = entry.eligibleAmountSen,
                    receiptId = entry.receiptId,
                    reviewStatus = entry.reviewStatus,
                    note = entry.note,
                    createdAtEpochMs = entry.createdAtEpochMs
                )
            },
            onQuickStatus = { entry, nextStatus ->
                haptic(HapticFeedbackType.TextHandleMove)
                binding.onUpdateStatus(entry.id, nextStatus)
            },
            onDismiss = { inspectedCategoryKey = null }
        )
    }

    // Add / Edit entry full-screen form
    val activeDraft = editingDraft
    if (activeDraft != null) {
        TaxEntryEditorSheet(
            draft = activeDraft,
            receipts = state.receipts,
            onSave = { entry, onError ->
                binding.onSaveEntry(
                    entry,
                    {
                        haptic(HapticFeedbackType.Confirm)
                        editingDraft = null
                    },
                    onError
                )
            },
            onDelete = if (activeDraft.existingId > 0L) {
                {
                    binding.onDeleteEntry(activeDraft.existingId)
                    editingDraft = null
                }
            } else null,
            onDismiss = { editingDraft = null }
        )
    }

    // Privacy confirmation before exporting CSV when Privacy Mode is active
    if (showPrivacyExportConfirm) {
        AlertDialog(
            onDismissRequest = { showPrivacyExportConfirm = false },
            title = { Text("Export unmasked CSV?") },
            text = { Text("Privacy Mode is on. The exported CSV includes exact RM amounts.") },
            confirmButton = {
                TextButton(onClick = {
                    showPrivacyExportConfirm = false
                    doExport()
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyExportConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReceiptSuggestionCard(
    receipt: ReceiptRecord,
    onReview: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val dateLabel = remember(receipt.dateEpochDay) {
        LocalDate.ofEpochDay(receipt.dateEpochDay).format(TAX_DATE_FORMAT)
    }
    CandyBubbleCard(
        tone = CandyTone.Sunny,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ReceiptLong,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = receipt.merchant.ifBlank { receipt.taxTag.label },
                    style = type.cardTitle,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val amtStr = receipt.amountSen?.let { " · ${maskedAmount(it)}" }.orEmpty()
                Text(
                    text = "${receipt.taxTag.label} · $dateLabel$amtStr",
                    style = type.supportingInfo,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            SpatialGlassPill(
                label = "Review",
                selected = true,
                accentColor = colors.primaryAction,
                onClick = onReview
            )
        }
    }
}

@Composable
private fun TaxCategoryCard(
    summary: TaxCategorySummary,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val tone = when (summary.spec.accentGroup) {
        TaxAccentGroup.GOLD -> CandyTone.Sunny
        TaxAccentGroup.MINT -> CandyTone.Mint
        TaxAccentGroup.SKY -> CandyTone.Sky
        TaxAccentGroup.CORAL -> CandyTone.Coral
    }
    CandyBubbleCard(
        tone = tone,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${summary.spec.title}", onClick = onClick)
            .testTag("tax_cat_${summary.spec.key}")
    ) {
        val stackHeader = LocalDensity.current.fontScale >= 1.35f
        val capBadgeText = "${maskedAmount(summary.readyCappedSen)} / ${MyrFormatter.format(summary.spec.maxLimitSen)}"
        val capBadgeTone = if (summary.overCapSen > 0L) CandyTone.Coral else CandyTone.Mint
        if (stackHeader) {
            Text(
                text = summary.spec.title,
                style = type.cardTitle,
                color = colors.primaryText
            )
            Spacer(Modifier.height(6.dp))
            CandyStatusBadge(text = capBadgeText, tone = capBadgeTone)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.spec.title,
                    style = type.cardTitle,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CandyStatusBadge(text = capBadgeText, tone = capBadgeTone)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = summary.spec.shortRule,
            style = type.supportingInfo,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        // Progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.glassSurfaceElevated.copy(alpha = 0.7f))
        ) {
            if (summary.progressRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(summary.progressRatio)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAction)
                )
            }
        }
        if (summary.entryCount > 0 || summary.pendingRawSen > 0L) {
            Spacer(Modifier.height(6.dp))
            val meta = buildString {
                append("${summary.entryCount} item")
                if (summary.entryCount != 1) append("s")
                if (summary.pendingRawSen > 0L) append(" · ${maskedAmount(summary.pendingRawSen)} pending")
                if (summary.overCapSen > 0L) append(" · capped")
            }
            Text(
                text = meta,
                style = type.supportingInfo,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaxCategoryDetailDialog(
    assessmentYear: Int,
    category: TaxCategorySummary,
    receiptsById: Map<Long, ReceiptRecord>,
    onAddItem: () -> Unit,
    onEditItem: (TaxReliefEntry) -> Unit,
    onQuickStatus: (TaxReliefEntry, TaxReliefReviewStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography

    CandyFullScreenForm(
        title = category.spec.title,
        subtitle = "YA $assessmentYear · Limit ${MyrFormatter.format(category.spec.maxLimitSen)}",
        onDismissRequest = onDismiss
    ) {
        CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = category.spec.detailRule,
                style = type.supportingInfo,
                color = colors.primaryText
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Keep supporting receipts for 7 years. Verify eligibility in LHDN MyTax.",
                style = type.supportingInfo,
                color = colors.secondaryText
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CandyStatusBadge(
                text = "Ready ${maskedAmount(category.readyCappedSen)}",
                tone = CandyTone.Mint
            )
            CandyStatusBadge(
                text = "Left ${maskedAmount(category.remainingCapSen)}",
                tone = CandyTone.Sunny
            )
        }

        Spacer(Modifier.height(12.dp))

        SpatialGlassButton(
            text = "Add Item",
            onClick = onAddItem,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        if (category.entries.isEmpty()) {
            CandyBubbleCard(tone = CandyTone.Coral, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No items logged in ${category.spec.title} yet.",
                    style = type.supportingInfo,
                    color = colors.secondaryText
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                category.entries.forEach { entry ->
                    val linkedReceipt = entry.receiptId?.let { receiptsById[it] }
                    val dateLabel = LocalDate.ofEpochDay(entry.dateEpochDay).format(TAX_DATE_FORMAT)
                    val statusTone = when (entry.reviewStatus) {
                        TaxReliefReviewStatus.READY -> CandyTone.Mint
                        TaxReliefReviewStatus.NEEDS_REVIEW -> CandyTone.Sunny
                        TaxReliefReviewStatus.EXCLUDED -> CandyTone.Coral
                    }
                    CandyBubbleCard(
                        tone = statusTone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Edit ${entry.title}") {
                                onEditItem(entry)
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.title,
                                style = type.cardTitle,
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            CandyStatusBadge(text = entry.reviewStatus.label, tone = statusTone)
                        }
                        Spacer(Modifier.height(4.dp))
                        val spentSuffix = entry.totalSpentSen?.let { total ->
                            if (total > entry.eligibleAmountSen) " of ${maskedAmount(total)}" else ""
                        }.orEmpty()
                        Text(
                            text = "Eligible ${maskedAmount(entry.eligibleAmountSen)}$spentSuffix · $dateLabel",
                            style = type.supportingInfo,
                            color = colors.primaryText
                        )
                        if (linkedReceipt != null) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Receipt: ${linkedReceipt.merchant.ifBlank { linkedReceipt.originalFileName.ifBlank { "Linked" } }}",
                                    style = type.supportingInfo,
                                    color = colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (entry.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = entry.note,
                                style = type.supportingInfo,
                                color = colors.secondaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaxReliefReviewStatus.entries.forEach { st ->
                                SpatialGlassPill(
                                    label = st.label,
                                    selected = entry.reviewStatus == st,
                                    accentColor = colors.primaryAction,
                                    onClick = {
                                        if (entry.reviewStatus != st) onQuickStatus(entry, st)
                                    }
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
private fun TaxEntryEditorSheet(
    draft: TaxEntryDraft,
    receipts: List<ReceiptRecord>,
    onSave: (TaxReliefEntry, (String) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val categories = remember(draft.assessmentYear) { TaxReliefCatalog.categoriesForYear(draft.assessmentYear) }

    var categoryKey by remember(draft) { mutableStateOf(draft.categoryKey) }
    var title by remember(draft) { mutableStateOf(draft.title) }
    var dateInput by remember(draft) { mutableStateOf(LocalDate.ofEpochDay(draft.dateEpochDay).toString()) }
    var totalSpentInput by remember(draft) { mutableStateOf(draft.totalSpentSen?.let(MyrFormatter::formatInputValue).orEmpty()) }
    var eligibleInput by remember(draft) { mutableStateOf(draft.eligibleAmountSen?.let(MyrFormatter::formatInputValue).orEmpty()) }
    var receiptId by remember(draft) { mutableStateOf(draft.receiptId) }
    var status by remember(draft) { mutableStateOf(draft.reviewStatus) }
    var note by remember(draft) { mutableStateOf(draft.note) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showReceiptPicker by remember { mutableStateOf(false) }

    val linkedReceipt = remember(receiptId, receipts) { receipts.firstOrNull { it.id == receiptId } }

    CandyFullScreenForm(
        title = if (draft.existingId > 0L) "Edit Tax Item" else "Add Tax Item",
        subtitle = "YA ${draft.assessmentYear}",
        onDismissRequest = onDismiss
    ) {
        Text("Category", style = type.cardTitle, color = colors.primaryText)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { spec ->
                SpatialGlassPill(
                    label = spec.title,
                    selected = spec.key == categoryKey,
                    accentColor = colors.primaryAction,
                    onClick = { categoryKey = spec.key }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; errorMessage = null },
            label = { Text("Item or merchant") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tax_title_input")
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = eligibleInput,
            onValueChange = { eligibleInput = it; errorMessage = null },
            label = { Text("Eligible amount (RM)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tax_eligible_input")
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = totalSpentInput,
            onValueChange = { totalSpentInput = it; errorMessage = null },
            label = { Text("Total spent (RM, optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tax_total_input")
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it; errorMessage = null },
            label = { Text("Date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Text("Receipt link", style = type.cardTitle, color = colors.primaryText)
        Spacer(Modifier.height(6.dp))
        CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
            if (linkedReceipt != null) {
                val rDate = LocalDate.ofEpochDay(linkedReceipt.dateEpochDay).format(TAX_DATE_FORMAT)
                Text(
                    text = linkedReceipt.merchant.ifBlank { linkedReceipt.originalFileName.ifBlank { "Linked receipt" } },
                    style = type.cardTitle,
                    color = colors.primaryText
                )
                Text(
                    text = "$rDate${linkedReceipt.amountSen?.let { " · ${maskedAmount(it)}" }.orEmpty()}",
                    style = type.supportingInfo,
                    color = colors.secondaryText
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpatialGlassPill(
                        label = "Change",
                        accentColor = colors.primaryAction,
                        onClick = { showReceiptPicker = true }
                    )
                    SpatialGlassPill(
                        label = "Unlink",
                        accentColor = colors.coral,
                        onClick = { receiptId = null }
                    )
                }
            } else {
                Text(
                    text = if (receipts.isEmpty()) "No receipts in Receipt Vault yet." else "Optional: link a receipt from Receipt Vault.",
                    style = type.supportingInfo,
                    color = colors.secondaryText
                )
                if (receipts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SpatialGlassPill(
                        label = "Link Receipt",
                        accentColor = colors.primaryAction,
                        onClick = { showReceiptPicker = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Review status", style = type.cardTitle, color = colors.primaryText)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaxReliefReviewStatus.entries.forEach { st ->
                SpatialGlassPill(
                    label = st.label,
                    selected = status == st,
                    accentColor = colors.primaryAction,
                    onClick = { status = st }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = errorMessage!!, style = type.supportingInfo, color = colors.coral)
        }

        Spacer(Modifier.height(16.dp))
        SpatialGlassButton(
            text = "Save",
            onClick = {
                val parsedEligible = MyrFormatter.parseToSen(eligibleInput)
                val parsedTotal = if (totalSpentInput.isBlank()) null else MyrFormatter.parseToSen(totalSpentInput)
                val parsedDate = runCatching { LocalDate.parse(dateInput.trim()) }.getOrNull()
                when {
                    title.trim().isEmpty() -> errorMessage = "Please enter an item title."
                    parsedEligible == null || parsedEligible <= 0L -> errorMessage = "Enter a valid eligible RM amount."
                    totalSpentInput.isNotBlank() && parsedTotal == null -> errorMessage = "Enter a valid total spent RM amount."
                    parsedTotal != null && parsedEligible > parsedTotal -> errorMessage = "Eligible amount cannot exceed total spent."
                    parsedDate == null -> errorMessage = "Use YYYY-MM-DD date format."
                    else -> {
                        onSave(
                            TaxReliefEntry(
                                id = draft.existingId,
                                assessmentYear = draft.assessmentYear,
                                categoryKey = categoryKey,
                                title = title.trim(),
                                dateEpochDay = parsedDate.toEpochDay(),
                                totalSpentSen = parsedTotal,
                                eligibleAmountSen = parsedEligible,
                                receiptId = receiptId,
                                reviewStatus = status,
                                note = note.trim(),
                                createdAtEpochMs = draft.createdAtEpochMs
                            )
                        ) { err -> errorMessage = err }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tax_save_btn")
        )

        if (onDelete != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = colors.coral)
                Spacer(Modifier.width(6.dp))
                Text("Delete", color = colors.coral)
            }
        }
    }

    if (showReceiptPicker) {
        AlertDialog(
            onDismissRequest = { showReceiptPicker = false },
            title = { Text("Link Receipt") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    receipts.take(15).forEach { r ->
                        val rDate = LocalDate.ofEpochDay(r.dateEpochDay).format(TAX_DATE_FORMAT)
                        CandyBubbleCard(
                            tone = if (r.id == receiptId) CandyTone.Mint else CandyTone.Sky,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    receiptId = r.id
                                    if (title.isBlank() && r.merchant.isNotBlank()) title = r.merchant
                                    if (totalSpentInput.isBlank() && r.amountSen != null) {
                                        totalSpentInput = MyrFormatter.formatInputValue(r.amountSen)
                                    }
                                    if (eligibleInput.isBlank() && r.amountSen != null) {
                                        eligibleInput = MyrFormatter.formatInputValue(r.amountSen)
                                    }
                                    showReceiptPicker = false
                                }
                        ) {
                            Text(
                                text = r.merchant.ifBlank { r.originalFileName.ifBlank { "Receipt #${r.id}" } },
                                style = type.cardTitle,
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$rDate${r.amountSen?.let { " · ${maskedAmount(it)}" }.orEmpty()}",
                                style = type.supportingInfo,
                                color = colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReceiptPicker = false }) { Text("Close") }
            }
        )
    }
}

private fun defaultDateForYear(ya: Int): LocalDate {
    val today = LocalDate.now()
    return if (today.year == ya) today else LocalDate.of(ya, 12, 31)
}
