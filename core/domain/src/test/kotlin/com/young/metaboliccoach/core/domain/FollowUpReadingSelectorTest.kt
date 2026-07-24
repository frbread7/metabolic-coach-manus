package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FollowUpReadingSelectorTest {
    @Test
    fun `selects exact-source reading after due time`() {
        val wrongSource = reading("wrong", "source-b", 1_001)
        val exactSource = reading("exact", "source-a", 1_002)

        val selection = FollowUpReadingSelector.select(
            readings = listOf(wrongSource, exactSource),
            exactSourceId = "source-a",
            dueAtEpochMillis = 1_000,
            deadlineEpochMillis = 2_000,
            nowEpochMillis = 1_100,
        )

        assertSame(exactSource, (selection as FollowUpSelection.Finalize).reading)
    }

    @Test
    fun `waits for post-due sample before deadline`() {
        val selection = FollowUpReadingSelector.select(
            readings = listOf(reading("before", "source-a", 999)),
            exactSourceId = "source-a",
            dueAtEpochMillis = 1_000,
            deadlineEpochMillis = 2_000,
            nowEpochMillis = 1_999,
        )

        assertEquals(FollowUpSelection.Wait, selection)
    }

    @Test
    fun `uses deterministic closest exact-source fallback at deadline`() {
        val firstById = reading("a", "source-a", 990)
        val secondById = reading("b", "source-a", 990)

        val selection = FollowUpReadingSelector.select(
            readings = listOf(secondById, firstById),
            exactSourceId = "source-a",
            dueAtEpochMillis = 1_000,
            deadlineEpochMillis = 2_000,
            nowEpochMillis = 2_000,
        )

        assertSame(firstById, (selection as FollowUpSelection.Finalize).reading)
    }

    @Test
    fun `finalizes without data when only another source exists at deadline`() {
        val selection = FollowUpReadingSelector.select(
            readings = listOf(reading("wrong", "source-b", 1_001)),
            exactSourceId = "source-a",
            dueAtEpochMillis = 1_000,
            deadlineEpochMillis = 2_000,
            nowEpochMillis = 2_000,
        )

        assertNull((selection as FollowUpSelection.Finalize).reading)
    }

    private fun reading(
        id: String,
        sourceId: String,
        measuredAtEpochMillis: Long,
    ) = GlucoseReading(
        id = id,
        valueMgDl = 120,
        deltaMgDl = null,
        rateMgDlPerMinute = null,
        trend = GlucoseTrend.STABLE,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = sourceId,
    )
}
