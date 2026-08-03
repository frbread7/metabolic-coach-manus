package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlycemicGoalScenario
import com.young.metaboliccoach.core.model.GlycemicMetricsStatus
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestoneEvaluation
import com.young.metaboliccoach.core.model.GlycemicScenarioStatus
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.MilestoneEvaluationState
import com.young.metaboliccoach.core.model.RollingGlycemicMetrics
import kotlin.math.abs
import kotlin.math.ceil

/** Pure, provider-independent calculations for the phone-side Glycemic Goal Planner. */
object GlycemicGoalPlanner {
    const val GMI_INTERCEPT = 3.31
    const val GMI_MEAN_GLUCOSE_COEFFICIENT = 0.02392
    const val MINIMUM_COVERAGE_PERCENT = 70.0
    const val DEFAULT_MAX_INTERPOLATION_GAP_MINUTES = 20L

    fun gmiFromMeanGlucose(meanGlucoseMgDl: Double): Double? =
        meanGlucoseMgDl.takeIf { it.isFinite() && it > 0.0 }?.let {
            GMI_INTERCEPT + GMI_MEAN_GLUCOSE_COEFFICIENT * it
        }

    fun meanGlucoseFromGmi(targetGmiPercent: Double): Double? =
        targetGmiPercent.takeIf { it.isFinite() }?.let {
            (it - GMI_INTERCEPT) / GMI_MEAN_GLUCOSE_COEFFICIENT
        }?.takeIf { it.isFinite() && it > 0.0 }

    fun calculateRollingMetrics(
        readings: List<GlucoseReading>,
        window: GlycemicWindow,
        windowEndEpochMillis: Long,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        lowGlucoseThresholdMgDl: Int,
        veryLowGlucoseThresholdMgDl: Int,
        maxInterpolationGapMinutes: Long = DEFAULT_MAX_INTERPOLATION_GAP_MINUTES,
    ): RollingGlycemicMetrics = calculateWindowMetrics(
        readings = readings,
        window = window,
        windowEndEpochMillis = windowEndEpochMillis,
        targetLowerMgDl = targetLowerMgDl,
        targetUpperMgDl = targetUpperMgDl,
        lowGlucoseThresholdMgDl = lowGlucoseThresholdMgDl,
        veryLowGlucoseThresholdMgDl = veryLowGlucoseThresholdMgDl,
        maxInterpolationGapMinutes = maxInterpolationGapMinutes,
    )

    fun calculateGoalScenario(
        readings: List<GlucoseReading>,
        horizon: GlycemicWindow,
        windowEndEpochMillis: Long,
        targetGmiPercent: Double,
        plannerSettings: GlycemicPlannerSettings,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        maxInterpolationGapMinutes: Long = DEFAULT_MAX_INTERPOLATION_GAP_MINUTES,
    ): GlycemicGoalScenario = calculateGoalScenarioInternal(
        readings = readings,
        displayHorizon = horizon,
        horizonDays = horizon.days.toDouble(),
        windowEndEpochMillis = windowEndEpochMillis,
        targetGmiPercent = targetGmiPercent,
        plannerSettings = plannerSettings,
        targetLowerMgDl = targetLowerMgDl,
        targetUpperMgDl = targetUpperMgDl,
        maxInterpolationGapMinutes = maxInterpolationGapMinutes,
    )

    fun calculateGoalScenarioForMilestone(
        readings: List<GlucoseReading>,
        milestone: GlycemicPlanningMilestone,
        windowEndEpochMillis: Long,
        plannerSettings: GlycemicPlannerSettings,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        maxInterpolationGapMinutes: Long = DEFAULT_MAX_INTERPOLATION_GAP_MINUTES,
    ): GlycemicGoalScenario {
        val displayHorizon = GlycemicWindow.fromDays(milestone.originalHorizonDays)
            ?: GlycemicWindow.DAYS_30
        val remainingDays = ceil(
            (milestone.targetDateEpochMillis - windowEndEpochMillis).toDouble() / DAY_MILLIS,
        ).toInt().coerceIn(1, 90)
        return calculateGoalScenarioInternal(
            readings = readings,
            displayHorizon = displayHorizon,
            horizonDays = remainingDays.toDouble(),
            windowEndEpochMillis = windowEndEpochMillis,
            targetGmiPercent = milestone.targetGmiPercent,
            plannerSettings = plannerSettings,
            targetLowerMgDl = targetLowerMgDl,
            targetUpperMgDl = targetUpperMgDl,
            maxInterpolationGapMinutes = maxInterpolationGapMinutes,
        ).copy(remainingWindowDays = remainingDays)
    }

