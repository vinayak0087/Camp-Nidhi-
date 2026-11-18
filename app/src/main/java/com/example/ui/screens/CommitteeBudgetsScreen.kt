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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Camp
import com.example.data.model.Committee
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.parseHexColorOrDefault
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.viewmodel.CampWorkspaceData

@Composable
fun CommitteeBudgetsScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    onSaveCommittee: (Camp, Committee, UserRole) -> Unit,
    onDeleteCommittee: (Camp, Committee, UserRole) -> Unit,
    onCreateTransfer: (Camp, Committee, Committee, Double, String, Boolean, UserRole) -> Unit
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Create or select a camp first.")
        return
    }
    val role = workspace.effectiveRole
    val canManage = role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER

    var editingCommittee by remember { mutableStateOf<Committee?>(null) }
    var showCommitteeDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Committee?>(null) }

    val summaries = workspace.dashboard.committeeSummaries

    Scaffold(
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = {
                        editingCommittee = null
                        showCommitteeDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_committee_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Committee")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Camp Approved Budget", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    FinanceCalculator.formatInr(camp.approvedBudget),
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Allocated to Committees", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    FinanceCalculator.formatInr(workspace.dashboard.totalAllocatedToCommittees),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                    color = if (workspace.dashboard.totalAllocatedToCommittees > camp.approvedBudget) {
                                        StatusDangerCrimson
                                    } else ForestGreenSecondary
                                )
                            }
                        }
                        if (canManage && workspace.committees.size >= 2) {
                            OutlinedButton(
                                onClick = { showTransferDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.transferBudget)
                            }
                        }
                    }
                }
            }

            if (summaries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Committees Yet",
                        message = "Add custom committees and assign leaders & budget allocations.",
                        icon = Icons.Default.Groups
                    )
                }
            } else {
                items(summaries, key = { it.committee.id }) { cs ->
                    val accent = parseHexColorOrDefault(cs.committee.colorHex, ForestGreenSecondary)
                    val progress = (cs.utilizationPct / 100.0).toFloat().coerceIn(0f, 1f)
                    val barColor = when {
                        cs.amountSpent > cs.allocatedBudget -> StatusDangerCrimson
                        cs.utilizationPct >= 85.0 -> StatusPendingAmber
                        else -> accent
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(cs.committee.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Leader: ${cs.committee.leaderName.ifBlank { "Unassigned" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (cs.amountSpent > cs.allocatedBudget) {
                                    StatusPill(
                                        text = "OVERSPENT",
                                        containerColor = StatusDangerContainer,
                                        contentColor = StatusDangerCrimson
                                    )
                                } else {
                                    Text(
                                        "${cs.utilizationPct.toInt()}% used",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = barColor
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                color = barColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricMini("Allocated", FinanceCalculator.formatCompactInr(cs.allocatedBudget))
                                MetricMini("Spent", FinanceCalculator.formatCompactInr(cs.amountSpent))
                                MetricMini("Pending", FinanceCalculator.formatCompactInr(cs.pendingExpenses))
                                MetricMini(
                                    "Remaining",
                                    FinanceCalculator.formatCompactInr(cs.remainingBudget),
                                    highlightColor = if (cs.remainingBudget < 0) StatusDangerCrimson else ForestGreenSecondary
                                )
                            }

                            if (canManage) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingCommittee = cs.committee
                                            showCommitteeDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.edit)
                                    }
                                    if (cs.expenseCount == 0) {
                                        IconButton(onClick = { deleteTarget = cs.committee }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = strings.delete,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCommitteeDialog) {
        CommitteeFormDialog(
            initial = editingCommittee,
            onSave = { comm ->
                onSaveCommittee(camp, comm, role)
                showCommitteeDialog = false
            },
            onDismiss = { showCommitteeDialog = false }
        )
    }

    if (showTransferDialog) {
        BudgetTransferDialog(
            committees = workspace.committees,
            canAutoApprove = role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER,
            onSubmit = { from, to, amount, reason, autoApprove ->
                onCreateTransfer(camp, from, to, amount, reason, autoApprove, role)
                showTransferDialog = false
            },
            onDismiss = { showTransferDialog = false }
        )
    }

    deleteTarget?.let { comm ->
        ConfirmDeleteDialog(
            title = strings.confirmDeleteTitle,
            message = "Delete unused committee '${comm.name}'?",
            onConfirm = {
                onDeleteCommittee(camp, comm, role)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun MetricMini(
    label: String,
    value: String,
    highlightColor: androidx.compose.ui.graphics.Color? = null
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CommitteeFormDialog(
    initial: Committee?,
    onSave: (Committee) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var leaderName by remember { mutableStateOf(initial?.leaderName ?: "") }
    var leaderEmail by remember { mutableStateOf(initial?.leaderEmail ?: "") }
    var budgetStr by remember { mutableStateOf(initial?.allocatedBudget?.toString() ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Committee" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Committee Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { budgetStr = it },
                    label = { Text("Allocated Budget (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = leaderName,
                    onValueChange = { leaderName = it },
                    label = { Text("Committee Leader Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = leaderEmail,
                    onValueChange = { leaderEmail = it },
                    label = { Text("Leader Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Scope / Notes") },
                    maxLines = 2,
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
                    val budget = budgetStr.toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "Committee name is required."
                        budget == null || budget < 0.0 -> error = "Enter a valid budget amount."
                        else -> onSave(
                            (initial ?: Committee()).copy(
                                name = name.trim(),
                                leaderName = leaderName.trim(),
                                leaderEmail = leaderEmail.trim(),
                                allocatedBudget = budget,
                                description = description.trim()
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetTransferDialog(
    committees: List<Committee>,
    canAutoApprove: Boolean,
    onSubmit: (Committee, Committee, Double, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var fromComm by remember { mutableStateOf(committees.first()) }
    var toComm by remember { mutableStateOf(committees.getOrElse(1) { committees.first() }) }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }
    var amountStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var autoApprove by remember { mutableStateOf(canAutoApprove) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Budget Between Committees") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = !fromExpanded }) {
                    OutlinedTextField(
                        value = "${fromComm.name} (${FinanceCalculator.formatCompactInr(fromComm.allocatedBudget)})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From Committee") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fromExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                        committees.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${FinanceCalculator.formatCompactInr(c.allocatedBudget)})") },
                                onClick = { fromComm = c; fromExpanded = false }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = !toExpanded }) {
                    OutlinedTextField(
                        value = "${toComm.name} (${FinanceCalculator.formatCompactInr(toComm.allocatedBudget)})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To Committee") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(toExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }) {
                        committees.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${FinanceCalculator.formatCompactInr(c.allocatedBudget)})") },
                                onClick = { toComm = c; toExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Transfer Amount (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Justification *") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (canAutoApprove) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Approve & Execute Immediately", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = autoApprove, onCheckedChange = { autoApprove = it })
                    }
                }
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
                        fromComm.id == toComm.id -> error = "Choose two different committees."
                        amt == null || amt <= 0.0 -> error = "Enter a valid transfer amount."
                        amt > fromComm.allocatedBudget -> error = "Exceeds source committee budget."
                        reason.isBlank() -> error = "Please enter a reason for audit trail."
                        else -> onSubmit(fromComm, toComm, amt, reason.trim(), autoApprove)
                    }
                }
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
