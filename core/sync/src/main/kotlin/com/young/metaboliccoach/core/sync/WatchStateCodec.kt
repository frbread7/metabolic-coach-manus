package com.young.metaboliccoach.core.sync

import com.google.android.gms.wearable.DataMap
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.CoachTheme
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.GlucoseUnit
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import com.young.metaboliccoach.core.model.WatchState
import javax.inject.Inject

class WatchStateCodec @Inject constructor() {
    fun encode(state: WatchState): DataMap = DataMap().apply {
        putInt(Keys.SCHEMA_VERSION, SyncSchema.VERSION)
        putLong(Keys.GENERATED_AT, state.generatedAtEpochMillis)
        state.phoneBatteryPercent?.let { putInt(Keys.PHONE_BATTERY, it) }
        putDataMap(Keys.SETTINGS, encodeSettings(state.settings))
        state.glucose?.let { putDataMap(Keys.GLUCOSE, encodeGlucose(it)) }
        state.activity?.let { putDataMap(Keys.ACTIVITY, encodeActivity(it)) }
        state.recommendation?.let { putDataMap(Keys.RECOMMENDATION, encodeRecommendation(it)) }
        state.activeSession?.let { putDataMap(Keys.ACTIVE_SESSION, encodeSession(it)) }
        state.phoneInstanceId?.let { putString(Keys.PHONE_INSTANCE_ID, it) }
        state.stateRevision?.let { putLong(Keys.STATE_REVISION, it) }
        state.lastSessionCommandAck?.let {
            putDataMap(Keys.SESSION_COMMAND_ACK, encodeSessionCommandAck(it))
        }
        state.dataResetId?.let { putString(Keys.DATA_RESET_ID, it) }
    }

    fun decode(dataMap: DataMap): WatchState? {
        if (dataMap.getInt(Keys.SCHEMA_VERSION) != SyncSchema.VERSION) return null
        return runCatching {
            WatchState(
                glucose = dataMap.getDataMap(Keys.GLUCOSE)?.let(::decodeGlucose),
                activity = dataMap.getDataMap(Keys.ACTIVITY)?.let(::decodeActivity),
                recommendation =
                    dataMap.getDataMap(Keys.RECOMMENDATION)?.let(::decodeRecommendation),
                settings = decodeSettings(dataMap.requiredDataMap(Keys.SETTINGS)),
                phoneBatteryPercent = dataMap.optionalInt(Keys.PHONE_BATTERY),
                generatedAtEpochMillis = dataMap.requiredLong(Keys.GENERATED_AT),
                activeSession = dataMap.getDataMap(Keys.ACTIVE_SESSION)?.let(::decodeSession),
                phoneInstanceId = dataMap.getString(Keys.PHONE_INSTANCE_ID),
                stateRevision = dataMap.optionalLong(Keys.STATE_REVISION),
                lastSessionCommandAck = dataMap.getDataMap(Keys.SESSION_COMMAND_ACK)
                    ?.let(::decodeSessionCommandAck),
                dataResetId = dataMap.getString(Keys.DATA_RESET_ID),
            )
        }.getOrNull()
    }

    fun encode(command: QuickActionCommand): DataMap = DataMap().apply {
        putInt(Keys.SCHEMA_VERSION, SyncSchema.VERSION)
        putString(Keys.COMMAND_ID, command.id)
        putString(Keys.COMMAND_TYPE, command.type.name)
        putLong(Keys.COMMAND_CREATED_AT, command.createdAtEpochMillis)
        command.sessionId?.let { putString(Keys.COMMAND_SESSION_ID, it) }
        command.recommendationId?.let { putString(Keys.COMMAND_RECOMMENDATION_ID, it) }
        command.recommendationValidUntilEpochMillis?.let {
            putLong(Keys.COMMAND_RECOMMENDATION_VALID_UNTIL, it)
        }
        command.recommendationReason?.let {
            putString(Keys.COMMAND_RECOMMENDATION_REASON, it.name)
        }
        command.recommendationAlgorithmVersion?.let {
            putInt(Keys.COMMAND_RECOMMENDATION_ALGORITHM_VERSION, it)
        }
        command.recommendationCreatedAtEpochMillis?.let {
            putLong(Keys.COMMAND_RECOMMENDATION_CREATED_AT, it)
        }
        command.triggerContextId?.let { putString(Keys.COMMAND_TRIGGER_CONTEXT_ID, it) }
        command.triggerAtEpochMillis?.let { putLong(Keys.COMMAND_TRIGGER_AT, it) }
        command.glucoseSourceId?.let { putString(Keys.COMMAND_GLUCOSE_SOURCE_ID, it) }
        command.safetyReadingId?.let { putString(Keys.COMMAND_SAFETY_READING_ID, it) }
        command.safetyReadingAtEpochMillis?.let {
            putLong(Keys.COMMAND_SAFETY_READING_AT, it)
        }
        command.dataResetId?.let { putString(Keys.DATA_RESET_ID, it) }
    }

