package de.oliveroehme.campaignfield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.oliveroehme.campaignfield.ui.CampaignFieldApp
import de.oliveroehme.campaignfield.ui.theme.CampaignFieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampaignFieldTheme {
                CampaignFieldApp()
            }
        }
    }
}
