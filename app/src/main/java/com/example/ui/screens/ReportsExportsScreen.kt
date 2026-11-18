package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Camp
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.CampWorkspaceData
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsExportsScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    onUpdateCampStatementFields: (Camp, UserRole, String) -> Unit,
    onMessage: (String) -> Unit
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Select a camp to generate financial statements.")
        return
    }
    val context = LocalContext.current
    val role = workspace.effectiveRole
    val summary = workspace.dashboard
    val ci = workspace.costIntelligence
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    var auditorRemarks by remember(camp.id, camp.auditorRemarks) { mutableStateOf(camp.auditorRemarks) }
    var treasurerDeclaration by remember(camp.id, camp.treasurerDeclaration) { mutableStateOf(camp.treasurerDeclaration) }

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Export Official Statements", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Report Date: ${dateFmt.format(Date())} • Camp: ${camp.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val pdf = ReportExporter.generateFinancialStatementPdf(
                                        context, camp, summary, ci, workspace.incomes, workspace.expenses
                                    )
                                    ReportExporter.shareFile(context, pdf, "application/pdf", "CampNidhi Financial Statement")
                                } catch (e: Exception) {
                                    onMessage("PDF export error: ${e.localizedMessage}")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.exportPdf, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val csv = ReportExporter.generateCsvExport(
                                        context, camp, workspace.incomes, workspace.expenses
                                    )
                                    ReportExporter.shareFile(context, csv, "text/csv", "CampNidhi Ledger CSV")
                                } catch (e: Exception) {
                                    onMessage("CSV export error: ${e.localizedMessage}")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.exportCsv, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Complete 13-Section Live Preview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row {
                        Icon(Icons.Default.Description, contentDescription = null, tint = ForestGreenSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Final Financial Statement Preview", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()

                    StatementRow("1. Camp Name", camp.name)
                    StatementRow("   Institution / Location", "${camp.institution.ifBlank { "-" }} • ${camp.location.ifBlank { "-" }}")
                    StatementRow("   Dates & Participants", "${dateFmt.format(Date(camp.startDateMillis))} – ${dateFmt.format(Date(camp.endDateMillis))} (${camp.participantCount})")
                    StatementRow("2. Total Approved Budget", FinanceCalculator.formatInr(summary.totalBudget), bold = true)
                    StatementRow("3. Total Funds Received", FinanceCalculator.formatInr(summary.totalFundsReceived), bold = true)
                    StatementRow("4. Participant Contributions", FinanceCalculator.formatInr(summary.totalParticipantContributions))
                    StatementRow("5. Total Expenditure (Committed)", FinanceCalculator.formatInr(summary.totalExpensesCommitted), bold = true)
                    StatementRow("   Total Paid Out", FinanceCalculator.formatInr(summary.totalExpensesPaid))
                    StatementRow("7. Outstanding Payments", FinanceCalculator.formatInr(summary.outstandingPayments))
                    StatementRow("8. Remaining Cash Balance", FinanceCalculator.formatInr(summary.remainingCashBalance), bold = true)
                    StatementRow("9. Cost Per Participant", FinanceCalculator.formatInr(ci.totalCostPerParticipant))
                    StatementRow("10. Receipt Verification", "${summary.verifiedExpensesCount} verified / ${summary.pendingVerificationCount} pending")
                    StatementRow("11. Ledger Volume", "${workspace.incomes.size} incomes • ${workspace.expenses.size} expenses")

                    HorizontalDivider()
                    Text("6. Committee-Wise Expenditure", style = MaterialTheme.typography.titleSmall)
                    summary.committeeSummaries.forEach { cs ->
                        StatementRow(
                            cs.committee.name,
                            "${FinanceCalculator.formatCompactInr(cs.amountSpent)} / ${FinanceCalculator.formatCompactInr(cs.allocatedBudget)}"
                        )
                    }

                    HorizontalDivider()
                    Text("12. Auditor Remarks", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = auditorRemarks,
                        onValueChange = { auditorRemarks = it },
                        enabled = role == UserRole.AUDITOR || role == UserRole.SYSTEM_ADMIN,
                        placeholder = { Text("Enter independent auditor observations...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("13. Treasurer Declaration", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = treasurerDeclaration,
                        onValueChange = { treasurerDeclaration = it },
                        enabled = role == UserRole.TREASURER || role == UserRole.SYSTEM_ADMIN,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (role != UserRole.COMMITTEE_LEADER) {
                        Button(
                            onClick = {
                                onUpdateCampStatementFields(
                                    camp.copy(
                                        auditorRemarks = auditorRemarks.trim(),
                                        treasurerDeclaration = treasurerDeclaration.trim()
                                    ),
                                    role,
                                    "Updated statement remarks & declaration"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Remarks & Declaration")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MonoFontFamily,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}