    fun decodeCommand(dataMap: DataMap): QuickActionCommand? {
        if (dataMap.getInt(Keys.SCHEMA_VERSION) != SyncSchema.VERSION) return null
        return runCatching {
            QuickActionCommand(
                id = dataMap.requiredString(Keys.COMMAND_ID),
                type = QuickActionType.valueOf(dataMap.requiredString(Keys.COMMAND_TYPE)),
                createdAtEpochMillis = dataMap.requiredLong(Keys.COMMAND_CREATED_AT),
                sessionId = dataMap.getString(Keys.COMMAND_SESSION_ID),
                recommendationId = dataMap.getString(Keys.COMMAND_RECOMMENDATION_ID),
                recommendationValidUntilEpochMillis =
                    dataMap.optionalLong(Keys.COMMAND_RECOMMENDATION_VALID_UNTIL),
                recommendationReason = dataMap.getString(Keys.COMMAND_RECOMMENDATION_REASON)
                    ?.let(CoachReason::valueOf),
                recommendationAlgorithmVersion =
                    dataMap.optionalInt(Keys.COMMAND_RECOMMENDATION_ALGORITHM_VERSION),
                recommendationCreatedAtEpochMillis =
                    dataMap.optionalLong(Keys.COMMAND_RECOMMENDATION_CREATED_AT),
                triggerContextId = dataMap.getString(Keys.COMMAND_TRIGGER_CONTEXT_ID),
                triggerAtEpochMillis = dataMap.optionalLong(Keys.COMMAND_TRIGGER_AT),
                glucoseSourceId = dataMap.getString(Keys.COMMAND_GLUCOSE_SOURCE_ID),
                safetyReadingId = dataMap.getString(Keys.COMMAND_SAFETY_READING_ID),
                safetyReadingAtEpochMillis =
                    dataMap.optionalLong(Keys.COMMAND_SAFETY_READING_AT),
                dataResetId = dataMap.getString(Keys.DATA_RESET_ID),
            )
        }.getOrNull()
    }

    private fun encodeGlucose(reading: GlucoseReading) = DataMap().apply {
        putString(Keys.ID, reading.id)
        putInt(Keys.GLUCOSE_VALUE, reading.valueMgDl)
        putString(Keys.GLUCOSE_TREND, reading.trend.name)
        reading.deltaMgDl?.let { putInt(Keys.GLUCOSE_DELTA, it) }
        reading.rateMgDlPerMinute?.let { putDouble(Keys.GLUCOSE_RATE, it) }
        putLong(Keys.MEASURED_AT, reading.measuredAtEpochMillis)
        putLong(Keys.RECEIVED_AT, reading.receivedAtEpochMillis)
        putString(Keys.SOURCE_ID, reading.sourceId)
    }

    private fun decodeGlucose(map: DataMap) = GlucoseReading(
        id = map.requiredString(Keys.ID),
        valueMgDl = map.requiredInt(Keys.GLUCOSE_VALUE),
        trend = runCatching {
            GlucoseTrend.valueOf(map.requiredString(Keys.GLUCOSE_TREND))
        }.getOrDefault(GlucoseTrend.UNKNOWN),
        deltaMgDl = map.optionalInt(Keys.GLUCOSE_DELTA),
        rateMgDlPerMinute = map.optionalDouble(Keys.GLUCOSE_RATE),
        measuredAtEpochMillis = map.requiredLong(Keys.MEASURED_AT),
        receivedAtEpochMillis = map.requiredLong(Keys.RECEIVED_AT),
        sourceId = map.requiredString(Keys.SOURCE_ID),
    )

    private fun encodeActivity(activity: ActivitySnapshot) = DataMap().apply {
        putLong(Keys.STEPS, activity.stepsToday)
        putDouble(Keys.FLOORS, activity.floorsToday)
        activity.latestHeartRateBpm?.let { putLong(Keys.HEART_RATE, it) }
        activity.activeCaloriesToday?.let { putDouble(Keys.ACTIVE_CALORIES, it) }
        activity.lastMovementAtEpochMillis?.let { putLong(Keys.LAST_MOVEMENT, it) }
        putInt(Keys.EXERCISE_SESSION_COUNT, activity.exerciseSessionCountToday)
        putLong(Keys.EXERCISE_DURATION_MINUTES, activity.exerciseDurationMinutesToday)
        putLong(Keys.MEASURED_AT, activity.measuredAtEpochMillis)
        putString(Keys.SOURCE_ID, activity.sourceId)
    }

    private fun decodeActivity(map: DataMap) = ActivitySnapshot(
        stepsToday = map.requiredLong(Keys.STEPS),
        floorsToday = map.requiredDouble(Keys.FLOORS),
        latestHeartRateBpm = map.optionalLong(Keys.HEART_RATE),
        activeCaloriesToday = map.optionalDouble(Keys.ACTIVE_CALORIES),
        lastMovementAtEpochMillis = map.optionalLong(Keys.LAST_MOVEMENT),
        measuredAtEpochMillis = map.requiredLong(Keys.MEASURED_AT),
        sourceId = map.requiredString(Keys.SOURCE_ID),
        exerciseSessionCountToday = map.intOrDefault(Keys.EXERCISE_SESSION_COUNT, 0),
        exerciseDurationMinutesToday = map.longOrDefault(Keys.EXERCISE_DURATION_MINUTES, 0),
    )

