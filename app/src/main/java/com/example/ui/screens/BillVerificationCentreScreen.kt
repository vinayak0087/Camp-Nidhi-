package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BillExtraction
import com.example.data.model.BillOriginal
import com.example.data.model.BillVerificationStatus
import com.example.data.model.BillVersion
import com.example.domain.FinanceCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusNeutralContainer
import com.example.ui.theme.StatusNeutralSlate
import com.example.ui.theme.StatusPaidContainer
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusPendingContainer
import com.example.ui.viewmodel.CampWorkspaceData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillVerificationCentreScreen(
    workspace: CampWorkspaceData,
    billOriginals: List<BillOriginal>,
    billExtractions: List<BillExtraction>,
    billVersions: List<BillVersion>,
    isPrimaryAdmin: Boolean,
    onOpenScanner: () -> Unit,
    onAdminUpdateBill: (
        bill: BillExtraction,
        vendor: String,
        billNumber: String,
        billDate: String,
        gstin: String,
        description: String,
        grandTotal: Double?,
        officialExplanation: String,
        adminAnnotations: String,
        newStatus: BillVerificationStatus,
        replacementUri: Uri?
    ) -> Unit
) {
    val activeCamp = workspace.activeCamp
    val campBills = remember(billExtractions, activeCamp?.id) {
        if (activeCamp == null) billExtractions else billExtractions.filter { it.campId == activeCamp.id }
    }

    val awaitingCount = campBills.count { it.status == BillVerificationStatus.SUBMITTED }
    val verifiedBills = campBills.filter { it.status == BillVerificationStatus.VERIFIED }
    val rejectedCount = campBills.count { it.status == BillVerificationStatus.REJECTED || it.status == BillVerificationStatus.DISPUTED }
    val duplicateAlertCount = campBills.count { bill ->
        bill.anomalyWarnings.any { it.contains("duplicate", ignoreCase = true) }
    }
    val linkedExpenseIds = campBills.map { it.expenseId }.filter { it.isNotBlank() }.toSet()
    val missingBillsCount = workspace.expenses.count { exp ->
        exp.id !in linkedExpenseIds && exp.receiptThumbnailBase64.isBlank() && exp.receiptUri.isBlank()
    }
    val totalVerifiedAmount = verifiedBills.sumOf { it.grandTotal ?: 0.0 }

    var statusFilter by remember { mutableStateOf<BillVerificationStatus?>(null) }
    var committeeFilter by remember { mutableStateOf("ALL") }
    var searchVendorOrDate by remember { mutableStateOf("") }
    var inspectingBill by remember { mutableStateOf<BillExtraction?>(null) }

    val filteredBills = remember(campBills, statusFilter, committeeFilter, searchVendorOrDate) {
        campBills.filter { b ->
            val okStatus = statusFilter == null || b.status == statusFilter
            val okComm = committeeFilter == "ALL" || b.committeeName.equals(committeeFilter, ignoreCase = true)
            val q = searchVendorOrDate.trim()
            val okSearch = q.isBlank() ||
                b.vendorName.contains(q, ignoreCase = true) ||
                b.billNumber.contains(q, ignoreCase = true) ||
                b.billDate.contains(q, ignoreCase = true) ||
                b.billVerificationId.contains(q, ignoreCase = true)
            okStatus && okComm && okSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bill Verification Centre", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Compare immutable originals against verified records and resolve anomalies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onOpenScanner) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Bill")
                }
            }
        }

        // 7 Verification Centre Metrics
        item {
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = "Total Bills Uploaded",
                    value = "${campBills.size}",
                    subtitle = "Preserved originals",
                    icon = Icons.Default.Receipt,
                    accentColor = NavyPrimary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Awaiting Verification",
                    value = "$awaitingCount",
                    subtitle = "Submitted queue",
                    icon = Icons.Default.Warning,
                    accentColor = StatusPendingAmber,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Verified Bills",
                    value = "${verifiedBills.size}",
                    subtitle = FinanceCalculator.formatInr(totalVerifiedAmount),
                    icon = Icons.Default.Verified,
                    accentColor = StatusPaidGreen,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Rejected / Disputed",
                    value = "$rejectedCount",
                    subtitle = "Flagged vouchers",
                    icon = Icons.Default.ErrorOutline,
                    accentColor = StatusDangerCrimson,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Duplicate Alerts",
                    value = "$duplicateAlertCount",
                    subtitle = "Hash / invoice matches",
                    icon = Icons.Default.Warning,
                    accentColor = StatusPendingAmber,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Missing Bills",
                    value = "$missingBillsCount",
                    subtitle = "Expenses without receipt",
                    icon = Icons.Default.Receipt,
                    accentColor = StatusInfoBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchVendorOrDate,
                    onValueChange = { searchVendorOrDate = it },
                    label = { Text("Search vendor, invoice no, date, or BV ID...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All Status") }
                    )
                    BillVerificationStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = if (statusFilter == st) null else st },
                            label = { Text(st.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = committeeFilter == "ALL",
                        onClick = { committeeFilter = "ALL" },
                        label = { Text("All Committees") }
                    )
                    workspace.committees.forEach { comm ->
                        FilterChip(
                            selected = committeeFilter == comm.name,
                            onClick = { committeeFilter = comm.name },
                            label = { Text(comm.name) }
                        )
                    }
                }
            }
        }

        if (filteredBills.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Scanned Bills Match Filter",
                    message = "Upload or capture bills in Smart Bill Scanner to track immutable originals and verification status.",
                    icon = Icons.Default.DocumentScanner,
                    actionLabel = "Open Smart Bill Scanner",
                    onActionClick = onOpenScanner
                )
            }
        } else {
            items(filteredBills, key = { it.id }) { bill ->
                val (bg, fg) = billStatusColors(bill.status)
                Card(
                    onClick = { inspectingBill = bill },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bill_card_${bill.billVerificationId}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bill.billVerificationId,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatusPill(text = bill.status.label, containerColor = bg, contentColor = fg)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bill.vendorName.ifBlank { "Vendor Pending" },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = FinanceCalculator.formatInr(bill.grandTotal ?: 0.0),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            "Invoice: ${bill.billNumber.ifBlank { "-" }} • Date: ${bill.billDate.ifBlank { "-" }} • ${bill.committeeName.ifBlank { "General" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bill.expenseCode.isNotBlank()) {
                            Text(
                                "Linked Expense: ${bill.expenseCode} (${bill.expenseApprovalStatus}) • Version v${bill.currentVersionNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForestGreenSecondary
                            )
                        }
                        if (bill.anomalyWarnings.isNotEmpty()) {
                            Text(
                                "⚠ ${bill.anomalyWarnings.first()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusPendingAmber
                            )
                        }
                    }
                }
            }
        }
    }

    inspectingBill?.let { bill ->
        val original = billOriginals.firstOrNull { it.id == bill.billOriginalId || it.id == bill.id }
        val versions = billVersions.filter { it.billId == bill.id }
        BillInspectorDialog(
            bill = bill,
            original = original,
            versions = versions,
            isPrimaryAdmin = isPrimaryAdmin,
            onSave = { v, num, date, gst, desc, total, expl, ann, st, repUri ->
                onAdminUpdateBill(bill, v, num, date, gst, desc, total, expl, ann, st, repUri)
                inspectingBill = null
            },
            onDismiss = { inspectingBill = null }
        )
    }
}