    fun evaluatePlanningMilestone(
        readings: List<GlucoseReading>,
        milestone: GlycemicPlanningMilestone,
        windowEndEpochMillis: Long,
        plannerSettings: GlycemicPlannerSettings,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        maxInterpolationGapMinutes: Long = DEFAULT_MAX_INTERPOLATION_GAP_MINUTES,
    ): GlycemicPlanningMilestoneEvaluation {
        val temporalState = milestone.temporalState(windowEndEpochMillis)
        if (windowEndEpochMillis < milestone.targetDateEpochMillis) {
            val scenario = calculateGoalScenarioForMilestone(
                readings = readings,
                milestone = milestone,
                windowEndEpochMillis = windowEndEpochMillis,
                plannerSettings = plannerSettings,
                targetLowerMgDl = targetLowerMgDl,
                targetUpperMgDl = targetUpperMgDl,
                maxInterpolationGapMinutes = maxInterpolationGapMinutes,
            )
            return GlycemicPlanningMilestoneEvaluation(
                milestoneId = milestone.id,
                targetDateEpochMillis = milestone.targetDateEpochMillis,
                temporalState = temporalState,
                evaluationState = null,
                rollingMetrics = scenario.recentSafety,
                scenario = scenario,
                detail = "Remaining-window scenario; not a treatment recommendation.",
            )
        }

        val metrics = calculateRollingMetrics(
            readings = readings,
            window = GlycemicWindow.DAYS_90,
            windowEndEpochMillis = milestone.targetDateEpochMillis,
            targetLowerMgDl = targetLowerMgDl,
            targetUpperMgDl = targetUpperMgDl,
            lowGlucoseThresholdMgDl = plannerSettings.lowGlucoseThresholdMgDl,
            veryLowGlucoseThresholdMgDl = plannerSettings.veryLowGlucoseThresholdMgDl,
            maxInterpolationGapMinutes = maxInterpolationGapMinutes,
        )
        val evaluationState = when {
            metrics.status == GlycemicMetricsStatus.SOURCE_DISCONTINUITY ->
                MilestoneEvaluationState.SOURCE_DISCONTINUITY
            metrics.status != GlycemicMetricsStatus.AVAILABLE ->
                MilestoneEvaluationState.INSUFFICIENT_DATA
            (metrics.timeBelowRangePercent ?: 0.0) > plannerSettings.maximumLowGlucosePercent ||
                (metrics.timeVeryLowPercent ?: 0.0) >
                plannerSettings.maximumVeryLowGlucosePercent ->
                MilestoneEvaluationState.SUPPRESSED_FOR_LOW_GLUCOSE_RISK
            (metrics.gmiPercent ?: Double.POSITIVE_INFINITY) <= milestone.targetGmiPercent ->
                MilestoneEvaluationState.TARGET_CONDITION_MET
            else -> MilestoneEvaluationState.TARGET_CONDITION_NOT_MET
        }
        return GlycemicPlanningMilestoneEvaluation(
            milestoneId = milestone.id,
            targetDateEpochMillis = milestone.targetDateEpochMillis,
            temporalState = temporalState,
            evaluationState = evaluationState,
            rollingMetrics = metrics,
            scenario = null,
            detail = when (evaluationState) {
                MilestoneEvaluationState.TARGET_CONDITION_MET ->
                    "Target condition met from sufficiently covered CGM-derived data."
                MilestoneEvaluationState.TARGET_CONDITION_NOT_MET ->
                    "Target condition was not met by the selected target date."
                MilestoneEvaluationState.SUPPRESSED_FOR_LOW_GLUCOSE_RISK ->
                    "Evaluation is hidden because low-glucose exposure exceeds the configured safety boundary."
                MilestoneEvaluationState.SOURCE_DISCONTINUITY ->
                    "The evaluation window contains more than one glucose source."
                MilestoneEvaluationState.INSUFFICIENT_DATA ->
                    "This milestone could not be evaluated from the available data."
                MilestoneEvaluationState.CALCULATION_UNAVAILABLE ->
                    "The milestone evaluation is not currently available."
            },
        )
    }

