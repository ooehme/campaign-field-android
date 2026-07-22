package de.oliveroehme.campaignfield.database

import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.SyncEventKind
import de.oliveroehme.campaignfield.domain.SyncFailureKind
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.SyncQueueStatus
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CachedValue<T>(
    val value: T,
    val cachedAtEpochMillis: Long,
)

interface OfflineStore {
    fun observeAssignments(): Flow<CachedValue<AssignmentPage>?>
    fun observeAssignment(id: String): Flow<CachedValue<AssignmentDetail>?>
    fun observeAssignmentMapData(id: String): Flow<CachedValue<AssignmentMapData>?>
    fun observeOfflineReadyAssignmentIds(): Flow<Set<String>>
    fun observeQueue(): Flow<List<SyncQueueItem>>

    suspend fun readAssignments(): CachedValue<AssignmentPage>?
    suspend fun readAssignment(id: String): CachedValue<AssignmentDetail>?
    suspend fun readAssignmentMapData(id: String): CachedValue<AssignmentMapData>?
    suspend fun storeAssignments(page: AssignmentPage)
    suspend fun storeAssignment(detail: AssignmentDetail)
    suspend fun storeAssignmentMapData(id: String, mapData: AssignmentMapData)

    suspend fun enqueueAssignmentStatusUpdate(
        assignmentId: String,
        previousStatus: AssignmentStatus,
        targetStatus: AssignmentStatus,
    ): SyncQueueItem

    suspend fun readPendingQueue(): List<SyncQueueItem>
    suspend fun claimQueueEvent(eventId: String): SyncQueueItem?
    suspend fun markQueueEventSynced(eventId: String)
    suspend fun markQueueEventRetryable(eventId: String, message: String)
    suspend fun markQueueEventFailed(
        eventId: String,
        failureKind: SyncFailureKind,
        message: String,
    )
    suspend fun retryQueueEvent(eventId: String): Boolean
    suspend fun resetStaleSyncingEvents(): Int
    suspend fun cleanupSyncedEvents(): Int
    fun clearBlocking(): Boolean
}

