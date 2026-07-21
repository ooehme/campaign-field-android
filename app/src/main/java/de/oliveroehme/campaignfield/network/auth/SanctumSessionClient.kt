package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.network.ApiConfiguration
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SanctumSessionClient internal constructor(
    private val configuration: ApiConfiguration,
    private val httpClient: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun signIn(email: String, password: String): SessionResult = withContext(ioDispatcher) {
        cookieJar.clear()

        execute(
            Request.Builder()
                .url(configuration.originEndpoint("sanctum/csrf-cookie"))
                .get()
                .build(),
        ).failureFor(SessionStage.CSRF)?.let { return@withContext it }

        val loginBody = Json.encodeToString(LoginRequest(email, password)).toRequestBody(JSON_MEDIA_TYPE)
        execute(
            Request.Builder()
                .url(configuration.apiEndpoint("login"))
                .post(loginBody)
                .build(),
        ).failureFor(SessionStage.LOGIN)?.let {
            cookieJar.clear()
            return@withContext it
        }

        val userResult = checkSessionInternal()
        if (userResult !is SessionResult.Authenticated) cookieJar.clear()
        userResult
    }

    suspend fun checkSession(): SessionResult = withContext(ioDispatcher) { checkSessionInternal() }

    suspend fun logout(): SessionResult = withContext(ioDispatcher) {
        try {
            val outcome = execute(
                Request.Builder()
                    .url(configuration.apiEndpoint("logout"))
                    .post(ByteArray(0).toRequestBody(null))
                    .build(),
            )
            outcome.failureFor(SessionStage.LOGOUT) ?: SessionResult.LoggedOut
        } finally {
            cookieJar.clear()
        }
    }

    private fun checkSessionInternal(): SessionResult {
        val outcome = execute(
            Request.Builder()
                .url(configuration.apiEndpoint("user"))
                .get()
                .build(),
        )
        return outcome.failureFor(SessionStage.USER) ?: SessionResult.Authenticated
    }

    private fun execute(request: Request): CallOutcome = try {
        httpClient.newCall(request).execute().use { response -> CallOutcome.Http(response.code) }
    } catch (_: IOException) {
        CallOutcome.TransportFailure
    }

    private fun CallOutcome.failureFor(stage: SessionStage): SessionResult.Failure? = when (this) {
        is CallOutcome.Http -> if (status in 200..299) null else SessionResult.Failure(stage, status)
        CallOutcome.TransportFailure -> SessionResult.Failure(stage, null)
    }

    @Serializable
    private data class LoginRequest(val email: String, val password: String)

    private sealed interface CallOutcome {
        data class Http(val status: Int) : CallOutcome
        data object TransportFailure : CallOutcome
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

sealed interface SessionResult {
    data object Authenticated : SessionResult
    data object LoggedOut : SessionResult
    data class Failure(val stage: SessionStage, val httpStatus: Int?) : SessionResult
}

enum class SessionStage {
    CSRF,
    LOGIN,
    USER,
    LOGOUT,
}
