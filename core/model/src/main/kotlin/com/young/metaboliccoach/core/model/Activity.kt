package com.young.metaboliccoach.core.model

data class ActivitySnapshot(
    val stepsToday: Long,
    val floorsToday: Double,
    val latestHeartRateBpm: Long?,
    val activeCaloriesToday: Double?,
    val lastMovementAtEpochMillis: Long?,
    val measuredAtEpochMillis: Long,
    val sourceId: String,
    val exerciseSessionCountToday: Int = 0,
    val exerciseDurationMinutesToday: Long = 0,
)

enum class InterventionType {
    WALK,
    STAIRS,
}

enum class InterventionStatus {
    STARTED,
    COMPLETED,
    CANCELLED,
    SNOOZED,
}

data class InterventionSession(
    val id: String,
    val type: InterventionType,
    val status: InterventionStatus,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val targetDurationMinutes: Int?,
    val targetFloors: Int?,
    val baselineGlucoseMgDl: Int?,
    val baselineGlucoseReadingId: String? = null,
    val baselineGlucoseMeasuredAtEpochMillis: Long? = null,
    val baselineGlucoseSourceId: String? = null,
    val glucoseAfterMgDl: Int?,
    val followUpDueAtEpochMillis: Long? = null,
    val followUpReadingAtEpochMillis: Long? = null,
    val followUpGlucoseReadingId: String? = null,
    val followUpGlucoseSourceId: String? = null,
    val followUpFinalizedAtEpochMillis: Long? = null,
    val recommendationId: String? = null,
    val recommendationReason: CoachReason? = null,
    val recommendationAlgorithmVersion: Int? = null,
    val recommendationCreatedAtEpochMillis: Long? = null,
    val recommendationValidUntilEpochMillis: Long? = null,
    val triggerContextId: String? = null,
    val triggerAtEpochMillis: Long? = null,
    val baselineEffectiveRateMgDlPerMinute: Double? = null,
    val lowGlucoseThresholdMgDlAtStart: Int? = null,
)

data class MealMarker(
    val id: String,
    val occurredAtEpochMillis: Long,
)

data class DailySummary(
    val dayStartEpochMillis: Long,
    val stableGlucosePercent: Int?,
    val completedWalks: Int,
    val completedStairSessions: Int,
    val steps: Long,
    val floors: Double,
    val exerciseSessionCount: Int = 0,
    val exerciseDurationMinutes: Long = 0,
)
