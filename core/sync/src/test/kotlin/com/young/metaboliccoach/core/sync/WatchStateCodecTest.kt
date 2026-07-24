package com.young.metaboliccoach.core.sync

import com.google.android.gms.wearable.DataMap
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachTheme
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.GlucoseUnit
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import com.young.metaboliccoach.core.model.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStateCodecTest {
    private val codec = WatchStateCodec()

    @Test
    fun `full watch state survives encode and decode`() {
        val state = WatchState(
            glucose = GlucoseReading(
                id = "health-connect:1720000000000",
                valueMgDl = 147,
                trend = GlucoseTrend.RISING,
                deltaMgDl = 12,
                rateMgDlPerMinute = 2.4,
                measuredAtEpochMillis = 1_720_000_000_000,
                receivedAtEpochMillis = 1_720_000_002_000,
                sourceId = "health-connect",
            ),
            activity = ActivitySnapshot(
                stepsToday = 8_642,
                floorsToday = 12.5,
                latestHeartRateBpm = 78,
                activeCaloriesToday = 432.25,
                lastMovementAtEpochMillis = 1_720_000_001_000,
                measuredAtEpochMillis = 1_720_000_003_000,
                sourceId = "samsung-health",
            ),
            recommendation = CoachRecommendation.Action(
                reason = CoachReason.RAPID_GLUCOSE_RISE,
                id = "rapid-rise:health-connect:1720000000000",
                createdAtEpochMillis = 1_720_000_003_500,
                validUntilEpochMillis = 1_720_000_900_000,
                interventionType = InterventionType.WALK,
                title = "Walk now?",
                actionLabel = "Start 12-minute walk",
                durationMinutes = 12,
                targetFloors = null,
                algorithmVersion = 2,
                triggerContextId = "health-connect:1720000000000",
                triggerAtEpochMillis = 1_720_000_000_000,
            ),
            settings = DefaultCoachSettings.create().copy(
                glucoseProviderMode = GlucoseProviderMode.XDRIP_BROADCAST,
                healthConnectGlucoseOriginPackage = "com.example.cgm",
                glucoseUnit = GlucoseUnit.MMOL_L,
                lowGlucoseThresholdMgDl = 75,
                targetLowerMgDl = 80,
                targetUpperMgDl = 155,
                rapidRiseThresholdMgDlPerMinute = 1.75,
                exercisePauseFallRateMgDlPerMinute = 1.5,
                staleReadingMinutes = 12,
                walkingDurationMinutes = 12,
                stairTargetFloors = 8,
                dailyStepGoal = 9_500,
                dailyFloorGoal = 14,
                prolongedInactivityMinutes = 47,
                postMealDelayMinutes = 25,
                postMealWindowMinutes = 55,
                reminderCooldownMinutes = 48,
                snoozeMinutes = 13,
                maximumNotificationsPerDay = 4,
                quietHoursStartMinuteOfDay = 1_320,
                quietHoursEndMinuteOfDay = 390,
                workingHoursStartMinuteOfDay = 510,
                workingHoursEndMinuteOfDay = 1_050,
                minimumObservationSamples = 5,
                minimumTimingBucketSamples = 9,
                minimumComparableTimingBuckets = 3,
                interventionTimingBucketMinutes = 7,
                postMealTimingBucketMinutes = 21,
                followUpDelayBucketMinutes = 18,
                baselineGlucoseBandMgDl = 25,
                interventionFollowUpMinutes = 75,
                quickActionExpiryMinutes = 720,
                walkingRemindersEnabled = false,
                stairRemindersEnabled = true,
                postMealRemindersEnabled = false,
                notificationsEnabled = true,
                theme = CoachTheme.HIGH_CONTRAST,
                fontScale = 1.25f,
            ),
            phoneBatteryPercent = 64,
            generatedAtEpochMillis = 1_720_000_004_000,
            activeSession = InterventionSession(
                id = "walk-123",
                type = InterventionType.WALK,
                status = InterventionStatus.STARTED,
                startedAtEpochMillis = 1_720_000_000_000,
                endedAtEpochMillis = null,
                targetDurationMinutes = 12,
                targetFloors = null,
                baselineGlucoseMgDl = 147,
                baselineGlucoseReadingId = "health-connect:1720000000000",
                baselineGlucoseMeasuredAtEpochMillis = 1_720_000_000_000,
                baselineGlucoseSourceId = "health-connect:com.example.cgm",
                glucoseAfterMgDl = null,
                recommendationId = "rapid-rise:health-connect:1720000000000",
                recommendationReason = CoachReason.RAPID_GLUCOSE_RISE,
                recommendationAlgorithmVersion = 2,
                recommendationCreatedAtEpochMillis = 1_720_000_003_500,
                recommendationValidUntilEpochMillis = 1_720_000_900_000,
                triggerContextId = "health-connect:1720000000000",
                triggerAtEpochMillis = 1_720_000_000_000,
                baselineEffectiveRateMgDlPerMinute = 2.4,
                lowGlucoseThresholdMgDlAtStart = 75,
            ),
            phoneInstanceId = "phone-installation-1",
            stateRevision = 42,
            lastSessionCommandAck = SessionCommandAck(
                commandId = "command-previous",
                sessionId = "walk-previous",
                outcome = SessionCommandOutcome.APPLIED,
            ),
            dataResetId = "data-reset-2",
        )

        assertEquals(state, codec.decode(codec.encode(state)))
    }

    @Test
    fun `watch state without newer analysis settings uses safe defaults`() {
        val defaults = DefaultCoachSettings.create()
        val encoded = codec.encode(
            WatchState(
                glucose = null,
                activity = null,
                recommendation = null,
                settings = defaults,
                phoneBatteryPercent = null,
                generatedAtEpochMillis = 43,
            ),
        )
        val encodedSettings = requireNotNull(encoded.getDataMap("settings"))
        listOf(
            "minimumTimingBucketSamples",
            "minimumComparableTimingBuckets",
            "interventionTimingBucketMinutes",
            "postMealTimingBucketMinutes",
            "followUpDelayBucketMinutes",
            "baselineGlucoseBandMgDl",
        ).forEach(encodedSettings::remove)

        val decoded = requireNotNull(codec.decode(encoded)).settings

        assertEquals(defaults.minimumTimingBucketSamples, decoded.minimumTimingBucketSamples)
        assertEquals(
            defaults.minimumComparableTimingBuckets,
            decoded.minimumComparableTimingBuckets,
        )
        assertEquals(
            defaults.interventionTimingBucketMinutes,
            decoded.interventionTimingBucketMinutes,
        )
        assertEquals(
            defaults.postMealTimingBucketMinutes,
            decoded.postMealTimingBucketMinutes,
        )
        assertEquals(defaults.followUpDelayBucketMinutes, decoded.followUpDelayBucketMinutes)
        assertEquals(defaults.baselineGlucoseBandMgDl, decoded.baselineGlucoseBandMgDl)
    }

    @Test
    fun `nullable state fields and information recommendation survive round trip`() {
        val state = WatchState(
            glucose = null,
            activity = ActivitySnapshot(
                stepsToday = 0,
                floorsToday = 0.0,
                latestHeartRateBpm = null,
                activeCaloriesToday = null,
                lastMovementAtEpochMillis = null,
                measuredAtEpochMillis = 42,
                sourceId = "health-connect",
            ),
            recommendation = CoachRecommendation.Information(
                reason = CoachReason.STALE_GLUCOSE_DATA,
                title = "Glucose data is stale",
                detail = "Open the phone app to refresh.",
            ),
            settings = DefaultCoachSettings.create(),
            phoneBatteryPercent = null,
            generatedAtEpochMillis = 43,
        )

        assertEquals(state, codec.decode(codec.encode(state)))
    }

    @Test
    fun `quick action command survives encode and decode`() {
        val command = QuickActionCommand(
            id = "command-123",
            type = QuickActionType.START_STAIRS,
            createdAtEpochMillis = 1_720_000_000_000,
            sessionId = "stairs-123",
            recommendationId = "inactive:reading",
            recommendationValidUntilEpochMillis = 1_720_000_900_000,
            recommendationReason = CoachReason.PROLONGED_INACTIVITY,
            recommendationAlgorithmVersion = 1,
            recommendationCreatedAtEpochMillis = 1_720_000_000_000,
            triggerContextId = "activity:1719990000000",
            triggerAtEpochMillis = 1_719_990_000_000,
            dataResetId = "data-reset-2",
        )

        assertEquals(command, codec.decodeCommand(codec.encode(command)))
    }

    @Test
    fun `unknown schema is rejected`() {
        val unsupportedState = DataMap().apply {
            putInt("schemaVersion", SyncSchema.VERSION + 1)
        }
        val unsupportedCommand = DataMap().apply {
            putInt("schemaVersion", SyncSchema.VERSION + 1)
        }

        assertNull(codec.decode(unsupportedState))
        assertNull(codec.decodeCommand(unsupportedCommand))
    }

    @Test
    fun `malformed command is rejected without throwing`() {
        val malformed = DataMap().apply {
            putInt("schemaVersion", SyncSchema.VERSION)
            putString("commandId", "command-123")
            putString("commandType", "NOT_A_REAL_ACTION")
            putLong("commandCreatedAt", 123)
        }

        assertNull(codec.decodeCommand(malformed))
    }

    @Test
    fun `watch state missing required settings is rejected without throwing`() {
        val malformed = DataMap().apply {
            putInt("schemaVersion", SyncSchema.VERSION)
            putLong("generatedAt", 123)
        }

        assertNull(codec.decode(malformed))
    }

    @Test
    fun `command missing required timestamp is rejected without throwing`() {
        val malformed = DataMap().apply {
            putInt("schemaVersion", SyncSchema.VERSION)
            putString("commandId", "command-123")
            putString("commandType", QuickActionType.START_WALK.name)
        }

        assertNull(codec.decodeCommand(malformed))
    }

    @Test
    fun `watch state with malformed active session is rejected atomically`() {
        val encoded = codec.encode(
            WatchState(
                glucose = null,
                activity = null,
                recommendation = null,
                settings = DefaultCoachSettings.create(),
                phoneBatteryPercent = null,
                generatedAtEpochMillis = 123,
            ),
        ).apply {
            putDataMap(
                "activeSession",
                DataMap().apply { putString("id", "incomplete-session") },
            )
        }

        assertNull(codec.decode(encoded))
    }

    @Test
    fun `watch state with unknown acknowledgement outcome is rejected atomically`() {
        val encoded = codec.encode(
            WatchState(
                glucose = null,
                activity = null,
                recommendation = null,
                settings = DefaultCoachSettings.create(),
                phoneBatteryPercent = null,
                generatedAtEpochMillis = 123,
            ),
        ).apply {
            putDataMap(
                "sessionCommandAck",
                DataMap().apply {
                    putString("ackCommandId", "command")
                    putString("ackOutcome", "UNKNOWN")
                },
            )
        }

        assertNull(codec.decode(encoded))
    }
}
