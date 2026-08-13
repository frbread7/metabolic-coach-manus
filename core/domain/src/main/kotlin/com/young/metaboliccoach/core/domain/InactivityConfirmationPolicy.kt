package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.InterventionType
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

data class InactivityConfirmation(
    val thresholdCrossingAtEpochMillis: Long,
    val activityFreshUntilEpochMillis: Long,
    val triggerIdentity: String,
    val recommendationId: String,
)

/** Confirms that a current, same-day activity snapshot proves prolonged inactivity. */
object InactivityConfirmationPolicy {
    const val ALGORITHM_VERSION = 4

    fun confirm(
        activity: ActivitySnapshot?,
        settings: CoachSettings,
        nowEpochMillis: Long,
        minuteOfDay: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): InactivityConfirmation? {
        activity ?: return null
        if (!settings.walkingRemindersEnabled) return null
        if (activity.sourceId.isBlank()) return null
        val lastMovementAtEpochMillis = activity.lastMovementAtEpochMillis ?: return null
        if (
            lastMovementAtEpochMillis > nowEpochMillis ||
            activity.measuredAtEpochMillis > nowEpochMillis ||
            lastMovementAtEpochMillis > activity.measuredAtEpochMillis
        ) {
            return null
        }
        if (!isCurrentLocalDate(activity, lastMovementAtEpochMillis, nowEpochMillis, zoneId)) {
            return null
        }
        if (!isInWorkingHours(minuteOfDay, settings)) return null

        val activityFreshnessMillis = checkedMultiply(
            settings.staleReadingMinutes.toLong(),
            MILLIS_PER_MINUTE,
        ) ?: return null
        if (activityFreshnessMillis <= 0L) return null
        val activityAgeMillis = checkedSubtract(
            nowEpochMillis,
            activity.measuredAtEpochMillis,
        ) ?: return null
        if (activityAgeMillis >= activityFreshnessMillis) return null
        val activityFreshUntilEpochMillis = checkedAdd(
            activity.measuredAtEpochMillis,
            activityFreshnessMillis,
        ) ?: return null

        val inactivityThresholdMillis = checkedMultiply(
            settings.prolongedInactivityMinutes.toLong(),
            MILLIS_PER_MINUTE,
        ) ?: return null
        if (inactivityThresholdMillis <= 0L) return null
        val thresholdCrossingAtEpochMillis = checkedAdd(
            lastMovementAtEpochMillis,
            inactivityThresholdMillis,
        ) ?: return null
        if (nowEpochMillis < thresholdCrossingAtEpochMillis) return null

        val identityHash = fingerprint(
            CoachReason.PROLONGED_INACTIVITY.name,
            activity.sourceId,
            lastMovementAtEpochMillis.toString(),
            thresholdCrossingAtEpochMillis.toString(),
            ALGORITHM_VERSION.toString(),
        )
        return InactivityConfirmation(
            thresholdCrossingAtEpochMillis = thresholdCrossingAtEpochMillis,
            activityFreshUntilEpochMillis = activityFreshUntilEpochMillis,
            triggerIdentity = "inactivity:v$ALGORITHM_VERSION:$identityHash",
            recommendationId = "PROLONGED_INACTIVITY:v$ALGORITHM_VERSION:$identityHash",
        )
    }

    fun matches(
        recommendation: CoachRecommendation.Action,
        activity: ActivitySnapshot?,
        settings: CoachSettings,
        nowEpochMillis: Long,
        minuteOfDay: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (
            recommendation.reason != CoachReason.PROLONGED_INACTIVITY ||
            recommendation.interventionType != InterventionType.WALK ||
            recommendation.targetFloors != null ||
            recommendation.algorithmVersion != ALGORITHM_VERSION
        ) {
            return false
        }
        val confirmation = confirm(
            activity = activity,
            settings = settings,
            nowEpochMillis = nowEpochMillis,
            minuteOfDay = minuteOfDay,
            zoneId = zoneId,
        ) ?: return false
        return recommendation.id == confirmation.recommendationId &&
            recommendation.triggerContextId == confirmation.triggerIdentity &&
            recommendation.triggerAtEpochMillis ==
            confirmation.thresholdCrossingAtEpochMillis
    }

    private fun isCurrentLocalDate(
        activity: ActivitySnapshot,
        lastMovementAtEpochMillis: Long,
        nowEpochMillis: Long,
        zoneId: ZoneId,
    ): Boolean {
        val currentDate = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        return Instant.ofEpochMilli(activity.measuredAtEpochMillis)
            .atZone(zoneId)
            .toLocalDate() == currentDate &&
            Instant.ofEpochMilli(lastMovementAtEpochMillis)
                .atZone(zoneId)
                .toLocalDate() == currentDate
    }

    private fun isInWorkingHours(
        minuteOfDay: Int,
        settings: CoachSettings,
    ): Boolean {
        val start = settings.workingHoursStartMinuteOfDay
        val end = settings.workingHoursEndMinuteOfDay
        if (minuteOfDay !in minuteOfDayRange) return false
        if (start !in minuteOfDayRange || end !in minuteOfDayRange) return false
        if (start == end) return true
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun checkedAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun checkedSubtract(left: Long, right: Long): Long? = try {
        Math.subtractExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun checkedMultiply(left: Long, right: Long): Long? = try {
        Math.multiplyExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun fingerprint(vararg parts: String): String {
        val input = parts.joinToString("|") { part -> "${part.length}:$part" }
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                val value = byte.toInt() and 0xff
                "${hex[value ushr 4]}${hex[value and 0x0f]}"
            }
    }

    private val minuteOfDayRange = 0 until 24 * 60
    private val hex = "0123456789abcdef".toCharArray()
    private const val MILLIS_PER_MINUTE = 60_000L
}
