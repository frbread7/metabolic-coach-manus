package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.GlycemicMetricsStatus
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicScenarioStatus
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.MilestoneEvaluationState
import com.young.metaboliccoach.core.model.MilestoneLifecycleState
import com.young.metaboliccoach.core.model.MilestoneTemporalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlycemicGoalPlannerTest {
    @Test
    fun `GMI equation and inverse round trip`() {
        val gmi = GlycemicGoalPlanner.gmiFromMeanGlucose(140.0)

        assertEquals(6.6588, checkNotNull(gmi), 0.0001)
        assertEquals(140.0, checkNotNull(GlycemicGoalPlanner.meanGlucoseFromGmi(gmi)), 0.0001)
    }

    @Test
    fun `time weighted rolling metrics calculate mean coverage and ranges`() {
        val now = DAY * 30
        val readings = readings(now, days = 30) { timestamp ->
            if (timestamp < now - 15 * DAY) 100 else 200
        }

        val metrics = GlycemicGoalPlanner.calculateRollingMetrics(
            readings = readings,
            window = GlycemicWindow.DAYS_30,
            windowEndEpochMillis = now,
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
            lowGlucoseThresholdMgDl = 70,
            veryLowGlucoseThresholdMgDl = 54,
        )

        assertEquals(GlycemicMetricsStatus.AVAILABLE, metrics.status)
        assertEquals(150.0, checkNotNull(metrics.meanGlucoseMgDl), 0.2)
        assertTrue(metrics.coveragePercent >= 99.0)
        assertTrue(checkNotNull(metrics.timeInRangePercent) in 45.0..55.0)
        assertTrue(checkNotNull(metrics.timeBelowRangePercent) < 0.1)
    }

    @Test
    fun `scenario uses preceding 60 days for a 30 day horizon`() {
        val now = DAY * 90
        val readings = readings(now, days = 90) { 150 }
        val targetMean = 140.0

        val scenario = GlycemicGoalPlanner.calculateGoalScenario(
            readings = readings,
            horizon = GlycemicWindow.DAYS_30,
            windowEndEpochMillis = now,
            targetGmiPercent = checkNotNull(GlycemicGoalPlanner.gmiFromMeanGlucose(targetMean)),
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(GlycemicScenarioStatus.AVAILABLE, scenario.status)
        assertEquals(150.0, checkNotNull(scenario.observedPastMeanGlucoseMgDl), 0.2)
        assertEquals(120.0, checkNotNull(scenario.scenarioFutureMeanGlucoseMgDl), 0.2)
    }

    @Test
    fun `scenario uses preceding 30 days for a 60 day horizon`() {
        val now = DAY * 90
        val readings = readings(now, days = 90) { timestamp ->
            if (timestamp < now - 30 * DAY) 150 else 130
        }
        val targetMean = 140.0

        val scenario = GlycemicGoalPlanner.calculateGoalScenario(
            readings = readings,
            horizon = GlycemicWindow.DAYS_60,
            windowEndEpochMillis = now,
            targetGmiPercent = checkNotNull(GlycemicGoalPlanner.gmiFromMeanGlucose(targetMean)),
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(GlycemicScenarioStatus.AVAILABLE, scenario.status)
        assertEquals(130.0, checkNotNull(scenario.observedPastMeanGlucoseMgDl), 0.2)
        assertEquals(145.0, checkNotNull(scenario.scenarioFutureMeanGlucoseMgDl), 0.2)
    }

    @Test
    fun `ninety day scenario targets the inverse GMI mean directly`() {
        val now = DAY * 90
        val readings = readings(now, days = 90) { 150 }
        val targetMean = 140.0

        val scenario = GlycemicGoalPlanner.calculateGoalScenario(
            readings = readings,
            horizon = GlycemicWindow.DAYS_90,
            windowEndEpochMillis = now,
            targetGmiPercent = checkNotNull(GlycemicGoalPlanner.gmiFromMeanGlucose(targetMean)),
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(GlycemicScenarioStatus.AVAILABLE, scenario.status)
        assertEquals(140.0, checkNotNull(scenario.scenarioFutureMeanGlucoseMgDl), 0.2)
    }

    @Test
    fun `long gaps make metrics insufficient`() {
        val now = DAY * 30
        val readings = listOf(
            reading("a", 120, now - 30 * DAY),
            reading("b", 120, now),
        )

        val metrics = GlycemicGoalPlanner.calculateRollingMetrics(
            readings = readings,
            window = GlycemicWindow.DAYS_30,
            windowEndEpochMillis = now,
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
            lowGlucoseThresholdMgDl = 70,
            veryLowGlucoseThresholdMgDl = 54,
        )

        assertEquals(GlycemicMetricsStatus.INSUFFICIENT_DATA, metrics.status)
        assertTrue(metrics.coveragePercent < 1.0)
    }

    @Test
    fun `low glucose risk suppresses a lower scenario`() {
        val now = DAY * 90
        val readings = readings(now, days = 90) { 100 }
        val settings = GlycemicPlannerSettings(
            maximumLowGlucosePercent = 4.0,
            maximumVeryLowGlucosePercent = 1.0,
        )

        val scenario = GlycemicGoalPlanner.calculateGoalScenario(
            readings = readings,
            horizon = GlycemicWindow.DAYS_90,
            windowEndEpochMillis = now,
            targetGmiPercent = checkNotNull(GlycemicGoalPlanner.gmiFromMeanGlucose(60.0)),
            plannerSettings = settings,
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(GlycemicScenarioStatus.SUPPRESSED_FOR_LOW_GLUCOSE_RISK, scenario.status)
        assertEquals(null, scenario.scenarioFutureMeanGlucoseMgDl)
    }

    @Test
    fun `mixed sources never combine`() {
        val now = DAY * 30
        val readings = readings(now, days = 30) { 120 } +
            listOf(reading("other", 120, now - DAY, sourceId = "other"))

        val metrics = GlycemicGoalPlanner.calculateRollingMetrics(
            readings = readings,
            window = GlycemicWindow.DAYS_30,
            windowEndEpochMillis = now,
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
            lowGlucoseThresholdMgDl = 70,
            veryLowGlucoseThresholdMgDl = 54,
        )

        assertEquals(GlycemicMetricsStatus.SOURCE_DISCONTINUITY, metrics.status)
    }

    @Test
    fun `source outside requested window does not create a false discontinuity`() {
        val now = DAY * 30
        val readings = readings(now, days = 30) { 120 } +
            listOf(reading("old-source", 120, now - 25 * DAY, sourceId = "other"))

        val metrics = GlycemicGoalPlanner.calculateRollingMetrics(
            readings = readings,
            window = GlycemicWindow.DAYS_14,
            windowEndEpochMillis = now,
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
            lowGlucoseThresholdMgDl = 70,
            veryLowGlucoseThresholdMgDl = 54,
        )

        assertEquals(GlycemicMetricsStatus.AVAILABLE, metrics.status)
        assertEquals(SOURCE, metrics.sourceId)
    }

    @Test
    fun `milestone scenario uses the actual remaining window after time passes`() {
        val now = DAY * 100
        val readings = readings(now, days = 90) { 150 }
        val milestone = milestone(
            id = "milestone",
            targetGmiPercent = checkNotNull(GlycemicGoalPlanner.gmiFromMeanGlucose(140.0)),
            targetDate = now + 20 * DAY,
            horizonDays = 30,
        )

        val scenario = GlycemicGoalPlanner.calculateGoalScenarioForMilestone(
            readings = readings,
            milestone = milestone,
            windowEndEpochMillis = now,
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(20, scenario.remainingWindowDays)
        assertEquals(105.0, checkNotNull(scenario.scenarioFutureMeanGlucoseMgDl), 0.3)
    }

    @Test
    fun `milestone evaluation suppresses a met target when low exposure is unsafe`() {
        val targetDate = DAY * 90
        val readings = readings(targetDate, days = 90) { 60 }
        val milestone = milestone(
            id = "milestone",
            targetGmiPercent = 7.0,
            targetDate = targetDate,
            horizonDays = 30,
        )

        val evaluation = GlycemicGoalPlanner.evaluatePlanningMilestone(
            readings = readings,
            milestone = milestone,
            windowEndEpochMillis = targetDate,
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(MilestoneTemporalState.DUE, evaluation.temporalState)
        assertEquals(
            MilestoneEvaluationState.SUPPRESSED_FOR_LOW_GLUCOSE_RISK,
            evaluation.evaluationState,
        )
    }

    @Test
    fun `milestone dates derive future due and past temporal states by local date`() {
        val now = 10 * DAY + 12 * 60 * 60 * 1_000L
        val future = milestone("future", targetDate = now + 2 * DAY, horizonDays = 30)
        val due = milestone("due", targetDate = now, horizonDays = 30)
        val past = milestone("past", targetDate = now - 2 * DAY, horizonDays = 30)

        assertEquals(MilestoneTemporalState.FUTURE, future.temporalState(now))
        assertEquals(MilestoneTemporalState.DUE, due.temporalState(now))
        assertEquals(MilestoneTemporalState.PAST, past.temporalState(now))
    }

    @Test
    fun `milestones sort active future then past then archived deterministically`() {
        val now = 100 * DAY
        val milestones = listOf(
            milestone(
                id = "archived",
                targetDate = now - DAY,
                horizonDays = 30,
                lifecycleState = MilestoneLifecycleState.ARCHIVED,
            ),
            milestone(id = "past-late", targetDate = now - DAY, horizonDays = 30),
            milestone(id = "future-late", targetDate = now + 10 * DAY, horizonDays = 30),
            milestone(id = "future-early", targetDate = now + DAY, horizonDays = 30),
            milestone(id = "past-early", targetDate = now - 10 * DAY, horizonDays = 30),
        )

        assertEquals(
            listOf("future-early", "future-late", "past-late", "past-early", "archived"),
            sortPlanningMilestones(milestones, now).map { it.id },
        )
    }

    private fun readings(
        now: Long,
        days: Int,
        value: (Long) -> Int,
    ): List<GlucoseReading> {
        val start = now - days * DAY
        return (start..now step SAMPLE_INTERVAL).mapIndexed { index, timestamp ->
            reading("$index", value(timestamp), timestamp)
        }
    }

    private fun reading(
        id: String,
        value: Int,
        timestamp: Long,
        sourceId: String = SOURCE,
    ) = GlucoseReading(
        id = "$sourceId:$id",
        valueMgDl = value,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = timestamp,
        receivedAtEpochMillis = timestamp,
        sourceId = sourceId,
    )

    private fun milestone(
        id: String,
        targetGmiPercent: Double = 7.0,
        targetDate: Long,
        horizonDays: Int,
        lifecycleState: MilestoneLifecycleState = MilestoneLifecycleState.ACTIVE,
    ) = GlycemicPlanningMilestone(
        id = id,
        title = null,
        targetGmiPercent = targetGmiPercent,
        targetProvenance = GlycemicTargetProvenance.USER_ENTERED,
        targetDateEpochMillis = targetDate,
        originalHorizonDays = horizonDays,
        lifecycleState = lifecycleState,
        createdAtEpochMillis = targetDate - DAY,
        updatedAtEpochMillis = targetDate - DAY,
        archivedAtEpochMillis = null,
        calculationContractVersion = 1,
    )

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
        const val SAMPLE_INTERVAL = 15 * 60 * 1_000L
        const val SOURCE = "nightscout:source"
    }
}
