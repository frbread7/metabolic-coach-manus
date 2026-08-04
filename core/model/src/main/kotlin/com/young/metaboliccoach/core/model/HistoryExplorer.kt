package com.young.metaboliccoach.core.model

/** Phone-only, read-only local-history windows. CUSTOM is resolved from local calendar dates. */
enum class HistoryPeriodPreset(
    val label: String,
    val rollingDurationMillis: Long?,
    val aggregationBucketMillis: Long?,
    val gmiEligible: Boolean,
) {
    HOURS_24("24 hours", 24L * HOUR_MILLIS, null, false),
    DAYS_7("7 days", 7L * DAY_MILLIS, 30L * MINUTE_MILLIS, false),
    DAYS_14("14 days", 14L * DAY_MILLIS, HOUR_MILLIS, true),
    DAYS_30("30 days", 30L * DAY_MILLIS, 2L * HOUR_MILLIS, true),
    DAYS_90("90 days", 90L * DAY_MILLIS, 6L * HOUR_MILLIS, true),
    CUSTOM("Custom", null, null, true),
}

data class HistoryRange(
    val preset: HistoryPeriodPreset,
    val startEpochMillis: Long,
    val endExclusiveEpochMillis: Long,
    val displayTimeZoneId: String,
    val calendarDayCount: Int,
    val includesPartialLatestDay: Boolean,
) {
    val durationMillis: Long
        get() = endExclusiveEpochMillis - startEpochMillis
}

data class HistoryCoverage(
    val requestedDurationMillis: Long,
    val validDurationMillis: Long,
    val coveragePercent: Double,
    val gapCount: Int,
    val largestGapMillis: Long,
)

data class GlucoseChartBucket(
    val startEpochMillis: Long,
    val endExclusiveEpochMillis: Long,
    val firstMgDl: Double,
    val lastMgDl: Double,
    val minimumMgDl: Double,
    val maximumMgDl: Double,
    val timeWeightedMeanMgDl: Double,
    val validDurationMillis: Long,
)

data class GlucoseChartSegment(
    val buckets: List<GlucoseChartBucket>,
    val startsAfterGap: Boolean,
    val endsBeforeGap: Boolean,
)

enum class GlucoseChartStatus {
    AVAILABLE,
    NO_DATA,
    INVALID_RANGE,
    CALCULATION_ERROR,
}

data class GlucoseChartResult(
    val sourceId: String,
    val range: HistoryRange,
    val segments: List<GlucoseChartSegment>,
    val coverage: HistoryCoverage,
    val latestMeasurementAtEpochMillis: Long?,
    val status: GlucoseChartStatus,
    val detail: String,
)

enum class SelectedPeriodGmiAvailability {
    AVAILABLE,
    INSUFFICIENT_DURATION,
    INSUFFICIENT_COVERAGE,
    NO_DATA,
    SOURCE_DISCONTINUITY,
    INVALID_RANGE,
    CALCULATION_ERROR,
}

enum class SelectedPeriodGmiQualifier {
    CONCENTRATED_GAPS,
    LOW_GLUCOSE_EXPOSURE,
    PARTIAL_LATEST_DAY,
}

data class SelectedPeriodGmiResult(
    val sourceId: String,
    val range: HistoryRange,
    val availability: SelectedPeriodGmiAvailability,
    val qualifiers: Set<SelectedPeriodGmiQualifier> = emptySet(),
    val gmiPercent: Double? = null,
    val timeWeightedMeanMgDl: Double? = null,
    val coveragePercent: Double = 0.0,
    val timeInRangePercent: Double? = null,
    val timeBelowRangePercent: Double? = null,
    val timeVeryLowPercent: Double? = null,
    val missingDurationMillis: Long = 0L,
    val largestGapMillis: Long = 0L,
    val detail: String,
)

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
private const val DAY_MILLIS = 24L * HOUR_MILLIS
