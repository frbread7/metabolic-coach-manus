package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.GlycemicMetricsStatus
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicScenarioStatus
import com.young.metaboliccoach.core.model.GlycemicWindow
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

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
        const val SAMPLE_INTERVAL = 15 * 60 * 1_000L
        const val SOURCE = "nightscout:source"
    }
}
