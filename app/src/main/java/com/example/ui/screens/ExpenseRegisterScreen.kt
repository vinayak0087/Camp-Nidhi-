package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
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
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomeRecord
import com.example.data.model.PaymentMethod
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExpenseStatusPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusPaidContainer
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.viewmodel.CampWorkspaceData
import com.example.util.ReceiptUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseRegisterScreen(
    workspace: CampWorkspaceData,
    strings: AppStrings,
    onSaveExpense: (Camp, ExpenseRecord, ExpenseRecord?, UserRole) -> Unit,
    onDeleteExpense: (Camp, ExpenseRecord, UserRole) -> Unit,
    findDuplicate: (List<ExpenseRecord>, Double, String, String) -> ExpenseRecord?
) {
    val camp = workspace.activeCamp
    if (camp == null) {
        EmptyStateCard("No Camp Selected", "Create or select a camp first to record expenses.")
        return
    }
    val role = workspace.effectiveRole
    val canCreate = role != UserRole.AUDITOR

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var committeeFilter by remember { mutableStateOf("ALL") }
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExpenseRecord?>(null) }

    val filtered = remember(workspace.expenses, searchQuery, statusFilter, committeeFilter) {
        workspace.expenses.filter { exp ->
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            val matchSearch = q.isBlank() ||
                exp.expenseCode.lowercase(Locale.ROOT).contains(q) ||
                exp.description.lowercase(Locale.ROOT).contains(q) ||
                exp.vendorName.lowercase(Locale.ROOT).contains(q) ||
                exp.committeeName.lowercase(Locale.ROOT).contains(q)
            val matchStatus = statusFilter == "ALL" || exp.paymentStatus == statusFilter
            val matchComm = committeeFilter == "ALL" || exp.committeeId == committeeFilter
            matchSearch && matchStatus && matchComm
        }
    }

    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Scaffold(
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = {
                        editingExpense = null
                        showForm = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_expense_fab")
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(strings.searchHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == "ALL",
                        onClick = { statusFilter = "ALL" },
                        label = { Text(strings.allFilter) }
                    )
                    ExpensePaymentStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st.name,
                            onClick = { statusFilter = st.name },
                            label = { Text(st.labelEn) }
                        )
                    }
                }
            }

            if (workspace.committees.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = committeeFilter == "ALL",
                            onClick = { committeeFilter = "ALL" },
                            label = { Text("All Committees") }
                        )
                        workspace.committees.forEach { c ->
                            FilterChip(
                                selected = committeeFilter == c.id,
                                onClick = { committeeFilter = c.id },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = strings.noDataYet,
                        message = "Submit committee bills with vendor names and receipt photographs for full audit accountability.",
                        icon = Icons.Default.ReceiptLong
                    )
                }
            } else {
                items(filtered, key = { it.id }) { exp ->
                    val canEditThis = when (role) {
                        UserRole.SYSTEM_ADMIN, UserRole.TREASURER -> true
                        UserRole.COMMITTEE_LEADER ->
                            exp.submittedByUid == workspace.activeCamp.ownerId || exp.status in listOf(
                                ExpensePaymentStatus.DRAFT,
                                ExpensePaymentStatus.SUBMITTED,
                                ExpensePaymentStatus.PENDING_APPROVAL,
                                ExpensePaymentStatus.REJECTED
                            )
                        UserRole.AUDITOR -> false
                    }

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
                                    "${exp.expenseCode} • ${exp.committeeName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (exp.receiptVerified) {
                                        StatusPill(
                                            text = "Verified",
                                            containerColor = StatusPaidContainer,
                                            contentColor = StatusPaidGreen
                                        )
                                    }
                                    ExpenseStatusPill(exp.status)
                                }
                            }

                            Text(exp.description, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${exp.vendorName} • ${exp.category} • ${dateFmt.format(Date(exp.dateMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        FinanceCalculator.formatInr(exp.amount),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (exp.paidAmount > 0.0) {
                                        Text(
                                            "Paid: ${FinanceCalculator.formatInr(exp.paidAmount)} (${exp.method.labelEn})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "By: ${exp.submittedByName}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (exp.approvedByName.isNotBlank()) {
                                        Text(
                                            "Approved: ${exp.approvedByName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            if (exp.receiptThumbnailBase64.isNotBlank()) {
                                val bmp = remember(exp.receiptThumbnailBase64) {
                                    ReceiptUtils.decodeBase64Bitmap(exp.receiptThumbnailBase64)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Receipt photo for ${exp.expenseCode}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }

                            if (exp.remarks.isNotBlank()) {
                                Text(
                                    "Remarks: ${exp.remarks}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (canEditThis) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingExpense = exp
                                            showForm = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.edit)
                                    }
                                    if (role == UserRole.SYSTEM_ADMIN || role == UserRole.TREASURER || exp.status == ExpensePaymentStatus.DRAFT) {
                                        IconButton(onClick = { deleteTarget = exp }) {
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

    if (showForm) {
        ExpenseFormDialog(
            camp = camp,
            committees = workspace.committees,
            incomes = workspace.incomes,
            initial = editingExpense,
            existingExpenses = workspace.expenses,
            actorRole = role,
            findDuplicate = findDuplicate,
            onSave = { record ->
                onSaveExpense(camp, record, editingExpense, role)
                showForm = false
            },
            onDismiss = { showForm = false }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = strings.confirmDeleteTitle,
            message = "Delete expense ${target.expenseCode} (${target.description})?",
            confirmLabel = strings.delete,
            cancelLabel = strings.cancel,
            onConfirm = {
                onDeleteExpense(camp, target, role)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFormDialog(
    camp: Camp,
    committees: List<Committee>,
    incomes: List<IncomeRecord>,
    initial: ExpenseRecord?,
    existingExpenses: List<ExpenseRecord>,
    actorRole: UserRole,
    findDuplicate: (List<ExpenseRecord>, Double, String, String) -> ExpenseRecord?,
    onSave: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val categories = camp.expenseCategories.ifEmpty { Camp.DEFAULT_EXPENSE_CATEGORIES }

    var selectedCommittee by remember {
        mutableStateOf(
            committees.firstOrNull { it.id == initial?.committeeId }
                ?: committees.firstOrNull()
        )
    }
    var commExpanded by remember { mutableStateOf(false) }

    var category by remember { mutableStateOf(initial?.category?.ifBlank { categories.first() } ?: categories.first()) }
    var catExpanded by remember { mutableStateOf(false) }

    var description by remember { mutableStateOf(initial?.description ?: "") }
    var vendor by remember { mutableStateOf(initial?.vendorName ?: "") }
    var amountStr by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var method by remember { mutableStateOf(initial?.method ?: PaymentMethod.UPI) }
    var status by remember {
        mutableStateOf(
            initial?.status ?: if (actorRole == UserRole.COMMITTEE_LEADER) ExpensePaymentStatus.SUBMITTED else ExpensePaymentStatus.PENDING_APPROVAL
        )
    }
    var sourceIncomeCode by remember { mutableStateOf(initial?.sourceIncomeCode ?: incomes.firstOrNull()?.transactionCode.orEmpty()) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var receiptUri by remember { mutableStateOf(initial?.receiptUri ?: "") }
    var receiptBase64 by remember { mutableStateOf(initial?.receiptThumbnailBase64 ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            receiptUri = uri.toString()
            receiptBase64 = ReceiptUtils.encodeReceiptThumbnail(context, uri) ?: ""
        }
    }

    val amountVal = amountStr.toDoubleOrNull() ?: 0.0
    val dupMatch = remember(amountVal, vendor) {
        if (amountVal > 0 && vendor.isNotBlank()) {
            findDuplicate(existingExpenses, amountVal, vendor, initial?.id ?: "")
        } else null
    }

    val allowedStatuses = if (actorRole == UserRole.COMMITTEE_LEADER) {
        listOf(ExpensePaymentStatus.DRAFT, ExpensePaymentStatus.SUBMITTED, ExpensePaymentStatus.PENDING_APPROVAL)
    } else {
        listOf(
            ExpensePaymentStatus.DRAFT,
            ExpensePaymentStatus.SUBMITTED,
            ExpensePaymentStatus.PENDING_APPROVAL,
            ExpensePaymentStatus.APPROVED
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Expense Entry" else "Correct ${initial.expenseCode}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initial != null && initial.status in listOf(ExpensePaymentStatus.APPROVED, ExpensePaymentStatus.PAID, ExpensePaymentStatus.PARTIALLY_PAID)) {
                    Text(
                        "Audit Protection: Editing an approved record logs previous & new values permanently in Audit History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusPendingAmber
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = commExpanded,
                    onExpandedChange = { commExpanded = !commExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCommittee?.name ?: "Select Committee",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Committee *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = commExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = commExpanded,
                        onDismissRequest = { commExpanded = false }
                    ) {
                        committees.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    selectedCommittee = c
                                    commExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Category *") },
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description *") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Vendor / Recipient *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (incomes.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = sourceExpanded,
                        onExpandedChange = { sourceExpanded = !sourceExpanded }
                    ) {
                        OutlinedTextField(
                            value = sourceIncomeCode.ifBlank { "General Camp Fund" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rupee Trail Source Fund") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sourceExpanded,
                            onDismissRequest = { sourceExpanded = false }
                        ) {
                            incomes.forEach { inc ->
                                DropdownMenuItem(
                                    text = { Text("${inc.transactionCode} • ${inc.source}") },
                                    onClick = {
                                        sourceIncomeCode = inc.transactionCode
                                        sourceExpanded = false
                                    }
                                )
                            }
                        }
                    }
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

                Text("Workflow Status", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedStatuses.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.labelEn) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (receiptBase64.isBlank()) "Upload Bill / Receipt Photo" else "Receipt Attached (Change)")
                }

                if (receiptBase64.isNotBlank()) {
                    val bmp = remember(receiptBase64) { ReceiptUtils.decodeBase64Bitmap(receiptBase64) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Receipt Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks / Justification") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (dupMatch != null) {
                    Text(
                        "Potential duplicate: ${dupMatch.expenseCode} for '${dupMatch.vendorName}' (₹${dupMatch.amount.toLong()}) already recorded.",
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
                    val comm = selectedCommittee
                    val amt = amountStr.toDoubleOrNull()
                    when {
                        comm == null -> validationError = "Please create/select a committee first."
                        description.isBlank() -> validationError = "Description is required."
                        vendor.isBlank() -> validationError = "Vendor or recipient is required."
                        amt == null || amt <= 0.0 -> validationError = "Enter a valid positive amount."
                        else -> {
                            onSave(
                                (initial ?: ExpenseRecord()).copy(
                                    dateMillis = initial?.dateMillis ?: System.currentTimeMillis(),
                                    committeeId = comm.id,
                                    committeeName = comm.name,
                                    category = category,
                                    description = description.trim(),
                                    vendorName = vendor.trim(),
                                    amount = amt,
                                    paymentMethod = method.name,
                                    paymentStatus = status.name,
                                    receiptUri = receiptUri,
                                    receiptThumbnailBase64 = receiptBase64,
                                    remarks = remarks.trim(),
                                    sourceIncomeCode = sourceIncomeCode
                                )
                            )
                        }
                    }
                }
            ) {
                Text("Save Expense")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
