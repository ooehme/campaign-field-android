package de.oliveroehme.campaignfield.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun CampaignFieldBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        AppDestination.shellItems.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(AppDestination.Assignments.route) { saveState = true }
                    }
                },
                icon = { Text(destination.marker) },
                label = { Text(destination.label) },
            )
        }
    }
}
