package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.UserPreferencesStore
import com.example.data.model.AppLanguage
import com.example.data.model.GovernanceAccessTier
import com.example.data.model.ThemeMode
import com.example.data.repository.CampNidhiRepository
import com.example.data.repository.GovernanceAndScannerRepository
import com.example.domain.BillScannerAiService
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.signOutUser
import com.example.ui.navigation.CampNidhiScaffold
import com.example.ui.screens.CoAdminPendingScreen
import com.example.ui.screens.FacultyDashboardAndPresentationScreen
import com.example.ui.screens.InvitedViewerScreen
import com.example.ui.screens.ViewerAccessRequestScreen
import com.example.ui.theme.CampNidhiTheme
import com.example.ui.viewmodel.CampNidhiViewModel
import com.example.ui.viewmodel.GovernanceViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(applicationContext, intent)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current.applicationContext
            val prefs = remember { UserPreferencesStore(context) }
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val language by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.EN)
            val scope = rememberCoroutineScope()

            val currentUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            CampNidhiTheme(themeMode = themeMode) {
                val user = currentUser
                if (user == null) {
                    SignInScreen(
                        language = language,
                        onLanguageChange = { lang ->
                            scope.launch { prefs.setLanguage(lang) }
                        },
                        onAuthSuccess = { /* authStateFlow advances reactively */ }
                    )
                } else {
                    key(user.uid) {
                        AuthenticatedCampNidhiApp(
                            user = user,
                            prefs = prefs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedCampNidhiApp(
    user: FirebaseUser,
    prefs: UserPreferencesStore
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Camp Member"
    val userEmail = user.email.orEmpty()

    val viewModel: CampNidhiViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                CampNidhiViewModel(
                    repository = CampNidhiRepository(appContext),
                    prefs = prefs,
                    currentUserId = user.uid,
                    currentUserName = displayName,
                    currentUserEmail = userEmail,
                    appContext = appContext
                )
            }
        }
    )

    val governanceViewModel: GovernanceViewModel = viewModel(
        key = "gov_${user.uid}",
        factory = viewModelFactory {
            initializer {
                GovernanceViewModel(
                    repo = GovernanceAndScannerRepository(appContext),
                    aiService = BillScannerAiService(),
                    appContext = appContext,
                    currentUserId = user.uid,
                    currentUserName = displayName,
                    currentUserEmail = userEmail
                )
            }
        }
    )

    val accessState by governanceViewModel.accessState.collectAsStateWithLifecycle()
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val viewerAcceptedShareId by governanceViewModel.viewerAcceptedShareId.collectAsStateWithLifecycle()
    val govStatusMessage by governanceViewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(workspaceState) {
        when (val ws = workspaceState) {
            is UiState.Success -> governanceViewModel.updateMemberCampPresence(
                hasCamps = ws.data.allCamps.isNotEmpty(),
                resolved = true
            )
            is UiState.Error -> governanceViewModel.updateMemberCampPresence(
                hasCamps = false,
                resolved = true
            )
            is UiState.Loading -> Unit
        }
    }

    val isGlobalObserver = accessState.tier == GovernanceAccessTier.PRIMARY_ADMIN ||
        accessState.tier == GovernanceAccessTier.CO_ADMIN_GRANTED
    LaunchedEffect(isGlobalObserver) {
        viewModel.setGlobalAdminObserver(isGlobalObserver)
    }

    val doSignOut: () -> Unit = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope
        )
    }

    if (accessState.isResolving) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Verifying access permissions...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    when (accessState.tier) {
        GovernanceAccessTier.PRIMARY_ADMIN,
        GovernanceAccessTier.CO_ADMIN_GRANTED,
        GovernanceAccessTier.CAMP_MEMBER -> {
            CampNidhiScaffold(
                viewModel = viewModel,
                governanceViewModel = governanceViewModel,
                accessState = accessState,
                onSignOut = doSignOut
            )
        }

        GovernanceAccessTier.CO_ADMIN_PENDING -> {
            CoAdminPendingScreen(
                email = userEmail,
                lastGrant = accessState.coAdminGrant,
                onSignOut = doSignOut
            )
        }

        GovernanceAccessTier.FACULTY_IN_CHARGE -> {
            val profile = accessState.facultyProfile
            if (profile != null) {
                FacultyDashboardAndPresentationScreen(
                    profile = profile,
                    casework = accessState.authorizedCasework,
                    onLaunchPresentation = { fullScreen ->
                        governanceViewModel.recordPresentationLaunch(profile, fullScreen)
                    },
                    onSignOut = doSignOut
                )
            } else {
                RestrictedViewerHost(
                    userEmail = userEmail,
                    userName = displayName,
                    myRequests = accessState.myAccessRequests,
                    statusMessage = govStatusMessage,
                    onClearStatus = governanceViewModel::clearStatusMessage,
                    onSubmitRequest = { camp, reason, secs ->
                        governanceViewModel.submitAccessRequest(camp, reason, secs)
                    },
                    onSignOut = doSignOut
                )
            }
        }

        GovernanceAccessTier.INVITED_VIEWER -> {
            val share = accessState.activeShare
            if (share != null) {
                InvitedViewerScreen(
                    share = share,
                    acceptedShareId = viewerAcceptedShareId,
                    onAcceptPopup = governanceViewModel::acceptViewerGrantPopup,
                    onSignOut = doSignOut
                )
            } else {
                RestrictedViewerHost(
                    userEmail = userEmail,
                    userName = displayName,
                    myRequests = accessState.myAccessRequests,
                    statusMessage = govStatusMessage,
                    onClearStatus = governanceViewModel::clearStatusMessage,
                    onSubmitRequest = { camp, reason, secs ->
                        governanceViewModel.submitAccessRequest(camp, reason, secs)
                    },
                    onSignOut = doSignOut
                )
            }
        }

        GovernanceAccessTier.VIEWER_UNAPPROVED -> {
            RestrictedViewerHost(
                userEmail = userEmail,
                userName = displayName,
                myRequests = accessState.myAccessRequests,
                statusMessage = govStatusMessage,
                onClearStatus = governanceViewModel::clearStatusMessage,
                onSubmitRequest = { camp, reason, secs ->
                    governanceViewModel.submitAccessRequest(camp, reason, secs)
                },
                onSignOut = doSignOut
            )
        }
    }
}

@Composable
private fun RestrictedViewerHost(
    userEmail: String,
    userName: String,
    myRequests: List<com.example.data.model.AccessRequest>,
    statusMessage: String?,
    onClearStatus: () -> Unit,
    onSubmitRequest: (String, String, List<String>) -> Unit,
    onSignOut: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearStatus()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        ViewerAccessRequestScreen(
            userEmail = userEmail,
            userName = userName,
            myRequests = myRequests,
            onSubmitRequest = onSubmitRequest,
            onSignOut = onSignOut
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}