    private fun encodeSession(session: InterventionSession) = DataMap().apply {
        putString(Keys.ID, session.id)
        putString(Keys.INTERVENTION_TYPE, session.type.name)
        putString(Keys.SESSION_STATUS, session.status.name)
        putLong(Keys.SESSION_STARTED_AT, session.startedAtEpochMillis)
        session.endedAtEpochMillis?.let { putLong(Keys.SESSION_ENDED_AT, it) }
        session.targetDurationMinutes?.let { putInt(Keys.DURATION, it) }
        session.targetFloors?.let { putInt(Keys.TARGET_FLOORS, it) }
        session.baselineGlucoseMgDl?.let { putInt(Keys.BASELINE_GLUCOSE, it) }
        session.baselineGlucoseReadingId?.let { putString(Keys.BASELINE_READING_ID, it) }
        session.baselineGlucoseMeasuredAtEpochMillis?.let {
            putLong(Keys.BASELINE_READING_AT, it)
        }
        session.baselineGlucoseSourceId?.let { putString(Keys.BASELINE_SOURCE_ID, it) }
        session.glucoseAfterMgDl?.let { putInt(Keys.FOLLOW_UP_GLUCOSE, it) }
        session.followUpDueAtEpochMillis?.let { putLong(Keys.FOLLOW_UP_DUE_AT, it) }
        session.followUpReadingAtEpochMillis?.let {
            putLong(Keys.FOLLOW_UP_READING_AT, it)
        }
        session.followUpGlucoseReadingId?.let { putString(Keys.FOLLOW_UP_READING_ID, it) }
        session.followUpGlucoseSourceId?.let { putString(Keys.FOLLOW_UP_SOURCE_ID, it) }
        session.followUpFinalizedAtEpochMillis?.let {
            putLong(Keys.FOLLOW_UP_FINALIZED_AT, it)
        }
        session.recommendationId?.let { putString(Keys.SESSION_RECOMMENDATION_ID, it) }
        session.recommendationReason?.let {
            putString(Keys.SESSION_RECOMMENDATION_REASON, it.name)
        }
        session.recommendationAlgorithmVersion?.let {
            putInt(Keys.SESSION_RECOMMENDATION_ALGORITHM_VERSION, it)
        }
        session.recommendationCreatedAtEpochMillis?.let {
            putLong(Keys.SESSION_RECOMMENDATION_CREATED_AT, it)
        }
        session.recommendationValidUntilEpochMillis?.let {
            putLong(Keys.SESSION_RECOMMENDATION_VALID_UNTIL, it)
        }
        session.triggerContextId?.let { putString(Keys.SESSION_TRIGGER_CONTEXT_ID, it) }
        session.triggerAtEpochMillis?.let { putLong(Keys.SESSION_TRIGGER_AT, it) }
        session.baselineEffectiveRateMgDlPerMinute?.let {
            putDouble(Keys.BASELINE_EFFECTIVE_RATE, it)
        }
        session.lowGlucoseThresholdMgDlAtStart?.let {
            putInt(Keys.SESSION_LOW_GLUCOSE_THRESHOLD, it)
        }
    }

    private fun decodeSession(map: DataMap): InterventionSession =
        InterventionSession(
            id = map.requiredString(Keys.ID),
            type = InterventionType.valueOf(map.requiredString(Keys.INTERVENTION_TYPE)),
            status = InterventionStatus.valueOf(map.requiredString(Keys.SESSION_STATUS)),
            startedAtEpochMillis = map.requiredLong(Keys.SESSION_STARTED_AT),
            endedAtEpochMillis = map.optionalLong(Keys.SESSION_ENDED_AT),
            targetDurationMinutes = map.optionalInt(Keys.DURATION),
            targetFloors = map.optionalInt(Keys.TARGET_FLOORS),
            baselineGlucoseMgDl = map.optionalInt(Keys.BASELINE_GLUCOSE),
            baselineGlucoseReadingId = map.getString(Keys.BASELINE_READING_ID),
            baselineGlucoseMeasuredAtEpochMillis = map.optionalLong(Keys.BASELINE_READING_AT),
            baselineGlucoseSourceId = map.getString(Keys.BASELINE_SOURCE_ID),
            glucoseAfterMgDl = map.optionalInt(Keys.FOLLOW_UP_GLUCOSE),
            followUpDueAtEpochMillis = map.optionalLong(Keys.FOLLOW_UP_DUE_AT),
            followUpReadingAtEpochMillis = map.optionalLong(Keys.FOLLOW_UP_READING_AT),
            followUpGlucoseReadingId = map.getString(Keys.FOLLOW_UP_READING_ID),
            followUpGlucoseSourceId = map.getString(Keys.FOLLOW_UP_SOURCE_ID),
            followUpFinalizedAtEpochMillis = map.optionalLong(Keys.FOLLOW_UP_FINALIZED_AT),
            recommendationId = map.getString(Keys.SESSION_RECOMMENDATION_ID),
            recommendationReason = map.getString(Keys.SESSION_RECOMMENDATION_REASON)
                ?.let(CoachReason::valueOf),
            recommendationAlgorithmVersion =
                map.optionalInt(Keys.SESSION_RECOMMENDATION_ALGORITHM_VERSION),
            recommendationCreatedAtEpochMillis =
                map.optionalLong(Keys.SESSION_RECOMMENDATION_CREATED_AT),
            recommendationValidUntilEpochMillis =
                map.optionalLong(Keys.SESSION_RECOMMENDATION_VALID_UNTIL),
            triggerContextId = map.getString(Keys.SESSION_TRIGGER_CONTEXT_ID),
            triggerAtEpochMillis = map.optionalLong(Keys.SESSION_TRIGGER_AT),
            baselineEffectiveRateMgDlPerMinute =
                map.optionalDouble(Keys.BASELINE_EFFECTIVE_RATE),
            lowGlucoseThresholdMgDlAtStart =
                map.optionalInt(Keys.SESSION_LOW_GLUCOSE_THRESHOLD),
        )

