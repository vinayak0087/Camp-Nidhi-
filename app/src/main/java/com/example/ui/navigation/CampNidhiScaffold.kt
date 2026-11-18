package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BillVerificationStatus
import com.example.data.model.CoAdminDelegatedRole
import com.example.data.model.DelegatedModule
import com.example.data.model.GovernanceAccessTier
import com.example.data.model.ThemeMode
import com.example.data.model.UserRole
import com.example.domain.FinanceCalculator
import com.example.localization.AppStrings
import com.example.localization.stringsFor
import com.example.ui.components.StatusPill
import com.example.ui.screens.AdminCommandCentreScreen
import com.example.ui.screens.ApprovalCentreScreen
import com.example.ui.screens.BillVerificationCentreScreen
import com.example.ui.screens.CampFormDialog
import com.example.ui.screens.CampManagementScreen
import com.example.ui.screens.CommitteeBudgetsScreen
import com.example.ui.screens.CostIntelligenceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseRegisterScreen
import com.example.ui.screens.IncomeRegisterScreen
import com.example.ui.screens.ReportsExportsScreen
import com.example.ui.screens.RupeeTrailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartBillScannerScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusPendingContainer
import com.example.ui.theme.WarmGoldBright
import com.example.ui.viewmodel.CampNidhiViewModel
import com.example.ui.viewmodel.CampWorkspaceData
import com.example.ui.viewmodel.GovernanceAccessState
import com.example.ui.viewmodel.GovernanceViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
    val moduleKey: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampNidhiScaffold(
    viewModel: CampNidhiViewModel,
    governanceViewModel: GovernanceViewModel,
    accessState: GovernanceAccessState,
    onSignOut: () -> Unit
) {
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val hasPendingSync by viewModel.hasPendingSync.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val vmIsBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val govStatusMessage by governanceViewModel.statusMessage.collectAsStateWithLifecycle()
    val govIsBusy by governanceViewModel.isBusy.collectAsStateWithLifecycle()
    val adminData by governanceViewModel.adminData.collectAsStateWithLifecycle()
    val billOriginals by governanceViewModel.billOriginals.collectAsStateWithLifecycle()
    val billExtractions by governanceViewModel.billExtractions.collectAsStateWithLifecycle()
    val billVersions by governanceViewModel.billVersions.collectAsStateWithLifecycle()
    val scanQueue by governanceViewModel.scanQueue.collectAsStateWithLifecycle()
    val activeScanIndex by governanceViewModel.activeScanIndex.collectAsStateWithLifecycle()

    val isBusy = vmIsBusy || govIsBusy
    val isPrimaryAdmin = accessState.tier == GovernanceAccessTier.PRIMARY_ADMIN
    val isCoAdmin = accessState.tier == GovernanceAccessTier.CO_ADMIN_GRANTED

    val strings = remember(language) { stringsFor(language) }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentRoute by rememberSaveable { mutableStateOf("dashboard") }
    var showQuickCampDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(govStatusMessage) {
        govStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            governanceViewModel.clearStatusMessage()
        }
    }

    val pendingApprovalCount = (workspaceState as? UiState.Success)?.data?.dashboard?.pendingApprovalCount ?: 0
    val awaitingBillVerifyCount = remember(billExtractions) {
        billExtractions.count { it.status == BillVerificationStatus.SUBMITTED }
    }
    val adminBadgeCount = adminData.unreadNotificationCount + adminData.pendingRequestCount

    val allNavItems = remember(
        strings,
        pendingApprovalCount,
        awaitingBillVerifyCount,
        adminBadgeCount,
        isPrimaryAdmin,
        isCoAdmin,
        accessState.coAdminGrant?.allowedModules
    ) {
        val base = listOf(
            NavDestinationItem("dashboard", strings.navDashboard, Icons.Default.Dashboard, moduleKey = DelegatedModule.DASHBOARD.key),
            NavDestinationItem("smart_bill_scanner", strings.navSmartBillScanner, Icons.Default.DocumentScanner, moduleKey = DelegatedModule.BILL_VERIFICATION.key),
            NavDestinationItem("bill_verification", strings.navBillVerification, Icons.Default.VerifiedUser, awaitingBillVerifyCount, moduleKey = DelegatedModule.BILL_VERIFICATION.key),
            NavDestinationItem("income_register", strings.navIncomeRegister, Icons.Default.Savings, moduleKey = DelegatedModule.INCOME.key),
            NavDestinationItem("expense_register", strings.navExpenseRegister, Icons.Default.ReceiptLong, moduleKey = DelegatedModule.EXPENSES.key),
            NavDestinationItem("committee_budgets", strings.navCommitteeBudgets, Icons.Default.AccountBalanceWallet, moduleKey = DelegatedModule.COMMITTEES.key),
            NavDestinationItem("fund_contributions", strings.navFundContributions, Icons.Default.Groups, moduleKey = DelegatedModule.INCOME.key),
            NavDestinationItem("approval_centre", strings.navApprovalCentre, Icons.Default.Approval, pendingApprovalCount, moduleKey = DelegatedModule.APPROVALS.key),
            NavDestinationItem("rupee_trail", strings.navRupeeTrail, Icons.Default.Route, moduleKey = DelegatedModule.RUPEE_TRAIL.key),
            NavDestinationItem("cost_intelligence", strings.navCostIntelligence, Icons.Default.Analytics, moduleKey = DelegatedModule.COST_INTELLIGENCE.key),
            NavDestinationItem("reports_exports", strings.navReportsExports, Icons.Default.Description, moduleKey = DelegatedModule.REPORTS.key),
            NavDestinationItem("camp_management", strings.navCampManagement, Icons.Default.Event, moduleKey = null),
            NavDestinationItem("user_management", strings.navUserManagement, Icons.Default.ManageAccounts, moduleKey = null),
            NavDestinationItem("admin_command_centre", strings.navAdminCommandCentre, Icons.Default.AdminPanelSettings, adminBadgeCount, moduleKey = "PRIMARY_ONLY"),
            NavDestinationItem("settings", strings.navSettings, Icons.Default.Settings, moduleKey = "ALWAYS")
        )
        base.filter { item ->
            when {
                item.moduleKey == "PRIMARY_ONLY" -> isPrimaryAdmin
                item.moduleKey == "ALWAYS" -> true
                isCoAdmin -> item.moduleKey != null && accessState.coAdminCanAccessModule(item.moduleKey)
                else -> true
            }
        }
    }

    val homeRoute = allNavItems.firstOrNull()?.route ?: "settings"
    LaunchedEffect(allNavItems, currentRoute) {
        if (allNavItems.none { it.route == currentRoute }) {
            currentRoute = homeRoute
        }
    }

    BackHandler(enabled = currentRoute != homeRoute) {
        currentRoute = homeRoute
    }

    val activeTitle = allNavItems.firstOrNull { it.route == currentRoute }?.label ?: strings.appName
    val roleLabelDisplay = when {
        isPrimaryAdmin -> "Primary Admin"
        isCoAdmin -> "Co-Admin • ${accessState.coAdminGrant?.assignedRoleLabel ?: "Delegated"}"
        else -> (workspaceState as? UiState.Success)?.data?.effectiveRole?.displayNameEn ?: UserRole.COMMITTEE_LEADER.displayNameEn
    }

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        val drawerContent: @Composable () -> Unit = {
            CampNidhiDrawerContent(
                strings = strings,
                navItems = allNavItems,
                currentRoute = currentRoute,
                roleLabel = roleLabelDisplay,
                userName = viewModel.currentUserName,
                userEmail = viewModel.currentUserEmail,
                onSelectRoute = { route ->
                    currentRoute = route
                    if (!isWideScreen) {
                        scope.launch { drawerState.close() }
                    }
                }
            )
        }

        val mainContent: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    CampNidhiTopBar(
                        title = activeTitle,
                        strings = strings,
                        workspace = (workspaceState as? UiState.Success)?.data,
                        isWideScreen = isWideScreen,
                        isOnline = isOnline,
                        hasPendingSync = hasPendingSync,
                        isBusy = isBusy,
                        canManageCamps = !isCoAdmin,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onSelectCamp = { viewModel.selectCamp(it) },
                        onOpenCampManager = { currentRoute = "camp_management" }
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        val preferredBottomRoutes = listOf(
                            "dashboard",
                            "smart_bill_scanner",
                            "expense_register",
                            "approval_centre",
                            "admin_command_centre",
                            "income_register",
                            "rupee_trail"
                        )
                        val bottomItems = preferredBottomRoutes
                            .mapNotNull { routeKey -> allNavItems.firstOrNull { it.route == routeKey } }
                            .take(5)
                            .ifEmpty { allNavItems.take(5) }
                        NavigationBar {
                            bottomItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { currentRoute = item.route },
                                    icon = {
                                        if (item.badgeCount > 0) {
                                            BadgedBox(badge = { Badge { Text("${item.badgeCount}") } }) {
                                                Icon(item.icon, contentDescription = item.label)
                                            }
                                        } else {
                                            Icon(item.icon, contentDescription = item.label)
                                        }
                                    },
                                    label = {
                                        Text(
                                            item.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("bottom_nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isCoAdmin && accessState.coAdminGrant != null) {
                        val grant = accessState.coAdminGrant
                        val remMins = ((grant.expiresAtMillis - accessState.nowMillis) / 60_000L).coerceAtLeast(0L)
                        val expStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(grant.expiresAtMillis))
                        Surface(
                            color = StatusInfoContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Temporary Co-Admin (${grant.assignedRoleLabel}) • ${remMins}m left (ends $expStr)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusInfoBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                val permTags = buildList {
                                    if (grant.canRead) add("Read")
                                    if (grant.canEdit) add("Edit")
                                    if (grant.canApprove) add("Approve")
                                    if (grant.financialLimit > 0.0) add("Limit ${FinanceCalculator.formatCompactInr(grant.financialLimit)}")
                                }.joinToString(" • ")
                                Text(
                                    text = permTags,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusInfoBlue
                                )
                            }
                        }
                    }

                    if (!isOnline || hasPendingSync) {
                        Surface(
                            color = StatusPendingContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (!isOnline) Icons.Default.CloudOff else Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = StatusPendingAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = strings.offlineQueuedBanner,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusPendingAmber
                                )
                            }
                        }
                    }

                    val data = (workspaceState as? UiState.Success)?.data
                    if (data != null && data.isRolePreviewActive && !isCoAdmin) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Previewing as ${data.effectiveRole.displayNameEn}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                TextButton(onClick = { viewModel.setRolePreview(null) }) {
                                    Text("Exit Preview")
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 1120.dp)
                        ) {
                            when (val state = workspaceState) {
                                is UiState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }

                                is UiState.Error -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        if (!isCoAdmin) {
                                            Spacer(Modifier.height(12.dp))
                                            Button(onClick = { currentRoute = "camp_management" }) {
                                                Text("Open Camp Management")
                                            }
                                        }
                                    }
                                }

                                is UiState.Success -> {
                                    val workspace = state.data
                                    val scopedWorkspace = if (isCoAdmin) {
                                        val grant = accessState.coAdminGrant
                                        val mappedRole = when {
                                            grant == null -> UserRole.AUDITOR
                                            grant.canApprove || (grant.canEdit && grant.assignedRole == CoAdminDelegatedRole.FINANCE_MANAGER.name) -> UserRole.TREASURER
                                            grant.canEdit -> UserRole.COMMITTEE_LEADER
                                            else -> UserRole.AUDITOR
                                        }
                                        workspace.copy(
                                            actualRole = mappedRole,
                                            effectiveRole = mappedRole,
                                            isRolePreviewActive = false
                                        )
                                    } else {
                                        workspace
                                    }

                                    CampNidhiScreenContent(
                                        route = currentRoute,
                                        workspace = scopedWorkspace,
                                        strings = strings,
                                        language = language,
                                        themeMode = themeMode,
                                        isPrimaryAdmin = isPrimaryAdmin,
                                        isCoAdmin = isCoAdmin,
                                        accessState = accessState,
                                        viewModel = viewModel,
                                        governanceViewModel = governanceViewModel,
                                        adminData = adminData,
                                        billOriginals = billOriginals,
                                        billExtractions = billExtractions,
                                        billVersions = billVersions,
                                        scanQueue = scanQueue,
                                        activeScanIndex = activeScanIndex,
                                        onNavigateRoute = { currentRoute = it },
                                        onQuickCreateCamp = {
                                            if (!isCoAdmin) showQuickCampDialog = true
                                        },
                                        onSignOut = onSignOut
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(272.dp)) {
                        drawerContent()
                    }
                },
                content = mainContent
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(288.dp)) {
                        drawerContent()
                    }
                },
                content = mainContent
            )
        }
    }

    if (showQuickCampDialog && !isCoAdmin) {
        CampFormDialog(
            initial = null,
            onSave = { camp ->
                viewModel.createCamp(camp, createDefaultCommittees = true)
                showQuickCampDialog = false
            },
            onDismiss = { showQuickCampDialog = false }
        )
    }
}

