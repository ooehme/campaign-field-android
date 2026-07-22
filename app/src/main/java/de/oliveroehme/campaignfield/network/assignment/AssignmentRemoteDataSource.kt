package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentMapFeature
import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.AssignmentLocationInput
import de.oliveroehme.campaignfield.domain.AreaSummary
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.domain.TeamSummary
import de.oliveroehme.campaignfield.map.FieldGeoJson
import de.oliveroehme.campaignfield.network.ApiConfiguration
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface AssignmentRemoteDataSource {
    suspend fun loadAssignments(
        userId: String?,
        teamIds: List<String>,
    ): AssignmentResult<AssignmentPage>

    suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail>

    suspend fun loadAssignmentMapData(
        id: String,
        type: AssignmentType,
    ): AssignmentResult<AssignmentMapData> = AssignmentResult.Success(AssignmentMapData())

    suspend fun updateAssignmentStatus(
        id: String,
        status: AssignmentStatus,
    ): AssignmentResult<AssignmentDetail> = AssignmentResult.Failure(
        AssignmentFailure.unsupportedMutation(),
    )

    suspend fun updateAssignmentBuildingStatus(
        id: String,
        status: BuildingStatus,
        clientEventKey: String? = null,
        knownUpdatedAt: String? = null,
    ): AssignmentResult<AssignmentMapFeature?> = AssignmentResult.Failure(
        AssignmentFailure.unsupportedMutation(),
    )

    suspend fun updateAssignmentBuilding(
        id: String,
        status: BuildingStatus? = null,
        notes: String? = null,
        includeNotes: Boolean = false,
        clientEventKey: String? = null,
        knownUpdatedAt: String? = null,
    ): AssignmentResult<AssignmentMapFeature?> = AssignmentResult.Failure(
        AssignmentFailure.unsupportedMutation(),
    )

    suspend fun deleteAssignmentBuilding(id: String): AssignmentResult<Unit> =
        AssignmentResult.Failure(AssignmentFailure.unsupportedMutation())

    suspend fun createPosterLocation(
        assignmentId: String,
        input: AssignmentLocationInput,
    ): AssignmentResult<AssignmentMapFeature> = AssignmentResult.Failure(
        AssignmentFailure.unsupportedMutation(),
    )

    suspend fun updatePosterLocation(
        id: String,
        input: AssignmentLocationInput,
    ): AssignmentResult<AssignmentMapFeature> = AssignmentResult.Failure(
        AssignmentFailure.unsupportedMutation(),
    )

    suspend fun deletePosterLocation(id: String): AssignmentResult<Unit> =
        AssignmentResult.Failure(AssignmentFailure.unsupportedMutation())

    suspend fun saveCampaignBoothLocation(
        assignmentId: String,
        existingId: String?,
        input: AssignmentLocationInput,
    ): AssignmentResult<AssignmentMapFeature> = AssignmentResult.Failure(
        AssignmentFailure.unsupportedMutation(),
    )

    suspend fun deleteCampaignBoothLocation(id: String): AssignmentResult<Unit> =
        AssignmentResult.Failure(AssignmentFailure.unsupportedMutation())
}