    private fun encodeSessionCommandAck(ack: SessionCommandAck) = DataMap().apply {
        putString(Keys.ACK_COMMAND_ID, ack.commandId)
        ack.sessionId?.let { putString(Keys.ACK_SESSION_ID, it) }
        putString(Keys.ACK_OUTCOME, ack.outcome.name)
    }

    private fun decodeSessionCommandAck(map: DataMap) = SessionCommandAck(
        commandId = map.requiredString(Keys.ACK_COMMAND_ID),
        sessionId = map.getString(Keys.ACK_SESSION_ID),
        outcome = SessionCommandOutcome.valueOf(map.requiredString(Keys.ACK_OUTCOME)),
    )

    private fun encodeRecommendation(recommendation: CoachRecommendation) = DataMap().apply {
        putString(Keys.RECOMMENDATION_REASON, recommendation.reason.name)
        when (recommendation) {
            is CoachRecommendation.Action -> {
                putString(Keys.RECOMMENDATION_KIND, RecommendationKind.ACTION)
                putString(Keys.RECOMMENDATION_ID, recommendation.id)
                putLong(Keys.RECOMMENDATION_CREATED_AT, recommendation.createdAtEpochMillis)
                putLong(Keys.RECOMMENDATION_VALID_UNTIL, recommendation.validUntilEpochMillis)
                putString(Keys.INTERVENTION_TYPE, recommendation.interventionType.name)
                putString(Keys.TITLE, recommendation.title)
                putString(Keys.ACTION_LABEL, recommendation.actionLabel)
                recommendation.durationMinutes?.let { putInt(Keys.DURATION, it) }
                recommendation.targetFloors?.let { putInt(Keys.TARGET_FLOORS, it) }
                putInt(Keys.RECOMMENDATION_ALGORITHM_VERSION, recommendation.algorithmVersion)
                recommendation.triggerContextId?.let {
                    putString(Keys.RECOMMENDATION_TRIGGER_CONTEXT_ID, it)
                }
                recommendation.triggerAtEpochMillis?.let {
                    putLong(Keys.RECOMMENDATION_TRIGGER_AT, it)
                }
                recommendation.glucoseSourceId?.let {
                    putString(Keys.RECOMMENDATION_GLUCOSE_SOURCE_ID, it)
                }
                recommendation.safetyReadingId?.let {
                    putString(Keys.RECOMMENDATION_SAFETY_READING_ID, it)
                }
                recommendation.safetyReadingAtEpochMillis?.let {
                    putLong(Keys.RECOMMENDATION_SAFETY_READING_AT, it)
                }
            }

            is CoachRecommendation.Information -> {
                putString(Keys.RECOMMENDATION_KIND, RecommendationKind.INFORMATION)
                putString(Keys.TITLE, recommendation.title)
                putString(Keys.DETAIL, recommendation.detail)
            }
        }
    }

