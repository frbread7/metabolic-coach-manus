package com.young.metaboliccoach.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.young.metaboliccoach.core.data.db.GlycemicPlanningMilestoneDao
import com.young.metaboliccoach.core.data.db.GlycemicPlanningMilestoneEntity
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.db.toModel
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.MilestoneLifecycleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GlycemicPlanningMilestoneRepositoryImplTest {
    private lateinit var dao: FakeMilestoneDao
    private lateinit var legacy: FakeLegacyGoalRepository
    private lateinit var clock: FakeCoachTimeSource
    private lateinit var repository: GlycemicPlanningMilestoneRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeMilestoneDao()
        legacy = FakeLegacyGoalRepository()
        clock = FakeCoachTimeSource(now = NOW)
        repository = GlycemicPlanningMilestoneRepositoryImpl(
            dao = dao,
            legacyGoalRepository = legacy,
            timeSource = clock,
            dataStore = newDataStore(),
        )
    }

    @Test
    fun `legacy singleton migrates once into a fixed selected milestone`() = runTest {
        legacy.settings = GlycemicPlannerSettings(
            targetGmiPercent = 7.5,
            targetProvenance = GlycemicTargetProvenance.USER_ENTERED,
            horizon = GlycemicWindow.DAYS_60,
        )

        val first = repository.observeMilestones().first()
        assertEquals(1, first.size)
        assertEquals("legacy-planner-target-v1", first.single().id)
        assertEquals(NOW + GlycemicWindow.DAYS_60.durationMillis, first.single().targetDateEpochMillis)
        assertEquals(
            "legacy-planner-target-v1",
            repository.observeSelectedMilestoneId().first(),
        )
        assertTrue(repository.observeMigrationNotice().first())
        assertEquals(1, dao.insertCount)

        clock.now = NOW + 5 * DAY
        val second = repository.observeMilestones().first()
        assertEquals(first.single(), second.single())
        assertEquals(1, dao.insertCount)
    }

    @Test
    fun `create is idempotent and does not replace an existing selection`() = runTest {
        legacy.settings = legacySettings(targetGmiPercent = 7.5)
        repository.observeMilestones().first()

        repository.create(milestone("second", targetDate = NOW + 90 * DAY))
        repository.create(milestone("second", targetDate = NOW + 90 * DAY))

        assertEquals(listOf("legacy-planner-target-v1", "second"), dao.models().map { it.id })
        assertEquals("legacy-planner-target-v1", repository.observeSelectedMilestoneId().first())
        assertEquals(2, dao.insertCount)
    }

    @Test
    fun `past milestone freezes planning fields but allows title edits`() = runTest {
        val past = milestone("past", targetDate = NOW - DAY)
        dao.upsert(past.toEntity())

        repository.update(past.copy(title = "Renamed"))
        assertEquals("Renamed", dao.getById("past")?.toModel()?.title)

        val failure = runCatching {
            repository.update(past.copy(targetGmiPercent = 6.5))
        }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun `archive and delete select deterministic active fallback`() = runTest {
        val first = milestone("first", targetDate = NOW + DAY)
        val second = milestone("second", targetDate = NOW + 2 * DAY)
        dao.upsert(first.toEntity())
        dao.upsert(second.toEntity())
        repository.select(first.id)

        repository.archive(first.id, NOW)
        assertEquals(second.id, repository.observeSelectedMilestoneId().first())

        repository.delete(second.id)
        assertEquals(first.id, repository.observeSelectedMilestoneId().first())
        assertEquals(listOf(first.id), dao.models().map { it.id })
    }

    @Test
    fun `reset removes milestones and migration state`() = runTest {
        legacy.settings = legacySettings(targetGmiPercent = 7.5)
        repository.observeMilestones().first()
        repository.dismissMigrationNotice()
        repository.reset()

        assertTrue(repository.observeMilestones().first().isEmpty())
        assertNull(repository.observeSelectedMilestoneId().first())
        assertFalse(repository.observeMigrationNotice().first())
        assertEquals(1, dao.insertCount)
    }

    private fun legacySettings(targetGmiPercent: Double) = GlycemicPlannerSettings(
        targetGmiPercent = targetGmiPercent,
        targetProvenance = GlycemicTargetProvenance.USER_ENTERED,
        horizon = GlycemicWindow.DAYS_30,
    )

    private fun milestone(
        id: String,
        targetDate: Long,
    ) = GlycemicPlanningMilestone(
        id = id,
        title = null,
        targetGmiPercent = 7.0,
        targetProvenance = GlycemicTargetProvenance.USER_ENTERED,
        targetDateEpochMillis = targetDate,
        originalHorizonDays = 30,
        lifecycleState = MilestoneLifecycleState.ACTIVE,
        createdAtEpochMillis = NOW,
        updatedAtEpochMillis = NOW,
        archivedAtEpochMillis = null,
        calculationContractVersion = 1,
    )

    private class FakeCoachTimeSource(var now: Long) : CoachTimeSource {
        override fun nowEpochMillis(): Long = now
        override fun minuteTicks(): Flow<Long> = flowOf(now)
    }

    private class FakeLegacyGoalRepository : GlycemicGoalRepository {
        var settings = GlycemicPlannerSettings()

        override fun observeSettings(): Flow<GlycemicPlannerSettings> = flowOf(settings)
        override suspend fun updateSettings(settings: GlycemicPlannerSettings) {
            this.settings = settings
        }
        override suspend fun updateSafetySettings(settings: GlycemicPlannerSettings) {
            this.settings = this.settings.copy(
                lowGlucoseThresholdMgDl = settings.lowGlucoseThresholdMgDl,
                veryLowGlucoseThresholdMgDl = settings.veryLowGlucoseThresholdMgDl,
                maximumLowGlucosePercent = settings.maximumLowGlucosePercent,
                maximumVeryLowGlucosePercent = settings.maximumVeryLowGlucosePercent,
            )
        }
        override suspend fun reset() {
            settings = GlycemicPlannerSettings()
        }
    }

    private class FakeMilestoneDao : GlycemicPlanningMilestoneDao {
        private val state = MutableStateFlow<List<GlycemicPlanningMilestoneEntity>>(emptyList())
        var insertCount = 0
            private set

        fun models(): List<GlycemicPlanningMilestone> = state.value.map { it.toModel() }

        override fun observeAll(): Flow<List<GlycemicPlanningMilestoneEntity>> = state

        override suspend fun getAll(): List<GlycemicPlanningMilestoneEntity> = state.value

        override suspend fun getById(id: String): GlycemicPlanningMilestoneEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun insertIfAbsent(milestone: GlycemicPlanningMilestoneEntity): Long {
            if (state.value.any { it.id == milestone.id }) return -1L
            insertCount += 1
            state.value = state.value + milestone
            return 1L
        }

        override suspend fun upsert(milestone: GlycemicPlanningMilestoneEntity) {
            state.value = state.value.filterNot { it.id == milestone.id } + milestone
        }

        override suspend fun archive(id: String, nowEpochMillis: Long): Int {
            val existing = state.value.firstOrNull { it.id == id } ?: return 0
            if (existing.lifecycleState != MilestoneLifecycleState.ACTIVE.name) return 0
            state.value = state.value.map {
                if (it.id == id) {
                    it.copy(
                        lifecycleState = MilestoneLifecycleState.ARCHIVED.name,
                        archivedAtEpochMillis = nowEpochMillis,
                        updatedAtEpochMillis = nowEpochMillis,
                    )
                } else {
                    it
                }
            }
            return 1
        }

        override suspend fun delete(id: String): Int {
            val before = state.value.size
            state.value = state.value.filterNot { it.id == id }
            return (before - state.value.size)
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    private fun newDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("metabolic-coach-milestones-", ".preferences_pb")
        check(file.delete())
        return PreferenceDataStoreFactory.create { file }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
        const val NOW = 1_700_000_000_000L
    }
}