    private fun calculateGoalScenarioInternal(
        readings: List<GlucoseReading>,
        displayHorizon: GlycemicWindow,
        horizonDays: Double,
        windowEndEpochMillis: Long,
        targetGmiPercent: Double,
        plannerSettings: GlycemicPlannerSettings,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        maxInterpolationGapMinutes: Long,
    ): GlycemicGoalScenario {
        val targetMean = meanGlucoseFromGmi(targetGmiPercent)
        if (
            targetMean == null ||
            targetGmiPercent !in MINIMUM_GMI_PERCENT..MAXIMUM_GMI_PERCENT ||
            targetMean !in MINIMUM_GLUCOSE_MG_DL.toDouble()..MAXIMUM_GLUCOSE_MG_DL.toDouble()
        ) {
            return GlycemicGoalScenario(
                horizon = displayHorizon,
                targetGmiPercent = targetGmiPercent,
                targetMeanGlucoseMgDl = targetMean,
                observedPastMeanGlucoseMgDl = null,
                scenarioFutureMeanGlucoseMgDl = null,
                recentSafety = null,
                status = GlycemicScenarioStatus.INVALID_TARGET,
                detail = "Choose a valid GMI target for the planner.",
            )
        }

        val recentSafety = calculateWindowMetrics(
            readings = readings,
            window = GlycemicWindow.DAYS_14,
            windowEndEpochMillis = windowEndEpochMillis,
            targetLowerMgDl = targetLowerMgDl,
            targetUpperMgDl = targetUpperMgDl,
            lowGlucoseThresholdMgDl = plannerSettings.lowGlucoseThresholdMgDl,
            veryLowGlucoseThresholdMgDl = plannerSettings.veryLowGlucoseThresholdMgDl,
            maxInterpolationGapMinutes = maxInterpolationGapMinutes,
        )
        if (recentSafety.status != GlycemicMetricsStatus.AVAILABLE) {
            return GlycemicGoalScenario(
                horizon = displayHorizon,
                targetGmiPercent = targetGmiPercent,
                targetMeanGlucoseMgDl = targetMean,
                observedPastMeanGlucoseMgDl = null,
                scenarioFutureMeanGlucoseMgDl = null,
                recentSafety = recentSafety,
                status = recentSafety.status.toScenarioStatus(),
                detail = when (recentSafety.status) {
                    GlycemicMetricsStatus.SOURCE_DISCONTINUITY ->
                        "The recent safety window contains more than one glucose source."
                    GlycemicMetricsStatus.INVALID_INPUT ->
                        "The planner received invalid safety thresholds or window settings."
                    else -> "At least 14 days of sufficiently covered CGM data is required."
                },
            )
        }

        val recentLowPercent = recentSafety.timeBelowRangePercent
        val recentVeryLowPercent = recentSafety.timeVeryLowPercent
        if (
            targetMean <= plannerSettings.lowGlucoseThresholdMgDl ||
            (
                recentLowPercent != null &&
                    recentLowPercent > plannerSettings.maximumLowGlucosePercent &&
                    targetMean < (recentSafety.meanGlucoseMgDl ?: Double.MAX_VALUE)
                ) ||
            (
                recentVeryLowPercent != null &&
                    recentVeryLowPercent >
                    plannerSettings.maximumVeryLowGlucosePercent &&
                    targetMean < (recentSafety.meanGlucoseMgDl ?: Double.MAX_VALUE)
                )
        ) {
            return GlycemicGoalScenario(
                horizon = displayHorizon,
                targetGmiPercent = targetGmiPercent,
                targetMeanGlucoseMgDl = targetMean,
                observedPastMeanGlucoseMgDl = null,
                scenarioFutureMeanGlucoseMgDl = null,
                recentSafety = recentSafety,
                status = GlycemicScenarioStatus.SUPPRESSED_FOR_LOW_GLUCOSE_RISK,
                detail = "The scenario is hidden because a lower mean may increase low-glucose exposure.",
            )
        }

        val observedPastMetrics = if (horizonDays < 90.0) {
            calculateWindowMetrics(
                readings = readings,
                window = GlycemicWindow.DAYS_90,
                windowEndEpochMillis = windowEndEpochMillis,
                requestedDurationMillis = ((90.0 - horizonDays) * DAY_MILLIS).toLong(),
                targetLowerMgDl = targetLowerMgDl,
                targetUpperMgDl = targetUpperMgDl,
                lowGlucoseThresholdMgDl = plannerSettings.lowGlucoseThresholdMgDl,
                veryLowGlucoseThresholdMgDl = plannerSettings.veryLowGlucoseThresholdMgDl,
                maxInterpolationGapMinutes = maxInterpolationGapMinutes,
            )
        } else {
            null
        }
        val observedPastMean = observedPastMetrics
            ?.takeIf { it.status == GlycemicMetricsStatus.AVAILABLE }
            ?.meanGlucoseMgDl

        if (horizonDays < 90.0 && observedPastMean == null) {
            return GlycemicGoalScenario(
                horizon = displayHorizon,
                targetGmiPercent = targetGmiPercent,
                targetMeanGlucoseMgDl = targetMean,
                observedPastMeanGlucoseMgDl = null,
                scenarioFutureMeanGlucoseMgDl = null,
                recentSafety = recentSafety,
                status = observedPastMetrics?.status.toScenarioStatus(),
                detail = when (observedPastMetrics?.status) {
                    GlycemicMetricsStatus.SOURCE_DISCONTINUITY ->
                        "The historical segment contains more than one glucose source."
                    GlycemicMetricsStatus.INVALID_INPUT ->
                        "The planner received invalid historical-window settings."
                    else -> "The historical segment for this horizon is not sufficiently covered."
                },
            )
        }

        val scenarioMean = if (horizonDays >= 90.0) {
            targetMean
        } else {
            ((90.0 * targetMean) - ((90.0 - horizonDays) * checkNotNull(observedPastMean))) /
                horizonDays
        }
        if (!scenarioMean.isFinite() || scenarioMean <= 0.0) {
            return GlycemicGoalScenario(
                horizon = displayHorizon,
                targetGmiPercent = targetGmiPercent,
                targetMeanGlucoseMgDl = targetMean,
                observedPastMeanGlucoseMgDl = observedPastMean,
                scenarioFutureMeanGlucoseMgDl = null,
                recentSafety = recentSafety,
                status = GlycemicScenarioStatus.NOT_ATTAINABLE_IN_SELECTED_WINDOW,
                detail = "The selected target cannot be represented by a positive future mean in this window.",
            )
        }
        if (scenarioMean <= plannerSettings.lowGlucoseThresholdMgDl) {
            return GlycemicGoalScenario(
                horizon = displayHorizon,
                targetGmiPercent = targetGmiPercent,
                targetMeanGlucoseMgDl = targetMean,
                observedPastMeanGlucoseMgDl = observedPastMean,
                scenarioFutureMeanGlucoseMgDl = null,
                recentSafety = recentSafety,
                status = GlycemicScenarioStatus.SUPPRESSED_FOR_LOW_GLUCOSE_RISK,
                detail = "The scenario falls at or below the configured low-glucose boundary.",
            )
        }

        val warning = scenarioMean > MAXIMUM_GLUCOSE_MG_DL
        return GlycemicGoalScenario(
            horizon = displayHorizon,
            targetGmiPercent = targetGmiPercent,
            targetMeanGlucoseMgDl = targetMean,
            observedPastMeanGlucoseMgDl = observedPastMean,
            scenarioFutureMeanGlucoseMgDl = scenarioMean,
            recentSafety = recentSafety,
            status = if (warning) {
                GlycemicScenarioStatus.AVAILABLE_WITH_WARNING
            } else {
                GlycemicScenarioStatus.AVAILABLE
            },
            detail = if (warning) {
                "The scenario is outside the supported glucose range; review the target with your clinician."
            } else {
                "Mathematical planning scenario for the remaining window; not a treatment recommendation."
            },
        )
    }