@Composable
private fun CampNidhiScreenContent(
    route: String,
    workspace: CampWorkspaceData,
    strings: AppStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    isPrimaryAdmin: Boolean,
    isCoAdmin: Boolean,
    accessState: GovernanceAccessState,
    viewModel: CampNidhiViewModel,
    governanceViewModel: GovernanceViewModel,
    adminData: com.example.ui.viewmodel.AdminCommandCentreData,
    billOriginals: List<com.example.data.model.BillOriginal>,
    billExtractions: List<com.example.data.model.BillExtraction>,
    billVersions: List<com.example.data.model.BillVersion>,
    scanQueue: List<com.example.ui.viewmodel.ScannedBillDraft>,
    activeScanIndex: Int,
    onNavigateRoute: (String) -> Unit,
    onQuickCreateCamp: () -> Unit,
    onSignOut: () -> Unit
) {
    val financialLimit = accessState.coAdminGrant?.financialLimit ?: 0.0
    val guardEdit: (() -> Unit) -> Unit = { action ->
        if (!isCoAdmin || accessState.coAdminCanEdit) {
            action()
        } else {
            viewModel.showMessage("Co-Admin grant does not include edit permission.")
        }
    }
    val guardApprove: (Double, () -> Unit) -> Unit = { amount, action ->
        if (isCoAdmin && !accessState.coAdminCanApprove) {
            viewModel.showMessage("Co-Admin grant does not include approval permission.")
        } else if (isCoAdmin && financialLimit > 0.0 && amount > financialLimit) {
            viewModel.showMessage("Amount exceeds Co-Admin approval limit (${FinanceCalculator.formatInr(financialLimit)}).")
        } else {
            action()
        }
    }

    when (route) {
        "dashboard" -> DashboardScreen(
            workspace = workspace,
            strings = strings,
            onNavigateRoute = onNavigateRoute,
            onQuickCreateCamp = onQuickCreateCamp,
            onLoadDemoCamp = { if (!isCoAdmin) viewModel.createSampleDemoCamp() }
        )

        "smart_bill_scanner" -> SmartBillScannerScreen(
            workspace = workspace,
            scanQueue = scanQueue,
            activeScanIndex = activeScanIndex,
            onEnqueueUris = { uris ->
                guardEdit {
                    governanceViewModel.enqueueScannedUris(uris, workspace.activeCamp, workspace.expenses)
                }
            },
            onSelectQueueIndex = governanceViewModel::selectScanIndex,
            onUpdateActiveDraft = { transform ->
                governanceViewModel.updateActiveDraft(workspace.activeCamp, workspace.expenses, transform)
            },
            onDiscardDraft = governanceViewModel::discardActiveDraft,
            onSaveScannedBill = { committee, existingExpense, createNew, submitForVerif ->
                guardEdit {
                    val camp = workspace.activeCamp
                    if (camp != null) {
                        governanceViewModel.saveActiveScannedBill(
                            camp = camp,
                            committee = committee,
                            existingExpense = existingExpense,
                            createNewExpense = createNew,
                            submitForVerification = submitForVerif
                        )
                    }
                }
            },
            onOpenVerificationCentre = { onNavigateRoute("bill_verification") }
        )

        "bill_verification" -> BillVerificationCentreScreen(
            workspace = workspace,
            billOriginals = billOriginals,
            billExtractions = billExtractions,
            billVersions = billVersions,
            isPrimaryAdmin = isPrimaryAdmin,
            onOpenScanner = { onNavigateRoute("smart_bill_scanner") },
            onAdminUpdateBill = governanceViewModel::adminVerifyOrCorrectBill
        )

        "admin_command_centre" -> if (isPrimaryAdmin) {
            AdminCommandCentreScreen(
                workspace = workspace,
                adminData = adminData,
                billExtractions = billExtractions,
                onMarkNotificationRead = governanceViewModel::markNotificationRead,
                onGrantCoAdmin = governanceViewModel::grantCoAdminAccess,
                onRevokeCoAdmin = governanceViewModel::revokeCoAdminAccess,
                onSaveFacultyProfile = governanceViewModel::saveFacultyProfile,
                onSaveCasework = governanceViewModel::saveCaseworkFinance,
                onApproveShare = governanceViewModel::approveInformationShare,
                onRejectRequest = governanceViewModel::rejectAccessRequest,
                onRevokeShare = governanceViewModel::revokeInformationShare,
                onExtendShare = governanceViewModel::extendInformationShare,
                onOpenVerificationCentre = { onNavigateRoute("bill_verification") }
            )
        }

        "income_register" -> IncomeRegisterScreen(
            workspace = workspace,
            strings = strings,
            contributionsOnly = false,
            onSaveIncome = { camp, income, role ->
                guardEdit { viewModel.saveIncome(camp, income, role) }
            },
            onDeleteIncome = { camp, income, role ->
                guardEdit { viewModel.deleteIncome(camp, income, role) }
            },
            onImportCsv = { camp, rows, role ->
                guardEdit { viewModel.importCsvRows(camp, rows, role) }
            },
            findDuplicate = viewModel::findDuplicateIncome
        )

        "expense_register" -> ExpenseRegisterScreen(
            workspace = workspace,
            strings = strings,
            onSaveExpense = { camp, expense, existingExpense, role ->
                guardEdit { viewModel.saveExpense(camp, expense, existingExpense, role) }
            },
            onDeleteExpense = { camp, expense, role ->
                guardEdit { viewModel.deleteExpense(camp, expense, role) }
            },
            findDuplicate = viewModel::findDuplicateExpense
        )

        "committee_budgets" -> CommitteeBudgetsScreen(
            workspace = workspace,
            strings = strings,
            onSaveCommittee = { camp, committee, role ->
                guardEdit { viewModel.saveCommittee(camp, committee, role) }
            },
            onDeleteCommittee = { camp, committee, role ->
                guardEdit { viewModel.deleteCommittee(camp, committee, role) }
            },
            onCreateTransfer = { camp, from, to, amt, reason, autoApprove, role ->
                guardEdit { viewModel.createBudgetTransfer(camp, from, to, amt, reason, autoApprove, role) }
            }
        )

        "fund_contributions" -> IncomeRegisterScreen(
            workspace = workspace,
            strings = strings,
            contributionsOnly = true,
            onSaveIncome = { camp, income, role ->
                guardEdit { viewModel.saveIncome(camp, income, role) }
            },
            onDeleteIncome = { camp, income, role ->
                guardEdit { viewModel.deleteIncome(camp, income, role) }
            },
            onImportCsv = { camp, rows, role ->
                guardEdit { viewModel.importCsvRows(camp, rows, role) }
            },
            findDuplicate = viewModel::findDuplicateIncome
        )

        "approval_centre" -> ApprovalCentreScreen(
            workspace = workspace,
            currentUserId = viewModel.currentUserId,
            strings = strings,
            onApproveOrRejectExpense = { camp, exp, approve, remarks, role ->
                guardApprove(exp.amount) { viewModel.approveOrRejectExpense(camp, exp, approve, remarks, role) }
            },
            onRecordPayment = { camp, exp, amt, method, ref, sourceCode, role ->
                guardEdit { viewModel.recordExpensePayment(camp, exp, amt, method, ref, sourceCode, role) }
            },
            onVerifyReceipt = { camp, exp, verified, remarks, role ->
                guardEdit { viewModel.verifyExpenseReceipt(camp, exp, verified, remarks, role) }
            },
            onResolveTransfer = { camp, tr, from, to, approve, role ->
                guardApprove(tr.amount) { viewModel.resolveBudgetTransfer(camp, tr, from, to, approve, role) }
            }
        )

        "rupee_trail" -> RupeeTrailScreen(
            workspace = workspace,
            strings = strings
        )

        "cost_intelligence" -> CostIntelligenceScreen(
            workspace = workspace,
            strings = strings
        )

        "reports_exports" -> ReportsExportsScreen(
            workspace = workspace,
            strings = strings,
            onUpdateCampStatementFields = { camp, role, summary ->
                guardEdit { viewModel.updateCamp(camp, role, summary) }
            },
            onMessage = viewModel::showMessage
        )

        "camp_management" -> CampManagementScreen(
            workspace = workspace,
            strings = strings,
            onSelectCamp = { campId ->
                viewModel.selectCamp(campId)
                onNavigateRoute("dashboard")
            },
            onCreateCamp = { camp -> viewModel.createCamp(camp, createDefaultCommittees = true) },
            onUpdateCamp = { camp, role -> viewModel.updateCamp(camp, role) },
            onDeleteCamp = viewModel::deleteCamp,
            onCreateDemoCamp = { viewModel.createSampleDemoCamp() },
            onResetDemoData = viewModel::resetDemoData
        )

        "user_management" -> UserManagementScreen(
            workspace = workspace,
            strings = strings,
            onSaveMember = viewModel::saveMember,
            onDeleteMember = viewModel::deleteMember
        )

        "settings" -> SettingsScreen(
            workspace = workspace,
            strings = strings,
            language = language,
            themeMode = themeMode,
            currentUserName = viewModel.currentUserName,
            currentUserEmail = viewModel.currentUserEmail,
            currentUserId = viewModel.currentUserId,
            onLanguageChange = viewModel::setLanguage,
            onThemeModeChange = viewModel::setThemeMode,
            onUpdateCampSettings = { camp, role, summary ->
                if (!isCoAdmin) viewModel.updateCamp(camp, role, summary)
            },
            onRolePreviewChange = { if (isPrimaryAdmin || !isCoAdmin) viewModel.setRolePreview(it) },
            onSignOut = onSignOut
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampNidhiTopBar(
    title: String,
    strings: AppStrings,
    workspace: CampWorkspaceData?,
    isWideScreen: Boolean,
    isOnline: Boolean,
    hasPendingSync: Boolean,
    isBusy: Boolean,
    canManageCamps: Boolean,
    onOpenMenu: () -> Unit,
    onSelectCamp: (String) -> Unit,
    onOpenCampManager: () -> Unit
) {
    var campMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            if (!isWideScreen) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.testTag("drawer_menu_button")
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu")
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val activeCamp = workspace?.activeCamp
                if (activeCamp != null) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { campMenuExpanded = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = activeCamp.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Camp",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = campMenuExpanded,
                            onDismissRequest = { campMenuExpanded = false }
                        ) {
                            workspace.allCamps.forEach { camp ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (camp.isDemoData) "${camp.name} [DEMO]" else camp.name
                                        )
                                    },
                                    onClick = {
                                        campMenuExpanded = false
                                        onSelectCamp(camp.id)
                                    }
                                )
                            }
                            if (canManageCamps) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(strings.navCampManagement) },
                                    onClick = {
                                        campMenuExpanded = false
                                        onOpenCampManager()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp),
                    strokeWidth = 2.dp
                )
            }
            if (workspace?.activeCamp?.isDemoData == true) {
                StatusPill(
                    text = "DEMO",
                    containerColor = WarmGoldBright,
                    contentColor = Color(0xFF2B1E00),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            StatusPill(
                text = if (isOnline && !hasPendingSync) "Synced" else "Queued",
                containerColor = if (isOnline && !hasPendingSync) ForestGreenContainer else StatusPendingContainer,
                contentColor = if (isOnline && !hasPendingSync) ForestGreenSecondary else StatusPendingAmber,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
    )
}

@Composable
private fun CampNidhiDrawerContent(
    strings: AppStrings,
    navItems: List<NavDestinationItem>,
    currentRoute: String,
    roleLabel: String,
    userName: String,
    userEmail: String,
    onSelectRoute: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = strings.appName,
                    tint = WarmGoldBright,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Text(
            text = "$userName • $userEmail",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        navItems.forEach { item ->
            NavigationDrawerItem(
                selected = currentRoute == item.route,
                onClick = { onSelectRoute(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    Text(
                        item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                badge = if (item.badgeCount > 0) {
                    {
                        Badge { Text("${item.badgeCount}") }
                    }
                } else null,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_nav_${item.route}")
            )
        }
    }
}
