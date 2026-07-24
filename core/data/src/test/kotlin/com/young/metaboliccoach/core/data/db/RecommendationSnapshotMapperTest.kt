package com.young.metaboliccoach.core.data.db

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationSnapshotMapperTest {
    @Test
    fun `phone-authored recommendation survives persistence mapping`() {
        val recommendation = CoachRecommendation.Action(
            reason = CoachReason.POST_MEAL_WINDOW,
            id = "post-meal:meal-123",
            createdAtEpochMillis = 1_000,
            validUntilEpochMillis = 61_000,
            interventionType = InterventionType.WALK,
            title = "A short walk may fit now",
            actionLabel = "Start 12-minute walk",
            durationMinutes = 12,
            targetFloors = null,
            algorithmVersion = 3,
            triggerContextId = "meal-123",
            triggerAtEpochMillis = 500,
        )

        assertEquals(recommendation, recommendation.toEntity().toModel())
    }
}
