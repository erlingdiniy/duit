package com.diniy.duit.devcenter.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.FeedbackSourceState

private val FEEDBACK_CATEGORIES = listOf("All", "Bug Report", "Feature Request", "General Feedback")
private const val MAX_CSV_BYTES = 2 * 1024 * 1024L

@Composable
fun DevFeedbackScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.authManager.clearPickerBackgroundSuppression()
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            var label = "feedback.csv"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) label = c.getString(nameIdx) ?: label
                    if (sizeIdx >= 0 && c.getLong(sizeIdx) > MAX_CSV_BYTES) {
                        error("CSV exceeds 2 MB limit.")
                    }
                }
            }
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }.orEmpty()
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_CSV_BYTES) { "CSV exceeds 2 MB limit." }
            viewModel.importFeedbackCsv(raw, label)
        }.onFailure { err ->
            viewModel.reportFeedbackImportError(err.message ?: "Unable to read CSV file.")
        }
    }

    val launchCsvPicker = {
        viewModel.authManager.suppressNextBackgroundLockForPicker()
        csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val fb = uiState.feedbackState) {
            is FeedbackSourceState.Unconfigured -> {
                item(key = "fb_unconfigured") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.skyBlue
                    ) {
                        DevSectionTitle("Feedback Setup")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = fb.guideMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
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
            is FeedbackSourceState.Error -> {
                item(key = "fb_error") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.error
                    ) {
                        DevSectionTitle("Feedback Unavailable")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = fb.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
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
            is FeedbackSourceState.Ready -> {
                val query = uiState.feedbackSearchQuery.trim().lowercase()
                val filtered = fb.items.filter { entry ->
                    val matchesCategory = uiState.feedbackCategoryFilter == "All" ||
                        entry.category == uiState.feedbackCategoryFilter
                    val matchesQuery = query.isEmpty() ||
                        entry.message.lowercase().contains(query) ||
                        entry.appVersion.lowercase().contains(query) ||
                        entry.status.lowercase().contains(query) ||
                        entry.email.lowercase().contains(query)
                    matchesCategory && matchesQuery
                }

                item(key = "fb_controls") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle(fb.sourceLabel)
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PrismaticTextField(
                            value = uiState.feedbackSearchQuery,
                            onValueChange = viewModel::updateFeedbackSearch,
                            label = "Search feedback",
                            placeholder = "Keyword, version or status"
                        )
                        Spacer(Modifier.height(8.dp))
                        DevChoiceChipRow(
                            items = FEEDBACK_CATEGORIES,
                            selected = uiState.feedbackCategoryFilter,
                            labelOf = { it },
                            onSelect = viewModel::updateFeedbackCategoryFilter
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${filtered.size} of ${fb.items.size} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "fb_empty_filter") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text(
                                text = "No feedback entries match your filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { entry ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DevToneBadge(label = entry.category, tone = "neutral")
                                DevToneBadge(label = entry.status, tone = entry.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(6.dp))
                            val meta = buildString {
                                append("v").append(entry.appVersion)
                                append(", ").append(formatIsoShort(entry.timestampIso))
                                if (entry.email.isNotBlank()) append(", ").append(entry.email)
                            }
                            Text(
                                text = meta,
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
