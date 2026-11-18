package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.WarmGoldTertiary
import com.example.ui.viewmodel.CampWorkspaceData

@Composable
fun CostIntelligenceScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Select a camp to view Cost Intelligence.")
        return
    }

    val ci = workspace.costIntelligence

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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(strings.navCostIntelligence, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Camp Horizon: Day ${ci.campElapsedDays} of ${ci.campTotalDays} • ${camp.participantCount} participants • ${ci.budgetUtilizedPct.toInt()}% budget utilised",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { (ci.budgetUtilizedPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = if (ci.budgetUtilizedPct > 100.0) StatusDangerCrimson else ForestGreenSecondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                }
            }
        }

        if (ci.overspendingAlertCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusDangerContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDangerCrimson)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${ci.overspendingAlertCount} committee(s) exceeded their allocated budget. Review variance below.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = StatusDangerCrimson
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard(
                        title = "Total Cost / Participant",
                        value = FinanceCalculator.formatInr(ci.totalCostPerParticipant),
                        subtitle = "All committed costs",
                        icon = Icons.Default.Groups,
                        accentColor = NavyPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Food Cost / Participant",
                        value = FinanceCalculator.formatInr(ci.foodCostPerParticipant),
                        subtitle = "Groceries & catering",
                        icon = Icons.Default.Restaurant,
                        accentColor = ForestGreenSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard(
                        title = "Travel / Participant",
                        value = FinanceCalculator.formatInr(ci.travelCostPerParticipant),
                        subtitle = "Bus & transport hire",
                        icon = Icons.Default.DirectionsBus,
                        accentColor = StatusInfoBlue,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Medical Expenditure",
                        value = FinanceCalculator.formatInr(ci.medicalExpenditure),
                        subtitle = "First aid & health camp",
                        icon = Icons.Default.LocalHospital,
                        accentColor = StatusDangerCrimson,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard(
                        title = "Daily Average Spend",
                        value = FinanceCalculator.formatInr(ci.dailyAverageExpenditure),
                        subtitle = "Over ${ci.campElapsedDays} active day(s)",
                        icon = Icons.Default.Analytics,
                        accentColor = WarmGoldTertiary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Projected Final Spend",
                        value = FinanceCalculator.formatInr(ci.projectedFinalExpenditure),
                        subtitle = if (ci.projectedFinalExpenditure > camp.approvedBudget) "Exceeds budget!" else "Within approved budget",
                        icon = Icons.Default.TrendingUp,
                        accentColor = if (ci.projectedFinalExpenditure > camp.approvedBudget) StatusDangerCrimson else ForestGreenSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Planned Budget vs Actual Expenditure",
                subtitle = "Committee variance & overspending warnings"
            )
        }

        items(ci.committeeSummaries, key = { "ci_${it.committee.id}" }) { cs ->
            val variance = cs.allocatedBudget - cs.amountSpent
            val isOver = variance < 0.0
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cs.committee.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (isOver) "OVER by ${FinanceCalculator.formatInr(-variance)}"
                            else "Under by ${FinanceCalculator.formatInr(variance)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = if (isOver) StatusDangerCrimson else ForestGreenSecondary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Planned: ${FinanceCalculator.formatInr(cs.allocatedBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Actual: ${FinanceCalculator.formatInr(cs.amountSpent)} (${cs.utilizationPct.toInt()}%)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isOver) StatusDangerCrimson else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (cs.utilizationPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = when {
                            isOver -> StatusDangerCrimson
                            cs.utilizationPct >= 85.0 -> StatusPendingAmber
                            else -> ForestGreenSecondary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}
