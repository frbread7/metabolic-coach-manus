package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.GlycemicWindow

object CoachSettingsBounds {
    val LOW_GLUCOSE_MG_DL = 40..120
    val TARGET_MG_DL = 40..400
    val TARGET_LOWER_MG_DL = TARGET_MG_DL.first until TARGET_MG_DL.last
    val TARGET_UPPER_MG_DL = (TARGET_MG_DL.first + 1)..TARGET_MG_DL.last
    val RAPID_RISE_MG_DL_PER_MINUTE = 0.5..10.0
    val EXERCISE_PAUSE_FALL_RATE_MG_DL_PER_MINUTE = 0.5..10.0
    val STALE_READING_MINUTES = 5..120
    val WALKING_DURATION_MINUTES = 1..120
    val STAIR_TARGET_FLOORS = 1..100
    val DAILY_STEP_GOAL = 1_000..100_000
    val DAILY_FLOOR_GOAL = 1..500
    val PROLONGED_INACTIVITY_MINUTES = 15..480
    val POST_MEAL_DELAY_MINUTES = 0..240
    val POST_MEAL_WINDOW_MINUTES = 5..240
    val REMINDER_COOLDOWN_MINUTES = 5..1_440
    val SNOOZE_MINUTES = 1..240
    val MAXIMUM_NOTIFICATIONS_PER_DAY = 0..48
    val MINUTE_OF_DAY = 0 until 24 * 60
    val MINIMUM_OBSERVATION_SAMPLES = 2..100
    val MINIMUM_TIMING_BUCKET_SAMPLES = 2..100
    val MINIMUM_COMPARABLE_TIMING_BUCKETS = 2..12
    val INTERVENTION_TIMING_BUCKET_MINUTES = 1..60
    val POST_MEAL_TIMING_BUCKET_MINUTES = 1..240
    val FOLLOW_UP_DELAY_BUCKET_MINUTES = 1..1_440
    val BASELINE_GLUCOSE_BAND_MG_DL = 5..100
    val INTERVENTION_FOLLOW_UP_MINUTES = 5..1_440
    val QUICK_ACTION_EXPIRY_MINUTES = 5..10_080
    val FONT_SCALE = 0.8f..1.6f
}

object GlycemicPlannerBounds {
    val SCENARIO_HORIZONS = setOf(
        GlycemicWindow.DAYS_30,
        GlycemicWindow.DAYS_60,
        GlycemicWindow.DAYS_90,
    )
    val TARGET_GMI_PERCENT = 3.5..15.0
    val LOW_GLUCOSE_MG_DL = 40..120
    val VERY_LOW_GLUCOSE_MG_DL = 40..100
    val MAXIMUM_LOW_EXPOSURE_PERCENT = 0.0f..20.0f
    val MAXIMUM_VERY_LOW_EXPOSURE_PERCENT = 0.0f..10.0f
}

