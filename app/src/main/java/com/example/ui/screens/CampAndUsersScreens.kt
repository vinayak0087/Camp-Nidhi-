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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Camp
import com.example.data.model.CampMember
import com.example.data.model.CampStatus
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusNeutralContainer
import com.example.ui.theme.StatusNeutralSlate
import com.example.ui.theme.WarmGoldBright
import com.example.ui.viewmodel.CampWorkspaceData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampManagementScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    onSelectCamp: (String) -> Unit,
    onCreateCamp: (Camp) -> Unit,
    onUpdateCamp: (Camp, UserRole) -> Unit,
    onDeleteCamp: (Camp) -> Unit,
    onCreateDemoCamp: () -> Unit,
    onResetDemoData: (List<Camp>) -> Unit
) {
    val role = workspace.effectiveRole
    var editingCamp by remember { mutableStateOf<Camp?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Camp?>(null) }
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val hasDemo = workspace.allCamps.any { it.isDemoData }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCamp = null
                    showForm = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Camp")
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreateDemoCamp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.loadDemoCamp, maxLines = 1)
                    }
                    if (hasDemo) {
                        OutlinedButton(
                            onClick = { onResetDemoData(workspace.allCamps) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.resetDemoData, maxLines = 1)
                        }
                    }
                }
            }

            if (workspace.allCamps.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Camps Created",
                        message = "Tap + to create a rural camp with isolated finances, participants, and committee budgets.",
                        icon = Icons.Default.Event
                    )
                }
            } else {
                items(workspace.allCamps, key = { it.id }) { camp ->
                    val isActive = camp.id == workspace.activeCamp?.id
                    Card(
                        onClick = { onSelectCamp(camp.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (isActive) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = ForestGreenSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(camp.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (camp.isDemoData) {
                                        StatusPill("DEMO DATA", WarmGoldBright, androidx.compose.ui.graphics.Color(0xFF2B1E00))
                                    }
                                    StatusPill(
                                        camp.campStatus.labelEn,
                                        if (camp.campStatus == CampStatus.ACTIVE) ForestGreenContainer else StatusNeutralContainer,
                                        if (camp.campStatus == CampStatus.ACTIVE) ForestGreenSecondary else StatusNeutralSlate
                                    )
                                }
                            }
                            Text(
                                "${camp.institution.ifBlank { "Organisation" }} • ${camp.location.ifBlank { "Rural Camp" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${dateFmt.format(Date(camp.startDateMillis))} – ${dateFmt.format(Date(camp.endDateMillis))} • ${camp.participantCount} Participants",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Approved Budget: ${FinanceCalculator.formatInr(camp.approvedBudget)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily)
                            )
                            if (role == UserRole.SYSTEM_ADMIN) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            val toggled = if (camp.campStatus == CampStatus.ARCHIVED) CampStatus.ACTIVE else CampStatus.ARCHIVED
                                            onUpdateCamp(camp.copy(status = toggled.name), role)
                                        }
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = "Archive/Restore")
                                    }
                                    IconButton(
                                        onClick = {
                                            editingCamp = camp
                                            showForm = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.edit)
                                    }
                                    IconButton(onClick = { deleteTarget = camp }) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        CampFormDialog(
            initial = editingCamp,
            onSave = { camp ->
                if (editingCamp == null) onCreateCamp(camp) else onUpdateCamp(camp, role)
                showForm = false
            },
            onDismiss = { showForm = false }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = strings.confirmDeleteTitle,
            message = "Delete camp '${target.name}' and all its financial ledgers?",
            onConfirm = {
                onDeleteCamp(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
fun CampFormDialog(
    initial: Camp?,
    onSave: (Camp) -> Unit,
    onDismiss: () -> Unit
) {
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var institution by remember { mutableStateOf(initial?.institution ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var participantsStr by remember { mutableStateOf(initial?.participantCount?.takeIf { it > 0 }?.toString() ?: "50") }
    var budgetStr by remember { mutableStateOf(initial?.approvedBudget?.takeIf { it > 0 }?.toString() ?: "100000") }
    var coordinator by remember { mutableStateOf(initial?.coordinatorName ?: "") }
    var financeLeader by remember { mutableStateOf(initial?.financeLeaderName ?: "") }
    var status by remember { mutableStateOf(initial?.campStatus ?: CampStatus.ACTIVE) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Rural Camp" else "Edit Camp") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Camp Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = institution, onValueChange = { institution = it }, label = { Text("Institution / Organisation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Village / Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = participantsStr,
                        onValueChange = { participantsStr = it },
                        label = { Text("Participants *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = budgetStr,
                        onValueChange = { budgetStr = it },
                        label = { Text("Approved Budget ₹ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = coordinator, onValueChange = { coordinator = it }, label = { Text("Camp Coordinator") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = financeLeader, onValueChange = { financeLeader = it }, label = { Text("Finance Committee Leader") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CampStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.labelEn) }
                        )
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
                    val pCount = participantsStr.toIntOrNull()
                    val budget = budgetStr.toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "Camp name is required."
                        pCount == null || pCount <= 0 -> error = "Enter valid participant count."
                        budget == null || budget < 0.0 -> error = "Enter valid approved budget."
                        else -> onSave(
                            (initial ?: Camp()).copy(
                                name = name.trim(),
                                institution = institution.trim(),
                                location = location.trim(),
                                startDateMillis = initial?.startDateMillis?.takeIf { it > 0 } ?: now,
                                endDateMillis = initial?.endDateMillis?.takeIf { it > 0 } ?: (now + 6 * dayMs),
                                participantCount = pCount,
                                coordinatorName = coordinator.trim(),
                                financeLeaderName = financeLeader.trim(),
                                approvedBudget = budget,
                                status = status.name
                            )
                        )
                    }
                }
            ) { Text("Save Camp") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun UserManagementScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    onSaveMember: (Camp, CampMember, UserRole) -> Unit,
    onDeleteMember: (Camp, CampMember, UserRole) -> Unit
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Select a camp to manage committee roles and permissions.")
        return
    }
    val role = workspace.effectiveRole
    val canManageUsers = role == UserRole.SYSTEM_ADMIN

    var editingMember by remember { mutableStateOf<CampMember?>(null) }
    var showForm by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (canManageUsers) {
                FloatingActionButton(
                    onClick = {
                        editingMember = null
                        showForm = true
                    }
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Member")
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Role-Based Access Control Matrix", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("• System Admin: Camps, users, roles, budgets, policies, full exports.", style = MaterialTheme.typography.bodySmall)
                        Text("• Finance / Treasurer: Income, expenses, payments, approvals, financial statements.", style = MaterialTheme.typography.bodySmall)
                        Text("• Committee Leader: Assigned committee budget, submit expenses & receipts.", style = MaterialTheme.typography.bodySmall)
                        Text("• Auditor / Reviewer: Verify receipt evidence, inspect audit trail, add remarks.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(workspace.members, key = { it.id }) { m ->
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
                            Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                            if (m.email.isNotBlank()) {
                                Text(m.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${m.userRole.displayNameEn} • ${if (m.isActive) "Active" else "Inactive"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (canManageUsers) {
                            Row {
                                IconButton(
                                    onClick = {
                                        editingMember = m
                                        showForm = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = strings.edit)
                                }
                                if (m.id != camp.ownerId) {
                                    IconButton(onClick = { onDeleteMember(camp, m, role) }) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        MemberFormDialog(
            initial = editingMember,
            onSave = { m ->
                onSaveMember(camp, m, role)
                showForm = false
            },
            onDismiss = { showForm = false }
        )
    }
}

@Composable
private fun MemberFormDialog(
    initial: CampMember?,
    onSave: (CampMember) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(initial?.displayName ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var memberUid by remember { mutableStateOf(initial?.memberUid ?: "") }
    var selectedRole by remember { mutableStateOf(initial?.userRole ?: UserRole.COMMITTEE_LEADER) }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Camp Team Member" else "Edit ${initial.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Full Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Google Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = memberUid,
                    onValueChange = { memberUid = it },
                    label = { Text("Firebase User UID (for cross-device sync)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Role", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserRole.entries.forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = { selectedRole = r },
                            label = { Text(r.displayNameEn) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Active")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isBlank()) {
                        error = "Member name is required."
                    } else {
                        onSave(
                            (initial ?: CampMember()).copy(
                                displayName = displayName.trim(),
                                email = email.trim(),
                                memberUid = memberUid.trim(),
                                role = selectedRole.name,
                                isActive = isActive
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
