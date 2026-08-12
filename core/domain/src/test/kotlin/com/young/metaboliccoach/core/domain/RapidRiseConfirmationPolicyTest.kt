package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidRiseConfirmationPolicyTest {
    private val settings = DefaultCoachSettings.create()
    private val now = 1_700_000_000_000L

    @Test
    fun `two consecutive exact-source readings at threshold confirm`() {
        val confirmation = RapidRiseConfirmationPolicy.confirm(
            olderReading = reading("older", now - 5 * 60_000L, rate = 2.0),
            latestReading = reading("latest", now, rate = 2.0),
            settings = settings,
        )

        assertEquals("latest", confirmation?.latestReading?.id)
        assertTrue(confirmation?.recommendationId?.startsWith("RAPID_GLUCOSE_RISE:v3:") == true)
        assertTrue((confirmation?.recommendationId?.length ?: Int.MAX_VALUE) <= 96)
    }

    @Test
    fun `missing cross-source tied out-of-order or excessive-gap pairs fail closed`() {
        val older = reading("older", now - 5 * 60_000L, rate = 3.0)
        val latest = reading("latest", now, rate = 3.0)

        assertNull(RapidRiseConfirmationPolicy.confirm(null, latest, settings))
        assertNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                latest.copy(sourceId = "other"),
                settings,
            ),
        )
        assertNull(
            RapidRiseConfirmationPolicy.confirm(
                older.copy(measuredAtEpochMillis = now),
                latest,
                settings,
            ),
        )
        assertNull(RapidRiseConfirmationPolicy.confirm(latest, older, settings))
        assertNull(
            RapidRiseConfirmationPolicy.confirm(
                older.copy(
                    measuredAtEpochMillis =
                        now - settings.staleReadingMinutes * 60_000L - 1,
                ),
                latest,
                settings,
            ),
        )
    }

    @Test
    fun `both effective rates must qualify and numeric rate overrides trend`() {
        val older = reading("older", now - 5 * 60_000L, rate = 3.0)
        val latest = reading("latest", now, rate = 3.0)

        assertNull(
            RapidRiseConfirmationPolicy.confirm(
                older.copy(rateMgDlPerMinute = settings.rapidRiseThresholdMgDlPerMinute - 0.01),
                latest,
                settings,
            ),
        )
        assertNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                latest.copy(
                    rateMgDlPerMinute = settings.rapidRiseThresholdMgDlPerMinute - 0.01,
                    trend = GlucoseTrend.RAPIDLY_RISING,
                ),
                settings,
            ),
        )
        assertTrue(
            RapidRiseConfirmationPolicy.confirm(
                older.copy(rateMgDlPerMinute = null, trend = GlucoseTrend.RISING),
                latest.copy(rateMgDlPerMinute = null, trend = GlucoseTrend.RISING),
                settings.copy(rapidRiseThresholdMgDlPerMinute = 1.0),
            ) != null,
        )
    }

    @Test
    fun `identity is stable and changes with source reading or timestamp`() {
        val older = reading("older", now - 5 * 60_000L, rate = 3.0)
        val latest = reading("latest", now, rate = 3.0)
        val first = requireNotNull(RapidRiseConfirmationPolicy.confirm(older, latest, settings))
        val repeated = requireNotNull(RapidRiseConfirmationPolicy.confirm(older, latest, settings))
        val changedReading = requireNotNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                latest.copy(id = "latest-2"),
                settings,
            ),
        )
        val changedTimestamp = requireNotNull(
            RapidRiseConfirmationPolicy.confirm(
                older,
                latest.copy(measuredAtEpochMillis = now + 1),
                settings,
            ),
        )

        assertEquals(first.recommendationId, repeated.recommendationId)
        assertNotEquals(first.recommendationId, changedReading.recommendationId)
        assertNotEquals(first.recommendationId, changedTimestamp.recommendationId)
        assertNotEquals("RAPID_GLUCOSE_RISE:latest", first.recommendationId)
    }

    @Test
    fun `tied newest records fail closed`() {
        val confirmation = RapidRiseConfirmationPolicy.confirmLatest(
            readings = listOf(
                reading("older-z", now - 5 * 60_000L, rate = 3.0),
                reading("older-a", now - 5 * 60_000L, rate = 3.0),
                reading("latest-z", now, rate = 3.0),
                reading("latest-a", now, rate = 3.0),
            ),
            settings = settings,
        )

        assertNull(confirmation)
    }

    @Test
    fun `canonical immediate predecessor is deterministic`() {
        val confirmation = RapidRiseConfirmationPolicy.confirmLatest(
            readings = listOf(
                reading("older-z", now - 5 * 60_000L, rate = 3.0),
                reading("older-a", now - 5 * 60_000L, rate = 3.0),
                reading("latest", now, rate = 3.0),
            ),
            settings = settings,
        )

        assertEquals("older-a", confirmation?.olderReading?.id)
        assertEquals("latest", confirmation?.latestReading?.id)
    }

    private fun reading(
        id: String,
        measuredAt: Long,
        rate: Double?,
        sourceId: String = "nightscout:server-a",
    ) = GlucoseReading(
        id = id,
        valueMgDl = 140,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 4,
        rateMgDlPerMinute = rate,
        measuredAtEpochMillis = measuredAt,
        receivedAtEpochMillis = measuredAt,
        sourceId = sourceId,
    )
}
