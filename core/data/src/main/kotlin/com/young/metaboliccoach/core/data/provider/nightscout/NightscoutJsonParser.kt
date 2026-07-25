package com.young.metaboliccoach.core.data.provider.nightscout

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class NightscoutParseException(message: String) : IllegalArgumentException(message)

class NightscoutJsonParser @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(
        body: String,
        sourceId: String,
        receivedAtEpochMillis: Long,
    ): List<GlucoseReading> {
        val array = runCatching { json.parseToJsonElement(body).jsonArray }
            .getOrElse { throw NightscoutParseException("Nightscout returned invalid JSON.") }
        if (array.isEmpty()) return emptyList()

        val rows = array.mapNotNull { element ->
            runCatching {
                element as? JsonObject ?: return@runCatching null
                element.toParsedEntry(receivedAtEpochMillis)
            }.getOrNull()
        }.filterNotNull()
            .distinctBy { it.remoteId ?: "${it.measuredAtEpochMillis}:${it.valueMgDl}" }
            .sortedWith(compareBy<ParsedEntry> { it.measuredAtEpochMillis }.thenBy {
                it.remoteId.orEmpty()
            })
        if (rows.isEmpty()) {
            throw NightscoutParseException("Nightscout returned no usable glucose readings.")
        }

        return rows.mapIndexed { index, row ->
            val previous = rows.getOrNull(index - 1)
            val elapsedMinutes = previous?.let {
                (row.measuredAtEpochMillis - it.measuredAtEpochMillis) /
                    MILLIS_PER_MINUTE.toDouble()
            }
            val delta = previous?.let { row.valueMgDl - it.valueMgDl }
                ?.takeIf {
                    elapsedMinutes != null &&
                        elapsedMinutes > 0.0 &&
                        elapsedMinutes <= MAXIMUM_DELTA_GAP_MINUTES
                }
            val rate = delta?.let { it / checkNotNull(elapsedMinutes) }
                ?.takeIf { it in -MAXIMUM_ABSOLUTE_RATE..MAXIMUM_ABSOLUTE_RATE }
            GlucoseReading(
                id = "$sourceId:${stableReadingSuffix(row)}",
                valueMgDl = row.valueMgDl,
                trend = row.direction.toGlucoseTrend(),
                deltaMgDl = delta?.takeIf { rate != null },
                rateMgDlPerMinute = rate,
                measuredAtEpochMillis = row.measuredAtEpochMillis,
                receivedAtEpochMillis = receivedAtEpochMillis,
                sourceId = sourceId,
            )
        }
    }

    private fun JsonObject.toParsedEntry(nowEpochMillis: Long): ParsedEntry? {
        val glucose = get("sgv")?.jsonPrimitive?.doubleOrNull
            ?.takeIf(Double::isFinite)
            ?.roundToInt()
            ?.takeIf { it in MINIMUM_GLUCOSE_MG_DL..MAXIMUM_GLUCOSE_MG_DL }
            ?: return null
        val measuredAt = get("date")?.jsonPrimitive?.longOrNull
            ?: get("dateString")?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: return null
        if (
            measuredAt < MINIMUM_TIMESTAMP_EPOCH_MILLIS ||
            measuredAt > nowEpochMillis + MAXIMUM_FUTURE_CLOCK_SKEW_MILLIS
        ) {
            return null
        }
        return ParsedEntry(
            remoteId = get("_id")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
            valueMgDl = glucose,
            measuredAtEpochMillis = measuredAt,
            direction = get("direction")?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun stableReadingSuffix(entry: ParsedEntry): String {
        val seed = entry.remoteId
            ?: "${entry.measuredAtEpochMillis}:${entry.valueMgDl}"
        return MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun String?.toGlucoseTrend(): GlucoseTrend = when (this?.trim()?.lowercase()) {
        "tripleup", "doubleup" -> GlucoseTrend.RAPIDLY_RISING
        "singleup" -> GlucoseTrend.RISING
        "fortyfiveup" -> GlucoseTrend.SLIGHTLY_RISING
        "flat" -> GlucoseTrend.STABLE
        "fortyfivedown" -> GlucoseTrend.SLIGHTLY_FALLING
        "singledown" -> GlucoseTrend.FALLING
        "doubledown", "tripledown" -> GlucoseTrend.RAPIDLY_FALLING
        else -> GlucoseTrend.UNKNOWN
    }

    private data class ParsedEntry(
        val remoteId: String?,
        val valueMgDl: Int,
        val measuredAtEpochMillis: Long,
        val direction: String?,
    )

    private companion object {
        const val MINIMUM_GLUCOSE_MG_DL = 20
        const val MAXIMUM_GLUCOSE_MG_DL = 600
        const val MINIMUM_TIMESTAMP_EPOCH_MILLIS = 946_684_800_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAXIMUM_DELTA_GAP_MINUTES = 20.0
        const val MAXIMUM_ABSOLUTE_RATE = 20.0
        const val MAXIMUM_FUTURE_CLOCK_SKEW_MILLIS = 5 * MILLIS_PER_MINUTE
    }
}
