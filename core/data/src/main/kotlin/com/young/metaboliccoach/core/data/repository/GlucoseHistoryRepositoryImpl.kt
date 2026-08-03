package com.young.metaboliccoach.core.data.repository

import androidx.room.withTransaction
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.GlucoseHistoryBackfillEntity
import com.young.metaboliccoach.core.data.db.GlucoseHistoryDao
import com.young.metaboliccoach.core.data.db.GlucoseHistorySettingsEntity
import com.young.metaboliccoach.core.data.db.GlucoseHistoryStatsRow
import com.young.metaboliccoach.core.data.db.MetabolicCoachDatabase
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.GlucoseHistoryRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.GlucoseHistoryBackfillStatus
import com.young.metaboliccoach.core.model.GlucoseHistoryRetentionPolicy
import com.young.metaboliccoach.core.model.GlucoseHistorySettings
import com.young.metaboliccoach.core.model.GlucoseHistoryStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseHistoryRepositoryImpl @Inject constructor(
    private val database: MetabolicCoachDatabase,
    private val glucoseDao: GlucoseDao,
    private val historyDao: GlucoseHistoryDao,
    private val providers: Set<@JvmSuppressWildcards GlucoseProvider>,
    private val settingsRepository: SettingsRepository,
    private val nightscoutSettingsRepository: NightscoutSettingsRepository,
    private val timeSource: CoachTimeSource,
) : GlucoseHistoryRepository {
    override fun observeStatus(): Flow<GlucoseHistoryStatus> = selectedSourceFlow()
        .flatMapLatest { sourceId ->
            val stats = sourceId?.let { glucoseDao.observeHistoryStatsForSource(it) }
                ?: flowOf(GlucoseHistoryStatsRow(null, null, 0))
            val backfill = sourceId?.let { historyDao.observeBackfill(it) }
                ?: flowOf(null)
            combine(historyDao.observeSettings(), stats, backfill) { settings, row, state ->
                val sourceState = state?.toModel()
                GlucoseHistoryStatus(
                    settings = settings?.toModel() ?: GlucoseHistorySettings(),
                    sourceId = sourceId,
                    oldestReadingAtEpochMillis = row.oldestReadingAtEpochMillis,
                    newestReadingAtEpochMillis = row.newestReadingAtEpochMillis,
                    readingCount = row.readingCount,
                    backfillStatus = sourceState?.status ?: GlucoseHistoryBackfillStatus.IDLE,
                    nextBackfillEndEpochMillis = sourceState?.nextBackfillEndEpochMillis,
                    lastError = sourceState?.lastError,
                )
            }
        }

    override suspend fun updateSettings(settings: GlucoseHistorySettings) {
        val existing = historyDao.getSettings()?.toModel()
        val confirmed = if (
            existing?.retentionPolicy == settings.retentionPolicy
        ) {
            settings.retentionConfirmed
        } else {
            // A changed retention boundary always requires a fresh explicit confirmation.
            false
        }
        historyDao.upsertSettings(
            settings.toEntity(retentionConfirmed = confirmed),
        )
    }

    override suspend fun confirmRetentionPolicy() {
        val settings = historyDao.getSettings()?.toModel() ?: GlucoseHistorySettings()
        val now = timeSource.nowEpochMillis()
        database.withTransaction {
            historyDao.upsertSettings(settings.copy(retentionConfirmed = true).toEntity())
            val cutoff = settings.retentionPolicy.cutoffEpochMillis(now)
            if (cutoff != null) {
                glucoseDao.getSourceIds().forEach { sourceId ->
                    glucoseDao.deleteOlderThanForSource(sourceId, cutoff)
                }
            }
        }
    }

    override suspend fun backfillNextChunk() {
        val settings = historyDao.getSettings()?.toModel() ?: GlucoseHistorySettings()
        check(settings.retentionConfirmed) {
            "Confirm the retention policy before downloading older history."
        }
        val sourceId = selectedSourceFlow().first()
            ?: error("Configure and select a glucose source before backfilling history.")
        val provider = providers.singleOrNull { it.handlesSource(sourceId) }
            ?: error("No provider is registered for the selected glucose source.")
        val now = timeSource.nowEpochMillis()
        val lowerBound = settings.retentionPolicy.cutoffEpochMillis(now)
        val stats = glucoseDao.getHistoryStatsForSource(sourceId)
        val previous = historyDao.getBackfill(sourceId)
        val endExclusive = (previous?.nextBackfillEndEpochMillis
            ?: stats.oldestReadingAtEpochMillis
            ?: (now + 1L)).coerceAtMost(now + 1L)
        if (lowerBound != null && endExclusive <= lowerBound) {
            historyDao.upsertBackfill(
                GlucoseHistoryBackfillEntity(
                    sourceId = sourceId,
                    nextBackfillEndEpochMillis = lowerBound,
                    status = GlucoseHistoryBackfillStatus.COMPLETE.name,
                    lastError = null,
                    updatedAtEpochMillis = now,
                ),
            )
            return
        }
        val start = maxOf(
            endExclusive - BACKFILL_CHUNK_MILLIS,
            lowerBound ?: Long.MIN_VALUE,
        )
        val endInclusive = endExclusive - 1L
        check(start <= endInclusive) { "The selected history range is empty." }
        historyDao.upsertBackfill(
            GlucoseHistoryBackfillEntity(
                sourceId = sourceId,
                nextBackfillEndEpochMillis = endExclusive,
                status = GlucoseHistoryBackfillStatus.RUNNING.name,
                lastError = null,
                updatedAtEpochMillis = now,
            ),
        )
        try {
            val readings = provider.readHistoryRange(start, endInclusive)
                .filter {
                    it.sourceId == sourceId &&
                        it.measuredAtEpochMillis in start..endInclusive
                }
            if (readings.isNotEmpty()) {
                glucoseDao.insertAll(readings.map { it.toEntity() })
            }
            val complete = lowerBound != null && start <= lowerBound
            historyDao.upsertBackfill(
                GlucoseHistoryBackfillEntity(
                    sourceId = sourceId,
                    nextBackfillEndEpochMillis = start,
                    status = when {
                        complete -> GlucoseHistoryBackfillStatus.COMPLETE.name
                        readings.isEmpty() -> GlucoseHistoryBackfillStatus.PAUSED.name
                        else -> GlucoseHistoryBackfillStatus.IDLE.name
                    },
                    lastError = if (readings.isEmpty()) {
                        "No readings were returned for this range; the checkpoint advanced."
                    } else {
                        null
                    },
                    updatedAtEpochMillis = timeSource.nowEpochMillis(),
                ),
            )
        } catch (cancellation: CancellationException) {
            historyDao.upsertBackfill(
                GlucoseHistoryBackfillEntity(
                    sourceId = sourceId,
                    nextBackfillEndEpochMillis = endExclusive,
                    status = GlucoseHistoryBackfillStatus.PAUSED.name,
                    lastError = "Backfill paused before this range completed.",
                    updatedAtEpochMillis = timeSource.nowEpochMillis(),
                ),
            )
            throw cancellation
        } catch (error: Throwable) {
            historyDao.upsertBackfill(
                GlucoseHistoryBackfillEntity(
                    sourceId = sourceId,
                    nextBackfillEndEpochMillis = endExclusive,
                    status = GlucoseHistoryBackfillStatus.FAILED.name,
                    lastError = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".take(
                        MAX_ERROR_LENGTH,
                    ),
                    updatedAtEpochMillis = timeSource.nowEpochMillis(),
                ),
            )
            throw error
        }
    }

    private fun selectedSourceFlow(): Flow<String?> = combine(
        settingsRepository.observe(),
        nightscoutSettingsRepository.observeNightscoutSettings(),
    ) { coachSettings, nightscoutSettings ->
        coachSettings.selectedGlucoseSourcePrefix(nightscoutSettings)
    }

    private fun GlucoseHistorySettingsEntity.toModel() = GlucoseHistorySettings(
        retentionPolicy = runCatching {
            GlucoseHistoryRetentionPolicy.valueOf(retentionPolicy)
        }.getOrDefault(GlucoseHistoryRetentionPolicy.LAST_90_DAYS),
        retentionConfirmed = retentionConfirmed,
    )

    private fun GlucoseHistorySettings.toEntity(
        retentionConfirmed: Boolean = this.retentionConfirmed,
    ) = GlucoseHistorySettingsEntity(
        retentionPolicy = retentionPolicy.name,
        retentionConfirmed = retentionConfirmed,
    )

    private fun GlucoseHistoryBackfillEntity.toModel() = GlucoseHistoryStatusBackfill(
        nextBackfillEndEpochMillis = nextBackfillEndEpochMillis,
        status = runCatching {
            GlucoseHistoryBackfillStatus.valueOf(status)
        }.getOrDefault(GlucoseHistoryBackfillStatus.PAUSED).let { persisted ->
            if (persisted == GlucoseHistoryBackfillStatus.RUNNING) {
                GlucoseHistoryBackfillStatus.PAUSED
            } else {
                persisted
            }
        },
        lastError = lastError ?: status.takeIf {
            it == GlucoseHistoryBackfillStatus.RUNNING.name
        }?.let { "Backfill was interrupted and can be resumed." },
    )

    private data class GlucoseHistoryStatusBackfill(
        val nextBackfillEndEpochMillis: Long?,
        val status: GlucoseHistoryBackfillStatus,
        val lastError: String?,
    )

    private companion object {
        const val BACKFILL_CHUNK_MILLIS = 90L * 24L * 60L * 60L * 1_000L
        const val MAX_ERROR_LENGTH = 240
    }
}
