package de.oliveroehme.campaignfield.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.data.sync.SyncRepository
import de.oliveroehme.campaignfield.ui.screens.AssignmentsScreen
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.ui.assignment.AssignmentDetailViewModel
import de.oliveroehme.campaignfield.ui.assignment.AssignmentListViewModel
import de.oliveroehme.campaignfield.ui.assignment.ScannerViewModel
import de.oliveroehme.campaignfield.ui.screens.AssignmentDetailScreen
import de.oliveroehme.campaignfield.ui.screens.MapScreen
import de.oliveroehme.campaignfield.ui.screens.ProfileScreen
import de.oliveroehme.campaignfield.ui.screens.ProofScreen
import de.oliveroehme.campaignfield.ui.screens.SyncScreen
import de.oliveroehme.campaignfield.ui.sync.SyncViewModel
import de.oliveroehme.campaignfield.location.CompassSource
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.location.LocationSource
import de.oliveroehme.campaignfield.map.MapConfiguration
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.network.auth.ProfileRemoteDataSource
import de.oliveroehme.campaignfield.ui.session.ProfileViewModel
import de.oliveroehme.campaignfield.ui.status.LocationAccessState

@Composable
fun CampaignFieldNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    profile: UserProfile,
    assignmentRepository: AssignmentRepository,
    profileRemoteDataSource: ProfileRemoteDataSource,
    syncRepository: SyncRepository,
    mapConfiguration: MapConfiguration,
    locationAccessState: LocationAccessState,
    locationSessionState: InMemoryLocationSessionState,
    locationSource: LocationSource,
    compassSource: CompassSource,
    networkStateProvider: NetworkStateProvider,
    isRefreshingProfile: Boolean,
    isLoggingOut: Boolean,
    onRefreshProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Assignments.route,
    ) {
        composable(AppDestination.Assignments.route) {
            val viewModel: AssignmentListViewModel = viewModel(
                factory = AssignmentListViewModel.factory(assignmentRepository, profile),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val syncViewModel: SyncViewModel = viewModel(
                factory = SyncViewModel.factory(syncRepository),
            )
            val syncState by syncViewModel.state.collectAsStateWithLifecycle()
            AssignmentsScreen(
                contentPadding = contentPadding,
                state = state,
                syncState = syncState,
                onRefresh = viewModel::refresh,
                onChangeStatus = viewModel::changeStatus,
                onOpenSync = {
                    navController.navigate(AppDestination.Sync.route) { launchSingleTop = true }
                },
                onOpenAssignment = { id ->
                    navController.navigate(AppDestination.assignmentDetailRoute(id))
                },
            )
        }
        composable(
            route = AppDestination.AssignmentDetail.route,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assignmentId = requireNotNull(backStackEntry.arguments?.getString("assignmentId"))
            val viewModel: AssignmentDetailViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = AssignmentDetailViewModel.factory(
                    assignmentRepository,
                    assignmentId,
                    profile,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val syncViewModel: SyncViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = SyncViewModel.factory(syncRepository),
            )
            val syncState by syncViewModel.state.collectAsStateWithLifecycle()
            AssignmentDetailScreen(
                contentPadding = contentPadding,
                state = state,
                syncState = syncState,
                onRefresh = viewModel::refresh,
                onChangeStatus = viewModel::changeStatus,
                onOpenMap = {
                    navController.navigate(AppDestination.assignmentMapRoute(assignmentId)) {
                        launchSingleTop = true
                    }
                },
                onOpenProof = {
                    navController.navigate(AppDestination.assignmentProofRoute(assignmentId)) {
                        launchSingleTop = true
                    }
                },
                onOpenSync = {
                    navController.navigate(AppDestination.Sync.route) { launchSingleTop = true }
                },
                onChangeBuildingStatus = viewModel::changeBuildingStatus,
                onChangeBuildingNotes = viewModel::changeBuildingNotes,
                onDeleteBuilding = viewModel::deleteAssignmentBuilding,
            )
        }
        composable(
            route = AppDestination.AssignmentMap.route,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assignmentId = requireNotNull(backStackEntry.arguments?.getString("assignmentId"))
            val viewModel: AssignmentDetailViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = AssignmentDetailViewModel.factory(
                    assignmentRepository,
                    assignmentId,
                    profile,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            MapScreen(
                contentPadding = contentPadding,
                includeStatusBarInset = false,
                assignmentState = state,
                scannerState = null,
                userLabel = profile.name,
                onRefreshAssignment = viewModel::refresh,
                onOpenAssignmentDetails = {
                    navController.navigate(AppDestination.assignmentDetailRoute(assignmentId)) {
                        launchSingleTop = true
                    }
                },
                onOpenProof = {
                    navController.navigate(AppDestination.assignmentProofRoute(assignmentId)) {
                        launchSingleTop = true
                    }
                },
                onChangeBuildingStatus = viewModel::changeBuildingStatus,
                onCreatePosterLocation = viewModel::createPosterLocation,
                onCreateCampaignBoothLocation = viewModel::createCampaignBoothLocation,
                onUpdateMapFeature = viewModel::updateMapFeature,
                onDeleteMapFeature = viewModel::deleteMapFeature,
                configuration = mapConfiguration,
                locationAccessState = locationAccessState,
                locationSessionState = locationSessionState,
                locationSource = locationSource,
                compassSource = compassSource,
                networkStateProvider = networkStateProvider,
            )
        }
        composable(
            route = AppDestination.AssignmentProof.route,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            ProofScreen(
                contentPadding = contentPadding,
                assignmentId = requireNotNull(backStackEntry.arguments?.getString("assignmentId")),
            )
        }
        composable(AppDestination.Map.route) {
            val viewModel: ScannerViewModel = viewModel(
                factory = ScannerViewModel.factory(assignmentRepository, profile),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            MapScreen(
                contentPadding = contentPadding,
                includeStatusBarInset = true,
                assignmentState = null,
                scannerState = state,
                userLabel = profile.name,
                onRefreshAssignment = viewModel::refresh,
                onOpenAssignmentDetails = {},
                onOpenProof = {},
                onChangeScannerBuildingStatus = viewModel::changeBuildingStatus,
                configuration = mapConfiguration,
                locationAccessState = locationAccessState,
                locationSessionState = locationSessionState,
                locationSource = locationSource,
                compassSource = compassSource,
                networkStateProvider = networkStateProvider,
            )
        }
        composable(AppDestination.Sync.route) {
            val viewModel: SyncViewModel = viewModel(
                factory = SyncViewModel.factory(syncRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            SyncScreen(
                contentPadding = contentPadding,
                state = state,
                onSynchronize = viewModel::synchronize,
                onRetry = viewModel::retry,
            )
        }
        composable(AppDestination.Profile.route) {
            val viewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModel.factory(profileRemoteDataSource),
            )
            val profileState by viewModel.state.collectAsStateWithLifecycle()
            val teamIds = profile.teams.mapNotNull { it.teamId }
            LaunchedEffect(teamIds) { viewModel.refresh(teamIds) }
            LaunchedEffect(profileState.profileRefreshRevision) {
                if (profileState.profileRefreshRevision > 0) {
                    onRefreshProfile()
                    viewModel.consumeProfileRefresh()
                }
            }
            ProfileScreen(
                contentPadding = contentPadding,
                profile = profile,
                state = profileState,
                isRefreshingProfile = isRefreshingProfile,
                isLoggingOut = isLoggingOut,
                onRefreshProfile = {
                    onRefreshProfile()
                    viewModel.refresh(teamIds)
                },
                onAcceptInvitation = { viewModel.respondToInvitation(it, accept = true) },
                onDeclineInvitation = { viewModel.respondToInvitation(it, accept = false) },
                onLogout = onLogout,
            )
        }
    }
}
