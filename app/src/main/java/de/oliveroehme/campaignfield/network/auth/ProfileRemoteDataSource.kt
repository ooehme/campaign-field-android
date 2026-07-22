package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.domain.auth.TeamDetail
import de.oliveroehme.campaignfield.domain.auth.TeamInvitation
import de.oliveroehme.campaignfield.network.ApiConfiguration
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

interface ProfileRemoteDataSource {
    suspend fun loadTeam(id: String): ProfileResult<TeamDetail>
    suspend fun loadInvitations(): ProfileResult<List<TeamInvitation>>
    suspend fun acceptInvitation(id: String): ProfileResult<Unit>
    suspend fun declineInvitation(id: String): ProfileResult<Unit>
}

class ProfileHttpClient internal constructor(
    private val configuration: ApiConfiguration,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parser: ProfileSupplementParser = ProfileSupplementParser(),
) : ProfileRemoteDataSource {
    override suspend fun loadTeam(id: String): ProfileResult<TeamDetail> = withContext(ioDispatcher) {
        when (val response = execute(configuration.apiEndpointSegments("teams", id))) {
            is RawResponse.Success -> parse { parser.parseTeam(response.body) }
            is RawResponse.HttpFailure -> ProfileResult.Failure(messageFor(response.status, "Team"))
            RawResponse.TransportFailure -> ProfileResult.Failure(NETWORK_ERROR)
        }
    }

    override suspend fun loadInvitations(): ProfileResult<List<TeamInvitation>> = withContext(ioDispatcher) {
        when (val response = execute(configuration.apiEndpoint("user/invitations"))) {
            is RawResponse.Success -> parse { parser.parseInvitations(response.body) }
            is RawResponse.HttpFailure -> ProfileResult.Failure(messageFor(response.status, "Einladungen"))
            RawResponse.TransportFailure -> ProfileResult.Failure(NETWORK_ERROR)
        }
    }

    override suspend fun acceptInvitation(id: String): ProfileResult<Unit> =
        changeInvitation(id, "accept")

    override suspend fun declineInvitation(id: String): ProfileResult<Unit> =
        changeInvitation(id, "decline")

    private suspend fun changeInvitation(id: String, action: String): ProfileResult<Unit> =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url(configuration.apiEndpointSegments("team-invitations", id, action))
                .post(ByteArray(0).toRequestBody(null))
                .build()
            when (val response = execute(request, readBody = false)) {
                is RawResponse.Success -> ProfileResult.Success(Unit)
                is RawResponse.HttpFailure -> ProfileResult.Failure(messageFor(response.status, "Einladung"))
                RawResponse.TransportFailure -> ProfileResult.Failure(NETWORK_ERROR)
            }
        }

    private inline fun <T> parse(block: () -> T): ProfileResult<T> = runCatching(block).fold(
        onSuccess = { ProfileResult.Success(it) },
        onFailure = { ProfileResult.Failure("Die Profildaten konnten nicht verarbeitet werden.") },
    )

    private fun execute(url: HttpUrl): RawResponse = execute(Request.Builder().url(url).get().build())

    private fun execute(request: Request, readBody: Boolean = true): RawResponse = try {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use RawResponse.HttpFailure(response.code)
            if (!readBody) return@use RawResponse.Success("")
            val body = response.body.readLimitedBytes()
                ?: return@use RawResponse.HttpFailure(INVALID_RESPONSE_STATUS)
            RawResponse.Success(body.toString(Charsets.UTF_8))
        }
    } catch (_: IOException) {
        RawResponse.TransportFailure
    }

    private fun ResponseBody.readLimitedBytes(): ByteArray? {
        if (contentLength() > MAX_RESPONSE_BYTES) return null
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        byteStream().use { input ->
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BYTES) return null
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun messageFor(status: Int, subject: String): String = when (status) {
        401 -> "Die Sitzung ist abgelaufen. Bitte erneut anmelden."
        403 -> "Keine Berechtigung, $subject anzuzeigen."
        404 -> "$subject wurden nicht gefunden."
        in 500..599 -> "Der Server ist vorübergehend nicht verfügbar."
        INVALID_RESPONSE_STATUS -> "Die Profildaten konnten nicht verarbeitet werden."
        else -> "$subject konnten nicht geladen werden."
    }

    private sealed interface RawResponse {
        data class Success(val body: String) : RawResponse
        data class HttpFailure(val status: Int) : RawResponse
        data object TransportFailure : RawResponse
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val INVALID_RESPONSE_STATUS = -1
        const val NETWORK_ERROR = "Keine Verbindung zum Server. Bitte Netzwerk prüfen."
    }
}

sealed interface ProfileResult<out T> {
    data class Success<T>(val value: T) : ProfileResult<T>
    data class Failure(val userMessage: String) : ProfileResult<Nothing>
}
