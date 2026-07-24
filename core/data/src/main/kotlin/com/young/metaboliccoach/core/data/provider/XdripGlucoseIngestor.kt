package com.young.metaboliccoach.core.data.provider

import android.content.Intent
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.GlucoseReadingEntity
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.GlucoseTrend
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

@Singleton
class XdripGlucoseIngestor @Inject constructor(
    private val glucoseDao: GlucoseDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun ingest(intent: Intent): Boolean {
        if (intent.action != ACTION_NEW_BG_ESTIMATE) return false
        if (settingsRepository.observe().first().glucoseProviderMode !=
            GlucoseProviderMode.XDRIP_BROADCAST
        ) {
            return false
        }

        val estimate = intent.readNumericExtra(EXTRA_BG_ESTIMATE) ?: return false
        val timestamp = intent.readLongNumericExtra(EXTRA_TIMESTAMP) ?: return false
        // xDrip broadcasts its calculated slope in mg/dL per millisecond.
        val slopeMgDlPerMinute = intent.readNumericExtra(EXTRA_BG_SLOPE)
            ?.times(MILLIS_PER_MINUTE)
        val now = System.currentTimeMillis()
        if (
            !estimate.isFinite() ||
            estimate !in MIN_GLUCOSE_MG_DL..MAX_GLUCOSE_MG_DL ||
            timestamp !in (now - MAX_SAMPLE_AGE_MILLIS)..(now + MAX_CLOCK_SKEW_MILLIS) ||
            slopeMgDlPerMinute?.isFinite() == false ||
            (slopeMgDlPerMinute != null &&
                slopeMgDlPerMinute !in -MAX_ABS_RATE_MG_DL_PER_MINUTE..
                MAX_ABS_RATE_MG_DL_PER_MINUTE)
        ) {
            return false
        }

        val previous = glucoseDao.getLatestForSource(PROVIDER_ID)
        val delta = previous?.takeIf { timestamp > it.measuredAtEpochMillis }?.let {
            estimate.roundToInt() - it.valueMgDl
        }
        glucoseDao.insertAll(
            listOf(
                GlucoseReadingEntity(
                    id = "$PROVIDER_ID:$timestamp",
                    valueMgDl = estimate.roundToInt(),
                    trend = slopeMgDlPerMinute.toTrend().name,
                    deltaMgDl = delta,
                    rateMgDlPerMinute = slopeMgDlPerMinute,
                    measuredAtEpochMillis = timestamp,
                    receivedAtEpochMillis = now,
                    sourceId = buildString {
                        append(PROVIDER_ID)
                        intent.getStringExtra(EXTRA_SOURCE_INFO)?.takeIf { it.isNotBlank() }?.let {
                            append(':')
                            append(it.take(MAX_SOURCE_LENGTH))
                        }
                    },
                ),
            ),
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun Intent.readNumericExtra(key: String): Double? = when (val value = extras?.get(key)) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is Number -> value.toDouble()
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun Intent.readLongNumericExtra(key: String): Long? =
        when (val value = extras?.get(key)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }

    private fun Double?.toTrend(): GlucoseTrend = when {
        this == null -> GlucoseTrend.UNKNOWN
        this <= -3.0 -> GlucoseTrend.RAPIDLY_FALLING
        this <= -2.0 -> GlucoseTrend.FALLING
        this <= -0.5 -> GlucoseTrend.SLIGHTLY_FALLING
        this < 0.5 -> GlucoseTrend.STABLE
        this < 2.0 -> GlucoseTrend.SLIGHTLY_RISING
        this < 3.0 -> GlucoseTrend.RISING
        else -> GlucoseTrend.RAPIDLY_RISING
    }

    companion object {
        const val ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
        const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
        const val EXTRA_SOURCE_INFO = "com.eveningoutpost.dexdrip.Extras.SourceInfo"
        const val PROVIDER_ID = XdripBroadcastGlucoseProvider.PROVIDER_ID
        private const val MIN_GLUCOSE_MG_DL = 20.0
        private const val MAX_GLUCOSE_MG_DL = 600.0
        private const val MAX_SAMPLE_AGE_MILLIS = 24 * 60 * 60 * 1_000L
        private const val MAX_CLOCK_SKEW_MILLIS = 5 * 60 * 1_000L
        private const val MILLIS_PER_MINUTE = 60_000.0
        private const val MAX_ABS_RATE_MG_DL_PER_MINUTE = 20.0
        private const val MAX_SOURCE_LENGTH = 80
    }
}
