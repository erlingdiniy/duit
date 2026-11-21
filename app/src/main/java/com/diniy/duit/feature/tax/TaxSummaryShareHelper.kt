package com.diniy.duit.feature.tax

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.tax.TaxSummaryCsvBuilder
import com.diniy.duit.domain.tax.TaxYearSummary
import java.io.File

/**
 * Exports a Year of Assessment summary CSV to cacheDir/shared/ and launches Android Sharesheet
 * via duit.'s existing FileProvider authority (.supportqr). Purely offline; zero network access.
 */
internal object TaxSummaryShareHelper {
    private const val SHARE_DIR = "shared"
    private const val FILE_PROVIDER_SUFFIX = ".supportqr"

    fun shareCsvSummary(
        context: Context,
        summary: TaxYearSummary,
        receipts: List<ReceiptRecord>
    ): Boolean = runCatching {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "duit-tax-pocket-YA${summary.assessmentYear}.csv")
        val csv = TaxSummaryCsvBuilder.buildCsv(summary, receipts.associateBy { it.id })
        file.writeText(csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$FILE_PROVIDER_SUFFIX",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "duit. My Tax Pocket — YA ${summary.assessmentYear}")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Tax Relief Summary YA ${summary.assessmentYear}", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export YA ${summary.assessmentYear} Summary").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}