class AssignmentHttpClient internal constructor(
    private val configuration: ApiConfiguration,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parser: AssignmentParser = AssignmentParser(),
    private val mapDataParser: AssignmentMapDataParser = AssignmentMapDataParser(),
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
                        onSuccess = { AssignmentResult.Success(enrichTargetArea(it)) },
                        onFailure = { AssignmentResult.Failure(AssignmentFailure.invalidResponse()) },
                    )
                is RawResponse.HttpFailure -> AssignmentResult.Failure(
                    AssignmentFailure.fromHttp(response.status, detailRequest = true),
                )
                RawResponse.TransportFailure -> AssignmentResult.Failure(AssignmentFailure.network())
            }
        }

    override suspend fun loadAssignmentMapData(
        id: String,
        type: AssignmentType,
    ): AssignmentResult<AssignmentMapData> = withContext(ioDispatcher) {
        val buildings = if (type == AssignmentType.LETTERBOX_DISTRIBUTION) {
            when (
                val result = loadFeaturePages(
                    pathSegments = listOf("assignments", id, "buildings"),
                    parse = mapDataParser::parseBuildings,
                )
            ) {
                is AssignmentResult.Success -> result.value
                is AssignmentResult.Failure -> return@withContext result
            }
        } else {
            FeatureCollectionResult.EMPTY
        }
        val posters = if (type == AssignmentType.POSTER_FREE || type == AssignmentType.POSTER_GUIDED) {
            when (
                val result = loadFeaturePages(
                    pathSegments = listOf("assignments", id, "poster-locations"),
                    parse = mapDataParser::parsePosters,
                )
            ) {
                is AssignmentResult.Success -> result.value
                is AssignmentResult.Failure -> return@withContext result
            }
        } else {
            FeatureCollectionResult.EMPTY
        }
        val campaignBooth = if (type == AssignmentType.CAMPAIGN_BOOTH) {
            when (val response = execute(configuration.apiEndpointSegments("assignments", id, "campaign-booth-location"))) {
                is RawResponse.Success -> runCatching { mapDataParser.parseCampaignBooth(response.body) }
                    .fold(
                        onSuccess = { listOf(it) },
                        onFailure = {
                            return@withContext AssignmentResult.Failure(AssignmentFailure.invalidResponse())
                        },
                    )
                is RawResponse.HttpFailure -> if (response.status == 404) {
                    emptyList()
                } else {
                    return@withContext AssignmentResult.Failure(
                        AssignmentFailure.fromHttp(response.status, detailRequest = true),
                    )
                }
                RawResponse.TransportFailure ->
                    return@withContext AssignmentResult.Failure(AssignmentFailure.network())
            }
        } else {
            emptyList()
        }
        AssignmentResult.Success(
            AssignmentMapData(
                buildingCount = buildings.total,
                posterCount = posters.total,
                campaignBoothCount = campaignBooth.size,
                features = buildings.features + posters.features + campaignBooth,
            ),
        )
    }

    private fun enrichTargetArea(detail: AssignmentDetail): AssignmentDetail {
        val original = detail.summary.area ?: return detail
        if (FieldGeoJson.positions(original.geoJson).isNotEmpty()) return detail
        val areaId = original.id ?: return detail

        val direct = when (val response = execute(configuration.apiEndpointSegments("areas", areaId))) {
            is RawResponse.Success -> runCatching { parser.parseArea(response.body) }.getOrNull()
            else -> null
        }
        var resolved = original.merge(direct)
        if (FieldGeoJson.positions(resolved.geoJson).isEmpty()) {
            val campaignId = detail.summary.campaign?.id
            if (!campaignId.isNullOrBlank()) {
                val url = configuration.apiEndpointSegments("campaigns", campaignId, "areas")
                    .newBuilder()
                    .addQueryParameter("per_page", PAGE_SIZE.toString())
                    .build()
                val campaignArea = when (val response = execute(url)) {
                    is RawResponse.Success -> runCatching { parser.parseAreas(response.body) }
                        .getOrNull()
                        ?.firstOrNull { it.id == areaId }
                    else -> null
                }
                resolved = resolved.merge(campaignArea)
            }
        }
        return detail.copy(summary = detail.summary.copy(area = resolved))
    }

    private fun AreaSummary.merge(fallback: AreaSummary?): AreaSummary = if (fallback == null) {
        this
    } else {
        copy(
            name = fallback.name.takeUnless { it.startsWith("Zielgebiet #") } ?: name,
            geoJson = fallback.geoJson ?: geoJson,
            centerLatitude = fallback.centerLatitude ?: centerLatitude,
            centerLongitude = fallback.centerLongitude ?: centerLongitude,
        )
    }

    override suspend fun updateAssignmentStatus(
        id: String,
        status: AssignmentStatus,
    ): AssignmentResult<AssignmentDetail> = withContext(ioDispatcher) {
        val body = """{"status":"${status.apiValue}"}""".toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(configuration.apiEndpointSegments("assignments", id))
            .patch(body)
            .build()
        when (val response = execute(request)) {
            is RawResponse.Success -> if (response.body.isBlank()) {
                loadAssignment(id)
            } else {
                runCatching { parser.parseDetail(response.body) }
                    .fold(
                        onSuccess = { AssignmentResult.Success(it) },
                        onFailure = { AssignmentResult.Failure(AssignmentFailure.invalidResponse()) },
                    )
            }
            is RawResponse.HttpFailure -> AssignmentResult.Failure(
                AssignmentFailure.fromHttp(
                    response.status,
                    detailRequest = true,
                    mutationRequest = true,
                ),
            )
            RawResponse.TransportFailure -> AssignmentResult.Failure(AssignmentFailure.network())
        }
    }

    override suspend fun updateAssignmentBuildingStatus(
        id: String,
        status: BuildingStatus,
        clientEventKey: String?,
        knownUpdatedAt: String?,
    ): AssignmentResult<AssignmentMapFeature?> = updateAssignmentBuilding(
        id = id,
        status = status,
        clientEventKey = clientEventKey,
        knownUpdatedAt = knownUpdatedAt,
    )

    override suspend fun updateAssignmentBuilding(
        id: String,
        status: BuildingStatus?,
        notes: String?,
        includeNotes: Boolean,
        clientEventKey: String?,
        knownUpdatedAt: String?,
    ): AssignmentResult<AssignmentMapFeature?> = withContext(ioDispatcher) {
        val payload = buildJsonObject {
            status?.let { put("status", it.apiValue) }
            if (includeNotes) put("notes", notes)
            clientEventKey?.let { put("client_event_key", it) }
            knownUpdatedAt?.let { put("updated_at", it) }
        }.toString()
        val body = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(configuration.apiEndpointSegments("assignment-buildings", id))
            .patch(body)
            .build()
        when (val response = execute(request)) {
            is RawResponse.Success -> AssignmentResult.Success(
                response.body.takeIf(String::isNotBlank)?.let { body ->
                    runCatching { mapDataParser.parseBuilding(body) }.getOrNull()
                },
            )
            is RawResponse.HttpFailure -> AssignmentResult.Failure(
                AssignmentFailure.fromHttp(
                    response.status,
                    detailRequest = true,
                    mutationRequest = true,
                ),
            )
            RawResponse.TransportFailure -> AssignmentResult.Failure(AssignmentFailure.network())
        }
    }

    override suspend fun deleteAssignmentBuilding(id: String): AssignmentResult<Unit> =
        deleteLocation(configuration.apiEndpointSegments("assignment-buildings", id))

    override suspend fun createPosterLocation(
        assignmentId: String,
        input: AssignmentLocationInput,
    ): AssignmentResult<AssignmentMapFeature> = mutateLocation(
        request = Request.Builder()
            .url(configuration.apiEndpointSegments("assignments", assignmentId, "poster-locations"))
            .post(input.toRequestBody())
            .build(),
        parse = mapDataParser::parsePoster,
    )

    override suspend fun updatePosterLocation(
        id: String,
        input: AssignmentLocationInput,
    ): AssignmentResult<AssignmentMapFeature> = mutateLocation(
        request = Request.Builder()
            .url(configuration.apiEndpointSegments("poster-locations", id))
            .patch(input.toRequestBody())
            .build(),
        parse = mapDataParser::parsePoster,
        emptyResponseFallback = AssignmentMapFeature(
            id = id,
            kind = AssignmentMapFeatureKind.POSTER,
            geometryGeoJson = input.pointGeoJson(),
            resourceStatus = input.status,
            label = input.label,
            note = input.note,
        ),
    )

    override suspend fun deletePosterLocation(id: String): AssignmentResult<Unit> =
        deleteLocation(configuration.apiEndpointSegments("poster-locations", id))

    override suspend fun saveCampaignBoothLocation(
        assignmentId: String,
        existingId: String?,
        input: AssignmentLocationInput,
    ): AssignmentResult<AssignmentMapFeature> {
        val requestBuilder = Request.Builder().url(
            if (existingId == null) {
                configuration.apiEndpointSegments("assignments", assignmentId, "campaign-booth-location")
            } else {
                configuration.apiEndpointSegments("campaign-booth-locations", existingId)
            },
        )
        val request = if (existingId == null) {
            requestBuilder.post(input.toRequestBody()).build()
        } else {
            requestBuilder.patch(input.toRequestBody()).build()
        }
        return mutateLocation(
            request = request,
            parse = mapDataParser::parseCampaignBooth,
            emptyResponseFallback = existingId?.let {
                AssignmentMapFeature(
                    id = it,
                    kind = AssignmentMapFeatureKind.CAMPAIGN_BOOTH,
                    geometryGeoJson = input.pointGeoJson(),
                    resourceStatus = input.status,
                    label = input.label,
                    note = input.note,
                )
            },
        )
    }

    override suspend fun deleteCampaignBoothLocation(id: String): AssignmentResult<Unit> =
        deleteLocation(configuration.apiEndpointSegments("campaign-booth-locations", id))

    private suspend fun mutateLocation(
        request: Request,
        parse: (String) -> AssignmentMapFeature,
        emptyResponseFallback: AssignmentMapFeature? = null,
    ): AssignmentResult<AssignmentMapFeature> = withContext(ioDispatcher) {
        when (val response = execute(request)) {
            is RawResponse.Success -> if (response.body.isBlank() && emptyResponseFallback != null) {
                AssignmentResult.Success(emptyResponseFallback)
            } else {
                runCatching { parse(response.body) }.fold(
                    onSuccess = { AssignmentResult.Success(it) },
                    onFailure = { AssignmentResult.Failure(AssignmentFailure.invalidResponse()) },
                )
            }
            is RawResponse.HttpFailure -> AssignmentResult.Failure(
                AssignmentFailure.fromHttp(response.status, detailRequest = true, mutationRequest = true),
            )
            RawResponse.TransportFailure -> AssignmentResult.Failure(AssignmentFailure.network())
        }
    }

    private suspend fun deleteLocation(url: HttpUrl): AssignmentResult<Unit> = withContext(ioDispatcher) {
        val request = Request.Builder().url(url).delete().build()
        when (val response = execute(request)) {
            is RawResponse.Success -> AssignmentResult.Success(Unit)
            is RawResponse.HttpFailure -> AssignmentResult.Failure(
                AssignmentFailure.fromHttp(response.status, detailRequest = true, mutationRequest = true),
            )
            RawResponse.TransportFailure -> AssignmentResult.Failure(AssignmentFailure.network())
        }
    }

    private fun AssignmentLocationInput.toRequestBody() = buildJsonObject {
        put("lat", latitude)
        put("lng", longitude)
        label?.let { put("label", it) }
        note?.let { put("notes", it) }
        status?.let { put("status", it) }
    }.toString().toRequestBody(JSON_MEDIA_TYPE)

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

    private fun loadFeaturePages(
        pathSegments: List<String>,
        parse: (String) -> AssignmentMapFeaturePage,
    ): AssignmentResult<FeatureCollectionResult> {
        val features = mutableListOf<AssignmentMapFeature>()
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
                    val page = runCatching { parse(response.body) }.getOrElse {
                        return AssignmentResult.Failure(AssignmentFailure.invalidResponse())
                    }
                    if (page.currentPage != requestedPage) {
                        return AssignmentResult.Failure(AssignmentFailure.invalidResponse())
                    }
                    lastPage = page.lastPage
                    if (lastPage > MAX_PAGES) {
                        return AssignmentResult.Failure(AssignmentFailure.invalidResponse())
                    }
                    total = maxOf(total, page.total)
                    features += page.features
                }
                is RawResponse.HttpFailure -> return AssignmentResult.Failure(
                    AssignmentFailure.fromHttp(response.status, detailRequest = true),
                )
                RawResponse.TransportFailure -> return AssignmentResult.Failure(AssignmentFailure.network())
            }
            requestedPage++
        } while (requestedPage <= lastPage)
        return AssignmentResult.Success(
            FeatureCollectionResult(
                total = maxOf(total, features.size),
                features = features.distinctBy { "${it.kind}:${it.id}" },
            ),
        )
    }

    private fun execute(url: HttpUrl): RawResponse = execute(Request.Builder().url(url).get().build())

    private fun execute(request: Request): RawResponse = try {
        httpClient.newCall(request).execute().use { response ->
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

    private data class FeatureCollectionResult(
        val total: Int,
        val features: List<AssignmentMapFeature>,
    ) {
        companion object {
            val EMPTY = FeatureCollectionResult(0, emptyList())
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 100
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val INVALID_RESPONSE_STATUS = -1
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

sealed interface AssignmentResult<out T> {
    data class Success<T>(
        val value: T,
        val source: AssignmentDataSource = AssignmentDataSource.REMOTE,
        val cachedAtEpochMillis: Long? = null,
    ) : AssignmentResult<T>
    data class Failure(val failure: AssignmentFailure) : AssignmentResult<Nothing>
}

enum class AssignmentDataSource {
    REMOTE,
    LOCAL_CACHE,
}

enum class AssignmentFailureKind {
    NETWORK,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    SERVER,
    VALIDATION,
    CONFLICT,
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

        internal fun unsupportedMutation() = AssignmentFailure(
            AssignmentFailureKind.UNEXPECTED,
            null,
            "Die Statusänderung ist für diese Datenquelle nicht verfügbar.",
        )

        internal fun fromHttp(
            status: Int,
            detailRequest: Boolean,
            mutationRequest: Boolean = false,
        ): AssignmentFailure {
            if (status == -1) return invalidResponse()
            val kind = when {
                status == 401 -> AssignmentFailureKind.UNAUTHORIZED
                status == 403 -> AssignmentFailureKind.FORBIDDEN
                status == 404 -> AssignmentFailureKind.NOT_FOUND
                status == 409 -> AssignmentFailureKind.CONFLICT
                status == 422 -> AssignmentFailureKind.VALIDATION
                status in 500..599 -> AssignmentFailureKind.SERVER
                else -> AssignmentFailureKind.UNEXPECTED
            }
            val message = when (kind) {
                AssignmentFailureKind.UNAUTHORIZED ->
                    "Die Sitzung ist abgelaufen. Bitte erneut anmelden."
                AssignmentFailureKind.FORBIDDEN -> if (mutationRequest) {
                    "Keine Berechtigung für diese Statusänderung."
                } else {
                    "Keine Berechtigung, diese Aufträge anzuzeigen."
                }
                AssignmentFailureKind.NOT_FOUND -> if (detailRequest) {
                    "Der Auftrag wurde nicht gefunden."
                } else {
                    "Der Assignment-Endpunkt ist nicht verfügbar."
                }
                AssignmentFailureKind.SERVER ->
                    "Der Server ist vorübergehend nicht verfügbar. Bitte später erneut versuchen."
                AssignmentFailureKind.VALIDATION ->
                    "Die Statusänderung wurde vom Server abgelehnt."
                AssignmentFailureKind.CONFLICT ->
                    "Der Auftrag wurde zwischenzeitlich geändert. Bitte Daten aktualisieren."
                else -> "Aufträge konnten nicht geladen werden. Bitte erneut versuchen."
            }
            return AssignmentFailure(kind, status, message)
        }
    }
}
