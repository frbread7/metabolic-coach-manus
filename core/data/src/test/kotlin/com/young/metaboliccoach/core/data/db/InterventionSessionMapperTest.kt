package com.young.metaboliccoach.core.data.db

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import org.junit.Assert.assertEquals
import org.junit.Test

class InterventionSessionMapperTest {
    @Test
    fun `prospective timing provenance survives entity mapping`() {
        val session = InterventionSession(
            id = "session",
            type = InterventionType.WALK,
            status = InterventionStatus.COMPLETED,
            startedAtEpochMillis = 10_000,
            endedAtEpochMillis = 610_000,
            targetDurationMinutes = 10,
            targetFloors = null,
            baselineGlucoseMgDl = 145,
            baselineGlucoseReadingId = "before",
            baselineGlucoseMeasuredAtEpochMillis = 9_000,
            baselineGlucoseSourceId = "health-connect:source",
            glucoseAfterMgDl = 125,
            followUpDueAtEpochMillis = 4_210_000,
            followUpReadingAtEpochMillis = 4_210_000,
            followUpGlucoseReadingId = "after",
            followUpGlucoseSourceId = "health-connect:source",
            followUpFinalizedAtEpochMillis = 4_220_000,
            recommendationId = "recommendation",
            recommendationReason = CoachReason.RAPID_GLUCOSE_RISE,
            recommendationAlgorithmVersion = 1,
            recommendationCreatedAtEpochMillis = 9_500,
            recommendationValidUntilEpochMillis = 900_000,
            triggerContextId = "reading",
            triggerAtEpochMillis = 8_000,
            baselineEffectiveRateMgDlPerMinute = 2.4,
            lowGlucoseThresholdMgDlAtStart = 70,
        )

        assertEquals(session, session.toEntity().toModel())
    }
}
