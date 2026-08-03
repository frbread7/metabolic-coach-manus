package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.GlucoseHistoryRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlucoseHistoryRetentionPolicyTest {
    @Test
    fun `bounded policies calculate deterministic measurement cutoffs`() {
        val now = 1_700_000_000_000L

        assertEquals(
            now - 90L * DAY_MILLIS,
            GlucoseHistoryRetentionPolicy.LAST_90_DAYS.cutoffEpochMillis(now),
        )
        assertEquals(
            now - 365L * DAY_MILLIS,
            GlucoseHistoryRetentionPolicy.LAST_YEAR.cutoffEpochMillis(now),
        )
    }

    @Test
    fun `keep all policy has no automatic cutoff`() {
        assertNull(
            GlucoseHistoryRetentionPolicy.KEEP_ALL_DOWNLOADED
                .cutoffEpochMillis(1_700_000_000_000L),
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
