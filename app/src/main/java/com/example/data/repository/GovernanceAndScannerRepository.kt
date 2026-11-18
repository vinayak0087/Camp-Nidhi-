package com.example.data.repository

import android.content.Context
import com.example.data.model.AccessRequest
import com.example.data.model.AccessRequestStatus
import com.example.data.model.AdminGovernanceConfig
import com.example.data.model.AdminNotification
import com.example.data.model.AdminSettings
import com.example.data.model.AuditEntityType
import com.example.data.model.AuditLog
import com.example.data.model.BillExtraction
import com.example.data.model.BillOriginal
import com.example.data.model.BillVerificationStatus
import com.example.data.model.BillVersion
import com.example.data.model.Camp
import com.example.data.model.CaseworkFinance
import com.example.data.model.CoAdminDelegatedRole
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.FacultyProfile
import com.example.data.model.InformationShare
import com.example.data.model.LoginEvent
import com.example.data.model.PaymentMethod
import com.example.data.model.PresentationSession
import com.example.data.model.RolePermissionConfig
import com.example.data.model.ShareableSection
import com.example.data.model.TemporaryAccessGrant
import com.example.data.model.UserProfile
import com.example.domain.DashboardSummary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class GovernanceAndScannerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(CampNidhiFirestoreProvider.getDatabase(context))

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required.")

    private fun currentEmail(): String =
        AdminGovernanceConfig.normalizeEmail(auth.currentUser?.email)

    private fun currentName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentEmail().substringBefore("@").ifBlank { "Member" }

    fun deterministicShareDocId(recipientEmail: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(AdminGovernanceConfig.normalizeEmail(recipientEmail).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "share_${digest.take(20)}"
    }

    private fun <T> observeSafeList(
        query: () -> com.google.firebase.firestore.Query,
        mapper: (DocumentSnapshot) -> T?
    ): Flow<List<T>> = flow {
        requireUid()
        emitAll(
            query().snapshots()
                .map { snap -> snap.documents.mapNotNull(mapper) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(emptyList())
                }
        )
    }

    // --- PRIMARY ADMIN BASELINE ---
    suspend fun ensurePrimaryAdminBaseline() {
        val uid = requireUid()
        val email = currentEmail()
        if (!AdminGovernanceConfig.isPrimaryAdminEmail(email)) return

        val settingsRef = db.collection("admin_settings").document(AdminGovernanceConfig.GLOBAL_ADMIN_SETTINGS_DOC_ID)
        if (!settingsRef.get().await().exists()) {
            settingsRef.set(
                AdminSettings(
                    updatedByUid = uid
                ).toMap()
            ).await()
        }

        FacultyProfile.DEFAULT_SLOTS.forEach { slot ->
            val ref = db.collection("faculty_profiles").document(slot.id)
            if (!ref.get().await().exists()) {
                ref.set(slot.toMap()).await()
            }
        }

        val roleSeeds = listOf(
            RolePermissionConfig(
                id = "primary_admin",
                roleKey = "PRIMARY_ADMIN",
                roleLabel = "Primary Admin (2501721049055@mcc.edu.in)",
                allowedModules = listOf("ALL"),
                canRead = true,
                canEdit = true,
                canApprove = true,
                isPrimaryExclusive = true
            ),
            RolePermissionConfig(
                id = "co_admin_delegated",
                roleKey = "CO_ADMIN",
                roleLabel = "Co-Admin (Delegated Only)",
                allowedModules = emptyList(),
                canRead = false,
                canEdit = false,
                canApprove = false,
                isPrimaryExclusive = false
            )
        )
        roleSeeds.forEach { rp ->
            val rRef = db.collection("role_permissions").document(rp.id)
            if (!rRef.get().await().exists()) {
                rRef.set(rp.toMap()).await()
            }
        }
    }

    // --- LOGIN EVENTS & NOTIFICATIONS ---
    suspend fun recordLoginEvent(
        roleClassification: String,
        eventType: String,
        outcome: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val email = currentEmail()
        val id = "login_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val event = LoginEvent(
            id = id,
            userUid = uid,
            email = email,
            displayName = currentName(),
            roleClassification = roleClassification,
            eventType = eventType,
            outcome = outcome,
            timestampMillis = System.currentTimeMillis()
        )
        db.collection("login_events").document(id).set(event.toCreateMap()).await()
    }

    suspend fun sendAdminNotification(
        type: String,
        title: String,
        message: String,
        relatedEntityId: String = ""
    ) {
        val uid = auth.currentUser?.uid ?: return
        val id = "notif_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val notif = AdminNotification(
            id = id,
            targetEmail = AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL,
            type = type,
            title = title,
            message = message,
            actorUid = uid,
            actorEmail = currentEmail(),
            relatedEntityId = relatedEntityId,
            isRead = false,
            createdAtMillis = System.currentTimeMillis()
        )
        db.collection("notifications").document(id).set(notif.toCreateMap()).await()
    }

    suspend fun markNotificationRead(notificationId: String) {
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
            .await()
    }

    fun observeNotifications(): Flow<List<AdminNotification>> = observeSafeList(
        query = { db.collection("notifications") },
        mapper = { it.toObject(AdminNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.createdAtMillis } }

    fun observeLoginEvents(): Flow<List<LoginEvent>> = observeSafeList(
        query = { db.collection("login_events") },
        mapper = { it.toObject(LoginEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.timestampMillis } }

    fun observeAllUsers(): Flow<List<UserProfile>> = observeSafeList(
        query = { db.collection("users") },
        mapper = { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedBy { it.displayName } }

    fun observeGlobalAuditLogs(): Flow<List<AuditLog>> = observeSafeList(
        query = { db.collection("financial_audit_logs") },
        mapper = { it.toObject(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    // --- CO-ADMIN TEMPORARY ROLE DELEGATION ---
    fun observeCoAdminActiveGrant(): Flow<TemporaryAccessGrant?> = flow {
        requireUid()
        emitAll(
            db.collection("temporary_access_grants")
                .document(AdminGovernanceConfig.CO_ADMIN_ACTIVE_GRANT_DOC_ID)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(TemporaryAccessGrant::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        )
    }

    fun observeTemporaryGrantHistory(): Flow<List<TemporaryAccessGrant>> = observeSafeList(
        query = { db.collection("temporary_access_grants") },
        mapper = { it.toObject(TemporaryAccessGrant::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list ->
        list.filter { it.id != AdminGovernanceConfig.CO_ADMIN_ACTIVE_GRANT_DOC_ID }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    suspend fun grantOrUpdateCoAdminAccess(
        role: CoAdminDelegatedRole,
        allowedModules: List<String>,
        canRead: Boolean,
        canEdit: Boolean,
        canApprove: Boolean,
        financialLimit: Double,
        startsAtMillis: Long,
        expiresAtMillis: Long,
        reason: String
    ) {
        val uid = requireUid()
        val historyId = "grant_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val grant = TemporaryAccessGrant(
            id = historyId,
            targetEmail = AdminGovernanceConfig.CO_ADMIN_EMAIL,
            assignedRole = role.name,
            assignedRoleLabel = role.label,
            allowedModules = allowedModules.distinct(),
            canRead = canRead,
            canEdit = canEdit,
            canApprove = canApprove,
            financialLimit = financialLimit,
            startsAtMillis = startsAtMillis,
            expiresAtMillis = expiresAtMillis,
            reason = reason.ifBlank { "Delegated ${role.label} access" },
            status = "APPROVED",
            revoked = false,
            grantedByUid = uid,
            grantedByEmail = currentEmail()
        )
        val batch = db.batch()
        batch.set(db.collection("temporary_access_grants").document(historyId), grant.toMap())
        batch.set(
            db.collection("temporary_access_grants").document(AdminGovernanceConfig.CO_ADMIN_ACTIVE_GRANT_DOC_ID),
            grant.toMap()
        )
        writeGlobalSecurityAudit(
            batch = batch,
            action = "GRANT_CO_ADMIN_ACCESS",
            entityId = historyId,
            summary = "Granted Co-Admin (${AdminGovernanceConfig.CO_ADMIN_EMAIL}) temporary '${role.label}' access (${allowedModules.joinToString()})"
        )
        batch.commit().await()

        sendAdminNotification(
            type = "TEMPORARY_ROLE_ASSIGNED",
            title = "Co-Admin Temporary Access Granted",
            message = "Assigned ${role.label} to ${AdminGovernanceConfig.CO_ADMIN_EMAIL} until expiry.",
            relatedEntityId = historyId
        )
    }

    suspend fun revokeCoAdminAccess(reason: String = "Revoked by Primary Admin") {
        val uid = requireUid()
        val activeRef = db.collection("temporary_access_grants")
            .document(AdminGovernanceConfig.CO_ADMIN_ACTIVE_GRANT_DOC_ID)
        val historyId = "revoke_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val revokedDoc = TemporaryAccessGrant(
            id = historyId,
            targetEmail = AdminGovernanceConfig.CO_ADMIN_EMAIL,
            assignedRole = CoAdminDelegatedRole.READ_ONLY_AUDITOR.name,
            assignedRoleLabel = "Revoked",
            allowedModules = emptyList(),
            canRead = false,
            canEdit = false,
            canApprove = false,
            startsAtMillis = 0L,
            expiresAtMillis = 0L,
            reason = reason,
            status = "REVOKED",
            revoked = true,
            revokedReason = reason,
            grantedByUid = uid,
            grantedByEmail = currentEmail()
        )
        val batch = db.batch()
        batch.set(activeRef, revokedDoc.toMap())
        batch.set(db.collection("temporary_access_grants").document(historyId), revokedDoc.toMap())
        writeGlobalSecurityAudit(
            batch = batch,
            action = "REVOKE_CO_ADMIN_ACCESS",
            entityId = historyId,
            summary = "Revoked Co-Admin delegated access: $reason"
        )
        batch.commit().await()

        sendAdminNotification(
            type = "ACCESS_REVOKED",
            title = "Co-Admin Access Revoked",
            message = "Access revoked for ${AdminGovernanceConfig.CO_ADMIN_EMAIL}: $reason",
            relatedEntityId = historyId
        )
    }

    // --- FACULTY PROFILES & CASEWORK FINANCE ---
    fun observeAllFacultyProfilesForAdmin(): Flow<List<FacultyProfile>> = observeSafeList(
        query = { db.collection("faculty_profiles") },
        mapper = { it.toObject(FacultyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedBy { it.profileSlotName } }

    fun observeMyFacultyProfile(email: String): Flow<FacultyProfile?> {
        val norm = AdminGovernanceConfig.normalizeEmail(email)
        val slots = listOf("faculty_01", "faculty_02", "faculty_03").map { slotId ->
            flow<FacultyProfile?> {
                requireUid()
                emitAll(
                    db.collection("faculty_profiles").document(slotId).snapshots()
                        .map { snap ->
                            if (!snap.exists()) null
                            else snap.toObject(FacultyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }
                        .catch { e ->
                            if (e is CancellationException) throw e
                            emit(null)
                        }
                )
            }
        }
        return combine(slots) { arr ->
            arr.firstOrNull {
                it != null &&
                    AdminGovernanceConfig.normalizeEmail(it.assignedEmail) == norm &&
                    it.isActiveNow()
            }
        }
    }

    suspend fun saveFacultyProfile(profile: FacultyProfile) {
        val uid = requireUid()
        val ref = db.collection("faculty_profiles").document(profile.id)
        val normalized = profile.copy(
            assignedEmail = AdminGovernanceConfig.normalizeEmail(profile.assignedEmail),
            approvedByEmail = currentEmail()
        )
        val batch = db.batch()
        batch.set(ref, normalized.toMap())
        writeGlobalSecurityAudit(
            batch = batch,
            action = "UPDATE_FACULTY_PROFILE",
            entityId = profile.id,
            summary = "${profile.profileSlotName} (${normalized.assignedEmail}) -> ${profile.status}, Casework: ${profile.approvedCaseworkName}"
        )
        batch.commit().await()
    }

    fun observeAllCaseworkForAdmin(): Flow<List<CaseworkFinance>> = observeSafeList(
        query = { db.collection("casework_finance") },
        mapper = { it.toObject(CaseworkFinance::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    )

    fun observeAuthorizedCasework(caseworkId: String): Flow<CaseworkFinance?> = flow {
        requireUid()
        if (caseworkId.isBlank()) {
            emit(null)
            return@flow
        }
        emitAll(
            db.collection("casework_finance").document(caseworkId).snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(CaseworkFinance::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        )
    }

    suspend fun saveCaseworkFinance(casework: CaseworkFinance): String {
        val uid = requireUid()
        val id = casework.id.ifBlank { "cw_${UUID.randomUUID().toString().take(8)}" }
        val normalized = casework.copy(
            id = id,
            caseworkId = id,
            ownerId = casework.ownerId.ifBlank { uid },
            memberUids = (casework.memberUids + uid).distinct()
        )
        db.collection("casework_finance").document(id).set(normalized.toCreateMap()).await()
        return id
    }

    suspend fun recordPresentationSession(profile: FacultyProfile, isFullScreen: Boolean) {
        val id = "pres_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val session = PresentationSession(
            id = id,
            facultyEmail = currentEmail(),
            facultyProfileId = profile.id,
            campId = profile.campId,
            caseworkId = profile.approvedCaseworkId,
            startedAtMillis = System.currentTimeMillis(),
            isFullScreen = isFullScreen
        )
        db.collection("presentation_sessions").document(id).set(session.toCreateMap()).await()
    }

    // --- ACCESS REQUESTS & CONTROLLED INFORMATION SHARING ---
    fun observeAllAccessRequests(): Flow<List<AccessRequest>> = observeSafeList(
        query = { db.collection("access_requests") },
        mapper = { it.toObject(AccessRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.requestedAtMillis } }

    fun observeMyAccessRequests(): Flow<List<AccessRequest>> = observeSafeList(
        query = {
            val uid = requireUid()
            db.collection("access_requests").whereEqualTo("requesterUid", uid)
        },
        mapper = { it.toObject(AccessRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.requestedAtMillis } }

    suspend fun submitViewerAccessRequest(
        requestedCampName: String,
        reason: String,
        requestedSections: List<String>,
        requestType: String = "VIEWER"
    ): String {
        val uid = requireUid()
        val email = currentEmail()
        val id = "req_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val req = AccessRequest(
            id = id,
            requesterUid = uid,
            requesterEmail = email,
            requesterName = currentName(),
            requestType = requestType,
            requestedCampName = requestedCampName.ifBlank { "Rural Camp Access" },
            reason = reason.ifBlank { "Financial review request" },
            requestedSections = requestedSections,
            status = AccessRequestStatus.AWAITING_APPROVAL.name,
            requestedAtMillis = System.currentTimeMillis()
        )
        db.collection("access_requests").document(id).set(req.toCreateMap()).await()

        sendAdminNotification(
            type = if (requestType == "FACULTY") "FACULTY_REQUEST" else "GUEST_ACCESS_REQUEST",
            title = "New $requestType Access Request",
            message = "$email requested access to '${req.requestedCampName}': ${req.reason}",
            relatedEntityId = id
        )
        return id
    }

    suspend fun rejectAccessRequest(requestId: String, notes: String) {
        db.collection("access_requests").document(requestId).update(
            mapOf(
                "status" to AccessRequestStatus.REJECTED.name,
                "reviewedByEmail" to currentEmail(),
                "reviewNotes" to notes.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun observeAllInformationShares(): Flow<List<InformationShare>> = observeSafeList(
        query = { db.collection("information_shares") },
        mapper = { it.toObject(InformationShare::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeMyActiveShare(email: String): Flow<InformationShare?> = flow {
        requireUid()
        val docId = deterministicShareDocId(email)
        emitAll(
            db.collection("information_shares").document(docId).snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(InformationShare::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.takeIf { it.isActiveNow() }
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        )
    }

    suspend fun approveAndCreateInformationShare(
        recipientEmail: String,
        camp: Camp,
        summary: DashboardSummary,
        bills: List<BillExtraction>,
        caseworks: List<CaseworkFinance>,
        allowedSections: List<String>,
        dateFromMillis: Long,
        dateToMillis: Long,
        expiresAtMillis: Long,
        linkedRequestId: String? = null
    ): String {
        val normEmail = AdminGovernanceConfig.normalizeEmail(recipientEmail)
        val shareId = deterministicShareDocId(normEmail)
        val allowed = allowedSections.toSet()

        // Strictly populate ONLY the approved projection fields!
        val committeeRows = if (ShareableSection.COMMITTEE_BUDGET.key in allowed || ShareableSection.COMMITTEE_EXPENSES.key in allowed) {
            summary.committeeSummaries.map { cs ->
                buildMap<String, Any> {
                    put("committeeName", cs.committee.name)
                    if (ShareableSection.COMMITTEE_BUDGET.key in allowed) put("allocatedBudget", cs.allocatedBudget)
                    if (ShareableSection.COMMITTEE_EXPENSES.key in allowed) put("amountSpent", cs.amountSpent)
                }
            }
        } else emptyList()

        val dailyRows = if (ShareableSection.DAILY_EXPENSE_REPORT.key in allowed || ShareableSection.INCOME_EXPENSE_CHARTS.key in allowed) {
            summary.dailyExpenseTrend.map { pt ->
                mapOf("dayLabel" to pt.dayLabel, "amount" to pt.amount)
            }
        } else emptyList()

        val verifiedBills = bills.filter { it.status == BillVerificationStatus.VERIFIED }
        val caseworkNote = if (ShareableSection.CASEWORK_SUMMARY.key in allowed) {
            caseworks.filter { it.campId == camp.id }.joinToString("\n") {
                "${it.caseworkCode} (${it.programmeName}): Budget ₹${it.allocatedBudget.toLong()}, Spent ₹${it.totalExpenses.toLong()}"
            }.ifBlank { "No anonymised casework summaries published for this camp." }
        } else ""

        val finalReportText = if (ShareableSection.FINAL_FINANCIAL_REPORT.key in allowed) {
            buildString {
                appendLine("Camp: ${camp.name} (${camp.location})")
                appendLine("Approved Budget: ₹${summary.totalBudget.toLong()}")
                appendLine("Total Funds Received: ₹${summary.totalFundsReceived.toLong()}")
                appendLine("Total Committed Expenses: ₹${summary.totalExpensesCommitted.toLong()}")
                appendLine("Remaining Cash Balance: ₹${summary.remainingCashBalance.toLong()}")
                if (camp.treasurerDeclaration.isNotBlank()) appendLine("Treasurer Declaration: ${camp.treasurerDeclaration}")
                if (camp.auditorRemarks.isNotBlank()) appendLine("Auditor Remarks: ${camp.auditorRemarks}")
            }
        } else ""

        val share = InformationShare(
            id = shareId,
            recipientEmail = normEmail,
            campId = camp.id,
            campName = camp.name,
            campLocation = if (ShareableSection.CAMP_OVERVIEW.key in allowed) camp.location else "",
            campInstitution = if (ShareableSection.CAMP_OVERVIEW.key in allowed) camp.institution else "",
            participantCount = if (ShareableSection.CAMP_OVERVIEW.key in allowed) camp.participantCount else null,
            allowedSections = allowedSections,
            dateFromMillis = dateFromMillis,
            dateToMillis = dateToMillis,
            expiresAtMillis = expiresAtMillis,
            status = AccessRequestStatus.APPROVED.name,
            revoked = false,
            grantedByEmail = currentEmail(),
            sharedTotalBudget = if (ShareableSection.TOTAL_BUDGET.key in allowed) summary.totalBudget else null,
            sharedTotalIncome = if (ShareableSection.TOTAL_INCOME.key in allowed || ShareableSection.INCOME_EXPENSE_CHARTS.key in allowed) summary.totalFundsReceived else null,
            sharedTotalExpenses = if (ShareableSection.TOTAL_EXPENSES.key in allowed || ShareableSection.INCOME_EXPENSE_CHARTS.key in allowed) summary.totalExpensesCommitted else null,
            sharedRemainingBalance = if (ShareableSection.REMAINING_BALANCE.key in allowed) summary.remainingCashBalance else null,
            sharedCommitteeRows = committeeRows,
            sharedDailyExpenses = dailyRows,
            sharedVerifiedBillsCount = if (ShareableSection.VERIFIED_BILL_SUMMARY.key in allowed) verifiedBills.size else null,
            sharedVerifiedBillsAmount = if (ShareableSection.VERIFIED_BILL_SUMMARY.key in allowed) verifiedBills.sumOf { it.grandTotal ?: 0.0 } else null,
            sharedCaseworkSummaryText = caseworkNote,
            sharedFinalReportText = finalReportText,
            isDemoData = camp.isDemoData
        )

        val batch = db.batch()
        batch.set(db.collection("information_shares").document(shareId), share.toCreateMap())
        if (!linkedRequestId.isNullOrBlank()) {
            batch.update(
                db.collection("access_requests").document(linkedRequestId),
                mapOf(
                    "status" to AccessRequestStatus.APPROVED.name,
                    "reviewedByEmail" to currentEmail(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        writeGlobalSecurityAudit(
            batch = batch,
            action = "GRANT_VIEWER_SHARE",
            entityId = shareId,
            summary = "Shared [${allowedSections.joinToString()}] of '${camp.name}' with $normEmail"
        )
        batch.commit().await()
        return shareId
    }

    suspend fun revokeInformationShare(share: InformationShare, reason: String = "Revoked by Primary Admin") {
        val batch = db.batch()
        batch.update(
            db.collection("information_shares").document(share.id),
            mapOf(
                "status" to AccessRequestStatus.REVOKED.name,
                "revoked" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        writeGlobalSecurityAudit(
            batch = batch,
            action = "REVOKE_VIEWER_SHARE",
            entityId = share.id,
            summary = "Revoked viewer access for ${share.recipientEmail}: $reason"
        )
        batch.commit().await()

        sendAdminNotification(
            type = "ACCESS_REVOKED",
            title = "Viewer Access Revoked",
            message = "Revoked information share for ${share.recipientEmail}.",
            relatedEntityId = share.id
        )
    }

    suspend fun extendInformationShare(share: InformationShare, newExpiryMillis: Long) {
        db.collection("information_shares").document(share.id).update(
            mapOf(
                "expiresAtMillis" to newExpiryMillis,
                "status" to AccessRequestStatus.APPROVED.name,
                "revoked" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun recordViewerLogin(share: InformationShare) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("information_shares").document(share.id).update(
                mapOf(
                    "recipientUid" to uid,
                    "lastLoginAtMillis" to System.currentTimeMillis(),
                    "loginCount" to share.loginCount + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    // --- SMART BILL SCANNER: IMMUTABLE ORIGINALS, EXTRACTIONS & VERSIONS ---
    fun observeBillOriginalsForAdminOrMember(isGlobalReader: Boolean): Flow<List<BillOriginal>> = observeSafeList(
        query = {
            val uid = requireUid()
            if (isGlobalReader) db.collection("bill_originals")
            else db.collection("bill_originals").whereArrayContains("memberUids", uid)
        },
        mapper = { it.toObject(BillOriginal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.uploadedAtMillis } }

    fun observeBillExtractionsForAdminOrMember(isGlobalReader: Boolean): Flow<List<BillExtraction>> = observeSafeList(
        query = {
            val uid = requireUid()
            if (isGlobalReader) db.collection("bill_extractions")
            else db.collection("bill_extractions").whereArrayContains("memberUids", uid)
        },
        mapper = { it.toObject(BillExtraction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeBillVersions(isGlobalReader: Boolean): Flow<List<BillVersion>> = observeSafeList(
        query = {
            val uid = requireUid()
            if (isGlobalReader) db.collection("bill_versions")
            else db.collection("bill_versions").whereArrayContains("memberUids", uid)
        },
        mapper = { it.toObject(BillVersion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    ).map { list -> list.sortedByDescending { it.versionNumber } }

    suspend fun saveScannedBillAndLinkExpense(
        camp: Camp,
        committee: Committee?,
        existingExpense: ExpenseRecord?,
        createNewExpense: Boolean,
        billOriginal: BillOriginal,
        extraction: BillExtraction,
        submitForVerification: Boolean
    ): String {
        val uid = requireUid()
        val billId = billOriginal.id.ifBlank { "bill_${UUID.randomUUID().toString().take(10)}" }
        val verificationId = extraction.billVerificationId.ifBlank {
            "BV-${(1000..9999).random()}-${UUID.randomUUID().toString().take(3).uppercase()}"
        }
        val members = (camp.memberUids + uid).distinct()

        var linkedExpenseId = existingExpense?.id.orEmpty()
        var linkedExpenseCode = existingExpense?.expenseCode.orEmpty()
        var linkedExpenseStatus = existingExpense?.paymentStatus.orEmpty()

        val batch = db.batch()
        val campRef = db.collection("camps").document(camp.id)

        if (createNewExpense) {
            linkedExpenseId = "exp_${UUID.randomUUID().toString().take(10)}"
            linkedExpenseCode = "EXP-${(100..999).random()}-${UUID.randomUUID().toString().take(3).uppercase()}"
            linkedExpenseStatus = if (submitForVerification) {
                ExpensePaymentStatus.SUBMITTED.name
            } else {
                ExpensePaymentStatus.DRAFT.name
            }
            val newExpense = ExpenseRecord(
                id = linkedExpenseId,
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = members,
                expenseCode = linkedExpenseCode,
                dateMillis = System.currentTimeMillis(),
                committeeId = committee?.id ?: "comm_general",
                committeeName = committee?.name ?: extraction.committeeName.ifBlank { "Overall Committee" },
                category = camp.expenseCategories.firstOrNull() ?: "Miscellaneous",
                description = extraction.itemDescription.ifBlank { "Bill ${extraction.billNumber} - ${extraction.vendorName}" },
                vendorName = extraction.vendorName.ifBlank { "Unknown Vendor" },
                amount = (extraction.grandTotal ?: 0.0).coerceAtLeast(0.0),
                paidAmount = 0.0,
                paymentMethod = PaymentMethod.CASH.name,
                paymentStatus = linkedExpenseStatus,
                receiptUri = billOriginal.protectedStorageRef,
                receiptThumbnailBase64 = billOriginal.originalPreviewBase64.take(180_000),
                receiptVerified = false,
                submittedByUid = uid,
                submittedByName = currentName(),
                remarks = "Linked Bill Verification ID: $verificationId",
                isDemoData = camp.isDemoData
            )
            batch.set(campRef.collection("expenses").document(linkedExpenseId), newExpense.toCreateMap())
            batch.set(db.collection("expense_records").document(linkedExpenseId), newExpense.toCreateMap())
        } else if (existingExpense != null) {
            batch.update(
                campRef.collection("expenses").document(existingExpense.id),
                mapOf(
                    "receiptUri" to billOriginal.protectedStorageRef,
                    "receiptThumbnailBase64" to billOriginal.originalPreviewBase64.take(180_000),
                    "remarks" to "${existingExpense.remarks} | Bill $verificationId linked".trim(' ', '|').take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val finalOriginal = billOriginal.copy(
            id = billId,
            billVerificationId = verificationId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = members,
            uploaderUid = uid,
            uploaderName = currentName(),
            uploaderEmail = currentEmail(),
            uploadedAtMillis = System.currentTimeMillis(),
            immutable = true,
            isDemoData = camp.isDemoData
        )
        batch.set(db.collection("bill_originals").document(billId), finalOriginal.toCreateMap())

        val finalStatus = if (submitForVerification) BillVerificationStatus.SUBMITTED.name else BillVerificationStatus.DRAFT.name
        val finalExtraction = extraction.copy(
            id = billId,
            billVerificationId = verificationId,
            billOriginalId = billId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = members,
            committeeId = committee?.id ?: extraction.committeeId,
            committeeName = committee?.name ?: extraction.committeeName,
            expenseId = linkedExpenseId,
            expenseCode = linkedExpenseCode,
            expenseApprovalStatus = linkedExpenseStatus,
            verificationStatus = finalStatus,
            currentVersionNumber = 1,
            submittedByUid = uid,
            submittedByName = currentName(),
            fileHashSha256 = billOriginal.fileHashSha256,
            isDemoData = camp.isDemoData
        )
        batch.set(db.collection("bill_extractions").document(billId), finalExtraction.toCreateMap())

        val v1 = BillVersion(
            id = "ver_${billId}_1",
            billId = billId,
            billVerificationId = verificationId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = members,
            versionNumber = 1,
            changeType = "ORIGINAL_EXTRACTION",
            vendorName = finalExtraction.vendorName,
            billNumber = finalExtraction.billNumber,
            billDate = finalExtraction.billDate,
            grandTotal = finalExtraction.grandTotal,
            explanation = "Initial immutable bill upload & AI extraction",
            replacementFileHash = finalOriginal.fileHashSha256,
            actorUid = uid,
            actorEmail = currentEmail(),
            actorName = currentName(),
            isDemoData = camp.isDemoData
        )
        batch.set(db.collection("bill_versions").document(v1.id), v1.toCreateMap())

        writeGlobalSecurityAudit(
            batch = batch,
            action = "UPLOAD_SCANNED_BILL",
            entityId = billId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = members,
            summary = "Uploaded original bill $verificationId (${finalExtraction.vendorName}, ₹${finalExtraction.grandTotal?.toLong() ?: 0L}) linked to ${linkedExpenseCode.ifBlank { "No Expense" }}"
        )
        batch.commit().await()

        sendAdminNotification(
            type = "BILL_UPLOADED",
            title = "Expense Bill Uploaded ($verificationId)",
            message = "${currentName()} uploaded bill $verificationId (${finalExtraction.vendorName}) for ${camp.name}.",
            relatedEntityId = billId
        )
        return verificationId
    }

    suspend fun adminCorrectOrVerifyBill(
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
        replacementPreviewBase64: String = "",
        replacementFileHash: String = ""
    ) {
        val uid = requireUid()
        val nextVersion = bill.currentVersionNumber + 1
        val verId = "ver_${bill.id}_$nextVersion"
        val batch = db.batch()

        batch.update(
            db.collection("bill_extractions").document(bill.id),
            mapOf(
                "vendorName" to updatedVendor.take(140),
                "billNumber" to updatedBillNumber.take(80),
                "billDate" to updatedBillDate.take(40),
                "gstin" to updatedGstin.take(30),
                "itemDescription" to updatedDescription.take(400),
                "grandTotal" to updatedGrandTotal,
                "officialDescription" to updatedDescription.take(400),
                "officialExplanation" to officialExplanation.take(500),
                "adminAnnotations" to adminAnnotations.take(500),
                "verificationStatus" to newStatus.name,
                "currentVersionNumber" to nextVersion,
                "verifiedByUid" to uid,
                "verifiedByName" to currentName(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val changeType = when {
            replacementPreviewBase64.isNotBlank() -> "VENDOR_REPLACEMENT"
            newStatus == BillVerificationStatus.DISPUTED -> "DISPUTED"
            newStatus == BillVerificationStatus.SUPERSEDED -> "SUPERSEDED"
            newStatus == BillVerificationStatus.VERIFIED -> "VERIFIED"
            newStatus == BillVerificationStatus.REJECTED -> "REJECTED"
            else -> "ADMIN_CORRECTION"
        }

        val versionDoc = BillVersion(
            id = verId,
            billId = bill.id,
            billVerificationId = bill.billVerificationId,
            campId = bill.campId,
            ownerId = bill.ownerId,
            memberUids = bill.memberUids,
            versionNumber = nextVersion,
            changeType = changeType,
            vendorName = updatedVendor,
            billNumber = updatedBillNumber,
            billDate = updatedBillDate,
            grandTotal = updatedGrandTotal,
            explanation = officialExplanation.ifBlank { "Updated by ${currentName()} ($changeType)" },
            replacementFileHash = replacementFileHash,
            replacementPreviewBase64 = replacementPreviewBase64,
            actorUid = uid,
            actorEmail = currentEmail(),
            actorName = currentName(),
            isDemoData = bill.isDemoData
        )
        batch.set(db.collection("bill_versions").document(verId), versionDoc.toCreateMap())

        if (bill.expenseId.isNotBlank() && bill.campId.isNotBlank() && newStatus == BillVerificationStatus.VERIFIED) {
            batch.update(
                db.collection("camps").document(bill.campId).collection("expenses").document(bill.expenseId),
                mapOf(
                    "receiptVerified" to true,
                    "receiptVerifiedBy" to currentName(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        writeGlobalSecurityAudit(
            batch = batch,
            action = "BILL_$changeType",
            entityId = bill.id,
            campId = bill.campId,
            ownerId = bill.ownerId,
            memberUids = bill.memberUids,
            previousValues = "Vendor: ${bill.vendorName}, BillNo: ${bill.billNumber}, Total: ₹${bill.grandTotal?.toLong() ?: 0L}, Status: ${bill.verificationStatus}",
            newValues = "Vendor: $updatedVendor, BillNo: $updatedBillNumber, Total: ₹${updatedGrandTotal?.toLong() ?: 0L}, Status: ${newStatus.name}",
            summary = "Bill ${bill.billVerificationId} $changeType by ${currentName()}: $officialExplanation"
        )
        batch.commit().await()
    }

    private fun writeGlobalSecurityAudit(
        batch: com.google.firebase.firestore.WriteBatch,
        action: String,
        entityId: String,
        summary: String,
        campId: String = "global_security",
        ownerId: String = requireUid(),
        memberUids: List<String> = listOf(requireUid()),
        previousValues: String = "",
        newValues: String = ""
    ) {
        val logId = "aud_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val log = AuditLog(
            id = logId,
            campId = campId,
            ownerId = ownerId,
            memberUids = memberUids,
            entityType = AuditEntityType.CAMP.name, // compatible with both CAMP and global logs
            entityId = entityId,
            entityCode = entityId.take(16).uppercase(),
            action = action,
            summary = summary,
            previousValues = previousValues,
            newValues = newValues,
            actorUid = requireUid(),
            actorName = currentName(),
            actorRole = if (AdminGovernanceConfig.isPrimaryAdminEmail(currentEmail())) "PRIMARY_ADMIN" else "MEMBER",
            isDemoData = false
        )
        batch.set(db.collection("financial_audit_logs").document(logId), log.toCreateMap())
    }
}
