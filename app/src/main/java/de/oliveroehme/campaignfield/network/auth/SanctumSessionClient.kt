package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.domain.auth.UserProfile
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

interface SessionRemoteDataSource {
    suspend fun signIn(email: String, password: String): SessionResult
    suspend fun checkSession(): SessionResult
    suspend fun logout(): SessionResult
}

class SanctumSessionClient internal constructor(
    private val configuration: ApiConfiguration,
    private val httpClient: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val userProfileParser: UserProfileParser = UserProfileParser(),
) : SessionRemoteDataSource {
    override suspend fun signIn(email: String, password: String): SessionResult = withContext(ioDispatcher) {
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

    override suspend fun checkSession(): SessionResult = withContext(ioDispatcher) { checkSessionInternal() }

    override suspend fun logout(): SessionResult = withContext(ioDispatcher) {
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
            readBody = true,
        )
        outcome.failureFor(SessionStage.USER)?.let { return it }
        val http = outcome as CallOutcome.Http
        val payload = http.body
            ?.takeIf { it.toByteArray().size <= MAX_USER_RESPONSE_BYTES }
            ?: return SessionResult.Failure(SessionErrorNormalizer.invalidResponse(SessionStage.USER))
        return runCatching { userProfileParser.parse(payload) }
            .fold(
                onSuccess = ::loadDetailedProfile,
                onFailure = {
                    SessionResult.Failure(SessionErrorNormalizer.invalidResponse(SessionStage.USER))
                },
            )
    }

    private fun loadDetailedProfile(sessionProfile: UserProfile): SessionResult {
        val userId = sessionProfile.id ?: return SessionResult.Authenticated(sessionProfile)
        val outcome = execute(
            Request.Builder()
                .url(configuration.apiEndpointSegments("users", userId))
                .get()
                .build(),
            readBody = true,
        )
        if (outcome is CallOutcome.Http && outcome.status == 401) {
            return SessionResult.Failure(SessionErrorNormalizer.from(SessionStage.USER, 401))
        }
        val payload = (outcome as? CallOutcome.Http)
            ?.takeIf { it.status in 200..299 }
            ?.body
            ?.takeIf { it.toByteArray().size <= MAX_USER_RESPONSE_BYTES }
            ?: return SessionResult.Authenticated(sessionProfile)
        val detailedProfile = runCatching { userProfileParser.parse(payload) }.getOrNull()
            ?: return SessionResult.Authenticated(sessionProfile)
        return SessionResult.Authenticated(
            detailedProfile.copy(
                id = detailedProfile.id ?: sessionProfile.id,
                name = detailedProfile.name.takeUnless { it == "Unbekannter Benutzer" }
                    ?: sessionProfile.name,
                email = detailedProfile.email.ifBlank { sessionProfile.email },
                appRole = detailedProfile.appRole ?: sessionProfile.appRole,
                roles = detailedProfile.roles.ifEmpty { sessionProfile.roles },
            ),
        )
    }

    private fun execute(request: Request, readBody: Boolean = false): CallOutcome = try {
        httpClient.newCall(request).execute().use { response ->
            CallOutcome.Http(
                status = response.code,
                body = if (readBody && response.isSuccessful) response.body.string() else null,
            )
        }
    } catch (_: IOException) {
        CallOutcome.TransportFailure
    }

    private fun CallOutcome.failureFor(stage: SessionStage): SessionResult.Failure? = when (this) {
        is CallOutcome.Http -> if (status in 200..299) {
            null
        } else {
            SessionResult.Failure(SessionErrorNormalizer.from(stage, status))
        }
        CallOutcome.TransportFailure -> SessionResult.Failure(SessionErrorNormalizer.from(stage, null))
    }

    @Serializable
    private data class LoginRequest(val email: String, val password: String)

    private sealed interface CallOutcome {
        data class Http(val status: Int, val body: String?) : CallOutcome
        data object TransportFailure : CallOutcome
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_USER_RESPONSE_BYTES = 1_048_576
    }
}

sealed interface SessionResult {
    data class Authenticated(val profile: UserProfile) : SessionResult
    data object LoggedOut : SessionResult
    data class Failure(val error: SessionFailure) : SessionResult
}

enum class SessionStage {
    CSRF,
    LOGIN,
    USER,
    LOGOUT,
}