@Composable
private fun BillInspectorDialog(
    bill: BillExtraction,
    original: BillOriginal?,
    versions: List<BillVersion>,
    isPrimaryAdmin: Boolean,
    onSave: (String, String, String, String, String, Double?, String, String, BillVerificationStatus, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var vendor by remember(bill.id) { mutableStateOf(bill.vendorName) }
    var billNumber by remember(bill.id) { mutableStateOf(bill.billNumber) }
    var billDate by remember(bill.id) { mutableStateOf(bill.billDate) }
    var gstin by remember(bill.id) { mutableStateOf(bill.gstin) }
    var description by remember(bill.id) { mutableStateOf(bill.itemDescription) }
    var grandTotalText by remember(bill.id) { mutableStateOf(bill.grandTotal?.toString().orEmpty()) }
    var explanation by remember(bill.id) { mutableStateOf(bill.officialExplanation) }
    var annotations by remember(bill.id) { mutableStateOf(bill.adminAnnotations) }
    var replacementUri by remember { mutableStateOf<Uri?>(null) }

    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) replacementUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${bill.billVerificationId} Inspection") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Verified Record") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Original (Read-Only)") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("History (${versions.size})") })
                }

                when (selectedTab) {
                    0 -> {
                        if (!isPrimaryAdmin) {
                            Text(
                                "Read-Only View: Only the Primary Admin may approve official corrections to verified financial information.",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusPendingAmber
                            )
                        }
                        OutlinedTextField(
                            value = vendor,
                            onValueChange = { vendor = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Vendor Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = billNumber,
                            onValueChange = { billNumber = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Bill Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = billDate,
                            onValueChange = { billDate = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Bill Date") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = gstin,
                            onValueChange = { gstin = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("GSTIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Official Item Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = grandTotalText,
                            onValueChange = { grandTotalText = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Verified Grand Total (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = explanation,
                            onValueChange = { explanation = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Official Explanation (Logged in Audit Trail)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = annotations,
                            onValueChange = { annotations = it },
                            enabled = isPrimaryAdmin,
                            label = { Text("Admin Annotations") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isPrimaryAdmin) {
                            OutlinedButton(
                                onClick = { replacementPicker.launch(arrayOf("application/pdf", "image/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (replacementUri != null) "Corrected Vendor Invoice Attached ✓" else "Attach Corrected Vendor Invoice")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        onSave(
                                            vendor, billNumber, billDate, gstin, description,
                                            grandTotalText.toDoubleOrNull(), explanation, annotations,
                                            BillVerificationStatus.VERIFIED, replacementUri
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Approve & Verify")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onSave(
                                            vendor, billNumber, billDate, gstin, description,
                                            grandTotalText.toDoubleOrNull(), explanation, annotations,
                                            BillVerificationStatus.DISPUTED, replacementUri
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mark Disputed")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        onSave(
                                            vendor, billNumber, billDate, gstin, description,
                                            grandTotalText.toDoubleOrNull(), explanation, annotations,
                                            BillVerificationStatus.SUPERSEDED, replacementUri
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Supersede")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onSave(
                                            vendor, billNumber, billDate, gstin, description,
                                            grandTotalText.toDoubleOrNull(), explanation, annotations,
                                            BillVerificationStatus.REJECTED, replacementUri
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reject")
                                }
                            }
                        }
                    }

                    1 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = ForestGreenSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Permanent Read-Only Original Upload",
                                style = MaterialTheme.typography.titleSmall,
                                color = ForestGreenSecondary
                            )
                        }
                        if (original != null) {
                            val bmp = remember(original.id) {
                                runCatching {
                                    val bytes = Base64.decode(original.originalPreviewBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Original Immutable Bill",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            Text("Uploaded By: ${original.uploaderName} (${original.uploaderEmail})", style = MaterialTheme.typography.bodySmall)
                            Text("Uploaded At: ${formatShortDateTime(original.uploadedAtMillis)}", style = MaterialTheme.typography.bodySmall)
                            Text("SHA-256: ${original.fileHashSha256}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                            Text("Storage Ref: ${original.protectedStorageRef}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                        } else {
                            Text("Original document metadata syncing...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    2 -> {
                        if (versions.isEmpty()) {
                            Text("Version history is loading.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            versions.forEach { ver ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "v${ver.versionNumber} • ${ver.changeType}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Text(
                                        "${ver.vendorName} • ${ver.billNumber} • ₹${ver.grandTotal?.toLong() ?: 0L}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (ver.explanation.isNotBlank()) {
                                        Text("Note: ${ver.explanation}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        "By ${ver.actorName} (${formatShortDateTime(ver.timestampMillis)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun billStatusColors(status: BillVerificationStatus) = when (status) {
    BillVerificationStatus.VERIFIED -> StatusPaidContainer to StatusPaidGreen
    BillVerificationStatus.SUBMITTED -> StatusInfoContainer to StatusInfoBlue
    BillVerificationStatus.DISPUTED -> StatusPendingContainer to StatusPendingAmber
    BillVerificationStatus.REJECTED -> StatusDangerContainer to StatusDangerCrimson
    BillVerificationStatus.SUPERSEDED -> StatusNeutralContainer to StatusNeutralSlate
    BillVerificationStatus.DRAFT -> StatusNeutralContainer to StatusNeutralSlate
}

private fun formatShortDateTime(millis: Long): String {
    if (millis <= 0L) return "-"
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
}
