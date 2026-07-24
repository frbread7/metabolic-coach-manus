package com.young.metaboliccoach.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearActionPolicyTest {
    @Test
    fun `manual start is accepted when no session is active`() {
        assertNull(
            WearActionPolicy.startRejection(
                blocksNewSession = false,
                recommendationId = null,
                recommendationValidUntilEpochMillis = null,
                effectiveRecommendationId = null,
                nowEpochMillis = 1_000,
            ),
        )
    }

    @Test
    fun `expired coached action has a terminal outcome`() {
        assertEquals(
            WearActionRejection.EXPIRED_RECOMMENDATION,
            WearActionPolicy.startRejection(
                blocksNewSession = false,
                recommendationId = "recommendation",
                recommendationValidUntilEpochMillis = 1_000,
                effectiveRecommendationId = null,
                nowEpochMillis = 1_000,
            ),
        )
    }

    @Test
    fun `superseded coached action has a terminal outcome`() {
        assertEquals(
            WearActionRejection.STALE_RECOMMENDATION,
            WearActionPolicy.startRejection(
                blocksNewSession = false,
                recommendationId = "old",
                recommendationValidUntilEpochMillis = 2_000,
                effectiveRecommendationId = "new",
                nowEpochMillis = 1_000,
            ),
        )
    }

    @Test
    fun `active or pending session takes precedence`() {
        assertEquals(
            WearActionRejection.SESSION_BUSY,
            WearActionPolicy.startRejection(
                blocksNewSession = true,
                recommendationId = "recommendation",
                recommendationValidUntilEpochMillis = 500,
                effectiveRecommendationId = null,
                nowEpochMillis = 1_000,
            ),
        )
    }
}

