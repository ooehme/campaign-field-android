package de.oliveroehme.campaignfield.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.oliveroehme.campaignfield.ui.screens.AssignmentsScreen
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.ui.screens.MapScreen
import de.oliveroehme.campaignfield.ui.screens.ProfileScreen
import de.oliveroehme.campaignfield.ui.screens.SyncScreen

@Composable
fun CampaignFieldNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    profile: UserProfile,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Assignments.route,
    ) {
        composable(AppDestination.Assignments.route) {
            AssignmentsScreen(contentPadding)
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
