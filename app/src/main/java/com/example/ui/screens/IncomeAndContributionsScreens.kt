package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Camp
import com.example.data.model.IncomePaymentStatus
import com.example.data.model.IncomeRecord
import com.example.data.model.PaymentMethod
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IncomeStatusPill
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.viewmodel.CampWorkspaceData
import com.example.util.ReceiptUtils
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IncomeRegisterScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    contributionsOnly: Boolean,
    onSaveIncome: (Camp, IncomeRecord, UserRole) -> Unit,
    onDeleteIncome: (Camp, IncomeRecord, UserRole) -> Unit,
    onImportCsv: (Camp, List<List<String>>, UserRole) -> Unit,
    findDuplicate: (List<IncomeRecord>, Double, String, String) -> IncomeRecord?
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Create or select a camp first to record income.")
        return
    }
    val context = LocalContext.current
    val role = workspace.effectiveRole
    val canEdit = role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedStatus by remember { mutableStateOf("ALL") }
    var editingIncome by remember { mutableStateOf<IncomeRecord?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<IncomeRecord?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val rows = ReportExporter.parseCsvLines(context, uri)
            onImportCsv(camp, rows, role)
        }
    }

    val baseList = remember(workspace.incomes, contributionsOnly) {
        if (contributionsOnly) {
            workspace.incomes.filter {
                val c = it.category.lowercase(Locale.ROOT)
                c.contains("student") || c.contains("staff") || c.contains("sponsorship") || c.contains("fundraising") || it.contributorName.isNotBlank()
            }
        } else {
            workspace.incomes
        }
    }

    val filtered = remember(baseList, searchQuery, selectedCategory, selectedStatus) {
        baseList.filter { inc ->
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            val matchSearch = q.isBlank() ||
                inc.transactionCode.lowercase(Locale.ROOT).contains(q) ||
                inc.source.lowercase(Locale.ROOT).contains(q) ||
                inc.contributorName.lowercase(Locale.ROOT).contains(q) ||
                inc.referenceNumber.lowercase(Locale.ROOT).contains(q)
            val matchCat = selectedCategory == "ALL" || inc.category == selectedCategory
            val matchStatus = selectedStatus == "ALL" || inc.paymentStatus == selectedStatus
            matchSearch && matchCat && matchStatus
        }
    }

    val totalReceived = filtered.sumOf { it.receivedAmount }
    val totalPromised = filtered.sumOf { it.promisedAmount }
    val totalOutstanding = filtered.sumOf { it.outstandingAmount }
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Scaffold(
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = {
                        editingIncome = null
                        showForm = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("add_income_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.add)
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
                                Text(
                                    if (contributionsOnly) strings.navFundContributions else strings.navIncomeRegister,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Unpaid pledges are tracked separately and never counted as cash received.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEdit) {
                                OutlinedButton(onClick = { csvLauncher.launch(arrayOf("text/*", "application/csv")) }) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.importCsv, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Received", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    FinanceCalculator.formatInr(totalReceived),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                    color = ForestGreenSecondary
                                )
                            }
                            Column {
                                Text("Promised", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    FinanceCalculator.formatInr(totalPromised),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Outstanding Due", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    FinanceCalculator.formatInr(totalOutstanding),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                    color = StatusPendingAmber
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search code, source, contributor, ref...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == "ALL",
                        onClick = { selectedStatus = "ALL" },
                        label = { Text(strings.allFilter) }
                    )
                    IncomePaymentStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st.name,
                            onClick = { selectedStatus = st.name },
                            label = { Text(st.labelEn) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = strings.noDataYet,
                        message = "Tap + to record student fees, staff contributions, sponsorships, or institutional grants.",
                        icon = Icons.Default.Savings
                    )
                }
            } else {
                items(filtered, key = { it.id }) { inc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                                Text(
                                    "${inc.transactionCode} • ${inc.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IncomeStatusPill(inc.status)
                            }
                            Text(inc.source, style = MaterialTheme.typography.titleMedium)
                            if (inc.contributorName.isNotBlank() || inc.contributorGroup.isNotBlank()) {
                                Text(
                                    listOf(inc.contributorName, inc.contributorGroup).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Received: ${FinanceCalculator.formatInr(inc.receivedAmount)} / ${FinanceCalculator.formatInr(inc.promisedAmount)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily)
                                )
                                Text(
                                    "${inc.method.labelEn} • ${dateFmt.format(Date(inc.dateMillis))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (inc.outstandingAmount > 0.0) {
                                Text(
                                    "Pending Balance Due: ${FinanceCalculator.formatInr(inc.outstandingAmount)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusPendingAmber
                                )
                            }
                            if (inc.evidenceBase64.isNotBlank()) {
                                val bmp = remember(inc.evidenceBase64) { ReceiptUtils.decodeBase64Bitmap(inc.evidenceBase64) }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Income evidence",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                            if (canEdit) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (inc.outstandingAmount > 0.0) {
                                        TextButton(
                                            onClick = {
                                                onSaveIncome(
                                                    camp,
                                                    inc.copy(receivedAmount = inc.promisedAmount),
                                                    role
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Mark Full Received")
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            editingIncome = inc
                                            showForm = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.edit)
                                    }
                                    IconButton(onClick = { deleteTarget = inc }) {
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

    if (showForm) {
        IncomeFormDialog(
            camp = camp,
            initial = editingIncome,
            existingIncomes = workspace.incomes,
            findDuplicate = findDuplicate,
            onSave = { record ->
                onSaveIncome(camp, record, role)
                showForm = false
            },
            onDismiss = { showForm = false }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = strings.confirmDeleteTitle,
            message = "Delete ${target.transactionCode} (${target.source})?",
            confirmLabel = strings.delete,
            cancelLabel = strings.cancel,
            onConfirm = {
                onDeleteIncome(camp, target, role)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeFormDialog(
    camp: Camp,
    initial: IncomeRecord?,
    existingIncomes: List<IncomeRecord>,
    findDuplicate: (List<IncomeRecord>, Double, String, String) -> IncomeRecord?,
    onSave: (IncomeRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val categories = camp.incomeCategories.ifEmpty { Camp.DEFAULT_INCOME_CATEGORIES }

    var category by remember { mutableStateOf(initial?.category?.ifBlank { categories.first() } ?: categories.first()) }
    var catExpanded by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(initial?.source ?: "") }
    var contributorName by remember { mutableStateOf(initial?.contributorName ?: "") }
    var contributorGroup by remember { mutableStateOf(initial?.contributorGroup ?: "") }
    var promisedStr by remember { mutableStateOf(initial?.promisedAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var receivedStr by remember { mutableStateOf(initial?.receivedAmount?.toString() ?: "") }
    var method by remember { mutableStateOf(initial?.method ?: PaymentMethod.UPI) }
    var reference by remember { mutableStateOf(initial?.referenceNumber ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var evidenceUri by remember { mutableStateOf(initial?.evidenceUri ?: "") }
    var evidenceBase64 by remember { mutableStateOf(initial?.evidenceBase64 ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            evidenceUri = uri.toString()
            evidenceBase64 = ReceiptUtils.encodeReceiptThumbnail(context, uri) ?: ""
        }
    }

    val promisedVal = promisedStr.toDoubleOrNull() ?: 0.0
    val dupMatch = remember(promisedVal, source) {
        if (promisedVal > 0 && source.isNotBlank()) {
            findDuplicate(existingIncomes, promisedVal, source, initial?.id ?: "")
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Record Income / Contribution" else "Edit ${initial.transactionCode}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Income Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    category = c
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Fund Source *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contributorName,
                    onValueChange = { contributorName = it },
                    label = { Text("Contributor Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contributorGroup,
                    onValueChange = { contributorGroup = it },
                    label = { Text("Department / Batch / Sponsor Group") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = promisedStr,
                        onValueChange = { promisedStr = it },
                        label = { Text("Promised ₹ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = receivedStr,
                        onValueChange = { receivedStr = it },
                        label = { Text("Received ₹ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.labelEn) }
                        )
                    }
                }

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference / Receipt No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (evidenceBase64.isBlank()) "Attach Supporting Evidence" else "Change Evidence Photo")
                }

                if (dupMatch != null) {
                    Text(
                        "Duplicate check: ${dupMatch.transactionCode} already exists for '${dupMatch.source}' with ₹${dupMatch.promisedAmount.toLong()}.",
                        color = StatusPendingAmber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (validationError != null) {
                    Text(
                        validationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val promised = promisedStr.toDoubleOrNull()
                    val received = receivedStr.toDoubleOrNull() ?: 0.0
                    when {
                        source.isBlank() -> validationError = "Please enter the fund source."
                        promised == null || promised <= 0.0 -> validationError = "Enter a valid promised/total amount."
                        received < 0.0 || received > promised -> validationError = "Received amount must be between ₹0 and ₹${promised.toLong()}."
                        else -> {
                            onSave(
                                (initial ?: IncomeRecord()).copy(
                                    dateMillis = initial?.dateMillis ?: System.currentTimeMillis(),
                                    category = category,
                                    source = source.trim(),
                                    contributorName = contributorName.trim(),
                                    contributorGroup = contributorGroup.trim(),
                                    promisedAmount = promised,
                                    receivedAmount = received,
                                    paymentMethod = method.name,
                                    referenceNumber = reference.trim(),
                                    notes = notes.trim(),
                                    evidenceUri = evidenceUri,
                                    evidenceBase64 = evidenceBase64
                                )
                            )
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
