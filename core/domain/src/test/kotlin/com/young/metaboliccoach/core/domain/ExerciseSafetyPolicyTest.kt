package com.young.metaboliccoach.core.domain

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
        recommendation = action(now + 60_000L),
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
