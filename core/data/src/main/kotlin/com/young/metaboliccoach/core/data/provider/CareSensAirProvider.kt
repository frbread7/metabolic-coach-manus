package com.young.metaboliccoach.core.data.provider

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capability boundary for a future vendor-approved CareSens Air integration.
 *
 * This provider intentionally returns no readings until i-SENS exposes an integration contract
 * that the application is authorized to use. It must never scrape private app storage or rely on
 * undocumented broadcasts.
 */
@Singleton
class CareSensAirProvider @Inject constructor() : GlucoseProvider {
    override val id = PROVIDER_ID

    override fun handlesSource(sourceId: String): Boolean =
        sourceId == PROVIDER_ID || sourceId.startsWith("$PROVIDER_ID:")

    override suspend fun status() = ProviderStatus(
        providerId = id,
        displayName = "CareSens Air",
        availability = ProviderAvailability.PARTNER_APPROVAL_REQUIRED,
        detail = "No authorized public CareSens Air data interface is configured.",
    )

    override suspend fun readSince(startEpochMillis: Long): List<GlucoseReading> = emptyList()

    override suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading> = emptyList()

    override suspend fun clearRuntimeCache() = Unit

    companion object {
        const val PROVIDER_ID = "caresens_air"
    }
}
