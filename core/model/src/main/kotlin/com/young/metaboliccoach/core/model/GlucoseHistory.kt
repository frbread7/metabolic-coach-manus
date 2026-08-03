package com.young.metaboliccoach.core.model

/**
 * User-selected local-history retention. The default remains bounded, while a user may opt in
 * to retaining downloaded records for a year or indefinitely.
 */
enum class GlucoseHistoryRetentionPolicy(
    val label: String,
    val retentionDays: Int?,
) {
    LAST_90_DAYS("90 days", 90),
    LAST_YEAR("1 year", 365),
    KEEP_ALL_DOWNLOADED("Keep all downloaded", null),
    ;

    fun cutoffEpochMillis(nowEpochMillis: Long): Long? = retentionDays?.let { days ->
        nowEpochMillis - days * DAY_MILLIS
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

data class GlucoseHistorySettings(
    val retentionPolicy: GlucoseHistoryRetentionPolicy =
        GlucoseHistoryRetentionPolicy.LAST_90_DAYS,
    /** Destructive pruning is disabled until the user explicitly confirms the policy. */
    val retentionConfirmed: Boolean = false,
)

enum class GlucoseHistoryBackfillStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETE,
    FAILED,
}

data class GlucoseHistoryStatus(
    val settings: GlucoseHistorySettings = GlucoseHistorySettings(),
    val sourceId: String? = null,
    val oldestReadingAtEpochMillis: Long? = null,
    val newestReadingAtEpochMillis: Long? = null,
    val readingCount: Long = 0,
    val backfillStatus: GlucoseHistoryBackfillStatus = GlucoseHistoryBackfillStatus.IDLE,
    val nextBackfillEndEpochMillis: Long? = null,
    val lastError: String? = null,
)
