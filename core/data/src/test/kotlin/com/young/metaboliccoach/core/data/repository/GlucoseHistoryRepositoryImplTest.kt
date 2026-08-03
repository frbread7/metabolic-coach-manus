package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.GlucoseHistoryBackfillEntity
import com.young.metaboliccoach.core.data.db.GlucoseHistoryDao
import com.young.metaboliccoach.core.data.db.GlucoseHistorySettingsEntity
import com.young.metaboliccoach.core.data.db.GlucoseHistoryStatsRow
import com.young.metaboliccoach.core.data.db.GlucoseReadingEntity
import com.young.metaboliccoach.core.data.db.MetabolicCoachDatabase
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.DefaultNightscoutSettings
import com.young.metaboliccoach.core.model.GlucoseHistoryBackfillStatus
import com.young.metaboliccoach.core.model.GlucoseHistoryRetentionPolicy
import com.young.metaboliccoach.core.model.GlucoseHistorySettings
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.domain.sourceId
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class GlucoseHistoryRepositoryImplTest {
    @Test
    fun `backfill persists only the selected source and completes at the retention boundary`() =
        runTest {
            val now = 100L * DAY_MILLIS
            val server = NightscoutServerConfig(
                id = "primary",
                displayName = "Primary",
                baseUrl = "https://primary.example",
            )
            val sourceId = server.sourceId(requireHttps = true)
            val older = reading(sourceId, now - 2L * DAY_MILLIS)
            val selectedBackfill = reading(sourceId, now - 80L * DAY_MILLIS)
            val siblingBackfill = reading("nightscout:https://other.example", now - 80L * DAY_MILLIS)
            val glucoseDao = InMemoryGlucoseDao(listOf(older.toEntity()))
            val historyDao = RecordingHistoryDao(
                settings = GlucoseHistorySettingsEntity(
                    retentionPolicy = GlucoseHistoryRetentionPolicy.LAST_90_DAYS.name,
                    retentionConfirmed = true,
                ),
            )
            val provider = RangeProvider(sourceId, listOf(selectedBackfill, siblingBackfill))
            val repository = repository(
                now = now,
                server = server,
                glucoseDao = glucoseDao,
                historyDao = historyDao,
                provider = provider,
            )

            repository.backfillNextChunk()

            assertEquals(
                listOf(older.toEntity(), selectedBackfill.toEntity()),
                glucoseDao.values,
            )
            assertEquals(1, provider.rangeCalls.size)
            assertEquals(now - 90L * DAY_MILLIS, provider.rangeCalls.single().first)
            assertEquals(
                GlucoseHistoryBackfillStatus.COMPLETE.name,
                historyDao.backfill?.status,
            )
            assertEquals(now - 90L * DAY_MILLIS, historyDao.backfill?.nextBackfillEndEpochMillis)
        }

    @Test
    fun `failed backfill retains its durable checkpoint and records failure state`() = runTest {
        val now = 100L * DAY_MILLIS
        val server = NightscoutServerConfig(
            id = "primary",
            displayName = "Primary",
            baseUrl = "https://primary.example",
        )
        val sourceId = server.sourceId(requireHttps = true)
        val checkpoint = now - 2L * DAY_MILLIS
        val historyDao = RecordingHistoryDao(
            settings = GlucoseHistorySettingsEntity(
                retentionPolicy = GlucoseHistoryRetentionPolicy.LAST_YEAR.name,
                retentionConfirmed = true,
            ),
            backfill = GlucoseHistoryBackfillEntity(
                sourceId = sourceId,
                nextBackfillEndEpochMillis = checkpoint,
                status = GlucoseHistoryBackfillStatus.PAUSED.name,
                lastError = null,
                updatedAtEpochMillis = now - 1L,
            ),
        )
        val provider = RangeProvider(sourceId, failure = IOException("offline"))
        val repository = repository(
            now = now,
            server = server,
            glucoseDao = InMemoryGlucoseDao(emptyList()),
            historyDao = historyDao,
            provider = provider,
        )

        var thrown: Throwable? = null
        try {
            repository.backfillNextChunk()
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IOException)
        assertEquals(checkpoint, historyDao.backfill?.nextBackfillEndEpochMillis)
        assertEquals(GlucoseHistoryBackfillStatus.FAILED.name, historyDao.backfill?.status)
        assertTrue(historyDao.backfill?.lastError.orEmpty().contains("IOException"))
    }

    private fun repository(
        now: Long,
        server: NightscoutServerConfig,
        glucoseDao: GlucoseDao,
        historyDao: GlucoseHistoryDao,
        provider: GlucoseProvider,
    ) = GlucoseHistoryRepositoryImpl(
        database = mock(MetabolicCoachDatabase::class.java),
        glucoseDao = glucoseDao,
        historyDao = historyDao,
        providers = setOf(provider),
        settingsRepository = MutableSettingsRepository(),
        nightscoutSettingsRepository = MutableNightscoutSettingsRepository(
            DefaultNightscoutSettings.create().copy(
                servers = listOf(server),
                activeServerId = server.id,
            ),
        ),
        timeSource = FixedTimeSource(now),
    )

    private fun reading(sourceId: String, measuredAt: Long) = GlucoseReading(
        id = "$sourceId:$measuredAt",
        valueMgDl = 140,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = null,
        rateMgDlPerMinute = null,
        measuredAtEpochMillis = measuredAt,
        receivedAtEpochMillis = measuredAt,
        sourceId = sourceId,
    )

    private class FixedTimeSource(private val now: Long) : CoachTimeSource {
        override fun nowEpochMillis(): Long = now
        override fun minuteTicks(): Flow<Long> = flowOf(now)
    }

    private class MutableSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(DefaultCoachSettings.create())
        override fun observe(): Flow<CoachSettings> = state
        override suspend fun update(settings: CoachSettings) {
            state.value = settings
        }
        override suspend fun reset() {
            state.value = DefaultCoachSettings.create()
        }
    }

    private class MutableNightscoutSettingsRepository(
        initial: NightscoutSettings,
    ) : NightscoutSettingsRepository {
        private val state = MutableStateFlow(initial)
        override fun observeNightscoutSettings(): Flow<NightscoutSettings> = state
        override suspend fun updateNightscoutSettings(settings: NightscoutSettings) {
            state.value = settings
        }
    }

    private class RangeProvider(
        private val sourceId: String,
        private val readings: List<GlucoseReading> = emptyList(),
        private val failure: Throwable? = null,
    ) : GlucoseProvider {
        val rangeCalls = mutableListOf<Pair<Long, Long>>()
        override val id: String = "nightscout"
        override fun handlesSource(sourceId: String): Boolean = sourceId == this.sourceId
        override fun observeState(): Flow<GlucoseProviderState> = flowOf(GlucoseProviderState.Idle)
        override suspend fun status() = ProviderStatus(
            providerId = id,
            displayName = "Nightscout",
            availability = ProviderAvailability.AVAILABLE,
            detail = "Ready",
        )
        override suspend fun readCurrent(): List<GlucoseReading> = emptyList()
        override suspend fun readHistorySince(startEpochMillis: Long): List<GlucoseReading> =
            readings.filter { it.measuredAtEpochMillis >= startEpochMillis }
        override suspend fun readHistoryRange(
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReading> {
            rangeCalls += startEpochMillis to endEpochMillis
            failure?.let { throw it }
            return readings
        }
        override suspend fun readSince(startEpochMillis: Long): List<GlucoseReading> =
            readHistorySince(startEpochMillis)
        override suspend fun readSinceExactSource(
            sourceId: String,
            startEpochMillis: Long,
        ): List<GlucoseReading> = readHistorySince(startEpochMillis).filter {
            it.sourceId == sourceId
        }
        override suspend fun clearRuntimeCache() = Unit
    }

    private class RecordingHistoryDao(
        settings: GlucoseHistorySettingsEntity,
        backfill: GlucoseHistoryBackfillEntity? = null,
    ) : GlucoseHistoryDao {
        private val settingsState = MutableStateFlow<GlucoseHistorySettingsEntity?>(settings)
        private val backfillState = MutableStateFlow(backfill)
        var backfill: GlucoseHistoryBackfillEntity?
            get() = backfillState.value
            private set(value) {
                backfillState.value = value
            }

        override fun observeSettings(): Flow<GlucoseHistorySettingsEntity?> = settingsState
        override suspend fun getSettings(): GlucoseHistorySettingsEntity? = settingsState.value
        override suspend fun upsertSettings(settings: GlucoseHistorySettingsEntity) {
            settingsState.value = settings
        }
        override fun observeBackfill(sourceId: String): Flow<GlucoseHistoryBackfillEntity?> =
            backfillState.map { it?.takeIf { state -> state.sourceId == sourceId } }
        override suspend fun getBackfill(sourceId: String): GlucoseHistoryBackfillEntity? =
            backfillState.value?.takeIf { it.sourceId == sourceId }
        override suspend fun upsertBackfill(state: GlucoseHistoryBackfillEntity) {
            backfill = state
        }
        override suspend fun deleteAllBackfill() {
            backfill = null
        }
    }

    private class InMemoryGlucoseDao(initial: List<GlucoseReadingEntity>) : GlucoseDao {
        private val state = MutableStateFlow(initial)
        val values: List<GlucoseReadingEntity> get() = state.value

        override suspend fun getLatest(): GlucoseReadingEntity? = state.value.latestOrNull()
        override suspend fun getLatestForSource(sourcePrefix: String): GlucoseReadingEntity? =
            state.value.filterSource(sourcePrefix).latestOrNull()
        override fun observeLatest(): Flow<GlucoseReadingEntity?> = state.map { it.latestOrNull() }
        override fun observeLatestForSource(sourcePrefix: String): Flow<GlucoseReadingEntity?> =
            state.map { it.filterSource(sourcePrefix).latestOrNull() }
        override suspend fun readingsBetween(startEpochMillis: Long, endEpochMillis: Long) =
            state.value.inRange(startEpochMillis, endEpochMillis)
        override suspend fun readingsBetweenForSource(
            sourcePrefix: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ) = state.value.filterSource(sourcePrefix).inRange(startEpochMillis, endEpochMillis)
        override suspend fun readingsBetweenExactSource(
            sourceId: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ) = state.value.filter { it.sourceId == sourceId }.inRange(startEpochMillis, endEpochMillis)
        override fun observeSinceForSource(
            sourcePrefix: String,
            startEpochMillis: Long,
        ): Flow<List<GlucoseReadingEntity>> = state.map {
            it.filterSource(sourcePrefix).filter { reading ->
                reading.measuredAtEpochMillis >= startEpochMillis
            }
        }
        override fun observeSinceExactSource(
            sourceId: String,
            startEpochMillis: Long,
        ): Flow<List<GlucoseReadingEntity>> = state.map {
            it.filter { reading ->
                reading.sourceId == sourceId && reading.measuredAtEpochMillis >= startEpochMillis
            }
        }
        override fun observeHistoryStatsForSource(sourceId: String): Flow<GlucoseHistoryStatsRow> =
            state.map { it.filter { reading -> reading.sourceId == sourceId }.stats() }
        override suspend fun getHistoryStatsForSource(sourceId: String) =
            state.value.filter { it.sourceId == sourceId }.stats()
        override suspend fun getSourceIds(): List<String> =
            state.value.map(GlucoseReadingEntity::sourceId).distinct()
        override suspend fun deleteOlderThanForSource(sourceId: String, cutoffEpochMillis: Long) {
            state.value = state.value.filter {
                it.sourceId != sourceId || it.measuredAtEpochMillis >= cutoffEpochMillis
            }
        }
        override suspend fun insertAll(readings: List<GlucoseReadingEntity>) {
            val byId = state.value.associateByTo(linkedMapOf(), GlucoseReadingEntity::id)
            readings.forEach { byId[it.id] = it }
            state.value = byId.values.toList()
        }

        private fun List<GlucoseReadingEntity>.filterSource(prefix: String) = filter {
            it.sourceId == prefix || it.sourceId.startsWith("$prefix:")
        }

        private fun List<GlucoseReadingEntity>.latestOrNull() = maxByOrNull {
            it.measuredAtEpochMillis
        }

        private fun List<GlucoseReadingEntity>.inRange(start: Long, end: Long) = filter {
            it.measuredAtEpochMillis in start..end
        }.sortedBy(GlucoseReadingEntity::measuredAtEpochMillis)

        private fun List<GlucoseReadingEntity>.stats() = GlucoseHistoryStatsRow(
            oldestReadingAtEpochMillis = minOfOrNull { it.measuredAtEpochMillis },
            newestReadingAtEpochMillis = maxOfOrNull { it.measuredAtEpochMillis },
            readingCount = size.toLong(),
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
