package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseChartBucket
import com.young.metaboliccoach.core.model.GlucoseChartResult
import com.young.metaboliccoach.core.model.GlucoseChartSegment
import com.young.metaboliccoach.core.model.GlucoseChartStatus
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.HistoryCoverage
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.SelectedPeriodGmiAvailability
import com.young.metaboliccoach.core.model.SelectedPeriodGmiQualifier
import com.young.metaboliccoach.core.model.SelectedPeriodGmiResult
import com.young.metaboliccoach.core.model.GlycemicMetricsStatus
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.math.roundToInt

sealed interface HistoryRangeResolution {
    data class Resolved(val range: HistoryRange) : HistoryRangeResolution
    data class Invalid(val detail: String) : HistoryRangeResolution
}

/** Resolves UI date choices once into immutable UTC half-open query intervals. */
object HistoryRangeResolver {
    fun resolveFixed(
        preset: HistoryPeriodPreset,
        nowEpochMillis: Long,
        displayTimeZoneId: String,
    ): HistoryRangeResolution {
        val duration = preset.rollingDurationMillis
            ?: return HistoryRangeResolution.Invalid("Choose start and end dates for Custom.")
        if (nowEpochMillis <= duration) {
            return HistoryRangeResolution.Invalid("The current time cannot resolve this range.")
        }
        return runCatching { ZoneId.of(displayTimeZoneId) }
            .fold(
                onSuccess = {
                    HistoryRangeResolution.Resolved(
                        HistoryRange(
                            preset = preset,
                            startEpochMillis = nowEpochMillis - duration,
                            endExclusiveEpochMillis = nowEpochMillis,
                            displayTimeZoneId = displayTimeZoneId,
                            calendarDayCount = preset.calendarDayCount(),
                            includesPartialLatestDay = true,
                        ),
                    )
                },
                onFailure = {
                    HistoryRangeResolution.Invalid("The display time zone is not available.")
                },
            )
    }

    fun resolveCustom(
        startDateEpochDay: Long,
        endDateEpochDay: Long,
        nowEpochMillis: Long,
        displayTimeZoneId: String,
    ): HistoryRangeResolution {
        return try {
            val zoneId = ZoneId.of(displayTimeZoneId)
            val startDate = LocalDate.ofEpochDay(startDateEpochDay)
            val endDate = LocalDate.ofEpochDay(endDateEpochDay)
            if (endDate.isBefore(startDate)) {
                return HistoryRangeResolution.Invalid(
                    "The end date must not be before the start date.",
                )
            }
            val calendarDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            if (calendarDays !in MINIMUM_CUSTOM_DAYS..MAXIMUM_CUSTOM_DAYS) {
                return HistoryRangeResolution.Invalid(
                    "Custom history must contain 14 to 90 completed calendar days.",
                )
            }
            val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
            if (!endDate.isBefore(today)) {
                return HistoryRangeResolution.Invalid(
                    "Custom history must end no later than the previous local day.",
                )
            }
            val start = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endExclusive = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            HistoryRangeResolution.Resolved(
                HistoryRange(
                    preset = HistoryPeriodPreset.CUSTOM,
                    startEpochMillis = start,
                    endExclusiveEpochMillis = endExclusive,
                    displayTimeZoneId = displayTimeZoneId,
                    calendarDayCount = calendarDays,
                    includesPartialLatestDay = false,
                ),
            )
        } catch (_: DateTimeException) {
            HistoryRangeResolution.Invalid("The custom dates or display time zone are invalid.")
        } catch (_: ArithmeticException) {
            HistoryRangeResolution.Invalid("The custom date range is outside the supported range.")
        }
    }

    private fun HistoryPeriodPreset.calendarDayCount(): Int = when (this) {
        HistoryPeriodPreset.HOURS_6,
        HistoryPeriodPreset.HOURS_12,
        HistoryPeriodPreset.HOURS_24 -> 1
        HistoryPeriodPreset.DAYS_7 -> 7
        HistoryPeriodPreset.DAYS_14 -> 14
        HistoryPeriodPreset.DAYS_30 -> 30
        HistoryPeriodPreset.DAYS_90 -> 90
        HistoryPeriodPreset.CUSTOM -> 0
    }

