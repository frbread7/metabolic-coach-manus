package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSafetyPolicyTest {
    private val now = 1_700_000_000_000L
    private val settings = DefaultCoachSettings.create()

    @Test
    fun `reading becomes stale exactly at configured boundary`() {
        val reading = glucose().copy(
            measuredAtEpochMillis =
                now - settings.staleReadingMinutes * 60_000L,
        )

        assertEquals(
            ExerciseSafetyStatus.STALE,
            ExerciseSafetyPolicy.evaluate(reading, settings, now),
        )
    }

    @Test
    fun `numeric rate takes precedence over conflicting trend`() {
        val reading = glucose().copy(
            trend = GlucoseTrend.RAPIDLY_RISING,
            rateMgDlPerMinute = -settings.exercisePauseFallRateMgDlPerMinute,
        )

        assertEquals(
            ExerciseSafetyStatus.FALLING_QUICKLY,
            ExerciseSafetyPolicy.evaluate(reading, settings, now),
        )
    }

    @Test
    fun `falling trend pauses exercise when numeric rate is unavailable`() {
        val reading = glucose().copy(
            trend = GlucoseTrend.RAPIDLY_FALLING,
            rateMgDlPerMinute = null,
        )

        assertEquals(
            ExerciseSafetyStatus.FALLING_QUICKLY,
            ExerciseSafetyPolicy.evaluate(reading, settings, now),
        )
    }

    @Test
    fun `wear suppresses an expired cached exercise action`() {
        val state = watchState().copy(
            recommendation = action(validUntilEpochMillis = now),
        )

        assertNull(state.effectiveRecommendation(now, minuteOfDay = 12 * 60))
    }

    @Test
    fun `wear suppresses cached action when current glucose is falling`() {
        val state = watchState().copy(
            glucose = glucose().copy(
                rateMgDlPerMinute = -settings.exercisePauseFallRateMgDlPerMinute,
            ),
        )

        assertNull(state.effectiveRecommendation(now, minuteOfDay = 12 * 60))
    }

    @Test
    fun `wear suppresses rapid action with incomplete provenance`() {
        val state = watchState().copy(
            recommendation = action(now + 60_000L),
        )

        assertNull(state.effectiveRecommendation(now, minuteOfDay = 12 * 60))
    }

    @Test
    fun `wear accepts matching rapid action and hides it after a newer reading`() {
        val current = glucose()
        val recommendation = action(now + 60_000L).copy(
            algorithmVersion = 3,
            triggerContextId = "rapid-pair:v3:fingerprint",
            triggerAtEpochMillis = current.measuredAtEpochMillis,
            glucoseSourceId = current.sourceId,
            safetyReadingId = current.id,
            safetyReadingAtEpochMillis = current.measuredAtEpochMillis,
        )
        val state = watchState().copy(
            glucose = current,
            recommendation = recommendation,
        )

        assertEquals(
            recommendation,
            state.effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
        assertNull(
            state.copy(
                glucose = current.copy(
                    id = "newer-reading",
                    measuredAtEpochMillis = now + 1,
                    receivedAtEpochMillis = now + 1,
                ),
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
    }

    @Test
    fun `wear suppresses action after glucose source changes`() {
        val current = glucose()
        val recommendation = action(now + 60_000L).copy(
            algorithmVersion = 3,
            triggerContextId = "rapid-pair:v3:fingerprint",
            triggerAtEpochMillis = current.measuredAtEpochMillis,
            glucoseSourceId = current.sourceId,
            safetyReadingId = current.id,
            safetyReadingAtEpochMillis = current.measuredAtEpochMillis,
        )

        assertNull(
            watchState().copy(
                glucose = current.copy(sourceId = "health-connect:source-b"),
                recommendation = recommendation,
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
    }

    @Test
    fun `wear suppresses cached action after entering quiet hours`() {
        val state = watchState()

        assertNull(
            state.effectiveRecommendation(
                now,
                minuteOfDay = settings.quietHoursStartMinuteOfDay,
            ),
        )
    }

    @Test
    fun `wear accepts policy-matching inactivity and tolerates a safe snapshot refresh`() {
        val activity = inactiveActivity()
        val recommendation = inactivityAction(activity)
        val state = watchState().copy(
            activity = activity,
            recommendation = recommendation,
        )

        assertEquals(
            recommendation,
            state.effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
        assertEquals(
            recommendation,
            state.copy(
                activity = activity.copy(
                    stepsToday = activity.stepsToday + 100,
                    measuredAtEpochMillis = now + 1_000L,
                ),
            ).effectiveRecommendation(now + 1_000L, minuteOfDay = 12 * 60),
        )
    }

    @Test
    fun `wear suppresses inactivity when current activity no longer confirms its episode`() {
        val activity = inactiveActivity()
        val recommendation = inactivityAction(activity)
        val state = watchState().copy(
            activity = activity,
            recommendation = recommendation,
        )

        assertNull(
            state.copy(
                activity = activity.copy(sourceId = "other-source"),
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
        assertNull(
            state.copy(
                activity = activity.copy(
                    measuredAtEpochMillis =
                        now - settings.staleReadingMinutes * 60_000L,
                ),
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
        assertNull(
            state.copy(
                settings = settings.copy(prolongedInactivityMinutes = 61),
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
        assertNull(
            state.copy(
                settings = settings.copy(walkingRemindersEnabled = false),
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
        assertNull(
            state.copy(
                recommendation = recommendation.copy(
                    interventionType = InterventionType.STAIRS,
                    durationMinutes = null,
                    targetFloors = settings.stairTargetFloors,
                    algorithmVersion = 2,
                ),
            ).effectiveRecommendation(now, minuteOfDay = 12 * 60),
        )
    }

    @Test
    fun `coached action gate rechecks current reading at tap time`() {
        assertTrue(CoachedExerciseActionPolicy.canStart(glucose(), settings, now, 12 * 60))
        assertFalse(
            CoachedExerciseActionPolicy.canStart(
                glucose().copy(valueMgDl = settings.lowGlucoseThresholdMgDl - 1),
                settings,
                now,
                12 * 60,
            ),
        )
        assertFalse(
            CoachedExerciseActionPolicy.canStart(
                glucose(),
                settings,
                now,
                settings.quietHoursStartMinuteOfDay,
            ),
        )
    }

    private fun watchState() = WatchState(
        glucose = glucose(),
        activity = null,
        recommendation = matchingAction(now + 60_000L),
        settings = settings,
        phoneBatteryPercent = null,
        generatedAtEpochMillis = now,
    )

    private fun action(validUntilEpochMillis: Long) = CoachRecommendation.Action(
        reason = CoachReason.RAPID_GLUCOSE_RISE,
        id = "action",
        createdAtEpochMillis = now,
        validUntilEpochMillis = validUntilEpochMillis,
        interventionType = InterventionType.WALK,
        title = "Walk now?",
        actionLabel = "START WALK",
        durationMinutes = 10,
        targetFloors = null,
    )

    private fun matchingAction(validUntilEpochMillis: Long): CoachRecommendation.Action {
        val current = glucose()
        return action(validUntilEpochMillis).copy(
            algorithmVersion = 3,
            triggerContextId = "rapid-pair:v3:fingerprint",
            triggerAtEpochMillis = current.measuredAtEpochMillis,
            glucoseSourceId = current.sourceId,
            safetyReadingId = current.id,
            safetyReadingAtEpochMillis = current.measuredAtEpochMillis,
        )
    }

    private fun inactivityAction(activity: ActivitySnapshot): CoachRecommendation.Action {
        val confirmation = requireNotNull(
            InactivityConfirmationPolicy.confirm(
                activity = activity,
                settings = settings,
                nowEpochMillis = now,
                minuteOfDay = 12 * 60,
            ),
        )
        val current = glucose()
        return CoachRecommendation.Action(
            reason = CoachReason.PROLONGED_INACTIVITY,
            id = confirmation.recommendationId,
            createdAtEpochMillis = now,
            validUntilEpochMillis = confirmation.activityFreshUntilEpochMillis,
            interventionType = InterventionType.WALK,
            title = "Walk now?",
            actionLabel = "START WALK",
            durationMinutes = settings.walkingDurationMinutes,
            targetFloors = null,
            algorithmVersion = InactivityConfirmationPolicy.ALGORITHM_VERSION,
            triggerContextId = confirmation.triggerIdentity,
            triggerAtEpochMillis = confirmation.thresholdCrossingAtEpochMillis,
            glucoseSourceId = current.sourceId,
            safetyReadingId = current.id,
            safetyReadingAtEpochMillis = current.measuredAtEpochMillis,
        )
    }

    private fun inactiveActivity() = ActivitySnapshot(
        stepsToday = 2_000,
        floorsToday = 1.0,
        latestHeartRateBpm = 70,
        activeCaloriesToday = 100.0,
        lastMovementAtEpochMillis = now - settings.prolongedInactivityMinutes * 60_000L,
        measuredAtEpochMillis = now,
        sourceId = "health-connect",
    )

    private fun glucose() = GlucoseReading(
        id = "reading",
        valueMgDl = 140,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = now,
        receivedAtEpochMillis = now,
        sourceId = "health-connect:source-a",
    )
}
