package com.young.metaboliccoach.background

import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.InactivityConfirmationPolicy
import com.young.metaboliccoach.core.domain.RapidRiseConfirmationPolicy
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DailySummary
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.PersonalObservation
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class QuickActionHandlerTest {
    @Test
    fun `expired coached action is rejected without mutation`() = runTest {
        val action = recommendation(
            id = "old-action",
            validUntilEpochMillis = NOW,
        )
        val fixture = fixture(recommendations = listOf(action))
        val result = fixture.handler.handle(
            command(
                recommendationId = "old-action",
                recommendationValidUntilEpochMillis = NOW,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED),
            result,
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `mixed version coached action without complete safety provenance is rejected`() = runTest {
        val action = recommendation(
            id = "legacy-action",
            validUntilEpochMillis = NOW + 60_000,
        )
        val fixture = fixture(
            readings = listOf(reading("current", "health-connect:caresens", NOW - 1_000)),
            recommendations = listOf(action),
        )

        val result = fixture.handler.handle(
            command(
                recommendationId = action.id,
                recommendationValidUntilEpochMillis = action.validUntilEpochMillis,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            result,
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `start stores the exact baseline reading provenance`() = runTest {
        val newest = reading("newest", "health-connect:caresens", NOW - 1_000)
        val fixture = fixture(readings = listOf(reading("older", "other", NOW - 2_000), newest))

        assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(command()))

        val stored = fixture.coaching.active
        assertEquals(newest.id, stored?.baselineGlucoseReadingId)
        assertEquals(newest.sourceId, stored?.baselineGlucoseSourceId)
        assertEquals(newest.measuredAtEpochMillis, stored?.baselineGlucoseMeasuredAtEpochMillis)
    }

    @Test
    fun `coached start is rejected after active glucose source changes`() = runTest {
        val baseline = reading("baseline", "health-connect:caresens", NOW - 1_000).copy(
            rateMgDlPerMinute = 1.5,
        )
        val action = recommendation(
            id = "recommendation",
            validUntilEpochMillis = NOW + 60_000,
            includeTimingProvenance = true,
            durationMinutes = 12,
            reason = CoachReason.POST_MEAL_WINDOW,
        )
        val fixture = fixture(
            readings = listOf(
                baseline,
                reading("other-source-newer", "other", NOW),
            ),
            recommendations = listOf(action),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            fixture.handler.handle(
                command(
                    recommendationId = "recommendation",
                    recommendationValidUntilEpochMillis = NOW + 60_000,
                    includeTimingProvenance = true,
                    reason = CoachReason.POST_MEAL_WINDOW,
                ),
            ),
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `coached start stores complete prospective timing provenance`() = runTest {
        val baseline = reading("baseline", "health-connect:caresens", NOW - 1_000).copy(
            rateMgDlPerMinute = 1.5,
        )
        val action = recommendation(
            id = "recommendation",
            validUntilEpochMillis = NOW + 60_000,
            includeTimingProvenance = true,
            durationMinutes = 12,
            reason = CoachReason.POST_MEAL_WINDOW,
        )
        val fixture = fixture(
            readings = listOf(baseline),
            recommendations = listOf(action),
        )

        assertEquals(
            CommandHandlingResult.Applied,
            fixture.handler.handle(
                command(
                    recommendationId = "recommendation",
                    recommendationValidUntilEpochMillis = NOW + 60_000,
                    includeTimingProvenance = true,
                    reason = CoachReason.POST_MEAL_WINDOW,
                ),
            ),
        )

        val stored = fixture.coaching.active
        assertEquals(baseline.id, stored?.baselineGlucoseReadingId)
        assertEquals(baseline.sourceId, stored?.baselineGlucoseSourceId)
        assertEquals("recommendation", stored?.recommendationId)
        assertEquals(CoachReason.POST_MEAL_WINDOW, stored?.recommendationReason)
        assertEquals(1, stored?.recommendationAlgorithmVersion)
        assertEquals(NOW - 2_000, stored?.triggerAtEpochMillis)
        assertEquals(12, stored?.targetDurationMinutes)
        assertEquals(1.5, stored?.baselineEffectiveRateMgDlPerMinute ?: 0.0, 0.0)
        assertEquals(
            DefaultCoachSettings.create().lowGlucoseThresholdMgDl,
            stored?.lowGlucoseThresholdMgDlAtStart,
        )
    }

    @Test
    fun `manual start does not invent recommendation provenance`() = runTest {
        val fixture = fixture(readings = listOf(reading("baseline", "source", NOW - 1_000)))

        assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(command()))

        assertNull(fixture.coaching.active?.recommendationId)
        assertNull(fixture.coaching.active?.recommendationReason)
        assertNull(fixture.coaching.active?.triggerContextId)
    }

    @Test
    fun `coached action is rejected when latest glucose becomes unsafe`() = runTest {
        val falling = reading("falling", "health-connect:caresens", NOW - 1_000).copy(
            rateMgDlPerMinute =
                -DefaultCoachSettings.create().exercisePauseFallRateMgDlPerMinute,
        )
        val fixture = fixture(
            readings = listOf(falling),
            recommendations = listOf(
                recommendation(
                    id = "current-action",
                    validUntilEpochMillis = NOW + 60_000,
                    includeTimingProvenance = true,
                    reason = CoachReason.POST_MEAL_WINDOW,
                ),
            ),
        )

        val result = fixture.handler.handle(
            command(
                recommendationId = "current-action",
                recommendationValidUntilEpochMillis = NOW + 60_000,
                includeTimingProvenance = true,
                reason = CoachReason.POST_MEAL_WINDOW,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_UNSAFE),
            result,
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `coached start accepted while valid survives delayed delivery`() = runTest {
        val actionAt = NOW - 20 * 60_000L
        val actionReading = reading(
            "safe-at-action",
            "health-connect:caresens",
            actionAt - 1_000,
        )
        val unsafeAtReceipt = reading(
            "unsafe-at-receipt",
            "health-connect:caresens",
            NOW - 1_000,
        ).copy(valueMgDl = 60)
        val fixture = fixture(
            readings = listOf(actionReading, unsafeAtReceipt),
            recommendations = listOf(
                recommendation(
                    id = "delayed-action",
                    createdAtEpochMillis = actionAt - 60_000,
                    validUntilEpochMillis = actionAt + 60_000,
                    includeTimingProvenance = true,
                    triggerAtEpochMillis = actionAt - 2_000,
                    safetyReadingAtEpochMillis = actionAt - 1_000,
                    reason = CoachReason.POST_MEAL_WINDOW,
                ),
            ),
        )

        val result = fixture.handler.handle(
            command(
                recommendationId = "delayed-action",
                recommendationValidUntilEpochMillis = actionAt + 60_000,
                includeTimingProvenance = true,
                createdAtEpochMillis = actionAt,
                recommendationCreatedAtEpochMillis = actionAt - 60_000,
                reason = CoachReason.POST_MEAL_WINDOW,
            ),
        )

        assertEquals(CommandHandlingResult.Applied, result)
        assertEquals(actionAt, fixture.coaching.active?.startedAtEpochMillis)
        assertEquals(actionReading.id, fixture.coaching.active?.baselineGlucoseReadingId)
    }

    @Test
    fun `coached start at recommendation validity boundary is rejected`() = runTest {
        val fixture = fixture(
            recommendations = listOf(
                recommendation(
                    id = "boundary-action",
                    createdAtEpochMillis = NOW - 60_000,
                    validUntilEpochMillis = NOW,
                ),
            ),
        )

        val result = fixture.handler.handle(
            command(
                recommendationId = "boundary-action",
                recommendationValidUntilEpochMillis = NOW,
                createdAtEpochMillis = NOW,
                recommendationCreatedAtEpochMillis = NOW - 60_000,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED),
            result,
        )
    }

    @Test
    fun `coached start before recommendation creation is rejected`() = runTest {
        val actionAt = NOW - 60_000
        val fixture = fixture(
            recommendations = listOf(
                recommendation(
                    id = "premature-action",
                    createdAtEpochMillis = actionAt + 1,
                    validUntilEpochMillis = NOW + 60_000,
                ),
            ),
        )

        val result = fixture.handler.handle(
            command(
                recommendationId = "premature-action",
                recommendationValidUntilEpochMillis = NOW + 60_000,
                createdAtEpochMillis = actionAt,
                recommendationCreatedAtEpochMillis = actionAt + 1,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED),
            result,
        )
    }

    @Test
    fun `coached start beyond generic command age is rejected`() = runTest {
        val actionAt =
            NOW - DefaultCoachSettings.create().quickActionExpiryMinutes * 60_000L - 1
        val fixture = fixture(
            recommendations = listOf(
                recommendation(
                    id = "too-old-action",
                    createdAtEpochMillis = actionAt - 60_000,
                    validUntilEpochMillis = actionAt + 60_000,
                ),
            ),
        )

        val result = fixture.handler.handle(
            command(
                recommendationId = "too-old-action",
                recommendationValidUntilEpochMillis = actionAt + 60_000,
                createdAtEpochMillis = actionAt,
                recommendationCreatedAtEpochMillis = actionAt - 60_000,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED),
            result,
        )
    }

    @Test
    fun `coached start without a phone-authored snapshot is rejected`() = runTest {
        val fixture = fixture()

        val result = fixture.handler.handle(
            command(
                recommendationId = "unknown-action",
                recommendationValidUntilEpochMillis = NOW + 60_000,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED),
            result,
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `conflicting echoed recommendation fields are rejected`() = runTest {
        val action = recommendation(
            id = "authored-action",
            validUntilEpochMillis = NOW + 60_000,
            includeTimingProvenance = true,
        )
        val fixture = fixture(recommendations = listOf(action))

        val result = fixture.handler.handle(
            command(
                recommendationId = action.id,
                recommendationValidUntilEpochMillis = action.validUntilEpochMillis,
                includeTimingProvenance = true,
            ).copy(recommendationReason = CoachReason.POST_MEAL_WINDOW),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            result,
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `confirmed rapid pair is accepted at action time`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW - 1_000L)
        val confirmation = requireNotNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                latest,
                DefaultCoachSettings.create(),
            ),
        )
        val action = rapidRecommendation(confirmation)
        val fixture = fixture(readings = listOf(older, latest), recommendations = listOf(action))

        val result = fixture.handler.handle(commandFor(action))

        assertEquals(CommandHandlingResult.Applied, result)
        assertEquals(action.id, fixture.coaching.active?.recommendationId)
        assertEquals(latest.id, fixture.coaching.active?.baselineGlucoseReadingId)
    }

    @Test
    fun `rapid action is rejected when newest same-source pair no longer confirms`() = runTest {
        val older = risingReading("older", NOW - 10 * 60_000L)
        val publishedLatest = risingReading("published-latest", NOW - 5 * 60_000L)
        val confirmation = requireNotNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                publishedLatest,
                DefaultCoachSettings.create(),
            ),
        )
        val action = rapidRecommendation(confirmation)
        val stableLatest = reading("stable-latest", publishedLatest.sourceId, NOW - 1_000L)
        val fixture = fixture(
            readings = listOf(older, publishedLatest, stableLatest),
            recommendations = listOf(action),
        )

        val result = fixture.handler.handle(commandFor(action))

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            result,
        )
        assertNull(fixture.coaching.active)
    }

    @Test
    fun `rapid action is rejected when confirmation history is missing`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW - 1_000L)
        val confirmation = requireNotNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                latest,
                DefaultCoachSettings.create(),
            ),
        )
        val action = rapidRecommendation(confirmation)
        val fixture = fixture(readings = listOf(latest), recommendations = listOf(action))

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            fixture.handler.handle(commandFor(action)),
        )
    }

    @Test
    fun `matching inactivity walk applies stores provenance and duplicate is idempotent`() =
        runTest {
            val inputs = inactivityInputs()
            val fixture = fixture(
                readings = listOf(inputs.glucose),
                activity = inputs.activity,
                recommendations = listOf(inputs.recommendation),
                settings = inputs.settings,
                now = inputs.now,
            )
            val command = commandFor(
                action = inputs.recommendation,
                id = "inactivity-session",
                createdAtEpochMillis = inputs.now,
            )

            assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(command))
            assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(command))

            val session = requireNotNull(fixture.coaching.active)
            assertEquals(1, fixture.coaching.sessions.size)
            assertEquals(InterventionType.WALK, session.type)
            assertEquals(inputs.recommendation.id, session.recommendationId)
            assertEquals(CoachReason.PROLONGED_INACTIVITY, session.recommendationReason)
            assertEquals(
                InactivityConfirmationPolicy.ALGORITHM_VERSION,
                session.recommendationAlgorithmVersion,
            )
            assertEquals(
                inputs.recommendation.createdAtEpochMillis,
                session.recommendationCreatedAtEpochMillis,
            )
            assertEquals(
                inputs.recommendation.validUntilEpochMillis,
                session.recommendationValidUntilEpochMillis,
            )
            assertEquals(inputs.recommendation.triggerContextId, session.triggerContextId)
            assertEquals(inputs.recommendation.triggerAtEpochMillis, session.triggerAtEpochMillis)
            assertEquals(inputs.glucose.id, session.baselineGlucoseReadingId)
            assertEquals(inputs.glucose.sourceId, session.baselineGlucoseSourceId)
        }

    @Test
    fun `authenticated inactivity replay remains applied after dynamic context changes`() =
        runTest {
            val inputs = inactivityInputs()
            val fixture = fixture(
                readings = listOf(inputs.glucose),
                activity = inputs.activity,
                recommendations = listOf(inputs.recommendation),
                settings = inputs.settings,
                now = inputs.now,
            )
            val command = commandFor(
                action = inputs.recommendation,
                id = "late-inactivity-replay",
                createdAtEpochMillis = inputs.now,
            )

            assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(command))

            fixture.time.now = inputs.recommendation.validUntilEpochMillis + 1L
            fixture.settings.set(
                inputs.settings.copy(
                    walkingRemindersEnabled = false,
                ),
            )
            fixture.activity.set(
                inputs.activity.copy(
                    lastMovementAtEpochMillis = fixture.time.now,
                    measuredAtEpochMillis = fixture.time.now,
                ),
            )
            fixture.glucose.set(
                listOf(
                    inputs.glucose.copy(
                        id = "unsafe-late-reading",
                        valueMgDl = inputs.settings.lowGlucoseThresholdMgDl - 1,
                        measuredAtEpochMillis = fixture.time.now,
                        receivedAtEpochMillis = fixture.time.now,
                    ),
                ),
            )

            assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(command))
            assertEquals(1, fixture.coaching.sessions.size)
            assertEquals("late-inactivity-replay", fixture.coaching.active?.id)
        }

    @Test
    fun `inactivity start uses a safe current glucose reading at processing time`() = runTest {
        val inputs = inactivityInputs()
        val currentGlucose = inputs.glucose.copy(
            id = "current-inactivity-safety-reading",
            valueMgDl = inputs.settings.lowGlucoseThresholdMgDl + 50,
            measuredAtEpochMillis = inputs.now,
            receivedAtEpochMillis = inputs.now,
        )
        val fixture = fixture(
            readings = listOf(inputs.glucose, currentGlucose),
            activity = inputs.activity,
            recommendations = listOf(inputs.recommendation),
            settings = inputs.settings,
            now = inputs.now,
        )

        assertEquals(
            CommandHandlingResult.Applied,
            fixture.handler.handle(
                commandFor(
                    action = inputs.recommendation,
                    id = "safe-current-inactivity",
                    createdAtEpochMillis = inputs.now,
                ),
            ),
        )
        assertEquals(currentGlucose.id, fixture.coaching.active?.baselineGlucoseReadingId)
    }

    @Test
    fun `inactivity start rejects unsafe same-source current glucose at processing time`() =
        runTest {
            val inputs = inactivityInputs()
            val invalidCurrentReadings = listOf(
                "low" to inputs.glucose.copy(
                    id = "current-low",
                    valueMgDl = inputs.settings.lowGlucoseThresholdMgDl - 1,
                    measuredAtEpochMillis = inputs.now,
                    receivedAtEpochMillis = inputs.now,
                ),
                "falling" to inputs.glucose.copy(
                    id = "current-falling",
                    rateMgDlPerMinute = -inputs.settings.exercisePauseFallRateMgDlPerMinute,
                    trend = GlucoseTrend.RAPIDLY_FALLING,
                    measuredAtEpochMillis = inputs.now,
                    receivedAtEpochMillis = inputs.now,
                ),
                "stale" to inputs.glucose.copy(
                    id = "current-stale",
                    measuredAtEpochMillis =
                        inputs.now - inputs.settings.staleReadingMinutes * 60_000L,
                    receivedAtEpochMillis = inputs.now,
                ),
                "future" to inputs.glucose.copy(
                    id = "current-future",
                    measuredAtEpochMillis = inputs.now + 1L,
                    receivedAtEpochMillis = inputs.now,
                ),
            )

            invalidCurrentReadings.forEach { (label, currentGlucose) ->
                val fixture = fixture(
                    readings = listOf(inputs.glucose, currentGlucose),
                    activity = inputs.activity,
                    recommendations = listOf(inputs.recommendation),
                    settings = inputs.settings,
                    now = inputs.now,
                )

                assertEquals(
                    label,
                    CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_UNSAFE),
                    fixture.handler.handle(
                        commandFor(
                            action = inputs.recommendation,
                            id = "unsafe-current-$label",
                            createdAtEpochMillis = inputs.now,
                        ),
                    ),
                )
                assertNull(label, fixture.coaching.active)
                assertEquals(label, 0, fixture.coaching.sessions.size)
            }
        }

    @Test
    fun `inactivity start rejects missing current glucose without a session`() = runTest {
        val inputs = inactivityInputs()
        val fixture = fixture(
            activity = inputs.activity,
            recommendations = listOf(inputs.recommendation),
            settings = inputs.settings,
            now = inputs.now,
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            fixture.handler.handle(
                commandFor(
                    action = inputs.recommendation,
                    id = "missing-current-glucose",
                    createdAtEpochMillis = inputs.now,
                ),
            ),
        )
        assertNull(fixture.coaching.active)
        assertEquals(0, fixture.coaching.sessions.size)
    }

    @Test
    fun `inactivity start rejects invalid current activity context`() = runTest {
        val inputs = inactivityInputs()
        val staleAt =
            inputs.now - inputs.settings.staleReadingMinutes * 60_000L
        val invalidActivities = listOf(
            "missing" to null,
            "missing movement" to inputs.activity.copy(lastMovementAtEpochMillis = null),
            "blank source" to inputs.activity.copy(sourceId = "   "),
            "stale" to inputs.activity.copy(measuredAtEpochMillis = staleAt),
            "future" to inputs.activity.copy(measuredAtEpochMillis = inputs.now + 1L),
            "inconsistent" to inputs.activity.copy(
                lastMovementAtEpochMillis = inputs.now - 60 * 60_000L,
                measuredAtEpochMillis = inputs.now - 61 * 60_000L,
            ),
            "new movement" to inputs.activity.copy(
                lastMovementAtEpochMillis = inputs.now - 60_000L,
                measuredAtEpochMillis = inputs.now,
            ),
            "source change" to inputs.activity.copy(sourceId = "health-connect:other-device"),
        )

        invalidActivities.forEach { (label, activity) ->
            val fixture = fixture(
                readings = listOf(inputs.glucose),
                activity = activity,
                recommendations = listOf(inputs.recommendation),
                settings = inputs.settings,
                now = inputs.now,
            )

            assertEquals(
                label,
                CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
                fixture.handler.handle(
                    commandFor(
                        action = inputs.recommendation,
                        id = "invalid-$label",
                        createdAtEpochMillis = inputs.now,
                    ),
                ),
            )
            assertNull(label, fixture.coaching.active)
            assertEquals(label, 0, fixture.coaching.sessions.size)
        }
    }

    @Test
    fun `inactivity start rejects changed threshold reminders and working hours`() = runTest {
        val inputs = inactivityInputs()
        val minuteOfDay = localMinuteOfDay(inputs.now)
        val invalidSettings = listOf(
            "threshold" to inputs.settings.copy(
                prolongedInactivityMinutes = inputs.settings.prolongedInactivityMinutes + 1,
            ),
            "walking disabled" to inputs.settings.copy(walkingRemindersEnabled = false),
            "outside working hours" to inputs.settings.copy(
                workingHoursStartMinuteOfDay = (minuteOfDay + 1) % MINUTES_PER_DAY,
                workingHoursEndMinuteOfDay = (minuteOfDay + 2) % MINUTES_PER_DAY,
            ),
        )

        invalidSettings.forEach { (label, settings) ->
            val fixture = fixture(
                readings = listOf(inputs.glucose),
                activity = inputs.activity,
                recommendations = listOf(inputs.recommendation),
                settings = settings,
                now = inputs.now,
            )

            assertEquals(
                label,
                CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
                fixture.handler.handle(
                    commandFor(
                        action = inputs.recommendation,
                        id = "invalid-$label",
                        createdAtEpochMillis = inputs.now,
                    ),
                ),
            )
            assertNull(label, fixture.coaching.active)
            assertEquals(label, 0, fixture.coaching.sessions.size)
        }
    }

    @Test
    fun `inactivity start expired at processing time is rejected as conflict`() = runTest {
        val inputs = inactivityInputs()
        val fixture = fixture(
            readings = listOf(inputs.glucose),
            activity = inputs.activity,
            recommendations = listOf(inputs.recommendation),
            settings = inputs.settings,
            now = inputs.recommendation.validUntilEpochMillis,
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            fixture.handler.handle(
                commandFor(
                    action = inputs.recommendation,
                    id = "expired-inactivity",
                    createdAtEpochMillis = inputs.recommendation.createdAtEpochMillis,
                ),
            ),
        )
        assertNull(fixture.coaching.active)
        assertEquals(0, fixture.coaching.sessions.size)
    }

    @Test
    fun `inactivity walk recommendation cannot be forged into stairs`() = runTest {
        val inputs = inactivityInputs()
        val forgedRecommendation = inputs.recommendation.copy(
            interventionType = InterventionType.STAIRS,
            durationMinutes = null,
            targetFloors = inputs.settings.stairTargetFloors,
        )
        val fixture = fixture(
            readings = listOf(inputs.glucose),
            activity = inputs.activity,
            recommendations = listOf(forgedRecommendation),
            settings = inputs.settings,
            now = inputs.now,
        )
        val forged = commandFor(
            action = forgedRecommendation,
            id = "forged-stairs",
            createdAtEpochMillis = inputs.now,
        ).copy(type = QuickActionType.START_STAIRS)

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            fixture.handler.handle(forged),
        )
        assertNull(fixture.coaching.active)
        assertEquals(0, fixture.coaching.sessions.size)
    }

    @Test
    fun `inactivity start rejects forged identity trigger and algorithm`() = runTest {
        val inputs = inactivityInputs()
        val invalidRecommendations = listOf(
            "identity" to inputs.recommendation.copy(id = "forged-inactivity-id"),
            "trigger" to inputs.recommendation.copy(triggerContextId = "forged-trigger"),
            "algorithm" to inputs.recommendation.copy(
                algorithmVersion = InactivityConfirmationPolicy.ALGORITHM_VERSION - 1,
            ),
        )

        invalidRecommendations.forEach { (label, recommendation) ->
            val fixture = fixture(
                readings = listOf(inputs.glucose),
                activity = inputs.activity,
                recommendations = listOf(recommendation),
                settings = inputs.settings,
                now = inputs.now,
            )

            assertEquals(
                label,
                CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
                fixture.handler.handle(
                    commandFor(
                        action = recommendation,
                        id = "forged-$label",
                        createdAtEpochMillis = inputs.now,
                    ),
                ),
            )
            assertNull(label, fixture.coaching.active)
            assertEquals(label, 0, fixture.coaching.sessions.size)
        }
    }

    @Test
    fun `duplicate start is idempotent and conflicting start is rejected`() = runTest {
        val fixture = fixture()
        val first = command(id = "session-a")
        val conflicting = command(id = "session-b")

        assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(first))
        assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(first))
        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            fixture.handler.handle(conflicting),
        )
        assertEquals("session-a", fixture.coaching.active?.id)
    }

    @Test
    fun `completion before start defers then converges after start`() = runTest {
        val fixture = fixture()
        val completion = QuickActionCommand(
            id = "complete",
            type = QuickActionType.MARK_COMPLETED,
            createdAtEpochMillis = NOW,
            sessionId = "session",
        )

        assertEquals(CommandHandlingResult.Deferred, fixture.handler.handle(completion))
        assertEquals(
            CommandHandlingResult.Applied,
            fixture.handler.handle(command(id = "session")),
        )
        assertEquals(CommandHandlingResult.Applied, fixture.handler.handle(completion))
        assertEquals(InterventionStatus.COMPLETED, fixture.coaching.sessions["session"]?.status)
    }

    @Test
    fun `offline completion remains valid for an existing active session after command expiry`() =
        runTest {
            val fixture = fixture()
            val startedAt = NOW - 2 * DAY_MILLIS
            val completedAt =
                NOW - DefaultCoachSettings.create().quickActionExpiryMinutes * 60_000L - 1
            fixture.coaching.startSession(
                InterventionSession(
                    id = "offline-session",
                    type = InterventionType.WALK,
                    status = InterventionStatus.STARTED,
                    startedAtEpochMillis = startedAt,
                    endedAtEpochMillis = null,
                    targetDurationMinutes = 10,
                    targetFloors = null,
                    baselineGlucoseMgDl = 140,
                    glucoseAfterMgDl = null,
                ),
            )

            val result = fixture.handler.handle(
                QuickActionCommand(
                    id = "offline-completion",
                    type = QuickActionType.MARK_COMPLETED,
                    createdAtEpochMillis = completedAt,
                    sessionId = "offline-session",
                ),
            )

            assertEquals(CommandHandlingResult.Applied, result)
            assertEquals(
                completedAt,
                fixture.coaching.sessions["offline-session"]?.endedAtEpochMillis,
            )
        }

    @Test
    fun `expired orphan completion is terminal instead of deferred forever`() = runTest {
        val fixture = fixture()

        val result = fixture.handler.handle(
            QuickActionCommand(
                id = "orphan-completion",
                type = QuickActionType.MARK_COMPLETED,
                createdAtEpochMillis =
                    NOW - DefaultCoachSettings.create().quickActionExpiryMinutes * 60_000L - 1,
                sessionId = "missing-session",
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED),
            result,
        )
    }

    private fun fixture(
        readings: List<GlucoseReading> = emptyList(),
        activity: ActivitySnapshot? = null,
        recommendations: List<CoachRecommendation.Action> = emptyList(),
        settings: CoachSettings = DefaultCoachSettings.create(),
        now: Long = NOW,
    ): Fixture {
        val coaching = FakeCoachingRepository(recommendations)
        val glucose = FakeGlucoseRepository(readings)
        val activities = FakeActivityRepository(activity)
        val coachSettings = FakeSettingsRepository(settings)
        val time = FakeTimeSource(now)
        val scheduler = Mockito.mock(InterventionFollowUpScheduler::class.java)
        return Fixture(
            coaching = coaching,
            glucose = glucose,
            activity = activities,
            settings = coachSettings,
            time = time,
            handler = QuickActionHandler(
                coachingRepository = coaching,
                glucoseRepository = glucose,
                activityRepository = activities,
                settingsRepository = coachSettings,
                followUpScheduler = scheduler,
                timeSource = time,
            ),
        )
    }

    private fun command(
        id: String = "session",
        recommendationId: String? = null,
        recommendationValidUntilEpochMillis: Long? = null,
        includeTimingProvenance: Boolean = false,
        createdAtEpochMillis: Long = NOW,
        recommendationCreatedAtEpochMillis: Long = createdAtEpochMillis - 1_000,
        reason: CoachReason = CoachReason.RAPID_GLUCOSE_RISE,
    ) = QuickActionCommand(
        id = id,
        type = QuickActionType.START_WALK,
        createdAtEpochMillis = createdAtEpochMillis,
        sessionId = id,
        recommendationId = recommendationId,
        recommendationValidUntilEpochMillis = recommendationValidUntilEpochMillis,
        recommendationReason =
            reason.takeIf { includeTimingProvenance },
        recommendationAlgorithmVersion = 1.takeIf { includeTimingProvenance },
        recommendationCreatedAtEpochMillis =
            recommendationCreatedAtEpochMillis.takeIf { recommendationId != null },
        triggerContextId = "reading-trigger".takeIf { includeTimingProvenance },
        triggerAtEpochMillis =
            (createdAtEpochMillis - 2_000).takeIf { includeTimingProvenance },
        glucoseSourceId = "health-connect:caresens".takeIf { includeTimingProvenance },
        safetyReadingId = "published-reading".takeIf { includeTimingProvenance },
        safetyReadingAtEpochMillis =
            (createdAtEpochMillis - 1_000).takeIf { includeTimingProvenance },
    )

    private fun recommendation(
        id: String,
        createdAtEpochMillis: Long = NOW - 1_000,
        validUntilEpochMillis: Long,
        includeTimingProvenance: Boolean = false,
        durationMinutes: Int = 10,
        triggerAtEpochMillis: Long = createdAtEpochMillis - 1_000,
        safetyReadingAtEpochMillis: Long = createdAtEpochMillis,
        reason: CoachReason = CoachReason.RAPID_GLUCOSE_RISE,
    ) = CoachRecommendation.Action(
        reason = reason,
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        validUntilEpochMillis = validUntilEpochMillis,
        interventionType = InterventionType.WALK,
        title = "Walk now?",
        actionLabel = "Start walk",
        durationMinutes = durationMinutes,
        targetFloors = null,
        algorithmVersion = 1,
        triggerContextId = "reading-trigger".takeIf { includeTimingProvenance },
        triggerAtEpochMillis = triggerAtEpochMillis.takeIf { includeTimingProvenance },
        glucoseSourceId = "health-connect:caresens".takeIf { includeTimingProvenance },
        safetyReadingId = "published-reading".takeIf { includeTimingProvenance },
        safetyReadingAtEpochMillis =
            safetyReadingAtEpochMillis.takeIf { includeTimingProvenance },
    )

    private fun reading(id: String, sourceId: String, measuredAt: Long) = GlucoseReading(
        id = id,
        valueMgDl = 140,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = measuredAt,
        receivedAtEpochMillis = measuredAt,
        sourceId = sourceId,
    )

    private fun risingReading(id: String, measuredAt: Long) =
        reading(id, "health-connect:caresens", measuredAt).copy(
            deltaMgDl = 10,
            rateMgDlPerMinute = 3.0,
            trend = GlucoseTrend.RAPIDLY_RISING,
        )

    private fun rapidRecommendation(
        confirmation: com.young.metaboliccoach.core.domain.RapidRiseConfirmation,
    ) = CoachRecommendation.Action(
        reason = CoachReason.RAPID_GLUCOSE_RISE,
        id = confirmation.recommendationId,
        createdAtEpochMillis = NOW - 2_000L,
        validUntilEpochMillis = NOW + 60_000L,
        interventionType = InterventionType.WALK,
        title = "Glucose is rising. Walk now?",
        actionLabel = "START WALK",
        durationMinutes = 10,
        targetFloors = null,
        algorithmVersion = RapidRiseConfirmationPolicy.ALGORITHM_VERSION,
        triggerContextId = confirmation.triggerIdentity,
        triggerAtEpochMillis = confirmation.latestReading.measuredAtEpochMillis,
        glucoseSourceId = confirmation.latestReading.sourceId,
        safetyReadingId = confirmation.latestReading.id,
        safetyReadingAtEpochMillis = confirmation.latestReading.measuredAtEpochMillis,
    )

    private fun commandFor(
        action: CoachRecommendation.Action,
        id: String = "rapid-session",
        createdAtEpochMillis: Long = NOW,
    ) = QuickActionCommand(
        id = id,
        type = QuickActionType.START_WALK,
        createdAtEpochMillis = createdAtEpochMillis,
        sessionId = id,
        recommendationId = action.id,
        recommendationValidUntilEpochMillis = action.validUntilEpochMillis,
        recommendationReason = action.reason,
        recommendationAlgorithmVersion = action.algorithmVersion,
        recommendationCreatedAtEpochMillis = action.createdAtEpochMillis,
        triggerContextId = action.triggerContextId,
        triggerAtEpochMillis = action.triggerAtEpochMillis,
        glucoseSourceId = action.glucoseSourceId,
        safetyReadingId = action.safetyReadingId,
        safetyReadingAtEpochMillis = action.safetyReadingAtEpochMillis,
    )

    private fun inactivityInputs(): InactivityInputs {
        val now = INACTIVITY_NOW
        val settings = DefaultCoachSettings.create().copy(
            stairRemindersEnabled = false,
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
            workingHoursStartMinuteOfDay = 0,
            workingHoursEndMinuteOfDay = 0,
        )
        val glucose = reading(
            id = "inactivity-safety-reading",
            sourceId = "health-connect:caresens",
            measuredAt = now - 1_000L,
        )
        val activity = ActivitySnapshot(
            stepsToday = 2_000,
            floorsToday = 1.0,
            latestHeartRateBpm = 70,
            activeCaloriesToday = 100.0,
            lastMovementAtEpochMillis =
                now - settings.prolongedInactivityMinutes * 60_000L,
            measuredAtEpochMillis = now - 1_000L,
            sourceId = "health-connect:activity-device",
        )
        val zoneId = ZoneId.systemDefault()
        val confirmation = requireNotNull(
            InactivityConfirmationPolicy.confirm(
                activity = activity,
                settings = settings,
                nowEpochMillis = now,
                minuteOfDay = localMinuteOfDay(now),
                zoneId = zoneId,
            ),
        )
        val recommendation = CoachRecommendation.Action(
            reason = CoachReason.PROLONGED_INACTIVITY,
            id = confirmation.recommendationId,
            createdAtEpochMillis = now,
            validUntilEpochMillis = minOf(
                glucose.measuredAtEpochMillis + settings.staleReadingMinutes * 60_000L,
                confirmation.activityFreshUntilEpochMillis,
            ),
            interventionType = InterventionType.WALK,
            title = "You've been inactive. Take a short walk?",
            actionLabel = "START WALK",
            durationMinutes = settings.walkingDurationMinutes,
            targetFloors = null,
            algorithmVersion = InactivityConfirmationPolicy.ALGORITHM_VERSION,
            triggerContextId = confirmation.triggerIdentity,
            triggerAtEpochMillis = confirmation.thresholdCrossingAtEpochMillis,
            glucoseSourceId = glucose.sourceId,
            safetyReadingId = glucose.id,
            safetyReadingAtEpochMillis = glucose.measuredAtEpochMillis,
        )
        return InactivityInputs(
            now = now,
            settings = settings,
            glucose = glucose,
            activity = activity,
            recommendation = recommendation,
        )
    }

    private fun localMinuteOfDay(nowEpochMillis: Long): Int =
        Instant.ofEpochMilli(nowEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .toSecondOfDay() / 60

    private data class Fixture(
        val coaching: FakeCoachingRepository,
        val glucose: FakeGlucoseRepository,
        val activity: FakeActivityRepository,
        val settings: FakeSettingsRepository,
        val time: FakeTimeSource,
        val handler: QuickActionHandler,
    )

    private data class InactivityInputs(
        val now: Long,
        val settings: CoachSettings,
        val glucose: GlucoseReading,
        val activity: ActivitySnapshot,
        val recommendation: CoachRecommendation.Action,
    )

    private class FakeTimeSource(
        var now: Long,
    ) : CoachTimeSource {
        override fun nowEpochMillis(): Long = now
        override fun minuteTicks(): Flow<Long> = flowOf(now)
    }

    private class FakeSettingsRepository(
        settings: CoachSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(settings)

        fun set(settings: CoachSettings) {
            state.value = settings
        }

        override fun observe(): Flow<CoachSettings> = state
        override suspend fun update(settings: CoachSettings) = set(settings)
        override suspend fun reset() = set(DefaultCoachSettings.create())
    }

    private class FakeActivityRepository(
        activity: ActivitySnapshot?,
    ) : ActivityRepository {
        private val state = MutableStateFlow(activity)

        fun set(activity: ActivitySnapshot?) {
            state.value = activity
        }

        override fun observeToday(): Flow<ActivitySnapshot?> = state
        override suspend fun refresh() = Unit
    }

    private class FakeGlucoseRepository(
        readings: List<GlucoseReading>,
    ) : GlucoseRepository {
        private val state = MutableStateFlow(readings)

        fun set(readings: List<GlucoseReading>) {
            state.value = readings
        }

        override fun observeLatest(): Flow<GlucoseReading?> =
            state.map { it.lastOrNull() }
        override fun observeProviderStatus(): Flow<ProviderStatus> = flowOf(
            ProviderStatus("fake", "Fake", ProviderAvailability.AVAILABLE, "Ready"),
        )
        override fun observeAvailableOrigins(): Flow<List<GlucoseDataOrigin>> =
            flowOf(emptyList())

        override suspend fun readingsBetween(
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReading> = state.value.filter {
            it.measuredAtEpochMillis in startEpochMillis..endEpochMillis
        }

        override suspend fun readingsBetweenExactSource(
            sourceId: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReading> = readingsBetween(startEpochMillis, endEpochMillis)
            .filter { it.sourceId == sourceId }

        override suspend fun refresh() = Unit
        override suspend fun refreshExactSource(sourceId: String) = Unit
        override suspend fun clearRuntimeCaches() = Unit
    }

    private class FakeCoachingRepository(
        initialRecommendations: List<CoachRecommendation.Action>,
    ) : CoachingRepository {
        val sessions = linkedMapOf<String, InterventionSession>()
        val recommendations =
            initialRecommendations.associateByTo(linkedMapOf(), CoachRecommendation.Action::id)
        var active: InterventionSession? = null
        private val consumed = mutableSetOf<String>()

        override fun observeCurrentRecommendation(): Flow<CoachRecommendation?> = flowOf(null)
        override fun observeTodaySummary(): Flow<DailySummary> = flowOf(
            DailySummary(0, null, 0, 0, 0, 0.0),
        )

        override fun observePersonalObservations(): Flow<List<PersonalObservation>> =
            flowOf(emptyList())

        override fun observeActiveSession(): Flow<InterventionSession?> = flowOf(active)
        override suspend fun saveMealMarker(marker: MealMarker) = Unit
        override suspend fun latestMealMarker(): MealMarker? = null

        override suspend fun startSession(session: InterventionSession): InterventionSession {
            sessions[session.id]?.let { return it }
            active?.let { return it }
            sessions[session.id] = session
            active = session
            return session
        }

        override suspend fun startSessionForRecommendation(
            session: InterventionSession,
            recommendationId: String,
            nowEpochMillis: Long,
        ): InterventionSession? {
            sessionForRecommendation(recommendationId)?.let { return it }
            active?.let { return it }
            if (!consumed.add(recommendationId)) return null
            sessions[session.id] = session
            active = session
            return session
        }

        override suspend fun completeSession(
            sessionId: String,
            endedAtEpochMillis: Long,
            followUpDueAtEpochMillis: Long,
        ): InterventionSession? {
            val session = sessions[sessionId] ?: return null
            val completed = session.copy(
                status = InterventionStatus.COMPLETED,
                endedAtEpochMillis = endedAtEpochMillis,
                followUpDueAtEpochMillis = followUpDueAtEpochMillis,
            )
            sessions[sessionId] = completed
            active = null
            return completed
        }

        override suspend fun session(sessionId: String): InterventionSession? =
            sessions[sessionId]

        override suspend fun sessionForRecommendation(
            recommendationId: String,
        ): InterventionSession? = sessions.values.firstOrNull {
            it.recommendationId == recommendationId
        }

        override suspend fun latestActiveSession(): InterventionSession? = active
        override suspend fun pendingFollowUpSessions(): List<InterventionSession> = emptyList()

        override suspend fun finalizeFollowUp(
            sessionId: String,
            glucoseMgDl: Int?,
            readingAtEpochMillis: Long?,
            readingId: String?,
            sourceId: String?,
            finalizedAtEpochMillis: Long,
        ): Boolean = false

        override suspend fun snooze(nowEpochMillis: Long) = Unit

        override suspend fun rememberRecommendation(
            recommendation: CoachRecommendation.Action,
        ): CoachRecommendation.Action =
            recommendations.getOrPut(recommendation.id) { recommendation }

        override suspend fun recommendationSnapshot(
            recommendationId: String,
        ): CoachRecommendation.Action? = recommendations[recommendationId]

        override suspend fun publishedRecommendationSnapshot(
            recommendationId: String,
            nowEpochMillis: Long,
        ): CoachRecommendation.Action? = recommendations[recommendationId]

        override suspend fun recordRecommendationPublished(
            recommendationId: String,
            nowEpochMillis: Long,
        ): Boolean = true

    }

    private companion object {
        const val NOW = 43_200_000L
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val MINUTES_PER_DAY = 24 * 60
        val INACTIVITY_NOW: Long = Instant.parse("2026-01-15T14:00:00Z").toEpochMilli()
    }
}
