package de.oliveroehme.campaignfield.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class CoreApiStatus {
    Checking,
    Reachable,
    Unreachable,
}

interface CoreApiHealthSource {
    suspend fun check(): CoreApiStatus
}

class CoreApiHealthClient internal constructor(
    private val configuration: ApiConfiguration,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoreApiHealthSource {
    private val healthHttpClient = httpClient.newBuilder()
        .callTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun check(): CoreApiStatus = withContext(ioDispatcher) {
        try {
            healthHttpClient.newCall(
                Request.Builder()
                    .url(configuration.apiEndpoint("health"))
                    .get()
                    .build(),
            ).execute().use { response ->
                if (response.isSuccessful) CoreApiStatus.Reachable else CoreApiStatus.Unreachable
            }
        } catch (_: IOException) {
            CoreApiStatus.Unreachable
        }
    }

    private companion object {
        const val HEALTH_CHECK_TIMEOUT_SECONDS = 6L
    }
}
