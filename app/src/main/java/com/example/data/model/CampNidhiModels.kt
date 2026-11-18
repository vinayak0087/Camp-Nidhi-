package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val displayNameEn: String, val displayNameTa: String) {
    SYSTEM_ADMIN("System Admin", "கணினி நிர்வாகி"),
    TREASURER("Finance / Treasurer", "பொருளாளர் / நிதிக்குழு"),
    COMMITTEE_LEADER("Committee Leader", "குழுத் தலைவர்"),
    AUDITOR("Auditor / Reviewer", "தணிக்கையாளர்");

    companion object {
        fun fromString(value: String?): UserRole =
            entries.firstOrNull { it.name == value } ?: COMMITTEE_LEADER
    }
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class AppLanguage(val code: String, val label: String) {
    EN("en", "English"),
    TA("ta", "தமிழ்");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: EN
    }
}

enum class CampStatus(val labelEn: String, val labelTa: String) {
    PLANNING("Planning", "திட்டமிடல்"),
    ACTIVE("Active", "செயலில்"),
    ARCHIVED("Archived", "காப்பகம்");

    companion object {
        fun fromString(value: String?): CampStatus =
            entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

enum class PaymentMethod(val labelEn: String, val labelTa: String) {
    CASH("Cash", "ரொக்கம்"),
    UPI("UPI", "யுபிஐ (UPI)"),
    BANK_TRANSFER("Bank Transfer", "வங்கி பரிமாற்றம்"),
    OTHER("Other", "மற்றவை");

    companion object {
        fun fromString(value: String?): PaymentMethod =
            entries.firstOrNull { it.name == value } ?: CASH
    }
}

enum class IncomePaymentStatus(val labelEn: String, val labelTa: String) {
    PAID("Received", "பெறப்பட்டது"),
    PARTIAL("Partial", "பகுதி பெறப்பட்டது"),
    PROMISED("Promised", "வாக்குறுதி");

    companion object {
        fun fromString(value: String?): IncomePaymentStatus =
            entries.firstOrNull { it.name == value } ?: PAID
    }
}

enum class ExpensePaymentStatus(val labelEn: String, val labelTa: String) {
    DRAFT("Draft", "வரைவு"),
    SUBMITTED("Submitted", "சமர்ப்பிக்கப்பட்டது"),
    PENDING_APPROVAL("Pending Approval", "ஒப்புதல் நிலுவை"),
    APPROVED("Approved", "ஒப்புதல் பெற்றது"),
    REJECTED("Rejected", "நிராகரிக்கப்பட்டது"),
    PARTIALLY_PAID("Partially Paid", "பகுதி செலுத்தப்பட்டது"),
    PAID("Paid", "செலுத்தப்பட்டது");

    companion object {
        fun fromString(value: String?): ExpensePaymentStatus =
            entries.firstOrNull { it.name == value } ?: SUBMITTED
    }
}

enum class TransferStatus(val labelEn: String, val labelTa: String) {
    PENDING("Pending", "நிலுவை"),
    APPROVED("Approved", "ஒப்புதல்"),
    REJECTED("Rejected", "நிராகரிக்கப்பட்டது");

    companion object {
        fun fromString(value: String?): TransferStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

enum class AuditEntityType {
    CAMP, COMMITTEE, INCOME, EXPENSE, TRANSFER, MEMBER, APPROVAL;

    companion object {
        fun fromString(value: String?): AuditEntityType =
            entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val preferredLanguage: String = "en",
    val themeMode: String = "SYSTEM",
    val activeCampId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.ifBlank { "Camp Member" }.take(100),
        "email" to email.take(150),
        "preferredLanguage" to preferredLanguage,
        "themeMode" to themeMode,
        "activeCampId" to activeCampId.take(128),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class Camp(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val institution: String = "",
    val location: String = "",
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val participantCount: Int = 0,
    val coordinatorName: String = "",
    val financeLeaderName: String = "",
    val approvedBudget: Double = 0.0,
    val status: String = CampStatus.ACTIVE.name,
    val approvalLimit: Double = 5000.0,
    val requireIndependentApproval: Boolean = true,
    val isDemoData: Boolean = false,
    val incomeCategories: List<String> = DEFAULT_INCOME_CATEGORIES,
    val expenseCategories: List<String> = DEFAULT_EXPENSE_CATEGORIES,
    val treasurerDeclaration: String = "I hereby declare that all receipts, payments, and committee expenditures recorded in CampNidhi are true and supported by verified vouchers.",
    val auditorRemarks: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val campStatus: CampStatus get() = CampStatus.fromString(status)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "name" to name.take(120),
        "institution" to institution.take(150),
        "location" to location.take(150),
        "startDateMillis" to startDateMillis,
        "endDateMillis" to endDateMillis,
        "participantCount" to participantCount.coerceIn(0, 100000),
        "coordinatorName" to coordinatorName.take(100),
        "financeLeaderName" to financeLeaderName.take(100),
        "approvedBudget" to approvedBudget.coerceAtLeast(0.0),
        "status" to status,
        "approvalLimit" to approvalLimit.coerceAtLeast(0.0),
        "requireIndependentApproval" to requireIndependentApproval,
        "isDemoData" to isDemoData,
        "incomeCategories" to incomeCategories.take(30),
        "expenseCategories" to expenseCategories.take(30),
        "treasurerDeclaration" to treasurerDeclaration.take(1000),
        "auditorRemarks" to auditorRemarks.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val DEFAULT_INCOME_CATEGORIES = listOf(
            "Student Contributions",
            "Staff Contributions",
            "Sponsorship",
            "Donations",
            "Institutional Funding",
            "Fundraising",
            "Other Income"
        )

        val DEFAULT_EXPENSE_CATEGORIES = listOf(
            "Groceries & Provisions",
            "Catering & Cooking Fuel",
            "Bus & Transport Hire",
            "First Aid & Medicines",
            "Printing & Stationery",
            "Stage, Sound & Media",
            "Sports Equipment",
            "Tools & Manual Labour",
            "Accommodation & Utilities",
            "Miscellaneous"
        )
    }
}

@IgnoreExtraProperties
data class CampMember(
    @DocumentId val id: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val memberUid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = UserRole.COMMITTEE_LEADER.name,
    val assignedCommitteeIds: List<String> = emptyList(),
    val isActive: Boolean = true,
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val userRole: UserRole get() = UserRole.fromString(role)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "memberUid" to memberUid.take(128),
        "displayName" to displayName.take(100),
        "email" to email.take(150),
        "role" to role,
        "assignedCommitteeIds" to assignedCommitteeIds.take(25),
        "isActive" to isActive,
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class Committee(
    @DocumentId val id: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val leaderName: String = "",
    val leaderEmail: String = "",
    val allocatedBudget: Double = 0.0,
    val colorHex: String = "#0F2942",
    val description: String = "",
    val isDefault: Boolean = false,
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "name" to name.take(100),
        "leaderName" to leaderName.take(100),
        "leaderEmail" to leaderEmail.take(150),
        "allocatedBudget" to allocatedBudget.coerceAtLeast(0.0),
        "colorHex" to colorHex.take(16),
        "description" to description.take(300),
        "isDefault" to isDefault,
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        data class DefaultCommitteeTemplate(val name: String, val colorHex: String, val weightPct: Double)

        val DEFAULT_TEMPLATES = listOf(
            DefaultCommitteeTemplate("Overall Committee", "#0F2942", 0.10),
            DefaultCommitteeTemplate("Food Committee", "#1E6B47", 0.32),
            DefaultCommitteeTemplate("Travel Committee", "#1D4ED8", 0.18),
            DefaultCommitteeTemplate("Medical Committee", "#B91C1C", 0.08),
            DefaultCommitteeTemplate("Finance Committee", "#B5851D", 0.03),
            DefaultCommitteeTemplate("Media Committee", "#7C3AED", 0.05),
            DefaultCommitteeTemplate("Cultural Committee", "#DB2777", 0.06),
            DefaultCommitteeTemplate("Programme Committee", "#0D9488", 0.06),
            DefaultCommitteeTemplate("Sports Committee", "#EA580C", 0.04),
            DefaultCommitteeTemplate("Manual Labour Committee", "#65A30D", 0.05),
            DefaultCommitteeTemplate("Other Committees", "#475569", 0.03)
        )
    }
}

@IgnoreExtraProperties
data class IncomeRecord(
    @DocumentId val id: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val transactionCode: String = "",
    val dateMillis: Long = 0L,
    val category: String = "",
    val source: String = "",
    val contributorName: String = "",
    val contributorGroup: String = "",
    val promisedAmount: Double = 0.0,
    val receivedAmount: Double = 0.0,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val referenceNumber: String = "",
    val notes: String = "",
    val evidenceUri: String = "",
    val evidenceBase64: String = "",
    val paymentStatus: String = IncomePaymentStatus.PAID.name,
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val method: PaymentMethod get() = PaymentMethod.fromString(paymentMethod)
    val status: IncomePaymentStatus get() = IncomePaymentStatus.fromString(paymentStatus)
    val outstandingAmount: Double get() = (promisedAmount - receivedAmount).coerceAtLeast(0.0)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "transactionCode" to transactionCode.take(40),
        "dateMillis" to dateMillis,
        "category" to category.take(80),
        "source" to source.take(120),
        "contributorName" to contributorName.take(100),
        "contributorGroup" to contributorGroup.take(100),
        "promisedAmount" to promisedAmount.coerceAtLeast(0.0),
        "receivedAmount" to receivedAmount.coerceAtLeast(0.0),
        "paymentMethod" to paymentMethod,
        "referenceNumber" to referenceNumber.take(80),
        "notes" to notes.take(500),
        "evidenceUri" to evidenceUri.take(500),
        "evidenceBase64" to evidenceBase64.take(220000),
        "paymentStatus" to paymentStatus,
        "recordedByUid" to recordedByUid,
        "recordedByName" to recordedByName.take(100),
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ExpenseRecord(
    @DocumentId val id: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val expenseCode: String = "",
    val dateMillis: Long = 0L,
    val committeeId: String = "",
    val committeeName: String = "",
    val category: String = "",
    val description: String = "",
    val vendorName: String = "",
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val paymentStatus: String = ExpensePaymentStatus.SUBMITTED.name,
    val receiptUri: String = "",
    val receiptThumbnailBase64: String = "",
    val receiptVerified: Boolean = false,
    val receiptVerifiedBy: String = "",
    val submittedByUid: String = "",
    val submittedByName: String = "",
    val approvedByUid: String = "",
    val approvedByName: String = "",
    val paidByName: String = "",
    val paymentReference: String = "",
    val remarks: String = "",
    val sourceIncomeCode: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val method: PaymentMethod get() = PaymentMethod.fromString(paymentMethod)
    val status: ExpensePaymentStatus get() = ExpensePaymentStatus.fromString(paymentStatus)
    val balanceDue: Double get() = (amount - paidAmount).coerceAtLeast(0.0)
    val hasReceiptEvidence: Boolean get() = receiptThumbnailBase64.isNotBlank() || receiptUri.isNotBlank()

    fun toCreateMap(): Map<String, Any> = mapOf(
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "expenseCode" to expenseCode.take(40),
        "dateMillis" to dateMillis,
        "committeeId" to committeeId,
        "committeeName" to committeeName.take(100),
        "category" to category.take(80),
        "description" to description.take(300),
        "vendorName" to vendorName.take(120),
        "amount" to amount.coerceAtLeast(0.0),
        "paidAmount" to paidAmount.coerceAtLeast(0.0),
        "paymentMethod" to paymentMethod,
        "paymentStatus" to paymentStatus,
        "receiptUri" to receiptUri.take(500),
        "receiptThumbnailBase64" to receiptThumbnailBase64.take(220000),
        "receiptVerified" to receiptVerified,
        "receiptVerifiedBy" to receiptVerifiedBy.take(100),
        "submittedByUid" to submittedByUid,
        "submittedByName" to submittedByName.take(100),
        "approvedByUid" to approvedByUid.take(128),
        "approvedByName" to approvedByName.take(100),
        "paidByName" to paidByName.take(100),
        "paymentReference" to paymentReference.take(80),
        "remarks" to remarks.take(500),
        "sourceIncomeCode" to sourceIncomeCode.take(80),
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class BudgetTransfer(
    @DocumentId val id: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val fromCommitteeId: String = "",
    val fromCommitteeName: String = "",
    val toCommitteeId: String = "",
    val toCommitteeName: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val status: String = TransferStatus.PENDING.name,
    val requestedByUid: String = "",
    val requestedByName: String = "",
    val approvedByUid: String = "",
    val approvedByName: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val transferStatus: TransferStatus get() = TransferStatus.fromString(status)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "fromCommitteeId" to fromCommitteeId,
        "fromCommitteeName" to fromCommitteeName.take(100),
        "toCommitteeId" to toCommitteeId,
        "toCommitteeName" to toCommitteeName.take(100),
        "amount" to amount.coerceAtLeast(0.01),
        "reason" to reason.take(300),
        "status" to status,
        "requestedByUid" to requestedByUid,
        "requestedByName" to requestedByName.take(100),
        "approvedByUid" to approvedByUid.take(128),
        "approvedByName" to approvedByName.take(100),
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val campId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val entityType: String = AuditEntityType.EXPENSE.name,
    val entityId: String = "",
    val entityCode: String = "",
    val action: String = "",
    val summary: String = "",
    val previousValues: String = "",
    val newValues: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val isDemoData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val type: AuditEntityType get() = AuditEntityType.fromString(entityType)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "campId" to campId,
        "ownerId" to ownerId,
        "memberUids" to memberUids.distinct().take(50),
        "entityType" to entityType,
        "entityId" to entityId.take(128),
        "entityCode" to entityCode.take(40),
        "action" to action.take(60),
        "summary" to summary.take(500),
        "previousValues" to previousValues.take(1000),
        "newValues" to newValues.take(1000),
        "actorUid" to actorUid,
        "actorName" to actorName.take(100),
        "actorRole" to actorRole.take(40),
        "isDemoData" to isDemoData,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
