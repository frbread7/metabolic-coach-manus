package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.InterventionType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionDisplayDeadlinePolicyTest {
    @Test
    fun `ordinary quiet-hours start bounds every action`() {
        val now = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 13 * 60,
            quietHoursEndMinuteOfDay = 14 * 60,
        )

        assertEquals(
            Instant.parse("2026-01-15T13:00:00Z").toEpochMilli(),
            deadline(action(CoachReason.POST_MEAL_WINDOW, now), settings, now),
        )
    }

    @Test
    fun `overnight quiet-hours start uses the same local day while eligible`() {
        val now = Instant.parse("2026-01-15T21:00:00Z").toEpochMilli()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 22 * 60,
            quietHoursEndMinuteOfDay = 7 * 60,
        )

        assertEquals(
            Instant.parse("2026-01-15T22:00:00Z").toEpochMilli(),
            deadline(action(CoachReason.RAPID_GLUCOSE_RISE, now), settings, now),
        )
    }

    @Test
    fun `inactivity uses earlier working-hours end`() {
        val now = Instant.parse("2026-01-15T17:00:00Z").toEpochMilli()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 22 * 60,
            quietHoursEndMinuteOfDay = 7 * 60,
            workingHoursStartMinuteOfDay = 8 * 60,
            workingHoursEndMinuteOfDay = 18 * 60,
        )

        assertEquals(
            Instant.parse("2026-01-15T18:00:00Z").toEpochMilli(),
            deadline(action(CoachReason.PROLONGED_INACTIVITY, now), settings, now),
        )
    }

    @Test
    fun `overnight working-hours end uses the next local day`() {
        val now = Instant.parse("2026-01-15T23:00:00Z").toEpochMilli()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
            workingHoursStartMinuteOfDay = 22 * 60,
            workingHoursEndMinuteOfDay = 6 * 60,
        )

        assertEquals(
            Instant.parse("2026-01-16T06:00:00Z").toEpochMilli(),
            deadline(action(CoachReason.PROLONGED_INACTIVITY, now), settings, now),
        )
    }

    @Test
    fun `equal start and end disable quiet hours and make working hours all day`() {
        val now = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        val validUntil = now + 6 * 60 * 60_000L
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
            workingHoursStartMinuteOfDay = 0,
            workingHoursEndMinuteOfDay = 0,
        )

        assertEquals(
            validUntil,
            deadline(
                action(CoachReason.PROLONGED_INACTIVITY, now, validUntil),
                settings,
                now,
            ),
        )
    }

    @Test
    fun `authoritative validity wins when earlier than time boundaries`() {
        val now = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        val validUntil = now + 10 * 60_000L
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 13 * 60,
            quietHoursEndMinuteOfDay = 14 * 60,
        )

        assertEquals(
            validUntil,
            deadline(action(CoachReason.POST_MEAL_WINDOW, now, validUntil), settings, now),
        )
    }

    @Test
    fun `exact quiet start and exact working end fail closed`() {
        val quietStart = Instant.parse("2026-01-15T22:00:00Z").toEpochMilli()
        val workingEnd = Instant.parse("2026-01-15T18:00:00Z").toEpochMilli()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 22 * 60,
            quietHoursEndMinuteOfDay = 7 * 60,
            workingHoursStartMinuteOfDay = 8 * 60,
            workingHoursEndMinuteOfDay = 18 * 60,
        )

        assertEquals(
            quietStart,
            deadline(action(CoachReason.POST_MEAL_WINDOW, quietStart), settings, quietStart),
        )
        assertEquals(
            workingEnd,
            deadline(action(CoachReason.PROLONGED_INACTIVITY, workingEnd), settings, workingEnd),
        )
    }

    @Test
    fun `non-inactivity actions ignore working-hours boundary`() {
        val now = Instant.parse("2026-01-15T17:00:00Z").toEpochMilli()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
            workingHoursStartMinuteOfDay = 8 * 60,
            workingHoursEndMinuteOfDay = 18 * 60,
        )
        val validUntil = now + 3 * 60 * 60_000L

        assertEquals(
            validUntil,
            deadline(action(CoachReason.POST_MEAL_WINDOW, now, validUntil), settings, now),
        )
    }

    @Test
    fun `ambiguous or missing DST boundary fails closed`() {
        val zone = ZoneId.of("America/New_York")
        val springNow = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val fallNow = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            springNow,
            deadline(
                action(CoachReason.POST_MEAL_WINDOW, springNow),
                DefaultCoachSettings.create().copy(
                    quietHoursStartMinuteOfDay = 2 * 60 + 30,
                    quietHoursEndMinuteOfDay = 3 * 60 + 30,
                ),
                springNow,
                zone,
            ),
        )
        assertEquals(
            fallNow,
            deadline(
                action(CoachReason.POST_MEAL_WINDOW, fallNow),
                DefaultCoachSettings.create().copy(
                    quietHoursStartMinuteOfDay = 1 * 60 + 30,
                    quietHoursEndMinuteOfDay = 2 * 60 + 30,
                ),
                fallNow,
                zone,
            ),
        )
    }

    private fun deadline(
        action: CoachRecommendation.Action,
        settings: com.young.metaboliccoach.core.model.CoachSettings,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.of("UTC"),
    ): Long = ActionDisplayDeadlinePolicy.displayUntilEpochMillis(
        recommendation = action,
        settings = settings,
        nowEpochMillis = nowEpochMillis,
        zoneId = zoneId,
    )

    private fun action(
        reason: CoachReason,
        nowEpochMillis: Long,
        validUntilEpochMillis: Long = nowEpochMillis + 24 * 60 * 60_000L,
    ) = CoachRecommendation.Action(
        reason = reason,
        id = "action",
        createdAtEpochMillis = nowEpochMillis,
        validUntilEpochMillis = validUntilEpochMillis,
        interventionType = InterventionType.WALK,
        title = "Walk?",
        actionLabel = "START WALK",
        durationMinutes = 10,
        targetFloors = null,
    )
}
