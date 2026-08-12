package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import java.security.MessageDigest

data class RapidRiseConfirmation(
    val olderReading: GlucoseReading,
    val latestReading: GlucoseReading,
) {
    private val fingerprint: String = fingerprint(
        CoachReason.RAPID_GLUCOSE_RISE.name,
        latestReading.sourceId,
        olderReading.id,
        olderReading.measuredAtEpochMillis.toString(),
        latestReading.id,
        latestReading.measuredAtEpochMillis.toString(),
        RapidRiseConfirmationPolicy.ALGORITHM_VERSION.toString(),
    )

    val triggerIdentity: String =
        "rapid-pair:v${RapidRiseConfirmationPolicy.ALGORITHM_VERSION}:$fingerprint"
    val recommendationId: String =
        "RAPID_GLUCOSE_RISE:v${RapidRiseConfirmationPolicy.ALGORITHM_VERSION}:$fingerprint"

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()

        private fun fingerprint(vararg parts: String): String {
            val input = parts.joinToString("|") { part -> "${part.length}:$part" }
            return MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte ->
                    val value = byte.toInt() and 0xff
                    "${HEX[value ushr 4]}${HEX[value and 0x0f]}"
                }
        }
    }
}

/**
 * Confirms a rapid rise from two consecutive normalized readings from one exact source.
 *
 * The configurable stale-reading window is also the maximum accepted separation between the two
 * readings. This keeps the confirmation tolerance aligned with the user's freshness policy rather
 * than introducing another hidden glucose-data threshold.
 */
object RapidRiseConfirmationPolicy {
    const val ALGORITHM_VERSION = 3

    fun confirm(
        olderReading: GlucoseReading?,
        latestReading: GlucoseReading?,
        settings: CoachSettings,
    ): RapidRiseConfirmation? {
        olderReading ?: return null
        latestReading ?: return null
        if (olderReading.id == latestReading.id) return null
        if (olderReading.sourceId != latestReading.sourceId) return null
        if (olderReading.measuredAtEpochMillis >= latestReading.measuredAtEpochMillis) return null
        val separationMillis =
            latestReading.measuredAtEpochMillis - olderReading.measuredAtEpochMillis
        if (separationMillis > settings.staleReadingMinutes * MILLIS_PER_MINUTE) return null
        val olderRate = effectiveRate(olderReading).takeIf(Double::isFinite) ?: return null
        val latestRate = effectiveRate(latestReading).takeIf(Double::isFinite) ?: return null
        if (olderRate < settings.rapidRiseThresholdMgDlPerMinute) return null
        if (latestRate < settings.rapidRiseThresholdMgDlPerMinute) return null
        return RapidRiseConfirmation(olderReading, latestReading)
    }

    fun confirmLatest(
        readings: List<GlucoseReading>,
        settings: CoachSettings,
    ): RapidRiseConfirmation? {
        val latest = latestReading(readings) ?: return null
        return confirmForLatest(readings, latest, settings)
    }

    fun confirmForLatest(
        readings: List<GlucoseReading>,
        latestReading: GlucoseReading,
        settings: CoachSettings,
    ): RapidRiseConfirmation? {
        val canonicalLatest = latestReading(readings) ?: return null
        if (
            canonicalLatest.id != latestReading.id ||
            canonicalLatest.sourceId != latestReading.sourceId ||
            canonicalLatest.measuredAtEpochMillis != latestReading.measuredAtEpochMillis
        ) {
            return null
        }
        val previous = immediatePredecessor(readings, latestReading)
        return confirm(previous, latestReading, settings)
    }

    fun immediatePredecessor(
        readings: List<GlucoseReading>,
        latestReading: GlucoseReading,
    ): GlucoseReading? {
        val ordered = readings.asSequence()
            .filter { it.sourceId == latestReading.sourceId }
            .sortedWith(
                compareByDescending<GlucoseReading> { it.measuredAtEpochMillis }
                    .thenBy { it.id },
            )
            .toList()
        val latestIndex = ordered.indexOfFirst { reading ->
            reading.id == latestReading.id &&
                reading.measuredAtEpochMillis == latestReading.measuredAtEpochMillis
        }
        if (latestIndex != 0) return null
        return ordered.getOrNull(1)
    }

    fun latestReading(readings: List<GlucoseReading>): GlucoseReading? {
        return readings.minWithOrNull(
            compareByDescending<GlucoseReading> { it.measuredAtEpochMillis }
                .thenBy { it.id },
        )
    }

    private fun effectiveRate(reading: GlucoseReading): Double =
        reading.rateMgDlPerMinute ?: reading.trend.approximateRateMgDlPerMinute

    private const val MILLIS_PER_MINUTE = 60_000L
}
