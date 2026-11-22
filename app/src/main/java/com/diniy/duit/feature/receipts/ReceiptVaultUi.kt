package com.diniy.duit.feature.receipts

import android.app.DatePickerDialog
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
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
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyMintIconButton
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.SpatialButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.receipt.ReceiptFilterTab
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptTaxTag
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RECEIPT_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * Full-screen Receipt Vault hub in Candy Bubble 2.0 style.
 * Uses LazyColumn directly (never nested inside verticalScroll) and syncs system bar contrast.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReceiptVaultScreen(
    state: ReceiptVaultUiState,
    fileStorage: ReceiptFileStorage,
    initialDraftTransaction: FinanceTransaction? = null,
    onSelectTab: (ReceiptFilterTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSaveReceipt: (ReceiptRecord, () -> Unit, (String) -> Unit) -> Unit,
    onLinkTransaction: (Long, Long?) -> Unit,
    onDeleteReceipt: (Long, () -> Unit) -> Unit,
    onDismissError: () -> Unit,
    onClose: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current

    var editingReceipt by remember { mutableStateOf<ReceiptRecord?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(initialDraftTransaction != null) }
    var prelinkedTransaction by remember { mutableStateOf(initialDraftTransaction) }
    var inspectedReceiptId by rememberSaveable { mutableStateOf<Long?>(null) }

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
                    paneTitle = "Receipt Vault"
                }
        ) {
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
                    text = "Receipt Vault",
                    style = type.screenHeading,
                    color = colors.primaryText,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                if (!state.isSamplePreview) {
                    CandyMintIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Add receipt",
                        onClick = {
                            editingReceipt = null
                            prelinkedTransaction = null
                            showEditor = true
                        },
                        modifier = Modifier.testTag("receipt_add_btn")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.errorMessage != null) {
                    item(key = "error") {
                        PrismaticErrorBanner(message = state.errorMessage, onDismiss = onDismissError)
                    }
                }

                if (state.isSamplePreview) {
                    item(key = "preview_notice") {
                        CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Receipts are private to your real records and hidden in Sample Preview.",
                                style = type.supportingInfo,
                                color = colors.secondaryText
                            )
                        }
                    }
                } else {
                    item(key = "summary_strip") {
                        CandyBubbleCard(tone = CandyTone.Peach, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryStat("Saved", state.allReceipts.size.toString(), Modifier.weight(1f))
                                SummaryStat("Linked", state.linkedCount.toString(), Modifier.weight(1f))
                                SummaryStat("Tax notes", state.taxTaggedCount.toString(), Modifier.weight(1f))
                            }
                        }
                    }

                    item(key = "search_bar") {
                        val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
                            com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = type.transactionDescription.copy(color = colors.primaryText),
                            placeholder = {
                                Text(
                                    text = "Search merchant, category, tag or notes",
                                    style = type.supportingInfo,
                                    color = colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                if (isLiquidGlass) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
                                } else {
                                    CandyIconBlob(Icons.Rounded.Search, CandyTone.Coral, size = 34.dp, tilt = -6f, solid = true)
                                }
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = colors.secondaryText)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isLiquidGlass) colors.elevatedSurface else colors.glassSurfaceElevated,
                                unfocusedContainerColor = if (isLiquidGlass) colors.elevatedSurface else colors.glassSurface,
                                focusedBorderColor = if (isLiquidGlass) colors.primaryAction else colors.coral,
                                unfocusedBorderColor = if (isLiquidGlass) colors.divider else androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_search_field")
                        )
                    }

                    item(key = "filter_pills") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReceiptFilterTab.entries.forEach { tab ->
                                SpatialGlassPill(
                                    label = tab.label,
                                    selected = state.selectedTab == tab,
                                    onClick = { onSelectTab(tab) }
                                )
                            }
                        }
                    }

                    if (state.filteredReceipts.isEmpty()) {
                        item(key = "empty_state") {
                            CandyBubbleCard(tone = CandyTone.Mint, modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CandyIconBlob(Icons.Rounded.ReceiptLong, CandyTone.Sunny, size = 56.dp, solid = true)
                                    Text(
                                        text = if (state.allReceipts.isEmpty()) "No receipts saved" else "No matching receipts",
                                        style = type.sectionHeading,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = if (state.allReceipts.isEmpty()) {
                                            "Save photos or PDFs privately on this phone."
                                        } else {
                                            "Try another search term or filter."
                                        },
                                        style = type.supportingInfo,
                                        color = colors.secondaryText
                                    )
                                    if (state.allReceipts.isEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        SpatialGlassButton(
                                            text = "Add Receipt",
                                            onClick = {
                                                editingReceipt = null
                                                prelinkedTransaction = null
                                                showEditor = true
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(state.filteredReceipts, key = { it.id }, contentType = { "receipt_card" }) { receipt ->
                            ReceiptListCard(
                                receipt = receipt,
                                linkedTx = receipt.transactionId?.let { state.transactionsById[it] },
                                privacy = privacy,
                                onClick = { inspectedReceiptId = receipt.id }
                            )
                        }
                    }

                    item(key = "vault_footer") {
                        Text(
                            text = "Files stay private on this phone. Tax tags are personal notes only, not confirmed tax claims.",
                            style = type.caption,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEditor && !state.isSamplePreview) {
        ReceiptEditorSheet(
            existing = editingReceipt,
            prelinkedTransaction = prelinkedTransaction,
            linkableTransactions = state.linkableTransactions,
            expenseCategories = state.expenseCategories,
            fileStorage = fileStorage,
            onSave = { record, onErr ->
                onSaveReceipt(
                    record,
                    {
                        showEditor = false
                        editingReceipt = null
                        prelinkedTransaction = null
                    },
                    onErr
                )
            },
            onDismiss = {
                showEditor = false
                editingReceipt = null
                prelinkedTransaction = null
            }
        )
    }

    inspectedReceiptId?.let { receiptId ->
        val receipt = state.allReceipts.firstOrNull { it.id == receiptId }
        if (receipt != null) {
            ReceiptDetailSheet(
                receipt = receipt,
                linkedTransaction = receipt.transactionId?.let { state.transactionsById[it] },
                linkableTransactions = state.linkableTransactions,
                fileStorage = fileStorage,
                onEdit = {
                    inspectedReceiptId = null
                    editingReceipt = receipt
                    prelinkedTransaction = null
                    showEditor = true
                },
                onLinkTransaction = { txId -> onLinkTransaction(receipt.id, txId) },
                onDelete = {
                    onDeleteReceipt(receipt.id) { inspectedReceiptId = null }
                },
                onDismiss = { inspectedReceiptId = null }
            )
        } else {
            inspectedReceiptId = null
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = type.cardBalance, color = colors.primaryText)
        Text(label, style = type.caption, color = colors.secondaryText)
    }
}

@Composable
private fun ReceiptListCard(
    receipt: ReceiptRecord,
    linkedTx: FinanceTransaction?,
    privacy: PrivacySettings,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val icon = when {
        receipt.isPdf -> Icons.Rounded.PictureAsPdf
        receipt.hasAttachment -> Icons.Rounded.Image
        else -> Icons.Rounded.Description
    }
    val tone = when {
        receipt.isTaxTagged -> CandyTone.Sunny
        receipt.isLinked -> CandyTone.Mint
        else -> CandyTone.Sky
    }
    CandyBubbleCard(
        tone = tone,
        onClick = onClick,
        onClickLabel = "Open ${receipt.merchant} receipt",
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CandyIconBlob(icon = icon, tone = tone, size = 44.dp, solid = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = receipt.merchant,
                    style = type.cardTitle,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    add(LocalDate.ofEpochDay(receipt.dateEpochDay).format(RECEIPT_DATE_FMT))
                    if (receipt.category.isNotBlank()) add(receipt.category)
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = type.caption,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (receipt.amountSen != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = PrivacyMask.formatAmount(receipt.amountSen, privacy),
                    style = type.cardTitle,
                    color = colors.primaryText
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CandyStatusBadge(
                text = if (linkedTx != null) "Linked: ${linkedTx.title}" else if (receipt.isLinked) "Linked" else "Standalone",
                tone = if (receipt.isLinked) CandyTone.Mint else CandyTone.Sky
            )
            if (receipt.isTaxTagged) {
                CandyStatusBadge(text = receipt.taxTag.label, tone = CandyTone.Sunny)
            }
        }
    }
}

@Composable
fun ReceiptEditorSheet(
    existing: ReceiptRecord?,
    prelinkedTransaction: FinanceTransaction?,
    linkableTransactions: List<FinanceTransaction>,
    expenseCategories: List<Category>,
    fileStorage: ReceiptFileStorage,
    onSave: (ReceiptRecord, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val context = LocalContext.current
    val privacy = LocalPrivacySettings.current
    val scope = rememberCoroutineScope()

    var merchant by rememberSaveable {
        mutableStateOf(existing?.merchant ?: prelinkedTransaction?.title.orEmpty())
    }
    var amountInput by rememberSaveable {
        mutableStateOf(
            (existing?.amountSen ?: prelinkedTransaction?.amountSen)?.let { sen ->
                val abs = kotlin.math.abs(sen)
                String.format(Locale.ROOT, "%d.%02d", abs / 100L, abs % 100L)
            }.orEmpty()
        )
    }
    var dateEpochDay by rememberSaveable {
        mutableLongStateOf(
            existing?.dateEpochDay
                ?: prelinkedTransaction?.let {
                    java.time.Instant.ofEpochMilli(it.occurredAtEpochMs)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                }
                ?: LocalDate.now().toEpochDay()
        )
    }
    var category by rememberSaveable {
        mutableStateOf(existing?.category ?: prelinkedTransaction?.category.orEmpty())
    }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var taxTagName by rememberSaveable { mutableStateOf((existing?.taxTag ?: ReceiptTaxTag.NONE).name) }
    var linkedTxId by rememberSaveable { mutableStateOf(existing?.transactionId ?: prelinkedTransaction?.id) }
    var fileRelativePath by rememberSaveable { mutableStateOf(existing?.fileRelativePath.orEmpty()) }
    var mimeType by rememberSaveable { mutableStateOf(existing?.mimeType.orEmpty()) }
    var originalFileName by rememberSaveable { mutableStateOf(existing?.originalFileName.orEmpty()) }
    var fileSizeBytes by rememberSaveable { mutableLongStateOf(existing?.fileSizeBytes ?: 0L) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var newlyImportedPath by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showTxPicker by rememberSaveable { mutableStateOf(false) }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        formError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { fileStorage.importFromUri(context, uri) }
            }.onSuccess { stored ->
                newlyImportedPath?.takeIf { it != existing?.fileRelativePath }?.let { fileStorage.deleteFile(it) }
                newlyImportedPath = stored.relativePath
                fileRelativePath = stored.relativePath
                mimeType = stored.mimeType
                originalFileName = stored.originalFileName
                fileSizeBytes = stored.fileSizeBytes
                if (merchant.isBlank()) {
                    merchant = stored.originalFileName.substringBeforeLast('.').ifBlank { "Receipt" }
                }
            }.onFailure { err ->
                formError = err.message ?: "Couldn't attach file."
            }
            isImporting = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = pendingCameraFile
        pendingCameraFile = null
        if (!ok || temp == null) {
            temp?.delete()
            return@rememberLauncherForActivityResult
        }
        isImporting = true
        formError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { fileStorage.importCameraFile(temp) }
            }.onSuccess { stored ->
                newlyImportedPath?.takeIf { it != existing?.fileRelativePath }?.let { fileStorage.deleteFile(it) }
                newlyImportedPath = stored.relativePath
                fileRelativePath = stored.relativePath
                mimeType = stored.mimeType
                originalFileName = stored.originalFileName
                fileSizeBytes = stored.fileSizeBytes
            }.onFailure { err ->
                formError = err.message ?: "Couldn't save captured photo."
            }
            isImporting = false
        }
    }

    val cancelAndCleanup: () -> Unit = {
        newlyImportedPath?.takeIf { it != existing?.fileRelativePath }?.let { fileStorage.deleteFile(it) }
        pendingCameraFile?.delete()
        onDismiss()
    }

    CandyFullScreenForm(
        title = if (existing == null) "Add Receipt" else "Edit Receipt",
        onDismissRequest = cancelAndCleanup
    ) {
        if (formError != null) {
            PrismaticErrorBanner(message = formError!!, onDismiss = { formError = null })
            Spacer(Modifier.height(10.dp))
        }

        // Attachment actions
        Text("Receipt file", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpatialGlassButton(
                text = "Camera",
                enabled = !isImporting,
                variant = SpatialButtonVariant.SecondaryGlass,
                onClick = {
                    runCatching {
                        val (file, uri) = fileStorage.createTempCameraTarget(context)
                        pendingCameraFile = file
                        cameraLauncher.launch(uri)
                    }.onFailure {
                        formError = "Camera capture is unavailable in this build."
                    }
                },
                modifier = Modifier.weight(1f)
            )
            SpatialGlassButton(
                text = "Choose File",
                enabled = !isImporting,
                variant = SpatialButtonVariant.SecondaryGlass,
                onClick = { documentLauncher.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.weight(1f)
            )
        }

        if (fileRelativePath.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (mimeType.equals("application/pdf", true)) Icons.Rounded.PictureAsPdf else Icons.Rounded.AttachFile,
                        contentDescription = null,
                        tint = colors.primaryText
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = originalFileName.ifBlank { fileRelativePath },
                            style = type.supportingInfo,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(fileSizeBytes / 1024L).coerceAtLeast(1L)} KB · Private storage",
                            style = type.caption,
                            color = colors.secondaryText
                        )
                    }
                    IconButton(onClick = {
                        newlyImportedPath?.takeIf { it != existing?.fileRelativePath }?.let { fileStorage.deleteFile(it) }
                        newlyImportedPath = null
                        fileRelativePath = ""
                        mimeType = ""
                        originalFileName = ""
                        fileSizeBytes = 0L
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove attachment", tint = colors.secondaryText)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant / Title") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amountInput,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) {
                        amountInput = input
                    }
                },
                label = { Text("Amount RM (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )
            SpatialGlassButton(
                text = LocalDate.ofEpochDay(dateEpochDay).format(RECEIPT_DATE_FMT),
                variant = SpatialButtonVariant.SecondaryGlass,
                onClick = {
                    val d = LocalDate.ofEpochDay(dateEpochDay)
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            dateEpochDay = LocalDate.of(year, month + 1, dayOfMonth).toEpochDay()
                        },
                        d.year,
                        d.monthValue - 1,
                        d.dayOfMonth
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (expenseCategories.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                expenseCategories.take(10).forEach { cat ->
                    SpatialGlassPill(
                        label = cat.name,
                        selected = category.equals(cat.name, ignoreCase = true),
                        onClick = { category = cat.name }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Transaction link", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        val linkedTx = linkedTxId?.let { id -> linkableTransactions.firstOrNull { it.id == id } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialGlassButton(
                text = linkedTx?.title ?: "Standalone",
                variant = SpatialButtonVariant.SecondaryGlass,
                onClick = { showTxPicker = true },
                modifier = Modifier.weight(1f)
            )
            if (linkedTxId != null) {
                SpatialGlassButton(
                    text = "Unlink",
                    variant = SpatialButtonVariant.SecondaryGlass,
                    onClick = { linkedTxId = null }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Tax tag (optional)", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReceiptTaxTag.entries.forEach { tag ->
                SpatialGlassPill(
                    label = tag.label,
                    selected = taxTagName == tag.name,
                    onClick = { taxTagName = tag.name }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "For personal organization only — not a confirmed tax claim.",
            style = type.caption,
            color = colors.secondaryText
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
        SpatialGlassButton(
            text = if (existing == null) "Save Receipt" else "Update Receipt",
            enabled = !isImporting,
            onClick = {
                val parsedSen = parseOptionalRinggitToSen(amountInput)
                if (amountInput.isNotBlank() && parsedSen == null) {
                    formError = "Enter a valid RM amount."
                    return@SpatialGlassButton
                }
                val record = ReceiptRecord(
                    id = existing?.id ?: 0L,
                    transactionId = linkedTxId,
                    merchant = merchant.trim(),
                    dateEpochDay = dateEpochDay,
                    amountSen = parsedSen,
                    category = category.trim(),
                    note = note.trim(),
                    taxTag = ReceiptTaxTag.fromStored(taxTagName),
                    fileRelativePath = fileRelativePath,
                    mimeType = mimeType,
                    originalFileName = originalFileName,
                    fileSizeBytes = fileSizeBytes,
                    createdAtEpochMs = existing?.createdAtEpochMs ?: 0L,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                onSave(record) { err -> formError = err }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showTxPicker) {
        TransactionLinkPickerDialog(
            transactions = linkableTransactions,
            selectedTxId = linkedTxId,
            privacy = privacy,
            onSelect = { txId ->
                linkedTxId = txId
                showTxPicker = false
            },
            onDismiss = { showTxPicker = false }
        )
    }
}

@Composable
private fun ReceiptDetailSheet(
    receipt: ReceiptRecord,
    linkedTransaction: FinanceTransaction?,
    linkableTransactions: List<FinanceTransaction>,
    fileStorage: ReceiptFileStorage,
    onEdit: () -> Unit,
    onLinkTransaction: (Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showLinkPicker by rememberSaveable { mutableStateOf(false) }
    var previewBitmap by remember(receipt.fileRelativePath) { mutableStateOf<Bitmap?>(null) }
    var pdfPageIndex by rememberSaveable(receipt.id) { mutableIntStateOf(0) }
    var pdfPageCount by remember(receipt.fileRelativePath) { mutableIntStateOf(0) }
    var previewMissing by remember(receipt.fileRelativePath) { mutableStateOf(false) }

    LaunchedEffect(receipt.fileRelativePath, receipt.mimeType, pdfPageIndex) {
        if (!receipt.hasAttachment) {
            previewBitmap = null
            pdfPageCount = 0
            previewMissing = false
            return@LaunchedEffect
        }
        val old = previewBitmap
        if (receipt.isPdf) {
            val rendered = withContext(Dispatchers.IO) { fileStorage.renderPdfPage(receipt.fileRelativePath, pdfPageIndex) }
            previewBitmap = rendered?.bitmap
            pdfPageCount = rendered?.pageCount ?: 0
            previewMissing = rendered == null
        } else {
            val decoded = withContext(Dispatchers.IO) { fileStorage.decodeImagePreview(receipt.fileRelativePath) }
            previewBitmap = decoded
            pdfPageCount = 0
            previewMissing = decoded == null
        }
        if (old != null && old !== previewBitmap) old.recycle()
    }

    DisposableEffect(receipt.fileRelativePath) {
        onDispose {
            previewBitmap?.recycle()
            previewBitmap = null
        }
    }

    CandyFullScreenForm(
        title = receipt.merchant,
        subtitle = LocalDate.ofEpochDay(receipt.dateEpochDay).format(RECEIPT_DATE_FMT),
        onDismissRequest = onDismiss
    ) {
        if (receipt.hasAttachment) {
            CandyBubbleCard(tone = CandyTone.Sky, modifier = Modifier.fillMaxWidth()) {
                val bmp = previewBitmap
                when {
                    bmp != null -> {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "${receipt.merchant} receipt preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        if (receipt.isPdf && pdfPageCount > 1) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CandyMintIconButton(
                                    icon = Icons.Rounded.ChevronLeft,
                                    contentDescription = "Previous page",
                                    onClick = { if (pdfPageIndex > 0) pdfPageIndex-- }
                                )
                                Text("Page ${pdfPageIndex + 1} of $pdfPageCount", style = type.caption, color = colors.secondaryText)
                                CandyMintIconButton(
                                    icon = Icons.Rounded.ChevronRight,
                                    contentDescription = "Next page",
                                    onClick = { if (pdfPageIndex < pdfPageCount - 1) pdfPageIndex++ }
                                )
                            }
                        }
                    }
                    previewMissing -> {
                        Text(
                            text = "Attached file isn't available on this device.",
                            style = type.supportingInfo,
                            color = colors.secondaryText
                        )
                    }
                    else -> {
                        Text("Loading preview…", style = type.supportingInfo, color = colors.secondaryText)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        CandyBubbleCard(tone = CandyTone.Peach, modifier = Modifier.fillMaxWidth()) {
            ReceiptDetailRow("Date", LocalDate.ofEpochDay(receipt.dateEpochDay).format(RECEIPT_DATE_FMT))
            if (receipt.amountSen != null) {
                ReceiptDetailRow("Amount", PrivacyMask.formatAmount(receipt.amountSen, privacy))
            }
            if (receipt.category.isNotBlank()) {
                ReceiptDetailRow("Category", receipt.category)
            }
            ReceiptDetailRow(
                label = "Status",
                value = linkedTransaction?.title?.let { "Linked · $it" }
                    ?: if (receipt.isLinked) "Linked" else "Standalone"
            )
            ReceiptDetailRow("Tax tag", receipt.taxTag.label)
            if (receipt.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Notes", style = type.caption, color = colors.secondaryText)
                Text(receipt.note, style = type.supportingInfo, color = colors.primaryText)
            }
            if (receipt.isTaxTagged) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tax tags are personal filing notes only, not confirmed tax claims.",
                    style = type.caption,
                    color = colors.secondaryText
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpatialGlassButton(
                text = if (receipt.isLinked) "Change Link" else "Attach Tx",
                variant = SpatialButtonVariant.SecondaryGlass,
                onClick = { showLinkPicker = true },
                modifier = Modifier.weight(1f)
            )
            if (receipt.isLinked) {
                SpatialGlassButton(
                    text = "Unlink",
                    variant = SpatialButtonVariant.SecondaryGlass,
                    onClick = { onLinkTransaction(null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpatialGlassButton(
                text = "Delete",
                variant = SpatialButtonVariant.SecondaryGlass,
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.weight(1f)
            )
            SpatialGlassButton(
                text = "Edit",
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showLinkPicker) {
        TransactionLinkPickerDialog(
            transactions = linkableTransactions,
            selectedTxId = receipt.transactionId,
            privacy = privacy,
            onSelect = { txId ->
                onLinkTransaction(txId)
                showLinkPicker = false
            },
            onDismiss = { showLinkPicker = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete receipt?") },
            text = {
                Text("This permanently deletes \"${receipt.merchant}\" and its private file. Your wallet balances and transactions will not change.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReceiptDetailRow(label: String, value: String) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = type.caption, color = colors.secondaryText)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = type.supportingInfo,
            color = colors.primaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransactionLinkPickerDialog(
    transactions: List<FinanceTransaction>,
    selectedTxId: Long?,
    privacy: PrivacySettings,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to transaction") },
        text = {
            if (transactions.isEmpty()) {
                Text("No income or expense transactions yet. Receipt will stay standalone.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "none") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClickLabel = "Keep standalone") { onSelect(null) }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = colors.secondaryText)
                            Spacer(Modifier.width(10.dp))
                            Text("Standalone (no link)", style = type.supportingInfo, color = colors.primaryText)
                        }
                    }
                    items(transactions, key = { it.id }) { tx ->
                        val isSelected = tx.id == selectedTxId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) colors.glassSurfaceSubtle else colors.appBackground.copy(alpha = 0f))
                                .clickable(onClickLabel = "Link to ${tx.title}") { onSelect(tx.id) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = if (isSelected) colors.coral else colors.secondaryText)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tx.title, style = type.supportingInfo, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tx.category, style = type.caption, color = colors.secondaryText, maxLines = 1)
                            }
                            Text(
                                text = PrivacyMask.formatAmount(tx.amountSen, privacy),
                                style = type.caption,
                                color = colors.primaryText
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Pure integer-string parser (Ringgit -> sen) without Double/Float rounding. */
internal fun parseOptionalRinggitToSen(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val match = Regex("^(\\d+)(?:\\.(\\d{0,2}))?$").matchEntire(trimmed) ?: return null
    val ringgit = match.groupValues[1].toLongOrNull() ?: return null
    val senStr = match.groupValues[2].padEnd(2, '0')
    val sen = senStr.toLongOrNull() ?: return null
    return runCatching { Math.addExact(Math.multiplyExact(ringgit, 100L), sen) }.getOrNull()
}
