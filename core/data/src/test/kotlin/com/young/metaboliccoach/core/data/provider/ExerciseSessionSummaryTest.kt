package com.young.metaboliccoach.core.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseSessionSummaryTest {
    @Test
    fun `summarizes every valid exercise session and latest end`() {
        val summary = summarizeExerciseSessions(
            listOf(
                ExerciseWindow(startEpochMillis = 0, endEpochMillis = 10 * 60_000L),
                ExerciseWindow(
                    startEpochMillis = 20 * 60_000L,
                    endEpochMillis = 45 * 60_000L,
                ),
            ),
        )

        assertEquals(2, summary.sessionCount)
        assertEquals(35, summary.durationMinutes)
        assertEquals(45 * 60_000L, summary.latestEndEpochMillis)
    }

    @Test
    fun `invalid reversed interval cannot corrupt activity aggregates`() {
        val summary = summarizeExerciseSessions(
            listOf(ExerciseWindow(startEpochMillis = 2_000, endEpochMillis = 1_000)),
        )

        assertEquals(0, summary.sessionCount)
        assertEquals(0, summary.durationMinutes)
        assertEquals(null, summary.latestEndEpochMillis)
    }
}