    private fun calculateWindowMetrics(
        readings: List<GlucoseReading>,
        window: GlycemicWindow,
        windowEndEpochMillis: Long,
        requestedDurationMillis: Long = window.durationMillis,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        lowGlucoseThresholdMgDl: Int,
        veryLowGlucoseThresholdMgDl: Int,
        maxInterpolationGapMinutes: Long,
    ): RollingGlycemicMetrics {
        val windowStart = windowEndEpochMillis - requestedDurationMillis
        val windowDurationMillis = windowEndEpochMillis - windowStart
        if (
            windowDurationMillis <= 0L ||
            targetLowerMgDl >= targetUpperMgDl ||
            veryLowGlucoseThresholdMgDl > lowGlucoseThresholdMgDl
        ) {
            return emptyMetrics(
                window = window,
                start = windowStart,
                end = windowEndEpochMillis,
                status = GlycemicMetricsStatus.INVALID_INPUT,
                detail = "The planner received invalid window or threshold settings.",
            )
        }
        val candidateReadings = readings
            .asSequence()
            .filter {
                it.measuredAtEpochMillis in
                    (windowStart - maxInterpolationGapMinutes * 60_000L)..windowEndEpochMillis
            }
            .toList()
        val sourceIds = candidateReadings.map(GlucoseReading::sourceId).distinct()
        if (sourceIds.size > 1) {
            return emptyMetrics(
                window = window,
                start = windowStart,
                end = windowEndEpochMillis,
                sourceId = null,
                status = GlycemicMetricsStatus.SOURCE_DISCONTINUITY,
                detail = "Glucose sources changed inside the requested window.",
            )
        }
        val points = candidateReadings
            .asSequence()
            .groupBy(GlucoseReading::measuredAtEpochMillis)
            .values
            .mapNotNull { sameTimestamp -> sameTimestamp.maxByOrNull(GlucoseReading::id) }
            .sortedBy(GlucoseReading::measuredAtEpochMillis)
        if (points.size < 2) {
            return emptyMetrics(
                window = window,
                start = windowStart,
                end = windowEndEpochMillis,
                sourceId = sourceIds.singleOrNull(),
                status = GlycemicMetricsStatus.INSUFFICIENT_DATA,
                detail = "At least two readings are required to estimate covered time.",
                sampleCount = points.size,
            )
        }

        var coveredMillis = 0L
        var glucoseIntegral = 0.0
        var inRangeMillis = 0L
        var belowRangeMillis = 0L
        var veryLowMillis = 0L
        var largestGapMillis = 0L
        val maxGapMillis = maxInterpolationGapMinutes * 60_000L

        points.zipWithNext().forEach { (first, second) ->
            val gapMillis = second.measuredAtEpochMillis - first.measuredAtEpochMillis
            if (gapMillis <= 0L) return@forEach
            largestGapMillis = maxOf(largestGapMillis, gapMillis)
            val segmentStart = maxOf(windowStart, first.measuredAtEpochMillis)
            val segmentEnd = minOf(windowEndEpochMillis, second.measuredAtEpochMillis)
            if (segmentEnd <= segmentStart || gapMillis > maxGapMillis) return@forEach
            val startValue = interpolate(
                first.valueMgDl.toDouble(),
                second.valueMgDl.toDouble(),
                first.measuredAtEpochMillis,
                second.measuredAtEpochMillis,
                segmentStart,
            )
            val endValue = interpolate(
                first.valueMgDl.toDouble(),
                second.valueMgDl.toDouble(),
                first.measuredAtEpochMillis,
                second.measuredAtEpochMillis,
                segmentEnd,
            )
            val segmentMillis = segmentEnd - segmentStart
            coveredMillis += segmentMillis
            glucoseIntegral += (startValue + endValue) / 2.0 * segmentMillis
            val classification = classifyDuration(
                startValue = startValue,
                endValue = endValue,
                durationMillis = segmentMillis,
                targetLowerMgDl = targetLowerMgDl.toDouble(),
                targetUpperMgDl = targetUpperMgDl.toDouble(),
                lowGlucoseThresholdMgDl = lowGlucoseThresholdMgDl.toDouble(),
                veryLowGlucoseThresholdMgDl = veryLowGlucoseThresholdMgDl.toDouble(),
            )
            inRangeMillis += classification.inRangeMillis
            belowRangeMillis += classification.belowRangeMillis
            veryLowMillis += classification.veryLowMillis
        }

        val coveragePercent = coveredMillis * 100.0 / windowDurationMillis
        val missingDurationMillis = (windowDurationMillis - coveredMillis).coerceAtLeast(0L)
        val available = coveragePercent >= MINIMUM_COVERAGE_PERCENT
        val mean = glucoseIntegral.takeIf { coveredMillis > 0L }?.let { it / coveredMillis }
        val percent: (Long) -> Double? = { value ->
            value.takeIf { coveredMillis > 0L }?.let { it * 100.0 / coveredMillis }
        }
        return RollingGlycemicMetrics(
            window = window,
            windowStartEpochMillis = windowStart,
            windowEndEpochMillis = windowEndEpochMillis,
            meanGlucoseMgDl = mean,
            gmiPercent = mean?.let(::gmiFromMeanGlucose),
            timeInRangePercent = percent(inRangeMillis),
            timeBelowRangePercent = percent(belowRangeMillis),
            timeVeryLowPercent = percent(veryLowMillis),
            coveragePercent = coveragePercent,
            missingDurationMillis = missingDurationMillis,
            largestGapMillis = largestGapMillis,
            sampleCount = points.count { it.measuredAtEpochMillis in windowStart..windowEndEpochMillis },
            sourceId = sourceIds.singleOrNull(),
            status = if (available) {
                GlycemicMetricsStatus.AVAILABLE
            } else {
                GlycemicMetricsStatus.INSUFFICIENT_DATA
            },
            detail = if (available) {
                "Coverage is sufficient for this rolling window."
            } else {
                "At least ${MINIMUM_COVERAGE_PERCENT.toInt()}% time coverage is required."
            },
        )
    }

