package com.young.metaboliccoach.core.data.db

import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InterventionDaoLifecycleTest {
    @Test
    fun `start keeps the existing active session and duplicate command is idempotent`() = runTest {
        val dao = FakeInterventionDao()
        val first = session("first")
        val duplicate = first.copy(targetDurationMinutes = 20)
        val second = session("second", InterventionType.STAIRS)

        assertEquals(first, dao.startIfNoActive(first))
        assertEquals(first, dao.startIfNoActive(duplicate))
        assertEquals(first, dao.startIfNoActive(second))
        assertEquals(listOf(first), dao.savedSessions())
    }

    @Test
    fun `coached start consumes recommendation and inserts session as one transaction`() = runTest {
        val dao = FakeInterventionDao()
        val candidate = session("coached").copy(recommendationId = "recommendation")

        assertEquals(
            candidate,
            dao.startForRecommendationIfAvailable(candidate, "recommendation", 0),
        )
        assertEquals("recommendation", dao.getCoachState()?.consumedRecommendationId)
        assertEquals(
            candidate,
            dao.startForRecommendationIfAvailable(
                candidate.copy(targetDurationMinutes = 20),
                "recommendation",
                0,
            ),
        )
        assertEquals(listOf(candidate), dao.savedSessions())
    }

    @Test
    fun `consumed recommendation without a session cannot start later`() = runTest {
        val dao = FakeInterventionDao()
        dao.upsertCoachState(
            CoachStateEntity(
                lastRecommendationAtEpochMillis = 1_000,
                lastRecommendationId = "recommendation",
                snoozedUntilEpochMillis = null,
                notificationDayStartEpochMillis = 0,
                notificationsSentToday = 1,
                consumedRecommendationId = "recommendation",
            ),
        )

        assertEquals(
            null,
            dao.startForRecommendationIfAvailable(
                session("coached").copy(recommendationId = "recommendation"),
                "recommendation",
                0,
            ),
        )
        assertEquals(emptyList<InterventionSessionEntity>(), dao.savedSessions())
    }

    private fun session(
        id: String,
        type: InterventionType = InterventionType.WALK,
    ) = InterventionSessionEntity(
        id = id,
        type = type.name,
        status = InterventionStatus.STARTED.name,
        startedAtEpochMillis = 1_000,
        endedAtEpochMillis = null,
        targetDurationMinutes = 10.takeIf { type == InterventionType.WALK },
        targetFloors = 6.takeIf { type == InterventionType.STAIRS },
        baselineGlucoseMgDl = 140,
        baselineGlucoseReadingId = "reading",
        baselineGlucoseMeasuredAtEpochMillis = 1_000,
        baselineGlucoseSourceId = "health-connect:source-a",
        glucoseAfterMgDl = null,
        followUpDueAtEpochMillis = null,
        followUpReadingAtEpochMillis = null,
        followUpGlucoseReadingId = null,
        followUpGlucoseSourceId = null,
        followUpFinalizedAtEpochMillis = null,
    )

    private class FakeInterventionDao : InterventionDao {
        private val sessions = linkedMapOf<String, InterventionSessionEntity>()
        private var coachState: CoachStateEntity? = null

        fun savedSessions() = sessions.values.toList()

        override suspend fun getById(sessionId: String) = sessions[sessionId]

        override suspend fun getByRecommendationId(
            recommendationId: String,
        ) = sessions.values.firstOrNull { it.recommendationId == recommendationId }

        override suspend fun latestActive() = sessions.values.lastOrNull {
            it.status == InterventionStatus.STARTED.name
        }

        override fun observeLatestActive(): Flow<InterventionSessionEntity?> =
            flowOf(sessions.values.lastOrNull {
                it.status == InterventionStatus.STARTED.name
            })

        override suspend fun pendingFollowUps(): List<InterventionSessionEntity> = emptyList()

        override fun observeSince(
            startEpochMillis: Long,
        ): Flow<List<InterventionSessionEntity>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<InterventionSessionEntity>> =
            flowOf(sessions.values.toList())

        override suspend fun upsert(session: InterventionSessionEntity) {
            sessions[session.id] = session
        }

        override suspend fun getCoachState(): CoachStateEntity? = coachState

        override suspend fun upsertCoachState(state: CoachStateEntity) {
            coachState = state
        }

        override suspend fun completeStarted(
            sessionId: String,
            endedAtEpochMillis: Long,
            followUpDueAtEpochMillis: Long,
        ): Int = 0

        override suspend fun finalizeFollowUp(
            sessionId: String,
            glucoseMgDl: Int?,
            readingAtEpochMillis: Long?,
            readingId: String?,
            sourceId: String?,
            finalizedAtEpochMillis: Long,
        ): Int = 0
    }
}
