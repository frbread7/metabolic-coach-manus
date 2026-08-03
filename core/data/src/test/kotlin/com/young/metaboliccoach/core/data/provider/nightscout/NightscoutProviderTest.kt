package com.young.metaboliccoach.core.data.provider.nightscout

import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsValidator
import com.young.metaboliccoach.core.domain.sourceId
import com.young.metaboliccoach.core.model.DefaultNightscoutSettings
import com.young.metaboliccoach.core.model.GlucoseProviderFailureKind
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.ProviderAvailability
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NightscoutProviderTest {
    @Test
    fun `status is configuration required until an active server is configured`() = runTest {
        val unconfigured = provider(settings = DefaultNightscoutSettings.create())
        val configured = provider(settings = settings())

        assertEquals(
            ProviderAvailability.CONFIGURATION_REQUIRED,
            unconfigured.provider.status().availability,
        )
        assertEquals(
            ProviderAvailability.AVAILABLE,
            configured.provider.status().availability,
        )
        assertEquals(0, unconfigured.apiClient.requests.size)
        assertEquals(0, configured.apiClient.requests.size)
    }

    @Test
    fun `successful fetch exposes normalized readings and available state`() = runTest {
        val fixture = provider(
            settings = settings(),
            responses = listOf(ok(body = body(value = 138, remoteId = "success"))),
        )

        val readings = fixture.provider.readSince(NOW - DAY)

        assertEquals(1, readings.size)
        assertEquals(138, readings.single().valueMgDl)
        assertEquals(
            fixture.settingsRepository.value.activeServer?.sourceId(requireHttps = true),
            readings.single().sourceId,
        )
        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Available)
        state as GlucoseProviderState.Available
        assertEquals(readings.single(), state.reading)
        assertEquals(NOW, state.refreshedAtEpochMillis)
    }

    @Test
    fun `current snapshot is fetched before bounded history and survives history failure`() =
        runTest {
        val start = NOW - (6 * DAY)
        val fixture = provider(
            settings = settings(),
            responses = listOf(
                ok(bodyAt(value = 142, remoteId = "current", measuredAt = NOW - FIVE_MINUTES)),
                Result.failure(NightscoutResponseTooLargeException()),
            ),
        )

        val readings = fixture.provider.readSince(start)

        assertEquals(listOf(142), readings.map { it.valueMgDl })
        assertEquals(listOf("current", "range"), fixture.apiClient.events)
        assertEquals(1, fixture.apiClient.rangeRequests.size)
        assertEquals(2_500, fixture.apiClient.rangeRequests.single().count)
        assertEquals(start, fixture.apiClient.rangeRequests.single().startEpochMillis)
    }

    @Test
    fun `older backfill completion cannot regress the current provider state`() = runTest {
        val start = NOW - (6 * DAY)
        val fixture = provider(
            settings = settings(),
            responses = listOf(
                ok(bodyAt(value = 150, remoteId = "current", measuredAt = NOW)),
                ok(bodyAt(value = 110, remoteId = "backfill", measuredAt = NOW - DAY)),
            ),
        )

        val readings = fixture.provider.readSince(start)

        assertEquals(listOf(110, 150), readings.map { it.valueMgDl })
        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Available)
        assertEquals(150, (state as GlucoseProviderState.Available).reading.valueMgDl)
    }

    @Test
    fun `retryable failures use bounded exponential delays before success`() = runTest {
        val fixture = provider(
            settings = settings(
                retryIntervalSeconds = 5,
                maximumRetryAttempts = 2,
            ),
            responses = listOf(
                Result.failure(IOException("offline")),
                Result.failure(SocketTimeoutException("slow")),
                ok(body = body(value = 144, remoteId = "after-retries")),
            ),
        )

        val readings = fixture.provider.readSince(0)

        assertEquals(144, readings.single().valueMgDl)
        assertEquals(3, fixture.apiClient.requests.size)
        assertEquals(listOf(5_000L, 10_000L), fixture.retrySleeper.delays)
    }

    @Test
    fun `exponential retry delay never exceeds the sixty second safety cap`() = runTest {
        val fixture = provider(
            settings = settings(
                retryIntervalSeconds = 60,
                maximumRetryAttempts = 3,
            ),
            responses = listOf(
                Result.failure(IOException("offline-1")),
                Result.failure(IOException("offline-2")),
                Result.failure(IOException("offline-3")),
                ok(body = body(value = 140, remoteId = "bounded-retry")),
            ),
        )

        fixture.provider.readSince(0)

        assertEquals(listOf(60_000L, 60_000L, 60_000L), fixture.retrySleeper.delays)
    }

    @Test
    fun `authentication and other client failures are not retried`() = runTest {
        listOf(401, 403, 404).forEach { statusCode ->
            val fixture = provider(
                settings = settings(maximumRetryAttempts = 3),
                responses = listOf(response(statusCode = statusCode)),
            )

            val thrown = expectThrows(NightscoutHttpException::class.java) {
                fixture.provider.readSince(0)
            }

            assertEquals(statusCode, thrown.statusCode)
            assertEquals(1, fixture.apiClient.requests.size)
            assertTrue(fixture.retrySleeper.delays.isEmpty())
            val state = fixture.provider.observeState().first()
            assertTrue(state is GlucoseProviderState.Degraded)
            state as GlucoseProviderState.Degraded
            assertNull(state.cached)
            if (statusCode == 401 || statusCode == 403) {
                assertEquals(GlucoseProviderFailureKind.AUTHENTICATION, state.failure.kind)
            }
            assertEquals(false, state.failure.retryable)
        }
    }

    @Test
    fun `oversized response is reported as a non-retryable response failure`() = runTest {
        val fixture = provider(
            settings = settings(maximumRetryAttempts = 3),
            responses = listOf(Result.failure(NightscoutResponseTooLargeException())),
        )

        expectThrows(NightscoutResponseTooLargeException::class.java) {
            fixture.provider.readSince(0)
        }

        assertEquals(1, fixture.apiClient.requests.size)
        assertTrue(fixture.retrySleeper.delays.isEmpty())
        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Degraded)
        state as GlucoseProviderState.Degraded
        assertEquals(GlucoseProviderFailureKind.RESPONSE, state.failure.kind)
        assertEquals(false, state.failure.retryable)
    }

    @Test
    fun `current refresh does not use conditional validators and rejects an unexpected 304`() =
        runTest {
        val fixture = provider(
            settings = settings(),
            responses = listOf(
                ok(
                    body = body(value = 136, remoteId = "cached"),
                    lastModified = "Wed, 22 Jul 2026 10:00:00 GMT",
                ),
                response(statusCode = 304),
            ),
        )

        val initial = fixture.provider.readSince(0)
        val thrown = expectThrows(NightscoutHttpException::class.java) {
            fixture.provider.readSince(0)
        }

        assertEquals(304, thrown.statusCode)
        assertNull(fixture.apiClient.requests.first().ifModifiedSince)
        assertNull(fixture.apiClient.requests.last().ifModifiedSince)
        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Degraded)
        assertEquals(initial.last(), (state as GlucoseProviderState.Degraded).cached)
    }

    @Test
    fun `cached glucose is retained and exposed when a later refresh fails`() = runTest {
        val fixture = provider(
            settings = settings(maximumRetryAttempts = 0),
            responses = listOf(
                ok(body = body(value = 132, remoteId = "cached-before-failure")),
                Result.failure(IOException("network unavailable")),
            ),
        )

        val initial = fixture.provider.readSince(0)
        expectThrows(IOException::class.java) {
            fixture.provider.readSince(0)
        }

        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Degraded)
        state as GlucoseProviderState.Degraded
        assertEquals(initial.last(), state.cached)
        assertEquals(GlucoseProviderFailureKind.CONNECTIVITY, state.failure.kind)
        assertTrue(state.failure.retryable)
    }

    @Test
    fun `clearing runtime cache removes readings and current validators`() = runTest {
        val fixture = provider(
            settings = settings(),
            responses = listOf(
                ok(
                    body = body(value = 132, remoteId = "sensitive-cache"),
                    lastModified = "cached-last-modified",
                ),
                response(statusCode = 304),
            ),
        )
        fixture.provider.readSince(0)

        fixture.provider.clearRuntimeCache()
        val thrown = expectThrows(NightscoutHttpException::class.java) {
            fixture.provider.readSince(0)
        }

        assertEquals(304, thrown.statusCode)
        assertNull(fixture.apiClient.requests.last().ifModifiedSince)
        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Degraded)
        assertNull((state as GlucoseProviderState.Degraded).cached)
    }

    @Test
    fun `cache and conditional request metadata remain isolated across server switches`() =
        runTest {
            val serverA = server(id = "server-a", baseUrl = "https://a.example")
            val serverB = server(id = "server-b", baseUrl = "https://b.example")
            val fixture = provider(
                settings = settings(
                    servers = listOf(serverA, serverB),
                    activeServerId = serverA.id,
                ),
                responses = listOf(
                    ok(
                        body = body(value = 111, remoteId = "a-reading"),
                        lastModified = "a-last-modified",
                    ),
                    ok(
                        body = body(value = 222, remoteId = "b-reading"),
                        lastModified = "b-last-modified",
                    ),
                    response(statusCode = 304),
                ),
            )

            val fromA = fixture.provider.readSince(0)
            fixture.settingsRepository.updateNightscoutSettings(
                fixture.settingsRepository.value.copy(activeServerId = serverB.id),
            )
            val fromB = fixture.provider.readSince(0)
            fixture.settingsRepository.updateNightscoutSettings(
                fixture.settingsRepository.value.copy(activeServerId = serverA.id),
            )
            val thrown = expectThrows(NightscoutHttpException::class.java) {
                fixture.provider.readSince(0)
            }

            assertEquals(111, fromA.single().valueMgDl)
            assertEquals(222, fromB.single().valueMgDl)
            assertEquals(304, thrown.statusCode)
            assertEquals(
                listOf(null, null, null),
                fixture.apiClient.requests.map { it.ifModifiedSince },
            )
            assertEquals(
                listOf(serverA.id, serverB.id, serverA.id),
                fixture.apiClient.requests.map { it.server.id },
            )
            assertTrue(fromA.single().sourceId != fromB.single().sourceId)
        }

    @Test
    fun `newest current reading wins regardless of backfill completion order`() {
        val sourceId = "nightscout:server-1"
        val current = reading(
            value = 155,
            remoteId = "current",
            measuredAt = NOW,
            sourceId = sourceId,
        )
        val olderBackfill = reading(
            value = 120,
            remoteId = "backfill",
            measuredAt = NOW - DAY,
            sourceId = sourceId,
        )

        assertEquals(
            current,
            mergeReadingsByNewest(listOf(olderBackfill), listOf(current)).last(),
        )
        assertEquals(
            current,
            mergeReadingsByNewest(listOf(current), listOf(olderBackfill)).last(),
        )
    }

    @Test
    fun `duplicate and out of order records choose a deterministic newest valid record`() {
        val sourceId = "nightscout:server-1"
        val duplicateOlder = reading(
            value = 140,
            remoteId = "duplicate",
            measuredAt = NOW - FIVE_MINUTES,
            receivedAt = NOW - 4 * FIVE_MINUTES,
            sourceId = sourceId,
        )
        val duplicateNewer = duplicateOlder.copy(
            valueMgDl = 141,
            receivedAtEpochMillis = NOW,
        )
        val newest = reading(
            value = 150,
            remoteId = "newest",
            measuredAt = NOW,
            sourceId = sourceId,
        )

        val merged = mergeReadingsByNewest(
            listOf(newest, duplicateOlder),
            listOf(duplicateNewer),
        )

        assertEquals(listOf(duplicateNewer, newest), merged)
    }

    @Test
    fun `successful refresh replaces stale runtime cache immediately`() = runTest {
        val fixture = provider(
            settings = settings(),
            responses = listOf(
                ok(bodyAt(value = 100, remoteId = "old", measuredAt = NOW - DAY)),
                ok(bodyAt(value = 160, remoteId = "new", measuredAt = NOW)),
            ),
        )

        fixture.provider.readSince(0)
        val refreshed = fixture.provider.readSince(0)

        assertEquals(160, refreshed.maxByOrNull { it.measuredAtEpochMillis }?.valueMgDl)
        val state = fixture.provider.observeState().first()
        assertTrue(state is GlucoseProviderState.Available)
        assertEquals(160, (state as GlucoseProviderState.Available).reading.valueMgDl)
    }

    @Test
    fun `cancellation propagates without retry or degraded failure state`() = runTest {
        val cancellation = CancellationException("caller stopped")
        val fixture = provider(
            settings = settings(maximumRetryAttempts = 3),
            responses = listOf(Result.failure(cancellation)),
        )

        val thrown = expectThrows(CancellationException::class.java) {
            fixture.provider.readSince(0)
        }

        assertSame(cancellation, thrown)
        assertEquals(1, fixture.apiClient.requests.size)
        assertTrue(fixture.retrySleeper.delays.isEmpty())
        assertTrue(fixture.provider.observeState().first() is GlucoseProviderState.Loading)
    }

    @Test
    fun `cancellation during retry delay stops before another network request`() = runTest {
        val sleeper = RecordingRetrySleeper(cancelOnSleep = true)
        val fixture = provider(
            settings = settings(maximumRetryAttempts = 3),
            responses = listOf(Result.failure(IOException("offline"))),
            retrySleeper = sleeper,
        )

        expectThrows(CancellationException::class.java) {
            fixture.provider.readSince(0)
        }

        assertEquals(1, fixture.apiClient.requests.size)
        assertEquals(listOf(5_000L), sleeper.delays)
    }

    private fun provider(
        settings: NightscoutSettings,
        responses: List<Result<NightscoutHttpResponse>> = emptyList(),
        retrySleeper: RecordingRetrySleeper = RecordingRetrySleeper(),
    ): ProviderFixture {
        val repository = MutableNightscoutSettingsRepository(settings)
        val apiClient = RecordingNightscoutApiClient(responses)
        return ProviderFixture(
            provider = NightscoutProvider(
                settingsRepository = repository,
                settingsValidator = NightscoutSettingsValidator(),
                apiClient = apiClient,
                parser = NightscoutJsonParser(),
                timeSource = FixedTimeSource(NOW),
                retrySleeper = retrySleeper,
            ),
            settingsRepository = repository,
            apiClient = apiClient,
            retrySleeper = retrySleeper,
        )
    }

    private fun settings(
        servers: List<NightscoutServerConfig> = listOf(server()),
        activeServerId: String? = servers.firstOrNull()?.id,
        retryIntervalSeconds: Int = 5,
        maximumRetryAttempts: Int = 2,
    ): NightscoutSettings = DefaultNightscoutSettings.create().copy(
        servers = servers,
        activeServerId = activeServerId,
        retryIntervalSeconds = retryIntervalSeconds,
        maximumRetryAttempts = maximumRetryAttempts,
    )

    private fun server(
        id: String = "server-1",
        baseUrl: String = "https://example.com",
    ) = NightscoutServerConfig(
        id = id,
        displayName = id,
        baseUrl = baseUrl,
    )

    private fun body(
        value: Int,
        remoteId: String,
    ): String = """
        [
          {
            "_id":"$remoteId",
            "sgv":$value,
            "date":${NOW - FIVE_MINUTES},
            "direction":"Flat"
          }
        ]
    """.trimIndent()

    private fun bodyAt(
        value: Int,
        remoteId: String,
        measuredAt: Long,
    ): String = """
        [
          {
            "_id":"$remoteId",
            "sgv":$value,
            "date":$measuredAt,
            "direction":"Flat"
          }
        ]
    """.trimIndent()

    private fun reading(
        value: Int,
        remoteId: String,
        measuredAt: Long,
        receivedAt: Long = measuredAt,
        sourceId: String,
    ): GlucoseReading = GlucoseReading(
        id = remoteId,
        valueMgDl = value,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = null,
        rateMgDlPerMinute = null,
        measuredAtEpochMillis = measuredAt,
        receivedAtEpochMillis = receivedAt,
        sourceId = sourceId,
    )

    private fun ok(
        body: String,
        lastModified: String? = null,
    ): Result<NightscoutHttpResponse> = response(
        statusCode = 200,
        body = body,
        lastModified = lastModified,
    )

    private fun response(
        statusCode: Int,
        body: String? = null,
        lastModified: String? = null,
    ): Result<NightscoutHttpResponse> = Result.success(
        NightscoutHttpResponse(
            statusCode = statusCode,
            body = body,
            lastModified = lastModified,
        ),
    )

    private data class ProviderFixture(
        val provider: NightscoutProvider,
        val settingsRepository: MutableNightscoutSettingsRepository,
        val apiClient: RecordingNightscoutApiClient,
        val retrySleeper: RecordingRetrySleeper,
    )

    private data class RecordedApiRequest(
        val server: NightscoutServerConfig,
        val connectionTimeoutSeconds: Int,
        val ifModifiedSince: String?,
    )

    private class RecordingNightscoutApiClient(
        responses: List<Result<NightscoutHttpResponse>>,
    ) : NightscoutApiClient {
        private val remaining = ArrayDeque(responses)
        val requests = mutableListOf<RecordedApiRequest>()
        val rangeRequests = mutableListOf<RecordedRangeRequest>()
        val events = mutableListOf<String>()

        override suspend fun fetchEntries(
            server: NightscoutServerConfig,
            connectionTimeoutSeconds: Int,
            ifModifiedSince: String?,
        ): NightscoutHttpResponse {
            events += "current"
            requests += RecordedApiRequest(
                server = server,
                connectionTimeoutSeconds = connectionTimeoutSeconds,
                ifModifiedSince = ifModifiedSince,
            )
            check(remaining.isNotEmpty()) { "No fake Nightscout response remains." }
            return remaining.removeFirst().getOrThrow()
        }

        override suspend fun fetchEntriesInRange(
            server: NightscoutServerConfig,
            connectionTimeoutSeconds: Int,
            startEpochMillis: Long,
            endEpochMillis: Long,
            count: Int,
        ): NightscoutHttpResponse {
            events += "range"
            rangeRequests += RecordedRangeRequest(
                server = server,
                connectionTimeoutSeconds = connectionTimeoutSeconds,
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
                count = count,
            )
            check(remaining.isNotEmpty()) { "No fake Nightscout response remains." }
            return remaining.removeFirst().getOrThrow()
        }
    }

    private data class RecordedRangeRequest(
        val server: NightscoutServerConfig,
        val connectionTimeoutSeconds: Int,
        val startEpochMillis: Long,
        val endEpochMillis: Long,
        val count: Int,
    )

    private class RecordingRetrySleeper(
        private val cancelOnSleep: Boolean = false,
    ) : NightscoutRetrySleeper {
        val delays = mutableListOf<Long>()

        override suspend fun sleep(delayMillis: Long) {
            delays += delayMillis
            if (cancelOnSleep) throw CancellationException("retry cancelled")
        }
    }

    private class MutableNightscoutSettingsRepository(
        initial: NightscoutSettings,
    ) : NightscoutSettingsRepository {
        private val state = MutableStateFlow(initial)
        val value: NightscoutSettings
            get() = state.value

        override fun observeNightscoutSettings(): Flow<NightscoutSettings> = state

        override suspend fun updateNightscoutSettings(settings: NightscoutSettings) {
            state.value = settings
        }
    }

    private class FixedTimeSource(
        private val now: Long,
    ) : CoachTimeSource {
        override fun nowEpochMillis(): Long = now

        override fun minuteTicks(): Flow<Long> = emptyFlow()
    }

    private suspend fun <T : Throwable> expectThrows(
        type: Class<T>,
        block: suspend () -> Unit,
    ): T {
        try {
            block()
            fail("Expected ${type.simpleName}")
        } catch (error: Throwable) {
            if (!type.isInstance(error)) {
                throw AssertionError(
                    "Expected ${type.simpleName}, got ${error.javaClass.simpleName}",
                    error,
                )
            }
            return checkNotNull(type.cast(error))
        }
        error("Unreachable")
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val FIVE_MINUTES = 5 * 60_000L
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