    private fun emptyMetrics(
        window: GlycemicWindow,
        start: Long,
        end: Long,
        status: GlycemicMetricsStatus,
        detail: String,
        sourceId: String? = null,
        sampleCount: Int = 0,
    ) = RollingGlycemicMetrics(
        window = window,
        windowStartEpochMillis = start,
        windowEndEpochMillis = end,
        meanGlucoseMgDl = null,
        gmiPercent = null,
        timeInRangePercent = null,
        timeBelowRangePercent = null,
        timeVeryLowPercent = null,
        coveragePercent = 0.0,
        missingDurationMillis = (end - start).coerceAtLeast(0L),
        largestGapMillis = 0L,
        sampleCount = sampleCount,
        sourceId = sourceId,
        status = status,
        detail = detail,
    )

    private fun invalidScenario(
        horizon: GlycemicWindow,
        targetGmiPercent: Double,
        targetMean: Double,
    ) = GlycemicGoalScenario(
        horizon = horizon,
        targetGmiPercent = targetGmiPercent,
        targetMeanGlucoseMgDl = targetMean,
        observedPastMeanGlucoseMgDl = null,
        scenarioFutureMeanGlucoseMgDl = null,
        recentSafety = null,
        status = GlycemicScenarioStatus.CALCULATION_ERROR,
        detail = "The selected horizon is not supported by the rolling 90-day model.",
    )

