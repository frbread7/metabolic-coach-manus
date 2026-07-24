package com.young.metaboliccoach.core.data.provider

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Boundary for Samsung Health Data SDK access.
 *
 * Samsung requires partner registration, an approved package name, and a registered release
 * certificate before production data access. Keeping that contract behind this provider prevents
 * partner-only SDK details from leaking into the repository or UI.
 */
@Singleton
class SamsungHealthPartnerDataProvider @Inject constructor() : ActivityDataProvider {
    override val id = PROVIDER_ID

    override suspend fun status() = ProviderStatus(
        providerId = id,
        displayName = "Samsung Health Data SDK",
        availability = ProviderAvailability.PARTNER_APPROVAL_REQUIRED,
        detail = "Samsung partner approval and release-certificate registration are required.",
    )

    override suspend fun readToday(now: Instant): ActivitySnapshot? = null

    companion object {
        const val PROVIDER_ID = "samsung_health_data_sdk"
    }
}
