package de.oliveroehme.campaignfield.map

import de.oliveroehme.campaignfield.BuildConfig
import java.net.URI

data class MapConfiguration(
    val styleUrl: String,
    val initialZoom: Double = 16.5,
    val initialPitch: Double = 20.0,
    val fallbackBearing: Double = 0.0,
) {
    init {
        val uri = runCatching { URI(styleUrl) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
            "Die Kartenstil-URL muss eine gültige HTTPS-URL sein."
        }
        require(initialZoom in 0.0..24.0)
        require(initialPitch in 0.0..60.0)
    }

    companion object {
        fun fromBuildConfig(): MapConfiguration = MapConfiguration(BuildConfig.MAP_STYLE_URL)

        const val FALLBACK_STYLE_JSON =
            "{\"version\":8,\"name\":\"Ohne Basiskarte\",\"sources\":{}," +
                "\"layers\":[{\"id\":\"background\",\"type\":\"background\"," +
                "\"paint\":{\"background-color\":\"#111820\"}}]}"
    }
}
