package com.young.metaboliccoach.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_readings ORDER BY measuredAtEpochMillis DESC LIMIT 1")
    suspend fun getLatest(): GlucoseReadingEntity?

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE sourceId = :sourcePrefix
           OR substr(sourceId, 1, length(:sourcePrefix) + 1) = :sourcePrefix || ':'
        ORDER BY measuredAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForSource(sourcePrefix: String): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_readings ORDER BY measuredAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<GlucoseReadingEntity?>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE sourceId = :sourcePrefix
           OR substr(sourceId, 1, length(:sourcePrefix) + 1) = :sourcePrefix || ':'
        ORDER BY measuredAtEpochMillis DESC
        LIMIT 1
        """,
    )
    fun observeLatestForSource(sourcePrefix: String): Flow<GlucoseReadingEntity?>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE measuredAtEpochMillis BETWEEN :startEpochMillis AND :endEpochMillis
        ORDER BY measuredAtEpochMillis ASC
        """,
    )
    suspend fun readingsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReadingEntity>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE (sourceId = :sourcePrefix
               OR substr(sourceId, 1, length(:sourcePrefix) + 1) = :sourcePrefix || ':')
          AND measuredAtEpochMillis BETWEEN :startEpochMillis AND :endEpochMillis
        ORDER BY measuredAtEpochMillis ASC
        """,
    )
    suspend fun readingsBetweenForSource(
        sourcePrefix: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReadingEntity>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE sourceId = :sourceId
          AND measuredAtEpochMillis BETWEEN :startEpochMillis AND :endEpochMillis
        ORDER BY measuredAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun readingsBetweenExactSource(
        sourceId: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReadingEntity>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE (sourceId = :sourcePrefix
               OR substr(sourceId, 1, length(:sourcePrefix) + 1) = :sourcePrefix || ':')
          AND measuredAtEpochMillis >= :startEpochMillis
        ORDER BY measuredAtEpochMillis ASC
        """,
    )
    fun observeSinceForSource(
        sourcePrefix: String,
        startEpochMillis: Long,
    ): Flow<List<GlucoseReadingEntity>>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE sourceId = :sourceId
          AND measuredAtEpochMillis >= :startEpochMillis
        ORDER BY measuredAtEpochMillis ASC, id ASC
        """,
    )
    fun observeSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): Flow<List<GlucoseReadingEntity>>

    @Upsert
    suspend fun insertAll(readings: List<GlucoseReadingEntity>)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_snapshots ORDER BY measuredAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<ActivitySnapshotEntity?>

    @Upsert
    suspend fun upsert(snapshot: ActivitySnapshotEntity)
}

@Dao
interface InterventionDao {
    @Query("SELECT * FROM intervention_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): InterventionSessionEntity?

    @Query(
        """
        SELECT * FROM intervention_sessions
        WHERE status = 'STARTED'
        ORDER BY startedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun latestActive(): InterventionSessionEntity?

    @Query(
        """
        SELECT * FROM intervention_sessions
        WHERE status = 'STARTED'
        ORDER BY startedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    fun observeLatestActive(): Flow<InterventionSessionEntity?>

    @Query(
        """
        SELECT * FROM intervention_sessions
        WHERE status = 'COMPLETED'
          AND followUpDueAtEpochMillis IS NOT NULL
          AND followUpFinalizedAtEpochMillis IS NULL
        ORDER BY followUpDueAtEpochMillis ASC
        """,
    )
    suspend fun pendingFollowUps(): List<InterventionSessionEntity>

    @Query(
        """
        SELECT * FROM intervention_sessions
        WHERE startedAtEpochMillis >= :startEpochMillis
        ORDER BY startedAtEpochMillis DESC
        """,
    )
    fun observeSince(startEpochMillis: Long): Flow<List<InterventionSessionEntity>>

    @Query("SELECT * FROM intervention_sessions ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<InterventionSessionEntity>>

    @Upsert
    suspend fun upsert(session: InterventionSessionEntity)

    @Query(
        """
        UPDATE intervention_sessions
        SET status = 'COMPLETED',
            endedAtEpochMillis = :endedAtEpochMillis,
            followUpDueAtEpochMillis = :followUpDueAtEpochMillis,
            glucoseAfterMgDl = NULL,
            followUpReadingAtEpochMillis = NULL,
            followUpGlucoseReadingId = NULL,
            followUpGlucoseSourceId = NULL,
            followUpFinalizedAtEpochMillis = NULL
        WHERE id = :sessionId
          AND status = 'STARTED'
        """,
    )
    suspend fun completeStarted(
        sessionId: String,
        endedAtEpochMillis: Long,
        followUpDueAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE intervention_sessions
        SET glucoseAfterMgDl = :glucoseMgDl,
            followUpReadingAtEpochMillis = :readingAtEpochMillis,
            followUpGlucoseReadingId = :readingId,
            followUpGlucoseSourceId = :sourceId,
            followUpFinalizedAtEpochMillis = :finalizedAtEpochMillis
        WHERE id = :sessionId
          AND status = 'COMPLETED'
          AND followUpDueAtEpochMillis IS NOT NULL
          AND followUpFinalizedAtEpochMillis IS NULL
        """,
    )
    suspend fun finalizeFollowUp(
        sessionId: String,
        glucoseMgDl: Int?,
        readingAtEpochMillis: Long?,
        readingId: String?,
        sourceId: String?,
        finalizedAtEpochMillis: Long,
    ): Int

    @Transaction
    suspend fun startIfNoActive(
        candidate: InterventionSessionEntity,
    ): InterventionSessionEntity {
        getById(candidate.id)?.let { return it }
        latestActive()?.let { return it }
        upsert(candidate)
        return candidate
    }
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_markers ORDER BY occurredAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<MealMarkerEntity?>

    @Query("SELECT * FROM meal_markers ORDER BY occurredAtEpochMillis ASC")
    fun observeAll(): Flow<List<MealMarkerEntity>>

    @Upsert
    suspend fun upsert(marker: MealMarkerEntity)
}

@Dao
interface CoachStateDao {
    @Query("SELECT * FROM coach_state WHERE singletonId = 1")
    fun observe(): Flow<CoachStateEntity?>

    @Query("SELECT * FROM coach_state WHERE singletonId = 1")
    suspend fun get(): CoachStateEntity?

    @Upsert
    suspend fun upsert(state: CoachStateEntity)
}

@Dao
interface RecommendationSnapshotDao {
    @Query("SELECT * FROM recommendation_snapshots WHERE id = :recommendationId")
    suspend fun getById(recommendationId: String): RecommendationSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(snapshot: RecommendationSnapshotEntity): Long

    @Query(
        "DELETE FROM recommendation_snapshots " +
            "WHERE validUntilEpochMillis < :cutoffEpochMillis",
    )
    suspend fun deleteExpiredBefore(cutoffEpochMillis: Long)

    @Query("DELETE FROM recommendation_snapshots")
    suspend fun deleteAll()
}
