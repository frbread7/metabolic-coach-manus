package com.young.metaboliccoach.core.data.db

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.MilestoneLifecycleState

fun GlucoseReadingEntity.toModel() = GlucoseReading(
    id = id,
    valueMgDl = valueMgDl,
    trend = GlucoseTrend.valueOf(trend),
    deltaMgDl = deltaMgDl,
    rateMgDlPerMinute = rateMgDlPerMinute,
    measuredAtEpochMillis = measuredAtEpochMillis,
    receivedAtEpochMillis = receivedAtEpochMillis,
    sourceId = sourceId,
)

fun GlucoseReading.toEntity() = GlucoseReadingEntity(
    id = id,
    valueMgDl = valueMgDl,
    trend = trend.name,
    deltaMgDl = deltaMgDl,
    rateMgDlPerMinute = rateMgDlPerMinute,
    measuredAtEpochMillis = measuredAtEpochMillis,
    receivedAtEpochMillis = receivedAtEpochMillis,
    sourceId = sourceId,
)

fun ActivitySnapshotEntity.toModel() = ActivitySnapshot(
    stepsToday = stepsToday,
    floorsToday = floorsToday,
    latestHeartRateBpm = latestHeartRateBpm,
    activeCaloriesToday = activeCaloriesToday,
    lastMovementAtEpochMillis = lastMovementAtEpochMillis,
    measuredAtEpochMillis = measuredAtEpochMillis,
    sourceId = sourceId,
    exerciseSessionCountToday = exerciseSessionCountToday,
    exerciseDurationMinutesToday = exerciseDurationMinutesToday,
)

fun InterventionSessionEntity.toModel() = InterventionSession(
    id = id,
    type = InterventionType.valueOf(type),
    status = InterventionStatus.valueOf(status),
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    targetDurationMinutes = targetDurationMinutes,
    targetFloors = targetFloors,
    baselineGlucoseMgDl = baselineGlucoseMgDl,
    baselineGlucoseReadingId = baselineGlucoseReadingId,
    baselineGlucoseMeasuredAtEpochMillis = baselineGlucoseMeasuredAtEpochMillis,
    baselineGlucoseSourceId = baselineGlucoseSourceId,
    glucoseAfterMgDl = glucoseAfterMgDl,
    followUpDueAtEpochMillis = followUpDueAtEpochMillis,
    followUpReadingAtEpochMillis = followUpReadingAtEpochMillis,
    followUpGlucoseReadingId = followUpGlucoseReadingId,
    followUpGlucoseSourceId = followUpGlucoseSourceId,
    followUpFinalizedAtEpochMillis = followUpFinalizedAtEpochMillis,
    recommendationId = recommendationId,
    recommendationReason = recommendationReason?.let(CoachReason::valueOf),
    recommendationAlgorithmVersion = recommendationAlgorithmVersion,
    recommendationCreatedAtEpochMillis = recommendationCreatedAtEpochMillis,
    recommendationValidUntilEpochMillis = recommendationValidUntilEpochMillis,
    triggerContextId = triggerContextId,
    triggerAtEpochMillis = triggerAtEpochMillis,
    baselineEffectiveRateMgDlPerMinute = baselineEffectiveRateMgDlPerMinute,
    lowGlucoseThresholdMgDlAtStart = lowGlucoseThresholdMgDlAtStart,
)

fun InterventionSession.toEntity() = InterventionSessionEntity(
    id = id,
    type = type.name,
    status = status.name,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    targetDurationMinutes = targetDurationMinutes,
    targetFloors = targetFloors,
    baselineGlucoseMgDl = baselineGlucoseMgDl,
    baselineGlucoseReadingId = baselineGlucoseReadingId,
    baselineGlucoseMeasuredAtEpochMillis = baselineGlucoseMeasuredAtEpochMillis,
    baselineGlucoseSourceId = baselineGlucoseSourceId,
    glucoseAfterMgDl = glucoseAfterMgDl,
    followUpDueAtEpochMillis = followUpDueAtEpochMillis,
    followUpReadingAtEpochMillis = followUpReadingAtEpochMillis,
    followUpGlucoseReadingId = followUpGlucoseReadingId,
    followUpGlucoseSourceId = followUpGlucoseSourceId,
    followUpFinalizedAtEpochMillis = followUpFinalizedAtEpochMillis,
    recommendationId = recommendationId,
    recommendationReason = recommendationReason?.name,
    recommendationAlgorithmVersion = recommendationAlgorithmVersion,
    recommendationCreatedAtEpochMillis = recommendationCreatedAtEpochMillis,
    recommendationValidUntilEpochMillis = recommendationValidUntilEpochMillis,
    triggerContextId = triggerContextId,
    triggerAtEpochMillis = triggerAtEpochMillis,
    baselineEffectiveRateMgDlPerMinute = baselineEffectiveRateMgDlPerMinute,
    lowGlucoseThresholdMgDlAtStart = lowGlucoseThresholdMgDlAtStart,
)

fun MealMarkerEntity.toModel() = MealMarker(id, occurredAtEpochMillis)

fun MealMarker.toEntity() = MealMarkerEntity(id, occurredAtEpochMillis)

fun RecommendationSnapshotEntity.toModel() = CoachRecommendation.Action(
    reason = CoachReason.valueOf(reason),
    id = id,
    createdAtEpochMillis = createdAtEpochMillis,
    validUntilEpochMillis = validUntilEpochMillis,
    interventionType = InterventionType.valueOf(interventionType),
    title = title,
    actionLabel = actionLabel,
    durationMinutes = durationMinutes,
    targetFloors = targetFloors,
    algorithmVersion = algorithmVersion,
    triggerContextId = triggerContextId,
    triggerAtEpochMillis = triggerAtEpochMillis,
)

fun CoachRecommendation.Action.toEntity() = RecommendationSnapshotEntity(
    id = id,
    reason = reason.name,
    createdAtEpochMillis = createdAtEpochMillis,
    validUntilEpochMillis = validUntilEpochMillis,
    interventionType = interventionType.name,
    title = title,
    actionLabel = actionLabel,
    durationMinutes = durationMinutes,
    targetFloors = targetFloors,
    algorithmVersion = algorithmVersion,
    triggerContextId = triggerContextId,
    triggerAtEpochMillis = triggerAtEpochMillis,
)

fun GlycemicPlanningMilestoneEntity.toModel() = GlycemicPlanningMilestone(
    id = id,
    title = title,
    targetGmiPercent = targetGmiPercent,
    targetProvenance = GlycemicTargetProvenance.valueOf(targetProvenance),
    targetDateEpochMillis = targetDateEpochMillis,
    originalHorizonDays = originalHorizonDays,
    lifecycleState = MilestoneLifecycleState.valueOf(lifecycleState),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
    calculationContractVersion = calculationContractVersion,
)

fun GlycemicPlanningMilestone.toEntity() = GlycemicPlanningMilestoneEntity(
    id = id,
    title = title,
    targetGmiPercent = targetGmiPercent,
    targetProvenance = targetProvenance.name,
    targetDateEpochMillis = targetDateEpochMillis,
    originalHorizonDays = originalHorizonDays,
    lifecycleState = lifecycleState.name,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
    calculationContractVersion = calculationContractVersion,
)
