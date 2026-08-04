package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.ActivityDao
import com.young.metaboliccoach.core.data.db.CoachStateDao
import com.young.metaboliccoach.core.data.db.CoachStateEntity
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.InterventionDao
import com.young.metaboliccoach.core.data.db.MealDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotEntity
import com.young.metaboliccoach.core.domain.CoachRuleEngine
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.ObservationAnalyzer
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class RecommendationSnapshotPersistenceTest {
    @Test
    fun `retry with the same id returns the original authoritative snapshot`() = runTest {
        val snapshots = InMemoryRecommendationSnapshotDao()
        val repository = CoachingRepositoryImpl(
            glucoseDao = mock(GlucoseDao::class.java),
            activityDao = mock(ActivityDao::class.java),
            interventionDao = mock(InterventionDao::class.java),
            mealDao = mock(MealDao::class.java),
            coachStateDao = mock(CoachStateDao::class.java),
            recommendationSnapshotDao = snapshots,
            settingsRepository = mock(SettingsRepository::class.java),
            glucoseRepository = mock(GlucoseRepository::class.java),
            ruleEngine = CoachRuleEngine(),
            observationAnalyzer = ObservationAnalyzer(),
            timeSource = mock(CoachTimeSource::class.java),
        )
        val original = recommendation(createdAtEpochMillis = 1_000)
        val retry = original.copy(
            createdAtEpochMillis = 2_000,
            validUntilEpochMillis = 62_000,
            durationMinutes = 12,
        )

        assertEquals(original, repository.rememberRecommendation(original))
        assertEquals(original, repository.rememberRecommendation(retry))
        assertEquals(original, repository.recommendationSnapshot(original.id))
    }

    @Test
    fun `expired immutable snapshot cannot be published again after snooze`() = runTest {
        val snapshots = InMemoryRecommendationSnapshotDao()
        val repository = CoachingRepositoryImpl(
            glucoseDao = mock(GlucoseDao::class.java),
            activityDao = mock(ActivityDao::class.java),
            interventionDao = mock(InterventionDao::class.java),
            mealDao = mock(MealDao::class.java),
            coachStateDao = mock(CoachStateDao::class.java),
            recommendationSnapshotDao = snapshots,
            settingsRepository = mock(SettingsRepository::class.java),
            glucoseRepository = mock(GlucoseRepository::class.java),
            ruleEngine = CoachRuleEngine(),
            observationAnalyzer = ObservationAnalyzer(),
            timeSource = mock(CoachTimeSource::class.java),
        )
        val original = recommendation(createdAtEpochMillis = 1_000)
        repository.rememberRecommendation(original)

        assertEquals(
            false,
            repository.recordRecommendationPublished(
                original.id,
                original.validUntilEpochMillis,
            ),
        )
    }

    @Test
    fun `publishing a new recommendation clears an old recommendations snooze`() = runTest {
        val snapshots = InMemoryRecommendationSnapshotDao()
        val states = InMemoryCoachStateDao(
            CoachStateEntity(
                lastRecommendationAtEpochMillis = 500,
                lastRecommendationId = "old-recommendation",
                snoozedUntilEpochMillis = 900,
                notificationDayStartEpochMillis = 0,
                notificationsSentToday = 1,
                deliveryCountForLastRecommendation = 1,
            ),
        )
        val repository = CoachingRepositoryImpl(
            glucoseDao = mock(GlucoseDao::class.java),
            activityDao = mock(ActivityDao::class.java),
            interventionDao = mock(InterventionDao::class.java),
            mealDao = mock(MealDao::class.java),
            coachStateDao = states,
            recommendationSnapshotDao = snapshots,
            settingsRepository = mock(SettingsRepository::class.java),
            glucoseRepository = mock(GlucoseRepository::class.java),
            ruleEngine = CoachRuleEngine(),
            observationAnalyzer = ObservationAnalyzer(),
            timeSource = mock(CoachTimeSource::class.java),
        )
        val next = recommendation(createdAtEpochMillis = 1_000).copy(id = "next")
        repository.rememberRecommendation(next)

        assertEquals(true, repository.recordRecommendationPublished(next.id, 1_001))
        assertEquals(null, states.get()?.snoozedUntilEpochMillis)
        assertEquals(false, repository.recordRecommendationPublished(next.id, 1_002))
    }

    private fun recommendation(createdAtEpochMillis: Long) = CoachRecommendation.Action(
        reason = CoachReason.RAPID_GLUCOSE_RISE,
        id = "rapid-rise:reading-1",
        createdAtEpochMillis = createdAtEpochMillis,
        validUntilEpochMillis = createdAtEpochMillis + 60_000,
        interventionType = InterventionType.WALK,
        title = "Glucose is rising. Walk now?",
        actionLabel = "START WALK",
        durationMinutes = 10,
        targetFloors = null,
        algorithmVersion = 1,
        triggerContextId = "reading-1",
        triggerAtEpochMillis = 500,
    )

    private class InMemoryRecommendationSnapshotDao : RecommendationSnapshotDao {
        private val snapshots = linkedMapOf<String, RecommendationSnapshotEntity>()

        override suspend fun getById(
            recommendationId: String,
        ): RecommendationSnapshotEntity? = snapshots[recommendationId]

        override suspend fun insertIfAbsent(snapshot: RecommendationSnapshotEntity): Long =
            if (snapshots.putIfAbsent(snapshot.id, snapshot) == null) 1L else -1L

        override suspend fun deleteExpiredBefore(cutoffEpochMillis: Long) {
            snapshots.entries.removeAll {
                it.value.validUntilEpochMillis < cutoffEpochMillis
            }
        }

        override suspend fun deleteAll() {
            snapshots.clear()
        }
    }

    private class InMemoryCoachStateDao(
        initial: CoachStateEntity?,
    ) : CoachStateDao {
        private var state = initial

        override fun observe(): Flow<CoachStateEntity?> = flowOf(state)

        override suspend fun get(): CoachStateEntity? = state

        override suspend fun upsert(state: CoachStateEntity) {
            this.state = state
        }
    }
}
