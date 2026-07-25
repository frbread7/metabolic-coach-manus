package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.PersonalObservationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationAnalyzerTest {
    private val analyzer = ObservationAnalyzer()

    @Test
    fun `manual sessions retain effect observations but never enter timing analysis`() {
        val sessions = listOf(-10, -20, 5).mapIndexed { index, change ->
            session(
                id = "legacy-$index",
                index = index,
                changeMgDl = change,
                coached = false,
            )
        }

        val result = analyze(sessions, minimumSamples = 3)

        assertEquals(1, result.size)
        assertEquals(PersonalObservationKind.ACTIVITY_EFFECT, result.single().kind)
        assertEquals(-10, result.single().medianChangeMgDl)
        assertTrue(result.single().text.contains("around 60 minutes"))
        assertTrue(result.single().text.contains("not medical advice"))
    }

    @Test
    fun `personal observations never combine sessions from different exact sources`() {
        val sourceA = listOf(-30, -20, -10).mapIndexed { index, change ->
            session("source-a-$index", index, change, coached = false)
        }
        val sourceB = listOf(30, 40, 50).mapIndexed { index, change ->
            session("source-b-$index", index + sourceA.size, change, coached = false)
                .withSource(OTHER_SOURCE_ID)
        }
        val settings = DefaultCoachSettings.create().copy(minimumObservationSamples = 3)

        val observationA = analyzer.analyze(
            sessions = sourceA + sourceB,
            exactSourceId = SOURCE_ID,
            settings = settings,
        ).single()
        val observationB = analyzer.analyze(
            sessions = sourceA + sourceB,
            exactSourceId = OTHER_SOURCE_ID,
            settings = settings,
        ).single()

        assertEquals(3, observationA.sampleCount)
        assertEquals(-20, observationA.medianChangeMgDl)
        assertEquals(3, observationB.sampleCount)
        assertEquals(40, observationB.medianChangeMgDl)
    }

    @Test
    fun `below threshold follow up cannot make an effect cohort sufficient`() {
        val sessions = listOf(-10, -20, -80).mapIndexed { index, change ->
            session(
                id = "effect-$index",
                index = index,
                changeMgDl = change,
                coached = false,
            )
        }

        assertTrue(analyze(sessions, minimumSamples = 3).isEmpty())
    }

    @Test
    fun `legacy outcomes without captured low threshold are excluded`() {
        val sessions = listOf(-10, -20, -30).mapIndexed { index, change ->
            session(
                id = "legacy-$index",
                index = index,
                changeMgDl = change,
                coached = false,
            ).copy(lowGlucoseThresholdMgDlAtStart = null)
        }

        assertTrue(analyze(sessions, minimumSamples = 3).isEmpty())
    }

    @Test
    fun `two separated trigger delay cohorts emit cautious intervention timing observation`() {
        val early = (0 until 8).map { index ->
            session(
                id = "early-$index",
                index = index,
                changeMgDl = -35 + index,
                triggerDelayMinutes = 2,
            )
        }
        val later = (0 until 8).map { index ->
            session(
                id = "later-$index",
                index = index + 8,
                changeMgDl = -8 + index,
                triggerDelayMinutes = 7,
            )
        }

        val observation = analyze(early + later, minimumSamples = 3)
            .single { it.kind == PersonalObservationKind.INTERVENTION_TIMING }

        assertEquals(0, observation.timingBucketStartMinutes)
        assertEquals(5, observation.timingBucketEndExclusiveMinutes)
        assertEquals(8, observation.sampleCount)
        assertEquals(16, observation.comparisonSampleCount)
        assertCautiousCopy(observation.text)
    }

    @Test
    fun `two separated meal delay cohorts emit post meal timing observation`() {
        val early = (0 until 8).map { index ->
            session(
                id = "meal-early-$index",
                index = index,
                changeMgDl = -30 + index,
                triggerDelayMinutes = 32,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }
        val later = (0 until 8).map { index ->
            session(
                id = "meal-later-$index",
                index = index + 8,
                changeMgDl = -6 + index,
                triggerDelayMinutes = 62,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }
        val meals = (early + later).map {
            MealMarker(
                id = requireNotNull(it.triggerContextId),
                occurredAtEpochMillis = requireNotNull(it.triggerAtEpochMillis),
            )
        }

        val observation = analyze(
            sessions = early + later,
            minimumSamples = 3,
            mealMarkers = meals,
        ).single { it.kind == PersonalObservationKind.POST_MEAL_ACTIVITY_TIMING }

        assertEquals(30, observation.timingBucketStartMinutes)
        assertEquals(45, observation.timingBucketEndExclusiveMinutes)
        assertTrue(observation.text.contains("after a marked meal"))
        assertCautiousCopy(observation.text)
    }

    @Test
    fun `timing analysis requires eight samples in each of two cohorts`() {
        val sessions = (0 until 7).map { index ->
            session("early-$index", index, -30, triggerDelayMinutes = 2)
        } + (0 until 7).map { index ->
            session("later-$index", index + 7, -5, triggerDelayMinutes = 7)
        }

        assertFalse(
            analyze(sessions, minimumSamples = 2)
                .any { it.kind != PersonalObservationKind.ACTIVITY_EFFECT },
        )
    }

    @Test
    fun `configured timing sample floor and generic bucket width control qualification`() {
        val early = (0 until 4).map { index ->
            session("early-$index", index, -30 + index, triggerDelayMinutes = 2)
        }
        val later = (0 until 4).map { index ->
            session("later-$index", index + 4, -5 + index, triggerDelayMinutes = 12)
        }
        val settings = DefaultCoachSettings.create().copy(
            minimumObservationSamples = 2,
            minimumTimingBucketSamples = 4,
            interventionTimingBucketMinutes = 10,
        )

        val observation = analyzer.analyze(
            sessions = early + later,
            exactSourceId = SOURCE_ID,
            settings = settings,
        ).single { it.kind == PersonalObservationKind.INTERVENTION_TIMING }

        assertEquals(0, observation.timingBucketStartMinutes)
        assertEquals(10, observation.timingBucketEndExclusiveMinutes)
        assertFalse(
            analyzer.analyze(
                sessions = early + later,
                exactSourceId = SOURCE_ID,
                settings = settings.copy(minimumTimingBucketSamples = 5),
            ).any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
    }

    @Test
    fun `configured post meal bucket width controls reported timing window`() {
        val early = (0 until 2).map { index ->
            session(
                "early-$index",
                index,
                -30 + index,
                triggerDelayMinutes = 32,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }
        val later = (0 until 2).map { index ->
            session(
                "later-$index",
                index + 2,
                -5 + index,
                triggerDelayMinutes = 62,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }
        val sessions = early + later
        val meals = sessions.map {
            MealMarker(
                id = requireNotNull(it.triggerContextId),
                occurredAtEpochMillis = requireNotNull(it.triggerAtEpochMillis),
            )
        }

        val observation = analyzer.analyze(
            sessions = sessions,
            exactSourceId = SOURCE_ID,
            settings = DefaultCoachSettings.create().copy(
                minimumObservationSamples = 2,
                minimumTimingBucketSamples = 2,
                postMealTimingBucketMinutes = 20,
            ),
            mealMarkers = meals,
        ).single { it.kind == PersonalObservationKind.POST_MEAL_ACTIVITY_TIMING }

        assertEquals(20, observation.timingBucketStartMinutes)
        assertEquals(40, observation.timingBucketEndExclusiveMinutes)
    }

    @Test
    fun `configured comparable bucket minimum controls timing publication`() {
        val sessions = (0 until 2).map { index ->
            session("early-$index", index, -40 + index, triggerDelayMinutes = 2)
        } + (0 until 2).map { index ->
            session("middle-$index", index + 2, -20 + index, triggerDelayMinutes = 7)
        } + (0 until 2).map { index ->
            session("late-$index", index + 4, -5 + index, triggerDelayMinutes = 12)
        }
        val settings = DefaultCoachSettings.create().copy(
            minimumObservationSamples = 2,
            minimumTimingBucketSamples = 2,
            minimumComparableTimingBuckets = 3,
        )

        assertTrue(
            analyzer.analyze(
                sessions = sessions,
                exactSourceId = SOURCE_ID,
                settings = settings,
            )
                .any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
        assertFalse(
            analyzer.analyze(
                sessions = sessions,
                exactSourceId = SOURCE_ID,
                settings = settings.copy(minimumComparableTimingBuckets = 4),
            ).any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
    }

    @Test
    fun `configured follow up and baseline bands control cohort comparability`() {
        val early = (0 until 2).map { index ->
            session("early-$index", index, -30 + index, triggerDelayMinutes = 2)
                .withBaseline(140)
                .withActualFollowUpDelay(60)
        }
        val later = (0 until 2).map { index ->
            session("later-$index", index + 2, -5 + index, triggerDelayMinutes = 7)
                .withBaseline(159)
                .withActualFollowUpDelay(70)
        }
        val settings = DefaultCoachSettings.create().copy(
            minimumObservationSamples = 2,
            minimumTimingBucketSamples = 2,
            followUpDelayBucketMinutes = 15,
            baselineGlucoseBandMgDl = 20,
        )

        assertTrue(
            analyzer.analyze(
                sessions = early + later,
                exactSourceId = SOURCE_ID,
                settings = settings,
            )
                .any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
        assertFalse(
            analyzer.analyze(
                sessions = early + later,
                exactSourceId = SOURCE_ID,
                settings = settings.copy(followUpDelayBucketMinutes = 5),
            ).any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
        assertFalse(
            analyzer.analyze(
                sessions = early + later,
                exactSourceId = SOURCE_ID,
                settings = settings.copy(baselineGlucoseBandMgDl = 10),
            ).any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
    }

    @Test
    fun `equal or overlapping timing distributions do not produce a preferred window`() {
        val sessions = (0 until 8).map { index ->
            session("early-$index", index, -10 + index, triggerDelayMinutes = 2)
        } + (0 until 8).map { index ->
            session("later-$index", index + 8, -10 + index, triggerDelayMinutes = 7)
        }

        assertFalse(
            analyze(sessions, minimumSamples = 3)
                .any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
    }

    @Test
    fun `missing matching meal provenance excludes post meal timing`() {
        val sessions = (0 until 8).map { index ->
            session(
                "early-$index",
                index,
                -30 + index,
                triggerDelayMinutes = 32,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        } + (0 until 8).map { index ->
            session(
                "later-$index",
                index + 8,
                -5 + index,
                triggerDelayMinutes = 62,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }

        assertFalse(
            analyze(sessions, minimumSamples = 3, mealMarkers = emptyList())
                .any { it.kind == PersonalObservationKind.POST_MEAL_ACTIVITY_TIMING },
        )
    }

    @Test
    fun `low follow up samples cannot make timing buckets appear sufficient`() {
        val early = (0 until 8).map { index ->
            session("early-$index", index, -30 + index, triggerDelayMinutes = 2)
        }.toMutableList()
        val later = (0 until 8).map { index ->
            session("later-$index", index + 8, -5 + index, triggerDelayMinutes = 7)
        }.toMutableList()
        early[0] = early[0].copy(glucoseAfterMgDl = 60)
        later[0] = later[0].copy(glucoseAfterMgDl = 60)

        assertFalse(
            analyze(early + later, minimumSamples = 3)
                .any { it.kind == PersonalObservationKind.INTERVENTION_TIMING },
        )
    }

    @Test
    fun `additional meal before follow up prevents a post meal timing preference`() {
        val early = (0 until 8).map { index ->
            session(
                "early-$index",
                index,
                -30 + index,
                triggerDelayMinutes = 32,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }
        val later = (0 until 8).map { index ->
            session(
                "later-$index",
                index + 8,
                -5 + index,
                triggerDelayMinutes = 62,
                reason = CoachReason.POST_MEAL_WINDOW,
            )
        }
        val meals = (early + later).map {
            MealMarker(
                id = requireNotNull(it.triggerContextId),
                occurredAtEpochMillis = requireNotNull(it.triggerAtEpochMillis),
            )
        } + MealMarker(
            id = "additional-meal",
            occurredAtEpochMillis = early.first().startedAtEpochMillis + MILLIS_PER_MINUTE,
        )

        assertFalse(
            analyze(
                sessions = early + later,
                minimumSamples = 3,
                mealMarkers = meals,
            ).any { it.kind == PersonalObservationKind.POST_MEAL_ACTIVITY_TIMING },
        )
    }

    @Test
    fun `invalid exact source or incomplete follow up is excluded`() {
        val valid = session("valid", 0, -10, coached = false)
        val mixedSource = session("mixed", 1, -20, coached = false).copy(
            followUpGlucoseSourceId = "health-connect:other",
        )
        val incomplete = session("incomplete", 2, -30, coached = false).copy(
            followUpFinalizedAtEpochMillis = null,
        )

        assertTrue(
            analyze(
                listOf(valid, mixedSource, incomplete),
                minimumSamples = 2,
            ).isEmpty(),
        )
    }

    private fun analyze(
        sessions: List<InterventionSession>,
        minimumSamples: Int,
        mealMarkers: List<MealMarker> = emptyList(),
    ) = analyzer.analyze(
        sessions = sessions,
        exactSourceId = SOURCE_ID,
        settings = DefaultCoachSettings.create().copy(
            minimumObservationSamples = minimumSamples,
        ),
        mealMarkers = mealMarkers,
    )

    private fun session(
        id: String,
        index: Int,
        changeMgDl: Int,
        triggerDelayMinutes: Int = 2,
        reason: CoachReason = CoachReason.RAPID_GLUCOSE_RISE,
        coached: Boolean = true,
    ): InterventionSession {
        val startedAt = BASE_EPOCH_MILLIS + index * SESSION_SPACING_MILLIS
        val endedAt = startedAt + 10 * MILLIS_PER_MINUTE
        val dueAt = endedAt + 60 * MILLIS_PER_MINUTE
        val triggerAt = startedAt - triggerDelayMinutes * MILLIS_PER_MINUTE
        return InterventionSession(
            id = id,
            type = InterventionType.WALK,
            status = InterventionStatus.COMPLETED,
            startedAtEpochMillis = startedAt,
            endedAtEpochMillis = endedAt,
            targetDurationMinutes = 10,
            targetFloors = null,
            baselineGlucoseMgDl = 140,
            baselineGlucoseReadingId = "before-$id",
            baselineGlucoseMeasuredAtEpochMillis = startedAt - MILLIS_PER_MINUTE,
            baselineGlucoseSourceId = SOURCE_ID,
            glucoseAfterMgDl = 140 + changeMgDl,
            followUpDueAtEpochMillis = dueAt,
            followUpReadingAtEpochMillis = dueAt,
            followUpGlucoseReadingId = "after-$id",
            followUpGlucoseSourceId = SOURCE_ID,
            followUpFinalizedAtEpochMillis = dueAt + MILLIS_PER_MINUTE,
            recommendationId = "recommendation-$id".takeIf { coached },
            recommendationReason = reason.takeIf { coached },
            recommendationAlgorithmVersion = 1.takeIf { coached },
            recommendationCreatedAtEpochMillis =
                (startedAt - MILLIS_PER_MINUTE).takeIf { coached },
            recommendationValidUntilEpochMillis =
                (startedAt + 15 * MILLIS_PER_MINUTE).takeIf { coached },
            triggerContextId = "trigger-$id".takeIf { coached },
            triggerAtEpochMillis = triggerAt.takeIf { coached },
            baselineEffectiveRateMgDlPerMinute = 1.0.takeIf { coached },
            lowGlucoseThresholdMgDlAtStart = 70,
        )
    }

    private fun InterventionSession.withBaseline(valueMgDl: Int): InterventionSession {
        val change = requireNotNull(glucoseAfterMgDl) - requireNotNull(baselineGlucoseMgDl)
        return copy(
            baselineGlucoseMgDl = valueMgDl,
            glucoseAfterMgDl = valueMgDl + change,
        )
    }

    private fun InterventionSession.withActualFollowUpDelay(
        minutes: Int,
    ): InterventionSession {
        val endedAt = requireNotNull(endedAtEpochMillis)
        val readingAt = endedAt + minutes * MILLIS_PER_MINUTE
        return copy(
            followUpReadingAtEpochMillis = readingAt,
            followUpFinalizedAtEpochMillis = readingAt + MILLIS_PER_MINUTE,
        )
    }

    private fun InterventionSession.withSource(sourceId: String): InterventionSession = copy(
        baselineGlucoseSourceId = sourceId,
        followUpGlucoseSourceId = sourceId,
    )

    private fun assertCautiousCopy(text: String) {
        val lower = text.lowercase()
        assertTrue(lower.contains("observed"))
        assertTrue(lower.contains("other factors"))
        assertTrue(lower.contains("not medical advice"))
        listOf("best", "ideal", "caused", "improves").forEach {
            assertFalse(lower.contains(it))
        }
    }

    private companion object {
        const val BASE_EPOCH_MILLIS = 1_700_000_000_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val SESSION_SPACING_MILLIS = 3 * 60 * MILLIS_PER_MINUTE
        const val SOURCE_ID = "health-connect:source-a"
        const val OTHER_SOURCE_ID = "nightscout:server-b:fingerprint"
    }
}
