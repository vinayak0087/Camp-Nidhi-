package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccessRequest
import com.example.data.model.AdminGovernanceConfig
import com.example.data.model.AdminNotification
import com.example.data.model.AuditLog
import com.example.data.model.BillExtraction
import com.example.data.model.BillOriginal
import com.example.data.model.BillVerificationStatus
import com.example.data.model.BillVersion
import com.example.data.model.Camp
import com.example.data.model.CaseworkFinance
import com.example.data.model.CoAdminDelegatedRole
import com.example.data.model.Committee
import com.example.data.model.ExpenseRecord
import com.example.data.model.FacultyProfile
import com.example.data.model.GovernanceAccessTier
import com.example.data.model.InformationShare
import com.example.data.model.LoginEvent
import com.example.data.model.TemporaryAccessGrant
import com.example.data.model.UserProfile
import com.example.data.repository.GovernanceAndScannerRepository
import com.example.data.repository.toReadableMessage
import com.example.domain.BillAnomalyDetector
import com.example.domain.BillScannerAiService
import com.example.domain.DashboardSummary
import com.example.util.BillDocumentHelper
import com.example.util.ProcessedBillDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class GovernanceAccessState(
    val isResolving: Boolean = true,
    val tier: GovernanceAccessTier = GovernanceAccessTier.VIEWER_UNAPPROVED,
    val coAdminGrant: TemporaryAccessGrant? = null,
    val facultyProfile: FacultyProfile? = null,
    val authorizedCasework: CaseworkFinance? = null,
    val activeShare: InformationShare? = null,
    val myAccessRequests: List<AccessRequest> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis()
) {
    fun coAdminCanAccessModule(moduleKey: String): Boolean {
        if (tier == GovernanceAccessTier.PRIMARY_ADMIN) return true
        if (tier != GovernanceAccessTier.CO_ADMIN_GRANTED) return false
        val grant = coAdminGrant ?: return false
        return grant.isCurrentlyValid(nowMillis) && moduleKey in grant.allowedModules
    }

    val coAdminCanEdit: Boolean
        get() = tier == GovernanceAccessTier.PRIMARY_ADMIN ||
            (tier == GovernanceAccessTier.CO_ADMIN_GRANTED && coAdminGrant?.canEdit == true)

    val coAdminCanApprove: Boolean
        get() = tier == GovernanceAccessTier.PRIMARY_ADMIN ||
            (tier == GovernanceAccessTier.CO_ADMIN_GRANTED && coAdminGrant?.canApprove == true)
}

data class AdminCommandCentreData(
    val users: List<UserProfile> = emptyList(),
    val notifications: List<AdminNotification> = emptyList(),
    val loginEvents: List<LoginEvent> = emptyList(),
    val accessRequests: List<AccessRequest> = emptyList(),
    val grantHistory: List<TemporaryAccessGrant> = emptyList(),
    val coAdminActiveGrant: TemporaryAccessGrant? = null,
    val facultyProfiles: List<FacultyProfile> = emptyList(),
    val caseworks: List<CaseworkFinance> = emptyList(),
    val informationShares: List<InformationShare> = emptyList(),
    val globalAuditLogs: List<AuditLog> = emptyList()
) {
    val unreadNotificationCount: Int get() = notifications.count { !it.isRead }
    val pendingRequestCount: Int get() = accessRequests.count { it.status == "AWAITING_APPROVAL" }
}

