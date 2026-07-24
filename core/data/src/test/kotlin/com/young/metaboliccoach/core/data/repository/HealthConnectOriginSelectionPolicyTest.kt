package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.provider.HealthConnectGlucoseProvider
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectOriginSelectionPolicyTest {
    @Test
    fun `single discovered origin is auto-selected and pinned`() {
        val selection = HealthConnectOriginSelectionPolicy.select(
            readings = listOf(reading("com.example.cgm", "one", 1_000)),
            configuredPackageName = null,
        )

        assertEquals("com.example.cgm", selection.autoSelectedPackageName)
        assertEquals("com.example.cgm", selection.selectedPackageName)
        assertEquals(listOf("one"), selection.selectedReadings.map(GlucoseReading::id))
        assertFalse(selection.requiresUserSelection)
    }

    @Test
    fun `multiple origins without configuration pause selection`() {
        val selection = HealthConnectOriginSelectionPolicy.select(
            readings = listOf(
                reading("com.example.alpha", "alpha", 1_000),
                reading("com.example.beta", "beta", 2_000),
            ),
            configuredPackageName = null,
        )

        assertNull(selection.selectedPackageName)
        assertTrue(selection.selectedReadings.isEmpty())
        assertTrue(selection.requiresUserSelection)
        assertEquals(
            listOf("com.example.alpha", "com.example.beta"),
            selection.availableOrigins.map { it.packageName },
        )
    }

    @Test
    fun `configured origin filters records without switching to newer writer`() {
        val selection = HealthConnectOriginSelectionPolicy.select(
            readings = listOf(
                reading("com.example.selected", "first", 1_000),
                reading("com.example.other", "newest", 9_000),
                reading("com.example.selected", "second", 2_000),
            ),
            configuredPackageName = "com.example.selected",
        )

        assertEquals("com.example.selected", selection.selectedPackageName)
        assertNull(selection.autoSelectedPackageName)
        assertEquals(
            listOf("first", "second"),
            selection.selectedReadings.map(GlucoseReading::id),
        )
        assertFalse(selection.requiresUserSelection)
    }

    @Test
    fun `missing configured origin is retained and never replaced`() {
        val selection = HealthConnectOriginSelectionPolicy.select(
            readings = listOf(reading("com.example.other", "other", 9_000)),
            configuredPackageName = "com.example.selected",
        )

        assertEquals("com.example.selected", selection.selectedPackageName)
        assertNull(selection.autoSelectedPackageName)
        assertTrue(selection.selectedReadings.isEmpty())
        assertFalse(selection.requiresUserSelection)
    }

    @Test
    fun `origins expose deterministic latest record time`() {
        val selection = HealthConnectOriginSelectionPolicy.select(
            readings = listOf(
                reading("com.example.cgm", "newer", 5_000),
                reading("com.example.cgm", "older", 1_000),
            ),
            configuredPackageName = "com.example.cgm",
        )

        assertEquals(5_000, selection.availableOrigins.single().latestReadingAtEpochMillis)
        assertEquals(
            listOf("older", "newer"),
            selection.selectedReadings.map(GlucoseReading::id),
        )
    }

    private fun reading(
        packageName: String,
        id: String,
        measuredAtEpochMillis: Long,
    ) = GlucoseReading(
        id = id,
        valueMgDl = 120,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = "${HealthConnectGlucoseProvider.PROVIDER_ID}:$packageName",
    )
}
