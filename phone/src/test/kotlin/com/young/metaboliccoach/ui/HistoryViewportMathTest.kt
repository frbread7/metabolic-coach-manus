package com.young.metaboliccoach.ui

import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryViewportMathTest {
    @Test
    fun `full and reset preserve exact half open selected range`() {
        val selected = range(0L, DAY_MILLIS)
        val full = HistoryViewportMath.full(selected)

        assertEquals(HistoryViewport(0L, DAY_MILLIS), full)
        assertEquals(
            full,
            HistoryViewportMath.reset(
                HistoryViewport(6L * HOUR_MILLIS, 18L * HOUR_MILLIS),
                selected,
            ),
        )
        assertEquals(DAY_MILLIS, full?.durationMillis)
    }

    @Test
    fun `two times zoom preserves center left and right focal instants`() {
        val selected = range(0L, DAY_MILLIS)
        val full = checkNotNull(HistoryViewportMath.full(selected))

        assertEquals(
            HistoryViewport(6L * HOUR_MILLIS, 18L * HOUR_MILLIS),
            HistoryViewportMath.zoom(full, selected, 2.0, 0.5),
        )
        assertEquals(
            HistoryViewport(0L, 12L * HOUR_MILLIS),
            HistoryViewportMath.zoom(full, selected, 2.0, 0.0),
        )
        assertEquals(
            HistoryViewport(12L * HOUR_MILLIS, DAY_MILLIS),
            HistoryViewportMath.zoom(full, selected, 2.0, 1.0),
        )
    }

    @Test
    fun `zoom clamps at thirty minutes and full selected duration`() {
        val selected = range(0L, DAY_MILLIS)
        val full = checkNotNull(HistoryViewportMath.full(selected))
        val minimum = HistoryViewportMath.zoom(full, selected, 10_000.0, 0.5)

        assertEquals(HistoryViewportMath.MINIMUM_DURATION_MILLIS, minimum.durationMillis)
        assertEquals(full, HistoryViewportMath.zoomOut(full, selected))
        assertEquals(minimum, HistoryViewportMath.zoomIn(minimum, selected))
    }

    @Test
    fun `pan direction and both selected boundaries clamp`() {
        val selected = range(0L, DAY_MILLIS)
        val middle = HistoryViewport(6L * HOUR_MILLIS, 18L * HOUR_MILLIS)

        assertEquals(
            HistoryViewport(9L * HOUR_MILLIS, 21L * HOUR_MILLIS),
            HistoryViewportMath.pan(middle, selected, -25.0, 100.0),
        )
        assertEquals(
            HistoryViewport(3L * HOUR_MILLIS, 15L * HOUR_MILLIS),
            HistoryViewportMath.pan(middle, selected, 25.0, 100.0),
        )
        assertEquals(
            HistoryViewport(12L * HOUR_MILLIS, DAY_MILLIS),
            HistoryViewportMath.pan(middle, selected, -10_000.0, 100.0),
        )
        assertEquals(
            HistoryViewport(0L, 12L * HOUR_MILLIS),
            HistoryViewportMath.pan(middle, selected, 10_000.0, 100.0),
        )
    }

    @Test
    fun `button zoom uses deterministic half and double durations`() {
        val selected = range(0L, DAY_MILLIS)
        val full = checkNotNull(HistoryViewportMath.full(selected))
        val zoomed = HistoryViewportMath.zoomIn(full, selected)

        assertEquals(12L * HOUR_MILLIS, zoomed.durationMillis)
        assertEquals(full, HistoryViewportMath.zoomOut(zoomed, selected))
    }

    @Test
    fun `epoch math remains elapsed time across daylight saving transition`() {
        val zone = ZoneId.of("America/New_York")
        val start = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli()
        val selected = range(start, start + DAY_MILLIS, zone.id)
        val full = checkNotNull(HistoryViewportMath.full(selected))

        val zoomed = HistoryViewportMath.zoomIn(full, selected)

        assertEquals(12L * HOUR_MILLIS, zoomed.durationMillis)
        assertEquals(zone.id, selected.displayTimeZoneId)
    }

    @Test
    fun `invalid non finite zero width and overflow inputs fail safely`() {
        val selected = range(0L, DAY_MILLIS)
        val full = checkNotNull(HistoryViewportMath.full(selected))

        assertEquals(full, HistoryViewportMath.zoom(full, selected, Double.NaN, 0.5))
        assertEquals(full, HistoryViewportMath.zoom(full, selected, 2.0, Double.POSITIVE_INFINITY))
        assertEquals(full, HistoryViewportMath.pan(full, selected, 10.0, 0.0))
        assertEquals(full, HistoryViewportMath.pan(full, selected, Double.NaN, 100.0))
        assertNull(HistoryViewportMath.full(range(Long.MIN_VALUE, Long.MAX_VALUE)))
        assertEquals(0L, HistoryViewport(Long.MIN_VALUE, Long.MAX_VALUE).durationMillis)
    }

    @Test
    fun `horizontal classifier leaves vertical and ambiguous gestures to parent scroll`() {
        assertTrue(HistoryViewportMath.isHorizontalIntent(10.0, 2.0))
        assertFalse(HistoryViewportMath.isHorizontalIntent(2.0, 10.0))
        assertFalse(HistoryViewportMath.isHorizontalIntent(10.0, 9.0))
        assertFalse(HistoryViewportMath.isHorizontalIntent(5.0, 5.0))
        assertFalse(HistoryViewportMath.isHorizontalIntent(Double.NaN, 0.0))
    }

    @Test
    fun `arbitrary focal zoom and compound transforms remain deterministic and bounded`() {
        val selected = range(0L, DAY_MILLIS)
        val full = checkNotNull(HistoryViewportMath.full(selected))
        val focalFraction = 0.37
        val focalBefore = full.startEpochMillis + full.durationMillis * focalFraction
        val zoomed = HistoryViewportMath.zoom(full, selected, 1.7, focalFraction)
        val focalAfter = zoomed.startEpochMillis + zoomed.durationMillis * focalFraction

        assertTrue(abs(focalBefore - focalAfter) <= 1.0)

        val panned = HistoryViewportMath.pan(zoomed, selected, -17.0, 300.0)
        assertTrue(panned.startEpochMillis >= selected.startEpochMillis)
        assertTrue(panned.endExclusiveEpochMillis <= selected.endExclusiveEpochMillis)
        assertEquals(zoomed.durationMillis, panned.durationMillis)
        assertEquals(panned, HistoryViewportMath.pan(panned, selected, 0.0, 300.0))
    }

    private fun range(
        start: Long,
        endExclusive: Long,
        zoneId: String = "UTC",
    ) = HistoryRange(
        preset = HistoryPeriodPreset.HOURS_24,
        startEpochMillis = start,
        endExclusiveEpochMillis = endExclusive,
        displayTimeZoneId = zoneId,
        calendarDayCount = 1,
        includesPartialLatestDay = true,
    )

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
