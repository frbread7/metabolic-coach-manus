package com.young.metaboliccoach.core.model

enum class CoachReason {
    RAPID_GLUCOSE_RISE,
    POST_MEAL_WINDOW,
    PROLONGED_INACTIVITY,
    LOW_GLUCOSE_SAFETY,
    FALLING_GLUCOSE_SAFETY,
    STALE_GLUCOSE_DATA,
}

sealed interface CoachRecommendation {
    val reason: CoachReason

    data class Action(
        override val reason: CoachReason,
        val id: String,
        val createdAtEpochMillis: Long,
        val validUntilEpochMillis: Long,
        val interventionType: InterventionType,
        val title: String,
        val actionLabel: String,
        val durationMinutes: Int?,
        val targetFloors: Int?,
        val algorithmVersion: Int = 1,
        val triggerContextId: String? = null,
        val triggerAtEpochMillis: Long? = null,
        val glucoseSourceId: String? = null,
        val safetyReadingId: String? = null,
        val safetyReadingAtEpochMillis: Long? = null,
    ) : CoachRecommendation

    data class Information(
        override val reason: CoachReason,
        val title: String,
        val detail: String,
    ) : CoachRecommendation
}

data class CoachContext(
    val nowEpochMillis: Long,
    val minuteOfDay: Int,
    val glucose: GlucoseReading?,
    val activity: ActivitySnapshot?,
    val mostRecentMeal: MealMarker?,
    val lastRecommendationAtEpochMillis: Long?,
    val lastRecommendationId: String? = null,
    val snoozedUntilEpochMillis: Long?,
    val notificationsSentToday: Int,
    val consumedRecommendationId: String? = null,
    val previousGlucose: GlucoseReading? = null,
)

enum class PersonalObservationKind {
    ACTIVITY_EFFECT,
    INTERVENTION_TIMING,
    POST_MEAL_ACTIVITY_TIMING,
}

data class PersonalObservation(
    val interventionType: InterventionType,
    val sampleCount: Int,
    val medianChangeMgDl: Int,
    val text: String,
    val kind: PersonalObservationKind = PersonalObservationKind.ACTIVITY_EFFECT,
    val triggerReason: CoachReason? = null,
    val timingBucketStartMinutes: Int? = null,
    val timingBucketEndExclusiveMinutes: Int? = null,
    val comparisonSampleCount: Int? = null,
    val excludedSampleCount: Int = 0,
    val algorithmVersion: Int = 1,
    val sampleWindowStartEpochMillis: Long? = null,
    val sampleWindowEndEpochMillis: Long? = null,
)
