package de.oliveroehme.campaignfield.network

import de.oliveroehme.campaignfield.BuildConfig

data class ApiConfiguration(val baseUrl: String) {
    companion object {
        fun fromBuildConfig(): ApiConfiguration = ApiConfiguration(
            baseUrl = normalizeApiBaseUrl(BuildConfig.API_BASE_URL),
        )
    }
}

fun normalizeApiBaseUrl(value: String): String = value.trim().trimEnd('/')
