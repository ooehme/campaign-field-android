package de.oliveroehme.campaignfield.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.oliveroehme.campaignfield.ui.navigation.AppDestination
import de.oliveroehme.campaignfield.ui.navigation.CampaignFieldBottomBar
import de.oliveroehme.campaignfield.ui.navigation.CampaignFieldNavHost

@Composable
fun CampaignFieldApp() {
    val navController = rememberNavController()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val showsAppShell = route != null && route != AppDestination.Login.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showsAppShell) {
                CampaignFieldBottomBar(navController = navController, currentRoute = route)
            }
        },
    ) { innerPadding ->
        CampaignFieldNavHost(
            navController = navController,
            contentPadding = innerPadding,
        )
    }
}
