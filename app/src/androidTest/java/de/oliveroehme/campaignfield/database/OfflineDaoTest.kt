package de.oliveroehme.campaignfield.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDaoTest {
    private lateinit var database: CampaignFieldDatabase
    private lateinit var dao: OfflineDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CampaignFieldDatabase::class.java,
        ).build()
        dao = database.offlineDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun equalTimestampsKeepInsertionOrder() = runBlocking {
        dao.insertQueueEvent(queueEvent(id = "z-first"))
        dao.insertQueueEvent(queueEvent(id = "a-second"))

        assertEquals(listOf("z-first", "a-second"), dao.readPendingQueue().map { it.id })
    }

    @Test
    fun queueEventCanOnlyBeClaimedOnce() = runBlocking {
        dao.insertQueueEvent(queueEvent(id = "event-1"))

        val claimed = dao.claimQueueEvent("event-1", now = 2_000L)
        val duplicateClaim = dao.claimQueueEvent("event-1", now = 2_001L)

        assertEquals("SYNCING", claimed?.status)
        assertEquals(1, claimed?.attempts)
        assertEquals(2_000L, claimed?.lastAttemptAtEpochMillis)
        assertNull(duplicateClaim)
    }

    @Test
    fun staleRecoveryAndRetentionOnlyTouchEligibleEvents() = runBlocking {
        dao.insertQueueEvent(
            queueEvent(id = "stale", status = "SYNCING", updatedAt = 100L, lastAttemptAt = 100L),
        )
        dao.insertQueueEvent(
            queueEvent(id = "fresh", status = "SYNCING", updatedAt = 900L, lastAttemptAt = 900L),
        )
        dao.insertQueueEvent(
            queueEvent(id = "old-synced", status = "SYNCED", updatedAt = 100L, syncedAt = 100L),
        )
        dao.insertQueueEvent(
            queueEvent(id = "new-synced", status = "SYNCED", updatedAt = 900L, syncedAt = 900L),
        )

        assertEquals(1, dao.resetStaleSyncingEvents(cutoff = 500L, now = 1_000L))
        assertEquals(1, dao.cleanupSyncedEvents(cutoff = 500L))

        val remaining = dao.readQueue().associateBy { it.id }
        assertEquals("PENDING", remaining.getValue("stale").status)
        assertEquals("SYNCING", remaining.getValue("fresh").status)
        assertEquals("SYNCED", remaining.getValue("new-synced").status)
        assertNull(remaining["old-synced"])
    }

    private fun queueEvent(
        id: String,
        status: String = "PENDING",
        updatedAt: Long = 1_000L,
        lastAttemptAt: Long? = null,
        syncedAt: Long? = null,
    ) = SyncQueueEntity(
        id = id,
        assignmentId = "assignment-1",
        kind = "ASSIGNMENT_STATUS_UPDATE",
        targetStatus = "active",
        previousStatus = "ready",
        status = status,
        attempts = 0,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = updatedAt,
        lastAttemptAtEpochMillis = lastAttemptAt,
        syncedAtEpochMillis = syncedAt,
        failureKind = null,
        lastError = null,
    )
}
