package com.young.metaboliccoach.core.model

enum class GlycemicWindow(val days: Int) {
    DAYS_14(14),
    DAYS_30(30),
    DAYS_60(60),
    DAYS_90(90),
    ;

    val durationMillis: Long
        get() = days * MILLIS_PER_DAY

    companion object {
        fun fromDays(days: Int): GlycemicWindow? = entries.firstOrNull { it.days == days }
    }
}

enum class GlycemicTargetProvenance {
    USER_ENTERED,
    CLINICIAN_AGREED,
}

enum class GlycemicMetricsStatus {
    AVAILABLE,
    INSUFFICIENT_DATA,
    SOURCE_DISCONTINUITY,
    INVALID_INPUT,
}

enum class GlycemicScenarioStatus {
    AVAILABLE,
    AVAILABLE_WITH_WARNING,
    INSUFFICIENT_DATA,
    SOURCE_DISCONTINUITY,
    NOT_ATTAINABLE_IN_SELECTED_WINDOW,
    SUPPRESSED_FOR_LOW_GLUCOSE_RISK,
    INVALID_TARGET,
    CALCULATION_ERROR,
}

enum class MilestoneLifecycleState {
    ACTIVE,
    ARCHIVED,
}

enum class MilestoneTemporalState {
    FUTURE,
    DUE,
    PAST,
}

enum class MilestoneEvaluationState {
    TARGET_CONDITION_MET,
    TARGET_CONDITION_NOT_MET,
    INSUFFICIENT_DATA,
    SOURCE_DISCONTINUITY,
    SUPPRESSED_FOR_LOW_GLUCOSE_RISK,
    CALCULATION_UNAVAILABLE,
}

/** A saved phone-only planning intention. It never changes coaching or Wear state. */
data class GlycemicPlanningMilestone(
    val id: String,
    val title: String?,
    val targetGmiPercent: Double,
    val targetProvenance: GlycemicTargetProvenance,
    val targetDateEpochMillis: Long,
    val originalHorizonDays: Int,
    val lifecycleState: MilestoneLifecycleState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
    val calculationContractVersion: Int,
)

data class GlycemicPlannerSettings(
    val targetGmiPercent: Double? = null,
    val targetProvenance: GlycemicTargetProvenance? = null,
    val horizon: GlycemicWindow = GlycemicWindow.DAYS_30,
    val lowGlucoseThresholdMgDl: Int = 70,
    val veryLowGlucoseThresholdMgDl: Int = 54,
    val maximumLowGlucosePercent: Double = 4.0,
    val maximumVeryLowGlucosePercent: Double = 1.0,
)

data class RollingGlycemicMetrics(
    val window: GlycemicWindow,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val meanGlucoseMgDl: Double?,
    val gmiPercent: Double?,
    val timeInRangePercent: Double?,
    val timeBelowRangePercent: Double?,
    val timeVeryLowPercent: Double?,
    val coveragePercent: Double,
    val missingDurationMillis: Long,
    val largestGapMillis: Long,
    val sampleCount: Int,
    val sourceId: String?,
    val status: GlycemicMetricsStatus,
    val detail: String,
)

data class GlycemicGoalScenario(
    val horizon: GlycemicWindow,
    val targetGmiPercent: Double,
    val targetMeanGlucoseMgDl: Double?,
    val observedPastMeanGlucoseMgDl: Double?,
    val scenarioFutureMeanGlucoseMgDl: Double?,
    val recentSafety: RollingGlycemicMetrics?,
    val status: GlycemicScenarioStatus,
    val detail: String,
    val remainingWindowDays: Int? = null,
)

data class GlycemicPlanningMilestoneEvaluation(
    val milestoneId: String,
    val targetDateEpochMillis: Long,
    val temporalState: MilestoneTemporalState,
    val evaluationState: MilestoneEvaluationState?,
    val rollingMetrics: RollingGlycemicMetrics?,
    val scenario: GlycemicGoalScenario?,
    val detail: String,
)

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L
