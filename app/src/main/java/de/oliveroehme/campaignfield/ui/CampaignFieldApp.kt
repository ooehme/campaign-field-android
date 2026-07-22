package de.oliveroehme.campaignfield.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.data.auth.AuthState
import de.oliveroehme.campaignfield.data.sync.SyncRepository
import de.oliveroehme.campaignfield.location.AndroidCurrentLocationRequester
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.location.LocationSource
import de.oliveroehme.campaignfield.location.CompassSource
import de.oliveroehme.campaignfield.map.MapConfiguration
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.network.CoreApiHealthSource
import de.oliveroehme.campaignfield.network.CoreApiStatus
import de.oliveroehme.campaignfield.network.auth.ProfileRemoteDataSource
import de.oliveroehme.campaignfield.ui.navigation.AppDestination
import de.oliveroehme.campaignfield.ui.navigation.CampaignFieldBottomBar
import de.oliveroehme.campaignfield.ui.navigation.CampaignFieldNavHost
import de.oliveroehme.campaignfield.ui.components.FieldGridBackground
import de.oliveroehme.campaignfield.ui.components.FieldHeader
import de.oliveroehme.campaignfield.ui.screens.LoginScreen
import de.oliveroehme.campaignfield.ui.screens.RestoringSessionScreen
import de.oliveroehme.campaignfield.ui.session.SessionViewModel
import de.oliveroehme.campaignfield.ui.status.rememberLocationAccessState
import kotlinx.coroutines.delay

@Composable
fun CampaignFieldApp(
    viewModel: SessionViewModel,
    assignmentRepository: AssignmentRepository,
    profileRemoteDataSource: ProfileRemoteDataSource,
    syncRepository: SyncRepository,
    coreApiHealthSource: CoreApiHealthSource,
    locationSessionState: InMemoryLocationSessionState,
    currentLocationRequester: AndroidCurrentLocationRequester,
    locationSource: LocationSource,
    compassSource: CompassSource,
    networkStateProvider: NetworkStateProvider,
    mapConfiguration: MapConfiguration,
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val loginFeedback by viewModel.loginFeedback.collectAsStateWithLifecycle()
    val isRefreshingProfile by viewModel.isRefreshingProfile.collectAsStateWithLifecycle()

    FieldGridBackground {
        when (val state = authState) {
            is AuthState.Restoring -> RestoringSessionScreen(state.cachedProfile)
            is AuthState.SignedOut,
            AuthState.SigningIn,
            -> LoginScreen(
                isLoading = state is AuthState.SigningIn,
                message = (state as? AuthState.SignedOut)?.message,
                feedback = loginFeedback,
                onInputChanged = viewModel::clearLoginFeedback,
                onLogin = viewModel::signIn,
            )
            is AuthState.SignedIn -> AuthenticatedApp(
                state = state,
                assignmentRepository = assignmentRepository,
                profileRemoteDataSource = profileRemoteDataSource,
                syncRepository = syncRepository,
                coreApiHealthSource = coreApiHealthSource,
                locationSessionState = locationSessionState,
                currentLocationRequester = currentLocationRequester,
                locationSource = locationSource,
                compassSource = compassSource,
                networkStateProvider = networkStateProvider,
                mapConfiguration = mapConfiguration,
                isRefreshingProfile = isRefreshingProfile,
                isLoggingOut = false,
                onRefreshProfile = viewModel::refreshProfile,
                onLogout = viewModel::logout,
            )
            is AuthState.SigningOut -> AuthenticatedApp(
                state = AuthState.SignedIn(state.profile),
                assignmentRepository = assignmentRepository,
                profileRemoteDataSource = profileRemoteDataSource,
                syncRepository = syncRepository,
                coreApiHealthSource = coreApiHealthSource,
                locationSessionState = locationSessionState,
                currentLocationRequester = currentLocationRequester,
                locationSource = locationSource,
                compassSource = compassSource,
                networkStateProvider = networkStateProvider,
                mapConfiguration = mapConfiguration,
                isRefreshingProfile = isRefreshingProfile,
                isLoggingOut = true,
                onRefreshProfile = viewModel::refreshProfile,
                onLogout = viewModel::logout,
            )
        }
    }
}

@Composable
private fun AuthenticatedApp(
    state: AuthState.SignedIn,
    assignmentRepository: AssignmentRepository,
    profileRemoteDataSource: ProfileRemoteDataSource,
    syncRepository: SyncRepository,
    coreApiHealthSource: CoreApiHealthSource,
    locationSessionState: InMemoryLocationSessionState,
    currentLocationRequester: AndroidCurrentLocationRequester,
    locationSource: LocationSource,
    compassSource: CompassSource,
    networkStateProvider: NetworkStateProvider,
    mapConfiguration: MapConfiguration,
    isRefreshingProfile: Boolean,
    isLoggingOut: Boolean,
    onRefreshProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(syncRepository) {
        syncRepository.synchronize()
    }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val locationAccessState = rememberLocationAccessState(
        sessionState = locationSessionState,
        requester = currentLocationRequester,
    )
    val coreApiStatus by produceState(
        initialValue = CoreApiStatus.Checking,
        key1 = coreApiHealthSource,
    ) {
        while (true) {
            value = coreApiHealthSource.check()
            delay(CORE_API_HEALTH_CHECK_INTERVAL_MS)
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            if (route != AppDestination.Map.route) {
                FieldHeader(
                    coreApiStatus = coreApiStatus,
                    locationAccessState = locationAccessState,
                )
            }
        },
        bottomBar = {
            if (route != null) {
                CampaignFieldBottomBar(navController = navController, currentRoute = route)
            }
        },
    ) { innerPadding ->
        CampaignFieldNavHost(
            navController = navController,
            contentPadding = innerPadding,
            profile = state.profile,
            assignmentRepository = assignmentRepository,
            profileRemoteDataSource = profileRemoteDataSource,
            syncRepository = syncRepository,
            mapConfiguration = mapConfiguration,
            locationAccessState = locationAccessState,
            locationSessionState = locationSessionState,
            locationSource = locationSource,
            compassSource = compassSource,
            networkStateProvider = networkStateProvider,
            isRefreshingProfile = isRefreshingProfile,
            isLoggingOut = isLoggingOut,
            onRefreshProfile = onRefreshProfile,
            onLogout = onLogout,
        )
    }
}

private const val CORE_API_HEALTH_CHECK_INTERVAL_MS = 30_000L