    const val MINIMUM_CUSTOM_DAYS = 14
    const val MAXIMUM_CUSTOM_DAYS = 90
}

/** Pure deterministic chart preparation. No provider, repository, or UI dependency is allowed. */
object GlucoseTrendSeriesBuilder {
    fun build(
        readings: List<GlucoseReading>,
        sourceId: String,
        range: HistoryRange,
        maxInterpolationGapMinutes: Long =
            GlycemicGoalPlanner.DEFAULT_MAX_INTERPOLATION_GAP_MINUTES,
        cancellationCheck: () -> Unit = {},
    ): GlucoseChartResult = buildInternal(
        readings = readings,
        sourceId = sourceId,
        range = range,
        maxInterpolationGapMinutes = maxInterpolationGapMinutes,
        usePresetAggregation = true,
        cancellationCheck = cancellationCheck,
    )

    /**
     * Builds a chart for an interactive viewport from canonical raw readings. Parent-period fixed
     * aggregation must not leak into a zoomed viewport; adaptive aggregation is used only when the
     * visible raw point count exceeds the defensive render cap.
     */
    fun buildViewport(
        readings: List<GlucoseReading>,
        sourceId: String,
        range: HistoryRange,
        maxInterpolationGapMinutes: Long =
            GlycemicGoalPlanner.DEFAULT_MAX_INTERPOLATION_GAP_MINUTES,
        cancellationCheck: () -> Unit = {},
    ): GlucoseChartResult = buildInternal(
        readings = readings,
        sourceId = sourceId,
        range = range,
        maxInterpolationGapMinutes = maxInterpolationGapMinutes,
        usePresetAggregation = false,
        cancellationCheck = cancellationCheck,
    )

    private fun buildInternal(
        readings: List<GlucoseReading>,
        sourceId: String,
        range: HistoryRange,
        maxInterpolationGapMinutes: Long,
        usePresetAggregation: Boolean,
        cancellationCheck: () -> Unit,
    ): GlucoseChartResult {
        cancellationCheck()
        if (sourceId.isBlank() || range.durationMillis <= 0L || maxInterpolationGapMinutes <= 0L) {
            return emptyResult(
                sourceId = sourceId,
                range = range,
                status = GlucoseChartStatus.INVALID_RANGE,
                detail = "The selected source or history range is invalid.",
            )
        }
        val maxGapMillis = maxInterpolationGapMinutes * MINUTE_MILLIS
        val points = readings
            .asSequence()
            .onEach { cancellationCheck() }
            .filter { it.sourceId == sourceId }
            .filter {
                it.measuredAtEpochMillis in
                    (range.startEpochMillis - maxGapMillis)..range.endExclusiveEpochMillis
            }
            .groupBy(GlucoseReading::measuredAtEpochMillis)
            .values
            .mapNotNull { sameTimestamp -> sameTimestamp.maxByOrNull(GlucoseReading::id) }
            .sortedWith(
                compareBy<GlucoseReading>(GlucoseReading::measuredAtEpochMillis)
                    .thenBy(GlucoseReading::id),
            )
        val inRangePoints = points.filter {
            it.measuredAtEpochMillis >= range.startEpochMillis &&
                it.measuredAtEpochMillis < range.endExclusiveEpochMillis
        }
        if (points.isEmpty() || (inRangePoints.isEmpty() && points.size < 2)) {
            return emptyResult(
                sourceId = sourceId,
                range = range,
                status = GlucoseChartStatus.NO_DATA,
                detail = "No locally stored readings are available for this period.",
            )
        }

        val preparedSegments = points
            .splitByGap(maxGapMillis)
            .mapNotNull { group ->
                cancellationCheck()
                group.prepare(range)
            }
        if (preparedSegments.isEmpty()) {
            return emptyResult(
                sourceId = sourceId,
                range = range,
                status = GlucoseChartStatus.NO_DATA,
                detail = "No locally stored readings are available for this period.",
            )
        }

        val allIntervals = preparedSegments.flatMap(PreparedSegment::intervals)
        val coverage = calculateCoverage(allIntervals, range)
        val requestedBucketMillis = range.preset.aggregationBucketMillis
            .takeIf { usePresetAggregation }
        val rawPointCount = preparedSegments.sumOf { it.displayPoints.size }
        val bucketMillis = when {
            requestedBucketMillis != null -> requestedBucketMillis
            rawPointCount <= MAXIMUM_RENDER_BUCKETS -> null
            else -> adaptiveBucketMillis(range.durationMillis)
        }
        val chartSegments = preparedSegments.mapNotNull { segment ->
            val buckets = if (bucketMillis == null) {
                segment.displayPoints
                    .let { displayPoints ->
                        if (usePresetAggregation) {
                            displayPoints
                        } else {
                            displayPoints.filter {
                                it.epochMillis >= range.startEpochMillis &&
                                    it.epochMillis < range.endExclusiveEpochMillis
                            }
                        }
                    }
                    .map(::pointBucket)
            } else {
                aggregate(segment, range, bucketMillis, cancellationCheck)
            }
            buckets.takeIf(List<GlucoseChartBucket>::isNotEmpty)?.let {
                GlucoseChartSegment(
                    buckets = it,
                    startsAfterGap = it.first().startEpochMillis > range.startEpochMillis,
                    endsBeforeGap = it.last().endExclusiveEpochMillis <
                        range.endExclusiveEpochMillis,
                )
            }
        }
        val boundedChartSegments = boundChartSegments(chartSegments)
        if (boundedChartSegments.isEmpty()) {
            return emptyResult(
                sourceId = sourceId,
                range = range,
                status = GlucoseChartStatus.NO_DATA,
                detail = "No locally stored readings are available for this period.",
            )
        }
        return GlucoseChartResult(
            sourceId = sourceId,
            range = range,
            segments = boundedChartSegments,
            coverage = coverage,
            latestMeasurementAtEpochMillis = inRangePoints.maxOfOrNull {
                it.measuredAtEpochMillis
            },
            status = GlucoseChartStatus.AVAILABLE,
            detail = if (coverage.coveragePercent >= GlycemicGoalPlanner.MINIMUM_COVERAGE_PERCENT) {
                "Local history coverage is sufficient for this period."
            } else {
                "This period contains incomplete local history; gaps remain disconnected."
            },
        )
    }