class RoomOfflineStore(
    private val database: CampaignFieldDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OfflineStore {
    private val dao = database.offlineDao()

    override fun observeAssignments(): Flow<CachedValue<AssignmentPage>?> = combine(
        dao.observeAssignmentList(),
        dao.observeQueue(),
    ) { entities, queue -> decodeAssignmentPage(entities, queue) }

    override fun observeAssignment(id: String): Flow<CachedValue<AssignmentDetail>?> = combine(
        dao.observeAssignment(id),
        dao.observeQueue(),
    ) { entity, queue -> entity?.let { decodeDetail(it, queue) } }

    override fun observeAssignmentMapData(id: String): Flow<CachedValue<AssignmentMapData>?> =
        dao.observeAssignment(id).map { entity -> entity?.let(::decodeMapData) }

    override fun observeOfflineReadyAssignmentIds(): Flow<Set<String>> =
        dao.observeOfflineReadyAssignmentIds().map(List<String>::toSet)

    override fun observeQueue(): Flow<List<SyncQueueItem>> =
        dao.observeQueue().map { entities -> entities.mapNotNull(::decodeQueueItem) }

    override suspend fun readAssignments(): CachedValue<AssignmentPage>? =
        decodeAssignmentPage(dao.readAssignmentList(), dao.readQueue())

    override suspend fun readAssignment(id: String): CachedValue<AssignmentDetail>? =
        dao.readAssignment(id)?.let { decodeDetail(it, dao.readQueue()) }

    override suspend fun readAssignmentMapData(id: String): CachedValue<AssignmentMapData>? =
        dao.readAssignment(id)?.let(::decodeMapData)

    override suspend fun storeAssignments(page: AssignmentPage) {
        val now = clock()
        dao.replaceAssignmentList(
            page.items.map { summary ->
                AssignmentCacheEntity(
                    assignmentId = summary.id,
                    summaryJson = json.encodeToString(summary),
                    detailJson = null,
                    cachedAtEpochMillis = now,
                )
            },
        )
    }

    override suspend fun storeAssignment(detail: AssignmentDetail) {
        dao.storeAssignment(
            AssignmentCacheEntity(
                assignmentId = detail.summary.id,
                summaryJson = json.encodeToString(detail.summary),
                detailJson = json.encodeToString(detail),
                cachedAtEpochMillis = clock(),
            ),
        )
    }

    override suspend fun storeAssignmentMapData(id: String, mapData: AssignmentMapData) {
        val existing = dao.readAssignment(id) ?: return
        dao.storeAssignment(
            existing.copy(
                mapDataJson = json.encodeToString(mapData),
                cachedAtEpochMillis = clock(),
            ),
        )
    }

    override suspend fun enqueueAssignmentStatusUpdate(
        assignmentId: String,
        previousStatus: AssignmentStatus,
        targetStatus: AssignmentStatus,
    ): SyncQueueItem {
        val now = clock()
        val entity = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            assignmentId = assignmentId,
            kind = SyncEventKind.ASSIGNMENT_STATUS_UPDATE.name,
            targetStatus = targetStatus.apiValue,
            previousStatus = previousStatus.apiValue,
            status = SyncQueueStatus.PENDING.name,
            attempts = 0,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            lastAttemptAtEpochMillis = null,
            syncedAtEpochMillis = null,
            failureKind = null,
            lastError = null,
        )
        dao.insertQueueEvent(entity)
        return requireNotNull(decodeQueueItem(entity))
    }

    override suspend fun readPendingQueue(): List<SyncQueueItem> =
        dao.readPendingQueue().mapNotNull(::decodeQueueItem)

    override suspend fun claimQueueEvent(eventId: String): SyncQueueItem? =
        dao.claimQueueEvent(eventId, clock())?.let(::decodeQueueItem)

    override suspend fun markQueueEventSynced(eventId: String) {
        val now = clock()
        dao.updateQueueEventStatus(
            eventId = eventId,
            status = SyncQueueStatus.SYNCED.name,
            now = now,
            syncedAt = now,
            failureKind = null,
            lastError = null,
        )
    }

    override suspend fun markQueueEventRetryable(eventId: String, message: String) {
        dao.updateQueueEventStatus(
            eventId = eventId,
            status = SyncQueueStatus.PENDING.name,
            now = clock(),
            syncedAt = null,
            failureKind = null,
            lastError = message,
        )
    }

    override suspend fun markQueueEventFailed(
        eventId: String,
        failureKind: SyncFailureKind,
        message: String,
    ) {
        dao.updateQueueEventStatus(
            eventId = eventId,
            status = SyncQueueStatus.FAILED.name,
            now = clock(),
            syncedAt = null,
            failureKind = failureKind.name,
            lastError = message,
        )
    }

    override suspend fun retryQueueEvent(eventId: String): Boolean =
        dao.retryQueueEvent(eventId, clock()) > 0

    override suspend fun resetStaleSyncingEvents(): Int {
        val now = clock()
        return dao.resetStaleSyncingEvents(now - SYNCING_TIMEOUT_MS, now)
    }

    override suspend fun cleanupSyncedEvents(): Int =
        dao.cleanupSyncedEvents(clock() - SYNCED_RETENTION_MS)

    override fun clearBlocking(): Boolean = runCatching {
        runBlocking(Dispatchers.IO) { database.clearAllTables() }
    }.isSuccess

    private fun decodeAssignmentPage(
        entities: List<AssignmentCacheEntity>,
        queue: List<SyncQueueEntity>,
    ): CachedValue<AssignmentPage>? {
        if (entities.isEmpty()) return null
        val overlays = pendingStatusOverlays(queue)
        val items = entities.mapNotNull { entity ->
            runCatching { json.decodeFromString<AssignmentSummary>(entity.summaryJson) }
                .getOrNull()
                ?.withStatusOverlay(overlays[entity.assignmentId])
        }
        if (items.isEmpty()) return null
        return CachedValue(
            value = AssignmentPage(items = items, total = items.size, perPage = items.size),
            cachedAtEpochMillis = entities.maxOf(AssignmentCacheEntity::cachedAtEpochMillis),
        )
    }

    private fun decodeDetail(
        entity: AssignmentCacheEntity,
        queue: List<SyncQueueEntity>,
    ): CachedValue<AssignmentDetail>? {
        val detail = entity.detailJson
            ?.let { encoded ->
                runCatching { json.decodeFromString<AssignmentDetail>(encoded) }.getOrNull()
            }
            ?: runCatching {
                AssignmentDetail(json.decodeFromString<AssignmentSummary>(entity.summaryJson))
            }.getOrNull()
            ?: return null
        val overlay = pendingStatusOverlays(queue)[entity.assignmentId]
        return CachedValue(
            value = detail.copy(summary = detail.summary.withStatusOverlay(overlay)),
            cachedAtEpochMillis = entity.cachedAtEpochMillis,
        )
    }

    private fun decodeMapData(entity: AssignmentCacheEntity): CachedValue<AssignmentMapData>? {
        val value = entity.mapDataJson
            ?.let { encoded ->
                runCatching { json.decodeFromString<AssignmentMapData>(encoded) }.getOrNull()
            }
            ?: return null
        return CachedValue(value, entity.cachedAtEpochMillis)
    }

    private fun pendingStatusOverlays(queue: List<SyncQueueEntity>): Map<String, AssignmentStatus> =
        queue.asSequence()
            .filter { it.kind == SyncEventKind.ASSIGNMENT_STATUS_UPDATE.name }
            .filter { it.status == SyncQueueStatus.PENDING.name || it.status == SyncQueueStatus.SYNCING.name }
            .sortedBy(SyncQueueEntity::createdAtEpochMillis)
            .mapNotNull { event ->
                AssignmentStatus.fromApi(event.targetStatus)
                    .takeUnless { it == AssignmentStatus.UNKNOWN }
                    ?.let { event.assignmentId to it }
            }
            .toMap()

    private fun AssignmentSummary.withStatusOverlay(status: AssignmentStatus?): AssignmentSummary =
        if (status == null || this.status == status) this else copy(status = status)

    private fun decodeQueueItem(entity: SyncQueueEntity): SyncQueueItem? = runCatching {
        SyncQueueItem(
            id = entity.id,
            assignmentId = entity.assignmentId,
            kind = SyncEventKind.valueOf(entity.kind),
            targetStatus = AssignmentStatus.fromApi(entity.targetStatus),
            previousStatus = AssignmentStatus.fromApi(entity.previousStatus),
            status = SyncQueueStatus.valueOf(entity.status),
            attempts = entity.attempts,
            createdAtEpochMillis = entity.createdAtEpochMillis,
            updatedAtEpochMillis = entity.updatedAtEpochMillis,
            lastAttemptAtEpochMillis = entity.lastAttemptAtEpochMillis,
            syncedAtEpochMillis = entity.syncedAtEpochMillis,
            failureKind = entity.failureKind?.let(SyncFailureKind::valueOf),
            lastError = entity.lastError,
        )
    }.getOrNull()

    private companion object {
        const val SYNCING_TIMEOUT_MS = 5 * 60_000L
        const val SYNCED_RETENTION_MS = 24 * 60 * 60_000L
    }
}
