package de.oliveroehme.campaignfield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.oliveroehme.campaignfield.ui.CampaignFieldApp
import de.oliveroehme.campaignfield.ui.session.SessionViewModel
import de.oliveroehme.campaignfield.ui.theme.CampaignFieldTheme

class MainActivity : ComponentActivity() {
    private val sessionViewModel: SessionViewModel by viewModels {
        val application = application as CampaignFieldApplication
        SessionViewModel.factory(application.appContainer.sessionRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampaignFieldTheme {
                val application = this@MainActivity.application as CampaignFieldApplication
                CampaignFieldApp(
                    viewModel = sessionViewModel,
                    assignmentRepository = application.appContainer.assignmentRepository,
                    syncRepository = application.appContainer.syncRepository,
                    coreApiHealthSource = application.appContainer.coreApiHealthSource,
                    locationSessionState = application.appContainer.locationSessionState,
                    currentLocationRequester = application.appContainer.currentLocationRequester,
                )
            }
        }
    }
}
