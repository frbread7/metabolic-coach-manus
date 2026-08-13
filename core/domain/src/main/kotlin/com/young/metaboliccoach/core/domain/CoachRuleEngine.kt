package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachContext
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.InterventionType

enum class CoachActionSuppression {
    NONE,
    CONFIGURATION,
    SNOOZE,
    DAILY_CAP,
    COOLDOWN,
}

data class CoachRuleEvaluation(
    val recommendation: CoachRecommendation?,
    val actionCandidate: CoachRecommendation.Action? = null,
    val actionSuppression: CoachActionSuppression = CoachActionSuppression.NONE,
)

class CoachRuleEngine {
    fun recommend(
        context: CoachContext,
        settings: CoachSettings,
        allowedActionReasons: Set<CoachReason> = ACTION_REASONS,
    ): CoachRecommendation? = evaluate(context, settings, allowedActionReasons).recommendation

    fun evaluate(
        context: CoachContext,
        settings: CoachSettings,
        allowedActionReasons: Set<CoachReason> = ACTION_REASONS,
    ): CoachRuleEvaluation {
        val glucose = context.glucose
        when (ExerciseSafetyPolicy.evaluate(glucose, settings, context.nowEpochMillis)) {
            ExerciseSafetyStatus.MISSING -> return information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose unavailable",
                detail = "Open the phone app to check the configured glucose provider.",
            )
            ExerciseSafetyStatus.FUTURE_DATED -> return information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose timestamp is ahead",
                detail = "Check the phone and CGM clocks before acting on this reading.",
            )
            ExerciseSafetyStatus.STALE -> return information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose data is stale",
                detail = "Check your CGM connection before acting on this reading.",
            )
            ExerciseSafetyStatus.BELOW_LOW_THRESHOLD -> return information(
                reason = CoachReason.LOW_GLUCOSE_SAFETY,
                title = "Below your configured threshold",
                detail = "Exercise coaching is paused. Follow your personal care plan.",
            )
            ExerciseSafetyStatus.FALLING_QUICKLY -> return information(
                reason = CoachReason.FALLING_GLUCOSE_SAFETY,
                title = "Glucose is falling quickly",
                detail = "Exercise coaching is paused. Check your CGM and personal care plan.",
            )
            ExerciseSafetyStatus.SAFE -> Unit
        }
        checkNotNull(glucose)
        val freshUntilEpochMillis =
            glucose.measuredAtEpochMillis + settings.staleReadingMinutes * MILLIS_PER_MINUTE
        val candidate = actionCandidate(
            context = context,
            settings = settings,
            allowedActionReasons = allowedActionReasons,
            freshUntilEpochMillis = freshUntilEpochMillis,
        ) ?: return CoachRuleEvaluation(recommendation = null)

        if (!settings.notificationsEnabled || isInQuietHours(context.minuteOfDay, settings)) {
            return suppressed(candidate, CoachActionSuppression.CONFIGURATION)
        }
        if ((context.snoozedUntilEpochMillis ?: Long.MIN_VALUE) > context.nowEpochMillis) {
            return suppressed(candidate, CoachActionSuppression.SNOOZE)
        }
        if (context.notificationsSentToday >= settings.maximumNotificationsPerDay) {
            return suppressed(candidate, CoachActionSuppression.DAILY_CAP)
        }
        if (isCooldownLimited(context, settings, candidate.id)) {
            return suppressed(candidate, CoachActionSuppression.COOLDOWN)
        }
        return CoachRuleEvaluation(
            recommendation = candidate,
            actionCandidate = candidate,
        )
    }

    private fun actionCandidate(
        context: CoachContext,
        settings: CoachSettings,
        allowedActionReasons: Set<CoachReason>,
        freshUntilEpochMillis: Long,
    ): CoachRecommendation.Action? {
        val glucose = requireNotNull(context.glucose)

        if (
            CoachReason.POST_MEAL_WINDOW in allowedActionReasons &&
            settings.postMealRemindersEnabled &&
            isInPostMealWindow(context, settings)
        ) {
            val meal = requireNotNull(context.mostRecentMeal)
            return walkRecommendation(
                reason = CoachReason.POST_MEAL_WINDOW,
                context = context,
                recommendationId = postMealRecommendationId(context),
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
                algorithmVersion = POST_MEAL_ALGORITHM_VERSION,
            )
        }

        val rapidRise = RapidRiseConfirmationPolicy.confirm(
            olderReading = context.previousGlucose,
            latestReading = glucose,
            settings = settings,
        )
        if (
            CoachReason.RAPID_GLUCOSE_RISE in allowedActionReasons &&
            settings.walkingRemindersEnabled &&
            rapidRise != null
        ) {
            return walkRecommendation(
                reason = CoachReason.RAPID_GLUCOSE_RISE,
                context = context,
                recommendationId = rapidRise.recommendationId,
                validUntilEpochMillis = freshUntilEpochMillis,
                settings = settings,
                title = "Glucose is rising. Walk now?",
                triggerContextId = rapidRise.triggerIdentity,
                triggerAtEpochMillis = glucose.measuredAtEpochMillis,
                algorithmVersion = RapidRiseConfirmationPolicy.ALGORITHM_VERSION,
            )
        }

        if (CoachReason.PROLONGED_INACTIVITY in allowedActionReasons) {
            val inactivity = InactivityConfirmationPolicy.confirm(
                activity = context.activity,
                settings = settings,
                nowEpochMillis = context.nowEpochMillis,
                minuteOfDay = context.minuteOfDay,
            )
            if (inactivity != null) {
                return walkRecommendation(
                    reason = CoachReason.PROLONGED_INACTIVITY,
                    context = context,
                    recommendationId = inactivity.recommendationId,
                    validUntilEpochMillis = minOf(
                        freshUntilEpochMillis,
                        inactivity.activityFreshUntilEpochMillis,
                    ),
                    settings = settings,
                    title = "You've been inactive. Take a short walk?",
                    triggerContextId = inactivity.triggerIdentity,
                    triggerAtEpochMillis = inactivity.thresholdCrossingAtEpochMillis,
                    algorithmVersion = InactivityConfirmationPolicy.ALGORITHM_VERSION,
                )
            }
        }

        return null
    }

    private fun walkRecommendation(
        reason: CoachReason,
        context: CoachContext,
        recommendationId: String,
        validUntilEpochMillis: Long,
        settings: CoachSettings,
        title: String,
        triggerContextId: String,
        triggerAtEpochMillis: Long,
        algorithmVersion: Int,
    ) = CoachRecommendation.Action(
        reason = reason,
        id = recommendationId,
        createdAtEpochMillis = context.nowEpochMillis,
        validUntilEpochMillis = validUntilEpochMillis,
        interventionType = InterventionType.WALK,
        title = title,
        actionLabel = "START WALK",
        durationMinutes = settings.walkingDurationMinutes,
        targetFloors = null,
        algorithmVersion = algorithmVersion,
        triggerContextId = triggerContextId,
        triggerAtEpochMillis = triggerAtEpochMillis,
        glucoseSourceId = context.glucose?.sourceId,
        safetyReadingId = context.glucose?.id,
        safetyReadingAtEpochMillis = context.glucose?.measuredAtEpochMillis,
    )

    private fun isCooldownLimited(
        context: CoachContext,
        settings: CoachSettings,
        candidateId: String,
    ): Boolean {
        val lastRecommendation = context.lastRecommendationAtEpochMillis ?: return false
        val snoozedUntil = context.snoozedUntilEpochMillis
        if (
            context.lastRecommendationId == candidateId &&
            snoozedUntil != null &&
            snoozedUntil <= context.nowEpochMillis
        ) {
            return false
        }
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

    private fun postMealRecommendationId(context: CoachContext): String = listOf(
        CoachReason.POST_MEAL_WINDOW.name,
        requireNotNull(context.mostRecentMeal).id,
        requireNotNull(context.glucose).sourceId,
        POST_MEAL_ALGORITHM_VERSION,
    ).joinToString(":")

    private fun information(
        reason: CoachReason,
        title: String,
        detail: String,
    ) = CoachRuleEvaluation(
        recommendation = CoachRecommendation.Information(reason, title, detail),
    )

    private fun suppressed(
        candidate: CoachRecommendation.Action,
        suppression: CoachActionSuppression,
    ) = CoachRuleEvaluation(
        recommendation = null,
        actionCandidate = candidate,
        actionSuppression = suppression,
    )

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val POST_MEAL_ALGORITHM_VERSION = 2
        private val ACTION_REASONS = setOf(
            CoachReason.RAPID_GLUCOSE_RISE,
            CoachReason.POST_MEAL_WINDOW,
            CoachReason.PROLONGED_INACTIVITY,
        )
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
