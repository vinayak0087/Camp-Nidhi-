package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Camp
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomeRecord
import com.example.domain.CostIntelligenceReport
import com.example.domain.DashboardSummary
import com.example.domain.FinanceCalculator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generateFinancialStatementPdf(
        context: Context,
        camp: Camp,
        summary: DashboardSummary,
        costIntel: CostIntelligenceReport,
        incomes: List<IncomeRecord>,
        expenses: List<ExpenseRecord>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = camp.name.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
        val file = File(dir, "CampNidhi_Statement_${safeName}.pdf")

        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas
        var y = 48f

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 41, 66)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(30, 107, 71)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(22, 32, 42)
            textSize = 10.5f
        }
        val boldBodyPaint = Paint().apply {
            color = Color.rgb(22, 32, 42)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(83, 96, 110)
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 194, 182)
            strokeWidth = 1f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 48f) {
                pdf.finishPage(page)
                pageNum += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 48f
            }
        }

        fun drawHeading(text: String) {
            ensureSpace(30f)
            y += 8f
            canvas.drawText(text, 40f, y, sectionPaint)
            y += 6f
            canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
            y += 14f
        }

        fun drawRow(label: String, value: String, bold: Boolean = false) {
            ensureSpace(18f)
            canvas.drawText(label.take(65), 44f, y, if (bold) boldBodyPaint else bodyPaint)
            canvas.drawText(value.take(35), 390f, y, boldBodyPaint)
            y += 15f
        }

        // Header
        canvas.drawText("CAMPNIDHI — FINAL FINANCIAL STATEMENT", 40f, y, titlePaint)
        y += 18f
        canvas.drawText("Report Date: ${dateFmt.format(Date())}  |  Camp: ${camp.name}", 40f, y, mutedPaint)
        y += 14f
        canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
        y += 18f

        // 1. Camp Details
        drawHeading("1. Camp Details")
        drawRow("Camp Name", camp.name)
        drawRow("Institution / Organisation", camp.institution.ifBlank { "-" })
        drawRow("Location", camp.location.ifBlank { "-" })
        drawRow("Camp Dates", "${dateFmt.format(Date(camp.startDateMillis))} to ${dateFmt.format(Date(camp.endDateMillis))}")
        drawRow("Participants", "${camp.participantCount}")
        drawRow("Camp Coordinator", camp.coordinatorName.ifBlank { "-" })
        drawRow("Finance Committee Leader", camp.financeLeaderName.ifBlank { "-" })

        // 2-9. Core Financial Summary
        drawHeading("2. Budget, Funds & Balance Overview")
        drawRow("Total Approved Budget", FinanceCalculator.formatInr(summary.totalBudget), bold = true)
        drawRow("Total Funds Received (Cash + Bank/UPI)", FinanceCalculator.formatInr(summary.totalFundsReceived), bold = true)
        drawRow("Total Promised Contributions", FinanceCalculator.formatInr(summary.totalPromisedFunds))
        drawRow("Total Participant Contributions Received", FinanceCalculator.formatInr(summary.totalParticipantContributions))
        drawRow("Outstanding Promised Contributions", FinanceCalculator.formatInr(summary.outstandingContributions))
        drawRow("Total Committed Expenditure", FinanceCalculator.formatInr(summary.totalExpensesCommitted), bold = true)
        drawRow("Total Paid Expenditure", FinanceCalculator.formatInr(summary.totalExpensesPaid))
        drawRow("Outstanding Vendor Payments", FinanceCalculator.formatInr(summary.outstandingPayments))
        drawRow("Remaining Cash Balance (Received - Paid)", FinanceCalculator.formatInr(summary.remainingCashBalance), bold = true)
        drawRow("Remaining Budget (Budget - Committed)", FinanceCalculator.formatInr(summary.remainingBudget))
        drawRow("Cost Per Participant", FinanceCalculator.formatInr(costIntel.totalCostPerParticipant))

        // 6. Committee-wise Expenditure
        drawHeading("3. Committee-Wise Budget vs Expenditure")
        drawRow("Committee", "Allocated | Spent | Rem")
        summary.committeeSummaries.forEach { cs ->
            val lineVal = "${FinanceCalculator.formatInr(cs.allocatedBudget)} | ${FinanceCalculator.formatInr(cs.amountSpent)} | ${FinanceCalculator.formatInr(cs.remainingBudget)}"
            drawRow(cs.committee.name, lineVal)
        }

        // 10. Verified & Pending
        drawHeading("4. Verification & Expense Audit Status")
        drawRow("Verified Receipt Expenses", "${summary.verifiedExpensesCount} entries")
        drawRow("Unverified Receipt Expenses", "${summary.pendingVerificationCount} entries")
        drawRow("Pending Approvals", "${summary.pendingApprovalCount} (${FinanceCalculator.formatInr(summary.pendingApprovalAmount)})")

        // 11. Income & Expense Summary
        drawHeading("5. Income & Expense Ledger Highlights")
        drawRow("Income Entries Recorded", "${incomes.size}")
        incomes.take(12).forEach { inc ->
            drawRow("${inc.transactionCode} • ${inc.source}", FinanceCalculator.formatInr(inc.receivedAmount))
        }
        drawRow("Expense Entries Recorded", "${expenses.size}", bold = true)
        expenses.take(15).forEach { exp ->
            drawRow("${exp.expenseCode} • ${exp.vendorName} (${exp.paymentStatus})", FinanceCalculator.formatInr(exp.amount))
        }

        // 12. Auditor Remarks
        drawHeading("6. Auditor Remarks")
        val remarks = camp.auditorRemarks.ifBlank { "No formal auditor remarks recorded yet." }
        remarks.chunked(85).forEach { chunk ->
            ensureSpace(16f)
            canvas.drawText(chunk, 44f, y, bodyPaint)
            y += 14f
        }

        // 13. Treasurer Declaration
        drawHeading("7. Treasurer Declaration")
        camp.treasurerDeclaration.chunked(85).forEach { chunk ->
            ensureSpace(16f)
            canvas.drawText(chunk, 44f, y, bodyPaint)
            y += 14f
        }
        y += 18f
        ensureSpace(24f)
        canvas.drawText("Treasurer: ${camp.financeLeaderName.ifBlank { "Finance Leader" }}", 44f, y, boldBodyPaint)
        canvas.drawText("Coordinator: ${camp.coordinatorName.ifBlank { "Coordinator" }}", 340f, y, boldBodyPaint)

        pdf.finishPage(page)
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        return file
    }

    fun generateCsvExport(
        context: Context,
        camp: Camp,
        incomes: List<IncomeRecord>,
        expenses: List<ExpenseRecord>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = camp.name.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
        val file = File(dir, "CampNidhi_Ledger_${safeName}.csv")
        val sb = StringBuilder()
        sb.appendLine("TYPE,CODE,DATE,CATEGORY_OR_COMMITTEE,SOURCE_OR_VENDOR,PROMISED_OR_BILLED,RECEIVED_OR_PAID,METHOD,STATUS,REFERENCE,VERIFIED,NOTES")
        incomes.forEach { inc ->
            sb.appendLine(
                listOf(
                    "INCOME",
                    inc.transactionCode,
                    dateFmt.format(Date(inc.dateMillis)),
                    inc.category,
                    inc.source,
                    inc.promisedAmount.toString(),
                    inc.receivedAmount.toString(),
                    inc.paymentMethod,
                    inc.paymentStatus,
                    inc.referenceNumber,
                    "N/A",
                    inc.notes
                ).joinToString(",") { csvEscape(it) }
            )
        }
        expenses.forEach { exp ->
            sb.appendLine(
                listOf(
                    "EXPENSE",
                    exp.expenseCode,
                    dateFmt.format(Date(exp.dateMillis)),
                    "${exp.committeeName} / ${exp.category}",
                    exp.vendorName,
                    exp.amount.toString(),
                    exp.paidAmount.toString(),
                    exp.paymentMethod,
                    exp.paymentStatus,
                    exp.paymentReference,
                    if (exp.receiptVerified) "VERIFIED" else "UNVERIFIED",
                    exp.description
                ).joinToString(",") { csvEscape(it) }
            )
        }
        file.writeText(sb.toString())
        return file
    }

    fun parseCsvLines(context: Context, uri: Uri): List<List<String>> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return emptyList()
        return text.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val result = mutableListOf<String>()
                val current = StringBuilder()
                var inQuotes = false
                for (ch in line) {
                    when {
                        ch == '"' -> inQuotes = !inQuotes
                        ch == ',' && !inQuotes -> {
                            result.add(current.toString().trim())
                            current.clear()
                        }
                        else -> current.append(ch)
                    }
                }
                result.add(current.toString().trim())
                result
            }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("\n", " ").replace("\r", " ")
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else clean
    }
}
