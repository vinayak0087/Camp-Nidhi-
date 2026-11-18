package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CaseworkFinance
import com.example.data.model.Committee
import com.example.data.model.FacultyAccessMode
import com.example.data.model.FacultyProfile
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
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.WarmGoldBright

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FacultyDashboardAndPresentationScreen(
    profile: FacultyProfile,
    casework: CaseworkFinance?,
    onLaunchPresentation: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val presentationOnly = profile.accessMode == FacultyAccessMode.PRESENTATION_ONLY.name
    var inPresentationMode by remember(profile.id, presentationOnly) { mutableStateOf(presentationOnly) }
    var isFullScreen by remember { mutableStateOf(false) }
    var selectedDayFilter by remember { mutableStateOf("ALL") }

    val dayKeys = remember(casework) {
        casework?.dailyActivity?.keys?.toList().orEmpty()
    }
    val filteredDailyPoints = remember(casework, selectedDayFilter) {
        val entries = casework?.dailyActivity?.entries.orEmpty()
        entries.filter { selectedDayFilter == "ALL" || it.key == selectedDayFilter }
            .mapIndexed { idx, entry ->
                DailyExpensePoint(
                    dayLabel = entry.key,
                    dateMillis = idx.toLong() * 86_400_000L,
                    amount = entry.value
                )
            }
    }
    val committeeSummaries = remember(casework) {
        val alloc = casework?.allocatedBudget ?: 0.0
        val entries = casework?.committeeBreakdown?.entries.orEmpty()
        val perCommAlloc = if (entries.isNotEmpty()) alloc / entries.size else alloc
        entries.map { (name, spent) ->
            CommitteeSummary(
                committee = Committee(name = name, allocatedBudget = perCommAlloc),
                allocatedBudget = perCommAlloc,
                amountSpent = spent,
                paidAmount = spent,
                pendingExpenses = 0.0,
                remainingBudget = perCommAlloc - spent,
                utilizationPct = if (perCommAlloc > 0) (spent / perCommAlloc) * 100.0 else 0.0,
                isOverspent = spent > perCommAlloc,
                expenseCount = 0
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (inPresentationMode) "Presentation Mode • ${profile.profileSlotName}"
                                else "Faculty Financial Dashboard",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "${profile.campName} • ${casework?.caseworkCode ?: profile.approvedCaseworkName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        if (!presentationOnly) {
                            if (inPresentationMode) {
                                TextButton(onClick = { inPresentationMode = false; isFullScreen = false }) {
                                    Text("Return to Dashboard")
                                }
                            } else {
                                TextButton(onClick = {
                                    inPresentationMode = true
                                    onLaunchPresentation(false)
                                }) {
                                    Text("Open Presentation")
                                }
                            }
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isFullScreen) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = "FULL SCREEN PRESENTATION • ${casework?.caseworkCode.orEmpty()}",
                            containerColor = ForestGreenContainer,
                            contentColor = ForestGreenSecondary
                        )
                        OutlinedButton(onClick = { isFullScreen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Exit Full Screen")
                        }
                    }
                }
            }

            // Header Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                casework?.programmeName ?: profile.approvedCaseworkName.ifBlank { "Assigned Casework" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            "Anonymised Casework Code: ${casework?.caseworkCode ?: "-"} • Zero personal beneficiary PII • Read-only verified view",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!inPresentationMode && !presentationOnly) {
                                OutlinedButton(
                                    onClick = {
                                        inPresentationMode = true
                                        onLaunchPresentation(false)
                                    },
                                    modifier = Modifier.testTag("faculty_open_presentation_btn")
                                ) {
                                    Icon(Icons.Default.PresentToAll, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open Presentation", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            if (inPresentationMode && !isFullScreen) {
                                OutlinedButton(
                                    onClick = {
                                        isFullScreen = true
                                        onLaunchPresentation(true)
                                    },
                                    modifier = Modifier.testTag("faculty_fullscreen_btn")
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Full Screen", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }

            if (casework == null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Casework summary is being synced or not yet published by Primary Admin.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                // 4 Financial KPIs
                item {
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMetricCard(
                            title = "Allocated Budget",
                            value = FinanceCalculator.formatInr(casework.allocatedBudget),
                            subtitle = casework.caseworkCode,
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = NavyPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Total Income",
                            value = FinanceCalculator.formatInr(casework.totalIncome),
                            subtitle = "Approved funding",
                            icon = Icons.Default.Savings,
                            accentColor = StatusPaidGreen,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Total Expenses",
                            value = FinanceCalculator.formatInr(casework.totalExpenses),
                            subtitle = "${casework.utilizationPct.toInt()}% utilised",
                            icon = Icons.Default.TrendingDown,
                            accentColor = StatusInfoBlue,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Remaining Balance",
                            value = FinanceCalculator.formatInr(casework.remainingBalance),
                            subtitle = "Available balance",
                            icon = Icons.Default.PieChart,
                            accentColor = ForestGreenSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Date Filter Chips for Activity & Charts
                if (dayKeys.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedDayFilter == "ALL",
                                onClick = { selectedDayFilter = "ALL" },
                                label = { Text("All Days") }
                            )
                            dayKeys.forEach { day ->
                                FilterChip(
                                    selected = selectedDayFilter == day,
                                    onClick = { selectedDayFilter = day },
                                    label = { Text(day) }
                                )
                            }
                        }
                    }
                }

                // Chart 1: Income vs Expense Bar Graph
                item {
                    IncomeVsExpenseChart(
                        title = "1. Income vs Expense Bar Graph",
                        fundsReceived = casework.totalIncome,
                        promisedFunds = casework.allocatedBudget,
                        expensesCommitted = casework.totalExpenses,
                        expensesPaid = casework.totalExpenses
                    )
                }

                // Chart 2: Committee-wise Expenditure Chart
                item {
                    CommitteeUtilizationChart(
                        title = "2. Committee-wise Expenditure Chart",
                        summaries = committeeSummaries
                    )
                }

                // Chart 3: Casework Budget Utilisation Chart
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("3. Casework Budget Utilisation", style = MaterialTheme.typography.titleMedium)
                            val pct = (casework.utilizationPct / 100.0).toFloat().coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { pct },
                                color = if (pct > 0.9f) StatusPendingAmber else ForestGreenSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                            )
                            Text(
                                "${casework.utilizationPct.toInt()}% of ${FinanceCalculator.formatInr(casework.allocatedBudget)} utilised (${casework.caseworkCode})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Chart 4: Category-wise Expense Pie Chart
                item {
                    CategoryPieChartCard(
                        title = "4. Category-wise Expense Pie Chart",
                        breakdown = casework.expenseCategoryBreakdown
                    )
                }

                // Chart 5: Daily Expense Trend
                item {
                    DailyExpenseTrendChart(
                        title = "5. Daily Expense Trend",
                        points = filteredDailyPoints
                    )
                }

                // Chart 6: Remaining Budget Chart
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("6. Remaining Budget Overview", style = MaterialTheme.typography.titleMedium)
                            val remPct = if (casework.allocatedBudget > 0) {
                                (casework.remainingBalance / casework.allocatedBudget).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { remPct },
                                color = NavyPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                            )
                            Text(
                                "Remaining: ${FinanceCalculator.formatInr(casework.remainingBalance)} (${(remPct * 100).toInt()}% left)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (casework.anonymisedSummaryNote.isNotBlank()) {
                                Text(
                                    casework.anonymisedSummaryNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun CategoryPieChartCard(
    title: String,
    breakdown: Map<String, Double>
) {
    val palette = listOf(NavyPrimary, ForestGreenSecondary, WarmGoldBright, StatusInfoBlue, StatusPendingAmber)
    val total = breakdown.values.sum().coerceAtLeast(1.0)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (breakdown.isEmpty()) {
                Text("No category breakdown published.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        var startAngle = -90f
                        breakdown.entries.forEachIndexed { idx, entry ->
                            val sweep = ((entry.value / total) * 360.0).toFloat()
                            drawArc(
                                color = palette[idx % palette.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = true,
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        breakdown.entries.forEachIndexed { idx, entry ->
                            val pct = ((entry.value / total) * 100.0).toInt()
                            Text(
                                "${entry.key}: ${FinanceCalculator.formatCompactInr(entry.value)} ($pct%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette[idx % palette.size]
                            )
                        }
                    }
                }
            }
        }
    }
}
