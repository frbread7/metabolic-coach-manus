package com.young.metaboliccoach.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "glucose_readings",
    indices = [
        Index(value = ["measuredAtEpochMillis"]),
        Index(value = ["sourceId", "measuredAtEpochMillis"]),
    ],
)
data class GlucoseReadingEntity(
    @PrimaryKey val id: String,
    val valueMgDl: Int,
    val trend: String,
    val deltaMgDl: Int?,
    val rateMgDlPerMinute: Double?,
    val measuredAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val sourceId: String,
)

@Entity(tableName = "glucose_history_settings")
data class GlucoseHistorySettingsEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val retentionPolicy: String,
    val retentionConfirmed: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "glucose_history_backfill_state")
data class GlucoseHistoryBackfillEntity(
    @PrimaryKey val sourceId: String,
    val nextBackfillEndEpochMillis: Long?,
    val status: String,
    val lastError: String?,
    val updatedAtEpochMillis: Long,
)

data class GlucoseHistoryStatsRow(
    val oldestReadingAtEpochMillis: Long?,
    val newestReadingAtEpochMillis: Long?,
    val readingCount: Long,
)

@Entity(tableName = "activity_snapshots")
data class ActivitySnapshotEntity(
    @PrimaryKey val dayStartEpochMillis: Long,
    val stepsToday: Long,
    val floorsToday: Double,
    val latestHeartRateBpm: Long?,
    val activeCaloriesToday: Double?,
    val lastMovementAtEpochMillis: Long?,
    val measuredAtEpochMillis: Long,
    val sourceId: String,
    val exerciseSessionCountToday: Int,
    val exerciseDurationMinutesToday: Long,
)

@Entity(
    tableName = "intervention_sessions",
    indices = [
        Index(value = ["status", "startedAtEpochMillis"]),
        Index(value = ["status", "followUpDueAtEpochMillis"]),
    ],
)
data class InterventionSessionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val status: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val targetDurationMinutes: Int?,
    val targetFloors: Int?,
    val baselineGlucoseMgDl: Int?,
    val baselineGlucoseReadingId: String?,
    val baselineGlucoseMeasuredAtEpochMillis: Long?,
    val baselineGlucoseSourceId: String?,
    val glucoseAfterMgDl: Int?,
    val followUpDueAtEpochMillis: Long?,
    val followUpReadingAtEpochMillis: Long?,
    val followUpGlucoseReadingId: String?,
    val followUpGlucoseSourceId: String?,
    val followUpFinalizedAtEpochMillis: Long?,
    val recommendationId: String? = null,
    val recommendationReason: String? = null,
    val recommendationAlgorithmVersion: Int? = null,
    val recommendationCreatedAtEpochMillis: Long? = null,
    val recommendationValidUntilEpochMillis: Long? = null,
    val triggerContextId: String? = null,
    val triggerAtEpochMillis: Long? = null,
    val baselineEffectiveRateMgDlPerMinute: Double? = null,
    val lowGlucoseThresholdMgDlAtStart: Int? = null,
)

@Entity(tableName = "meal_markers")
data class MealMarkerEntity(
    @PrimaryKey val id: String,
    val occurredAtEpochMillis: Long,
)

@Entity(tableName = "coach_state")
data class CoachStateEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val lastRecommendationAtEpochMillis: Long?,
    val lastRecommendationId: String?,
    val snoozedUntilEpochMillis: Long?,
    val notificationDayStartEpochMillis: Long,
    val notificationsSentToday: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "recommendation_snapshots",
    indices = [Index(value = ["validUntilEpochMillis"])],
)
data class RecommendationSnapshotEntity(
    @PrimaryKey val id: String,
    val reason: String,
    val createdAtEpochMillis: Long,
    val validUntilEpochMillis: Long,
    val interventionType: String,
    val title: String,
    val actionLabel: String,
    val durationMinutes: Int?,
    val targetFloors: Int?,
    val algorithmVersion: Int,
    val triggerContextId: String?,
    val triggerAtEpochMillis: Long?,
)

@Entity(
    tableName = "glycemic_planning_milestones",
    indices = [
        Index(value = ["lifecycleState", "targetDateEpochMillis"]),
        Index(value = ["createdAtEpochMillis"]),
    ],
)
data class GlycemicPlanningMilestoneEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val targetGmiPercent: Double,
    val targetProvenance: String,
    val targetDateEpochMillis: Long,
    val originalHorizonDays: Int,
    val lifecycleState: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
    val calculationContractVersion: Int,
)
