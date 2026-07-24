package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.db.toModel
import com.young.metaboliccoach.core.data.provider.CareSensAirProvider
import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.data.provider.HealthConnectGlucoseProvider
import com.young.metaboliccoach.core.data.provider.XdripBroadcastGlucoseProvider
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseRepositoryImpl @Inject constructor(
    private val glucoseDao: GlucoseDao,
    healthConnect: HealthConnectGlucoseProvider,
    careSensAir: CareSensAirProvider,
    xdrip: XdripBroadcastGlucoseProvider,
    private val settingsRepository: SettingsRepository,
) : GlucoseRepository {
    private val providers: Map<GlucoseProviderMode, GlucoseProvider> = mapOf(
        GlucoseProviderMode.HEALTH_CONNECT to healthConnect,
        GlucoseProviderMode.XDRIP_BROADCAST to xdrip,
        GlucoseProviderMode.CARESENS_PARTNER to careSensAir,
    )
    private val providerStatus = MutableStateFlow(
        ProviderStatus(
            providerId = HealthConnectGlucoseProvider.PROVIDER_ID,
            displayName = "Health Connect glucose",
            availability = ProviderAvailability.PERMISSION_REQUIRED,
            detail = "Provider status has not been refreshed.",
        ),
    )
    private val availableOrigins = MutableStateFlow<List<GlucoseDataOrigin>>(emptyList())

    override fun observeLatest(): Flow<GlucoseReading?> =
        settingsRepository.observe().flatMapLatest { settings ->
            settings.selectedGlucoseSourcePrefix()?.let {
                glucoseDao.observeLatestForSource(it)
            } ?: flowOf(null)
        }.map { it?.toModel() }

    override fun observeProviderStatus(): Flow<ProviderStatus> = providerStatus

    override fun observeAvailableOrigins(): Flow<List<GlucoseDataOrigin>> = availableOrigins

    override suspend fun readingsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReading> {
        val sourcePrefix = settingsRepository.observe().first()
            .selectedGlucoseSourcePrefix()
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
        val selected = requireNotNull(providers[mode])
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
        private const val HISTORY_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
    }
}

internal fun GlucoseProviderMode.sourcePrefix(): String = when (this) {
    GlucoseProviderMode.HEALTH_CONNECT -> HealthConnectGlucoseProvider.PROVIDER_ID
    GlucoseProviderMode.XDRIP_BROADCAST -> XdripBroadcastGlucoseProvider.PROVIDER_ID
    GlucoseProviderMode.CARESENS_PARTNER -> CareSensAirProvider.PROVIDER_ID
}

internal fun CoachSettings.selectedGlucoseSourcePrefix(): String? =
    when (val mode = glucoseProviderMode.supportedForCurrentBuild()) {
        GlucoseProviderMode.HEALTH_CONNECT ->
            healthConnectGlucoseOriginPackage?.let {
                "${mode.sourcePrefix()}:$it"
            }
        else -> mode.sourcePrefix()
    }

private fun GlucoseProviderMode.displayName(): String = when (this) {
    GlucoseProviderMode.HEALTH_CONNECT -> "Health Connect glucose"
    GlucoseProviderMode.XDRIP_BROADCAST -> "xDrip broadcast"
    GlucoseProviderMode.CARESENS_PARTNER -> "CareSens Air partner integration"
}
