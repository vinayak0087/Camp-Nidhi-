package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AuditEntityType
import com.example.data.model.AuditLog
import com.example.data.model.BudgetTransfer
import com.example.data.model.Camp
import com.example.data.model.CampMember
import com.example.data.model.CampStatus
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomePaymentStatus
import com.example.data.model.IncomeRecord
import com.example.data.model.PaymentMethod
import com.example.data.model.TransferStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

object CampNidhiFirestoreProvider {
    @Volatile
    private var configured = false

    fun getDatabase(context: Context): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder()
                                    .setSizeBytes(50L * 1024L * 1024L)
                                    .build()
                            )
                            .build()
                    } catch (_: IllegalStateException) {
                        // Already configured prior to first call
                    }
                    configured = true
                }
            }
        }
        return db
    }
}

class CampNidhiRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(CampNidhiFirestoreProvider.getDatabase(context))

    private val _hasPendingSync = MutableStateFlow(false)
    val hasPendingSync: StateFlow<Boolean> = _hasPendingSync.asStateFlow()

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required before accessing CampNidhi.")

    private fun currentUserName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Camp Member"

    private fun currentUserEmail(): String = auth.currentUser?.email ?: ""

    private fun <T> observeCollection(
        path: String,
        queryBlock: () -> com.google.firebase.firestore.Query,
        mapper: (DocumentSnapshot) -> T?
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        emitAll(
            queryBlock()
                .snapshots()
                .map { snapshot ->
                    _hasPendingSync.value = snapshot.metadata.hasPendingWrites()
                    snapshot.documents.mapNotNull { mapper(it) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    // --- USER PROFILE ---
    suspend fun ensureUserProfile(
        uid: String = requireUserId(),
        displayName: String = currentUserName(),
        email: String = currentUserEmail()
    ) {
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "Camp Member" },
                    email = email
                )
                docRef.set(profile.toCreateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateUserProfilePreferences(
        languageCode: String? = null,
        themeMode: String? = null,
        activeCampId: String? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        languageCode?.let { updates["preferredLanguage"] = it }
        themeMode?.let { updates["themeMode"] = it }
        activeCampId?.let { updates["activeCampId"] = it }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    // --- CAMPS ---
    fun observeCamps(observeAllForAdmin: Boolean = false): Flow<List<Camp>> = observeCollection(
        path = "camps",
        queryBlock = {
            val uid = requireUserId()
            if (observeAllForAdmin) {
                db.collection("camps")
            } else {
                db.collection("camps").whereArrayContains("memberUids", uid)
            }
        },
        mapper = { doc ->
            doc.toObject(Camp::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    suspend fun createCampWithDefaults(
        camp: Camp,
        createDefaultCommittees: Boolean = true
    ): String {
        val uid = requireUserId()
        val campId = camp.id.ifBlank { "camp_${UUID.randomUUID().toString().take(12)}" }
        val campRef = db.collection("camps").document(campId)
        val memberUids = (camp.memberUids + uid).distinct()
        val normalizedCamp = camp.copy(
            id = campId,
            ownerId = uid,
            memberUids = memberUids,
            financeLeaderName = camp.financeLeaderName.ifBlank { currentUserName() },
            coordinatorName = camp.coordinatorName.ifBlank { currentUserName() }
        )

        val batch = db.batch()
        batch.set(campRef, normalizedCamp.toCreateMap())

        val adminMember = CampMember(
            campId = campId,
            ownerId = uid,
            memberUids = memberUids,
            memberUid = uid,
            displayName = currentUserName(),
            email = currentUserEmail(),
            role = UserRole.SYSTEM_ADMIN.name,
            assignedCommitteeIds = emptyList(),
            isActive = true,
            isDemoData = camp.isDemoData
        )
        batch.set(campRef.collection("members").document(uid), adminMember.toCreateMap())

        if (createDefaultCommittees) {
            Committee.DEFAULT_TEMPLATES.forEachIndexed { idx, tpl ->
                val commId = "comm_${idx}_${UUID.randomUUID().toString().take(6)}"
                val budgetShare = ((camp.approvedBudget * tpl.weightPct) / 100.0).toLong() * 100.0
                val committee = Committee(
                    campId = campId,
                    ownerId = uid,
                    memberUids = memberUids,
                    name = tpl.name,
                    leaderName = if (tpl.name.contains("Finance")) currentUserName() else "",
                    allocatedBudget = budgetShare,
                    colorHex = tpl.colorHex,
                    description = "Default committee allocation for ${tpl.name}",
                    isDefault = true,
                    isDemoData = camp.isDemoData
                )
                batch.set(campRef.collection("committees").document(commId), committee.toCreateMap())
            }
        }

        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = campId,
                ownerId = uid,
                memberUids = memberUids,
                entityType = AuditEntityType.CAMP.name,
                entityId = campId,
                entityCode = campId.take(12).uppercase(),
                action = "CREATE_CAMP",
                summary = "Created camp '${normalizedCamp.name}' with budget ₹${normalizedCamp.approvedBudget.toLong()}",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = UserRole.SYSTEM_ADMIN.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )

        try {
            batch.commit().await()
            return campId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, campRef.path, auth)
            throw e
        }
    }

    suspend fun updateCamp(
        camp: Camp,
        actorRole: UserRole,
        summary: String = "Updated camp settings for ${camp.name}"
    ) {
        val uid = requireUserId()
        require(camp.id.isNotBlank()) { "Camp ID required" }
        val campRef = db.collection("camps").document(camp.id)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        when (actorRole) {
            UserRole.AUDITOR -> {
                updates["auditorRemarks"] = camp.auditorRemarks.take(1000)
            }
            UserRole.TREASURER -> {
                updates["approvedBudget"] = camp.approvedBudget.coerceAtLeast(0.0)
                updates["incomeCategories"] = camp.incomeCategories.take(30)
                updates["expenseCategories"] = camp.expenseCategories.take(30)
                updates["treasurerDeclaration"] = camp.treasurerDeclaration.take(1000)
            }
            else -> {
                updates["name"] = camp.name.take(120)
                updates["institution"] = camp.institution.take(150)
                updates["location"] = camp.location.take(150)
                updates["startDateMillis"] = camp.startDateMillis
                updates["endDateMillis"] = camp.endDateMillis
                updates["participantCount"] = camp.participantCount.coerceIn(0, 100000)
                updates["coordinatorName"] = camp.coordinatorName.take(100)
                updates["financeLeaderName"] = camp.financeLeaderName.take(100)
                updates["approvedBudget"] = camp.approvedBudget.coerceAtLeast(0.0)
                updates["status"] = camp.status
                updates["approvalLimit"] = camp.approvalLimit.coerceAtLeast(0.0)
                updates["requireIndependentApproval"] = camp.requireIndependentApproval
                updates["incomeCategories"] = camp.incomeCategories.take(30)
                updates["expenseCategories"] = camp.expenseCategories.take(30)
                updates["treasurerDeclaration"] = camp.treasurerDeclaration.take(1000)
                updates["auditorRemarks"] = camp.auditorRemarks.take(1000)
                updates["memberUids"] = (camp.memberUids + camp.ownerId).distinct().take(50)
            }
        }
        val batch = db.batch()
        batch.update(campRef, updates)
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.CAMP.name,
                entityId = camp.id,
                entityCode = camp.id.take(10).uppercase(),
                action = "UPDATE_CAMP",
                summary = summary,
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, campRef.path, auth)
            throw e
        }
    }

    suspend fun deleteCamp(camp: Camp) {
        requireUserId()
        val campRef = db.collection("camps").document(camp.id)
        try {
            // Delete subcollections where permitted, then camp document
            val subCollections = listOf("committees", "incomes", "expenses", "budget_transfers", "members")
            for (sub in subCollections) {
                val snap = campRef.collection(sub).whereArrayContains("memberUids", camp.ownerId).get().await()
                if (!snap.isEmpty) {
                    val batch = db.batch()
                    snap.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            if (camp.isDemoData) {
                val logsSnap = campRef.collection("audit_logs").whereArrayContains("memberUids", camp.ownerId).get().await()
                if (!logsSnap.isEmpty) {
                    val batch = db.batch()
                    logsSnap.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            campRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, campRef.path, auth)
            throw e
        }
    }

    // --- MEMBERS ---
    fun observeMembers(campId: String, observeAllForAdmin: Boolean = false): Flow<List<CampMember>> = observeCollection(
        path = "camps/$campId/members",
        queryBlock = {
            val uid = requireUserId()
            val col = db.collection("camps").document(campId).collection("members")
            if (observeAllForAdmin) col else col.whereArrayContains("memberUids", uid)
        },
        mapper = { doc ->
            doc.toObject(CampMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    suspend fun saveMember(camp: Camp, member: CampMember, actorRole: UserRole) {
        val uid = requireUserId()
        val memberId = member.id.ifBlank {
            member.memberUid.takeIf { it.isNotBlank() } ?: "mem_${UUID.randomUUID().toString().take(10)}"
        }
        val campRef = db.collection("camps").document(camp.id)
        val memberRef = campRef.collection("members").document(memberId)

        val updatedCampMemberUids = if (member.memberUid.isNotBlank() && member.isActive) {
            (camp.memberUids + member.memberUid + camp.ownerId).distinct().take(50)
        } else {
            camp.memberUids
        }

        val batch = db.batch()
        if (updatedCampMemberUids != camp.memberUids) {
            batch.update(
                campRef,
                mapOf(
                    "memberUids" to updatedCampMemberUids,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val normalized = member.copy(
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = updatedCampMemberUids,
            isDemoData = camp.isDemoData
        )
        if (member.id.isBlank()) {
            batch.set(memberRef, normalized.toCreateMap())
        } else {
            batch.update(
                memberRef,
                mapOf(
                    "memberUid" to normalized.memberUid.take(128),
                    "memberUids" to normalized.memberUids,
                    "displayName" to normalized.displayName.take(100),
                    "email" to normalized.email.take(150),
                    "role" to normalized.role,
                    "assignedCommitteeIds" to normalized.assignedCommitteeIds.take(25),
                    "isActive" to normalized.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = updatedCampMemberUids,
                entityType = AuditEntityType.MEMBER.name,
                entityId = memberId,
                entityCode = member.role,
                action = if (member.id.isBlank()) "ADD_MEMBER" else "UPDATE_MEMBER",
                summary = "Assigned ${member.displayName} as ${member.role}",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path, auth)
            throw e
        }
    }

    suspend fun deleteMember(camp: Camp, member: CampMember, actorRole: UserRole) {
        val uid = requireUserId()
        require(member.id != camp.ownerId) { "Camp owner cannot be removed." }
        val campRef = db.collection("camps").document(camp.id)
        val memberRef = campRef.collection("members").document(member.id)
        val batch = db.batch()
        batch.delete(memberRef)
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.MEMBER.name,
                entityId = member.id,
                entityCode = member.role,
                action = "REMOVE_MEMBER",
                summary = "Removed member ${member.displayName}",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path, auth)
            throw e
        }
    }

    // --- COMMITTEES & TRANSFERS ---
    fun observeCommittees(campId: String, observeAllForAdmin: Boolean = false): Flow<List<Committee>> = observeCollection(
        path = "camps/$campId/committees",
        queryBlock = {
            val uid = requireUserId()
            val col = db.collection("camps").document(campId).collection("committees")
            if (observeAllForAdmin) col else col.whereArrayContains("memberUids", uid)
        },
        mapper = { doc ->
            doc.toObject(Committee::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    suspend fun saveCommittee(camp: Camp, committee: Committee, actorRole: UserRole): String {
        val uid = requireUserId()
        val isNew = committee.id.isBlank()
        val commId = committee.id.ifBlank { "comm_${UUID.randomUUID().toString().take(10)}" }
        val campRef = db.collection("camps").document(camp.id)
        val commRef = campRef.collection("committees").document(commId)
        val batch = db.batch()
        val normalized = committee.copy(
            id = commId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = camp.memberUids,
            isDemoData = camp.isDemoData
        )
        if (isNew) {
            batch.set(commRef, normalized.toCreateMap())
        } else {
            batch.update(
                commRef,
                mapOf(
                    "memberUids" to camp.memberUids,
                    "name" to normalized.name.take(100),
                    "leaderName" to normalized.leaderName.take(100),
                    "leaderEmail" to normalized.leaderEmail.take(150),
                    "allocatedBudget" to normalized.allocatedBudget.coerceAtLeast(0.0),
                    "colorHex" to normalized.colorHex.take(16),
                    "description" to normalized.description.take(300),
                    "isDefault" to normalized.isDefault,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.COMMITTEE.name,
                entityId = commId,
                entityCode = normalized.name.take(20),
                action = if (isNew) "CREATE_COMMITTEE" else "UPDATE_COMMITTEE",
                summary = "${if (isNew) "Created" else "Updated"} ${normalized.name} (Budget: ₹${normalized.allocatedBudget.toLong()})",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
            return commId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, commRef.path, auth)
            throw e
        }
    }

    suspend fun deleteCommittee(camp: Camp, committee: Committee, actorRole: UserRole) {
        val uid = requireUserId()
        val campRef = db.collection("camps").document(camp.id)
        val commRef = campRef.collection("committees").document(committee.id)
        val batch = db.batch()
        batch.delete(commRef)
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.COMMITTEE.name,
                entityId = committee.id,
                entityCode = committee.name.take(20),
                action = "DELETE_COMMITTEE",
                summary = "Deleted unused committee '${committee.name}'",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commRef.path, auth)
            throw e
        }
    }

    fun observeBudgetTransfers(campId: String, observeAllForAdmin: Boolean = false): Flow<List<BudgetTransfer>> = observeCollection(
        path = "camps/$campId/budget_transfers",
        queryBlock = {
            val uid = requireUserId()
            val col = db.collection("camps").document(campId).collection("budget_transfers")
            if (observeAllForAdmin) col else col.whereArrayContains("memberUids", uid)
        },
        mapper = { doc ->
            doc.toObject(BudgetTransfer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    suspend fun createBudgetTransfer(
        camp: Camp,
        from: Committee,
        to: Committee,
        amount: Double,
        reason: String,
        autoApprove: Boolean,
        actorRole: UserRole
    ) {
        val uid = requireUserId()
        require(from.id != to.id) { "Source and destination committees must be different." }
        require(amount > 0.0) { "Transfer amount must be positive." }
        require(from.allocatedBudget >= amount) { "Source committee does not have enough allocated budget." }

        val transferId = "trf_${UUID.randomUUID().toString().take(10)}"
        val campRef = db.collection("camps").document(camp.id)
        val trfRef = campRef.collection("budget_transfers").document(transferId)
        val status = if (autoApprove && (actorRole == UserRole.SYSTEM_ADMIN || actorRole == UserRole.TREASURER)) {
            TransferStatus.APPROVED.name
        } else {
            TransferStatus.PENDING.name
        }

        val transfer = BudgetTransfer(
            id = transferId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = camp.memberUids,
            fromCommitteeId = from.id,
            fromCommitteeName = from.name,
            toCommitteeId = to.id,
            toCommitteeName = to.name,
            amount = amount,
            reason = reason.ifBlank { "Reallocation of camp budget" },
            status = status,
            requestedByUid = uid,
            requestedByName = currentUserName(),
            approvedByUid = if (status == TransferStatus.APPROVED.name) uid else "",
            approvedByName = if (status == TransferStatus.APPROVED.name) currentUserName() else "",
            isDemoData = camp.isDemoData
        )

        val batch = db.batch()
        batch.set(trfRef, transfer.toCreateMap())
        if (status == TransferStatus.APPROVED.name) {
            batch.update(
                campRef.collection("committees").document(from.id),
                mapOf(
                    "allocatedBudget" to (from.allocatedBudget - amount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                campRef.collection("committees").document(to.id),
                mapOf(
                    "allocatedBudget" to (to.allocatedBudget + amount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.TRANSFER.name,
                entityId = transferId,
                entityCode = transferId.take(10).uppercase(),
                action = "BUDGET_TRANSFER_${status}",
                summary = "Transferred ₹${amount.toLong()} from ${from.name} to ${to.name} ($status)",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, trfRef.path, auth)
            throw e
        }
    }

    suspend fun resolveBudgetTransfer(
        camp: Camp,
        transfer: BudgetTransfer,
        from: Committee?,
        to: Committee?,
        approve: Boolean,
        actorRole: UserRole
    ) {
        val uid = requireUserId()
        val campRef = db.collection("camps").document(camp.id)
        val trfRef = campRef.collection("budget_transfers").document(transfer.id)
        val newStatus = if (approve) TransferStatus.APPROVED.name else TransferStatus.REJECTED.name
        val batch = db.batch()
        batch.update(
            trfRef,
            mapOf(
                "status" to newStatus,
                "approvedByUid" to uid,
                "approvedByName" to currentUserName(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (approve && from != null && to != null) {
            batch.update(
                campRef.collection("committees").document(from.id),
                mapOf(
                    "allocatedBudget" to (from.allocatedBudget - transfer.amount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                campRef.collection("committees").document(to.id),
                mapOf(
                    "allocatedBudget" to (to.allocatedBudget + transfer.amount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.TRANSFER.name,
                entityId = transfer.id,
                entityCode = transfer.id.take(10).uppercase(),
                action = "BUDGET_TRANSFER_${newStatus}",
                summary = "${newStatus}: ₹${transfer.amount.toLong()} (${transfer.fromCommitteeName} -> ${transfer.toCommitteeName})",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, trfRef.path, auth)
            throw e
        }
    }

    // --- INCOMES & CONTRIBUTIONS ---
    fun observeIncomes(campId: String, observeAllForAdmin: Boolean = false): Flow<List<IncomeRecord>> = observeCollection(
        path = "camps/$campId/incomes",
        queryBlock = {
            val uid = requireUserId()
            val col = db.collection("camps").document(campId).collection("incomes")
            if (observeAllForAdmin) col else col.whereArrayContains("memberUids", uid)
        },
        mapper = { doc ->
            doc.toObject(IncomeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    suspend fun saveIncome(camp: Camp, income: IncomeRecord, actorRole: UserRole): String {
        val uid = requireUserId()
        val isNew = income.id.isBlank()
        val incomeId = income.id.ifBlank { "inc_${UUID.randomUUID().toString().take(10)}" }
        val txCode = income.transactionCode.ifBlank {
            "INC-${(100..999).random()}-${UUID.randomUUID().toString().take(3).uppercase()}"
        }
        val derivedStatus = when {
            income.receivedAmount <= 0.0 && income.promisedAmount > 0.0 -> IncomePaymentStatus.PROMISED.name
            income.receivedAmount < income.promisedAmount -> IncomePaymentStatus.PARTIAL.name
            else -> IncomePaymentStatus.PAID.name
        }

        val campRef = db.collection("camps").document(camp.id)
        val incRef = campRef.collection("incomes").document(incomeId)
        val normalized = income.copy(
            id = incomeId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = camp.memberUids,
            transactionCode = txCode,
            paymentStatus = derivedStatus,
            recordedByUid = income.recordedByUid.ifBlank { uid },
            recordedByName = income.recordedByName.ifBlank { currentUserName() },
            isDemoData = camp.isDemoData
        )
        val batch = db.batch()
        if (isNew) {
            batch.set(incRef, normalized.toCreateMap())
        } else {
            batch.update(
                incRef,
                mapOf(
                    "memberUids" to camp.memberUids,
                    "transactionCode" to normalized.transactionCode.take(40),
                    "dateMillis" to normalized.dateMillis,
                    "category" to normalized.category.take(80),
                    "source" to normalized.source.take(120),
                    "contributorName" to normalized.contributorName.take(100),
                    "contributorGroup" to normalized.contributorGroup.take(100),
                    "promisedAmount" to normalized.promisedAmount.coerceAtLeast(0.0),
                    "receivedAmount" to normalized.receivedAmount.coerceAtLeast(0.0),
                    "paymentMethod" to normalized.paymentMethod,
                    "referenceNumber" to normalized.referenceNumber.take(80),
                    "notes" to normalized.notes.take(500),
                    "evidenceUri" to normalized.evidenceUri.take(500),
                    "evidenceBase64" to normalized.evidenceBase64.take(220000),
                    "paymentStatus" to normalized.paymentStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.INCOME.name,
                entityId = incomeId,
                entityCode = txCode,
                action = if (isNew) "CREATE_INCOME" else "UPDATE_INCOME",
                summary = "${if (isNew) "Recorded" else "Updated"} $txCode (${normalized.source}): Received ₹${normalized.receivedAmount.toLong()} / Promised ₹${normalized.promisedAmount.toLong()}",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
            return incomeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, incRef.path, auth)
            throw e
        }
    }

    suspend fun deleteIncome(camp: Camp, income: IncomeRecord, actorRole: UserRole) {
        val uid = requireUserId()
        val campRef = db.collection("camps").document(camp.id)
        val incRef = campRef.collection("incomes").document(income.id)
        val batch = db.batch()
        batch.delete(incRef)
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.INCOME.name,
                entityId = income.id,
                entityCode = income.transactionCode,
                action = "DELETE_INCOME",
                summary = "Deleted income record ${income.transactionCode} (${income.source})",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, incRef.path, auth)
            throw e
        }
    }

    // --- EXPENSES & APPROVALS ---
    fun observeExpenses(campId: String, observeAllForAdmin: Boolean = false): Flow<List<ExpenseRecord>> = observeCollection(
        path = "camps/$campId/expenses",
        queryBlock = {
            val uid = requireUserId()
            val col = db.collection("camps").document(campId).collection("expenses")
            if (observeAllForAdmin) col else col.whereArrayContains("memberUids", uid)
        },
        mapper = { doc ->
            doc.toObject(ExpenseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    suspend fun saveExpense(
        camp: Camp,
        expense: ExpenseRecord,
        existingExpense: ExpenseRecord?,
        actorRole: UserRole
    ): String {
        val uid = requireUserId()
        val isNew = expense.id.isBlank()
        val expId = expense.id.ifBlank { "exp_${UUID.randomUUID().toString().take(10)}" }
        val expCode = expense.expenseCode.ifBlank {
            "EXP-${(100..999).random()}-${UUID.randomUUID().toString().take(3).uppercase()}"
        }
        val campRef = db.collection("camps").document(camp.id)
        val expRef = campRef.collection("expenses").document(expId)

        val normalized = expense.copy(
            id = expId,
            campId = camp.id,
            ownerId = camp.ownerId,
            memberUids = camp.memberUids,
            expenseCode = expCode,
            submittedByUid = expense.submittedByUid.ifBlank { uid },
            submittedByName = expense.submittedByName.ifBlank { currentUserName() },
            isDemoData = camp.isDemoData
        )

        val batch = db.batch()
        if (isNew) {
            batch.set(expRef, normalized.toCreateMap())
        } else {
            val updates = mutableMapOf<String, Any>(
                "dateMillis" to normalized.dateMillis,
                "committeeId" to normalized.committeeId,
                "committeeName" to normalized.committeeName.take(100),
                "category" to normalized.category.take(80),
                "description" to normalized.description.take(300),
                "vendorName" to normalized.vendorName.take(120),
                "amount" to normalized.amount.coerceAtLeast(0.0),
                "paymentMethod" to normalized.paymentMethod,
                "paymentStatus" to normalized.paymentStatus,
                "receiptUri" to normalized.receiptUri.take(500),
                "receiptThumbnailBase64" to normalized.receiptThumbnailBase64.take(220000),
                "remarks" to normalized.remarks.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (actorRole == UserRole.SYSTEM_ADMIN || actorRole == UserRole.TREASURER) {
                updates["sourceIncomeCode"] = normalized.sourceIncomeCode.take(80)
                updates["paidAmount"] = normalized.paidAmount.coerceAtLeast(0.0)
            }
            batch.update(expRef, updates)
        }

        val prevSummary = existingExpense?.let {
            "Amt: ₹${it.amount.toLong()}, Status: ${it.paymentStatus}, Vendor: ${it.vendorName}"
        } ?: ""
        val newSummary = "Amt: ₹${normalized.amount.toLong()}, Status: ${normalized.paymentStatus}, Vendor: ${normalized.vendorName}"

        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.EXPENSE.name,
                entityId = expId,
                entityCode = expCode,
                action = if (isNew) "CREATE_EXPENSE" else "CORRECT_EXPENSE",
                summary = "${if (isNew) "Submitted" else "Corrected"} $expCode (${normalized.committeeName}) ₹${normalized.amount.toLong()}",
                previousValues = prevSummary,
                newValues = newSummary,
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
            return expId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, expRef.path, auth)
            throw e
        }
    }

    suspend fun approveOrRejectExpense(
        camp: Camp,
        expense: ExpenseRecord,
        approve: Boolean,
        remarks: String,
        actorRole: UserRole
    ) {
        val uid = requireUserId()
        if (approve && camp.requireIndependentApproval && expense.submittedByUid == uid && actorRole != UserRole.SYSTEM_ADMIN) {
            throw IllegalStateException("Independent verification policy prevents approving your own submitted expense.")
        }
        val campRef = db.collection("camps").document(camp.id)
        val expRef = campRef.collection("expenses").document(expense.id)
        val newStatus = if (approve) ExpensePaymentStatus.APPROVED.name else ExpensePaymentStatus.REJECTED.name
        val batch = db.batch()
        batch.update(
            expRef,
            mapOf(
                "paymentStatus" to newStatus,
                "approvedByUid" to uid,
                "approvedByName" to currentUserName(),
                "remarks" to remarks.ifBlank { expense.remarks }.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.APPROVAL.name,
                entityId = expense.id,
                entityCode = expense.expenseCode,
                action = if (approve) "APPROVE_EXPENSE" else "REJECT_EXPENSE",
                summary = "${if (approve) "Approved" else "Rejected"} ${expense.expenseCode} (₹${expense.amount.toLong()})",
                previousValues = expense.paymentStatus,
                newValues = newStatus,
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, expRef.path, auth)
            throw e
        }
    }

    suspend fun recordExpensePayment(
        camp: Camp,
        expense: ExpenseRecord,
        paidAmountNow: Double,
        paymentMethod: PaymentMethod,
        paymentReference: String,
        sourceIncomeCode: String,
        actorRole: UserRole
    ) {
        val uid = requireUserId()
        val totalPaid = (expense.paidAmount + paidAmountNow).coerceAtMost(expense.amount)
        val newStatus = if (totalPaid >= expense.amount) {
            ExpensePaymentStatus.PAID.name
        } else {
            ExpensePaymentStatus.PARTIALLY_PAID.name
        }

        val campRef = db.collection("camps").document(camp.id)
        val expRef = campRef.collection("expenses").document(expense.id)
        val batch = db.batch()
        batch.update(
            expRef,
            mapOf(
                "paidAmount" to totalPaid,
                "paymentMethod" to paymentMethod.name,
                "paymentStatus" to newStatus,
                "paidByName" to currentUserName(),
                "paymentReference" to paymentReference.take(80),
                "sourceIncomeCode" to sourceIncomeCode.ifBlank { expense.sourceIncomeCode }.take(80),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.EXPENSE.name,
                entityId = expense.id,
                entityCode = expense.expenseCode,
                action = "RECORD_PAYMENT",
                summary = "Paid ₹${paidAmountNow.toLong()} toward ${expense.expenseCode} via ${paymentMethod.name} (Total paid: ₹${totalPaid.toLong()})",
                previousValues = "${expense.paymentStatus} (Paid ₹${expense.paidAmount.toLong()})",
                newValues = "$newStatus (Paid ₹${totalPaid.toLong()})",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, expRef.path, auth)
            throw e
        }
    }

    suspend fun verifyExpenseReceipt(
        camp: Camp,
        expense: ExpenseRecord,
        verified: Boolean,
        remarks: String,
        actorRole: UserRole
    ) {
        val uid = requireUserId()
        val campRef = db.collection("camps").document(camp.id)
        val expRef = campRef.collection("expenses").document(expense.id)
        val batch = db.batch()
        batch.update(
            expRef,
            mapOf(
                "receiptVerified" to verified,
                "receiptVerifiedBy" to if (verified) currentUserName() else "",
                "remarks" to remarks.ifBlank { expense.remarks }.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.APPROVAL.name,
                entityId = expense.id,
                entityCode = expense.expenseCode,
                action = if (verified) "VERIFY_RECEIPT" else "UNVERIFY_RECEIPT",
                summary = "${if (verified) "Verified" else "Flagged"} receipt evidence for ${expense.expenseCode}",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, expRef.path, auth)
            throw e
        }
    }

    suspend fun deleteExpense(camp: Camp, expense: ExpenseRecord, actorRole: UserRole) {
        val uid = requireUserId()
        val campRef = db.collection("camps").document(camp.id)
        val expRef = campRef.collection("expenses").document(expense.id)
        val batch = db.batch()
        batch.delete(expRef)
        val logRef = campRef.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
        batch.set(
            logRef,
            AuditLog(
                campId = camp.id,
                ownerId = camp.ownerId,
                memberUids = camp.memberUids,
                entityType = AuditEntityType.EXPENSE.name,
                entityId = expense.id,
                entityCode = expense.expenseCode,
                action = "DELETE_EXPENSE",
                summary = "Deleted expense ${expense.expenseCode} (${expense.description})",
                actorUid = uid,
                actorName = currentUserName(),
                actorRole = actorRole.name,
                isDemoData = camp.isDemoData
            ).toCreateMap()
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, expRef.path, auth)
            throw e
        }
    }

    // --- AUDIT LOGS ---
    fun observeAuditLogs(campId: String, observeAllForAdmin: Boolean = false): Flow<List<AuditLog>> = observeCollection(
        path = "camps/$campId/audit_logs",
        queryBlock = {
            val uid = requireUserId()
            val col = db.collection("camps").document(campId).collection("audit_logs")
            if (observeAllForAdmin) col else col.whereArrayContains("memberUids", uid)
        },
        mapper = { doc ->
            doc.toObject(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    )

    // --- EXPLICIT DEMO DATA SEEDER & SAFE RESET ---
    suspend fun createSampleDemoCamp(): String {
        val uid = requireUserId()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val campId = "demo_camp_${UUID.randomUUID().toString().take(8)}"
        val camp = Camp(
            id = campId,
            ownerId = uid,
            memberUids = listOf(uid),
            name = "DEMO DATA: Melur Rural NSS Camp",
            institution = "Madurai Community College NSS",
            location = "Melur Village Panchayat, TN",
            startDateMillis = now - 3 * dayMs,
            endDateMillis = now + 3 * dayMs,
            participantCount = 60,
            coordinatorName = "Dr. Selvakumar R.",
            financeLeaderName = currentUserName(),
            approvedBudget = 120000.0,
            status = CampStatus.ACTIVE.name,
            approvalLimit = 5000.0,
            requireIndependentApproval = false,
            isDemoData = true
        )
        createCampWithDefaults(camp, createDefaultCommittees = false)

        val campRef = db.collection("camps").document(campId)
        val batch = db.batch()
        val committees = listOf(
            Committee(id = "comm_food", campId = campId, ownerId = uid, memberUids = listOf(uid), name = "Food Committee", leaderName = "Anitha K.", allocatedBudget = 42000.0, colorHex = "#1E6B47", isDefault = true, isDemoData = true),
            Committee(id = "comm_travel", campId = campId, ownerId = uid, memberUids = listOf(uid), name = "Travel Committee", leaderName = "Praveen M.", allocatedBudget = 24000.0, colorHex = "#1D4ED8", isDefault = true, isDemoData = true),
            Committee(id = "comm_medical", campId = campId, ownerId = uid, memberUids = listOf(uid), name = "Medical Committee", leaderName = "Dr. Kavya", allocatedBudget = 12000.0, colorHex = "#B91C1C", isDefault = true, isDemoData = true),
            Committee(id = "comm_prog", campId = campId, ownerId = uid, memberUids = listOf(uid), name = "Programme Committee", leaderName = "Surya V.", allocatedBudget = 15000.0, colorHex = "#0D9488", isDefault = true, isDemoData = true),
            Committee(id = "comm_labour", campId = campId, ownerId = uid, memberUids = listOf(uid), name = "Manual Labour Committee", leaderName = "Dinesh T.", allocatedBudget = 12000.0, colorHex = "#65A30D", isDefault = true, isDemoData = true),
            Committee(id = "comm_overall", campId = campId, ownerId = uid, memberUids = listOf(uid), name = "Overall Committee", leaderName = currentUserName(), allocatedBudget = 15000.0, colorHex = "#0F2942", isDefault = true, isDemoData = true)
        )
        committees.forEach { c ->
            batch.set(campRef.collection("committees").document(c.id), c.toCreateMap())
        }

        val incomes = listOf(
            IncomeRecord(
                id = "inc_demo_1", campId = campId, ownerId = uid, memberUids = listOf(uid),
                transactionCode = "INC-DEMO-01", dateMillis = now - 4 * dayMs,
                category = "Student Contributions", source = "60 NSS Volunteers Fee (₹800 each)",
                contributorName = "NSS Batch 2026", contributorGroup = "III Year B.Sc",
                promisedAmount = 48000.0, receivedAmount = 44000.0,
                paymentMethod = PaymentMethod.UPI.name, referenceNumber = "UPI-6029481",
                notes = "55 students paid full, 5 partial pending", paymentStatus = IncomePaymentStatus.PARTIAL.name,
                recordedByUid = uid, recordedByName = currentUserName(), isDemoData = true
            ),
            IncomeRecord(
                id = "inc_demo_2", campId = campId, ownerId = uid, memberUids = listOf(uid),
                transactionCode = "INC-DEMO-02", dateMillis = now - 3 * dayMs,
                category = "Institutional Funding", source = "College NSS Grant",
                contributorName = "Principal's Office", contributorGroup = "Institution",
                promisedAmount = 50000.0, receivedAmount = 50000.0,
                paymentMethod = PaymentMethod.BANK_TRANSFER.name, referenceNumber = "NEFT-993812",
                notes = "Sanctioned camp grant", paymentStatus = IncomePaymentStatus.PAID.name,
                recordedByUid = uid, recordedByName = currentUserName(), isDemoData = true
            ),
            IncomeRecord(
                id = "inc_demo_3", campId = campId, ownerId = uid, memberUids = listOf(uid),
                transactionCode = "INC-DEMO-03", dateMillis = now - 2 * dayMs,
                category = "Sponsorship", source = "Kaveri Agro Traders Sponsorship",
                contributorName = "Mr. Muthuvel", contributorGroup = "Local Sponsor",
                promisedAmount = 20000.0, receivedAmount = 10000.0,
                paymentMethod = PaymentMethod.CASH.name, referenceNumber = "REC-104",
                notes = "Balance ₹10,000 promised on Day 5", paymentStatus = IncomePaymentStatus.PARTIAL.name,
                recordedByUid = uid, recordedByName = currentUserName(), isDemoData = true
            )
        )
        incomes.forEach { inc ->
            batch.set(campRef.collection("incomes").document(inc.id), inc.toCreateMap())
        }

        val expenses = listOf(
            ExpenseRecord(
                id = "exp_demo_1", campId = campId, ownerId = uid, memberUids = listOf(uid),
                expenseCode = "EXP-DEMO-01", dateMillis = now - 3 * dayMs,
                committeeId = "comm_food", committeeName = "Food Committee",
                category = "Groceries & Provisions", description = "Rice, dal, oil, and vegetables for Days 1-3",
                vendorName = "Sri Murugan Wholesale Mart", amount = 26500.0, paidAmount = 26500.0,
                paymentMethod = PaymentMethod.UPI.name, paymentStatus = ExpensePaymentStatus.PAID.name,
                receiptVerified = true, receiptVerifiedBy = "Auditor Demo",
                submittedByUid = uid, submittedByName = "Anitha K.", approvedByUid = uid, approvedByName = currentUserName(),
                paidByName = currentUserName(), paymentReference = "UPI-883421", sourceIncomeCode = "INC-DEMO-02", isDemoData = true
            ),
            ExpenseRecord(
                id = "exp_demo_2", campId = campId, ownerId = uid, memberUids = listOf(uid),
                expenseCode = "EXP-DEMO-02", dateMillis = now - 2 * dayMs,
                committeeId = "comm_travel", committeeName = "Travel Committee",
                category = "Bus & Transport Hire", description = "2 Camp Buses college to Melur village",
                vendorName = "Meenakshi Travels", amount = 22000.0, paidAmount = 12000.0,
                paymentMethod = PaymentMethod.BANK_TRANSFER.name, paymentStatus = ExpensePaymentStatus.PARTIALLY_PAID.name,
                receiptVerified = true, receiptVerifiedBy = "Auditor Demo",
                submittedByUid = uid, submittedByName = "Praveen M.", approvedByUid = uid, approvedByName = currentUserName(),
                paidByName = currentUserName(), paymentReference = "IMPS-44210", sourceIncomeCode = "INC-DEMO-02", isDemoData = true
            ),
            ExpenseRecord(
                id = "exp_demo_3", campId = campId, ownerId = uid, memberUids = listOf(uid),
                expenseCode = "EXP-DEMO-03", dateMillis = now - 1 * dayMs,
                committeeId = "comm_medical", committeeName = "Medical Committee",
                category = "First Aid & Medicines", description = "ORS, bandages, fever & antiseptic kits for village health drive",
                vendorName = "Jan Aushadhi Kendra", amount = 6400.0, paidAmount = 6400.0,
                paymentMethod = PaymentMethod.CASH.name, paymentStatus = ExpensePaymentStatus.PAID.name,
                receiptVerified = false,
                submittedByUid = uid, submittedByName = "Dr. Kavya", approvedByUid = uid, approvedByName = currentUserName(),
                paidByName = currentUserName(), paymentReference = "VOUCH-09", sourceIncomeCode = "INC-DEMO-01", isDemoData = true
            ),
            ExpenseRecord(
                id = "exp_demo_4", campId = campId, ownerId = uid, memberUids = listOf(uid),
                expenseCode = "EXP-DEMO-04", dateMillis = now,
                committeeId = "comm_food", committeeName = "Food Committee",
                category = "Catering & Cooking Fuel", description = "LPG cylinders & evening snacks for community school program",
                vendorName = "Melur Gas Agency", amount = 18500.0, paidAmount = 0.0,
                paymentMethod = PaymentMethod.UPI.name, paymentStatus = ExpensePaymentStatus.PENDING_APPROVAL.name,
                receiptVerified = false,
                submittedByUid = uid, submittedByName = "Anitha K.", sourceIncomeCode = "INC-DEMO-01", isDemoData = true
            )
        )
        expenses.forEach { exp ->
            batch.set(campRef.collection("expenses").document(exp.id), exp.toCreateMap())
        }

        try {
            batch.commit().await()
            return campId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, campRef.path, auth)
            throw e
        }
    }

    suspend fun resetAllDemoCamps(camps: List<Camp>) {
        camps.filter { it.isDemoData }.forEach { demoCamp ->
            deleteCamp(demoCamp)
        }
    }
}
