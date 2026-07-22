package de.oliveroehme.campaignfield.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampaignFieldDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CampaignFieldDatabase::class.java,
    )

    @Test
    fun everyHistoricalSchemaMigratesToCurrentSchema() {
        (1..3).forEach { version ->
            val databaseName = "migration-$version-current"
            helper.createDatabase(databaseName, version).close()

            helper.runMigrationsAndValidate(
                databaseName,
                4,
                true,
                *CampaignFieldDatabase.ALL_MIGRATIONS,
            ).close()
        }
    }

    @Test
    fun migrationFromVersionOnePreservesCacheAndQueueData() {
        val databaseName = "migration-1-data"
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO assignment_cache (
                    assignmentId, summaryJson, detailJson, cachedAtEpochMillis
                ) VALUES (?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "assignment-1",
                    "{\"id\":1}",
                    "{\"title\":\"Alt\"}",
                    1_000L,
                ),
            )
            execSQL(
                """
                INSERT INTO sync_queue (
                    id, assignmentId, kind, targetStatus, previousStatus, status, attempts,
                    createdAtEpochMillis, updatedAtEpochMillis, lastAttemptAtEpochMillis,
                    syncedAtEpochMillis, failureKind, lastError
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "event-1",
                    "assignment-1",
                    "ASSIGNMENT_STATUS_UPDATE",
                    "active",
                    "ready",
                    "PENDING",
                    2,
                    1_001L,
                    1_002L,
                    1_003L,
                    null,
                    null,
                    "Netzwerkfehler",
                ),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            *CampaignFieldDatabase.ALL_MIGRATIONS,
        ).use { database ->
            database.query(
                """
                SELECT summaryJson, detailJson, cachedAtEpochMillis, mapDataJson
                FROM assignment_cache WHERE assignmentId = 'assignment-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{\"id\":1}", cursor.getString(0))
                assertEquals("{\"title\":\"Alt\"}", cursor.getString(1))
                assertEquals(1_000L, cursor.getLong(2))
                assertTrue(cursor.isNull(3))
            }
            database.query(
                """
                SELECT targetStatus, previousStatus, status, attempts, lastError,
                    subjectId, payloadJson
                FROM sync_queue WHERE id = 'event-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("active", cursor.getString(0))
                assertEquals("ready", cursor.getString(1))
                assertEquals("PENDING", cursor.getString(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals("Netzwerkfehler", cursor.getString(4))
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
            }
        }
    }
}
