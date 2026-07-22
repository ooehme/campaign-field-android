package de.oliveroehme.campaignfield

import android.app.Application

class CampaignFieldApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