data class ScannedBillDraft(
    val billId: String,
    val billVerificationId: String,
    val processedDoc: ProcessedBillDocument,
    val isExtracting: Boolean = true,
    val extractionError: String? = null,
    val vendorName: String = "",
    val billNumber: String = "",
    val billDate: String = "",
    val gstin: String = "",
    val itemDescription: String = "",
    val quantityText: String = "",
    val unitPriceText: String = "",
    val subtotalText: String = "",
    val gstAmountText: String = "",
    val discountText: String = "",
    val grandTotalText: String = "",
    val paymentInfo: String = "",
    val confidenceLevel: String = "MEDIUM",
    val uncertainFields: List<String> = emptyList(),
    val anomalyWarnings: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class GovernanceViewModel(
    private val repo: GovernanceAndScannerRepository,
    private val aiService: BillScannerAiService,
    private val appContext: Context,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String
) : ViewModel() {

    val isPrimaryAdmin: Boolean = AdminGovernanceConfig.isPrimaryAdminEmail(currentUserEmail)
    val isCoAdmin: Boolean = AdminGovernanceConfig.isCoAdminEmail(currentUserEmail)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _hasMemberCamps = MutableStateFlow(false)
    private val _campsResolved = MutableStateFlow(false)

    private val _viewerAcceptedShareId = MutableStateFlow("")
    val viewerAcceptedShareId: StateFlow<String> = _viewerAcceptedShareId.asStateFlow()

    private val _scanQueue = MutableStateFlow<List<ScannedBillDraft>>(emptyList())
    val scanQueue: StateFlow<List<ScannedBillDraft>> = _scanQueue.asStateFlow()

    private val _activeScanIndex = MutableStateFlow(0)
    val activeScanIndex: StateFlow<Int> = _activeScanIndex.asStateFlow()

    private var lastRecordedSessionEventKey: String = ""

    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(10_000L)
        }
    }

    init {
        if (isPrimaryAdmin) {
            viewModelScope.launch {
                try {
                    repo.ensurePrimaryAdminBaseline()
                } catch (e: Exception) {
                    Log.w(TAG, "Primary Admin baseline init note: ${e.message}")
                }
            }
        }
    }

    fun updateMemberCampPresence(hasCamps: Boolean, resolved: Boolean) {
        _hasMemberCamps.value = hasCamps
        if (resolved) _campsResolved.value = true
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun acceptViewerGrantPopup(shareId: String) {
        _viewerAcceptedShareId.value = shareId
    }

    private val coAdminGrantFlow = repo.observeCoAdminActiveGrant()
    private val myFacultyProfileFlow = repo.observeMyFacultyProfile(currentUserEmail)
    private val myActiveShareFlow = repo.observeMyActiveShare(currentUserEmail)
    private val myRequestsFlow = repo.observeMyAccessRequests()

    private val authorizedCaseworkFlow: Flow<CaseworkFinance?> = myFacultyProfileFlow.flatMapLatest { profile ->
        if (profile != null && profile.isActiveNow() && profile.approvedCaseworkId.isNotBlank()) {
            repo.observeAuthorizedCasework(profile.approvedCaseworkId)
        } else {
            flowOf(null)
        }
    }

    private data class CoreIdentityInputs(
        val grant: TemporaryAccessGrant?,
        val faculty: FacultyProfile?,
        val casework: CaseworkFinance?,
        val share: InformationShare?,
        val requests: List<AccessRequest>
    )

    private val coreInputsFlow = combine(
        coAdminGrantFlow,
        myFacultyProfileFlow,
        authorizedCaseworkFlow,
        myActiveShareFlow,
        myRequestsFlow
    ) { grant, faculty, casework, share, requests ->
        CoreIdentityInputs(grant, faculty, casework, share, requests)
    }

    val accessState: StateFlow<GovernanceAccessState> = combine(
        coreInputsFlow,
        _hasMemberCamps,
        _campsResolved,
        tickerFlow
    ) { core, hasCamps, campsResolved, now ->
        val tier = when {
            isPrimaryAdmin -> GovernanceAccessTier.PRIMARY_ADMIN
            isCoAdmin -> {
                if (core.grant?.isCurrentlyValid(now) == true) {
                    GovernanceAccessTier.CO_ADMIN_GRANTED
                } else {
                    GovernanceAccessTier.CO_ADMIN_PENDING
                }
            }
            core.faculty?.isActiveNow(now) == true -> GovernanceAccessTier.FACULTY_IN_CHARGE
            core.share?.isActiveNow(now) == true -> GovernanceAccessTier.INVITED_VIEWER
            hasCamps -> GovernanceAccessTier.CAMP_MEMBER
            else -> GovernanceAccessTier.VIEWER_UNAPPROVED
        }
        val resolving = !isPrimaryAdmin && !isCoAdmin && !campsResolved &&
            core.faculty == null && core.share == null

        GovernanceAccessState(
            isResolving = resolving,
            tier = tier,
            coAdminGrant = core.grant,
            facultyProfile = core.faculty,
            authorizedCasework = core.casework,
            activeShare = core.share,
            myAccessRequests = core.requests,
            nowMillis = now
        )
    }.onEach { state ->
        if (!state.isResolving) {
            recordLoginAuditOnce(state)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GovernanceAccessState(
            isResolving = !isPrimaryAdmin,
            tier = if (isPrimaryAdmin) GovernanceAccessTier.PRIMARY_ADMIN else GovernanceAccessTier.VIEWER_UNAPPROVED
        )
    )

    private fun recordLoginAuditOnce(state: GovernanceAccessState) {
        val key = "${state.tier.name}_${state.activeShare?.id.orEmpty()}_${state.facultyProfile?.id.orEmpty()}"
        if (lastRecordedSessionEventKey == key) return
        lastRecordedSessionEventKey = key
        viewModelScope.launch {
            try {
                when (state.tier) {
                    GovernanceAccessTier.PRIMARY_ADMIN -> {
                        repo.recordLoginEvent("PRIMARY_ADMIN", "PRIMARY_ADMIN_LOGIN", "ALLOWED")
                    }
                    GovernanceAccessTier.CO_ADMIN_PENDING -> {
                        val isExpired = state.coAdminGrant?.let {
                            it.status == "APPROVED" && !it.revoked && System.currentTimeMillis() >= it.expiresAtMillis
                        } == true
                        repo.recordLoginEvent(
                            "CO_ADMIN",
                            "CO_ADMIN_LOGIN_ATTEMPT",
                            if (isExpired) "EXPIRED" else "PENDING_APPROVAL"
                        )
                        repo.sendAdminNotification(
                            type = "CO_ADMIN_LOGIN_ATTEMPT",
                            title = if (isExpired) "Co-Admin Temporary Access Expired" else "Co-Admin Login Attempt (Pending Approval)",
                            message = "${AdminGovernanceConfig.CO_ADMIN_EMAIL} logged in and requires Primary Admin approval or time extension.",
                            relatedEntityId = AdminGovernanceConfig.CO_ADMIN_ACTIVE_GRANT_DOC_ID
                        )
                    }
                    GovernanceAccessTier.CO_ADMIN_GRANTED -> {
                        repo.recordLoginEvent("CO_ADMIN", "CO_ADMIN_LOGIN_SUCCESS", "ALLOWED")
                        repo.sendAdminNotification(
                            type = "CO_ADMIN_LOGIN_SUCCESS",
                            title = "Co-Admin Accessed Delegated Workspace",
                            message = "${AdminGovernanceConfig.CO_ADMIN_EMAIL} accessed role '${state.coAdminGrant?.assignedRoleLabel.orEmpty()}'.",
                            relatedEntityId = state.coAdminGrant?.id.orEmpty()
                        )
                    }
                    GovernanceAccessTier.FACULTY_IN_CHARGE -> {
                        repo.recordLoginEvent("FACULTY_IN_CHARGE", "FACULTY_LOGIN", "ALLOWED")
                    }
                    GovernanceAccessTier.INVITED_VIEWER -> {
                        repo.recordLoginEvent("INVITED_VIEWER", "VIEWER_LOGIN", "ALLOWED")
                        state.activeShare?.let { repo.recordViewerLogin(it) }
                    }
                    GovernanceAccessTier.CAMP_MEMBER -> {
                        repo.recordLoginEvent("CAMP_MEMBER", "MEMBER_LOGIN", "ALLOWED")
                    }
                    GovernanceAccessTier.VIEWER_UNAPPROVED -> {
                        repo.recordLoginEvent("UNAPPROVED_USER", "UNAUTHORIZED_ATTEMPT", "DENIED")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Login audit skipped: ${e.message}")
            }
        }
    }

    // --- BILL COLLECTIONS ---
    private val isGlobalBillReaderFlow = accessState.flatMapLatest { st ->
        flowOf(st.tier == GovernanceAccessTier.PRIMARY_ADMIN || st.coAdminCanAccessModule("BILL_VERIFICATION"))
    }

    val billOriginals: StateFlow<List<BillOriginal>> = isGlobalBillReaderFlow.flatMapLatest { global ->
        repo.observeBillOriginalsForAdminOrMember(global)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val billExtractions: StateFlow<List<BillExtraction>> = isGlobalBillReaderFlow.flatMapLatest { global ->
        repo.observeBillExtractionsForAdminOrMember(global)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val billVersions: StateFlow<List<BillVersion>> = isGlobalBillReaderFlow.flatMapLatest { global ->
        repo.observeBillVersions(global)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // --- PRIMARY ADMIN COMMAND CENTRE ---
    private val adminPartA = if (isPrimaryAdmin) {
        combine(
            repo.observeAllUsers(),
            repo.observeNotifications(),
            repo.observeLoginEvents(),
            repo.observeAllAccessRequests(),
            repo.observeTemporaryGrantHistory()
        ) { users, notifs, logins, reqs, grants ->
            AdminPartA(users, notifs, logins, reqs, grants)
        }
    } else flowOf(AdminPartA())

    private val adminPartB = if (isPrimaryAdmin) {
        combine(
            coAdminGrantFlow,
            repo.observeAllFacultyProfilesForAdmin(),
            repo.observeAllCaseworkForAdmin(),
            repo.observeAllInformationShares(),
            repo.observeGlobalAuditLogs()
        ) { activeGrant, faculty, caseworks, shares, logs ->
            AdminPartB(activeGrant, faculty, caseworks, shares, logs)
        }
    } else flowOf(AdminPartB())

    val adminData: StateFlow<AdminCommandCentreData> = combine(adminPartA, adminPartB) { a, b ->
        AdminCommandCentreData(
            users = a.users,
            notifications = a.notifs,
            loginEvents = a.logins,
            accessRequests = a.reqs,
            grantHistory = a.grants,
            coAdminActiveGrant = b.activeGrant,
            facultyProfiles = b.faculty,
            caseworks = b.caseworks,
            informationShares = b.shares,
            globalAuditLogs = b.logs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminCommandCentreData())

    private data class AdminPartA(
        val users: List<UserProfile> = emptyList(),
        val notifs: List<AdminNotification> = emptyList(),
        val logins: List<LoginEvent> = emptyList(),
        val reqs: List<AccessRequest> = emptyList(),
        val grants: List<TemporaryAccessGrant> = emptyList()
    )

    private data class AdminPartB(
        val activeGrant: TemporaryAccessGrant? = null,
        val faculty: List<FacultyProfile> = emptyList(),
        val caseworks: List<CaseworkFinance> = emptyList(),
        val shares: List<InformationShare> = emptyList(),
        val logs: List<AuditLog> = emptyList()
    )

    // --- SMART BILL SCANNER PIPELINE ---
    fun enqueueScannedUris(uris: List<Uri>, activeCamp: Camp?, existingExpenses: List<ExpenseRecord>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { uri ->
                val billId = "bill_${UUID.randomUUID().toString().take(10)}"
                val verificationId = "BV-${(1000..9999).random()}-${UUID.randomUUID().toString().take(3).uppercase()}"
                val processed = withContext(Dispatchers.IO) {
                    BillDocumentHelper.processBillUri(appContext, uri, billId)
                }
                if (processed == null) {
                    _statusMessage.value = "Unable to read selected bill image or PDF."
                    return@forEach
                }

                val initialDraft = ScannedBillDraft(
                    billId = billId,
                    billVerificationId = verificationId,
                    processedDoc = processed,
                    isExtracting = true
                )
                _scanQueue.value = _scanQueue.value + initialDraft
                _activeScanIndex.value = (_scanQueue.value.size - 1).coerceAtLeast(0)

                val result = aiService.extractBillDetails(processed.bitmapForOcr)
                val updatedList = _scanQueue.value.map { item ->
                    if (item.billId != billId) item
                    else {
                        val ocr = result.getOrNull()
                        if (ocr != null) {
                            val warnings = BillAnomalyDetector.analyze(
                                vendorName = ocr.vendorName,
                                billNumber = ocr.billNumber,
                                billDate = ocr.billDate,
                                quantity = ocr.quantity,
                                unitPrice = ocr.unitPrice,
                                subtotal = ocr.subtotal,
                                gstAmount = ocr.gstAmount,
                                discount = ocr.discount,
                                grandTotal = ocr.grandTotal,
                                confidenceLevel = ocr.confidenceLevel,
                                uncertainFields = ocr.uncertainFields,
                                fileHashSha256 = processed.fileHashSha256,
                                camp = activeCamp,
                                existingBills = billExtractions.value,
                                existingExpenses = existingExpenses
                            )
                            item.copy(
                                isExtracting = false,
                                vendorName = ocr.vendorName,
                                billNumber = ocr.billNumber,
                                billDate = ocr.billDate,
                                gstin = ocr.gstin,
                                itemDescription = ocr.itemDescription,
                                quantityText = ocr.quantity?.toString().orEmpty(),
                                unitPriceText = ocr.unitPrice?.toString().orEmpty(),
                                subtotalText = ocr.subtotal?.toString().orEmpty(),
                                gstAmountText = ocr.gstAmount?.toString().orEmpty(),
                                discountText = ocr.discount?.toString().orEmpty(),
                                grandTotalText = ocr.grandTotal?.toString().orEmpty(),
                                paymentInfo = ocr.paymentInfo,
                                confidenceLevel = ocr.confidenceLevel,
                                uncertainFields = ocr.uncertainFields,
                                anomalyWarnings = warnings
                            )
                        } else {
                            item.copy(
                                isExtracting = false,
                                confidenceLevel = "LOW",
                                extractionError = result.exceptionOrNull()?.message ?: "OCR extraction unavailable; please fill details manually.",
                                anomalyWarnings = listOf("AI OCR could not auto-extract fields. Enter verified bill values manually.")
                            )
                        }
                    }
                }
                _scanQueue.value = updatedList
            }
        }
    }

    fun selectScanIndex(idx: Int) {
        if (idx in _scanQueue.value.indices) {
            _activeScanIndex.value = idx
        }
    }

    fun updateActiveDraft(
        camp: Camp?,
        existingExpenses: List<ExpenseRecord>,
        transform: (ScannedBillDraft) -> ScannedBillDraft
    ) {
        val idx = _activeScanIndex.value
        val current = _scanQueue.value.getOrNull(idx) ?: return
        val edited = transform(current)
        val warnings = BillAnomalyDetector.analyze(
            vendorName = edited.vendorName,
            billNumber = edited.billNumber,
            billDate = edited.billDate,
            quantity = edited.quantityText.toDoubleOrNull(),
            unitPrice = edited.unitPriceText.toDoubleOrNull(),
            subtotal = edited.subtotalText.toDoubleOrNull(),
            gstAmount = edited.gstAmountText.toDoubleOrNull(),
            discount = edited.discountText.toDoubleOrNull(),
            grandTotal = edited.grandTotalText.toDoubleOrNull(),
            confidenceLevel = edited.confidenceLevel,
            uncertainFields = edited.uncertainFields,
            fileHashSha256 = edited.processedDoc.fileHashSha256,
            camp = camp,
            existingBills = billExtractions.value,
            existingExpenses = existingExpenses
        )
        val updated = edited.copy(anomalyWarnings = warnings)
        _scanQueue.value = _scanQueue.value.toMutableList().apply { set(idx, updated) }
    }

    fun discardActiveDraft() {
        val idx = _activeScanIndex.value
        if (idx in _scanQueue.value.indices) {
            val next = _scanQueue.value.toMutableList().apply { removeAt(idx) }
            _scanQueue.value = next
            _activeScanIndex.value = (_activeScanIndex.value).coerceAtMost((next.size - 1).coerceAtLeast(0))
        }
    }

    fun saveActiveScannedBill(
        camp: Camp,
        committee: Committee?,
        existingExpense: ExpenseRecord?,
        createNewExpense: Boolean,
        submitForVerification: Boolean
    ) {
        val draft = _scanQueue.value.getOrNull(_activeScanIndex.value) ?: return
        launchAction(
            if (submitForVerification) "Bill ${draft.billVerificationId} submitted for verification"
            else "Bill ${draft.billVerificationId} saved as draft"
        ) {
            val original = BillOriginal(
                id = draft.billId,
                billVerificationId = draft.billVerificationId,
                fileHashSha256 = draft.processedDoc.fileHashSha256,
                mimeType = draft.processedDoc.mimeType,
                fileName = draft.processedDoc.fileName,
                fileSizeBytes = draft.processedDoc.fileSizeBytes,
                protectedStorageRef = draft.processedDoc.protectedStorageRef,
                originalPreviewBase64 = draft.processedDoc.originalPreviewBase64,
                immutable = true
            )
            val extraction = BillExtraction(
                id = draft.billId,
                billVerificationId = draft.billVerificationId,
                vendorName = draft.vendorName.trim(),
                billNumber = draft.billNumber.trim(),
                billDate = draft.billDate.trim(),
                gstin = draft.gstin.trim(),
                itemDescription = draft.itemDescription.trim(),
                quantity = draft.quantityText.toDoubleOrNull(),
                unitPrice = draft.unitPriceText.toDoubleOrNull(),
                subtotal = draft.subtotalText.toDoubleOrNull(),
                gstAmount = draft.gstAmountText.toDoubleOrNull(),
                discount = draft.discountText.toDoubleOrNull(),
                grandTotal = draft.grandTotalText.toDoubleOrNull(),
                paymentInfo = draft.paymentInfo.trim(),
                confidenceLevel = draft.confidenceLevel,
                uncertainFields = draft.uncertainFields,
                anomalyWarnings = draft.anomalyWarnings
            )
            repo.saveScannedBillAndLinkExpense(
                camp = camp,
                committee = committee,
                existingExpense = existingExpense,
                createNewExpense = createNewExpense,
                billOriginal = original,
                extraction = extraction,
                submitForVerification = submitForVerification
            )
            discardActiveDraft()
        }
    }

    fun adminVerifyOrCorrectBill(
        bill: BillExtraction,
        updatedVendor: String,
        updatedBillNumber: String,
        updatedBillDate: String,
        updatedGstin: String,
        updatedDescription: String,
        updatedGrandTotal: Double?,
        officialExplanation: String,
        adminAnnotations: String,
        newStatus: BillVerificationStatus,
        replacementUri: Uri? = null
    ) {
        launchAction("Updated bill ${bill.billVerificationId} (${newStatus.label})") {
            var repPreview = ""
            var repHash = ""
            if (replacementUri != null) {
                val processed = withContext(Dispatchers.IO) {
                    BillDocumentHelper.processBillUri(appContext, replacementUri, "${bill.id}_rep_${System.currentTimeMillis()}")
                }
                if (processed != null) {
                    repPreview = processed.originalPreviewBase64
                    repHash = processed.fileHashSha256
                }
            }
            repo.adminCorrectOrVerifyBill(
                bill = bill,
                updatedVendor = updatedVendor,
                updatedBillNumber = updatedBillNumber,
                updatedBillDate = updatedBillDate,
                updatedGstin = updatedGstin,
                updatedDescription = updatedDescription,
                updatedGrandTotal = updatedGrandTotal,
                officialExplanation = officialExplanation,
                adminAnnotations = adminAnnotations,
                newStatus = newStatus,
                replacementPreviewBase64 = repPreview,
                replacementFileHash = repHash
            )
        }
    }

    // --- GOVERNANCE ACTIONS ---
    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            try {
                repo.markNotificationRead(notificationId)
            } catch (_: Exception) {
            }
        }
    }

    fun grantCoAdminAccess(
        role: CoAdminDelegatedRole,
        allowedModules: List<String>,
        canRead: Boolean,
        canEdit: Boolean,
        canApprove: Boolean,
        financialLimit: Double,
        durationMinutes: Long,
        reason: String
    ) {
        launchAction("Granted Co-Admin temporary '${role.label}' access") {
            val now = System.currentTimeMillis()
            repo.grantOrUpdateCoAdminAccess(
                role = role,
                allowedModules = allowedModules,
                canRead = canRead,
                canEdit = canEdit,
                canApprove = canApprove,
                financialLimit = financialLimit,
                startsAtMillis = now,
                expiresAtMillis = now + (durationMinutes.coerceAtLeast(1L) * 60_000L),
                reason = reason
            )
        }
    }

    fun revokeCoAdminAccess(reason: String) {
        launchAction("Co-Admin temporary access revoked") {
            repo.revokeCoAdminAccess(reason)
        }
    }

    fun saveFacultyProfile(profile: FacultyProfile) {
        launchAction("Saved ${profile.profileSlotName}") {
            repo.saveFacultyProfile(profile)
        }
    }

    fun saveCaseworkFinance(casework: CaseworkFinance) {
        launchAction("Saved anonymised casework '${casework.caseworkCode}'") {
            repo.saveCaseworkFinance(casework)
        }
    }

    fun recordPresentationLaunch(profile: FacultyProfile, fullScreen: Boolean) {
        viewModelScope.launch {
            try {
                repo.recordPresentationSession(profile, fullScreen)
            } catch (_: Exception) {
            }
        }
    }

    fun submitAccessRequest(
        requestedCampName: String,
        reason: String,
        requestedSections: List<String>,
        requestType: String = "VIEWER"
    ) {
        launchAction("Access request sent to Primary Admin") {
            repo.submitViewerAccessRequest(requestedCampName, reason, requestedSections, requestType)
        }
    }

    fun approveInformationShare(
        recipientEmail: String,
        camp: Camp,
        summary: DashboardSummary,
        allowedSections: List<String>,
        dateFromMillis: Long,
        dateToMillis: Long,
        durationHours: Long,
        linkedRequestId: String? = null
    ) {
        launchAction("Approved controlled sharing for $recipientEmail") {
            val expiresAt = System.currentTimeMillis() + (durationHours.coerceAtLeast(1L) * 3_600_000L)
            repo.approveAndCreateInformationShare(
                recipientEmail = recipientEmail,
                camp = camp,
                summary = summary,
                bills = billExtractions.value.filter { it.campId == camp.id },
                caseworks = adminData.value.caseworks,
                allowedSections = allowedSections,
                dateFromMillis = dateFromMillis,
                dateToMillis = dateToMillis,
                expiresAtMillis = expiresAt,
                linkedRequestId = linkedRequestId
            )
        }
    }

    fun rejectAccessRequest(requestId: String, notes: String) {
        launchAction("Access request rejected") {
            repo.rejectAccessRequest(requestId, notes)
        }
    }

    fun revokeInformationShare(share: InformationShare, reason: String = "Revoked by Primary Admin") {
        launchAction("Revoked access for ${share.recipientEmail}") {
            repo.revokeInformationShare(share, reason)
        }
    }

    fun extendInformationShare(share: InformationShare, extraHours: Long) {
        launchAction("Extended access for ${share.recipientEmail}") {
            val base = maxOf(System.currentTimeMillis(), share.expiresAtMillis)
            repo.extendInformationShare(share, base + extraHours.coerceAtLeast(1L) * 3_600_000L)
        }
    }

    private fun launchAction(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                block()
                _statusMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.toReadableMessage("Action failed")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "GovernanceVM"
    }
}
