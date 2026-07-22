package de.oliveroehme.campaignfield.domain

enum class SyncQueueStatus(val displayName: String) {
    PENDING("Offen"),
    SYNCING("Sync läuft"),
    SYNCED("Gesynct"),
    FAILED("Fehler"),
}

enum class SyncEventKind(val displayName: String) {
    ASSIGNMENT_STATUS_UPDATE("Assignment-Status"),
}

enum class SyncFailureKind {
    AUTHENTICATION,
    PERMISSION,
    VALIDATION,
    CONFLICT,
    INVALID_DATA,
    UNKNOWN,
}

data class SyncQueueItem(
    val id: String,
    val assignmentId: String,
    val kind: SyncEventKind,
    val targetStatus: AssignmentStatus,
    val previousStatus: AssignmentStatus,
    val status: SyncQueueStatus,
    val attempts: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long? = null,
    val syncedAtEpochMillis: Long? = null,
    val failureKind: SyncFailureKind? = null,
    val lastError: String? = null,
)

data class SyncQueueSummary(
    val pending: Int = 0,
    val syncing: Int = 0,
    val failed: Int = 0,
    val synced: Int = 0,
) {
    val unresolved: Int get() = pending + syncing
}

fun List<SyncQueueItem>.toSyncQueueSummary(): SyncQueueSummary = SyncQueueSummary(
    pending = count { it.status == SyncQueueStatus.PENDING },
    syncing = count { it.status == SyncQueueStatus.SYNCING },
    failed = count { it.status == SyncQueueStatus.FAILED },
    synced = count { it.status == SyncQueueStatus.SYNCED },
)
