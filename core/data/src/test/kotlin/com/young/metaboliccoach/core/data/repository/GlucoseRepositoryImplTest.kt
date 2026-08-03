package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.GlucoseReadingEntity
import com.young.metaboliccoach.core.data.db.GlucoseHistoryStatsRow
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.data.provider.nightscout.NightscoutProvider
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.sourceId
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.DefaultNightscoutSettings
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseRepositoryImplTest {
    @Test
    fun `refresh inserts normalized Nightscout readings for the selected server`() = runTest {
        val server = server("primary", "https://primary.example")
        val settings = nightscoutSettings(listOf(server), server.id)
        val reading = reading(
            valueMgDl = 138,
            sourceId = server.sourceId(requireHttps = true),
            measuredAtEpochMillis = 2_000,
        )
        val fixture = repository(
            nightscoutSettings = settings,
            readResults = listOf(Result.success(listOf(reading))),
        )

        fixture.repository.refresh()

        assertEquals(listOf(reading.toEntity()), fixture.dao.values)
        assertEquals(reading, fixture.repository.observeLatest().firstValue())
        assertEquals(1, fixture.provider.readCalls)
        assertEquals(
            ProviderAvailability.AVAILABLE,
            fixture.repository.observeProviderStatus().firstValue().availability,
        )
    }

    @Test
    fun `network failure retains persistent cache and publishes error status`() = runTest {
        val server = server("primary", "https://primary.example")
        val sourceId = server.sourceId(requireHttps = true)
        val cached = reading(
            valueMgDl = 127,
            sourceId = sourceId,
            measuredAtEpochMillis = 1_000,
        )
        val fixture = repository(
            nightscoutSettings = nightscoutSettings(listOf(server), server.id),
            existing = listOf(cached.toEntity()),
            readResults = listOf(Result.failure(IOException("offline"))),
        )

        fixture.repository.refresh()

        assertEquals(listOf(cached.toEntity()), fixture.dao.values)
        assertEquals(cached, fixture.repository.observeLatest().firstValue())
        val status = fixture.repository.observeProviderStatus().firstValue()
        assertEquals(ProviderAvailability.ERROR, status.availability)
        assertTrue(status.detail.contains("cached data retained", ignoreCase = true))
    }

    @Test
    fun `current snapshot is persisted before history failure and remains the latest reading`() =
        runTest {
            val server = server("primary", "https://primary.example")
            val sourceId = server.sourceId(requireHttps = true)
            val stale = reading(120, sourceId, 1_000)
            val current = reading(180, sourceId, 2_000)
            val fixture = repository(
                nightscoutSettings = nightscoutSettings(listOf(server), server.id),
                existing = listOf(stale.toEntity()),
                currentResults = listOf(Result.success(listOf(current))),
                readResults = listOf(Result.failure(IOException("history unavailable"))),
            )

            fixture.repository.refresh()

            assertEquals(current, fixture.repository.observeLatest().firstValue())
            assertTrue(fixture.dao.values.contains(current.toEntity()))
            assertEquals(1, fixture.provider.currentCalls)
            assertEquals(1, fixture.provider.readCalls)
            val status = fixture.repository.observeProviderStatus().firstValue()
            assertEquals(ProviderAvailability.ERROR, status.availability)
            assertTrue(status.detail.contains("history", ignoreCase = true))
            assertTrue(status.detail.contains("cached data retained", ignoreCase = true))
        }

    @Test
    fun `active server switching isolates cached readings by exact source identity`() = runTest {
        val serverA = server("server-a", "https://a.example")
        val serverB = server("server-b", "https://b.example")
        val sourceA = serverA.sourceId(requireHttps = true)
        val sourceB = serverB.sourceId(requireHttps = true)
        val readingA = reading(
            valueMgDl = 111,
            sourceId = sourceA,
            measuredAtEpochMillis = 1_000,
        )
        val readingB = reading(
            valueMgDl = 222,
            sourceId = sourceB,
            measuredAtEpochMillis = 2_000,
        )
        val settings = nightscoutSettings(
            servers = listOf(serverA, serverB),
            activeServerId = serverA.id,
        )
        val fixture = repository(
            nightscoutSettings = settings,
            readResults = listOf(
                Result.success(listOf(readingA)),
                Result.success(listOf(readingB)),
            ),
        )

        fixture.repository.refresh()
        assertEquals(readingA, fixture.repository.observeLatest().firstValue())

        fixture.nightscoutSettingsRepository.updateNightscoutSettings(
            settings.copy(activeServerId = serverB.id),
        )
        fixture.repository.refresh()
        assertEquals(readingB, fixture.repository.observeLatest().firstValue())
        assertEquals(
            listOf(readingB),
            fixture.repository.readingsBetween(0, Long.MAX_VALUE),
        )

        fixture.nightscoutSettingsRepository.updateNightscoutSettings(
            settings.copy(activeServerId = serverA.id),
        )
        assertEquals(readingA, fixture.repository.observeLatest().firstValue())
        assertEquals(
            listOf(readingA),
            fixture.repository.readingsBetween(0, Long.MAX_VALUE),
        )
    }

    @Test
    fun `invalid persisted active server configuration exposes no selected glucose`() = runTest {
        val server = server("invalid", "http://insecure.example")
        val fixture = repository(
            nightscoutSettings = nightscoutSettings(
                servers = listOf(server),
                activeServerId = server.id,
            ),
        )

        assertEquals(null, fixture.repository.observeLatest().firstValue())
        assertTrue(fixture.repository.readingsBetween(0, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun `exact source refresh routes through provider capability and excludes sibling source`() =
        runTest {
            val serverA = server("server-a", "https://a.example")
            val serverB = server("server-b", "https://b.example")
            val sourceA = serverA.sourceId(requireHttps = true)
            val sourceB = serverB.sourceId(requireHttps = true)
            val readingA = reading(111, sourceA, 1_000)
            val readingB = reading(222, sourceB, 2_000)
            val fixture = repository(
                nightscoutSettings = nightscoutSettings(
                    servers = listOf(serverA, serverB),
                    activeServerId = serverA.id,
                ),
                readResults = listOf(Result.success(listOf(readingA, readingB))),
            )

            fixture.repository.refreshExactSource(sourceA)

            assertEquals(listOf(sourceA), fixture.provider.exactSourceCalls)
            assertEquals(listOf(readingA.toEntity()), fixture.dao.values)
        }

    private fun repository(
        nightscoutSettings: NightscoutSettings,
        existing: List<GlucoseReadingEntity> = emptyList(),
        currentResults: List<Result<List<GlucoseReading>>> = emptyList(),
        readResults: List<Result<List<GlucoseReading>>> = emptyList(),
    ): RepositoryFixture {
        val dao = InMemoryGlucoseDao(existing)
        val provider = RecordingGlucoseProvider(
            readResults = readResults,
            currentResults = currentResults,
        )
        val nightscoutSettingsRepository =
            MutableNightscoutSettingsRepository(nightscoutSettings)
        return RepositoryFixture(
            repository = GlucoseRepositoryImpl(
                glucoseDao = dao,
                providers = setOf(provider),
                settingsRepository = MutableSettingsRepository(),
                nightscoutSettingsRepository = nightscoutSettingsRepository,
            ),
            dao = dao,
            provider = provider,
            nightscoutSettingsRepository = nightscoutSettingsRepository,
        )
    }

    private fun nightscoutSettings(
        servers: List<NightscoutServerConfig>,
        activeServerId: String,
    ): NightscoutSettings = DefaultNightscoutSettings.create().copy(
        servers = servers,
        activeServerId = activeServerId,
    )

    private fun server(
        id: String,
        url: String,
    ) = NightscoutServerConfig(
        id = id,
        displayName = id,
        baseUrl = url,
    )

    private fun reading(
        valueMgDl: Int,
        sourceId: String,
        measuredAtEpochMillis: Long,
    ) = GlucoseReading(
        id = "$sourceId:$measuredAtEpochMillis",
        valueMgDl = valueMgDl,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = null,
        rateMgDlPerMinute = null,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = sourceId,
    )

    private data class RepositoryFixture(
        val repository: GlucoseRepositoryImpl,
        val dao: InMemoryGlucoseDao,
        val provider: RecordingGlucoseProvider,
        val nightscoutSettingsRepository: MutableNightscoutSettingsRepository,
    )

    private class RecordingGlucoseProvider(
        readResults: List<Result<List<GlucoseReading>>>,
        currentResults: List<Result<List<GlucoseReading>>> = emptyList(),
    ) : GlucoseProvider {
        private val remaining = ArrayDeque(readResults)
        private val remainingCurrent = ArrayDeque(currentResults)
        var readCalls = 0
            private set
        var currentCalls = 0
            private set
        val exactSourceCalls = mutableListOf<String>()

        override val id: String = NightscoutProvider.PROVIDER_ID

        override fun handlesSource(sourceId: String): Boolean =
            sourceId.startsWith("$id:")

        override fun observeState(): Flow<GlucoseProviderState> =
            flowOf(GlucoseProviderState.Idle)

        override suspend fun status(): ProviderStatus = ProviderStatus(
            providerId = id,
            displayName = "Nightscout",
            availability = ProviderAvailability.AVAILABLE,
            detail = "Ready",
        )

        override suspend fun readCurrent(): List<GlucoseReading> {
            if (remainingCurrent.isEmpty()) return emptyList()
            currentCalls += 1
            check(remainingCurrent.isNotEmpty()) { "No fake current result remains." }
            return remainingCurrent.removeFirst().getOrThrow()
        }

        override suspend fun readSince(startEpochMillis: Long): List<GlucoseReading> {
            readCalls += 1
            check(remaining.isNotEmpty()) { "No fake provider result remains." }
            return remaining.removeFirst().getOrThrow()
        }

        override suspend fun readSinceExactSource(
            sourceId: String,
            startEpochMillis: Long,
        ): List<GlucoseReading> {
            exactSourceCalls += sourceId
            return readSince(startEpochMillis)
                .filter { it.sourceId == sourceId }
        }

        override suspend fun clearRuntimeCache() = Unit
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

    private class InMemoryGlucoseDao(
        initial: List<GlucoseReadingEntity>,
    ) : GlucoseDao {
        private val state = MutableStateFlow(initial)
        val values: List<GlucoseReadingEntity>
            get() = state.value

        override suspend fun getLatest(): GlucoseReadingEntity? =
            state.value.latestOrNull()

        override suspend fun getLatestForSource(sourcePrefix: String): GlucoseReadingEntity? =
            state.value.filterSource(sourcePrefix)
                .latestOrNull()

        override fun observeLatest(): Flow<GlucoseReadingEntity?> =
            state.map { readings -> readings.latestOrNull() }

        override fun observeLatestForSource(
            sourcePrefix: String,
        ): Flow<GlucoseReadingEntity?> = state.map { readings ->
            readings.filterSource(sourcePrefix)
                .latestOrNull()
        }

        override suspend fun readingsBetween(
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> =
            state.value.inRange(startEpochMillis, endEpochMillis)

        override suspend fun readingsBetweenForSource(
            sourcePrefix: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> =
            state.value.filterSource(sourcePrefix)
                .inRange(startEpochMillis, endEpochMillis)

        override suspend fun readingsBetweenExactSource(
            sourceId: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> =
            state.value.filter { it.sourceId == sourceId }
                .inRange(startEpochMillis, endEpochMillis)

        override fun observeSinceForSource(
            sourcePrefix: String,
            startEpochMillis: Long,
        ): Flow<List<GlucoseReadingEntity>> = state.map { readings ->
            readings.filterSource(sourcePrefix)
                .filter { it.measuredAtEpochMillis >= startEpochMillis }
                .sortedBy(GlucoseReadingEntity::measuredAtEpochMillis)
        }

        override fun observeSinceExactSource(
            sourceId: String,
            startEpochMillis: Long,
        ): Flow<List<GlucoseReadingEntity>> = state.map { readings ->
            readings.filter {
                it.sourceId == sourceId &&
                    it.measuredAtEpochMillis >= startEpochMillis
            }.sortedWith(
                compareBy<GlucoseReadingEntity> { it.measuredAtEpochMillis }
                    .thenBy { it.id },
            )
        }

        override fun observeHistoryStatsForSource(
            sourceId: String,
        ): Flow<GlucoseHistoryStatsRow> = state.map { readings ->
            readings.filter { it.sourceId == sourceId }.historyStats()
        }

        override suspend fun getHistoryStatsForSource(sourceId: String): GlucoseHistoryStatsRow =
            state.value.filter { it.sourceId == sourceId }.historyStats()

        override suspend fun getSourceIds(): List<String> =
            state.value.map(GlucoseReadingEntity::sourceId).distinct().sorted()

        override suspend fun deleteOlderThanForSource(
            sourceId: String,
            cutoffEpochMillis: Long,
        ) {
            val newestId = state.value
                .filter { it.sourceId == sourceId }
                .maxWithOrNull(
                    compareBy<GlucoseReadingEntity> { it.measuredAtEpochMillis }
                        .thenBy { it.id },
                )?.id
            state.value = state.value.filter {
                it.sourceId != sourceId ||
                    it.measuredAtEpochMillis >= cutoffEpochMillis ||
                    it.id == newestId
            }
        }

        override suspend fun insertAll(readings: List<GlucoseReadingEntity>) {
            val byId = state.value.associateByTo(linkedMapOf(), GlucoseReadingEntity::id)
            readings.forEach { byId[it.id] = it }
            state.value = byId.values.toList()
        }

        private fun List<GlucoseReadingEntity>.filterSource(
            sourcePrefix: String,
        ): List<GlucoseReadingEntity> = filter {
            it.sourceId == sourcePrefix || it.sourceId.startsWith("$sourcePrefix:")
        }

        private fun List<GlucoseReadingEntity>.latestOrNull(): GlucoseReadingEntity? =
            maxWithOrNull(
                compareBy<GlucoseReadingEntity> { it.measuredAtEpochMillis }
                    .thenBy { it.sourceId }
                    .thenBy { it.id },
            )

        private fun List<GlucoseReadingEntity>.inRange(
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> = filter {
            it.measuredAtEpochMillis in startEpochMillis..endEpochMillis
        }.sortedBy(GlucoseReadingEntity::measuredAtEpochMillis)

        private fun List<GlucoseReadingEntity>.historyStats(): GlucoseHistoryStatsRow =
            GlucoseHistoryStatsRow(
                oldestReadingAtEpochMillis = minOfOrNull { it.measuredAtEpochMillis },
                newestReadingAtEpochMillis = maxOfOrNull { it.measuredAtEpochMillis },
                readingCount = size.toLong(),
            )
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()
}