    private fun decodeRecommendation(map: DataMap): CoachRecommendation {
        val reason = CoachReason.valueOf(map.requiredString(Keys.RECOMMENDATION_REASON))
        return when (map.requiredString(Keys.RECOMMENDATION_KIND)) {
            RecommendationKind.ACTION -> CoachRecommendation.Action(
                reason = reason,
                id = map.getString(Keys.RECOMMENDATION_ID) ?: "legacy:${reason.name}",
                createdAtEpochMillis =
                    map.optionalLong(Keys.RECOMMENDATION_CREATED_AT) ?: Long.MIN_VALUE,
                validUntilEpochMillis =
                    map.optionalLong(Keys.RECOMMENDATION_VALID_UNTIL) ?: Long.MIN_VALUE,
                interventionType =
                    InterventionType.valueOf(map.requiredString(Keys.INTERVENTION_TYPE)),
                title = map.requiredString(Keys.TITLE),
                actionLabel = map.requiredString(Keys.ACTION_LABEL),
                durationMinutes = map.optionalInt(Keys.DURATION),
                targetFloors = map.optionalInt(Keys.TARGET_FLOORS),
                algorithmVersion =
                    map.intOrDefault(Keys.RECOMMENDATION_ALGORITHM_VERSION, 1),
                triggerContextId = map.getString(Keys.RECOMMENDATION_TRIGGER_CONTEXT_ID),
                triggerAtEpochMillis = map.optionalLong(Keys.RECOMMENDATION_TRIGGER_AT),
                glucoseSourceId = map.getString(Keys.RECOMMENDATION_GLUCOSE_SOURCE_ID),
                safetyReadingId = map.getString(Keys.RECOMMENDATION_SAFETY_READING_ID),
                safetyReadingAtEpochMillis =
                    map.optionalLong(Keys.RECOMMENDATION_SAFETY_READING_AT),
            )

            RecommendationKind.INFORMATION -> CoachRecommendation.Information(
                reason = reason,
                title = map.requiredString(Keys.TITLE),
                detail = map.requiredString(Keys.DETAIL),
            )

            else -> error("Unsupported recommendation kind.")
        }
    }