    private fun GlycemicMetricsStatus?.toScenarioStatus(): GlycemicScenarioStatus = when (this) {
        GlycemicMetricsStatus.SOURCE_DISCONTINUITY -> GlycemicScenarioStatus.SOURCE_DISCONTINUITY
        GlycemicMetricsStatus.INVALID_INPUT -> GlycemicScenarioStatus.CALCULATION_ERROR
        else -> GlycemicScenarioStatus.INSUFFICIENT_DATA
    }

    private fun interpolate(
        firstValue: Double,
        secondValue: Double,
        firstTime: Long,
        secondTime: Long,
        time: Long,
    ): Double {
        val denominator = (secondTime - firstTime).toDouble()
        if (denominator <= 0.0) return firstValue
        val fraction = (time - firstTime) / denominator
        return firstValue + (secondValue - firstValue) * fraction.coerceIn(0.0, 1.0)
    }

    private fun classifyDuration(
        startValue: Double,
        endValue: Double,
        durationMillis: Long,
        targetLowerMgDl: Double,
        targetUpperMgDl: Double,
        lowGlucoseThresholdMgDl: Double,
        veryLowGlucoseThresholdMgDl: Double,
    ): Classification {
        val fractions = buildList {
            add(0.0)
            add(1.0)
            val difference = endValue - startValue
            if (abs(difference) > 1e-9) {
                listOf(
                    targetLowerMgDl,
                    targetUpperMgDl,
                    lowGlucoseThresholdMgDl,
                    veryLowGlucoseThresholdMgDl,
                ).forEach { threshold ->
                    val fraction = (threshold - startValue) / difference
                    if (fraction > 0.0 && fraction < 1.0) add(fraction)
                }
            }
        }.distinct().sorted()
        var inRangeMillis = 0L
        var belowRangeMillis = 0L
        var veryLowMillis = 0L
        fractions.zipWithNext().forEach { (startFraction, endFraction) ->
            val fractionDuration = ((endFraction - startFraction) * durationMillis).toLong()
            val midpoint = (startFraction + endFraction) / 2.0
            val value = startValue + (endValue - startValue) * midpoint
            when {
                value < veryLowGlucoseThresholdMgDl -> {
                    veryLowMillis += fractionDuration
                    belowRangeMillis += fractionDuration
                }
                value < lowGlucoseThresholdMgDl -> belowRangeMillis += fractionDuration
                value in targetLowerMgDl..targetUpperMgDl -> inRangeMillis += fractionDuration
            }
        }
        val assigned = inRangeMillis + belowRangeMillis
        val remainder = durationMillis - assigned
        if (remainder > 0L) {
            val midpoint = startValue + (endValue - startValue) / 2.0
            when {
                midpoint < veryLowGlucoseThresholdMgDl -> {
                    veryLowMillis += remainder
                    belowRangeMillis += remainder
                }
                midpoint < lowGlucoseThresholdMgDl -> belowRangeMillis += remainder
                midpoint in targetLowerMgDl..targetUpperMgDl -> inRangeMillis += remainder
            }
        }
        return Classification(inRangeMillis, belowRangeMillis, veryLowMillis)
    }

    private data class Classification(
        val inRangeMillis: Long,
        val belowRangeMillis: Long,
        val veryLowMillis: Long,
    )

    private const val MINIMUM_GMI_PERCENT = 3.5
    private const val MAXIMUM_GMI_PERCENT = 15.0
    private const val MINIMUM_GLUCOSE_MG_DL = 20
    private const val MAXIMUM_GLUCOSE_MG_DL = 600
    private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
}
