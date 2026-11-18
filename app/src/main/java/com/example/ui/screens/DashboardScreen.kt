package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.CommitteeUtilizationChart
import com.example.ui.components.DailyExpenseTrendChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExpenseStatusPill
import com.example.ui.components.IncomeVsExpenseChart
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.WarmGoldBright
import com.example.ui.theme.WarmGoldTertiary
import com.example.ui.viewmodel.CampWorkspaceData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    onNavigateRoute: (String) -> Unit,
    onQuickCreateCamp: () -> Unit,
    onLoadDemoCamp: () -> Unit
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EmptyStateCard(
                    title = "Welcome to CampNidhi",
                    message = "Create your first rural camp workspace to manage committee budgets, student contributions, receipt-backed expenses, and final audit statements — or load a clearly labeled sample demo camp to explore.",
                    icon = Icons.Default.AccountBalance,
                    actionLabel = "Create New Camp",
                    onActionClick = onQuickCreateCamp
                )
            }
            item {
                OutlinedButton(
                    onClick = onLoadDemoCamp,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(strings.loadDemoCamp)
                }
            }
        }
        return
    }

    val summary = workspace.dashboard
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Camp Hero Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NavyPrimary, Color(0xFF174363), ForestGreenSecondary)
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = camp.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "${camp.location.ifBlank { "Rural Camp" }} • ${camp.participantCount} Participants",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFDCE8F2)
                            )
                        }
                        if (camp.isDemoData) {
                            StatusPill(
                                text = strings.demoBadge,
                                containerColor = WarmGoldBright,
                                contentColor = Color(0xFF2B1E00)
                            )
                        }
                    }

                    val remPct = summary.budgetRemainingPct.coerceIn(0.0, 100.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.budgetRemainingPct}: ${remPct.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFF9ECC7)
                        )
                        Text(
                            text = "${FinanceCalculator.formatInr(summary.remainingBudget)} left",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = Color.White
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ((100.0 - remPct) / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = WarmGoldBright,
                        trackColor = Color.White.copy(alpha = 0.24f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateRoute("expense_register") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Expense", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onNavigateRoute("income_register") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ Income", color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Overspending Alerts Banner
        if (summary.overspentCommittees.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusDangerContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = strings.overspendingAlerts,
                            tint = StatusDangerCrimson
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.overspendingAlerts} (${summary.overspentCommittees.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = StatusDangerCrimson
                            )
                            Text(
                                text = summary.overspentCommittees.joinToString(", ") {
                                    "${it.committee.name} (+${FinanceCalculator.formatCompactInr(it.amountSpent - it.allocatedBudget)})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusDangerCrimson
                            )
                        }
                    }
                }
            }
        }

        // 8 Live Financial KPI Cards (2-column grid rows)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        title = strings.totalBudget,
                        value = FinanceCalculator.formatInr(summary.totalBudget),
                        subtitle = "Allocated: ${FinanceCalculator.formatCompactInr(summary.totalAllocatedToCommittees)}",
                        icon = Icons.Default.AccountBalance,
                        accentColor = NavyPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("committee_budgets") }
                    )
                    KpiMetricCard(
                        title = strings.totalFundsReceived,
                        value = FinanceCalculator.formatInr(summary.totalFundsReceived),
                        subtitle = "Promised: ${FinanceCalculator.formatCompactInr(summary.totalPromisedFunds)}",
                        icon = Icons.Default.Savings,
                        accentColor = ForestGreenSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("income_register") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        title = strings.totalExpenses,
                        value = FinanceCalculator.formatInr(summary.totalExpensesCommitted),
                        subtitle = "Paid: ${FinanceCalculator.formatCompactInr(summary.totalExpensesPaid)}",
                        icon = Icons.Default.ReceiptLong,
                        accentColor = StatusDangerCrimson,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("expense_register") }
                    )
                    KpiMetricCard(
                        title = strings.remainingBalance,
                        value = FinanceCalculator.formatInr(summary.remainingCashBalance),
                        subtitle = "Cash on hand (Rec - Paid)",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = if (summary.remainingCashBalance >= 0) ForestGreenSecondary else StatusDangerCrimson,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        title = strings.pendingApprovals,
                        value = "${summary.pendingApprovalCount} items",
                        subtitle = FinanceCalculator.formatInr(summary.pendingApprovalAmount),
                        icon = Icons.Default.Approval,
                        accentColor = StatusPendingAmber,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("approval_centre") }
                    )
                    KpiMetricCard(
                        title = strings.outstandingPayments,
                        value = FinanceCalculator.formatInr(summary.outstandingPayments),
                        subtitle = "Unpaid approved bills",
                        icon = Icons.Default.PendingActions,
                        accentColor = StatusInfoBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("approval_centre") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        title = strings.participantContributions,
                        value = FinanceCalculator.formatInr(summary.totalParticipantContributions),
                        subtitle = "Due: ${FinanceCalculator.formatCompactInr(summary.outstandingContributions)}",
                        icon = Icons.Default.Groups,
                        accentColor = WarmGoldTertiary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("fund_contributions") }
                    )
                    KpiMetricCard(
                        title = strings.costPerParticipant,
                        value = FinanceCalculator.formatInr(summary.costPerParticipant),
                        subtitle = "${camp.participantCount} participants",
                        icon = Icons.Default.Payments,
                        accentColor = NavyPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("cost_intelligence") }
                    )
                }
            }
        }

        // Charts
        item {
            IncomeVsExpenseChart(
                title = strings.incomeVsExpense,
                fundsReceived = summary.totalFundsReceived,
                promisedFunds = summary.totalPromisedFunds,
                expensesCommitted = summary.totalExpensesCommitted,
                expensesPaid = summary.totalExpensesPaid
            )
        }

        item {
            CommitteeUtilizationChart(
                title = strings.committeeUtilisation,
                summaries = summary.committeeSummaries
            )
        }

        item {
            DailyExpenseTrendChart(
                title = strings.dailyExpenseTrend,
                points = summary.dailyExpenseTrend
            )
        }

        // Upcoming / Due Payments
        item {
            SectionHeader(
                title = strings.upcomingPayments,
                actionLabel = "View All",
                onActionClick = { onNavigateRoute("approval_centre") }
            )
        }
        if (summary.upcomingPayments.isEmpty()) {
            item {
                Text(
                    "All approved expenses are settled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(summary.upcomingPayments, key = { "up_${it.id}" }) { exp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${exp.expenseCode} • ${exp.vendorName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${exp.committeeName} • Due ${FinanceCalculator.formatInr(exp.balanceDue)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExpenseStatusPill(exp.status)
                    }
                }
            }
        }

        // Recent Transactions
        item {
            SectionHeader(
                title = strings.recentTransactions,
                actionLabel = strings.navRupeeTrail,
                onActionClick = { onNavigateRoute("rupee_trail") }
            )
        }
        items(summary.recentExpenses, key = { "rec_${it.id}" }) { exp ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(exp.description, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${exp.expenseCode} • ${exp.committeeName} • ${dateFmt.format(Date(exp.dateMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            FinanceCalculator.formatInr(exp.amount),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily)
                        )
                        ExpenseStatusPill(exp.status)
                    }
                }
            }
        }
    }
}
