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
import de.oliveroehme.campaignfield.ui.screens.SyncScreen

@Composable
fun CampaignFieldNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    profile: UserProfile,
    assignmentRepository: AssignmentRepository,
    isLoggingOut: Boolean,
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
                onSelectTeam = viewModel::selectTeam,
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
                onBack = navController::navigateUp,
                onRefresh = viewModel::refresh,
                onOpenMap = {
                    navController.navigate(AppDestination.Map.route) { launchSingleTop = true }
                },
            )
        }
        composable(AppDestination.Map.route) {
            MapScreen(contentPadding)
        }
        composable(AppDestination.Sync.route) {
            SyncScreen(contentPadding)
        }
        composable(AppDestination.Profile.route) {
            ProfileScreen(
                contentPadding = contentPadding,
                profile = profile,
                isLoggingOut = isLoggingOut,
                onLogout = onLogout,
            )
        }
    }
}
