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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.Camp
import com.example.data.model.ThemeMode
import com.example.data.model.UserRole
import com.example.localization.AppStrings
import com.example.ui.viewmodel.CampWorkspaceData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    currentUserName: String,
    currentUserEmail: String,
    currentUserId: String,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUpdateCampSettings: (Camp, UserRole, String) -> Unit,
    onRolePreviewChange: (UserRole?) -> Unit,
    onSignOut: () -> Unit
) {
    val camp = workspace.activeCamp
    val actualRole = workspace.actualRole
    val canEditCampConfig = actualRole == UserRole.SYSTEM_ADMIN || actualRole == UserRole.TREASURER

    var approvalLimitStr by remember(camp?.id, camp?.approvalLimit) {
        mutableStateOf(camp?.approvalLimit?.toString() ?: "5000")
    }
    var requireIndependent by remember(camp?.id, camp?.requireIndependentApproval) {
        mutableStateOf(camp?.requireIndependentApproval ?: true)
    }
    var newIncomeCat by remember { mutableStateOf("") }
    var newExpenseCat by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Account Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(currentUserName, style = MaterialTheme.typography.titleMedium)
                    Text(currentUserEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "UID: $currentUserId • ${strings.roleLabel}: ${workspace.effectiveRole.displayNameEn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Language & Appearance
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang.label) }
                            )
                        }
                    }

                    Text(strings.themeLabel, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        // Role Preview (for System Admin testing)
        if (actualRole == UserRole.SYSTEM_ADMIN) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Role View Preview (Admin Testing)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Preview how Treasurer, Committee Leader, or Auditor views the workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            UserRole.entries.forEach { r ->
                                FilterChip(
                                    selected = workspace.effectiveRole == r,
                                    onClick = { onRolePreviewChange(if (r == actualRole) null else r) },
                                    label = { Text(r.displayNameEn) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Editable Approval Settings & Categories
        if (camp != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Approval Policy (${camp.name})", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = approvalLimitStr,
                            onValueChange = { approvalLimitStr = it },
                            enabled = actualRole == UserRole.SYSTEM_ADMIN,
                            label = { Text("High-Value Approval Limit (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Require Independent Verification\n(Block self-approval)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = requireIndependent,
                                enabled = actualRole == UserRole.SYSTEM_ADMIN,
                                onCheckedChange = { requireIndependent = it }
                            )
                        }
                        if (actualRole == UserRole.SYSTEM_ADMIN) {
                            Button(
                                onClick = {
                                    val limit = approvalLimitStr.toDoubleOrNull() ?: camp.approvalLimit
                                    onUpdateCampSettings(
                                        camp.copy(
                                            approvalLimit = limit.coerceAtLeast(0.0),
                                            requireIndependentApproval = requireIndependent
                                        ),
                                        actualRole,
                                        "Updated approval policy settings"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Approval Policy")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Editable Income Categories", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            camp.incomeCategories.forEach { cat ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(cat) },
                                    trailingIcon = if (canEditCampConfig && camp.incomeCategories.size > 1) {
                                        {
                                            IconButton(
                                                onClick = {
                                                    onUpdateCampSettings(
                                                        camp.copy(incomeCategories = camp.incomeCategories - cat),
                                                        actualRole,
                                                        "Removed income category '$cat'"
                                                    )
                                                },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    } else null
                                )
                            }
                        }
                        if (canEditCampConfig) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newIncomeCat,
                                    onValueChange = { newIncomeCat = it },
                                    label = { Text("New Income Category") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val clean = newIncomeCat.trim()
                                        if (clean.isNotBlank() && clean !in camp.incomeCategories) {
                                            onUpdateCampSettings(
                                                camp.copy(incomeCategories = camp.incomeCategories + clean),
                                                actualRole,
                                                "Added income category '$clean'"
                                            )
                                            newIncomeCat = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        }

                        Text("Editable Expense Categories", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            camp.expenseCategories.forEach { cat ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(cat) },
                                    trailingIcon = if (canEditCampConfig && camp.expenseCategories.size > 1) {
                                        {
                                            IconButton(
                                                onClick = {
                                                    onUpdateCampSettings(
                                                        camp.copy(expenseCategories = camp.expenseCategories - cat),
                                                        actualRole,
                                                        "Removed expense category '$cat'"
                                                    )
                                                },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    } else null
                                )
                            }
                        }
                        if (canEditCampConfig) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newExpenseCat,
                                    onValueChange = { newExpenseCat = it },
                                    label = { Text("New Expense Category") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val clean = newExpenseCat.trim()
                                        if (clean.isNotBlank() && clean !in camp.expenseCategories) {
                                            onUpdateCampSettings(
                                                camp.copy(expenseCategories = camp.expenseCategories + clean),
                                                actualRole,
                                                "Added expense category '$clean'"
                                            )
                                            newExpenseCat = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Backup Guidance & Sign Out
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Backup & Offline Reliability", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "CampNidhi caches all active ledgers locally on your device and syncs automatically to Cloud Firestore when internet returns. Use Reports & Exports → Export CSV/PDF after each camp day for archival backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.signOut)
                    }
                }
            }
        }
    }
}