    private fun encodeSettings(settings: CoachSettings) = DataMap().apply {
        putString(Keys.PROVIDER_MODE, settings.glucoseProviderMode.name)
        settings.healthConnectGlucoseOriginPackage?.let {
            putString(Keys.HEALTH_CONNECT_GLUCOSE_ORIGIN, it)
        }
        putString(Keys.UNIT, settings.glucoseUnit.name)
        putInt(Keys.LOW_THRESHOLD, settings.lowGlucoseThresholdMgDl)
        putInt(Keys.TARGET_LOWER, settings.targetLowerMgDl)
        putInt(Keys.TARGET_UPPER, settings.targetUpperMgDl)
        putDouble(Keys.RAPID_RISE, settings.rapidRiseThresholdMgDlPerMinute)
        putDouble(Keys.EXERCISE_PAUSE_FALL_RATE, settings.exercisePauseFallRateMgDlPerMinute)
        putInt(Keys.STALE_MINUTES, settings.staleReadingMinutes)
        putInt(Keys.WALK_MINUTES, settings.walkingDurationMinutes)
        putInt(Keys.STAIR_FLOORS, settings.stairTargetFloors)
        putInt(Keys.DAILY_STEP_GOAL, settings.dailyStepGoal)
        putInt(Keys.DAILY_FLOOR_GOAL, settings.dailyFloorGoal)
        putInt(Keys.INACTIVITY_MINUTES, settings.prolongedInactivityMinutes)
        putInt(Keys.POST_MEAL_DELAY, settings.postMealDelayMinutes)
        putInt(Keys.POST_MEAL_WINDOW, settings.postMealWindowMinutes)
        putInt(Keys.COOLDOWN_MINUTES, settings.reminderCooldownMinutes)
        putInt(Keys.SNOOZE_MINUTES, settings.snoozeMinutes)
        putInt(Keys.MAX_NOTIFICATIONS, settings.maximumNotificationsPerDay)
        putInt(Keys.QUIET_START, settings.quietHoursStartMinuteOfDay)
        putInt(Keys.QUIET_END, settings.quietHoursEndMinuteOfDay)
        putInt(Keys.WORK_START, settings.workingHoursStartMinuteOfDay)
        putInt(Keys.WORK_END, settings.workingHoursEndMinuteOfDay)
        putInt(Keys.MIN_OBSERVATIONS, settings.minimumObservationSamples)
        putInt(Keys.MIN_TIMING_BUCKET_SAMPLES, settings.minimumTimingBucketSamples)
        putInt(
            Keys.MIN_COMPARABLE_TIMING_BUCKETS,
            settings.minimumComparableTimingBuckets,
        )
        putInt(
            Keys.INTERVENTION_TIMING_BUCKET_MINUTES,
            settings.interventionTimingBucketMinutes,
        )
        putInt(Keys.POST_MEAL_TIMING_BUCKET_MINUTES, settings.postMealTimingBucketMinutes)
        putInt(Keys.FOLLOW_UP_DELAY_BUCKET_MINUTES, settings.followUpDelayBucketMinutes)
        putInt(Keys.BASELINE_GLUCOSE_BAND_MG_DL, settings.baselineGlucoseBandMgDl)
        putInt(Keys.INTERVENTION_FOLLOW_UP_MINUTES, settings.interventionFollowUpMinutes)
        putInt(Keys.QUICK_ACTION_EXPIRY_MINUTES, settings.quickActionExpiryMinutes)
        putBoolean(Keys.WALK_ENABLED, settings.walkingRemindersEnabled)
        putBoolean(Keys.STAIRS_ENABLED, settings.stairRemindersEnabled)
        putBoolean(Keys.POST_MEAL_ENABLED, settings.postMealRemindersEnabled)
        putBoolean(Keys.NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
        putString(Keys.THEME, settings.theme.name)
        putFloat(Keys.FONT_SCALE, settings.fontScale)
    }

    private fun decodeSettings(map: DataMap): CoachSettings {
        val defaults = DefaultCoachSettings.create()
        return CoachSettings(
            glucoseProviderMode =
                map.enumOrDefault(Keys.PROVIDER_MODE, defaults.glucoseProviderMode),
            healthConnectGlucoseOriginPackage =
                map.getString(Keys.HEALTH_CONNECT_GLUCOSE_ORIGIN),
            glucoseUnit = map.enumOrDefault(Keys.UNIT, defaults.glucoseUnit),
            lowGlucoseThresholdMgDl = map.requiredInt(Keys.LOW_THRESHOLD),
            targetLowerMgDl = map.requiredInt(Keys.TARGET_LOWER),
            targetUpperMgDl = map.requiredInt(Keys.TARGET_UPPER),
            rapidRiseThresholdMgDlPerMinute = map.requiredDouble(Keys.RAPID_RISE),
            exercisePauseFallRateMgDlPerMinute = map.doubleOrDefault(
                Keys.EXERCISE_PAUSE_FALL_RATE,
                defaults.exercisePauseFallRateMgDlPerMinute,
            ),
            staleReadingMinutes = map.requiredInt(Keys.STALE_MINUTES),
            walkingDurationMinutes = map.requiredInt(Keys.WALK_MINUTES),
            stairTargetFloors = map.requiredInt(Keys.STAIR_FLOORS),
            dailyStepGoal = map.intOrDefault(Keys.DAILY_STEP_GOAL, defaults.dailyStepGoal),
            dailyFloorGoal = map.intOrDefault(Keys.DAILY_FLOOR_GOAL, defaults.dailyFloorGoal),
            prolongedInactivityMinutes = map.requiredInt(Keys.INACTIVITY_MINUTES),
            postMealDelayMinutes = map.requiredInt(Keys.POST_MEAL_DELAY),
            postMealWindowMinutes = map.requiredInt(Keys.POST_MEAL_WINDOW),
            reminderCooldownMinutes = map.requiredInt(Keys.COOLDOWN_MINUTES),
            snoozeMinutes = map.requiredInt(Keys.SNOOZE_MINUTES),
            maximumNotificationsPerDay = map.requiredInt(Keys.MAX_NOTIFICATIONS),
            quietHoursStartMinuteOfDay = map.requiredInt(Keys.QUIET_START),
            quietHoursEndMinuteOfDay = map.requiredInt(Keys.QUIET_END),
            workingHoursStartMinuteOfDay = map.requiredInt(Keys.WORK_START),
            workingHoursEndMinuteOfDay = map.requiredInt(Keys.WORK_END),
            minimumObservationSamples = map.requiredInt(Keys.MIN_OBSERVATIONS),
            minimumTimingBucketSamples = map.intOrDefault(
                Keys.MIN_TIMING_BUCKET_SAMPLES,
                defaults.minimumTimingBucketSamples,
            ),
            minimumComparableTimingBuckets = map.intOrDefault(
                Keys.MIN_COMPARABLE_TIMING_BUCKETS,
                defaults.minimumComparableTimingBuckets,
            ),
            interventionTimingBucketMinutes = map.intOrDefault(
                Keys.INTERVENTION_TIMING_BUCKET_MINUTES,
                defaults.interventionTimingBucketMinutes,
            ),
            postMealTimingBucketMinutes = map.intOrDefault(
                Keys.POST_MEAL_TIMING_BUCKET_MINUTES,
                defaults.postMealTimingBucketMinutes,
            ),
            followUpDelayBucketMinutes = map.intOrDefault(
                Keys.FOLLOW_UP_DELAY_BUCKET_MINUTES,
                defaults.followUpDelayBucketMinutes,
            ),
            baselineGlucoseBandMgDl = map.intOrDefault(
                Keys.BASELINE_GLUCOSE_BAND_MG_DL,
                defaults.baselineGlucoseBandMgDl,
            ),
            interventionFollowUpMinutes = map.intOrDefault(
                Keys.INTERVENTION_FOLLOW_UP_MINUTES,
                defaults.interventionFollowUpMinutes,
            ),
            quickActionExpiryMinutes = map.intOrDefault(
                Keys.QUICK_ACTION_EXPIRY_MINUTES,
                defaults.quickActionExpiryMinutes,
            ),
            walkingRemindersEnabled = map.requiredBoolean(Keys.WALK_ENABLED),
            stairRemindersEnabled = map.requiredBoolean(Keys.STAIRS_ENABLED),
            postMealRemindersEnabled = map.requiredBoolean(Keys.POST_MEAL_ENABLED),
            notificationsEnabled = map.requiredBoolean(Keys.NOTIFICATIONS_ENABLED),
            theme = map.enumOrDefault(Keys.THEME, defaults.theme),
            fontScale = map.floatOrDefault(Keys.FONT_SCALE, defaults.fontScale),
        )
    }

    private inline fun <reified T : Enum<T>> DataMap.enumOrDefault(
        key: String,
        default: T,
    ): T = getString(key)
        ?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
        ?: default

    private fun DataMap.requiredString(key: String): String =
        requireNotNull(getString(key)) { "Missing required sync field: $key" }

    private fun DataMap.requiredDataMap(key: String): DataMap =
        requireNotNull(getDataMap(key)) { "Missing required sync field: $key" }

    private fun DataMap.requiredBoolean(key: String): Boolean {
        require(containsKey(key)) { "Missing required sync field: $key" }
        return getBoolean(key)
    }

    private fun DataMap.requiredInt(key: String): Int {
        require(containsKey(key)) { "Missing required sync field: $key" }
        return getInt(key)
    }

    private fun DataMap.requiredLong(key: String): Long {
        require(containsKey(key)) { "Missing required sync field: $key" }
        return getLong(key)
    }

    private fun DataMap.requiredDouble(key: String): Double {
        require(containsKey(key)) { "Missing required sync field: $key" }
        return getDouble(key)
    }

    private fun DataMap.optionalInt(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun DataMap.intOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInt(key) else default

    private fun DataMap.floatOrDefault(key: String, default: Float): Float =
        if (containsKey(key)) getFloat(key) else default

    private fun DataMap.optionalLong(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun DataMap.longOrDefault(key: String, default: Long): Long =
        if (containsKey(key)) getLong(key) else default

    private fun DataMap.optionalDouble(key: String): Double? =
        if (containsKey(key)) getDouble(key) else null

    private fun DataMap.doubleOrDefault(key: String, default: Double): Double =
        if (containsKey(key)) getDouble(key) else default

    private object RecommendationKind {
        const val ACTION = "action"
        const val INFORMATION = "information"
    }

    private object Keys {
        const val SCHEMA_VERSION = "schemaVersion"
        const val GENERATED_AT = "generatedAt"
        const val PHONE_BATTERY = "phoneBattery"
        const val SETTINGS = "settings"
        const val GLUCOSE = "glucose"
        const val ACTIVITY = "activity"
        const val RECOMMENDATION = "recommendation"
        const val ACTIVE_SESSION = "activeSession"
        const val PHONE_INSTANCE_ID = "phoneInstanceId"
        const val STATE_REVISION = "stateRevision"
        const val SESSION_COMMAND_ACK = "sessionCommandAck"
        const val DATA_RESET_ID = "dataResetId"
        const val ACK_COMMAND_ID = "ackCommandId"
        const val ACK_SESSION_ID = "ackSessionId"
        const val ACK_OUTCOME = "ackOutcome"
        const val ID = "id"
        const val GLUCOSE_VALUE = "glucoseValue"
        const val GLUCOSE_TREND = "glucoseTrend"
        const val GLUCOSE_DELTA = "glucoseDelta"
        const val GLUCOSE_RATE = "glucoseRate"
        const val MEASURED_AT = "measuredAt"
        const val RECEIVED_AT = "receivedAt"
        const val SOURCE_ID = "sourceId"
        const val STEPS = "steps"
        const val FLOORS = "floors"
        const val HEART_RATE = "heartRate"
        const val ACTIVE_CALORIES = "activeCalories"
        const val LAST_MOVEMENT = "lastMovement"
        const val EXERCISE_SESSION_COUNT = "exerciseSessionCount"
        const val EXERCISE_DURATION_MINUTES = "exerciseDurationMinutes"
        const val RECOMMENDATION_KIND = "recommendationKind"
        const val RECOMMENDATION_REASON = "recommendationReason"
        const val RECOMMENDATION_ID = "recommendationId"
        const val RECOMMENDATION_CREATED_AT = "recommendationCreatedAt"
        const val RECOMMENDATION_VALID_UNTIL = "recommendationValidUntil"
        const val RECOMMENDATION_ALGORITHM_VERSION = "recommendationAlgorithmVersion"
        const val RECOMMENDATION_TRIGGER_CONTEXT_ID = "recommendationTriggerContextId"
        const val RECOMMENDATION_TRIGGER_AT = "recommendationTriggerAt"
        const val RECOMMENDATION_GLUCOSE_SOURCE_ID = "recommendationGlucoseSourceId"
        const val RECOMMENDATION_SAFETY_READING_ID = "recommendationSafetyReadingId"
        const val RECOMMENDATION_SAFETY_READING_AT = "recommendationSafetyReadingAt"
        const val INTERVENTION_TYPE = "interventionType"
        const val TITLE = "title"
        const val DETAIL = "detail"
        const val ACTION_LABEL = "actionLabel"
        const val DURATION = "duration"
        const val TARGET_FLOORS = "targetFloors"
        const val SESSION_STATUS = "sessionStatus"
        const val SESSION_STARTED_AT = "sessionStartedAt"
        const val SESSION_ENDED_AT = "sessionEndedAt"
        const val BASELINE_GLUCOSE = "baselineGlucose"
        const val BASELINE_READING_ID = "baselineReadingId"
        const val BASELINE_READING_AT = "baselineReadingAt"
        const val BASELINE_SOURCE_ID = "baselineSourceId"
        const val FOLLOW_UP_GLUCOSE = "followUpGlucose"
        const val FOLLOW_UP_DUE_AT = "followUpDueAt"
        const val FOLLOW_UP_READING_AT = "followUpReadingAt"
        const val FOLLOW_UP_READING_ID = "followUpReadingId"
        const val FOLLOW_UP_SOURCE_ID = "followUpSourceId"
        const val FOLLOW_UP_FINALIZED_AT = "followUpFinalizedAt"
        const val SESSION_RECOMMENDATION_ID = "sessionRecommendationId"
        const val SESSION_RECOMMENDATION_REASON = "sessionRecommendationReason"
        const val SESSION_RECOMMENDATION_ALGORITHM_VERSION =
            "sessionRecommendationAlgorithmVersion"
        const val SESSION_RECOMMENDATION_CREATED_AT = "sessionRecommendationCreatedAt"
        const val SESSION_RECOMMENDATION_VALID_UNTIL = "sessionRecommendationValidUntil"
        const val SESSION_TRIGGER_CONTEXT_ID = "sessionTriggerContextId"
        const val SESSION_TRIGGER_AT = "sessionTriggerAt"
        const val BASELINE_EFFECTIVE_RATE = "baselineEffectiveRate"
        const val SESSION_LOW_GLUCOSE_THRESHOLD = "sessionLowGlucoseThreshold"
        const val COMMAND_ID = "commandId"
        const val COMMAND_TYPE = "commandType"
        const val COMMAND_CREATED_AT = "commandCreatedAt"
        const val COMMAND_SESSION_ID = "commandSessionId"
        const val COMMAND_RECOMMENDATION_ID = "commandRecommendationId"
        const val COMMAND_RECOMMENDATION_VALID_UNTIL = "commandRecommendationValidUntil"
        const val COMMAND_RECOMMENDATION_REASON = "commandRecommendationReason"
        const val COMMAND_RECOMMENDATION_ALGORITHM_VERSION =
            "commandRecommendationAlgorithmVersion"
        const val COMMAND_GLUCOSE_SOURCE_ID = "commandGlucoseSourceId"
        const val COMMAND_SAFETY_READING_ID = "commandSafetyReadingId"
        const val COMMAND_SAFETY_READING_AT = "commandSafetyReadingAt"
        const val COMMAND_RECOMMENDATION_CREATED_AT = "commandRecommendationCreatedAt"
        const val COMMAND_TRIGGER_CONTEXT_ID = "commandTriggerContextId"
        const val COMMAND_TRIGGER_AT = "commandTriggerAt"
        const val PROVIDER_MODE = "providerMode"
        const val HEALTH_CONNECT_GLUCOSE_ORIGIN = "healthConnectGlucoseOrigin"
        const val UNIT = "unit"
        const val LOW_THRESHOLD = "lowThreshold"
        const val TARGET_LOWER = "targetLower"
        const val TARGET_UPPER = "targetUpper"
        const val RAPID_RISE = "rapidRise"
        const val EXERCISE_PAUSE_FALL_RATE = "exercisePauseFallRate"
        const val STALE_MINUTES = "staleMinutes"
        const val WALK_MINUTES = "walkMinutes"
        const val STAIR_FLOORS = "stairFloors"
        const val DAILY_STEP_GOAL = "dailyStepGoal"
        const val DAILY_FLOOR_GOAL = "dailyFloorGoal"
        const val INACTIVITY_MINUTES = "inactivityMinutes"
        const val POST_MEAL_DELAY = "postMealDelay"
        const val POST_MEAL_WINDOW = "postMealWindow"
        const val COOLDOWN_MINUTES = "cooldownMinutes"
        const val SNOOZE_MINUTES = "snoozeMinutes"
        const val MAX_NOTIFICATIONS = "maxNotifications"
        const val QUIET_START = "quietStart"
        const val QUIET_END = "quietEnd"
        const val WORK_START = "workStart"
        const val WORK_END = "workEnd"
        const val MIN_OBSERVATIONS = "minObservations"
        const val MIN_TIMING_BUCKET_SAMPLES = "minimumTimingBucketSamples"
        const val MIN_COMPARABLE_TIMING_BUCKETS = "minimumComparableTimingBuckets"
        const val INTERVENTION_TIMING_BUCKET_MINUTES =
            "interventionTimingBucketMinutes"
        const val POST_MEAL_TIMING_BUCKET_MINUTES = "postMealTimingBucketMinutes"
        const val FOLLOW_UP_DELAY_BUCKET_MINUTES = "followUpDelayBucketMinutes"
        const val BASELINE_GLUCOSE_BAND_MG_DL = "baselineGlucoseBandMgDl"
        const val INTERVENTION_FOLLOW_UP_MINUTES = "interventionFollowUpMinutes"
        const val QUICK_ACTION_EXPIRY_MINUTES = "quickActionExpiryMinutes"
        const val WALK_ENABLED = "walkEnabled"
        const val STAIRS_ENABLED = "stairsEnabled"
        const val POST_MEAL_ENABLED = "postMealEnabled"
        const val NOTIFICATIONS_ENABLED = "notificationsEnabled"
        const val THEME = "theme"
        const val FONT_SCALE = "fontScale"
    }
}
