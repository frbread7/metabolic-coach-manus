package com.young.metaboliccoach.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionProgressTest {
    @Test
    fun `walk countdown is bounded by configured duration`() {
        assertEquals(
            600L,
            remainingWalkSeconds(
                startedAtEpochMillis = 10_000,
                durationMinutes = 10,
                nowEpochMillis = 5_000,
            ),
        )
    }

    @Test
    fun `walk countdown reaches zero and never becomes negative`() {
        assertEquals(
            0L,
            remainingWalkSeconds(
                startedAtEpochMillis = 10_000,
                durationMinutes = 10,
                nowEpochMillis = 700_001,
            ),
        )
    }
}
