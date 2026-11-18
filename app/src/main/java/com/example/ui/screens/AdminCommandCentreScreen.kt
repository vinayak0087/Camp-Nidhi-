package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessRequest
import com.example.data.model.AccessRequestStatus
import com.example.data.model.AdminGovernanceConfig
import com.example.data.model.BillExtraction
import com.example.data.model.BillVerificationStatus
import com.example.data.model.Camp
import com.example.data.model.CaseworkFinance
import com.example.data.model.CoAdminDelegatedRole
import com.example.data.model.DelegatedModule
import com.example.data.model.FacultyAccessMode
import com.example.data.model.FacultyProfile
import com.example.data.model.InformationShare
import com.example.data.model.ShareableSection
import com.example.domain.DashboardSummary
import com.example.domain.FinanceCalculator
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
import com.example.ui.viewmodel.AdminCommandCentreData
import com.example.ui.viewmodel.CampWorkspaceData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminCommandCentreScreen(
    workspace: CampWorkspaceData,
    adminData: AdminCommandCentreData,
    billExtractions: List<BillExtraction>,
    onMarkNotificationRead: (String) -> Unit,
    onGrantCoAdmin: (CoAdminDelegatedRole, List<String>, Boolean, Boolean, Boolean, Double, Long, String) -> Unit,
    onRevokeCoAdmin: (String) -> Unit,
    onSaveFacultyProfile: (FacultyProfile) -> Unit,
    onSaveCasework: (CaseworkFinance) -> Unit,
    onApproveShare: (String, Camp, DashboardSummary, List<String>, Long, Long, Long, String?) -> Unit,
    onRejectRequest: (String, String) -> Unit,
    onRevokeShare: (InformationShare, String) -> Unit,
    onExtendShare: (InformationShare, Long) -> Unit,
    onOpenVerificationCentre: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Co-Admin", "Faculty (3)", "Viewer Share", "Audit & Logins")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> CommandOverviewTab(
                adminData = adminData,
                billExtractions = billExtractions,
                onMarkRead = onMarkNotificationRead,
                onNavigateTab = { selectedTab = it },
                onOpenVerificationCentre = onOpenVerificationCentre
            )

            1 -> CoAdminManagerTab(
                adminData = adminData,
                onGrantCoAdmin = onGrantCoAdmin,
                onRevokeCoAdmin = onRevokeCoAdmin
            )

            2 -> FacultyAndCaseworkTab(
                workspace = workspace,
                adminData = adminData,
                onSaveFacultyProfile = onSaveFacultyProfile,
                onSaveCasework = onSaveCasework
            )

            3 -> ControlledSharingTab(
                workspace = workspace,
                adminData = adminData,
                onApproveShare = onApproveShare,
                onRejectRequest = onRejectRequest,
                onRevokeShare = onRevokeShare,
                onExtendShare = onExtendShare
            )

            4 -> GovernanceAuditTab(adminData = adminData)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandOverviewTab(
    adminData: AdminCommandCentreData,
    billExtractions: List<BillExtraction>,
    onMarkRead: (String) -> Unit,
    onNavigateTab: (Int) -> Unit,
    onOpenVerificationCentre: () -> Unit
) {
    val now = System.currentTimeMillis()
    val dayAgo = now - 86_400_000L
    val activeUsers24h = adminData.loginEvents.filter { it.timestampMillis >= dayAgo }.map { it.email }.distinct().size
    val coAdminActive = adminData.coAdminActiveGrant?.isCurrentlyValid(now) == true
    val enabledFaculty = adminData.facultyProfiles.count { it.isActiveNow(now) }
    val activeGuestShares = adminData.informationShares.count { it.isActiveNow(now) }
    val pendingBills = billExtractions.count { it.status == BillVerificationStatus.SUBMITTED }
    val revocationCount = adminData.grantHistory.count { it.revoked || it.status == "REVOKED" } +
        adminData.informationShares.count { it.revoked || it.status == "REVOKED" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Primary Admin Security Command Centre",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        "Exclusive Authority: ${AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // All 10 Required Metrics
        item {
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard("Total Registered Users", "${adminData.users.size}", "Verified accounts", Icons.Default.People, NavyPrimary, Modifier.weight(1f))
                KpiMetricCard("Active Users (24h)", "$activeUsers24h", "Recent sessions", Icons.Default.VerifiedUser, StatusPaidGreen, Modifier.weight(1f))
                KpiMetricCard("Pending Access Requests", "${adminData.pendingRequestCount}", "Awaiting approval", Icons.Default.Share, StatusPendingAmber, Modifier.weight(1f), onClick = { onNavigateTab(3) })
                KpiMetricCard("Co-Admin Access Status", if (coAdminActive) "ACTIVE" else "LOCKED", AdminGovernanceConfig.CO_ADMIN_EMAIL, Icons.Default.AdminPanelSettings, if (coAdminActive) StatusPaidGreen else StatusPendingAmber, Modifier.weight(1f), onClick = { onNavigateTab(1) })
                KpiMetricCard("Faculty Access Status", "$enabledFaculty / 3 Active", "Casework & Presentation", Icons.Default.School, ForestGreenSecondary, Modifier.weight(1f), onClick = { onNavigateTab(2) })
                KpiMetricCard("Guest Access Status", "$activeGuestShares Active", "Controlled info shares", Icons.Default.Share, StatusInfoBlue, Modifier.weight(1f), onClick = { onNavigateTab(3) })
                KpiMetricCard("Pending Bill Verification", "$pendingBills", "Needs Primary review", Icons.Default.CheckCircle, StatusPendingAmber, Modifier.weight(1f), onClick = onOpenVerificationCentre)
                KpiMetricCard("Recent Financial Changes", "${adminData.globalAuditLogs.size}", "Immutable audit log", Icons.Default.History, NavyPrimary, Modifier.weight(1f), onClick = { onNavigateTab(4) })
                KpiMetricCard("Recent Logins", "${adminData.loginEvents.size}", "Monitored login trail", Icons.Default.VerifiedUser, StatusInfoBlue, Modifier.weight(1f), onClick = { onNavigateTab(4) })
                KpiMetricCard("Access Revocation History", "$revocationCount", "Revoked grants & shares", Icons.Default.Block, StatusDangerCrimson, Modifier.weight(1f), onClick = { onNavigateTab(4) })
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Notification Centre (${adminData.unreadNotificationCount} unread)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        if (adminData.notifications.isEmpty()) {
            item {
                Text(
                    "No security or verification notifications yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(adminData.notifications.take(25), key = { it.id }) { notif ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else StatusInfoContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(notif.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (!notif.isRead) {
                                TextButton(onClick = { onMarkRead(notif.id) }) { Text("Mark Read") }
                            }
                        }
                        Text(notif.message, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${notif.type} • ${formatAdminDate(notif.createdAtMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoAdminManagerTab(
    adminData: AdminCommandCentreData,
    onGrantCoAdmin: (CoAdminDelegatedRole, List<String>, Boolean, Boolean, Boolean, Double, Long, String) -> Unit,
    onRevokeCoAdmin: (String) -> Unit
) {
    val activeGrant = adminData.coAdminActiveGrant
    val isCurrentlyValid = activeGrant?.isCurrentlyValid() == true

    var selectedRole by remember { mutableStateOf(CoAdminDelegatedRole.BILL_VERIFICATION_ASSISTANT) }
    var selectedModules by remember { mutableStateOf(selectedRole.defaultModules.toSet()) }
    var canRead by remember { mutableStateOf(true) }
    var canEdit by remember { mutableStateOf(true) }
    var canApprove by remember { mutableStateOf(false) }
    var limitText by remember { mutableStateOf("15000") }
    var durationMinutes by remember { mutableStateOf(120L) }
    var reason by remember { mutableStateOf("Camp finance & bill verification duty") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentlyValid) ForestGreenContainer else StatusPendingContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Co-Admin: ${AdminGovernanceConfig.CO_ADMIN_EMAIL}", style = MaterialTheme.typography.titleSmall)
                    if (isCurrentlyValid && activeGrant != null) {
                        Text(
                            "ACTIVE: ${activeGrant.assignedRoleLabel} • Expires ${formatAdminDate(activeGrant.expiresAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForestGreenSecondary
                        )
                        Button(
                            onClick = { onRevokeCoAdmin("Revoked immediately by Primary Admin") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("revoke_coadmin_btn")
                        ) {
                            Text("Revoke Co-Admin Access Now")
                        }
                    } else {
                        Text(
                            "Status: LOCKED / PENDING APPROVAL (No active grant; automatic zero access enforced).",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPendingAmber
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Admin Permission Manager (Temporary Delegation)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoAdminDelegatedRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = {
                                    selectedRole = role
                                    selectedModules = role.defaultModules.toSet()
                                    canEdit = role != CoAdminDelegatedRole.READ_ONLY_AUDITOR
                                    canApprove = role == CoAdminDelegatedRole.FINANCE_MANAGER
                                },
                                label = { Text(role.label) }
                            )
                        }
                    }

                    Text("Allowed Modules", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DelegatedModule.entries.forEach { mod ->
                            FilterChip(
                                selected = mod.key in selectedModules,
                                onClick = {
                                    selectedModules = if (mod.key in selectedModules) selectedModules - mod.key else selectedModules + mod.key
                                },
                                label = { Text(mod.label) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = canRead, onCheckedChange = { canRead = it })
                            Text("Read")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = canEdit, onCheckedChange = { canEdit = it })
                            Text("Edit")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = canApprove, onCheckedChange = { canApprove = it })
                            Text("Approve")
                        }
                    }

                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Financial Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Expiry Duration", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15L to "15m", 60L to "1 hr", 240L to "4 hrs", 1440L to "24 hrs", 4320L to "72 hrs").forEach { (mins, label) ->
                            FilterChip(
                                selected = durationMinutes == mins,
                                onClick = { durationMinutes = mins },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Delegation Purpose / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onGrantCoAdmin(
                                selectedRole,
                                selectedModules.toList(),
                                canRead,
                                canEdit,
                                canApprove,
                                limitText.toDoubleOrNull() ?: 0.0,
                                durationMinutes,
                                reason
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_coadmin_btn")
                    ) {
                        Text("Grant Temporary Access")
                    }
                }
            }
        }

        items(adminData.grantHistory.take(15), key = { it.id }) { g ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${g.assignedRoleLabel} • ${g.status}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Modules: ${g.allowedModules.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    Text("Expires: ${formatAdminDate(g.expiresAtMillis)}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacultyAndCaseworkTab(
    workspace: CampWorkspaceData,
    adminData: AdminCommandCentreData,
    onSaveFacultyProfile: (FacultyProfile) -> Unit,
    onSaveCasework: (CaseworkFinance) -> Unit
) {
    val activeCamp = workspace.activeCamp
    var editingProfile by remember { mutableStateOf<FacultyProfile?>(null) }
    var showCreateCasework by remember { mutableStateOf(false) }

    val profiles = if (adminData.facultyProfiles.isEmpty()) FacultyProfile.DEFAULT_SLOTS else adminData.facultyProfiles

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
                    Text("3 Faculty-In-Charge Profiles", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Disabled by default. Each faculty sees only their assigned anonymised casework/programme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showCreateCasework = true }) {
                    Text("+ Casework")
                }
            }
        }

        items(profiles, key = { it.id }) { prof ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(prof.profileSlotName, style = MaterialTheme.typography.titleSmall)
                        StatusPill(
                            text = if (prof.isActiveNow()) "ENABLED" else prof.status,
                            containerColor = if (prof.isActiveNow()) StatusPaidContainer else StatusNeutralContainer,
                            contentColor = if (prof.isActiveNow()) StatusPaidGreen else StatusNeutralSlate
                        )
                    }
                    Text("Email: ${prof.assignedEmail.ifBlank { "Unassigned" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Casework: ${prof.approvedCaseworkName.ifBlank { "None" }} • Mode: ${prof.accessMode}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { editingProfile = prof }) {
                        Text("Configure ${prof.profileSlotName}")
                    }
                }
            }
        }

        item {
            Text("Anonymised Casework Finance Records (${adminData.caseworks.size})", style = MaterialTheme.typography.titleSmall)
        }
        items(adminData.caseworks, key = { it.id }) { cw ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${cw.caseworkCode} • ${cw.programmeName}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Budget ${FinanceCalculator.formatInr(cw.allocatedBudget)} • Spent ${FinanceCalculator.formatInr(cw.totalExpenses)} (${cw.facultyProfileId})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    editingProfile?.let { prof ->
        var email by remember { mutableStateOf(prof.assignedEmail) }
        var name by remember { mutableStateOf(prof.assignedFacultyName) }
        var enabled by remember { mutableStateOf(prof.isEnabled) }
        var mode by remember { mutableStateOf(FacultyAccessMode.fromString(prof.accessMode)) }
        var selectedCasework by remember {
            mutableStateOf(adminData.caseworks.firstOrNull { it.id == prof.approvedCaseworkId } ?: adminData.caseworks.firstOrNull())
        }
        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = { Text("Configure ${prof.profileSlotName}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Faculty Institutional Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Faculty Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Assign Anonymised Casework", style = MaterialTheme.typography.labelMedium)
                    adminData.caseworks.forEach { cw ->
                        FilterChip(
                            selected = selectedCasework?.id == cw.id,
                            onClick = { selectedCasework = cw },
                            label = { Text("${cw.caseworkCode} - ${cw.programmeName}") }
                        )
                    }
                    Text("Access Mode", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FacultyAccessMode.entries.forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable Profile Access")
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cw = selectedCasework
                    onSaveFacultyProfile(
                        prof.copy(
                            assignedEmail = email.trim().lowercase(),
                            assignedFacultyName = name.trim(),
                            campId = cw?.campId ?: activeCamp?.id.orEmpty(),
                            campName = activeCamp?.name.orEmpty(),
                            approvedCaseworkId = cw?.id.orEmpty(),
                            approvedCaseworkName = cw?.let { "${it.caseworkCode} • ${it.programmeName}" }.orEmpty(),
                            accessMode = mode.name,
                            isEnabled = enabled,
                            status = if (enabled) "APPROVED" else "DISABLED",
                            expiresAtMillis = if (enabled) System.currentTimeMillis() + 30L * 86_400_000L else 0L
                        )
                    )
                    editingProfile = null
                }) { Text("Save Profile") }
            },
            dismissButton = { TextButton(onClick = { editingProfile = null }) { Text("Cancel") } }
        )
    }

    if (showCreateCasework && activeCamp != null) {
        var code by remember { mutableStateOf("CW-2026-0${adminData.caseworks.size + 1}") }
        var progName by remember { mutableStateOf("Rural Community Support Programme") }
        var budgetText by remember { mutableStateOf("25000") }
        var expText by remember { mutableStateOf("14500") }
        var slotId by remember { mutableStateOf("faculty_01") }
        AlertDialog(
            onDismissRequest = { showCreateCasework = false },
            title = { Text("Create Anonymised Casework Finance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use anonymised casework codes only. Never store personal beneficiary PII.", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Casework ID / Code") }, singleLine = true)
                    OutlinedTextField(value = progName, onValueChange = { progName = it }, label = { Text("Programme Name") }, singleLine = true)
                    OutlinedTextField(value = budgetText, onValueChange = { budgetText = it }, label = { Text("Allocated Budget (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = expText, onValueChange = { expText = it }, label = { Text("Expenses So Far (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("faculty_01", "faculty_02", "faculty_03").forEach { s ->
                            FilterChip(selected = slotId == s, onClick = { slotId = s }, label = { Text(s.uppercase()) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val b = budgetText.toDoubleOrNull() ?: 25000.0
                    val e = expText.toDoubleOrNull() ?: 0.0
                    onSaveCasework(
                        CaseworkFinance(
                            campId = activeCamp.id,
                            caseworkCode = code.trim(),
                            programmeName = progName.trim(),
                            facultyProfileId = slotId,
                            allocatedBudget = b,
                            totalIncome = b,
                            totalExpenses = e,
                            incomeCategoryBreakdown = mapOf("Institutional Grant" to b),
                            expenseCategoryBreakdown = mapOf("Field Materials" to e * 0.6, "Logistics" to e * 0.4),
                            committeeBreakdown = mapOf("Programme Committee" to e * 0.7, "Food Committee" to e * 0.3),
                            dailyActivity = mapOf("Day 1" to e * 0.35, "Day 2" to e * 0.35, "Day 3" to e * 0.30),
                            anonymisedSummaryNote = "Anonymised community programme ledger (zero personal PII).",
                            isDemoData = activeCamp.isDemoData
                        )
                    )
                    showCreateCasework = false
                }) { Text("Save Casework") }
            },
            dismissButton = { TextButton(onClick = { showCreateCasework = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlledSharingTab(
    workspace: CampWorkspaceData,
    adminData: AdminCommandCentreData,
    onApproveShare: (String, Camp, DashboardSummary, List<String>, Long, Long, Long, String?) -> Unit,
    onRejectRequest: (String, String) -> Unit,
    onRevokeShare: (InformationShare, String) -> Unit,
    onExtendShare: (InformationShare, Long) -> Unit
) {
    val activeCamp = workspace.activeCamp
    var recipientEmail by remember { mutableStateOf("") }
    var linkedRequestId by remember { mutableStateOf<String?>(null) }
    var selectedSections by remember { mutableStateOf(setOf(ShareableSection.CAMP_OVERVIEW.key, ShareableSection.TOTAL_INCOME.key)) }
    var durationHours by remember { mutableStateOf(48L) }
    var showConfirmModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val pendingRequests = adminData.accessRequests.filter { it.status == AccessRequestStatus.AWAITING_APPROVAL.name }
        if (pendingRequests.isNotEmpty()) {
            item { Text("Pending Viewer / Faculty Requests (${pendingRequests.size})", style = MaterialTheme.typography.titleSmall) }
            items(pendingRequests, key = { it.id }) { req ->
                Card(colors = CardDefaults.cardColors(containerColor = StatusPendingContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${req.requesterName} (${req.requesterEmail})", style = MaterialTheme.typography.titleSmall)
                        Text("Camp: ${req.requestedCampName} • Purpose: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                        if (req.requestedSections.isNotEmpty()) {
                            Text("Requested: ${req.requestedSections.joinToString()}", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                recipientEmail = req.requesterEmail
                                linkedRequestId = req.id
                                if (req.requestedSections.isNotEmpty()) {
                                    selectedSections = req.requestedSections.toSet()
                                }
                            }) { Text("Select for Approval") }
                            OutlinedButton(onClick = { onRejectRequest(req.id, "Declined by Primary Admin") }) { Text("Reject") }
                        }
                    }
                }
            }
        }

        // WHAT INFORMATION DO YOU WANT TO SHARE?
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WHAT INFORMATION DO YOU WANT TO SHARE?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text("Viewer Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Approved Sections (Strict Projection — Unchecked Data Cannot Be Accessed)", style = MaterialTheme.typography.labelSmall)
                    ShareableSection.entries.forEach { sec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sec.key in selectedSections,
                                onCheckedChange = { checked ->
                                    selectedSections = if (checked) selectedSections + sec.key else selectedSections - sec.key
                                }
                            )
                            Text(sec.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Expiry Time", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24L to "24 hrs", 48L to "48 hrs", 168L to "7 days").forEach { (h, lbl) ->
                            FilterChip(selected = durationHours == h, onClick = { durationHours = h }, label = { Text(lbl) })
                        }
                    }
                    Button(
                        enabled = activeCamp != null && recipientEmail.isNotBlank() && selectedSections.isNotEmpty(),
                        onClick = { showConfirmModal = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review & Grant Controlled Share")
                    }
                }
            }
        }

        item { Text("Active & Past Information Shares (${adminData.informationShares.size})", style = MaterialTheme.typography.titleSmall) }
        items(adminData.informationShares, key = { it.id }) { share ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(share.recipientEmail, style = MaterialTheme.typography.titleSmall)
                        StatusPill(
                            text = if (share.isActiveNow()) "ACTIVE" else share.status,
                            containerColor = if (share.isActiveNow()) StatusPaidContainer else StatusDangerContainer,
                            contentColor = if (share.isActiveNow()) StatusPaidGreen else StatusDangerCrimson
                        )
                    }
                    Text("Camp: ${share.campName} • Sections: ${share.allowedSections.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    Text("Logins: ${share.loginCount} • Expires: ${formatAdminDate(share.expiresAtMillis)}", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onExtendShare(share, 24L) }) { Text("Extend +24h") }
                        if (!share.revoked) {
                            OutlinedButton(
                                onClick = { onRevokeShare(share, "Revoked by Primary Admin") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Revoke") }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmModal && activeCamp != null) {
        AlertDialog(
            onDismissRequest = { showConfirmModal = false },
            title = { Text("CONFIRM INFORMATION SHARING") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recipient: ${recipientEmail.trim().lowercase()}", style = MaterialTheme.typography.bodySmall)
                    Text("Camp: ${activeCamp.name}", style = MaterialTheme.typography.bodySmall)
                    Text("Approved Sections:", style = MaterialTheme.typography.labelMedium)
                    selectedSections.forEach { key ->
                        Text("• ${ShareableSection.labelForKey(key)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Expiry Time: $durationHours hours from approval", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onApproveShare(
                        recipientEmail.trim(),
                        activeCamp,
                        workspace.dashboard,
                        selectedSections.toList(),
                        activeCamp.startDateMillis,
                        activeCamp.endDateMillis,
                        durationHours,
                        linkedRequestId
                    )
                    showConfirmModal = false
                    linkedRequestId = null
                }) {
                    Text("Confirm & Grant")
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmModal = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GovernanceAuditTab(adminData: AdminCommandCentreData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Recent Login Events (${adminData.loginEvents.size})", style = MaterialTheme.typography.titleSmall) }
        items(adminData.loginEvents.take(20), key = { it.id }) { ev ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${ev.email} • ${ev.eventType} (${ev.outcome})", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Text("${ev.roleClassification} • ${formatAdminDate(ev.timestampMillis)}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item { Text("Financial & Security Audit Log (${adminData.globalAuditLogs.size})", style = MaterialTheme.typography.titleSmall) }
        items(adminData.globalAuditLogs.take(30), key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${log.action} • ${log.actorName}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatAdminDate(millis: Long): String {
    if (millis <= 0L) return "-"
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))
}