    private fun List<GlucoseReading>.splitByGap(maxGapMillis: Long): List<List<GlucoseReading>> {
        if (isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<GlucoseReading>>()
        forEach { point ->
            val current = groups.lastOrNull()
            if (
                current == null ||
                point.measuredAtEpochMillis - current.last().measuredAtEpochMillis > maxGapMillis
            ) {
                groups += mutableListOf(point)
            } else {
                current += point
            }
        }
        return groups
    }

    private fun List<GlucoseReading>.prepare(range: HistoryRange): PreparedSegment? {
        if (size == 1) {
            val point = single()
            return point.takeIf {
                it.measuredAtEpochMillis >= range.startEpochMillis &&
                    it.measuredAtEpochMillis < range.endExclusiveEpochMillis
            }?.let {
                PreparedSegment(
                    intervals = emptyList(),
                    displayPoints = listOf(
                        TimedValue(it.measuredAtEpochMillis, it.valueMgDl.toDouble()),
                    ),
                )
            }
        }
        val intervals = zipWithNext().mapNotNull { (first, second) ->
            val start = maxOf(first.measuredAtEpochMillis, range.startEpochMillis)
            val end = minOf(second.measuredAtEpochMillis, range.endExclusiveEpochMillis)
            if (end <= start) return@mapNotNull null
            LinearInterval(
                startEpochMillis = start,
                endExclusiveEpochMillis = end,
                startValueMgDl = interpolate(first, second, start),
                endValueMgDl = interpolate(first, second, end),
            )
        }
        if (intervals.isEmpty()) return null
        val displayPoints = buildList {
            add(TimedValue(intervals.first().startEpochMillis, intervals.first().startValueMgDl))
            intervals.forEach { interval ->
                add(TimedValue(interval.endExclusiveEpochMillis, interval.endValueMgDl))
            }
        }.distinctBy(TimedValue::epochMillis)
        return PreparedSegment(intervals, displayPoints)
    }

    private fun aggregate(
        segment: PreparedSegment,
        range: HistoryRange,
        bucketMillis: Long,
        cancellationCheck: () -> Unit,
    ): List<GlucoseChartBucket> {
        if (segment.intervals.isEmpty()) return segment.displayPoints.map(::pointBucket)
        val buckets = sortedMapOf<Long, MutableBucket>()
        segment.intervals.forEach { interval ->
            cancellationCheck()
            var pieceStart = interval.startEpochMillis
            while (pieceStart < interval.endExclusiveEpochMillis) {
                cancellationCheck()
                val index = (pieceStart - range.startEpochMillis) / bucketMillis
                val bucketStart = range.startEpochMillis + index * bucketMillis
                val bucketEnd = minOf(
                    bucketStart + bucketMillis,
                    range.endExclusiveEpochMillis,
                    interval.endExclusiveEpochMillis,
                )
                val startValue = interval.valueAt(pieceStart)
                val endValue = interval.valueAt(bucketEnd)
                buckets.getOrPut(bucketStart) {
                    MutableBucket(bucketStart, minOf(bucketStart + bucketMillis, range.endExclusiveEpochMillis))
                }.add(pieceStart, bucketEnd, startValue, endValue)
                pieceStart = bucketEnd
            }
        }
        return buckets.values.map(MutableBucket::toModel)
    }

    private fun calculateCoverage(
        intervals: List<LinearInterval>,
        range: HistoryRange,
    ): HistoryCoverage {
        val ordered = intervals.sortedBy(LinearInterval::startEpochMillis)
        var cursor = range.startEpochMillis
        var covered = 0L
        var gapCount = 0
        var largestGap = 0L
        ordered.forEach { interval ->
            val start = maxOf(interval.startEpochMillis, range.startEpochMillis)
            val end = minOf(interval.endExclusiveEpochMillis, range.endExclusiveEpochMillis)
            if (end <= start) return@forEach
            if (start > cursor) {
                val gap = start - cursor
                gapCount += 1
                largestGap = maxOf(largestGap, gap)
            }
            val effectiveStart = maxOf(start, cursor)
            if (end > effectiveStart) covered += end - effectiveStart
            cursor = maxOf(cursor, end)
        }
        if (cursor < range.endExclusiveEpochMillis) {
            val gap = range.endExclusiveEpochMillis - cursor
            gapCount += 1
            largestGap = maxOf(largestGap, gap)
        }
        val duration = range.durationMillis.coerceAtLeast(0L)
        return HistoryCoverage(
            requestedDurationMillis = duration,
            validDurationMillis = covered.coerceAtMost(duration),
            coveragePercent = if (duration > 0L) covered * 100.0 / duration else 0.0,
            gapCount = gapCount,
            largestGapMillis = largestGap,
        )
    }

    private fun adaptiveBucketMillis(durationMillis: Long): Long {
        val required = ceil(durationMillis.toDouble() / MAXIMUM_RENDER_BUCKETS).toLong()
        return ADAPTIVE_BUCKETS.firstOrNull { it >= required } ?: ADAPTIVE_BUCKETS.last()
    }

    /**
     * A pathological sparse series can contain more than 400 singleton segments, which cannot be
     * bucketed without falsely connecting gaps. Retain deterministic representatives as separate
     * segments, including the global first/last and extrema, and never exceed the render cap.
     */
    private fun boundChartSegments(
        segments: List<GlucoseChartSegment>,
    ): List<GlucoseChartSegment> {
        data class IndexedBucket(
            val segmentIndex: Int,
            val bucketIndex: Int,
            val bucket: GlucoseChartBucket,
        )

        val indexed = segments.flatMapIndexed { segmentIndex, segment ->
            segment.buckets.mapIndexed { bucketIndex, bucket ->
                IndexedBucket(segmentIndex, bucketIndex, bucket)
            }
        }
        if (indexed.size <= MAXIMUM_RENDER_BUCKETS) return segments

        val selectedIndices = linkedSetOf<Int>()
        selectedIndices += 0
        selectedIndices += indexed.lastIndex
        selectedIndices += indexed.indices.minBy { indexed[it].bucket.minimumMgDl }
        selectedIndices += indexed.indices.maxBy { indexed[it].bucket.maximumMgDl }
        for (slot in 0 until MAXIMUM_RENDER_BUCKETS) {
            if (selectedIndices.size >= MAXIMUM_RENDER_BUCKETS) break
            selectedIndices += (
                slot * indexed.lastIndex.toDouble() / (MAXIMUM_RENDER_BUCKETS - 1)
                ).roundToInt()
        }
        if (selectedIndices.size < MAXIMUM_RENDER_BUCKETS) {
            for (index in indexed.indices) {
                selectedIndices += index
                if (selectedIndices.size >= MAXIMUM_RENDER_BUCKETS) break
            }
        }
        val selected = selectedIndices.sorted().map(indexed::get).groupBy(IndexedBucket::segmentIndex)
        return selected.map { (segmentIndex, retained) ->
            segments[segmentIndex].copy(
                buckets = retained.sortedBy(IndexedBucket::bucketIndex).map(IndexedBucket::bucket),
            )
        }
    }

    private fun pointBucket(point: TimedValue) = GlucoseChartBucket(
        startEpochMillis = point.epochMillis,
        endExclusiveEpochMillis = point.epochMillis + 1L,
        firstMgDl = point.valueMgDl,
        lastMgDl = point.valueMgDl,
        minimumMgDl = point.valueMgDl,
        maximumMgDl = point.valueMgDl,
        timeWeightedMeanMgDl = point.valueMgDl,
        validDurationMillis = 0L,
    )

    private fun interpolate(
        first: GlucoseReading,
        second: GlucoseReading,
        atEpochMillis: Long,
    ): Double {
        val duration = second.measuredAtEpochMillis - first.measuredAtEpochMillis
        if (duration <= 0L) return first.valueMgDl.toDouble()
        val fraction = (atEpochMillis - first.measuredAtEpochMillis).toDouble() / duration
        return first.valueMgDl + (second.valueMgDl - first.valueMgDl) * fraction.coerceIn(0.0, 1.0)
    }

    private fun emptyResult(
        sourceId: String,
        range: HistoryRange,
        status: GlucoseChartStatus,
        detail: String,
    ) = GlucoseChartResult(
        sourceId = sourceId,
        range = range,
        segments = emptyList(),
        coverage = HistoryCoverage(range.durationMillis.coerceAtLeast(0L), 0L, 0.0, 0, 0L),
        latestMeasurementAtEpochMillis = null,
        status = status,
        detail = detail,
    )

    private data class TimedValue(val epochMillis: Long, val valueMgDl: Double)

    private data class LinearInterval(
        val startEpochMillis: Long,
        val endExclusiveEpochMillis: Long,
        val startValueMgDl: Double,
        val endValueMgDl: Double,
    ) {
        fun valueAt(epochMillis: Long): Double {
            val duration = endExclusiveEpochMillis - startEpochMillis
            if (duration <= 0L) return startValueMgDl
            val fraction = (epochMillis - startEpochMillis).toDouble() / duration
            return startValueMgDl +
                (endValueMgDl - startValueMgDl) * fraction.coerceIn(0.0, 1.0)
        }
    }

    private data class PreparedSegment(
        val intervals: List<LinearInterval>,
        val displayPoints: List<TimedValue>,
    )

    private class MutableBucket(
        private val bucketStart: Long,
        private val bucketEnd: Long,
    ) {
        private var firstTime = Long.MAX_VALUE
        private var lastTime = Long.MIN_VALUE
        private var first = 0.0
        private var last = 0.0
        private var minimum = Double.POSITIVE_INFINITY
        private var maximum = Double.NEGATIVE_INFINITY
        private var integral = 0.0
        private var validDuration = 0L

        fun add(start: Long, end: Long, startValue: Double, endValue: Double) {
            if (end <= start) return
            if (start < firstTime) {
                firstTime = start
                first = startValue
            }
            if (end >= lastTime) {
                lastTime = end
                last = endValue
            }
            minimum = minOf(minimum, startValue, endValue)
            maximum = maxOf(maximum, startValue, endValue)
            val duration = end - start
            integral += (startValue + endValue) / 2.0 * duration
            validDuration += duration
        }

        fun toModel() = GlucoseChartBucket(
            startEpochMillis = bucketStart,
            endExclusiveEpochMillis = bucketEnd,
            firstMgDl = first,
            lastMgDl = last,
            minimumMgDl = minimum,
            maximumMgDl = maximum,
            timeWeightedMeanMgDl = if (validDuration > 0L) integral / validDuration else first,
            validDurationMillis = validDuration,
        )
    }

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
    private const val MAXIMUM_RENDER_BUCKETS = 400
    private val ADAPTIVE_BUCKETS = longArrayOf(
        5L * MINUTE_MILLIS,
        15L * MINUTE_MILLIS,
        30L * MINUTE_MILLIS,
        HOUR_MILLIS,
        2L * HOUR_MILLIS,
        3L * HOUR_MILLIS,
        6L * HOUR_MILLIS,
    )
}

/** Descriptive selected-period GMI. It has no goal, coaching, notification, or Wear side effect. */
object SelectedPeriodGmiCalculator {
    fun calculate(
        readings: List<GlucoseReading>,
        sourceId: String,
        range: HistoryRange,
        plannerSettings: GlycemicPlannerSettings,
        targetLowerMgDl: Int,
        targetUpperMgDl: Int,
        cancellationCheck: () -> Unit = {},
    ): SelectedPeriodGmiResult {
        cancellationCheck()
        if (sourceId.isBlank() || range.durationMillis <= 0L) {
            return unavailable(
                sourceId,
                range,
                SelectedPeriodGmiAvailability.INVALID_RANGE,
                "The selected source or period is invalid.",
            )
        }
        if (!range.preset.gmiEligible || range.calendarDayCount < MINIMUM_GMI_DAYS) {
            return unavailable(
                sourceId,
                range,
                SelectedPeriodGmiAvailability.INSUFFICIENT_DURATION,
                "Selected-period GMI requires at least 14 days of CGM history.",
            )
        }
        if (range.calendarDayCount > MAXIMUM_GMI_DAYS) {
            return unavailable(
                sourceId,
                range,
                SelectedPeriodGmiAvailability.INVALID_RANGE,
                "Selected-period GMI supports at most 90 days.",
            )
        }
        val candidateReadings = readings.filter {
            it.measuredAtEpochMillis in
                (range.startEpochMillis - MAX_INTERPOLATION_LEAD_MILLIS)..
                    range.endExclusiveEpochMillis
        }
        if (candidateReadings.any { it.sourceId != sourceId }) {
            return unavailable(
                sourceId,
                range,
                SelectedPeriodGmiAvailability.SOURCE_DISCONTINUITY,
                "The selected period cannot combine glucose sources.",
            )
        }
        val exactSourceReadings = candidateReadings.filter { it.sourceId == sourceId }
        if (exactSourceReadings.isEmpty()) {
            return unavailable(
                sourceId,
                range,
                SelectedPeriodGmiAvailability.NO_DATA,
                "No locally stored readings are available for this period.",
            )
        }
        val metrics = try {
            GlycemicGoalPlanner.calculateSelectedPeriodMetrics(
                readings = exactSourceReadings,
                rangeStartEpochMillis = range.startEpochMillis,
                rangeEndExclusiveEpochMillis = range.endExclusiveEpochMillis,
                targetLowerMgDl = targetLowerMgDl,
                targetUpperMgDl = targetUpperMgDl,
                lowGlucoseThresholdMgDl = plannerSettings.lowGlucoseThresholdMgDl,
                veryLowGlucoseThresholdMgDl = plannerSettings.veryLowGlucoseThresholdMgDl,
                cancellationCheck = cancellationCheck,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unavailable(
                sourceId,
                range,
                SelectedPeriodGmiAvailability.CALCULATION_ERROR,
                "Selected-period GMI could not be calculated.",
            )
        }
        val availability = when (metrics.status) {
            GlycemicMetricsStatus.AVAILABLE -> SelectedPeriodGmiAvailability.AVAILABLE
            GlycemicMetricsStatus.SOURCE_DISCONTINUITY ->
                SelectedPeriodGmiAvailability.SOURCE_DISCONTINUITY
            GlycemicMetricsStatus.INVALID_INPUT -> SelectedPeriodGmiAvailability.INVALID_RANGE
            GlycemicMetricsStatus.INSUFFICIENT_DATA -> if (metrics.sampleCount < 2) {
                SelectedPeriodGmiAvailability.NO_DATA
            } else {
                SelectedPeriodGmiAvailability.INSUFFICIENT_COVERAGE
            }
        }
        val boundaryAwareLargestGapMillis = maxOf(
            metrics.largestGapMillis,
            GlucoseTrendSeriesBuilder.build(
                readings = exactSourceReadings,
                sourceId = sourceId,
                range = range,
                cancellationCheck = cancellationCheck,
            ).coverage.largestGapMillis,
        )
        val qualifiers = buildSet {
            if (range.includesPartialLatestDay) add(SelectedPeriodGmiQualifier.PARTIAL_LATEST_DAY)
            if (boundaryAwareLargestGapMillis >= CONCENTRATED_GAP_MILLIS) {
                add(SelectedPeriodGmiQualifier.CONCENTRATED_GAPS)
            }
            if (
                (metrics.timeBelowRangePercent ?: 0.0) >
                    plannerSettings.maximumLowGlucosePercent ||
                (metrics.timeVeryLowPercent ?: 0.0) >
                    plannerSettings.maximumVeryLowGlucosePercent
            ) {
                add(SelectedPeriodGmiQualifier.LOW_GLUCOSE_EXPOSURE)
            }
        }
        return SelectedPeriodGmiResult(
            sourceId = sourceId,
            range = range,
            availability = availability,
            qualifiers = qualifiers,
            gmiPercent = metrics.gmiPercent.takeIf {
                availability == SelectedPeriodGmiAvailability.AVAILABLE
            },
            timeWeightedMeanMgDl = metrics.meanGlucoseMgDl,
            coveragePercent = metrics.coveragePercent,
            timeInRangePercent = metrics.timeInRangePercent,
            timeBelowRangePercent = metrics.timeBelowRangePercent,
            timeVeryLowPercent = metrics.timeVeryLowPercent,
            missingDurationMillis = metrics.missingDurationMillis,
            largestGapMillis = boundaryAwareLargestGapMillis,
            detail = when (availability) {
                SelectedPeriodGmiAvailability.AVAILABLE ->
                    "Selected-period GMI is available from sufficiently covered local CGM history."
                SelectedPeriodGmiAvailability.INSUFFICIENT_COVERAGE ->
                    "At least 70% valid-time coverage is required for selected-period GMI."
                SelectedPeriodGmiAvailability.SOURCE_DISCONTINUITY ->
                    "The selected period cannot combine glucose sources."
                SelectedPeriodGmiAvailability.NO_DATA ->
                    "At least two readings with covered time are required."
                SelectedPeriodGmiAvailability.INVALID_RANGE ->
                    "The selected period is invalid."
                SelectedPeriodGmiAvailability.INSUFFICIENT_DURATION ->
                    "Selected-period GMI requires at least 14 days of CGM history."
                SelectedPeriodGmiAvailability.CALCULATION_ERROR ->
                    "Selected-period GMI could not be calculated."
            },
        )
    }

    private fun unavailable(
        sourceId: String,
        range: HistoryRange,
        availability: SelectedPeriodGmiAvailability,
        detail: String,
    ) = SelectedPeriodGmiResult(
        sourceId = sourceId,
        range = range,
        availability = availability,
        missingDurationMillis = range.durationMillis.coerceAtLeast(0L),
        detail = detail,
    )

    private const val MINIMUM_GMI_DAYS = 14
    private const val MAXIMUM_GMI_DAYS = 90
    private const val HOUR_MILLIS = 60L * 60L * 1_000L
    private const val CONCENTRATED_GAP_MILLIS = 6L * HOUR_MILLIS
    private const val MAX_INTERPOLATION_LEAD_MILLIS =
        GlycemicGoalPlanner.DEFAULT_MAX_INTERPOLATION_GAP_MINUTES * 60_000L
}
