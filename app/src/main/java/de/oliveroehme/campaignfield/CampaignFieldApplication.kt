package de.oliveroehme.campaignfield

import android.app.Application
import org.maplibre.android.MapLibre

class CampaignFieldApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
