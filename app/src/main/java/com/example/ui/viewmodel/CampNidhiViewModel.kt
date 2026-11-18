package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesStore
import com.example.data.model.AppLanguage
import com.example.data.model.AuditLog
import com.example.data.model.BudgetTransfer
import com.example.data.model.Camp
import com.example.data.model.CampMember
import com.example.data.model.CampStatus
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomeRecord
import com.example.data.model.PaymentMethod
import com.example.data.model.ThemeMode
import com.example.data.model.UserRole
import com.example.data.repository.CampNidhiRepository
import com.example.data.repository.toReadableMessage
import com.example.domain.CostIntelligenceReport
import com.example.domain.DashboardSummary
import com.example.domain.FinanceCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class CampWorkspaceData(
    val activeCamp: Camp?,
    val allCamps: List<Camp>,
    val committees: List<Committee>,
    val incomes: List<IncomeRecord>,
    val expenses: List<ExpenseRecord>,
    val transfers: List<BudgetTransfer>,
    val members: List<CampMember>,
    val auditLogs: List<AuditLog>,
    val dashboard: DashboardSummary,
    val costIntelligence: CostIntelligenceReport,
    val actualRole: UserRole,
    val effectiveRole: UserRole,
    val isRolePreviewActive: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class CampNidhiViewModel(
    private val repository: CampNidhiRepository,
    private val prefs: UserPreferencesStore,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    appContext: Context
) : ViewModel() {

    private val _selectedCampId = MutableStateFlow("")
    private val _previewRoleOverride = MutableStateFlow<UserRole?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline(appContext))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val hasPendingSync: StateFlow<Boolean> = repository.hasPendingSync

    val language: StateFlow<AppLanguage> = prefs.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.EN)

    val themeMode: StateFlow<ThemeMode> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemeMode.SYSTEM)

    init {
        viewModelScope.launch {
            try {
                repository.ensureUserProfile(currentUserId, currentUserName, currentUserEmail)
            } catch (e: Exception) {
                Log.w(TAG, "Profile ensure skipped/queued: ${e.message}")
            }
        }
        viewModelScope.launch {
            prefs.activeCampIdFlow.collect { savedId ->
                if (_selectedCampId.value.isBlank() && savedId.isNotBlank()) {
                    _selectedCampId.value = savedId
                }
            }
        }
        observeNetworkConnectivity(appContext)
    }

    private fun <T> Flow<List<T>>.catchFirestoreError(label: String): Flow<List<T>> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error on $label: ${root.message}", root)
        _snackbarMessage.value = root.toReadableMessage("Failed to sync $label")
        emit(emptyList())
    }

    private val _isGlobalAdminObserver = MutableStateFlow(
        com.example.data.model.AdminGovernanceConfig.isPrimaryAdminEmail(currentUserEmail)
    )

    fun setGlobalAdminObserver(enabled: Boolean) {
        if (_isGlobalAdminObserver.value != enabled) {
            _isGlobalAdminObserver.value = enabled
        }
    }

    private val campsFlow: Flow<List<Camp>> = _isGlobalAdminObserver.flatMapLatest { isGlobal ->
        repository.observeCamps(observeAllForAdmin = isGlobal)
            .catchFirestoreError("camps")
    }

    private val activeCampFlow: Flow<Camp?> = combine(campsFlow, _selectedCampId) { camps, selectedId ->
        camps.firstOrNull { it.id == selectedId }
            ?: camps.firstOrNull { it.campStatus == CampStatus.ACTIVE }
            ?: camps.firstOrNull()
    }

    val workspaceState: StateFlow<UiState<CampWorkspaceData>> = combine(
        campsFlow,
        activeCampFlow,
        _previewRoleOverride,
        _isGlobalAdminObserver
    ) { camps, activeCamp, previewRole, isGlobal ->
        WorkspaceQueryParams(camps, activeCamp, previewRole, isGlobal)
    }.flatMapLatest { (camps, activeCamp, previewRole, isGlobal) ->
        if (activeCamp == null) {
            val emptySummary = FinanceCalculator.computeDashboard(null, emptyList(), emptyList(), emptyList())
            val emptyCost = FinanceCalculator.computeCostIntelligence(null, emptySummary, emptyList())
            flowOf<UiState<CampWorkspaceData>>(
                UiState.Success(
                    CampWorkspaceData(
                        activeCamp = null,
                        allCamps = camps,
                        committees = emptyList(),
                        incomes = emptyList(),
                        expenses = emptyList(),
                        transfers = emptyList(),
                        members = emptyList(),
                        auditLogs = emptyList(),
                        dashboard = emptySummary,
                        costIntelligence = emptyCost,
                        actualRole = UserRole.SYSTEM_ADMIN,
                        effectiveRole = UserRole.SYSTEM_ADMIN,
                        isRolePreviewActive = false
                    )
                )
            )
        } else {
            val campId = activeCamp.id
            val baseStreams = combine(
                repository.observeCommittees(campId, observeAllForAdmin = isGlobal).catchFirestoreError("committees"),
                repository.observeIncomes(campId, observeAllForAdmin = isGlobal).catchFirestoreError("incomes"),
                repository.observeExpenses(campId, observeAllForAdmin = isGlobal).catchFirestoreError("expenses"),
                repository.observeBudgetTransfers(campId, observeAllForAdmin = isGlobal).catchFirestoreError("transfers"),
                repository.observeMembers(campId, observeAllForAdmin = isGlobal).catchFirestoreError("members")
            ) { committees, incomes, expenses, transfers, members ->
                FiveStreams(committees, incomes, expenses, transfers, members)
            }
            combine(
                baseStreams,
                repository.observeAuditLogs(campId, observeAllForAdmin = isGlobal).catchFirestoreError("audit_logs")
            ) { five, logs ->
                val actualRole = when {
                    com.example.data.model.AdminGovernanceConfig.isPrimaryAdminEmail(currentUserEmail) -> UserRole.SYSTEM_ADMIN
                    activeCamp.ownerId == currentUserId -> UserRole.SYSTEM_ADMIN
                    else -> five.members.firstOrNull { it.memberUid == currentUserId }?.userRole
                        ?: UserRole.COMMITTEE_LEADER
                }
                val effectiveRole = if (actualRole == UserRole.SYSTEM_ADMIN && previewRole != null) {
                    previewRole
                } else {
                    actualRole
                }
                val sortedIncomes = five.incomes.sortedByDescending { it.dateMillis }
                val sortedExpenses = five.expenses.sortedByDescending { it.dateMillis }
                val sortedLogs = logs.sortedByDescending { it.createdAt?.seconds ?: 0L }
                val dashboard = FinanceCalculator.computeDashboard(
                    camp = activeCamp,
                    committees = five.committees.sortedBy { it.name },
                    incomes = sortedIncomes,
                    expenses = sortedExpenses
                )
                val costIntel = FinanceCalculator.computeCostIntelligence(
                    camp = activeCamp,
                    summary = dashboard,
                    expenses = sortedExpenses
                )
                UiState.Success<CampWorkspaceData>(
                    CampWorkspaceData(
                        activeCamp = activeCamp,
                        allCamps = camps.sortedByDescending { it.startDateMillis },
                        committees = five.committees.sortedBy { it.name },
                        incomes = sortedIncomes,
                        expenses = sortedExpenses,
                        transfers = five.transfers.sortedByDescending { it.createdAt?.seconds ?: 0L },
                        members = five.members.sortedBy { it.displayName },
                        auditLogs = sortedLogs,
                        dashboard = dashboard,
                        costIntelligence = costIntel,
                        actualRole = actualRole,
                        effectiveRole = effectiveRole,
                        isRolePreviewActive = actualRole == UserRole.SYSTEM_ADMIN && previewRole != null && previewRole != actualRole
                    )
                )
            }
        }
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.e(TAG, "Fatal workspace stream error", root)
        emit(UiState.Error(root.toReadableMessage("Unable to load camp workspace")))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    private data class FiveStreams(
        val committees: List<Committee>,
        val incomes: List<IncomeRecord>,
        val expenses: List<ExpenseRecord>,
        val transfers: List<BudgetTransfer>,
        val members: List<CampMember>
    )

    private data class WorkspaceQueryParams(
        val camps: List<Camp>,
        val activeCamp: Camp?,
        val previewRole: UserRole?,
        val isGlobal: Boolean
    )

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun selectCamp(campId: String) {
        _selectedCampId.value = campId
        _previewRoleOverride.value = null
        viewModelScope.launch { prefs.setActiveCampId(campId) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            prefs.setLanguage(lang)
            try {
                repository.updateUserProfilePreferences(languageCode = lang.code)
            } catch (_: Exception) {
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
            try {
                repository.updateUserProfilePreferences(themeMode = mode.name)
            } catch (_: Exception) {
            }
        }
    }

    fun setRolePreview(role: UserRole?) {
        _previewRoleOverride.value = role
    }

    private fun launchMutation(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                block()
                _snackbarMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.toReadableMessage("Action failed")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // --- CAMP ACTIONS ---
    fun createCamp(camp: Camp, createDefaultCommittees: Boolean = true) {
        launchMutation("Camp '${camp.name}' created") {
            val id = repository.createCampWithDefaults(camp, createDefaultCommittees)
            _selectedCampId.value = id
            prefs.setActiveCampId(id)
        }
    }

    fun updateCamp(camp: Camp, role: UserRole, summary: String = "Updated camp details") {
        launchMutation("Camp updated") {
            repository.updateCamp(camp, role, summary)
        }
    }

    fun deleteCamp(camp: Camp) {
        launchMutation("Camp '${camp.name}' deleted") {
            repository.deleteCamp(camp)
            if (_selectedCampId.value == camp.id) {
                _selectedCampId.value = ""
            }
        }
    }

    fun createSampleDemoCamp() {
        launchMutation("Sample Demo Camp created (labeled DEMO DATA)") {
            val id = repository.createSampleDemoCamp()
            _selectedCampId.value = id
            prefs.setActiveCampId(id)
        }
    }

    fun resetDemoData(camps: List<Camp>) {
        launchMutation("All demo camps cleared safely") {
            repository.resetAllDemoCamps(camps)
            _selectedCampId.value = ""
        }
    }

    // --- COMMITTEE ACTIONS ---
    fun saveCommittee(camp: Camp, committee: Committee, role: UserRole) {
        launchMutation("Saved committee '${committee.name}'") {
            repository.saveCommittee(camp, committee, role)
        }
    }

    fun deleteCommittee(camp: Camp, committee: Committee, role: UserRole) {
        launchMutation("Deleted committee '${committee.name}'") {
            repository.deleteCommittee(camp, committee, role)
        }
    }

    fun createBudgetTransfer(
        camp: Camp,
        from: Committee,
        to: Committee,
        amount: Double,
        reason: String,
        autoApprove: Boolean,
        role: UserRole
    ) {
        launchMutation(if (autoApprove) "Budget transfer completed" else "Transfer request submitted for approval") {
            repository.createBudgetTransfer(camp, from, to, amount, reason, autoApprove, role)
        }
    }

    fun resolveBudgetTransfer(
        camp: Camp,
        transfer: BudgetTransfer,
        from: Committee?,
        to: Committee?,
        approve: Boolean,
        role: UserRole
    ) {
        launchMutation(if (approve) "Budget transfer approved" else "Budget transfer rejected") {
            repository.resolveBudgetTransfer(camp, transfer, from, to, approve, role)
        }
    }

    // --- INCOME ACTIONS ---
    fun saveIncome(camp: Camp, income: IncomeRecord, role: UserRole) {
        launchMutation("Income entry saved") {
            repository.saveIncome(camp, income, role)
        }
    }

    fun deleteIncome(camp: Camp, income: IncomeRecord, role: UserRole) {
        launchMutation("Income entry deleted") {
            repository.deleteIncome(camp, income, role)
        }
    }

    fun findDuplicateIncome(existing: List<IncomeRecord>, amount: Double, source: String, excludeId: String): IncomeRecord? {
        return existing.firstOrNull {
            it.id != excludeId &&
                kotlin.math.abs(it.promisedAmount - amount) < 0.01 &&
                it.source.equals(source.trim(), ignoreCase = true)
        }
    }

    // --- EXPENSE ACTIONS ---
    fun saveExpense(camp: Camp, expense: ExpenseRecord, existingExpense: ExpenseRecord?, role: UserRole) {
        launchMutation("Expense ${if (existingExpense == null) "recorded" else "updated"}") {
            repository.saveExpense(camp, expense, existingExpense, role)
        }
    }

    fun approveOrRejectExpense(camp: Camp, expense: ExpenseRecord, approve: Boolean, remarks: String, role: UserRole) {
        launchMutation(if (approve) "${expense.expenseCode} approved" else "${expense.expenseCode} rejected") {
            repository.approveOrRejectExpense(camp, expense, approve, remarks, role)
        }
    }

    fun recordExpensePayment(
        camp: Camp,
        expense: ExpenseRecord,
        amountNow: Double,
        method: PaymentMethod,
        ref: String,
        sourceCode: String,
        role: UserRole
    ) {
        launchMutation("Payment recorded for ${expense.expenseCode}") {
            repository.recordExpensePayment(camp, expense, amountNow, method, ref, sourceCode, role)
        }
    }

    fun verifyExpenseReceipt(camp: Camp, expense: ExpenseRecord, verified: Boolean, remarks: String, role: UserRole) {
        launchMutation(if (verified) "Receipt verified for ${expense.expenseCode}" else "Receipt flagged for ${expense.expenseCode}") {
            repository.verifyExpenseReceipt(camp, expense, verified, remarks, role)
        }
    }

    fun deleteExpense(camp: Camp, expense: ExpenseRecord, role: UserRole) {
        launchMutation("Expense ${expense.expenseCode} deleted") {
            repository.deleteExpense(camp, expense, role)
        }
    }

    fun findDuplicateExpense(existing: List<ExpenseRecord>, amount: Double, vendor: String, excludeId: String): ExpenseRecord? {
        return existing.firstOrNull {
            it.id != excludeId &&
                kotlin.math.abs(it.amount - amount) < 0.01 &&
                it.vendorName.equals(vendor.trim(), ignoreCase = true)
        }
    }

    // --- MEMBERS ---
    fun saveMember(camp: Camp, member: CampMember, role: UserRole) {
        launchMutation("Member '${member.displayName}' saved") {
            repository.saveMember(camp, member, role)
        }
    }

    fun deleteMember(camp: Camp, member: CampMember, role: UserRole) {
        launchMutation("Member removed") {
            repository.deleteMember(camp, member, role)
        }
    }

    // --- CSV IMPORT ---
    fun importCsvRows(camp: Camp, rows: List<List<String>>, role: UserRole) {
        if (rows.isEmpty()) {
            showMessage("CSV file is empty")
            return
        }
        launchMutation("Imported CSV rows into ${camp.name}") {
            var imported = 0
            val dataRows = if (rows.firstOrNull()?.firstOrNull()?.equals("TYPE", ignoreCase = true) == true) {
                rows.drop(1)
            } else rows

            for (cols in dataRows) {
                if (cols.size < 6) continue
                val type = cols[0].uppercase()
                val amount = cols[5].toDoubleOrNull() ?: continue
                if (type == "INCOME") {
                    repository.saveIncome(
                        camp,
                        IncomeRecord(
                            transactionCode = cols.getOrNull(1).orEmpty(),
                            dateMillis = System.currentTimeMillis(),
                            category = cols.getOrNull(3).orEmpty().ifBlank { "Donations" },
                            source = cols.getOrNull(4).orEmpty().ifBlank { "CSV Import" },
                            promisedAmount = amount,
                            receivedAmount = cols.getOrNull(6)?.toDoubleOrNull() ?: amount,
                            paymentMethod = PaymentMethod.fromString(cols.getOrNull(7)).name,
                            referenceNumber = cols.getOrNull(9).orEmpty(),
                            notes = cols.getOrNull(11).orEmpty()
                        ),
                        role
                    )
                    imported++
                }
            }
            if (imported == 0) {
                throw IllegalArgumentException("No valid INCOME rows found. Expected format: TYPE,CODE,DATE,CATEGORY,SOURCE,PROMISED,RECEIVED,METHOD")
            }
        }
    }

    private fun checkCurrentlyOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeNetworkConnectivity(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = checkCurrentlyOnline(context)
            }
        }
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "CampNidhiVM"
    }
}
