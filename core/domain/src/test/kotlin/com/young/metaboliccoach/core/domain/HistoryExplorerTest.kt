package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.SelectedPeriodGmiAvailability
import com.young.metaboliccoach.core.model.SelectedPeriodGmiQualifier
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HistoryExplorerTest {
    @Test
    fun `fixed ranges end at the current instant`() {
        val resolved = HistoryRangeResolver.resolveFixed(
            preset = HistoryPeriodPreset.DAYS_7,
            nowEpochMillis = NOW,
            displayTimeZoneId = "UTC",
        ) as HistoryRangeResolution.Resolved

        assertEquals(NOW, resolved.range.endExclusiveEpochMillis)
        assertEquals(NOW - 7L * DAY_MILLIS, resolved.range.startEpochMillis)
        assertTrue(resolved.range.includesPartialLatestDay)
    }

    @Test
    fun `custom range uses completed local days across daylight saving`() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 3, 14)
        val now = LocalDate.of(2026, 3, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        val resolved = HistoryRangeResolver.resolveCustom(
            startDateEpochDay = start.toEpochDay(),
            endDateEpochDay = end.toEpochDay(),
            nowEpochMillis = now,
            displayTimeZoneId = zone.id,
        ) as HistoryRangeResolution.Resolved

        assertEquals(14, resolved.range.calendarDayCount)
        assertEquals(14L * DAY_MILLIS - HOUR_MILLIS, resolved.range.durationMillis)
        assertEquals(
            start.atStartOfDay(zone).toInstant().toEpochMilli(),
            resolved.range.startEpochMillis,
        )
        assertEquals(
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            resolved.range.endExclusiveEpochMillis,
        )
    }

    @Test
    fun `custom range includes fall back hour and supports exactly ninety days`() {
        val zone = ZoneId.of("America/New_York")
        val fallStart = LocalDate.of(2026, 10, 25)
        val fallEnd = fallStart.plusDays(13)
        val now = LocalDate.of(2027, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val fallRange = HistoryRangeResolver.resolveCustom(
            fallStart.toEpochDay(),
            fallEnd.toEpochDay(),
            now,
            zone.id,
        ) as HistoryRangeResolution.Resolved
        val ninetyStart = LocalDate.of(2026, 9, 1)

        assertEquals(14L * DAY_MILLIS + HOUR_MILLIS, fallRange.range.durationMillis)
        assertTrue(
            HistoryRangeResolver.resolveCustom(
                ninetyStart.toEpochDay(),
                ninetyStart.plusDays(89).toEpochDay(),
                now,
                zone.id,
            ) is HistoryRangeResolution.Resolved,
        )
        assertTrue(
            HistoryRangeResolver.resolveCustom(
                ninetyStart.toEpochDay(),
                ninetyStart.plusDays(90).toEpochDay(),
                now,
                zone.id,
            ) is HistoryRangeResolution.Invalid,
        )
    }

    @Test
    fun `custom range rejects current day and unsupported durations`() {
        val today = Instant.ofEpochMilli(NOW).atZone(ZoneId.of("UTC")).toLocalDate()

        assertTrue(
            HistoryRangeResolver.resolveCustom(
                today.minusDays(13).toEpochDay(),
                today.toEpochDay(),
                NOW,
                "UTC",
            ) is HistoryRangeResolution.Invalid,
        )
        assertTrue(
            HistoryRangeResolver.resolveCustom(
                today.minusDays(12).toEpochDay(),
                today.minusDays(1).toEpochDay(),
                NOW,
                "UTC",
            ) is HistoryRangeResolution.Invalid,
        )
    }

    @Test
    fun `chart ordering is deterministic and long gaps stay disconnected`() {
        val range = range(HistoryPeriodPreset.HOURS_24, start = 0L, end = 4L * HOUR_MILLIS)
        val readings = listOf(
            reading("d", 2L * HOUR_MILLIS + 5L * MINUTE_MILLIS, 170),
            reading("a", 0L, 100),
            reading("c", 2L * HOUR_MILLIS, 160),
            reading("b", 5L * MINUTE_MILLIS, 110),
            reading("other", 10L * MINUTE_MILLIS, 400, source = "other-source"),
        )

        val first = GlucoseTrendSeriesBuilder.build(readings, SOURCE, range)
        val second = GlucoseTrendSeriesBuilder.build(readings.shuffled(Random(7)), SOURCE, range)

        assertEquals(first, second)
        assertEquals(2, first.segments.size)
        assertTrue(first.coverage.gapCount >= 2)
        assertTrue(first.segments.all { it.buckets.all { bucket -> bucket.maximumMgDl < 400.0 } })
    }

    @Test
    fun `aggregated chart preserves extrema and stays bounded`() {
        val start = 0L
        val end = 90L * DAY_MILLIS
        val range = range(HistoryPeriodPreset.DAYS_90, start, end)
        val readings = readingsEveryFiveMinutes(start, end) { index ->
            when (index) {
                10 -> 45
                11 -> 320
                else -> 140
            }
        }

        val result = GlucoseTrendSeriesBuilder.build(readings, SOURCE, range)
        val buckets = result.segments.flatMap { it.buckets }

        assertTrue(buckets.size <= 400)
        assertEquals(45.0, buckets.minOf { it.minimumMgDl }, 0.001)
        assertEquals(320.0, buckets.maxOf { it.maximumMgDl }, 0.001)
    }

    @Test
    fun `sparse singleton segments remain bounded and preserve global extrema`() {
        val range = range(HistoryPeriodPreset.DAYS_90, 0L, 90L * DAY_MILLIS)
        val readings = buildList {
            var timestamp = 0L
            var index = 0
            while (timestamp < range.endExclusiveEpochMillis) {
                val value = when (index) {
                    17 -> 45
                    4_017 -> 320
                    else -> 140
                }
                add(reading("s-$index", timestamp, value))
                timestamp += 21L * MINUTE_MILLIS
                index += 1
            }
        }

        val result = GlucoseTrendSeriesBuilder.build(readings, SOURCE, range)
        val buckets = result.segments.flatMap { it.buckets }

        assertTrue(buckets.size <= 400)
        assertEquals(45.0, buckets.minOf { it.minimumMgDl }, 0.001)
        assertEquals(320.0, buckets.maxOf { it.maximumMgDl }, 0.001)
        assertTrue(result.segments.all { it.buckets.size == 1 })
    }

    @Test
    fun `chart preparation cooperatively observes cancellation`() {
        val range = range(HistoryPeriodPreset.DAYS_90, 0L, 90L * DAY_MILLIS)
        val readings = readingsEveryFiveMinutes(0L, range.endExclusiveEpochMillis) { 140 }
        var checks = 0

        assertThrows(CancellationException::class.java) {
            GlucoseTrendSeriesBuilder.build(
                readings = readings,
                sourceId = SOURCE,
                range = range,
                cancellationCheck = {
                    checks += 1
                    if (checks >= 100) throw CancellationException("superseded")
                },
            )
        }
        assertTrue(checks >= 100)
    }

    @Test
    fun `selected period GMI enforces duration and coverage`() {
        val shortRange = range(
            HistoryPeriodPreset.DAYS_7,
            start = 0L,
            end = 7L * DAY_MILLIS,
        )
        val fourteenDayRange = range(
            HistoryPeriodPreset.DAYS_14,
            start = 0L,
            end = 14L * DAY_MILLIS,
        )
        val fullReadings = readingsEveryFiveMinutes(0L, fourteenDayRange.endExclusiveEpochMillis) {
            140
        }

        val short = SelectedPeriodGmiCalculator.calculate(
            fullReadings,
            SOURCE,
            shortRange,
            GlycemicPlannerSettings(),
            70,
            180,
        )
        val available = SelectedPeriodGmiCalculator.calculate(
            fullReadings,
            SOURCE,
            fourteenDayRange,
            GlycemicPlannerSettings(),
            70,
            180,
        )
        val sparse = SelectedPeriodGmiCalculator.calculate(
            fullReadings.take(fullReadings.size / 2),
            SOURCE,
            fourteenDayRange,
            GlycemicPlannerSettings(),
            70,
            180,
        )

        assertEquals(SelectedPeriodGmiAvailability.INSUFFICIENT_DURATION, short.availability)
        assertEquals(SelectedPeriodGmiAvailability.AVAILABLE, available.availability)
        assertNotNull(available.gmiPercent)
        assertEquals(SelectedPeriodGmiAvailability.INSUFFICIENT_COVERAGE, sparse.availability)
    }

    @Test
    fun `low glucose exposure qualifies descriptive GMI without hiding it`() {
        val range = range(HistoryPeriodPreset.DAYS_14, 0L, 14L * DAY_MILLIS)
        val result = SelectedPeriodGmiCalculator.calculate(
            readings = readingsEveryFiveMinutes(0L, range.endExclusiveEpochMillis) { 60 },
            sourceId = SOURCE,
            range = range,
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(SelectedPeriodGmiAvailability.AVAILABLE, result.availability)
        assertNotNull(result.gmiPercent)
        assertTrue(SelectedPeriodGmiQualifier.LOW_GLUCOSE_EXPOSURE in result.qualifiers)
    }

    @Test
    fun `selected period GMI includes trailing missing time in largest gap`() {
        val range = range(HistoryPeriodPreset.DAYS_14, 0L, 14L * DAY_MILLIS)
        val tenDays = readingsEveryFiveMinutes(0L, 10L * DAY_MILLIS) { 140 }

        val result = SelectedPeriodGmiCalculator.calculate(
            readings = tenDays,
            sourceId = SOURCE,
            range = range,
            plannerSettings = GlycemicPlannerSettings(),
            targetLowerMgDl = 70,
            targetUpperMgDl = 180,
        )

        assertEquals(SelectedPeriodGmiAvailability.AVAILABLE, result.availability)
        assertEquals(4L * DAY_MILLIS, result.largestGapMillis)
        assertTrue(SelectedPeriodGmiQualifier.CONCENTRATED_GAPS in result.qualifiers)
    }

    @Test
    fun `selected period GMI accepts exact seventy percent coverage`() {
        val range = range(HistoryPeriodPreset.DAYS_14, 0L, 14L * DAY_MILLIS)
        val coveredUntil = (range.durationMillis * 70L) / 100L
        val readings = readingsEveryFiveMinutes(0L, coveredUntil - MINUTE_MILLIS) { 140 } +
            reading("exact-boundary", coveredUntil, 140)

        val result = SelectedPeriodGmiCalculator.calculate(
            readings,
            SOURCE,
            range,
            GlycemicPlannerSettings(),
            70,
            180,
        )

        assertEquals(SelectedPeriodGmiAvailability.AVAILABLE, result.availability)
        assertEquals(70.0, result.coveragePercent, 0.000_001)
    }

    @Test
    fun `selected period mean is time weighted rather than row weighted`() {
        val range = range(HistoryPeriodPreset.DAYS_14, 0L, 14L * DAY_MILLIS)
        val readings = buildList {
            var timestamp = 0L
            var index = 0
            while (timestamp <= 7L * DAY_MILLIS) {
                add(reading("early-$index", timestamp, 100))
                timestamp += 20L * MINUTE_MILLIS
                index += 1
            }
            timestamp = 7L * DAY_MILLIS + 5L * MINUTE_MILLIS
            while (timestamp <= range.endExclusiveEpochMillis) {
                add(reading("late-$index", timestamp, 200))
                timestamp += 5L * MINUTE_MILLIS
                index += 1
            }
        }

        val result = SelectedPeriodGmiCalculator.calculate(
            readings,
            SOURCE,
            range,
            GlycemicPlannerSettings(),
            70,
            180,
        )
        val rowMean = readings.map { it.valueMgDl }.average()

        assertEquals(SelectedPeriodGmiAvailability.AVAILABLE, result.availability)
        assertEquals(150.0, requireNotNull(result.timeWeightedMeanMgDl), 0.1)
        assertTrue(rowMean > 175.0)
    }

    @Test
    fun `selected period GMI rejects mixed source input defensively`() {
        val range = range(HistoryPeriodPreset.DAYS_14, 0L, 14L * DAY_MILLIS)
        val readings = readingsEveryFiveMinutes(0L, range.endExclusiveEpochMillis) { 140 }
            .toMutableList()
            .also { it += reading("foreign", DAY_MILLIS, 140, source = "other-source") }

        val result = SelectedPeriodGmiCalculator.calculate(
            readings,
            SOURCE,
            range,
            GlycemicPlannerSettings(),
            70,
            180,
        )

        assertEquals(SelectedPeriodGmiAvailability.SOURCE_DISCONTINUITY, result.availability)
    }

    @Test
    fun `selected period GMI propagates cooperative cancellation`() {
        val range = range(HistoryPeriodPreset.DAYS_14, 0L, 14L * DAY_MILLIS)
        val readings = readingsEveryFiveMinutes(0L, range.endExclusiveEpochMillis) { 140 }

        assertThrows(CancellationException::class.java) {
            SelectedPeriodGmiCalculator.calculate(
                readings = readings,
                sourceId = SOURCE,
                range = range,
                plannerSettings = GlycemicPlannerSettings(),
                targetLowerMgDl = 70,
                targetUpperMgDl = 180,
                cancellationCheck = { throw CancellationException("superseded") },
            )
        }
    }

    private fun range(preset: HistoryPeriodPreset, start: Long, end: Long) = HistoryRange(
        preset = preset,
        startEpochMillis = start,
        endExclusiveEpochMillis = end,
        displayTimeZoneId = "UTC",
        calendarDayCount = when (preset) {
            HistoryPeriodPreset.HOURS_24 -> 1
            HistoryPeriodPreset.DAYS_7 -> 7
            HistoryPeriodPreset.DAYS_14 -> 14
            HistoryPeriodPreset.DAYS_30 -> 30
            HistoryPeriodPreset.DAYS_90 -> 90
            HistoryPeriodPreset.CUSTOM -> 14
        },
        includesPartialLatestDay = preset != HistoryPeriodPreset.CUSTOM,
    )

    private fun readingsEveryFiveMinutes(
        start: Long,
        endInclusive: Long,
        value: (Int) -> Int,
    ): List<GlucoseReading> = buildList {
        var timestamp = start
        var index = 0
        while (timestamp <= endInclusive) {
            add(reading("r-$index", timestamp, value(index)))
            timestamp += 5L * MINUTE_MILLIS
            index += 1
        }
    }

    private fun reading(
        id: String,
        measuredAt: Long,
        value: Int,
        source: String = SOURCE,
    ) = GlucoseReading(
        id = id,
        valueMgDl = value,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = null,
        rateMgDlPerMinute = null,
        measuredAtEpochMillis = measuredAt,
        receivedAtEpochMillis = measuredAt,
        sourceId = source,
    )

    private companion object {
        const val SOURCE = "nightscout:test"
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60L * MINUTE_MILLIS
        const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val NOW = 1_800_000_000_000L
    }
}
