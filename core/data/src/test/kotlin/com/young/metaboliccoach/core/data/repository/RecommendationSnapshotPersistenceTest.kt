package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.ActivityDao
import com.young.metaboliccoach.core.data.db.CoachStateDao
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.InterventionDao
import com.young.metaboliccoach.core.data.db.MealDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotEntity
import com.young.metaboliccoach.core.domain.CoachRuleEngine
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.ObservationAnalyzer
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import kotlinx.coroutines.test.runTest
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
    }
}
