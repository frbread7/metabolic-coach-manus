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
    @Query(
        "SELECT * FROM glucose_readings " +
            "ORDER BY measuredAtEpochMillis DESC, sourceId ASC, id ASC LIMIT 1",
    )
    suspend fun getLatest(): GlucoseReadingEntity?

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE sourceId = :sourcePrefix
           OR substr(sourceId, 1, length(:sourcePrefix) + 1) = :sourcePrefix || ':'
        ORDER BY measuredAtEpochMillis DESC, id ASC
        LIMIT 1
        """,
    )
    suspend fun getLatestForSource(sourcePrefix: String): GlucoseReadingEntity?

    @Query(
        "SELECT * FROM glucose_readings " +
            "ORDER BY measuredAtEpochMillis DESC, sourceId ASC, id ASC LIMIT 1",
    )
    fun observeLatest(): Flow<GlucoseReadingEntity?>

    @Query(
        """
        SELECT * FROM glucose_readings
        WHERE sourceId = :sourcePrefix
           OR substr(sourceId, 1, length(:sourcePrefix) + 1) = :sourcePrefix || ':'
        ORDER BY measuredAtEpochMillis DESC, id ASC
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

    @Query(
        """
        SELECT MIN(measuredAtEpochMillis) AS oldestReadingAtEpochMillis,
               MAX(measuredAtEpochMillis) AS newestReadingAtEpochMillis,
               COUNT(*) AS readingCount
        FROM glucose_readings
        WHERE sourceId = :sourceId
        """,
    )
    fun observeHistoryStatsForSource(sourceId: String): Flow<GlucoseHistoryStatsRow>

    @Query(
        """
        SELECT MIN(measuredAtEpochMillis) AS oldestReadingAtEpochMillis,
               MAX(measuredAtEpochMillis) AS newestReadingAtEpochMillis,
               COUNT(*) AS readingCount
        FROM glucose_readings
        WHERE sourceId = :sourceId
        """,
    )
    suspend fun getHistoryStatsForSource(sourceId: String): GlucoseHistoryStatsRow

    @Query("SELECT DISTINCT sourceId FROM glucose_readings ORDER BY sourceId ASC")
    suspend fun getSourceIds(): List<String>

    /** Prune only records older than the chosen cutoff and keep each source's newest record. */
    @Query(
        """
        DELETE FROM glucose_readings
        WHERE sourceId = :sourceId
          AND measuredAtEpochMillis < :cutoffEpochMillis
          AND id NOT IN (
              SELECT id FROM glucose_readings
              WHERE sourceId = :sourceId
              ORDER BY measuredAtEpochMillis DESC, id ASC
              LIMIT 1
          )
        """,
    )
    suspend fun deleteOlderThanForSource(
        sourceId: String,
        cutoffEpochMillis: Long,
    )

    @Upsert
    suspend fun insertAll(readings: List<GlucoseReadingEntity>)
}

@Dao
interface GlucoseHistoryDao {
    @Query("SELECT * FROM glucose_history_settings WHERE singletonId = 1 LIMIT 1")
    fun observeSettings(): Flow<GlucoseHistorySettingsEntity?>

    @Query("SELECT * FROM glucose_history_settings WHERE singletonId = 1 LIMIT 1")
    suspend fun getSettings(): GlucoseHistorySettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: GlucoseHistorySettingsEntity)

    @Query(
        "SELECT * FROM glucose_history_backfill_state " +
            "WHERE sourceId = :sourceId LIMIT 1",
    )
    fun observeBackfill(sourceId: String): Flow<GlucoseHistoryBackfillEntity?>

    @Query(
        "SELECT * FROM glucose_history_backfill_state " +
            "WHERE sourceId = :sourceId LIMIT 1",
    )
    suspend fun getBackfill(sourceId: String): GlucoseHistoryBackfillEntity?

    @Upsert
    suspend fun upsertBackfill(state: GlucoseHistoryBackfillEntity)

    @Query("DELETE FROM glucose_history_backfill_state")
    suspend fun deleteAllBackfill()
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
        "SELECT * FROM intervention_sessions WHERE recommendationId = :recommendationId " +
            "ORDER BY startedAtEpochMillis ASC LIMIT 1",
    )
    suspend fun getByRecommendationId(recommendationId: String): InterventionSessionEntity?

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

    @Query("SELECT * FROM coach_state WHERE singletonId = 1")
    suspend fun getCoachState(): CoachStateEntity?

    @Upsert
    suspend fun upsertCoachState(state: CoachStateEntity)

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

    @Transaction
    suspend fun startForRecommendationIfAvailable(
        candidate: InterventionSessionEntity,
        recommendationId: String,
        currentDayStartEpochMillis: Long,
    ): InterventionSessionEntity? {
        getByRecommendationId(recommendationId)?.let { return it }
        getById(candidate.id)?.let { return it }
        latestActive()?.let { return it }
        val storedState = getCoachState()
        val currentState = if (
            storedState == null ||
            storedState.notificationDayStartEpochMillis != currentDayStartEpochMillis
        ) {
            CoachStateEntity(
                lastRecommendationAtEpochMillis = storedState?.lastRecommendationAtEpochMillis,
                lastRecommendationId = storedState?.lastRecommendationId,
                snoozedUntilEpochMillis = storedState?.snoozedUntilEpochMillis,
                notificationDayStartEpochMillis = currentDayStartEpochMillis,
                notificationsSentToday = 0,
                deliveryCountForLastRecommendation =
                    storedState?.deliveryCountForLastRecommendation ?: 0,
                consumedRecommendationId = storedState?.consumedRecommendationId,
            )
        } else {
            storedState
        }
        if (currentState.consumedRecommendationId == recommendationId) return null
        upsertCoachState(currentState.copy(consumedRecommendationId = recommendationId))
        upsert(candidate)
        return candidate
    }
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_markers ORDER BY occurredAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<MealMarkerEntity?>

    @Query("SELECT * FROM meal_markers ORDER BY occurredAtEpochMillis DESC LIMIT 1")
    suspend fun latest(): MealMarkerEntity?

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

@Dao
interface GlycemicPlanningMilestoneDao {
    @Query(
        "SELECT * FROM glycemic_planning_milestones " +
            "ORDER BY lifecycleState ASC, targetDateEpochMillis ASC, " +
            "createdAtEpochMillis ASC, id ASC",
    )
    fun observeAll(): Flow<List<GlycemicPlanningMilestoneEntity>>

    @Query(
        "SELECT * FROM glycemic_planning_milestones " +
            "ORDER BY lifecycleState ASC, targetDateEpochMillis ASC, " +
            "createdAtEpochMillis ASC, id ASC",
    )
    suspend fun getAll(): List<GlycemicPlanningMilestoneEntity>

    @Query("SELECT * FROM glycemic_planning_milestones WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GlycemicPlanningMilestoneEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(milestone: GlycemicPlanningMilestoneEntity): Long

    @Upsert
    suspend fun upsert(milestone: GlycemicPlanningMilestoneEntity)

    @Query(
        "UPDATE glycemic_planning_milestones " +
            "SET lifecycleState = 'ARCHIVED', archivedAtEpochMillis = :nowEpochMillis, " +
            "updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE id = :id AND lifecycleState = 'ACTIVE'",
    )
    suspend fun archive(id: String, nowEpochMillis: Long): Int

    @Query("DELETE FROM glycemic_planning_milestones WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM glycemic_planning_milestones")
    suspend fun deleteAll()
}
