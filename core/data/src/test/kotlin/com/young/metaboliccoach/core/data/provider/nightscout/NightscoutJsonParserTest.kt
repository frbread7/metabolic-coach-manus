package com.young.metaboliccoach.core.data.provider.nightscout

import com.young.metaboliccoach.core.model.GlucoseTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NightscoutJsonParserTest {
    private val parser = NightscoutJsonParser()

    @Test
    fun `newest first Nightscout payload is normalized chronologically`() {
        val readings = parser.parse(
            body = """
                [
                  {
                    "_id": "newer",
                    "sgv": 138,
                    "date": ${NOW - FIVE_MINUTES},
                    "direction": "SingleUp",
                    "noise": 1
                  },
                  {
                    "_id": "older",
                    "sgv": 123,
                    "date": ${NOW - 2 * FIVE_MINUTES},
                    "direction": "Flat"
                  }
                ]
            """.trimIndent(),
            sourceId = SOURCE_ID,
            receivedAtEpochMillis = NOW,
        )

        assertEquals(listOf(123, 138), readings.map { it.valueMgDl })
        assertEquals(listOf(NOW - 2 * FIVE_MINUTES, NOW - FIVE_MINUTES), readings.map {
            it.measuredAtEpochMillis
        })
        assertNull(readings.first().deltaMgDl)
        assertEquals(15, readings.last().deltaMgDl)
        assertEquals(3.0, readings.last().rateMgDlPerMinute!!, 0.000_001)
        assertEquals(GlucoseTrend.RISING, readings.last().trend)
        assertEquals(SOURCE_ID, readings.last().sourceId)
        assertTrue(readings.all { it.id.startsWith("$SOURCE_ID:") })
        assertTrue(readings.all { it.receivedAtEpochMillis == NOW })
    }

    @Test
    fun `all supported Nightscout directions map to normalized trends`() {
        val cases = mapOf(
            "TripleUp" to GlucoseTrend.RAPIDLY_RISING,
            "DoubleUp" to GlucoseTrend.RAPIDLY_RISING,
            "SingleUp" to GlucoseTrend.RISING,
            "FortyFiveUp" to GlucoseTrend.SLIGHTLY_RISING,
            "Flat" to GlucoseTrend.STABLE,
            "FortyFiveDown" to GlucoseTrend.SLIGHTLY_FALLING,
            "SingleDown" to GlucoseTrend.FALLING,
            "DoubleDown" to GlucoseTrend.RAPIDLY_FALLING,
            "TripleDown" to GlucoseTrend.RAPIDLY_FALLING,
            "NONE" to GlucoseTrend.UNKNOWN,
            "NOT COMPUTABLE" to GlucoseTrend.UNKNOWN,
            "RATE OUT OF RANGE" to GlucoseTrend.UNKNOWN,
        )

        cases.entries.forEachIndexed { index, (direction, expected) ->
            val reading = parser.parse(
                body = """[{"sgv":120,"date":${NOW - index},"direction":"$direction"}]""",
                sourceId = SOURCE_ID,
                receivedAtEpochMillis = NOW,
            ).single()

            assertEquals("Direction $direction", expected, reading.trend)
        }
    }

    @Test
    fun `dateString is accepted and unusable rows are skipped`() {
        val readings = parser.parse(
            body = """
                [
                  {"sgv":19,"date":$NOW},
                  {"sgv":601,"date":$NOW},
                  {"sgv":130},
                  {"sgv":130,"date":0},
                  {"sgv":130,"date":${NOW + FIVE_MINUTES + 1}},
                  {"not":"an entry"},
                  "not an object",
                  {
                    "sgv":142,
                    "dateString":"2023-11-14T22:08:20Z",
                    "direction":"Mystery",
                    "futureField":{"isIgnored":true}
                  }
                ]
            """.trimIndent(),
            sourceId = SOURCE_ID,
            receivedAtEpochMillis = NOW,
        )

        assertEquals(1, readings.size)
        assertEquals(142, readings.single().valueMgDl)
        assertEquals(1_699_999_700_000L, readings.single().measuredAtEpochMillis)
        assertEquals(GlucoseTrend.UNKNOWN, readings.single().trend)
    }

    @Test
    fun `delta is suppressed across stale gaps and implausible rates`() {
        val staleGap = parser.parse(
            body = """
                [
                  {"sgv":130,"date":${NOW - 21 * MINUTE}},
                  {"sgv":120,"date":${NOW - 42 * MINUTE}}
                ]
            """.trimIndent(),
            sourceId = SOURCE_ID,
            receivedAtEpochMillis = NOW,
        )
        val implausibleRate = parser.parse(
            body = """
                [
                  {"sgv":150,"date":${NOW - MINUTE}},
                  {"sgv":100,"date":${NOW - 2 * MINUTE}}
                ]
            """.trimIndent(),
            sourceId = SOURCE_ID,
            receivedAtEpochMillis = NOW,
        )

        assertNull(staleGap.last().deltaMgDl)
        assertNull(staleGap.last().rateMgDlPerMinute)
        assertNull(implausibleRate.last().deltaMgDl)
        assertNull(implausibleRate.last().rateMgDlPerMinute)
    }

    @Test
    fun `reading identity is stable across receipt times and distinct by remote id`() {
        val body = """
            [
              {"_id":"reading-b","sgv":130,"date":${NOW - FIVE_MINUTES}},
              {"_id":"reading-a","sgv":120,"date":${NOW - 2 * FIVE_MINUTES}}
            ]
        """.trimIndent()

        val first = parser.parse(body, SOURCE_ID, NOW)
        val later = parser.parse(body, SOURCE_ID, NOW + MINUTE)

        assertEquals(first.map { it.id }, later.map { it.id })
        assertNotEquals(first.first().id, first.last().id)
    }

    @Test
    fun `valid empty array is distinct from malformed or unusable responses`() {
        assertTrue(parser.parse("[]", SOURCE_ID, NOW).isEmpty())

        expectParseFailure("""{"sgv":120,"date":$NOW}""")
        expectParseFailure("[{}]")
        expectParseFailure("not-json")
    }

    private fun expectParseFailure(body: String) {
        try {
            parser.parse(body, SOURCE_ID, NOW)
            fail("Expected NightscoutParseException for $body")
        } catch (_: NightscoutParseException) {
            // Expected.
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
        const val FIVE_MINUTES = 5 * MINUTE
        const val SOURCE_ID = "nightscout:server-1:fingerprint"
    }
}
