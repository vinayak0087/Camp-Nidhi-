package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.Committee
import com.example.data.model.ExpenseRecord
import com.example.domain.FinanceCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusDangerContainer
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusPendingContainer
import com.example.ui.viewmodel.CampWorkspaceData
import com.example.ui.viewmodel.ScannedBillDraft
import com.example.util.BillDocumentHelper

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmartBillScannerScreen(
    workspace: CampWorkspaceData,
    scanQueue: List<ScannedBillDraft>,
    activeScanIndex: Int,
    onEnqueueUris: (List<Uri>) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onUpdateActiveDraft: ((ScannedBillDraft) -> ScannedBillDraft) -> Unit,
    onDiscardDraft: () -> Unit,
    onSaveScannedBill: (Committee?, ExpenseRecord?, Boolean, Boolean) -> Unit,
    onOpenVerificationCentre: () -> Unit
) {
    val context = LocalContext.current
    val activeCamp = workspace.activeCamp
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) {
            onEnqueueUris(listOf(uri))
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            val uri = BillDocumentHelper.createCameraOutputUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionDenied = true
        }
    }
    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onEnqueueUris(listOf(uri))
    }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onEnqueueUris(uris)
    }
    val pdfAndDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onEnqueueUris(listOf(uri))
    }

    val launchCamera = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            cameraPermissionDenied = false
            val uri = BillDocumentHelper.createCameraOutputUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val currentDraft = scanQueue.getOrNull(activeScanIndex)
    var selectedCommittee by remember(activeCamp?.id, workspace.committees) {
        mutableStateOf(workspace.committees.firstOrNull())
    }
    var createNewExpenseMode by remember(currentDraft?.billId) { mutableStateOf(true) }
    var selectedExistingExpense by remember(currentDraft?.billId, workspace.expenses) {
        mutableStateOf(workspace.expenses.firstOrNull())
    }
    var commMenuOpen by remember { mutableStateOf(false) }
    var expMenuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Brand Hero Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "SMART BILL SCANNER",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenVerificationCentre,
                            modifier = Modifier.testTag("open_verification_centre_btn")
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Verification Centre", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        "Scan. Extract. Verify. Track.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                    )
                    Text(
                        "AI OCR extracts 12 financial fields while preserving a permanent, tamper-proof original image or PDF with SHA-256 verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 6 Scanning Options
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Capture or Upload Expense Bills (JPG, JPEG, PNG, PDF)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = launchCamera,
                            modifier = Modifier.testTag("scanner_open_camera_btn")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Camera")
                        }
                        OutlinedButton(
                            onClick = launchCamera,
                            modifier = Modifier.testTag("scanner_capture_photo_btn")
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Capture Bill Photograph")
                        }
                        OutlinedButton(
                            onClick = {
                                singleImagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("scanner_upload_image_btn")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload Image")
                        }
                        OutlinedButton(
                            onClick = { pdfAndDocPicker.launch(arrayOf("application/pdf", "image/*")) },
                            modifier = Modifier.testTag("scanner_upload_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                multiImagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("scanner_multi_bills_btn")
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Multiple Bills")
                        }
                        OutlinedButton(
                            onClick = {
                                singleImagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("scanner_gallery_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload from Gallery")
                        }
                    }
                    if (cameraPermissionDenied) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StatusPendingContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Camera permission is required to capture bill photos directly. You can grant Camera permission or use Upload Image / Upload PDF.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusPendingAmber,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (activeCamp == null) {
            item {
                EmptyStateCard(
                    title = "No Active Camp Selected",
                    message = "Select or create a camp first to scan and link expense bills.",
                    icon = Icons.Default.DocumentScanner
                )
            }
        } else if (scanQueue.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Ready to Scan Bills",
                    message = "Choose any capture or upload option above. Multi-bill batches queue automatically for verification.",
                    icon = Icons.Default.DocumentScanner
                )
            }
        } else if (currentDraft != null) {
            // Queue Selector for Multi-Bill Scanning
            if (scanQueue.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        scanQueue.forEachIndexed { idx, item ->
                            FilterChip(
                                selected = idx == activeScanIndex,
                                onClick = { onSelectQueueIndex(idx) },
                                label = {
                                    Text(
                                        if (item.isExtracting) "Bill ${idx + 1} (OCR...)"
                                        else "${item.billVerificationId} (${item.vendorName.ifBlank { "Bill ${idx + 1}" }})"
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Immutable Original Preview & Hash Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Immutable Original Document",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Bill Verification ID: ${currentDraft.billVerificationId}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            StatusPill(
                                text = "READ-ONLY ORIGINAL",
                                containerColor = ForestGreenContainer,
                                contentColor = ForestGreenSecondary
                            )
                            IconButton(onClick = onDiscardDraft) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Discard Scan")
                            }
                        }

                        val previewBmp = remember(currentDraft.billId) {
                            val raw = Base64.decode(currentDraft.processedDoc.originalPreviewBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        }
                        if (previewBmp != null) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Original Scanned Bill",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = ForestGreenSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "SHA-256: ${currentDraft.processedDoc.fileHashSha256.take(24)}... • ${currentDraft.processedDoc.mimeType}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentDraft.isExtracting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("AI Bill Intelligence is reading receipt fields...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Anomaly & Confidence Warnings
            if (currentDraft.anomalyWarnings.isNotEmpty() || currentDraft.extractionError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusPendingContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusPendingAmber)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Verification Alerts (Confidence: ${currentDraft.confidenceLevel})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StatusPendingAmber
                                )
                            }
                            currentDraft.extractionError?.let {
                                Text("• $it", style = MaterialTheme.typography.bodySmall, color = StatusDangerCrimson)
                            }
                            currentDraft.anomalyWarnings.forEach { warn ->
                                Text("• $warn", style = MaterialTheme.typography.bodySmall, color = StatusPendingAmber)
                            }
                        }
                    }
                }
            }

            // 12-Field Editable Verification Form
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Verify Extracted Bill Information (12 Fields)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Unclear or missing numbers are never invented. Review highlighted fields before saving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        @Composable
                        fun fieldBox(
                            label: String,
                            value: String,
                            fieldKey: String,
                            numeric: Boolean = false,
                            onChange: (String) -> Unit
                        ) {
                            val isUncertain = currentDraft.uncertainFields.any {
                                it.contains(fieldKey, ignoreCase = true)
                            } || (fieldKey in listOf("vendorName", "billNumber", "grandTotal") && value.isBlank())
                            OutlinedTextField(
                                value = value,
                                onValueChange = onChange,
                                label = {
                                    Text(if (isUncertain) "$label (Verify!)" else label)
                                },
                                isError = isUncertain,
                                singleLine = fieldKey != "itemDescription",
                                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        fieldBox("1. Vendor / Shop Name", currentDraft.vendorName, "vendorName") { v ->
                            onUpdateActiveDraft { it.copy(vendorName = v) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("2. Bill Number", currentDraft.billNumber, "billNumber") { v ->
                                    onUpdateActiveDraft { it.copy(billNumber = v) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("3. Bill Date", currentDraft.billDate, "billDate") { v ->
                                    onUpdateActiveDraft { it.copy(billDate = v) }
                                }
                            }
                        }
                        fieldBox("4. GSTIN (Optional)", currentDraft.gstin, "gstin") { v ->
                            onUpdateActiveDraft { it.copy(gstin = v) }
                        }
                        fieldBox("5. Item Description", currentDraft.itemDescription, "itemDescription") { v ->
                            onUpdateActiveDraft { it.copy(itemDescription = v) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("6. Quantity", currentDraft.quantityText, "quantity", numeric = true) { v ->
                                    onUpdateActiveDraft { it.copy(quantityText = v) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("7. Unit Price (₹)", currentDraft.unitPriceText, "unitPrice", numeric = true) { v ->
                                    onUpdateActiveDraft { it.copy(unitPriceText = v) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("8. Subtotal (₹)", currentDraft.subtotalText, "subtotal", numeric = true) { v ->
                                    onUpdateActiveDraft { it.copy(subtotalText = v) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("9. GST Amount (₹)", currentDraft.gstAmountText, "gstAmount", numeric = true) { v ->
                                    onUpdateActiveDraft { it.copy(gstAmountText = v) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("10. Discount (₹)", currentDraft.discountText, "discount", numeric = true) { v ->
                                    onUpdateActiveDraft { it.copy(discountText = v) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                fieldBox("11. Grand Total (₹)", currentDraft.grandTotalText, "grandTotal", numeric = true) { v ->
                                    onUpdateActiveDraft { it.copy(grandTotalText = v) }
                                }
                            }
                        }
                        fieldBox("12. Payment Information", currentDraft.paymentInfo, "paymentInfo") { v ->
                            onUpdateActiveDraft { it.copy(paymentInfo = v) }
                        }
                    }
                }
            }

            // Bill-to-Expense Connection Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Bill-to-Expense Connection", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Camp: ${activeCamp.name} • Submitted By: ${workspace.effectiveRole.displayNameEn}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = createNewExpenseMode,
                                onClick = { createNewExpenseMode = true },
                                label = { Text("Create New Expense") },
                                modifier = Modifier.testTag("bill_mode_create_expense")
                            )
                            FilterChip(
                                selected = !createNewExpenseMode,
                                onClick = { createNewExpenseMode = false },
                                label = { Text("Connect Existing Expense") },
                                modifier = Modifier.testTag("bill_mode_connect_expense")
                            )
                        }

                        if (createNewExpenseMode) {
                            ExposedDropdownMenuBox(
                                expanded = commMenuOpen,
                                onExpandedChange = { commMenuOpen = !commMenuOpen }
                            ) {
                                OutlinedTextField(
                                    value = selectedCommittee?.name ?: "Overall Committee",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Target Committee") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(commMenuOpen) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = commMenuOpen,
                                    onDismissRequest = { commMenuOpen = false }
                                ) {
                                    workspace.committees.forEach { comm ->
                                        DropdownMenuItem(
                                            text = { Text(comm.name) },
                                            onClick = {
                                                selectedCommittee = comm
                                                commMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = expMenuOpen,
                                onExpandedChange = { expMenuOpen = !expMenuOpen }
                            ) {
                                OutlinedTextField(
                                    value = selectedExistingExpense?.let {
                                        "${it.expenseCode} • ${it.vendorName} (${FinanceCalculator.formatInr(it.amount)})"
                                    } ?: "No existing expenses in camp",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Existing Expense") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expMenuOpen) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = expMenuOpen,
                                    onDismissRequest = { expMenuOpen = false }
                                ) {
                                    workspace.expenses.forEach { exp ->
                                        DropdownMenuItem(
                                            text = { Text("${exp.expenseCode} • ${exp.vendorName} (${FinanceCalculator.formatInr(exp.amount)})") },
                                            onClick = {
                                                selectedExistingExpense = exp
                                                expMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSaveScannedBill(
                                        selectedCommittee,
                                        if (createNewExpenseMode) null else selectedExistingExpense,
                                        createNewExpenseMode,
                                        false
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bill_save_draft_btn")
                            ) {
                                Text("Save as Draft")
                            }
                            Button(
                                onClick = {
                                    onSaveScannedBill(
                                        selectedCommittee,
                                        if (createNewExpenseMode) null else selectedExistingExpense,
                                        createNewExpenseMode,
                                        true
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bill_submit_verification_btn")
                            ) {
                                Text("Submit for Verification")
                            }
                        }
                    }
                }
            }
        }
    }
}
