package com.young.metaboliccoach.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.young.metaboliccoach.core.data.di.DatabaseMigrations
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MetabolicCoachDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrateFromVersion1To8PreservesRowsAndAddsSafeDefaults() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO activity_snapshots (
                    dayStartEpochMillis,
                    stepsToday,
                    floorsToday,
                    latestHeartRateBpm,
                    activeCaloriesToday,
                    lastMovementAtEpochMillis,
                    measuredAtEpochMillis,
                    sourceId
                ) VALUES (1000, 4321, 7.5, 72, 321.0, 2000, 3000, 'legacy')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO intervention_sessions (
                    id,
                    type,
                    status,
                    startedAtEpochMillis,
                    endedAtEpochMillis,
                    targetDurationMinutes,
                    targetFloors,
                    baselineGlucoseMgDl,
                    glucoseAfterMgDl
                ) VALUES ('session', 'WALK', 'COMPLETED', 4000, 5000, 10, NULL, 140, 125)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            *DatabaseMigrations.ALL,
        ).use { database ->
            database.query(
                """
                SELECT stepsToday, floorsToday, sourceId,
                       exerciseSessionCountToday, exerciseDurationMinutesToday
                FROM activity_snapshots
                WHERE dayStartEpochMillis = 1000
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(4_321L, cursor.getLong(0))
                assertEquals(7.5, cursor.getDouble(1), 0.0)
                assertEquals("legacy", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0L, cursor.getLong(4))
            }
            database.query(
                """
                SELECT baselineGlucoseMgDl, followUpDueAtEpochMillis,
                       baselineGlucoseReadingId, followUpGlucoseReadingId,
                       recommendationId, recommendationReason,
                       recommendationAlgorithmVersion,
                       recommendationCreatedAtEpochMillis,
                       recommendationValidUntilEpochMillis,
                       triggerContextId, triggerAtEpochMillis,
                       baselineEffectiveRateMgDlPerMinute,
                       lowGlucoseThresholdMgDlAtStart
                FROM intervention_sessions
                WHERE id = 'session'
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(140, cursor.getInt(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals(true, cursor.isNull(4))
                assertEquals(true, cursor.isNull(5))
                assertEquals(true, cursor.isNull(6))
                assertEquals(true, cursor.isNull(7))
                assertEquals(true, cursor.isNull(8))
                assertEquals(true, cursor.isNull(9))
                assertEquals(true, cursor.isNull(10))
                assertEquals(true, cursor.isNull(11))
                assertEquals(true, cursor.isNull(12))
            }
            database.query(
                "SELECT COUNT(*) FROM recommendation_snapshots",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun everySupportedStartingVersionHasAValidatedPathToVersion8() {
        for (startVersion in 2..7) {
            val databaseName = "$TEST_DATABASE-$startVersion"
            helper.createDatabase(databaseName, startVersion).close()
            helper.runMigrationsAndValidate(
                databaseName,
                8,
                true,
                *DatabaseMigrations.ALL,
            ).close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
