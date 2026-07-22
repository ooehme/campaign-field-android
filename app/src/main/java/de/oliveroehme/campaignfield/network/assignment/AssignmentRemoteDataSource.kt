package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.TeamSummary
import de.oliveroehme.campaignfield.network.ApiConfiguration
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

interface AssignmentRemoteDataSource {
    suspend fun loadAssignments(
        userId: String?,
        teamIds: List<String>,
    ): AssignmentResult<AssignmentPage>

    suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail>
}

class AssignmentHttpClient internal constructor(
    private val configuration: ApiConfiguration,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parser: AssignmentParser = AssignmentParser(),
) : AssignmentRemoteDataSource {
    override suspend fun loadAssignments(
        userId: String?,
        teamIds: List<String>,
    ): AssignmentResult<AssignmentPage> = withContext(ioDispatcher) {
        val uniqueTeams = teamIds.filter(String::isNotBlank).distinct()
        if (uniqueTeams.isNotEmpty()) {
            val combined = mutableListOf<AssignmentSummary>()
            for (teamId in uniqueTeams) {
                when (val teamResult = loadAllPages(listOf("teams", teamId, "assignments"))) {
                    is AssignmentResult.Success -> combined += teamResult.value.items.map { assignment ->
                        if (assignment.team?.id == null) {
                            assignment.copy(team = TeamSummary(teamId, "Team #$teamId"))
                        } else {
                            assignment
                        }
                    }
                    is AssignmentResult.Failure -> {
                        if (!userId.isNullOrBlank() && teamResult.failure.shouldUseUserFallback) {
                            return@withContext loadAllPages(listOf("users", userId, "assignments"))
                        }
                        return@withContext teamResult
                    }
                }
            }
            val deduplicated = combined.distinctBy { it.id }
            return@withContext AssignmentResult.Success(
                AssignmentPage(
                    items = deduplicated,
                    perPage = deduplicated.size,
                    total = deduplicated.size,
                ),
            )
        }

        if (!userId.isNullOrBlank()) {
            return@withContext loadAllPages(listOf("users", userId, "assignments"))
        }

        AssignmentResult.Success(AssignmentPage(emptyList()))
    }

    override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> =
        withContext(ioDispatcher) {
            when (val response = execute(configuration.apiEndpointSegments("assignments", id))) {
                is RawResponse.Success -> runCatching { parser.parseDetail(response.body) }
                    .fold(
                        onSuccess = { AssignmentResult.Success(it) },
                        onFailure = { AssignmentResult.Failure(AssignmentFailure.invalidResponse()) },
                    )
                is RawResponse.HttpFailure -> AssignmentResult.Failure(
                    AssignmentFailure.fromHttp(response.status, detailRequest = true),
                )
                RawResponse.TransportFailure -> AssignmentResult.Failure(AssignmentFailure.network())
            }
        }

    private fun loadAllPages(pathSegments: List<String>): AssignmentResult<AssignmentPage> {
        val items = mutableListOf<AssignmentSummary>()
        var requestedPage = 1
        var lastPage = 1
        var total = 0
        do {
            val url = configuration.apiEndpointSegments(*pathSegments.toTypedArray())
                .newBuilder()
                .addQueryParameter("per_page", PAGE_SIZE.toString())
                .addQueryParameter("page", requestedPage.toString())
                .build()
            when (val response = execute(url)) {
                is RawResponse.Success -> {
                    val page = runCatching { parser.parsePage(response.body) }
                        .getOrElse {
                            return AssignmentResult.Failure(AssignmentFailure.invalidResponse())
                        }
                    lastPage = page.lastPage
                    if (lastPage > MAX_PAGES) {
                        return AssignmentResult.Failure(AssignmentFailure.invalidResponse())
                    }
                    total = page.total
                    items += page.items
                }
                is RawResponse.HttpFailure -> return AssignmentResult.Failure(
                    AssignmentFailure.fromHttp(response.status, detailRequest = false),
                )
                RawResponse.TransportFailure -> return AssignmentResult.Failure(AssignmentFailure.network())
            }
            requestedPage++
        } while (requestedPage <= lastPage)

        val deduplicated = items.distinctBy { it.id }
        return AssignmentResult.Success(
            AssignmentPage(
                items = deduplicated,
                currentPage = lastPage,
                lastPage = lastPage,
                perPage = PAGE_SIZE,
                total = maxOf(total, deduplicated.size),
            ),
        )
    }

    private fun execute(url: HttpUrl): RawResponse = try {
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@use RawResponse.HttpFailure(response.code)
            val body = response.body.readLimitedBytes()
            if (body == null) {
                RawResponse.HttpFailure(INVALID_RESPONSE_STATUS)
            } else {
                RawResponse.Success(body.toString(Charsets.UTF_8))
            }
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

    private sealed interface RawResponse {
        data class Success(val body: String) : RawResponse
        data class HttpFailure(val status: Int) : RawResponse
        data object TransportFailure : RawResponse
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 100
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val INVALID_RESPONSE_STATUS = -1
    }
}

sealed interface AssignmentResult<out T> {
    data class Success<T>(val value: T) : AssignmentResult<T>
    data class Failure(val failure: AssignmentFailure) : AssignmentResult<Nothing>
}

enum class AssignmentFailureKind {
    NETWORK,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    SERVER,
    INVALID_RESPONSE,
    UNEXPECTED,
}

data class AssignmentFailure(
    val kind: AssignmentFailureKind,
    val httpStatus: Int?,
    val userMessage: String,
) {
    internal val shouldUseUserFallback: Boolean
        get() = httpStatus == 403 || httpStatus == 404

    companion object {
        internal fun network() = AssignmentFailure(
            AssignmentFailureKind.NETWORK,
            null,
            "Keine Verbindung zum Server. Bitte Netzwerk prüfen und erneut versuchen.",
        )

        internal fun invalidResponse() = AssignmentFailure(
            AssignmentFailureKind.INVALID_RESPONSE,
            null,
            "Die Assignment-Daten konnten nicht verarbeitet werden. Bitte erneut versuchen.",
        )

        internal fun fromHttp(status: Int, detailRequest: Boolean): AssignmentFailure {
            if (status == -1) return invalidResponse()
            val kind = when {
                status == 401 -> AssignmentFailureKind.UNAUTHORIZED
                status == 403 -> AssignmentFailureKind.FORBIDDEN
                status == 404 -> AssignmentFailureKind.NOT_FOUND
                status in 500..599 -> AssignmentFailureKind.SERVER
                else -> AssignmentFailureKind.UNEXPECTED
            }
            val message = when (kind) {
                AssignmentFailureKind.UNAUTHORIZED ->
                    "Die Sitzung ist abgelaufen. Bitte erneut anmelden."
                AssignmentFailureKind.FORBIDDEN ->
                    "Keine Berechtigung, diese Aufträge anzuzeigen."
                AssignmentFailureKind.NOT_FOUND -> if (detailRequest) {
                    "Der Auftrag wurde nicht gefunden."
                } else {
                    "Der Assignment-Endpunkt ist nicht verfügbar."
                }
                AssignmentFailureKind.SERVER ->
                    "Der Server ist vorübergehend nicht verfügbar. Bitte später erneut versuchen."
                else -> "Aufträge konnten nicht geladen werden. Bitte erneut versuchen."
            }
            return AssignmentFailure(kind, status, message)
        }
    }
}
