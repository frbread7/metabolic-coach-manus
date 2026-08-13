package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.InterventionType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InactivityConfirmationPolicyTest {
    private val zoneId = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-12T12:00:00Z").toEpochMilli()
    private val settings = DefaultCoachSettings.create()

    @Test
    fun `current same-day snapshot confirms exactly at threshold`() {
        val activity = activity(lastMovementAtEpochMillis = now - 90 * MINUTE)
        val confirmation = requireNotNull(confirm(activity))

        assertEquals(now - 30 * MINUTE, confirmation.thresholdCrossingAtEpochMillis)
        assertEquals(now + settings.staleReadingMinutes * MINUTE, confirmation.activityFreshUntilEpochMillis)
        assertEquals(
            "inactivity:v4:22a2e5e918541b553f5eb4fd29f7ea2366269b92f129238b8b0dd2a5aa348cf0",
            confirmation.triggerIdentity,
        )
        assertEquals(
            "PROLONGED_INACTIVITY:v4:22a2e5e918541b553f5eb4fd29f7ea2366269b92f129238b8b0dd2a5aa348cf0",
            confirmation.recommendationId,
        )
        assertTrue(confirmation.recommendationId.length <= 96)

        assertTrue(
            confirm(
                activity(lastMovementAtEpochMillis = now - settings.prolongedInactivityMinutes * MINUTE),
            ) != null,
        )
        assertNull(
            confirm(
                activity(
                    lastMovementAtEpochMillis =
                        now - settings.prolongedInactivityMinutes * MINUTE + 1,
                ),
            ),
        )
    }

    @Test
    fun `missing disabled blank and inconsistent activity fail closed`() {
        val valid = activity()

        assertNull(confirm(null))
        assertNull(confirm(valid, settings.copy(walkingRemindersEnabled = false)))
        assertNull(confirm(valid.copy(sourceId = "   ")))
        assertNull(confirm(valid.copy(lastMovementAtEpochMillis = null)))
        assertNull(confirm(valid.copy(lastMovementAtEpochMillis = now + 1)))
        assertNull(confirm(valid.copy(measuredAtEpochMillis = now + 1)))
        assertNull(
            confirm(
                valid.copy(
                    lastMovementAtEpochMillis = now - MINUTE,
                    measuredAtEpochMillis = now - 2 * MINUTE,
                ),
            ),
        )
        assertNull(confirm(valid, settings.copy(staleReadingMinutes = 0)))
        assertNull(confirm(valid, settings.copy(prolongedInactivityMinutes = 0)))
    }

    @Test
    fun `snapshot and movement must both be fresh current-local-date evidence`() {
        val staleBoundary = activity().copy(
            measuredAtEpochMillis = now - settings.staleReadingMinutes * MINUTE,
        )
        val previousDate = Instant.parse("2026-08-11T23:59:59Z").toEpochMilli()

        assertNull(confirm(staleBoundary))
        assertNull(confirm(activity(lastMovementAtEpochMillis = previousDate)))
        assertNull(
            confirm(
                activity(
                    lastMovementAtEpochMillis = previousDate - MINUTE,
                    measuredAtEpochMillis = previousDate,
                ),
            ),
        )
    }

    @Test
    fun `working hours are start-inclusive end-exclusive and support overnight`() {
        assertTrue(confirm(activity(), minuteOfDay = settings.workingHoursStartMinuteOfDay) != null)
        assertNull(confirm(activity(), minuteOfDay = settings.workingHoursEndMinuteOfDay))
        assertNull(confirm(activity(), minuteOfDay = -1))
        assertNull(confirm(activity(), minuteOfDay = 24 * 60))

        val overnight = settings.copy(
            workingHoursStartMinuteOfDay = 22 * 60,
            workingHoursEndMinuteOfDay = 6 * 60,
        )
        assertTrue(confirm(activity(), overnight, minuteOfDay = 22 * 60) != null)
        assertTrue(confirm(activity(), overnight, minuteOfDay = 5 * 60 + 59) != null)
        assertNull(confirm(activity(), overnight, minuteOfDay = 6 * 60))
        assertNull(confirm(activity(), overnight, minuteOfDay = 12 * 60))

        val allDay = overnight.copy(
            workingHoursEndMinuteOfDay = overnight.workingHoursStartMinuteOfDay,
        )
        assertTrue(confirm(activity(), allDay, minuteOfDay = 12 * 60) != null)
    }

    @Test
    fun `checked timestamp arithmetic fails closed instead of wrapping`() {
        val thresholdOverflowNow = Long.MAX_VALUE - MINUTE
        val thresholdOverflowActivity = activity(
            lastMovementAtEpochMillis = thresholdOverflowNow,
            measuredAtEpochMillis = thresholdOverflowNow,
        )
        val extremeSettings = settings.copy(staleReadingMinutes = 1)

        assertNull(
            InactivityConfirmationPolicy.confirm(
                activity = thresholdOverflowActivity,
                settings = extremeSettings,
                nowEpochMillis = thresholdOverflowNow,
                minuteOfDay = 12 * 60,
                zoneId = zoneId,
            ),
        )

        val freshnessOverflowNow = Long.MAX_VALUE - 1
        assertNull(
            InactivityConfirmationPolicy.confirm(
                activity = activity(
                    lastMovementAtEpochMillis = freshnessOverflowNow,
                    measuredAtEpochMillis = freshnessOverflowNow,
                ),
                settings = extremeSettings,
                nowEpochMillis = freshnessOverflowNow,
                minuteOfDay = 12 * 60,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun `identity excludes snapshot counters and measurement time but binds episode inputs`() {
        val base = activity(lastMovementAtEpochMillis = now - 90 * MINUTE)
        val first = requireNotNull(confirm(base))
        val refreshed = requireNotNull(
            confirm(
                base.copy(
                    stepsToday = 9_999,
                    floorsToday = 42.0,
                    measuredAtEpochMillis = now - MINUTE,
                ),
            ),
        )
        val changedSource = requireNotNull(confirm(base.copy(sourceId = "other-source")))
        val changedMovement = requireNotNull(
            confirm(
                base.copy(
                    lastMovementAtEpochMillis =
                        requireNotNull(base.lastMovementAtEpochMillis) + 1,
                ),
            ),
        )
        val changedThreshold = requireNotNull(
            confirm(base, settings.copy(prolongedInactivityMinutes = 61)),
        )

        assertEquals(first.recommendationId, refreshed.recommendationId)
        assertEquals(first.triggerIdentity, refreshed.triggerIdentity)
        assertNotEquals(first.recommendationId, changedSource.recommendationId)
        assertNotEquals(first.recommendationId, changedMovement.recommendationId)
        assertNotEquals(first.recommendationId, changedThreshold.recommendationId)
    }

    @Test
    fun `matches requires exact v4 walk identity and trigger`() {
        val activity = activity(lastMovementAtEpochMillis = now - 90 * MINUTE)
        val confirmation = requireNotNull(confirm(activity))
        val recommendation = action(confirmation)

        assertTrue(matches(recommendation, activity))
        assertTrue(matches(recommendation.copy(durationMinutes = 99), activity))
        assertFalse(matches(recommendation.copy(reason = CoachReason.POST_MEAL_WINDOW), activity))
        assertFalse(matches(recommendation.copy(interventionType = InterventionType.STAIRS), activity))
        assertFalse(matches(recommendation.copy(targetFloors = 1), activity))
        assertFalse(matches(recommendation.copy(algorithmVersion = 3), activity))
        assertFalse(matches(recommendation.copy(id = "wrong"), activity))
        assertFalse(matches(recommendation.copy(triggerContextId = "wrong"), activity))
        assertFalse(matches(recommendation.copy(triggerAtEpochMillis = now), activity))
        assertFalse(matches(recommendation, activity.copy(sourceId = "other-source")))
        assertFalse(matches(recommendation, null))
    }

    private fun confirm(
        activity: ActivitySnapshot?,
        settings: CoachSettings = this.settings,
        minuteOfDay: Int = 12 * 60,
    ) = InactivityConfirmationPolicy.confirm(
        activity = activity,
        settings = settings,
        nowEpochMillis = now,
        minuteOfDay = minuteOfDay,
        zoneId = zoneId,
    )

    private fun matches(
        recommendation: CoachRecommendation.Action,
        activity: ActivitySnapshot?,
    ) = InactivityConfirmationPolicy.matches(
        recommendation = recommendation,
        activity = activity,
        settings = settings,
        nowEpochMillis = now,
        minuteOfDay = 12 * 60,
        zoneId = zoneId,
    )

    private fun action(confirmation: InactivityConfirmation) = CoachRecommendation.Action(
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
    )

    private fun activity(
        lastMovementAtEpochMillis: Long? = now - 90 * MINUTE,
        measuredAtEpochMillis: Long = now,
    ) = ActivitySnapshot(
        stepsToday = 2_000,
        floorsToday = 1.0,
        latestHeartRateBpm = 70,
        activeCaloriesToday = 100.0,
        lastMovementAtEpochMillis = lastMovementAtEpochMillis,
        measuredAtEpochMillis = measuredAtEpochMillis,
        sourceId = "health-connect",
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
