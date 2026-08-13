package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Computes the conservative end of an already-eligible action's visible lifetime.
 *
 * This does not rewrite the authoritative recommendation validity or provenance. A return value
 * equal to [nowEpochMillis] means the display boundary could not be proven safely.
 */
object ActionDisplayDeadlinePolicy {
    fun displayUntilEpochMillis(
        recommendation: CoachRecommendation.Action,
        settings: CoachSettings,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (recommendation.validUntilEpochMillis <= nowEpochMillis) return nowEpochMillis
        if (!validMinute(settings.quietHoursStartMinuteOfDay) ||
            !validMinute(settings.quietHoursEndMinuteOfDay)
        ) {
            return nowEpochMillis
        }
        val now = try {
            Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        } catch (_: DateTimeException) {
            return nowEpochMillis
        }
        val minuteOfDay = now.toLocalTime().toSecondOfDay() / 60
        var deadline = recommendation.validUntilEpochMillis

        val quietStart = settings.quietHoursStartMinuteOfDay
        val quietEnd = settings.quietHoursEndMinuteOfDay
        if (quietStart != quietEnd) {
            if (isInTimeRange(minuteOfDay, quietStart, quietEnd)) return nowEpochMillis
            val quietStartAt = uniqueBoundaryEpochMillis(
                date = if (minuteOfDay < quietStart) now.toLocalDate() else {
                    nextDate(now.toLocalDate()) ?: return nowEpochMillis
                },
                minuteOfDay = quietStart,
                zoneId = zoneId,
            ) ?: return nowEpochMillis
            if (quietStartAt <= nowEpochMillis) return nowEpochMillis
            deadline = minOf(deadline, quietStartAt)
        }

        if (recommendation.reason == CoachReason.PROLONGED_INACTIVITY) {
            val workingStart = settings.workingHoursStartMinuteOfDay
            val workingEnd = settings.workingHoursEndMinuteOfDay
            if (!validMinute(workingStart) || !validMinute(workingEnd)) {
                return nowEpochMillis
            }
            if (workingStart != workingEnd) {
                if (!isInTimeRange(minuteOfDay, workingStart, workingEnd)) {
                    return nowEpochMillis
                }
                val workingEndDate = when {
                    workingStart < workingEnd -> now.toLocalDate()
                    minuteOfDay >= workingStart ->
                        nextDate(now.toLocalDate()) ?: return nowEpochMillis
                    else -> now.toLocalDate()
                }
                val workingEndAt = uniqueBoundaryEpochMillis(
                    date = workingEndDate,
                    minuteOfDay = workingEnd,
                    zoneId = zoneId,
                ) ?: return nowEpochMillis
                if (workingEndAt <= nowEpochMillis) return nowEpochMillis
                deadline = minOf(deadline, workingEndAt)
            }
        }
        return deadline
    }

    private fun uniqueBoundaryEpochMillis(
        date: LocalDate,
        minuteOfDay: Int,
        zoneId: ZoneId,
    ): Long? {
        val localDateTime = try {
            LocalDateTime.of(
                date,
                LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR),
            )
        } catch (_: DateTimeException) {
            return null
        }
        val offsets = zoneId.rules.getValidOffsets(localDateTime)
        if (offsets.size != 1) return null
        return try {
            localDateTime.toInstant(offsets.single()).toEpochMilli()
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun nextDate(date: LocalDate): LocalDate? = try {
        date.plusDays(1)
    } catch (_: DateTimeException) {
        null
    }

    private fun validMinute(value: Int): Boolean = value in 0 until MINUTES_PER_DAY

    private fun isInTimeRange(minuteOfDay: Int, start: Int, end: Int): Boolean =
        if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
}