class SettingsValidator {
    fun validate(settings: CoachSettings): List<String> = buildList {
        settings.healthConnectGlucoseOriginPackage?.let { packageName ->
            if (
                packageName.isBlank() ||
                packageName.length > MAX_HEALTH_CONNECT_ORIGIN_LENGTH ||
                packageName.any { it.isWhitespace() || it == ':' }
            ) {
                add("Health Connect glucose source package is invalid.")
            }
        }
        if (settings.lowGlucoseThresholdMgDl !in CoachSettingsBounds.LOW_GLUCOSE_MG_DL) {
            add("Low glucose threshold must be between 40 and 120 mg/dL.")
        }
        if (settings.targetLowerMgDl >= settings.targetUpperMgDl) {
            add("Target lower bound must be below the target upper bound.")
        }
        if (settings.targetLowerMgDl !in CoachSettingsBounds.TARGET_MG_DL) {
            add("Target lower bound must be between 40 and 400 mg/dL.")
        }
        if (settings.targetUpperMgDl !in CoachSettingsBounds.TARGET_MG_DL) {
            add("Target upper bound must be between 40 and 400 mg/dL.")
        }
        if (
            settings.rapidRiseThresholdMgDlPerMinute !in
            CoachSettingsBounds.RAPID_RISE_MG_DL_PER_MINUTE
        ) {
            add("Rapid-rise threshold must be between 0.5 and 10 mg/dL/min.")
        }
        if (
            settings.exercisePauseFallRateMgDlPerMinute !in
            CoachSettingsBounds.EXERCISE_PAUSE_FALL_RATE_MG_DL_PER_MINUTE
        ) {
            add("Exercise-pause fall-rate must be between 0.5 and 10 mg/dL/min.")
        }
        if (settings.staleReadingMinutes !in CoachSettingsBounds.STALE_READING_MINUTES) {
            add("Stale-reading age must be between 5 and 120 minutes.")
        }
        if (
            settings.walkingDurationMinutes !in
            CoachSettingsBounds.WALKING_DURATION_MINUTES
        ) {
            add("Walking duration must be between 1 and 120 minutes.")
        }
        if (settings.stairTargetFloors !in CoachSettingsBounds.STAIR_TARGET_FLOORS) {
            add("Stair target must be between 1 and 100 floors.")
        }
        if (settings.dailyStepGoal !in CoachSettingsBounds.DAILY_STEP_GOAL) {
            add("Daily step goal must be between 1000 and 100000 steps.")
        }
        if (settings.dailyFloorGoal !in CoachSettingsBounds.DAILY_FLOOR_GOAL) {
            add("Daily floor goal must be between 1 and 500 floors.")
        }
        if (
            settings.prolongedInactivityMinutes !in
            CoachSettingsBounds.PROLONGED_INACTIVITY_MINUTES
        ) {
            add("Inactivity threshold must be between 15 and 480 minutes.")
        }
        if (settings.postMealDelayMinutes !in CoachSettingsBounds.POST_MEAL_DELAY_MINUTES) {
            add("Post-meal delay must be between 0 and 240 minutes.")
        }
        if (settings.postMealWindowMinutes !in CoachSettingsBounds.POST_MEAL_WINDOW_MINUTES) {
            add("Post-meal window must be between 5 and 240 minutes.")
        }
        if (
            settings.reminderCooldownMinutes !in
            CoachSettingsBounds.REMINDER_COOLDOWN_MINUTES
        ) {
            add("Reminder cooldown must be between 5 and 1440 minutes.")
        }
        if (settings.snoozeMinutes !in CoachSettingsBounds.SNOOZE_MINUTES) {
            add("Snooze duration must be between 1 and 240 minutes.")
        }
        if (
            settings.maximumNotificationsPerDay !in
            CoachSettingsBounds.MAXIMUM_NOTIFICATIONS_PER_DAY
        ) {
            add("Daily notification limit must be between 0 and 48.")
        }
        if (
            settings.quietHoursStartMinuteOfDay !in CoachSettingsBounds.MINUTE_OF_DAY ||
            settings.quietHoursEndMinuteOfDay !in CoachSettingsBounds.MINUTE_OF_DAY
        ) {
            add("Quiet-hour times must be valid minutes of day.")
        }
        if (
            settings.workingHoursStartMinuteOfDay !in CoachSettingsBounds.MINUTE_OF_DAY ||
            settings.workingHoursEndMinuteOfDay !in CoachSettingsBounds.MINUTE_OF_DAY
        ) {
            add("Working-hour times must be valid minutes of day.")
        }
        if (
            settings.minimumObservationSamples !in
            CoachSettingsBounds.MINIMUM_OBSERVATION_SAMPLES
        ) {
            add("Observation sample minimum must be between 2 and 100.")
        }
        if (
            settings.minimumTimingBucketSamples !in
            CoachSettingsBounds.MINIMUM_TIMING_BUCKET_SAMPLES
        ) {
            add("Timing-bucket sample minimum must be between 2 and 100.")
        }
        if (
            settings.minimumComparableTimingBuckets !in
            CoachSettingsBounds.MINIMUM_COMPARABLE_TIMING_BUCKETS
        ) {
            add("Comparable timing-bucket minimum must be between 2 and 12.")
        }
        if (
            settings.interventionTimingBucketMinutes !in
            CoachSettingsBounds.INTERVENTION_TIMING_BUCKET_MINUTES
        ) {
            add("Intervention timing-bucket width must be between 1 and 60 minutes.")
        }
        if (
            settings.postMealTimingBucketMinutes !in
            CoachSettingsBounds.POST_MEAL_TIMING_BUCKET_MINUTES
        ) {
            add("Post-meal timing-bucket width must be between 1 and 240 minutes.")
        }
        if (
            settings.followUpDelayBucketMinutes !in
            CoachSettingsBounds.FOLLOW_UP_DELAY_BUCKET_MINUTES
        ) {
            add("Follow-up delay bucket width must be between 1 and 1440 minutes.")
        }
        if (
            settings.baselineGlucoseBandMgDl !in
            CoachSettingsBounds.BASELINE_GLUCOSE_BAND_MG_DL
        ) {
            add("Baseline glucose band must be between 5 and 100 mg/dL.")
        }
        if (
            settings.interventionFollowUpMinutes !in
            CoachSettingsBounds.INTERVENTION_FOLLOW_UP_MINUTES
        ) {
            add("Intervention follow-up delay must be between 5 and 1440 minutes.")
        }
        if (
            settings.quickActionExpiryMinutes !in
            CoachSettingsBounds.QUICK_ACTION_EXPIRY_MINUTES
        ) {
            add("Quick-action expiry must be between 5 and 10080 minutes.")
        }
        if (settings.fontScale !in CoachSettingsBounds.FONT_SCALE) {
            add("Font scale must be between 0.8 and 1.6.")
        }
    }

    private companion object {
        const val MAX_HEALTH_CONNECT_ORIGIN_LENGTH = 255
    }
}
