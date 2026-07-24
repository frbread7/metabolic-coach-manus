package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseReading
import kotlin.math.abs

sealed interface FollowUpSelection {
    data object Wait : FollowUpSelection
    data class Finalize(val reading: GlucoseReading?) : FollowUpSelection
}

object FollowUpReadingSelector {
    fun select(
        readings: List<GlucoseReading>,
        exactSourceId: String,
        dueAtEpochMillis: Long,
        deadlineEpochMillis: Long,
        nowEpochMillis: Long,
    ): FollowUpSelection {
        require(deadlineEpochMillis >= dueAtEpochMillis)
        val candidates = readings
            .asSequence()
            .filter { it.sourceId == exactSourceId }
            .filter { it.measuredAtEpochMillis <= minOf(nowEpochMillis, deadlineEpochMillis) }
            .toList()
        val comparator = compareBy<GlucoseReading> {
            abs(it.measuredAtEpochMillis - dueAtEpochMillis)
        }.thenBy { it.id }
        val afterDue = candidates
            .filter { it.measuredAtEpochMillis >= dueAtEpochMillis }
            .minWithOrNull(comparator)
        if (afterDue != null) return FollowUpSelection.Finalize(afterDue)
        if (nowEpochMillis < deadlineEpochMillis) return FollowUpSelection.Wait
        return FollowUpSelection.Finalize(candidates.minWithOrNull(comparator))
    }
}
