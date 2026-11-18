package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BudgetTransfer
import com.example.data.model.Camp
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.PaymentMethod
import com.example.data.model.TransferStatus
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExpenseStatusPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusPendingContainer
import com.example.ui.viewmodel.CampWorkspaceData

@Composable
fun ApprovalCentreScreen(
    workspace: CampWorkspaceData,
    currentUserId: String,
    strings: AppStrings,
    onApproveOrRejectExpense: (Camp, ExpenseRecord, Boolean, String, UserRole) -> Unit,
    onRecordPayment: (Camp, ExpenseRecord, Double, PaymentMethod, String, String, UserRole) -> Unit,
    onVerifyReceipt: (Camp, ExpenseRecord, Boolean, String, UserRole) -> Unit,
    onResolveTransfer: (Camp, BudgetTransfer, Committee?, Committee?, Boolean, UserRole) -> Unit
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Select a camp to manage approvals.")
        return
    }
    val role = workspace.effectiveRole
    val canApprove = role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER
    val canPay = role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER
    val canVerifyReceipt = role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER || role == UserRole.AUDITOR

    val pendingApprovals = workspace.expenses.filter {
        it.status == ExpensePaymentStatus.SUBMITTED || it.status == ExpensePaymentStatus.PENDING_APPROVAL
    }
    val awaitingPayment = workspace.expenses.filter {
        it.status == ExpensePaymentStatus.APPROVED || it.status == ExpensePaymentStatus.PARTIALLY_PAID
    }
    val unverifiedReceipts = workspace.expenses.filter {
        (it.status == ExpensePaymentStatus.PAID || it.status == ExpensePaymentStatus.PARTIALLY_PAID) && !it.receiptVerified
    }
    val pendingTransfers = workspace.transfers.filter { it.transferStatus == TransferStatus.PENDING }

    var paymentTarget by remember { mutableStateOf<ExpenseRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Approval Policy Governance", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "High-value alert limit: ${FinanceCalculator.formatInr(camp.approvalLimit)} • Independent verification: ${if (camp.requireIndependentApproval) "Required" else "Relaxed"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Pending Expense Approvals
        item {
            SectionHeader(
                title = "Expense Approval Queue (${pendingApprovals.size})",
                subtitle = "Step 1: Verify budget & approve submitted expenses"
            )
        }
        if (pendingApprovals.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Pending Expense Approvals",
                    message = "All submitted committee expenses have been reviewed.",
                    icon = Icons.Default.Approval
                )
            }
        } else {
            items(pendingApprovals, key = { "app_${it.id}" }) { exp ->
                val isSelfSubmitted = exp.submittedByUid == currentUserId
                val blockedByIndependentPolicy = camp.requireIndependentApproval && isSelfSubmitted && role != UserRole.SYSTEM_ADMIN
                val exceedsLimit = exp.amount >= camp.approvalLimit

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${exp.expenseCode} • ${exp.committeeName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (exceedsLimit) {
                                    StatusPill(
                                        text = "Above Limit",
                                        containerColor = StatusPendingContainer,
                                        contentColor = StatusPendingAmber
                                    )
                                }
                                ExpenseStatusPill(exp.status)
                            }
                        }
                        Text(exp.description, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Vendor: ${exp.vendorName} • Submitted by ${exp.submittedByName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            FinanceCalculator.formatInr(exp.amount),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                        )

                        if (blockedByIndependentPolicy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusPendingAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Independent approval required: another authorized reviewer must approve your submission.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusPendingAmber
                                )
                            }
                        }

                        if (canApprove) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onApproveOrRejectExpense(camp, exp, false, "Rejected in Approval Centre", role) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.reject)
                                }
                                Button(
                                    onClick = { onApproveOrRejectExpense(camp, exp, true, "Approved for disbursement", role) },
                                    enabled = !blockedByIndependentPolicy,
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenSecondary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.approve)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Approved Expenses Awaiting Payment
        item {
            SectionHeader(
                title = "Ready for Payment (${awaitingPayment.size})",
                subtitle = "Step 2: Treasurer records cash, UPI, or bank disbursement"
            )
        }
        items(awaitingPayment, key = { "pay_${it.id}" }) { exp ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${exp.expenseCode} • ${exp.vendorName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Due: ${FinanceCalculator.formatInr(exp.balanceDue)} of ${FinanceCalculator.formatInr(exp.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canPay) {
                        Button(onClick = { paymentTarget = exp }) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pay")
                        }
                    } else {
                        ExpenseStatusPill(exp.status)
                    }
                }
            }
        }

        // Section 3: Receipt Verification Queue (Auditor / Treasurer)
        item {
            SectionHeader(
                title = "Receipt Verification (${unverifiedReceipts.size})",
                subtitle = "Step 3: Auditor / Finance checks bill evidence"
            )
        }
        items(unverifiedReceipts, key = { "ver_${it.id}" }) { exp ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${exp.expenseCode} • ${exp.vendorName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (exp.hasReceiptEvidence) "Receipt attached • Ready to verify" else "No receipt photo uploaded yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (exp.hasReceiptEvidence) ForestGreenSecondary else StatusDangerCrimson
                        )
                    }
                    if (canVerifyReceipt) {
                        OutlinedButton(
                            onClick = { onVerifyReceipt(camp, exp, true, "Receipt verified in Approval Centre", role) }
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.verifyReceipt)
                        }
                    }
                }
            }
        }

        // Section 4: Pending Committee Budget Transfers
        if (pendingTransfers.isNotEmpty()) {
            item {
                SectionHeader(title = "Pending Budget Transfers (${pendingTransfers.size})")
            }
            items(pendingTransfers, key = { "trf_${it.id}" }) { trf ->
                val from = workspace.committees.firstOrNull { it.id == trf.fromCommitteeId }
                val to = workspace.committees.firstOrNull { it.id == trf.toCommitteeId }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${trf.fromCommitteeName} → ${trf.toCommitteeName}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${FinanceCalculator.formatInr(trf.amount)} • ${trf.reason}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (canApprove) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onResolveTransfer(camp, trf, from, to, false, role) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.reject) }
                                Button(
                                    onClick = { onResolveTransfer(camp, trf, from, to, true, role) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.approve) }
                            }
                        }
                    }
                }
            }
        }
    }

    paymentTarget?.let { target ->
        RecordPaymentDialog(
            expense = target,
            onConfirm = { amt, method, ref ->
                onRecordPayment(camp, target, amt, method, ref, target.sourceIncomeCode, role)
                paymentTarget = null
            },
            onDismiss = { paymentTarget = null }
        )
    }
}

@Composable
private fun RecordPaymentDialog(
    expense: ExpenseRecord,
    onConfirm: (Double, PaymentMethod, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf(expense.balanceDue.toString()) }
    var method by remember { mutableStateOf(expense.method) }
    var reference by remember { mutableStateOf(expense.paymentReference) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay ${expense.expenseCode}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Vendor: ${expense.vendorName}\nBalance Due: ${FinanceCalculator.formatInr(expense.balanceDue)}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Payment Amount Now (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.labelEn) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Transaction / Voucher Ref") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    when {
                        amt == null || amt <= 0.0 -> error = "Enter a positive payment amount."
                        amt > expense.balanceDue + 0.01 -> error = "Cannot exceed balance due."
                        else -> onConfirm(amt, method, reference.trim())
                    }
                }
            ) { Text("Record Payment") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
