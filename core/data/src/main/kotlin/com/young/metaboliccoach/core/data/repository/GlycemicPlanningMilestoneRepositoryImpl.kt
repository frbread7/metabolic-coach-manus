package com.young.metaboliccoach.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.young.metaboliccoach.core.data.db.GlycemicPlanningMilestoneDao
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.db.toModel
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.GlycemicPlanningMilestoneRepository
import com.young.metaboliccoach.core.domain.GLYCEMIC_MILESTONE_CALCULATION_CONTRACT_VERSION
import com.young.metaboliccoach.core.domain.normalizedMilestoneTitle
import com.young.metaboliccoach.core.domain.sortPlanningMilestones
import com.young.metaboliccoach.core.domain.temporalState
import com.young.metaboliccoach.core.domain.validateMilestoneDraft
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.MilestoneLifecycleState
import com.young.metaboliccoach.core.model.MilestoneTemporalState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class GlycemicPlanningMilestoneRepositoryImpl @Inject constructor(
    private val dao: GlycemicPlanningMilestoneDao,
    private val legacyGoalRepository: GlycemicGoalRepository,
    private val timeSource: CoachTimeSource,
    private val dataStore: DataStore<Preferences>,
) : GlycemicPlanningMilestoneRepository {
    private val migrationMutex = Mutex()

    override fun observeMilestones(): Flow<List<GlycemicPlanningMilestone>> = flow {
        ensureMigrated()
        emitAll(
            dao.observeAll().map { entities ->
                entities.map { it.toModel() }
            },
        )
    }

    override fun observeSelectedMilestoneId(): Flow<String?> = flow {
        ensureMigrated()
        emitAll(
            dataStore.data
                .map { it[Keys.selectedMilestoneId] }
                .distinctUntilChanged(),
        )
    }

    override fun observeMigrationNotice(): Flow<Boolean> = flow {
        ensureMigrated()
        emitAll(
            dataStore.data
                .map { it[Keys.migrationNoticePending] ?: false }
                .distinctUntilChanged(),
        )
    }

    override suspend fun create(milestone: GlycemicPlanningMilestone) {
        ensureMigrated()
        val now = timeSource.nowEpochMillis()
        validateMilestoneDraft(
            title = milestone.title,
            targetGmiPercent = milestone.targetGmiPercent,
            targetProvenance = milestone.targetProvenance,
            horizonDays = milestone.originalHorizonDays,
            targetDateEpochMillis = milestone.targetDateEpochMillis,
            nowEpochMillis = now,
        )
        require(milestone.lifecycleState == MilestoneLifecycleState.ACTIVE) {
            "A new milestone must be active."
        }
        require(milestone.calculationContractVersion ==
            GLYCEMIC_MILESTONE_CALCULATION_CONTRACT_VERSION) {
            "The milestone calculation contract is unsupported."
        }
        dao.insertIfAbsent(
            milestone.copy(title = normalizedMilestoneTitle(milestone.title)).toEntity(),
        )
        ensureSelectionAfterCreate(milestone.id)
    }

    override suspend fun update(milestone: GlycemicPlanningMilestone) {
        ensureMigrated()
        val existing = requireNotNull(dao.getById(milestone.id)) {
            "The planning milestone no longer exists."
        }.toModel()
        val now = timeSource.nowEpochMillis()
        val existingTemporal = existing.temporalState(now)
        if (existingTemporal != MilestoneTemporalState.FUTURE) {
            require(milestone.targetGmiPercent == existing.targetGmiPercent) {
                "Past or due milestones cannot change their target."
            }
            require(milestone.targetDateEpochMillis == existing.targetDateEpochMillis) {
                "Past or due milestones cannot change their target date."
            }
            require(milestone.originalHorizonDays == existing.originalHorizonDays) {
                "Past or due milestones cannot change their horizon."
            }
        } else {
            validateMilestoneDraft(
                title = milestone.title,
                targetGmiPercent = milestone.targetGmiPercent,
                targetProvenance = milestone.targetProvenance,
                horizonDays = milestone.originalHorizonDays,
                targetDateEpochMillis = milestone.targetDateEpochMillis,
                nowEpochMillis = now,
            )
        }
        require(milestone.lifecycleState == existing.lifecycleState) {
            "Milestone lifecycle changes must use archive."
        }
        dao.upsert(
            milestone.copy(
                title = normalizedMilestoneTitle(milestone.title),
                createdAtEpochMillis = existing.createdAtEpochMillis,
                updatedAtEpochMillis = now,
                calculationContractVersion = existing.calculationContractVersion,
            ).toEntity(),
        )
    }

    override suspend fun archive(id: String, nowEpochMillis: Long) {
        ensureMigrated()
        dao.archive(id, nowEpochMillis)
        val selected = selectedId()
        if (selected == id) selectFallback(nowEpochMillis)
    }

    override suspend fun delete(id: String) {
        ensureMigrated()
        val selected = selectedId()
        dao.delete(id)
        if (selected == id) selectFallback(timeSource.nowEpochMillis())
    }

    override suspend fun select(id: String?) {
        ensureMigrated()
        id?.let {
            requireNotNull(dao.getById(it)) { "The planning milestone no longer exists." }
        }
        dataStore.edit { values ->
            id?.let { values[Keys.selectedMilestoneId] = it }
                ?: values.remove(Keys.selectedMilestoneId)
        }
    }

    override suspend fun dismissMigrationNotice() {
        ensureMigrated()
        dataStore.edit { it[Keys.migrationNoticePending] = false }
    }

    override suspend fun reset() {
        migrationMutex.withLock {
            dao.deleteAll()
            dataStore.edit {
                it.clear()
                it[Keys.migrationVersion] = MIGRATION_VERSION
                it[Keys.migrationNoticePending] = false
            }
        }
    }

    private suspend fun ensureMigrated() {
        migrationMutex.withLock {
            val values = dataStore.data.first()
            if ((values[Keys.migrationVersion] ?: 0) >= MIGRATION_VERSION) return

            val legacy = legacyGoalRepository.observeSettings().first()
            val legacyTarget = legacy.targetGmiPercent
            if (legacyTarget == null) {
                dataStore.edit { settings ->
                    settings[Keys.migrationVersion] = MIGRATION_VERSION
                    settings[Keys.migrationNoticePending] = false
                }
                return
            }

            val now = timeSource.nowEpochMillis()
            val milestone = GlycemicPlanningMilestone(
                id = LEGACY_MILESTONE_ID,
                title = null,
                targetGmiPercent = legacyTarget,
                targetProvenance = legacy.targetProvenance
                    ?: GlycemicTargetProvenance.USER_ENTERED,
                targetDateEpochMillis = now + legacy.horizon.durationMillis,
                originalHorizonDays = legacy.horizon.days,
                lifecycleState = MilestoneLifecycleState.ACTIVE,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                archivedAtEpochMillis = null,
                calculationContractVersion =
                    GLYCEMIC_MILESTONE_CALCULATION_CONTRACT_VERSION,
            )
            dao.insertIfAbsent(milestone.toEntity())
            dataStore.edit { settings ->
                settings[Keys.selectedMilestoneId] = LEGACY_MILESTONE_ID
                settings[Keys.migrationVersion] = MIGRATION_VERSION
                settings[Keys.migrationNoticePending] = true
            }
        }
    }

    private suspend fun ensureSelectionAfterCreate(id: String) {
        val selected = selectedId()
        if (selected == null) select(id)
    }

    private suspend fun selectFallback(nowEpochMillis: Long) {
        val fallback = dao.getAll()
            .map { it.toModel() }
            .let { sortPlanningMilestones(it, nowEpochMillis) }
            .firstOrNull()
        select(fallback?.id)
    }

    private suspend fun selectedId(): String? = dataStore.data
        .first()[Keys.selectedMilestoneId]

    private object Keys {
        val selectedMilestoneId = stringPreferencesKey("selected_milestone_id")
        val migrationVersion = intPreferencesKey("migration_version")
        val migrationNoticePending = booleanPreferencesKey("migration_notice_pending")
    }

    private companion object {
        const val MIGRATION_VERSION = 1
        const val LEGACY_MILESTONE_ID = "legacy-planner-target-v1"
    }
}
