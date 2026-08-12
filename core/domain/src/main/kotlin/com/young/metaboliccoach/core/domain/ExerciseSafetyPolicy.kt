package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.WatchState
import java.time.Instant
import java.time.ZoneId

enum class ExerciseSafetyStatus {
    SAFE,
    MISSING,
    FUTURE_DATED,
    STALE,
    BELOW_LOW_THRESHOLD,
    FALLING_QUICKLY,
}

object ExerciseSafetyPolicy {
    fun evaluate(
        reading: GlucoseReading?,
        settings: CoachSettings,
        nowEpochMillis: Long,
    ): ExerciseSafetyStatus {
        reading ?: return ExerciseSafetyStatus.MISSING
        val ageMillis = nowEpochMillis - reading.measuredAtEpochMillis
        if (ageMillis < 0) return ExerciseSafetyStatus.FUTURE_DATED
        if (ageMillis >= settings.staleReadingMinutes * MILLIS_PER_MINUTE) {
            return ExerciseSafetyStatus.STALE
        }
        if (reading.valueMgDl < settings.lowGlucoseThresholdMgDl) {
            return ExerciseSafetyStatus.BELOW_LOW_THRESHOLD
        }
        val effectiveRate =
            reading.rateMgDlPerMinute ?: reading.trend.approximateRateMgDlPerMinute
        if (effectiveRate <= -settings.exercisePauseFallRateMgDlPerMinute) {
            return ExerciseSafetyStatus.FALLING_QUICKLY
        }
        return ExerciseSafetyStatus.SAFE
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}

object CoachedExerciseActionPolicy {
    fun canStart(
        reading: GlucoseReading?,
        settings: CoachSettings,
        nowEpochMillis: Long,
        minuteOfDay: Int = minuteOfDay(nowEpochMillis),
    ): Boolean = settings.notificationsEnabled &&
        !isInTimeRange(
            minuteOfDay,
            settings.quietHoursStartMinuteOfDay,
            settings.quietHoursEndMinuteOfDay,
        ) &&
        ExerciseSafetyPolicy.evaluate(reading, settings, nowEpochMillis) ==
        ExerciseSafetyStatus.SAFE
}

fun WatchState.effectiveRecommendation(
    nowEpochMillis: Long,
    minuteOfDay: Int = minuteOfDay(nowEpochMillis),
): CoachRecommendation? {
    val candidate = recommendation ?: return null
    if (candidate !is CoachRecommendation.Action) return candidate
    if (
        activeSession != null ||
        nowEpochMillis >= candidate.validUntilEpochMillis ||
        !candidate.hasCurrentActionProvenance(glucose) ||
        !CoachedExerciseActionPolicy.canStart(
            glucose,
            settings,
            nowEpochMillis,
            minuteOfDay,
        )
    ) {
        return null
    }
    return candidate
}

fun CoachRecommendation.Action.hasCurrentActionProvenance(
    reading: GlucoseReading?,
): Boolean {
    val provenanceComplete = listOf(
        triggerContextId,
        triggerAtEpochMillis,
        glucoseSourceId,
        safetyReadingId,
        safetyReadingAtEpochMillis,
    ).all { it != null }
    if (!provenanceComplete) return false
    val currentReading = reading ?: return false
    if (glucoseSourceId != currentReading.sourceId) return false
    return reason != CoachReason.RAPID_GLUCOSE_RISE ||
        (
            safetyReadingId == currentReading.id &&
                safetyReadingAtEpochMillis == currentReading.measuredAtEpochMillis
            )
}

private fun minuteOfDay(epochMillis: Long): Int = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()
    .toSecondOfDay() / 60

private fun isInTimeRange(
    minuteOfDay: Int,
    start: Int,
    end: Int,
): Boolean {
    if (start == end) return false
    return if (start < end) {
        minuteOfDay in start until end
    } else {
        minuteOfDay >= start || minuteOfDay < end
    }
}
