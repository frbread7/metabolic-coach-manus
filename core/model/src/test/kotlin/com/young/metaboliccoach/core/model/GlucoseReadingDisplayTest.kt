package com.young.metaboliccoach.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GlucoseReadingDisplayTest {
    private val reading = GlucoseReading(
        id = "reading",
        valueMgDl = 126,
        trend = GlucoseTrend.RISING,
        deltaMgDl = 18,
        rateMgDlPerMinute = 1.80182,
        measuredAtEpochMillis = 1_000,
        receivedAtEpochMillis = 1_000,
        sourceId = "test",
    )

    @Test
    fun `numeric rate follows selected glucose unit`() {
        assertEquals("+1.8 mg/dL/min", reading.displayRateWithUnit(GlucoseUnit.MG_DL))
        assertEquals("+0.10 mmol/L/min", reading.displayRateWithUnit(GlucoseUnit.MMOL_L))
    }

    @Test
    fun `unit conversion round trips stored thresholds`() {
        val displayed = GlucoseUnit.MMOL_L.fromMgDl(180.0)

        assertEquals(9.99, displayed, 0.001)
        assertEquals(180.0, GlucoseUnit.MMOL_L.toMgDl(displayed), 0.0001)
    }
}
