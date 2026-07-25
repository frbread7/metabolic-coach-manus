package com.young.metaboliccoach.core.data.provider.nightscout

import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsValidator
import com.young.metaboliccoach.core.domain.sourceId
import com.young.metaboliccoach.core.model.GlucoseProviderFailure
import com.young.metaboliccoach.core.model.GlucoseProviderFailureKind
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import java.io.IOException
import java.io.InterruptedIOException
import javax.net.ssl.SSLException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NightscoutRetrySleeper {
    suspend fun sleep(delayMillis: Long)
}

@Singleton
class CoroutineNightscoutRetrySleeper @Inject constructor() : NightscoutRetrySleeper {
    override suspend fun sleep(delayMillis: Long) = delay(delayMillis)
}

class NightscoutHttpException(
    val statusCode: Int,
) : IOException("Nightscout request failed with HTTP status $statusCode.")

@Singleton
class NightscoutProvider @Inject constructor(
    private val settingsRepository: NightscoutSettingsRepository,
    private val settingsValidator: NightscoutSettingsValidator,
    private val apiClient: NightscoutApiClient,
    private val parser: NightscoutJsonParser,
    private val timeSource: CoachTimeSource,
    private val retrySleeper: NightscoutRetrySleeper,
) : GlucoseProvider {
    override val id: String = PROVIDER_ID

    private val refreshMutex = Mutex()
    private val state = MutableStateFlow<GlucoseProviderState>(GlucoseProviderState.Idle)
    private val cacheBySource = mutableMapOf<String, List<GlucoseReading>>()
    private val lastModifiedBySource = mutableMapOf<String, String>()

    override fun handlesSource(sourceId: String): Boolean =
        sourceId.startsWith("$PROVIDER_ID:")

    override fun observeState(): Flow<GlucoseProviderState> = state.asStateFlow()

    override suspend fun status(): ProviderStatus {
        val settings = settingsRepository.observeNightscoutSettings().first()
        val errors = settingsValidator.validate(settings)
        val active = settings.activeServer
        return when {
            errors.isNotEmpty() -> ProviderStatus(
                providerId = id,
                displayName = DISPLAY_NAME,
                availability = ProviderAvailability.CONFIGURATION_REQUIRED,
                detail = errors.first(),
            )
            active == null -> ProviderStatus(
                providerId = id,
                displayName = DISPLAY_NAME,
                availability = ProviderAvailability.CONFIGURATION_REQUIRED,
                detail = "Configure and select a Nightscout server in Settings.",
            )
            else -> ProviderStatus(
                providerId = id,
                displayName = DISPLAY_NAME,
                availability = ProviderAvailability.AVAILABLE,
                detail = "Ready to retrieve glucose from ${active.displayName}.",
            )
        }
    }

    override suspend fun readSince(startEpochMillis: Long): List<GlucoseReading> =
        refreshMutex.withLock {
            val settings = settingsValidator.normalize(
                settingsRepository.observeNightscoutSettings().first(),
            )
            retainConfiguredSourceCaches(settings)
            val server = settings.activeServer
            if (server == null) {
                state.value = GlucoseProviderState.ConfigurationRequired
                return@withLock emptyList()
            }
            readServerLocked(
                server = server,
                settings = settings,
                startEpochMillis = startEpochMillis,
                publishState = true,
            )
        }

    override suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading> = refreshMutex.withLock {
        val settings = settingsValidator.normalize(
            settingsRepository.observeNightscoutSettings().first(),
        )
        retainConfiguredSourceCaches(settings)
        val server = settings.configuredServers.firstOrNull {
            it.sourceId(settings.requireHttps) == sourceId
        } ?: return@withLock emptyList()
        readServerLocked(
            server = server,
            settings = settings,
            startEpochMillis = startEpochMillis,
            publishState = settings.activeServer
                ?.sourceId(settings.requireHttps) == sourceId,
        )
    }

    override suspend fun clearRuntimeCache() {
        refreshMutex.withLock {
            cacheBySource.clear()
            lastModifiedBySource.clear()
            state.value = GlucoseProviderState.Idle
        }
    }

    private fun retainConfiguredSourceCaches(settings: NightscoutSettings) {
        val configuredSourceIds = settings.configuredServers
            .mapTo(mutableSetOf()) { it.sourceId(settings.requireHttps) }
        cacheBySource.keys.retainAll(configuredSourceIds)
        lastModifiedBySource.keys.retainAll(configuredSourceIds)
    }

    private suspend fun readServerLocked(
        server: NightscoutServerConfig,
        settings: NightscoutSettings,
        startEpochMillis: Long,
        publishState: Boolean,
    ): List<GlucoseReading> {
        val sourceId = server.sourceId(settings.requireHttps)
        val cached = cacheBySource[sourceId].orEmpty()
        if (publishState) {
            state.value = GlucoseProviderState.Loading(cached.lastOrNull())
        }
        return try {
            val readings = fetchWithRetry(
                server = server,
                sourceId = sourceId,
                connectionTimeoutSeconds = settings.connectionTimeoutSeconds,
                retryIntervalSeconds = settings.retryIntervalSeconds,
                maximumRetryAttempts = settings.maximumRetryAttempts,
            )
            if (readings.isNotEmpty()) {
                cacheBySource[sourceId] = readings
                if (publishState) {
                    state.value = GlucoseProviderState.Available(
                        reading = readings.last(),
                        refreshedAtEpochMillis = timeSource.nowEpochMillis(),
                    )
                }
            } else if (publishState) {
                state.value = GlucoseProviderState.Degraded(
                    cached = cached.lastOrNull(),
                    failure = GlucoseProviderFailure(
                        kind = GlucoseProviderFailureKind.RESPONSE,
                        detail = "The active Nightscout server returned no glucose readings.",
                        retryable = false,
                    ),
                )
            }
            (if (readings.isEmpty()) cached else readings)
                .filter { it.measuredAtEpochMillis >= startEpochMillis }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (publishState) {
                val failure = error.toProviderFailure()
                state.value = GlucoseProviderState.Degraded(
                    cached = cached.lastOrNull(),
                    failure = failure,
                )
            }
            throw error
        }
    }

    private suspend fun fetchWithRetry(
        server: NightscoutServerConfig,
        sourceId: String,
        connectionTimeoutSeconds: Int,
        retryIntervalSeconds: Int,
        maximumRetryAttempts: Int,
    ): List<GlucoseReading> {
        var retryIndex = 0
        while (true) {
            try {
                val response = apiClient.fetchEntries(
                    server = server,
                    connectionTimeoutSeconds = connectionTimeoutSeconds,
                    ifModifiedSince = lastModifiedBySource[sourceId]
                        ?.takeIf { cacheBySource[sourceId].orEmpty().isNotEmpty() },
                )
                return when (response.statusCode) {
                    HTTP_OK -> {
                        val body = response.body
                            ?: throw NightscoutParseException(
                                "Nightscout returned an empty response.",
                            )
                        val parsed = parser.parse(
                            body = body,
                            sourceId = sourceId,
                            receivedAtEpochMillis = timeSource.nowEpochMillis(),
                        )
                        response.lastModified?.let { lastModifiedBySource[sourceId] = it }
                        parsed
                    }
                    HTTP_NOT_MODIFIED -> cacheBySource[sourceId].orEmpty()
                    else -> throw NightscoutHttpException(response.statusCode)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (!error.isRetryable() || retryIndex >= maximumRetryAttempts) throw error
                val baseDelay = retryIntervalSeconds * MILLIS_PER_SECOND
                val delayMillis = (baseDelay shl retryIndex)
                    .coerceAtMost(MAXIMUM_SINGLE_RETRY_DELAY_MILLIS)
                retrySleeper.sleep(delayMillis)
                retryIndex += 1
            }
        }
    }

    private fun Throwable.isRetryable(): Boolean = when (this) {
        is SSLException -> false
        is InterruptedIOException -> true
        is NightscoutHttpException ->
            statusCode == 408 || statusCode == 429 || statusCode in 500..599
        is NightscoutResponseTooLargeException -> false
        is NightscoutParseException -> false
        is IllegalArgumentException -> false
        is IOException -> true
        else -> false
    }

    private fun Throwable.toProviderFailure(): GlucoseProviderFailure = when (this) {
        is SSLException -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.CONNECTIVITY,
            detail = "Nightscout TLS verification failed. Check the HTTPS server.",
            retryable = false,
        )
        is InterruptedIOException -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.TIMEOUT,
            detail = "Nightscout timed out; cached glucose was retained.",
            retryable = true,
        )
        is NightscoutHttpException -> when (statusCode) {
            401, 403 -> GlucoseProviderFailure(
                kind = GlucoseProviderFailureKind.AUTHENTICATION,
                detail = "Nightscout denied access. Check the server configuration.",
                retryable = false,
            )
            in 500..599 -> GlucoseProviderFailure(
                kind = GlucoseProviderFailureKind.SERVER,
                detail = "Nightscout is temporarily unavailable; cached glucose was retained.",
                retryable = true,
            )
            else -> GlucoseProviderFailure(
                kind = GlucoseProviderFailureKind.RESPONSE,
                detail = "Nightscout returned an unsupported response.",
                retryable = isRetryable(),
            )
        }
        is NightscoutParseException -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.RESPONSE,
            detail = "Nightscout returned glucose data that could not be read.",
            retryable = false,
        )
        is NightscoutResponseTooLargeException -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.RESPONSE,
            detail = "Nightscout returned more glucose data than the app can safely read.",
            retryable = false,
        )
        is IOException -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.CONNECTIVITY,
            detail = "Nightscout could not be reached; cached glucose was retained.",
            retryable = true,
        )
        is IllegalArgumentException -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.CONFIGURATION,
            detail = "Nightscout configuration is invalid.",
            retryable = false,
        )
        else -> GlucoseProviderFailure(
            kind = GlucoseProviderFailureKind.UNKNOWN,
            detail = "Nightscout refresh failed; cached glucose was retained.",
            retryable = false,
        )
    }

    companion object {
        const val PROVIDER_ID = "nightscout"
        private const val DISPLAY_NAME = "Nightscout"
        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MAXIMUM_SINGLE_RETRY_DELAY_MILLIS = MILLIS_PER_MINUTE
    }
}
