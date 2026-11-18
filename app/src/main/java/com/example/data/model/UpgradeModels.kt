package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object AdminGovernanceConfig {
    const val PRIMARY_ADMIN_EMAIL = "2501721049055@mcc.edu.in"
    const val CO_ADMIN_EMAIL = "2501721049029@mcc.edu.in"
    const val CO_ADMIN_ACTIVE_GRANT_DOC_ID = "coadmin_active_grant"
    const val GLOBAL_ADMIN_SETTINGS_DOC_ID = "global_security"

    fun normalizeEmail(email: String?): String = email.orEmpty().trim().lowercase()

    fun isPrimaryAdminEmail(email: String?): Boolean =
        normalizeEmail(email) == PRIMARY_ADMIN_EMAIL

    fun isCoAdminEmail(email: String?): Boolean =
        normalizeEmail(email) == CO_ADMIN_EMAIL
}

enum class GovernanceAccessTier(val label: String) {
    PRIMARY_ADMIN("Primary Admin"),
    CO_ADMIN_GRANTED("Co-Admin (Delegated)"),
    CO_ADMIN_PENDING("Co-Admin (Awaiting Approval)"),
    CAMP_MEMBER("Camp Committee Member"),
    FACULTY_IN_CHARGE("Faculty In-Charge"),
    INVITED_VIEWER("Invited Viewer"),
    VIEWER_UNAPPROVED("Unapproved / Zero Access")
}

enum class DelegatedModule(val key: String, val label: String) {
    DASHBOARD("DASHBOARD", "Dashboard"),
    INCOME("INCOME", "Income & Contributions"),
    EXPENSES("EXPENSES", "Expense Register"),
    COMMITTEES("COMMITTEES", "Committee Budgets"),
    APPROVALS("APPROVALS", "Approval Centre"),
    BILL_VERIFICATION("BILL_VERIFICATION", "Smart Bill Scanner & Verification"),
    RUPEE_TRAIL("RUPEE_TRAIL", "Rupee Trail & Audit"),
    COST_INTELLIGENCE("COST_INTELLIGENCE", "Camp Cost Intelligence"),
    REPORTS("REPORTS", "Reports & Exports")
}

enum class CoAdminDelegatedRole(val label: String, val defaultModules: List<String>) {
    FINANCE_MANAGER(
        "Finance Manager",
        listOf("DASHBOARD", "INCOME", "EXPENSES", "COMMITTEES", "APPROVALS", "RUPEE_TRAIL", "COST_INTELLIGENCE")
    ),
    BILL_VERIFICATION_ASSISTANT(
        "Bill Verification Assistant",
        listOf("DASHBOARD", "BILL_VERIFICATION", "EXPENSES", "RUPEE_TRAIL")
    ),
    COMMITTEE_MANAGER(
        "Committee Manager",
        listOf("DASHBOARD", "COMMITTEES", "EXPENSES", "COST_INTELLIGENCE")
    ),
    REPORT_PREPARATION_ASSISTANT(
        "Report Preparation Assistant",
        listOf("DASHBOARD", "REPORTS", "COST_INTELLIGENCE", "RUPEE_TRAIL")
    ),
    READ_ONLY_AUDITOR(
        "Read-Only Auditor",
        listOf("DASHBOARD", "INCOME", "EXPENSES", "COMMITTEES", "BILL_VERIFICATION", "RUPEE_TRAIL", "REPORTS")
    );

    companion object {
        fun fromString(value: String?): CoAdminDelegatedRole =
            entries.firstOrNull { it.name == value || it.label == value } ?: READ_ONLY_AUDITOR
    }
}

enum class ShareableSection(val key: String, val label: String) {
    CAMP_OVERVIEW("CAMP_OVERVIEW", "Camp Overview"),
    TOTAL_BUDGET("TOTAL_BUDGET", "Total Budget"),
    TOTAL_INCOME("TOTAL_INCOME", "Total Income"),
    TOTAL_EXPENSES("TOTAL_EXPENSES", "Total Expenses"),
    REMAINING_BALANCE("REMAINING_BALANCE", "Remaining Balance"),
    COMMITTEE_BUDGET("COMMITTEE_BUDGET", "Committee-wise Budget"),
    COMMITTEE_EXPENSES("COMMITTEE_EXPENSES", "Committee-wise Expenses"),
    INCOME_EXPENSE_CHARTS("INCOME_EXPENSE_CHARTS", "Income and Expense Charts"),
    CASEWORK_SUMMARY("CASEWORK_SUMMARY", "Casework Financial Summary"),
    DAILY_EXPENSE_REPORT("DAILY_EXPENSE_REPORT", "Daily Expense Report"),
    VERIFIED_BILL_SUMMARY("VERIFIED_BILL_SUMMARY", "Verified Bill Summary"),
    FINAL_FINANCIAL_REPORT("FINAL_FINANCIAL_REPORT", "Final Financial Report");

