package de.oliveroehme.campaignfield.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.data.auth.AuthState
import de.oliveroehme.campaignfield.ui.navigation.AppDestination
import de.oliveroehme.campaignfield.ui.navigation.CampaignFieldBottomBar
import de.oliveroehme.campaignfield.ui.navigation.CampaignFieldNavHost
import de.oliveroehme.campaignfield.ui.components.FieldGridBackground
import de.oliveroehme.campaignfield.ui.components.FieldHeader
import de.oliveroehme.campaignfield.ui.screens.LoginScreen
import de.oliveroehme.campaignfield.ui.screens.RestoringSessionScreen
import de.oliveroehme.campaignfield.ui.session.SessionViewModel

@Composable
fun CampaignFieldApp(
    viewModel: SessionViewModel,
    assignmentRepository: AssignmentRepository,
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val loginFeedback by viewModel.loginFeedback.collectAsStateWithLifecycle()

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
                isLoggingOut = false,
                onLogout = viewModel::logout,
            )
            is AuthState.SigningOut -> AuthenticatedApp(
                state = AuthState.SignedIn(state.profile),
                assignmentRepository = assignmentRepository,
                isLoggingOut = true,
                onLogout = viewModel::logout,
            )
        }
    }
}

@Composable
private fun AuthenticatedApp(
    state: AuthState.SignedIn,
    assignmentRepository: AssignmentRepository,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            if (route != AppDestination.Map.route) FieldHeader()
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
            isLoggingOut = isLoggingOut,
            onLogout = onLogout,
        )
    }
}
