package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachContext
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.InterventionType

class CoachRuleEngine {
    fun recommend(
        context: CoachContext,
        settings: CoachSettings,
    ): CoachRecommendation? {
        val glucose = context.glucose
        when (ExerciseSafetyPolicy.evaluate(glucose, settings, context.nowEpochMillis)) {
            ExerciseSafetyStatus.MISSING -> return CoachRecommendation.Information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose unavailable",
                detail = "Open the phone app to check the configured glucose provider.",
            )
            ExerciseSafetyStatus.FUTURE_DATED -> return CoachRecommendation.Information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose timestamp is ahead",
                detail = "Check the phone and CGM clocks before acting on this reading.",
            )
            ExerciseSafetyStatus.STALE -> return CoachRecommendation.Information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose data is stale",
                detail = "Check your CGM connection before acting on this reading.",
            )
            ExerciseSafetyStatus.BELOW_LOW_THRESHOLD -> {
                return CoachRecommendation.Information(
                    reason = CoachReason.LOW_GLUCOSE_SAFETY,
                    title = "Below your configured threshold",
                    detail = "Exercise coaching is paused. Follow your personal care plan.",
                )
            }
            ExerciseSafetyStatus.FALLING_QUICKLY -> return CoachRecommendation.Information(
                reason = CoachReason.FALLING_GLUCOSE_SAFETY,
                title = "Glucose is falling quickly",
                detail = "Exercise coaching is paused. Check your CGM and personal care plan.",
            )
            ExerciseSafetyStatus.SAFE -> Unit
        }
        checkNotNull(glucose)
        val freshUntilEpochMillis =
            glucose.measuredAtEpochMillis + settings.staleReadingMinutes * MILLIS_PER_MINUTE

        if (!settings.notificationsEnabled || isInQuietHours(context.minuteOfDay, settings)) {
            return null
        }
        if ((context.snoozedUntilEpochMillis ?: Long.MIN_VALUE) > context.nowEpochMillis) {
            return null
        }

        if (isRateLimited(context, settings)) return null

        val effectiveRate = glucose.rateMgDlPerMinute
            ?: glucose.trend.approximateRateMgDlPerMinute
        if (
            settings.walkingRemindersEnabled &&
            effectiveRate >= settings.rapidRiseThresholdMgDlPerMinute
        ) {
            return walkRecommendation(
                reason = CoachReason.RAPID_GLUCOSE_RISE,
                context = context,
                glucoseReadingId = glucose.id,
                validUntilEpochMillis = freshUntilEpochMillis,
                settings = settings,
                title = "Glucose is rising. Walk now?",
                triggerContextId = glucose.id,
                triggerAtEpochMillis = glucose.measuredAtEpochMillis,
            )
        }

        if (settings.postMealRemindersEnabled && isInPostMealWindow(context, settings)) {
            val meal = requireNotNull(context.mostRecentMeal)
            return walkRecommendation(
                reason = CoachReason.POST_MEAL_WINDOW,
                context = context,
                glucoseReadingId = glucose.id,
                validUntilEpochMillis = minOf(
                    freshUntilEpochMillis,
                    meal.occurredAtEpochMillis +
                        (settings.postMealDelayMinutes + settings.postMealWindowMinutes) *
                        MILLIS_PER_MINUTE,
                ),
                settings = settings,
                title = "A short walk may fit now",
                triggerContextId = meal.id,
                triggerAtEpochMillis = meal.occurredAtEpochMillis,
            )
        }

        val inactiveMinutes = context.activity?.lastMovementAtEpochMillis?.let { lastMovement ->
            (context.nowEpochMillis - lastMovement).coerceAtLeast(0) / MILLIS_PER_MINUTE
        }
        if (
            isInWorkingHours(context.minuteOfDay, settings) &&
            inactiveMinutes != null &&
            inactiveMinutes >= settings.prolongedInactivityMinutes &&
            (settings.stairRemindersEnabled || settings.walkingRemindersEnabled)
        ) {
            val inactivityActivity = requireNotNull(context.activity)
            val lastMovementAtEpochMillis =
                requireNotNull(inactivityActivity.lastMovementAtEpochMillis)
            val triggerAtEpochMillis =
                lastMovementAtEpochMillis +
                    settings.prolongedInactivityMinutes * MILLIS_PER_MINUTE
            val triggerContextId =
                "${inactivityActivity.sourceId}:$lastMovementAtEpochMillis"
            return if (settings.stairRemindersEnabled) {
                CoachRecommendation.Action(
                    reason = CoachReason.PROLONGED_INACTIVITY,
                    id = actionId(
                        CoachReason.PROLONGED_INACTIVITY,
                        glucose.id,
                        context.activity?.measuredAtEpochMillis?.toString(),
                    ),
                    createdAtEpochMillis = context.nowEpochMillis,
                    validUntilEpochMillis = freshUntilEpochMillis,
                    interventionType = InterventionType.STAIRS,
                    title = "Climb ${settings.stairTargetFloors} floors?",
                    actionLabel = "START",
                    durationMinutes = null,
                    targetFloors = settings.stairTargetFloors,
                    algorithmVersion = ALGORITHM_VERSION,
                    triggerContextId = triggerContextId,
                    triggerAtEpochMillis = triggerAtEpochMillis,
                )
            } else {
                walkRecommendation(
                    reason = CoachReason.PROLONGED_INACTIVITY,
                    context = context,
                    glucoseReadingId = glucose.id,
                    validUntilEpochMillis = freshUntilEpochMillis,
                    settings = settings,
                    title = "You've been inactive. Take a short walk?",
                    triggerContextId = triggerContextId,
                    triggerAtEpochMillis = triggerAtEpochMillis,
                )
            }
        }

        return null
    }

    private fun walkRecommendation(
        reason: CoachReason,
        context: CoachContext,
        glucoseReadingId: String,
        validUntilEpochMillis: Long,
        settings: CoachSettings,
        title: String,
        triggerContextId: String,
        triggerAtEpochMillis: Long,
    ) = CoachRecommendation.Action(
        reason = reason,
        id = actionId(
            reason,
            glucoseReadingId,
            context.mostRecentMeal?.id.takeIf { reason == CoachReason.POST_MEAL_WINDOW },
        ),
        createdAtEpochMillis = context.nowEpochMillis,
        validUntilEpochMillis = validUntilEpochMillis,
        interventionType = InterventionType.WALK,
        title = title,
        actionLabel = "START WALK",
        durationMinutes = settings.walkingDurationMinutes,
        targetFloors = null,
        algorithmVersion = ALGORITHM_VERSION,
        triggerContextId = triggerContextId,
        triggerAtEpochMillis = triggerAtEpochMillis,
    )

    private fun isRateLimited(
        context: CoachContext,
        settings: CoachSettings,
    ): Boolean {
        if (context.notificationsSentToday >= settings.maximumNotificationsPerDay) return true
        val lastRecommendation = context.lastRecommendationAtEpochMillis ?: return false
        val elapsedMinutes =
            (context.nowEpochMillis - lastRecommendation).coerceAtLeast(0) / MILLIS_PER_MINUTE
        return elapsedMinutes < settings.reminderCooldownMinutes
    }

    private fun isInPostMealWindow(
        context: CoachContext,
        settings: CoachSettings,
    ): Boolean {
        val meal = context.mostRecentMeal ?: return false
        val elapsedMillis = context.nowEpochMillis - meal.occurredAtEpochMillis
        if (elapsedMillis < 0) return false
        val windowStartMillis = settings.postMealDelayMinutes * MILLIS_PER_MINUTE
        val windowEndMillis =
            windowStartMillis + settings.postMealWindowMinutes * MILLIS_PER_MINUTE
        return elapsedMillis >= windowStartMillis && elapsedMillis < windowEndMillis
    }

    private fun isInQuietHours(
        minuteOfDay: Int,
        settings: CoachSettings,
    ): Boolean {
        val start = settings.quietHoursStartMinuteOfDay
        val end = settings.quietHoursEndMinuteOfDay
        if (start == end) return false
        return isInTimeRange(minuteOfDay, start, end)
    }

    private fun isInWorkingHours(
        minuteOfDay: Int,
        settings: CoachSettings,
    ): Boolean {
        val start = settings.workingHoursStartMinuteOfDay
        val end = settings.workingHoursEndMinuteOfDay
        return start == end || isInTimeRange(minuteOfDay, start, end)
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val ALGORITHM_VERSION = 1

        private fun actionId(
            reason: CoachReason,
            glucoseReadingId: String,
            contextId: String?,
        ): String = listOfNotNull(reason.name, glucoseReadingId, contextId).joinToString(":")

        private fun isInTimeRange(
            minuteOfDay: Int,
            start: Int,
            end: Int,
        ): Boolean = if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }
}
