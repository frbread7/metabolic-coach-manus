package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.MilestoneLifecycleState
import com.young.metaboliccoach.core.model.MilestoneTemporalState
import java.time.Instant
import java.time.ZoneId

const val GLYCEMIC_MILESTONE_CALCULATION_CONTRACT_VERSION = 1
const val GLYCEMIC_MILESTONE_MAX_TITLE_LENGTH = 80

fun GlycemicPlanningMilestone.temporalState(
    nowEpochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MilestoneTemporalState {
    val targetDate = Instant.ofEpochMilli(targetDateEpochMillis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    return when {
        targetDate.isAfter(today) -> MilestoneTemporalState.FUTURE
        targetDate.isBefore(today) -> MilestoneTemporalState.PAST
        else -> MilestoneTemporalState.DUE
    }
}

fun sortPlanningMilestones(
    milestones: List<GlycemicPlanningMilestone>,
    nowEpochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<GlycemicPlanningMilestone> = milestones.sortedWith(
    compareBy<GlycemicPlanningMilestone> {
        if (it.lifecycleState == MilestoneLifecycleState.ARCHIVED) 1 else 0
    }.thenComparator { left, right ->
        if (left.lifecycleState == MilestoneLifecycleState.ARCHIVED ||
            right.lifecycleState == MilestoneLifecycleState.ARCHIVED
        ) {
            compareNullableDescending(left.archivedAtEpochMillis, right.archivedAtEpochMillis)
        } else {
            val leftTemporal = left.temporalState(nowEpochMillis, zoneId)
            val rightTemporal = right.temporalState(nowEpochMillis, zoneId)
            val leftPast = leftTemporal == MilestoneTemporalState.PAST
            val rightPast = rightTemporal == MilestoneTemporalState.PAST
            when {
                leftPast && !rightPast -> 1
                !leftPast && rightPast -> -1
                leftPast && rightPast -> right.targetDateEpochMillis.compareTo(
                    left.targetDateEpochMillis,
                )
                else -> left.targetDateEpochMillis.compareTo(right.targetDateEpochMillis)
            }
        }
    }.thenBy { it.createdAtEpochMillis }
        .thenBy { it.id },
)

fun validateMilestoneDraft(
    title: String?,
    targetGmiPercent: Double,
    targetProvenance: GlycemicTargetProvenance,
    horizonDays: Int,
    targetDateEpochMillis: Long,
    nowEpochMillis: Long,
) {
    require(targetGmiPercent.isFinite() &&
        targetGmiPercent in GlycemicPlannerBounds.TARGET_GMI_PERCENT) {
        "The GMI target must be between 3.5 and 15.0."
    }
    require(GlycemicPlannerBounds.SCENARIO_HORIZONS.any { it.days == horizonDays }) {
        "The milestone horizon must be 30, 60, or 90 days."
    }
    require(targetDateEpochMillis > nowEpochMillis) {
        "A new milestone must have a future target date."
    }
    require(title.orEmpty().trim().length <= GLYCEMIC_MILESTONE_MAX_TITLE_LENGTH) {
        "The milestone title is too long."
    }
}

fun normalizedMilestoneTitle(title: String?): String? = title
    ?.trim()
    ?.takeIf(String::isNotEmpty)

private fun compareNullableDescending(left: Long?, right: Long?): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> right.compareTo(left)
}
