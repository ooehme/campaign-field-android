package de.oliveroehme.campaignfield.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.ui.screens.AssignmentsScreen
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.ui.assignment.AssignmentDetailViewModel
import de.oliveroehme.campaignfield.ui.assignment.AssignmentListViewModel
import de.oliveroehme.campaignfield.ui.screens.AssignmentDetailScreen
import de.oliveroehme.campaignfield.ui.screens.MapScreen
import de.oliveroehme.campaignfield.ui.screens.ProfileScreen
import de.oliveroehme.campaignfield.ui.screens.ProofScreen
import de.oliveroehme.campaignfield.ui.screens.SyncScreen

@Composable
fun CampaignFieldNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    profile: UserProfile,
    assignmentRepository: AssignmentRepository,
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
            AssignmentsScreen(
                contentPadding = contentPadding,
                state = state,
                onRefresh = viewModel::refresh,
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
                factory = AssignmentDetailViewModel.factory(assignmentRepository, assignmentId),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            AssignmentDetailScreen(
                contentPadding = contentPadding,
                state = state,
                onRefresh = viewModel::refresh,
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
            )
        }
        composable(
            route = AppDestination.AssignmentMap.route,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType }),
        ) {
            MapScreen(contentPadding, includeStatusBarInset = false)
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
            MapScreen(contentPadding, includeStatusBarInset = true)
        }
        composable(AppDestination.Sync.route) {
            SyncScreen(contentPadding)
        }
        composable(AppDestination.Profile.route) {
            ProfileScreen(
                contentPadding = contentPadding,
                profile = profile,
                isRefreshingProfile = isRefreshingProfile,
                isLoggingOut = isLoggingOut,
                onRefreshProfile = onRefreshProfile,
                onLogout = onLogout,
            )
        }
    }
}
