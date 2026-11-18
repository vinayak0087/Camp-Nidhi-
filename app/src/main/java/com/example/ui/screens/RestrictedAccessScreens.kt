package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessRequest
import com.example.data.model.AccessRequestStatus
import com.example.data.model.AdminGovernanceConfig
import com.example.data.model.Committee
import com.example.data.model.InformationShare
import com.example.data.model.ShareableSection
import com.example.data.model.TemporaryAccessGrant
import com.example.domain.CommitteeSummary
import com.example.domain.DailyExpensePoint
import com.example.domain.FinanceCalculator
import com.example.ui.components.CommitteeUtilizationChart
import com.example.ui.components.DailyExpenseTrendChart
import com.example.ui.components.IncomeVsExpenseChart
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusPendingContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoAdminPendingScreen(
    email: String,
    lastGrant: TemporaryAccessGrant?,
    onSignOut: () -> Unit
) {
    val statusText = when {
        lastGrant == null -> "Access Pending Approval"
        lastGrant.revoked || lastGrant.status == "REVOKED" -> "Temporary Access Revoked (${lastGrant.revokedReason})"
        System.currentTimeMillis() >= lastGrant.expiresAtMillis -> "Temporary Access Expired"
        else -> "Access Pending Primary Admin Approval"
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Co-Admin Access Control") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = StatusPendingAmber,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text("Co-Admin Account Restricted", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            StatusPill(text = statusText, containerColor = StatusPendingContainer, contentColor = StatusPendingAmber)
            Spacer(Modifier.height(12.dp))
            Text(
                "Signed in as $email.\n\nThe Primary Admin (${AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL}) has been notified of your login attempt. You will receive access automatically once a time-bounded role is approved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("coadmin_pending_signout")
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sign Out")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerAccessRequestScreen(
    userEmail: String,
    userName: String,
    myRequests: List<AccessRequest>,
    onSubmitRequest: (String, String, List<String>) -> Unit,
    onSignOut: () -> Unit
) {
    var campName by remember { mutableStateOf("Rural Camp 2026") }
    var reason by remember { mutableStateOf("") }
    var requestedSections by remember {
        mutableStateOf(setOf(ShareableSection.CAMP_OVERVIEW.key, ShareableSection.TOTAL_INCOME.key))
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("CampNidhi • Controlled Access") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusPendingContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = StatusPendingAmber)
                            Spacer(Modifier.width(8.dp))
                            Text("Zero Access by Default", style = MaterialTheme.typography.titleMedium, color = StatusPendingAmber)
                        }
                        Text(
                            "Signed in as $userName ($userEmail). No financial or camp information is visible until the Primary Admin (${AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL}) explicitly grants your requested sections.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (myRequests.isNotEmpty()) {
                item { Text("Your Request Status", style = MaterialTheme.typography.titleSmall) }
                items(myRequests, key = { it.id }) { req ->
                    val (bg, fg) = when (req.requestStatus) {
                        AccessRequestStatus.APPROVED -> ForestGreenContainer to ForestGreenSecondary
                        AccessRequestStatus.REJECTED, AccessRequestStatus.REVOKED -> StatusDangerContainer to StatusDangerCrimson
                        else -> StatusPendingContainer to StatusPendingAmber
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(req.requestedCampName, style = MaterialTheme.typography.titleSmall)
                                StatusPill(text = req.requestStatus.label, containerColor = bg, contentColor = fg)
                            }
                            Text("Reason: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                            if (req.reviewNotes.isNotBlank()) {
                                Text("Admin Note: ${req.reviewNotes}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Request Information Access", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = campName,
                            onValueChange = { campName = it },
                            label = { Text("Camp or Programme Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Purpose of Access Request") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Information Requested", style = MaterialTheme.typography.labelMedium)
                        ShareableSection.entries.forEach { sec ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = sec.key in requestedSections,
                                    onCheckedChange = { checked ->
                                        requestedSections = if (checked) requestedSections + sec.key else requestedSections - sec.key
                                    }
                                )
                                Text(sec.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            enabled = requestedSections.isNotEmpty(),
                            onClick = {
                                onSubmitRequest(campName.trim(), reason.trim(), requestedSections.toList())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("viewer_submit_request_btn")
                        ) {
                            Text("Submit Access Request")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InvitedViewerScreen(
    share: InformationShare,
    acceptedShareId: String,
    onAcceptPopup: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val allowed = remember(share.allowedSections) { share.allowedSections.toSet() }
    val expiryFormatted = remember(share.expiresAtMillis) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(share.expiresAtMillis))
    }

    if (acceptedShareId != share.id) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreenSecondary) },
            title = { Text("ACCESS GRANTED") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Camp: ${share.campName}", style = MaterialTheme.typography.titleSmall)
                    Text("Permitted Sections:", style = MaterialTheme.typography.labelMedium)
                    share.allowedSections.forEach { key ->
                        Text("• ${ShareableSection.labelForKey(key)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Access Expires: $expiryFormatted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAcceptPopup(share.id) },
                    modifier = Modifier.testTag("viewer_access_granted_continue")
                ) {
                    Text("Continue")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shared Report • ${share.campName}", style = MaterialTheme.typography.titleMedium)
                        Text("Expires $expiryFormatted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ShareableSection.CAMP_OVERVIEW.key in allowed) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Camp Overview", style = MaterialTheme.typography.titleSmall)
                            Text("${share.campName} • ${share.campLocation}", style = MaterialTheme.typography.bodyMedium)
                            Text("Institution: ${share.campInstitution} • Participants: ${share.participantCount ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ShareableSection.TOTAL_BUDGET.key in allowed && share.sharedTotalBudget != null) {
                        KpiMetricCard("Total Budget", FinanceCalculator.formatInr(share.sharedTotalBudget), "Approved budget", Icons.Default.AccountBalanceWallet, NavyPrimary, Modifier.weight(1f))
                    }
                    if (ShareableSection.TOTAL_INCOME.key in allowed && share.sharedTotalIncome != null) {
                        KpiMetricCard("Total Income", FinanceCalculator.formatInr(share.sharedTotalIncome), "Shared figure", Icons.Default.Savings, StatusPaidGreen, Modifier.weight(1f))
                    }
                    if (ShareableSection.TOTAL_EXPENSES.key in allowed && share.sharedTotalExpenses != null) {
                        KpiMetricCard("Total Expenses", FinanceCalculator.formatInr(share.sharedTotalExpenses), "Shared figure", Icons.Default.TrendingDown, StatusInfoBlue, Modifier.weight(1f))
                    }
                    if (ShareableSection.REMAINING_BALANCE.key in allowed && share.sharedRemainingBalance != null) {
                        KpiMetricCard("Remaining Balance", FinanceCalculator.formatInr(share.sharedRemainingBalance), "Available cash", Icons.Default.AccountBalanceWallet, ForestGreenSecondary, Modifier.weight(1f))
                    }
                }
            }

            if (ShareableSection.INCOME_EXPENSE_CHARTS.key in allowed) {
                item {
                    IncomeVsExpenseChart(
                        title = "Income vs Expenditure",
                        fundsReceived = share.sharedTotalIncome ?: 0.0,
                        promisedFunds = share.sharedTotalBudget ?: 0.0,
                        expensesCommitted = share.sharedTotalExpenses ?: 0.0,
                        expensesPaid = share.sharedTotalExpenses ?: 0.0
                    )
                }
            }

            if ((ShareableSection.COMMITTEE_BUDGET.key in allowed || ShareableSection.COMMITTEE_EXPENSES.key in allowed) &&
                share.sharedCommitteeRows.isNotEmpty()
            ) {
                val summaries = share.sharedCommitteeRows.map { row ->
                    val name = row["committeeName"]?.toString().orEmpty()
                    val alloc = (row["allocatedBudget"] as? Number)?.toDouble() ?: 0.0
                    val spent = (row["amountSpent"] as? Number)?.toDouble() ?: 0.0
                    CommitteeSummary(
                        committee = Committee(name = name, allocatedBudget = alloc),
                        allocatedBudget = alloc,
                        amountSpent = spent,
                        paidAmount = spent,
                        pendingExpenses = 0.0,
                        remainingBudget = alloc - spent,
                        utilizationPct = if (alloc > 0) (spent / alloc) * 100.0 else 0.0,
                        isOverspent = spent > alloc,
                        expenseCount = 0
                    )
                }
                item {
                    CommitteeUtilizationChart("Committee-wise Summary", summaries)
                }
            }

            if (ShareableSection.DAILY_EXPENSE_REPORT.key in allowed && share.sharedDailyExpenses.isNotEmpty()) {
                val points = share.sharedDailyExpenses.mapIndexed { idx, row ->
                    DailyExpensePoint(
                        dayLabel = row["dayLabel"]?.toString().orEmpty(),
                        dateMillis = (row["dateMillis"] as? Number)?.toLong() ?: (idx.toLong() * 86_400_000L),
                        amount = (row["amount"] as? Number)?.toDouble() ?: 0.0
                    )
                }
                item {
                    DailyExpenseTrendChart("Daily Expense Report", points)
                }
            }

            if (ShareableSection.VERIFIED_BILL_SUMMARY.key in allowed) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = ForestGreenSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text("Verified Bill Summary", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "${share.sharedVerifiedBillsCount ?: 0} verified bills • Total Verified Amount: ${FinanceCalculator.formatInr(share.sharedVerifiedBillsAmount ?: 0.0)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (ShareableSection.CASEWORK_SUMMARY.key in allowed && share.sharedCaseworkSummaryText.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Casework Financial Summary (Anonymised)", style = MaterialTheme.typography.titleSmall)
                            Text(share.sharedCaseworkSummaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (ShareableSection.FINAL_FINANCIAL_REPORT.key in allowed && share.sharedFinalReportText.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Final Financial Report", style = MaterialTheme.typography.titleSmall)
                            Text(share.sharedFinalReportText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
