package de.oliveroehme.campaignfield.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assignment_cache")
data class AssignmentCacheEntity(
    @PrimaryKey val assignmentId: String,
    val summaryJson: String,
    val detailJson: String?,
    val cachedAtEpochMillis: Long,
    val mapDataJson: String? = null,
)

@Entity(
    tableName = "assignment_list_entries",
    foreignKeys = [
        ForeignKey(
            entity = AssignmentCacheEntity::class,
            parentColumns = ["assignmentId"],
            childColumns = ["assignmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assignmentId")],
)
data class AssignmentListEntryEntity(
    @PrimaryKey val assignmentId: String,
    val position: Int,
)

@Entity(
    tableName = "sync_queue",
    indices = [Index("status"), Index("assignmentId")],
)
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val assignmentId: String,
    val kind: String,
    val targetStatus: String,
    val previousStatus: String,
    val status: String,
    val attempts: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long?,
    val syncedAtEpochMillis: Long?,
    val failureKind: String?,
    val lastError: String?,
)

@Dao
abstract class OfflineDao {
    @Query(
        """
        SELECT assignment_cache.* FROM assignment_cache
        INNER JOIN assignment_list_entries
            ON assignment_list_entries.assignmentId = assignment_cache.assignmentId
        ORDER BY assignment_list_entries.position ASC
        """,
    )
    abstract fun observeAssignmentList(): Flow<List<AssignmentCacheEntity>>

    @Query(
        """
        SELECT assignment_cache.* FROM assignment_cache
        INNER JOIN assignment_list_entries
            ON assignment_list_entries.assignmentId = assignment_cache.assignmentId
        ORDER BY assignment_list_entries.position ASC
        """,
    )
    abstract suspend fun readAssignmentList(): List<AssignmentCacheEntity>

    @Query("SELECT * FROM assignment_cache WHERE assignmentId = :assignmentId LIMIT 1")
    abstract fun observeAssignment(assignmentId: String): Flow<AssignmentCacheEntity?>

    @Query("SELECT * FROM assignment_cache WHERE assignmentId = :assignmentId LIMIT 1")
    abstract suspend fun readAssignment(assignmentId: String): AssignmentCacheEntity?

    @Query(
        "SELECT assignmentId FROM assignment_cache " +
            "WHERE detailJson IS NOT NULL AND mapDataJson IS NOT NULL",
    )
    abstract fun observeOfflineReadyAssignmentIds(): Flow<List<String>>

    @Upsert
    protected abstract suspend fun upsertAssignment(entity: AssignmentCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertListEntries(entries: List<AssignmentListEntryEntity>)

    @Query("DELETE FROM assignment_list_entries")
    protected abstract suspend fun deleteListEntries()

    @Transaction
    open suspend fun replaceAssignmentList(entities: List<AssignmentCacheEntity>) {
        deleteListEntries()
        entities.forEachIndexed { index, entity ->
            val existing = readAssignment(entity.assignmentId)
            upsertAssignment(
                entity.copy(
                    detailJson = entity.detailJson ?: existing?.detailJson,
                    mapDataJson = entity.mapDataJson ?: existing?.mapDataJson,
                ),
            )
            insertListEntries(listOf(AssignmentListEntryEntity(entity.assignmentId, index)))
        }
    }

    @Transaction
    open suspend fun storeAssignment(entity: AssignmentCacheEntity) {
        val existing = readAssignment(entity.assignmentId)
        upsertAssignment(
            entity.copy(
                detailJson = entity.detailJson ?: existing?.detailJson,
                mapDataJson = entity.mapDataJson ?: existing?.mapDataJson,
            ),
        )
    }

    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMillis ASC")
    abstract fun observeQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMillis ASC")
    abstract suspend fun readQueue(): List<SyncQueueEntity>

    @Query(
        """
        SELECT * FROM sync_queue
        WHERE status = 'PENDING'
        ORDER BY createdAtEpochMillis ASC
        """,
    )
    abstract suspend fun readPendingQueue(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE id = :eventId LIMIT 1")
    protected abstract suspend fun readQueueEvent(eventId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertQueueEvent(entity: SyncQueueEntity)

    @Upsert
    protected abstract suspend fun upsertQueueEvent(entity: SyncQueueEntity)

    @Transaction
    open suspend fun claimQueueEvent(eventId: String, now: Long): SyncQueueEntity? {
        val current = readQueueEvent(eventId)?.takeIf { it.status == "PENDING" } ?: return null
        return current.copy(
            status = "SYNCING",
            attempts = current.attempts + 1,
            updatedAtEpochMillis = now,
            lastAttemptAtEpochMillis = now,
            failureKind = null,
            lastError = null,
        ).also { upsertQueueEvent(it) }
    }

    @Query(
        """
        UPDATE sync_queue SET
            status = :status,
            updatedAtEpochMillis = :now,
            syncedAtEpochMillis = :syncedAt,
            failureKind = :failureKind,
            lastError = :lastError
        WHERE id = :eventId
        """,
    )
    abstract suspend fun updateQueueEventStatus(
        eventId: String,
        status: String,
        now: Long,
        syncedAt: Long?,
        failureKind: String?,
        lastError: String?,
    )

    @Query(
        """
        UPDATE sync_queue SET
            status = 'PENDING',
            updatedAtEpochMillis = :now,
            syncedAtEpochMillis = NULL,
            failureKind = NULL,
            lastError = NULL
        WHERE id = :eventId AND status = 'FAILED'
        """,
    )
    abstract suspend fun retryQueueEvent(eventId: String, now: Long): Int

    @Query(
        """
        UPDATE sync_queue SET status = 'PENDING', updatedAtEpochMillis = :now
        WHERE status = 'SYNCING'
          AND COALESCE(lastAttemptAtEpochMillis, updatedAtEpochMillis) < :cutoff
        """,
    )
    abstract suspend fun resetStaleSyncingEvents(cutoff: Long, now: Long): Int

    @Query(
        """
        DELETE FROM sync_queue
        WHERE status = 'SYNCED'
          AND COALESCE(syncedAtEpochMillis, updatedAtEpochMillis) < :cutoff
        """,
    )
    abstract suspend fun cleanupSyncedEvents(cutoff: Long): Int
}

@Database(
    entities = [
        AssignmentCacheEntity::class,
        AssignmentListEntryEntity::class,
        SyncQueueEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CampaignFieldDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        const val DATABASE_NAME = "campaign-field.db"

        fun create(context: Context): CampaignFieldDatabase = Room.databaseBuilder(
            context.applicationContext,
            CampaignFieldDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `assignment_cache` ADD COLUMN `mapDataJson` TEXT")
            }
        }
    }
}
