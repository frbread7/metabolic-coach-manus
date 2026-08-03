package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.db.toModel
import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.data.provider.HealthConnectGlucoseProvider
import com.young.metaboliccoach.core.data.provider.nightscout.NightscoutProvider
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.sourceId
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseRepositoryImpl @Inject constructor(
    private val glucoseDao: GlucoseDao,
    providers: Set<@JvmSuppressWildcards GlucoseProvider>,
    private val settingsRepository: SettingsRepository,
    private val nightscoutSettingsRepository: NightscoutSettingsRepository,
) : GlucoseRepository {
    private val providersById = providers.associateBy(GlucoseProvider::id)

    init {
        require(providersById.size == providers.size) {
            "Glucose provider IDs must be unique."
        }
    }

    private val providerStatus = MutableStateFlow(
        ProviderStatus(
            providerId = NightscoutProvider.PROVIDER_ID,
            displayName = "Nightscout",
            availability = ProviderAvailability.CONFIGURATION_REQUIRED,
            detail = "Configure and select a Nightscout server in Settings.",
        ),
    )
    private val availableOrigins = MutableStateFlow<List<GlucoseDataOrigin>>(emptyList())

    override fun observeLatest(): Flow<GlucoseReading?> =
        combine(
            settingsRepository.observe(),
            nightscoutSettingsRepository.observeNightscoutSettings(),
        ) { settings, nightscoutSettings ->
            settings.selectedGlucoseSourcePrefix(nightscoutSettings)
        }.flatMapLatest { sourcePrefix ->
            sourcePrefix?.let {
                glucoseDao.observeLatestForSource(it)
            } ?: flowOf(null)
        }.map { it?.toModel() }

    override fun observeProviderStatus(): Flow<ProviderStatus> = providerStatus

    override fun observeProviderState(): Flow<GlucoseProviderState> =
        combine(
            settingsRepository.observe(),
            nightscoutSettingsRepository.observeNightscoutSettings(),
        ) { settings, nightscoutSettings ->
            settings to nightscoutSettings
        }.flatMapLatest { (settings, nightscoutSettings) ->
            val provider = providerFor(settings.glucoseProviderMode)
            provider.observeState().map { providerState ->
                if (settings.glucoseProviderMode.supportedForCurrentBuild() !=
                    GlucoseProviderMode.NIGHTSCOUT
                ) {
                    providerState
                } else {
                    providerState.forSelectedNightscoutSource(nightscoutSettings)
                }
            }
        }

    override fun observeAvailableOrigins(): Flow<List<GlucoseDataOrigin>> = availableOrigins

    override suspend fun readingsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReading> {
        val settings = settingsRepository.observe().first()
        val nightscoutSettings =
            nightscoutSettingsRepository.observeNightscoutSettings().first()
        val sourcePrefix = settings
            .selectedGlucoseSourcePrefix(nightscoutSettings)
            ?: return emptyList()
        return glucoseDao.readingsBetweenForSource(
            sourcePrefix = sourcePrefix,
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
        ).map { it.toModel() }
    }

    override suspend fun readingsBetweenExactSource(
        sourceId: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReading> = glucoseDao.readingsBetweenExactSource(
        sourceId = sourceId,
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
    ).map { it.toModel() }

    override suspend fun refresh() {
        val settings = settingsRepository.observe().first()
        val mode = settings.glucoseProviderMode.supportedForCurrentBuild()
        val selected = providerFor(mode)
        if (mode != GlucoseProviderMode.HEALTH_CONNECT) {
            availableOrigins.value = emptyList()
        }
        val status = try {
            selected.status()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ProviderStatus(
                providerId = selected.id,
                displayName = mode.displayName(),
                availability = ProviderAvailability.ERROR,
                detail = "Provider status check failed (${error.javaClass.simpleName}).",
            )
        }
        providerStatus.value = status
        if (status.availability != ProviderAvailability.AVAILABLE) {
            if (mode == GlucoseProviderMode.HEALTH_CONNECT) {
                availableOrigins.value = emptyList()
            }
            return
        }
        val start = System.currentTimeMillis() - HISTORY_LOOKBACK_MILLIS
        val readings = try {
            selected.readSince(start)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (mode == GlucoseProviderMode.HEALTH_CONNECT) {
                availableOrigins.value = emptyList()
            }
            providerStatus.value = status.copy(
                availability = ProviderAvailability.ERROR,
                detail = "Provider read failed (${error.javaClass.simpleName}); cached data retained.",
            )
            return
        }
        if (mode == GlucoseProviderMode.HEALTH_CONNECT) {
            applyHealthConnectSelection(
                settings = settings,
                status = status,
                readings = readings,
            )
        } else {
            glucoseDao.insertAll(readings.map { it.toEntity() })
        }
    }

    override suspend fun refreshExactSource(sourceId: String) {
        val matchingProviders = providersById.values.filter {
            it.handlesSource(sourceId)
        }
        require(matchingProviders.size == 1) {
            "Expected one glucose provider for the requested exact source."
        }
        val provider = matchingProviders.single()
        val readings = provider.readSinceExactSource(
            sourceId = sourceId,
            startEpochMillis = System.currentTimeMillis() - HISTORY_LOOKBACK_MILLIS,
        )
        if (readings.isNotEmpty()) {
            glucoseDao.insertAll(readings.map { it.toEntity() })
        }
    }

    override suspend fun clearRuntimeCaches() {
        var firstFailure: Throwable? = null
        providersById.values.forEach { provider ->
            try {
                provider.clearRuntimeCache()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }
        availableOrigins.value = emptyList()
        providerStatus.value = ProviderStatus(
            providerId = NightscoutProvider.PROVIDER_ID,
            displayName = "Nightscout",
            availability = ProviderAvailability.CONFIGURATION_REQUIRED,
            detail = "Configure and select a Nightscout server in Settings.",
        )
        firstFailure?.let { throw it }
    }

    private fun providerFor(mode: GlucoseProviderMode): GlucoseProvider {
        val providerId = mode.supportedForCurrentBuild().providerId()
        return requireNotNull(providersById[providerId]) {
            "No glucose provider is registered for $providerId."
        }
    }

    private suspend fun applyHealthConnectSelection(
        settings: CoachSettings,
        status: ProviderStatus,
        readings: List<GlucoseReading>,
    ) {
        val selection = HealthConnectOriginSelectionPolicy.select(
            readings = readings,
            configuredPackageName = settings.healthConnectGlucoseOriginPackage,
        )
        availableOrigins.value = selection.availableOrigins
        if (selection.requiresUserSelection) {
            providerStatus.value = status.copy(
                availability = ProviderAvailability.CONFIGURATION_REQUIRED,
                detail = "Multiple glucose sources were found. Choose one in Settings; " +
                    "coaching remains paused until then.",
            )
            return
        }
        selection.autoSelectedPackageName?.let { packageName ->
            settingsRepository.update(
                settings.copy(healthConnectGlucoseOriginPackage = packageName),
            )
        }
        val selectedPackage = selection.selectedPackageName
        if (selectedPackage == null) {
            providerStatus.value = status.copy(
                detail = "No glucose records were found in the last 24 hours.",
            )
            return
        }
        if (selection.selectedReadings.isEmpty()) {
            providerStatus.value = status.copy(
                detail = "The selected source $selectedPackage has no records in the last " +
                    "24 hours. The selection was retained.",
            )
            return
        }
        glucoseDao.insertAll(selection.selectedReadings.map { it.toEntity() })
        providerStatus.value = status.copy(
            detail = "Using $selectedPackage for glucose. " +
                "${selection.availableOrigins.size} source(s) were discovered.",
        )
    }

    companion object {
        private const val HISTORY_LOOKBACK_MILLIS = 90 * 24 * 60 * 60 * 1_000L
    }
}

internal fun GlucoseProviderMode.sourcePrefix(): String = when (this) {
    GlucoseProviderMode.NIGHTSCOUT -> NightscoutProvider.PROVIDER_ID
    GlucoseProviderMode.HEALTH_CONNECT -> HealthConnectGlucoseProvider.PROVIDER_ID
    GlucoseProviderMode.XDRIP_BROADCAST -> "xdrip_broadcast"
    GlucoseProviderMode.CARESENS_PARTNER -> "caresens_air"
}

private fun GlucoseProviderMode.providerId(): String =
    supportedForCurrentBuild().sourcePrefix()

internal fun CoachSettings.selectedGlucoseSourcePrefix(
    nightscoutSettings: NightscoutSettings,
): String? =
    when (val mode = glucoseProviderMode.supportedForCurrentBuild()) {
        GlucoseProviderMode.NIGHTSCOUT ->
            nightscoutSettings.activeServer?.let { server ->
                runCatching {
                    server.sourceId(nightscoutSettings.requireHttps)
                }.getOrNull()
            }
        GlucoseProviderMode.HEALTH_CONNECT ->
            healthConnectGlucoseOriginPackage?.let {
                "${mode.sourcePrefix()}:$it"
            }
        else -> mode.sourcePrefix()
    }

private fun GlucoseProviderMode.displayName(): String = when (this) {
    GlucoseProviderMode.NIGHTSCOUT -> "Nightscout"
    GlucoseProviderMode.HEALTH_CONNECT -> "Health Connect glucose"
    GlucoseProviderMode.XDRIP_BROADCAST -> "xDrip broadcast"
    GlucoseProviderMode.CARESENS_PARTNER -> "CareSens Air partner integration"
}

private fun GlucoseProviderState.forSelectedNightscoutSource(
    settings: NightscoutSettings,
): GlucoseProviderState {
    val selectedSource = settings.activeServer
        ?.let { runCatching { it.sourceId(settings.requireHttps) }.getOrNull() }
        ?: return GlucoseProviderState.ConfigurationRequired
    return when (this) {
        GlucoseProviderState.Idle -> this
        GlucoseProviderState.ConfigurationRequired -> this
        is GlucoseProviderState.Loading ->
            copy(cached = cached?.takeIf { it.sourceId == selectedSource })
        is GlucoseProviderState.Available ->
            takeIf { reading.sourceId == selectedSource }
                ?: GlucoseProviderState.Loading(cached = null)
        is GlucoseProviderState.Degraded ->
            copy(cached = cached?.takeIf { it.sourceId == selectedSource })
    }
}
