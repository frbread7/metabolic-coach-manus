package com.young.metaboliccoach.core.data.provider

import android.os.Build
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * xDrip is an optional, user-enabled compatibility route and is not an i-SENS API.
 *
 * Samples arrive through [XdripGlucoseIngestor]; refresh is intentionally a no-op because the
 * documented compatibility broadcast is push-based.
 */
@Singleton
class XdripBroadcastGlucoseProvider @Inject constructor(
    private val glucoseDao: GlucoseDao,
) : GlucoseProvider {
    override val id = PROVIDER_ID

    override suspend fun status(): ProviderStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ProviderStatus(
                providerId = id,
                displayName = "xDrip local broadcast",
                availability = ProviderAvailability.UNSUPPORTED,
                detail = "Android 14 or newer is required for sender-identity verification.",
            )
        }
        val latest = glucoseDao.getLatestForSource(PROVIDER_ID)
        return ProviderStatus(
            providerId = id,
            displayName = "xDrip local broadcast",
            availability = ProviderAvailability.AVAILABLE,
            detail = if (latest == null) {
                "Waiting for a compatible xDrip glucose broadcast."
            } else {
                "Last xDrip sample received at ${latest.receivedAtEpochMillis}."
            },
        )
    }

    override suspend fun readSince(startEpochMillis: Long): List<GlucoseReading> = emptyList()

    companion object {
        const val PROVIDER_ID = "xdrip_broadcast"
    }
}
