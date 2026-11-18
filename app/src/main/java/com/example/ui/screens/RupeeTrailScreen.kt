package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusNeutralSlate
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.viewmodel.CampWorkspaceData
import com.example.util.ReceiptUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RupeeTrailScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Create or select a camp to inspect the Rupee Trail.")
        return
    }

    var searchTx by remember { mutableStateOf("") }
    var selectedExpenseId by remember(workspace.expenses) {
        mutableStateOf(workspace.expenses.firstOrNull()?.id ?: "")
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val matchingExpenses = remember(workspace.expenses, searchTx) {
        val q = searchTx.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) workspace.expenses
        else workspace.expenses.filter {
            it.expenseCode.lowercase(Locale.ROOT).contains(q) ||
                it.vendorName.lowercase(Locale.ROOT).contains(q) ||
                it.description.lowercase(Locale.ROOT).contains(q) ||
                it.sourceIncomeCode.lowercase(Locale.ROOT).contains(q)
        }
    }

    val selectedExpense: ExpenseRecord? = remember(matchingExpenses, selectedExpenseId) {
        matchingExpenses.firstOrNull { it.id == selectedExpenseId } ?: matchingExpenses.firstOrNull()
    }

    val linkedIncome = remember(workspace.incomes, selectedExpense) {
        val code = selectedExpense?.sourceIncomeCode.orEmpty()
        workspace.incomes.firstOrNull { it.transactionCode == code }
            ?: workspace.incomes.firstOrNull()
    }

    val linkedCommittee = remember(workspace.dashboard.committeeSummaries, selectedExpense) {
        workspace.dashboard.committeeSummaries.firstOrNull { it.committee.id == selectedExpense?.committeeId }
    }

    val timeFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(strings.navRupeeTrail, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "End-to-end accountability chain from Source of Funds to Final Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchTx,
                        onValueChange = { searchTx = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Trace by Expense ID, Vendor, or Source Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (matchingExpenses.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedExpense?.let { "${it.expenseCode} • ${it.vendorName} (${FinanceCalculator.formatInr(it.amount)})" }
                                    ?: "Select transaction",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Traceable Expense") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                matchingExpenses.forEach { exp ->
                                    DropdownMenuItem(
                                        text = { Text("${exp.expenseCode} • ${exp.vendorName} (${FinanceCalculator.formatInr(exp.amount)})") },
                                        onClick = {
                                            selectedExpenseId = exp.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedExpense == null) {
            item {
                EmptyStateCard(
                    title = "No Expenses to Trace",
                    message = "Record an expense to inspect its complete 8-stage Rupee Trail.",
                    icon = Icons.Default.Route
                )
            }
        } else {
            item {
                val exp = selectedExpense
                val isApproved = exp.status in listOf(
                    ExpensePaymentStatus.APPROVED,
                    ExpensePaymentStatus.PARTIALLY_PAID,
                    ExpensePaymentStatus.PAID
                )
                val isPaid = exp.status == ExpensePaymentStatus.PAID || exp.status == ExpensePaymentStatus.PARTIALLY_PAID

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrailStepCard(
                        stepNo = 1,
                        title = "SOURCE OF FUNDS",
                        detail = if (linkedIncome != null) {
                            "${linkedIncome.transactionCode} • ${linkedIncome.source} (${linkedIncome.category})\nReceived: ${FinanceCalculator.formatInr(linkedIncome.receivedAmount)} via ${linkedIncome.method.labelEn}"
                        } else {
                            "General Camp Pool • Total Received: ${FinanceCalculator.formatInr(workspace.dashboard.totalFundsReceived)}"
                        },
                        icon = Icons.Default.Savings,
                        done = linkedIncome != null || workspace.dashboard.totalFundsReceived > 0.0
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 2,
                        title = "CAMP TOTAL BUDGET",
                        detail = "${camp.name}\nApproved Camp Budget: ${FinanceCalculator.formatInr(camp.approvedBudget)} (${camp.participantCount} participants)",
                        icon = Icons.Default.AccountBalance,
                        done = camp.approvedBudget > 0.0
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 3,
                        title = "COMMITTEE ALLOCATION",
                        detail = if (linkedCommittee != null) {
                            "${linkedCommittee.committee.name} (Leader: ${linkedCommittee.committee.leaderName.ifBlank { "Unassigned" }})\nAllocated: ${FinanceCalculator.formatInr(linkedCommittee.allocatedBudget)} • Remaining: ${FinanceCalculator.formatInr(linkedCommittee.remainingBudget)}"
                        } else {
                            exp.committeeName
                        },
                        icon = Icons.Default.Groups,
                        done = true
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 4,
                        title = "EXPENSE REQUEST",
                        detail = "${exp.expenseCode} • ${exp.description}\nVendor: ${exp.vendorName} • Amount: ${FinanceCalculator.formatInr(exp.amount)} • Submitted by ${exp.submittedByName}",
                        icon = Icons.Default.RequestQuote,
                        done = true
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 5,
                        title = "APPROVAL",
                        detail = when {
                            exp.status == ExpensePaymentStatus.REJECTED ->
                                "Rejected by ${exp.approvedByName.ifBlank { "Reviewer" }} (${exp.remarks})"
                            isApproved ->
                                "Approved by ${exp.approvedByName.ifBlank { "Authorized Approver" }}"
                            else -> "Awaiting approval (${exp.status.labelEn})"
                        },
                        icon = Icons.Default.Approval,
                        done = isApproved,
                        isRejected = exp.status == ExpensePaymentStatus.REJECTED
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 6,
                        title = "PAYMENT",
                        detail = if (isPaid) {
                            "${exp.status.labelEn}: ${FinanceCalculator.formatInr(exp.paidAmount)} / ${FinanceCalculator.formatInr(exp.amount)} via ${exp.method.labelEn}\nRef: ${exp.paymentReference.ifBlank { "Voucher recorded" }} • Paid by ${exp.paidByName.ifBlank { "Treasurer" }}"
                        } else {
                            "Pending disbursement (Due: ${FinanceCalculator.formatInr(exp.balanceDue)})"
                        },
                        icon = Icons.Default.Payments,
                        done = exp.status == ExpensePaymentStatus.PAID
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 7,
                        title = "BILL / RECEIPT EVIDENCE",
                        detail = when {
                            exp.receiptVerified -> "Verified by ${exp.receiptVerifiedBy.ifBlank { "Auditor" }}"
                            exp.hasReceiptEvidence -> "Photo uploaded • Pending Auditor verification"
                            else -> "Missing bill/receipt upload"
                        },
                        icon = Icons.Default.Receipt,
                        done = exp.receiptVerified,
                        extraContent = if (exp.receiptThumbnailBase64.isNotBlank()) {
                            {
                                val bmp = remember(exp.receiptThumbnailBase64) {
                                    ReceiptUtils.decodeBase64Bitmap(exp.receiptThumbnailBase64)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Receipt thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        } else null
                    )
                    TrailConnector()
                    TrailStepCard(
                        stepNo = 8,
                        title = "FINAL ACCOUNT RECONCILIATION",
                        detail = "Camp Cash Balance: ${FinanceCalculator.formatInr(workspace.dashboard.remainingCashBalance)}\nAccountability Status: ${if (exp.status == ExpensePaymentStatus.PAID && exp.receiptVerified) "COMPLETE & VERIFIED" else "IN PROGRESS"}",
                        icon = Icons.Default.AssignmentTurnedIn,
                        done = exp.status == ExpensePaymentStatus.PAID && exp.receiptVerified
                    )
                }
            }
        }

        // Financial Activity Timeline (Immutable Audit Logs)
        item {
            Spacer(Modifier.height(6.dp))
            SectionHeader(
                title = "Financial Activity Timeline (${workspace.auditLogs.size})",
                subtitle = "Immutable audit trail of all camp financial events"
            )
        }
        items(workspace.auditLogs.take(30), key = { it.id }) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${log.action} ${if (log.entityCode.isNotBlank()) "• ${log.entityCode}" else ""}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                log.createdAt?.toDate()?.let { timeFmt.format(it) } ?: "Pending sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(log.summary, style = MaterialTheme.typography.bodySmall)
                        if (log.previousValues.isNotBlank() && log.newValues.isNotBlank()) {
                            Text(
                                "${log.previousValues} → ${log.newValues}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Actor: ${log.actorName} (${log.actorRole})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailConnector() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TrailStepCard(
    stepNo: Int,
    title: String,
    detail: String,
    icon: ImageVector,
    done: Boolean,
    isRejected: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null
) {
    val badgeColor = when {
        isRejected -> StatusDangerCrimson
        done -> ForestGreenSecondary
        else -> StatusPendingAmber
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$stepNo. $title",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = badgeColor
                    )
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
                if (done) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = ForestGreenSecondary, modifier = Modifier.size(18.dp))
                }
            }
            extraContent?.invoke()
        }
    }
}