    companion object {
        fun labelForKey(key: String): String = entries.firstOrNull { it.key == key }?.label ?: key
    }
}

enum class BillVerificationStatus(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Awaiting Verification"),
    VERIFIED("Verified"),
    DISPUTED("Disputed"),
    REJECTED("Rejected"),
    SUPERSEDED("Superseded");

    companion object {
        fun fromString(value: String?): BillVerificationStatus =
            entries.firstOrNull { it.name == value } ?: SUBMITTED
    }
}

enum class AccessRequestStatus(val label: String) {
    AWAITING_APPROVAL("Awaiting Approval"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    REVOKED("Revoked"),
    EXPIRED("Expired");

    companion object {
        fun fromString(value: String?): AccessRequestStatus =
            entries.firstOrNull { it.name == value } ?: AWAITING_APPROVAL
    }
}

enum class FacultyAccessMode(val label: String) {
    PRESENTATION_ONLY("Presentation Only"),
    DASHBOARD_AND_PRESENTATION("Dashboard & Presentation");

    companion object {
        fun fromString(value: String?): FacultyAccessMode =
            entries.firstOrNull { it.name == value } ?: DASHBOARD_AND_PRESENTATION
    }
}

@IgnoreExtraProperties
data class AdminSettings(
    @DocumentId val id: String = AdminGovernanceConfig.GLOBAL_ADMIN_SETTINGS_DOC_ID,
    val primaryAdminEmail: String = AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL,
    val coAdminEmail: String = AdminGovernanceConfig.CO_ADMIN_EMAIL,
    val coAdminDefaultPending: Boolean = true,
    val viewerZeroAccessDefault: Boolean = true,
    val strictBillOriginalPreservation: Boolean = true,
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "primaryAdminEmail" to primaryAdminEmail,
        "coAdminEmail" to coAdminEmail,
        "coAdminDefaultPending" to coAdminDefaultPending,
        "viewerZeroAccessDefault" to viewerZeroAccessDefault,
        "strictBillOriginalPreservation" to strictBillOriginalPreservation,
        "updatedByUid" to updatedByUid,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class RolePermissionConfig(
    @DocumentId val id: String = "",
    val roleKey: String = "",
    val roleLabel: String = "",
    val allowedModules: List<String> = emptyList(),
    val canRead: Boolean = true,
    val canEdit: Boolean = false,
    val canApprove: Boolean = false,
    val isPrimaryExclusive: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "roleKey" to roleKey,
        "roleLabel" to roleLabel,
        "allowedModules" to allowedModules,
        "canRead" to canRead,
        "canEdit" to canEdit,
        "canApprove" to canApprove,
        "isPrimaryExclusive" to isPrimaryExclusive,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class TemporaryAccessGrant(
    @DocumentId val id: String = "",
    val targetEmail: String = AdminGovernanceConfig.CO_ADMIN_EMAIL,
    val targetUid: String = "",
    val assignedRole: String = CoAdminDelegatedRole.READ_ONLY_AUDITOR.name,
    val assignedRoleLabel: String = CoAdminDelegatedRole.READ_ONLY_AUDITOR.label,
    val allowedModules: List<String> = emptyList(),
    val canRead: Boolean = true,
    val canEdit: Boolean = false,
    val canApprove: Boolean = false,
    val financialLimit: Double = 0.0,
    val startsAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val reason: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, REVOKED, EXPIRED
    val revoked: Boolean = false,
    val revokedReason: String = "",
    val grantedByUid: String = "",
    val grantedByEmail: String = "",
    val lastUsedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        status == "APPROVED" && !revoked && nowMillis >= startsAtMillis && nowMillis < expiresAtMillis

    fun toMap(): Map<String, Any> = mapOf(
        "targetEmail" to targetEmail.trim().lowercase(),
        "targetUid" to targetUid.take(128),
        "assignedRole" to assignedRole,
        "assignedRoleLabel" to assignedRoleLabel.take(100),
        "allowedModules" to allowedModules.take(20),
        "canRead" to canRead,
        "canEdit" to canEdit,
        "canApprove" to canApprove,
        "financialLimit" to financialLimit.coerceAtLeast(0.0),
        "startsAtMillis" to startsAtMillis,
        "expiresAtMillis" to expiresAtMillis,
        "reason" to reason.take(500),
        "status" to status,
        "revoked" to revoked,
        "revokedReason" to revokedReason.take(300),
        "grantedByUid" to grantedByUid.take(128),
        "grantedByEmail" to grantedByEmail.take(150),
        "lastUsedAtMillis" to lastUsedAtMillis,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class LoginEvent(
    @DocumentId val id: String = "",
    val userUid: String = "",
    val email: String = "",
    val displayName: String = "",
    val roleClassification: String = "",
    val eventType: String = "", // PRIMARY_ADMIN_LOGIN, CO_ADMIN_LOGIN_ATTEMPT, CO_ADMIN_LOGIN_SUCCESS, FACULTY_LOGIN, VIEWER_LOGIN, UNAUTHORIZED_ATTEMPT
    val outcome: String = "", // ALLOWED, PENDING_APPROVAL, DENIED, EXPIRED
    val timestampMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userUid" to userUid.take(128),
        "email" to email.trim().lowercase().take(150),
        "displayName" to displayName.take(100),
        "roleClassification" to roleClassification.take(80),
        "eventType" to eventType.take(80),
        "outcome" to outcome.take(60),
        "timestampMillis" to timestampMillis,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AdminNotification(
    @DocumentId val id: String = "",
    val targetEmail: String = AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL,
    val type: String = "", // CO_ADMIN_LOGIN_ATTEMPT, CO_ADMIN_LOGIN_SUCCESS, CO_ADMIN_ACTION, GUEST_ACCESS_REQUEST, FACULTY_REQUEST, BILL_UPLOADED, ADMIN_CORRECTION_REQUESTED, PERMISSION_EXPIRING, REPORT_SUBMITTED, ACCESS_REVOKED
    val title: String = "",
    val message: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val relatedEntityId: String = "",
    val isRead: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "targetEmail" to targetEmail.trim().lowercase().take(150),
        "type" to type.take(80),
        "title" to title.take(140),
        "message" to message.take(500),
        "actorUid" to actorUid.take(128),
        "actorEmail" to actorEmail.trim().lowercase().take(150),
        "relatedEntityId" to relatedEntityId.take(128),
        "isRead" to isRead,
        "createdAtMillis" to createdAtMillis,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AccessRequest(
    @DocumentId val id: String = "",
    val requesterUid: String = "",
    val requesterEmail: String = "",
    val requesterName: String = "",
    val requestType: String = "VIEWER", // VIEWER, FACULTY, CO_ADMIN
    val requestedCampId: String = "",
    val requestedCampName: String = "",
    val reason: String = "",
    val requestedSections: List<String> = emptyList(),
    val status: String = AccessRequestStatus.AWAITING_APPROVAL.name,
    val reviewedByEmail: String = "",
    val reviewNotes: String = "",
    val requestedAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val requestStatus: AccessRequestStatus get() = AccessRequestStatus.fromString(status)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "requesterUid" to requesterUid.take(128),
        "requesterEmail" to requesterEmail.trim().lowercase().take(150),
        "requesterName" to requesterName.take(100),
        "requestType" to requestType.take(40),
        "requestedCampId" to requestedCampId.take(128),
        "requestedCampName" to requestedCampName.take(140),
        "reason" to reason.take(500),
        "requestedSections" to requestedSections.take(20),
        "status" to status,
        "reviewedByEmail" to reviewedByEmail.take(150),
        "reviewNotes" to reviewNotes.take(300),
        "requestedAtMillis" to requestedAtMillis,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class SharedCommitteeSummary(
    val committeeName: String = "",
    val allocatedBudget: Double? = null,
    val amountSpent: Double? = null
)

@IgnoreExtraProperties
data class SharedDailyExpense(
    val dayLabel: String = "",
    val amount: Double = 0.0
)

@IgnoreExtraProperties
data class SharedBillSummaryItem(
    val billVerificationId: String = "",
    val vendorName: String = "",
    val committeeName: String = "",
    val amount: Double = 0.0,
    val verificationStatus: String = ""
)

@IgnoreExtraProperties
data class InformationShare(
    @DocumentId val id: String = "",
    val recipientEmail: String = "",
    val recipientUid: String = "",
    val campId: String = "",
    val campName: String = "",
    val campLocation: String = "",
    val campInstitution: String = "",
    val participantCount: Int? = null,
    val allowedSections: List<String> = emptyList(),
    val dateFromMillis: Long = 0L,
    val dateToMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val status: String = AccessRequestStatus.APPROVED.name, // APPROVED, REVOKED, EXPIRED
    val revoked: Boolean = false,
    val grantedByEmail: String = AdminGovernanceConfig.PRIMARY_ADMIN_EMAIL,
    val lastLoginAtMillis: Long = 0L,
    val loginCount: Int = 0,
    // Strictly scoped pre-projected snapshot fields (populated ONLY for allowedSections!)
    val sharedTotalBudget: Double? = null,
    val sharedTotalIncome: Double? = null,
    val sharedTotalExpenses: Double? = null,
    val sharedRemainingBalance: Double? = null,
    val sharedCommitteeRows: List<Map<String, Any>> = emptyList(),
    val sharedDailyExpenses: List<Map<String, Any>> = emptyList(),
    val sharedVerifiedBillsCount: Int? = null,
    val sharedVerifiedBillsAmount: Double? = null,
    val sharedCaseworkSummaryText: String = "",
    val sharedFinalReportText: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isActiveNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
        status == AccessRequestStatus.APPROVED.name && !revoked && nowMillis < expiresAtMillis

    fun toCreateMap(): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "recipientEmail" to recipientEmail.trim().lowercase().take(150),
            "recipientUid" to recipientUid.take(128),
            "campId" to campId.take(128),
            "campName" to campName.take(140),
            "campLocation" to campLocation.take(150),
            "campInstitution" to campInstitution.take(150),
            "allowedSections" to allowedSections.take(20),
            "dateFromMillis" to dateFromMillis,
            "dateToMillis" to dateToMillis,
            "expiresAtMillis" to expiresAtMillis,
            "status" to status,
            "revoked" to revoked,
            "grantedByEmail" to grantedByEmail.take(150),
            "lastLoginAtMillis" to lastLoginAtMillis,
            "loginCount" to loginCount,
            "isDemoData" to isDemoData,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        participantCount?.let { m["participantCount"] = it }
        sharedTotalBudget?.let { m["sharedTotalBudget"] = it }
        sharedTotalIncome?.let { m["sharedTotalIncome"] = it }
        sharedTotalExpenses?.let { m["sharedTotalExpenses"] = it }
        sharedRemainingBalance?.let { m["sharedRemainingBalance"] = it }
        if (sharedCommitteeRows.isNotEmpty()) m["sharedCommitteeRows"] = sharedCommitteeRows
        if (sharedDailyExpenses.isNotEmpty()) m["sharedDailyExpenses"] = sharedDailyExpenses
        sharedVerifiedBillsCount?.let { m["sharedVerifiedBillsCount"] = it }
        sharedVerifiedBillsAmount?.let { m["sharedVerifiedBillsAmount"] = it }
        if (sharedCaseworkSummaryText.isNotBlank()) m["sharedCaseworkSummaryText"] = sharedCaseworkSummaryText.take(2000)
        if (sharedFinalReportText.isNotBlank()) m["sharedFinalReportText"] = sharedFinalReportText.take(4000)
        return m
    }
}

@IgnoreExtraProperties
data class FacultyProfile(
    @DocumentId val id: String = "", // faculty_01, faculty_02, faculty_03
    val profileSlotName: String = "FACULTY ACCESS 01",
    val assignedEmail: String = "",
    val assignedFacultyName: String = "",
    val campId: String = "",
    val campName: String = "",
    val approvedCaseworkId: String = "",
    val approvedCaseworkName: String = "",
    val permittedInfo: List<String> = listOf(
        "ALLOCATED_BUDGET",
        "TOTAL_INCOME",
        "TOTAL_EXPENSES",
        "REMAINING_BALANCE",
        "CHARTS",
        "DATE_ACTIVITY"
    ),
    val accessMode: String = FacultyAccessMode.DASHBOARD_AND_PRESENTATION.name,
    val isEnabled: Boolean = false,
    val status: String = "DISABLED", // DISABLED, PENDING, APPROVED, REJECTED, REVOKED
    val expiresAtMillis: Long = 0L,
    val approvedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isActiveNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isEnabled && status == "APPROVED" && assignedEmail.isNotBlank() && (expiresAtMillis <= 0L || nowMillis < expiresAtMillis)

    fun toMap(): Map<String, Any> = mapOf(
        "profileSlotName" to profileSlotName.take(60),
        "assignedEmail" to assignedEmail.trim().lowercase().take(150),
        "assignedFacultyName" to assignedFacultyName.take(100),
        "campId" to campId.take(128),
        "campName" to campName.take(140),
        "approvedCaseworkId" to approvedCaseworkId.take(128),
        "approvedCaseworkName" to approvedCaseworkName.take(140),
        "permittedInfo" to permittedInfo.take(20),
        "accessMode" to accessMode,
        "isEnabled" to isEnabled,
        "status" to status,
        "expiresAtMillis" to expiresAtMillis,
        "approvedByEmail" to approvedByEmail.take(150),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val DEFAULT_SLOTS = listOf(
            FacultyProfile(id = "faculty_01", profileSlotName = "FACULTY ACCESS 01"),
            FacultyProfile(id = "faculty_02", profileSlotName = "FACULTY ACCESS 02"),
            FacultyProfile(id = "faculty_03", profileSlotName = "FACULTY ACCESS 03")
        )
    }
}

@IgnoreExtraProperties
data class CaseworkFinance(
    @DocumentId val id: String = "",
    val caseworkId: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val caseworkCode: String = "", // Anonymised e.g. CW-2026-01 (no beneficiary personal PII)
    val programmeName: String = "",
    val facultyProfileId: String = "", // faculty_01 / faculty_02 / faculty_03
    val allocatedBudget: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val incomeCategoryBreakdown: Map<String, Double> = emptyMap(),
    val expenseCategoryBreakdown: Map<String, Double> = emptyMap(),
    val committeeBreakdown: Map<String, Double> = emptyMap(),
    val dailyActivity: Map<String, Double> = emptyMap(),
    val anonymisedSummaryNote: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val remainingBalance: Double get() = (allocatedBudget - totalExpenses)
    val utilizationPct: Double get() = if (allocatedBudget > 0) (totalExpenses / allocatedBudget) * 100.0 else 0.0

    fun toCreateMap(): Map<String, Any> = mapOf(
        "caseworkId" to (caseworkId.ifBlank { id }),
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "caseworkCode" to caseworkCode.take(40),
        "programmeName" to programmeName.take(140),
        "facultyProfileId" to facultyProfileId.take(40),
        "allocatedBudget" to allocatedBudget.coerceAtLeast(0.0),
        "totalIncome" to totalIncome.coerceAtLeast(0.0),
        "totalExpenses" to totalExpenses.coerceAtLeast(0.0),
        "incomeCategoryBreakdown" to incomeCategoryBreakdown,
        "expenseCategoryBreakdown" to expenseCategoryBreakdown,
        "committeeBreakdown" to committeeBreakdown,
        "dailyActivity" to dailyActivity,
        "anonymisedSummaryNote" to anonymisedSummaryNote.take(500),
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class PresentationSession(
    @DocumentId val id: String = "",
    val facultyEmail: String = "",
    val facultyProfileId: String = "",
    val campId: String = "",
    val caseworkId: String = "",
    val startedAtMillis: Long = System.currentTimeMillis(),
    val isFullScreen: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "facultyEmail" to facultyEmail.trim().lowercase().take(150),
        "facultyProfileId" to facultyProfileId.take(40),
        "campId" to campId.take(128),
        "caseworkId" to caseworkId.take(128),
        "startedAtMillis" to startedAtMillis,
        "isFullScreen" to isFullScreen,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class BillOriginal(
    @DocumentId val id: String = "",
    val billVerificationId: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val uploaderUid: String = "",
    val uploaderName: String = "",
    val uploaderEmail: String = "",
    val uploadedAtMillis: Long = 0L,
    val fileHashSha256: String = "",
    val mimeType: String = "image/jpeg",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val protectedStorageRef: String = "",
    val originalPreviewBase64: String = "", // Immutable preserved raster copy of original document
    val immutable: Boolean = true,
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "billVerificationId" to billVerificationId.take(40),
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "uploaderUid" to uploaderUid,
        "uploaderName" to uploaderName.take(100),
        "uploaderEmail" to uploaderEmail.trim().lowercase().take(150),
        "uploadedAtMillis" to uploadedAtMillis,
        "fileHashSha256" to fileHashSha256.take(128),
        "mimeType" to mimeType.take(80),
        "fileName" to fileName.take(160),
        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
        "protectedStorageRef" to protectedStorageRef.take(300),
        "originalPreviewBase64" to originalPreviewBase64.take(350000),
        "immutable" to true,
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class BillExtraction(
    @DocumentId val id: String = "",
    val billVerificationId: String = "",
    val billOriginalId: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val committeeId: String = "",
    val committeeName: String = "",
    val expenseId: String = "",
    val expenseCode: String = "",
    val expenseApprovalStatus: String = "",
    // 12 OCR / AI extracted fields
    val vendorName: String = "",
    val billNumber: String = "",
    val billDate: String = "",
    val gstin: String = "",
    val itemDescription: String = "",
    val quantity: Double? = null,
    val unitPrice: Double? = null,
    val subtotal: Double? = null,
    val gstAmount: Double? = null,
    val discount: Double? = null,
    val grandTotal: Double? = null,
    val paymentInfo: String = "",
    // OCR & anomaly indicators
    val confidenceLevel: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val uncertainFields: List<String> = emptyList(),
    val anomalyWarnings: List<String> = emptyList(),
    val officialDescription: String = "",
    val adminAnnotations: String = "",
    val officialExplanation: String = "",
    val verificationStatus: String = BillVerificationStatus.SUBMITTED.name,
    val currentVersionNumber: Int = 1,
    val submittedByUid: String = "",
    val submittedByName: String = "",
    val verifiedByUid: String = "",
    val verifiedByName: String = "",
    val fileHashSha256: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val status: BillVerificationStatus get() = BillVerificationStatus.fromString(verificationStatus)

    fun toCreateMap(): Map<String, Any?> = mapOf(
        "billVerificationId" to billVerificationId.take(40),
        "billOriginalId" to billOriginalId,
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "committeeId" to committeeId.take(128),
        "committeeName" to committeeName.take(100),
        "expenseId" to expenseId.take(128),
        "expenseCode" to expenseCode.take(40),
        "expenseApprovalStatus" to expenseApprovalStatus.take(40),
        "vendorName" to vendorName.take(140),
        "billNumber" to billNumber.take(80),
        "billDate" to billDate.take(40),
        "gstin" to gstin.take(30),
        "itemDescription" to itemDescription.take(400),
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "subtotal" to subtotal,
        "gstAmount" to gstAmount,
        "discount" to discount,
        "grandTotal" to grandTotal,
        "paymentInfo" to paymentInfo.take(140),
        "confidenceLevel" to confidenceLevel.take(20),
        "uncertainFields" to uncertainFields.take(20),
        "anomalyWarnings" to anomalyWarnings.take(20),
        "officialDescription" to officialDescription.take(400),
        "adminAnnotations" to adminAnnotations.take(500),
        "officialExplanation" to officialExplanation.take(500),
        "verificationStatus" to verificationStatus,
        "currentVersionNumber" to currentVersionNumber,
        "submittedByUid" to submittedByUid,
        "submittedByName" to submittedByName.take(100),
        "verifiedByUid" to verifiedByUid.take(128),
        "verifiedByName" to verifiedByName.take(100),
        "fileHashSha256" to fileHashSha256.take(128),
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class BillVersion(
    @DocumentId val id: String = "",
    val billId: String = "",
    val billVerificationId: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val versionNumber: Int = 1,
    val changeType: String = "ORIGINAL_EXTRACTION", // ORIGINAL_EXTRACTION, ADMIN_CORRECTION, VENDOR_REPLACEMENT, VERIFIED, DISPUTED, REJECTED, SUPERSEDED
    val vendorName: String = "",
    val billNumber: String = "",
    val billDate: String = "",
    val grandTotal: Double? = null,
    val explanation: String = "",
    val replacementFileHash: String = "",
    val replacementPreviewBase64: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val actorName: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any?> = mapOf(
        "billId" to billId,
        "billVerificationId" to billVerificationId.take(40),
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "versionNumber" to versionNumber,
        "changeType" to changeType.take(40),
        "vendorName" to vendorName.take(140),
        "billNumber" to billNumber.take(80),
        "billDate" to billDate.take(40),
        "grandTotal" to grandTotal,
        "explanation" to explanation.take(500),
        "replacementFileHash" to replacementFileHash.take(128),
        "replacementPreviewBase64" to replacementPreviewBase64.take(250000),
        "actorUid" to actorUid,
        "actorEmail" to actorEmail.take(150),
        "actorName" to actorName.take(100),
        "timestampMillis" to timestampMillis,
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
