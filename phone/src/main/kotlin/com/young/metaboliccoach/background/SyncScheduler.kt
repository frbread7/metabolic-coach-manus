package com.young.metaboliccoach.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.young.metaboliccoach.core.data.provider.HealthConnectPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(context)
    }

    suspend fun configurePeriodic() {
        val backgroundReadsAvailable = backgroundReadAccessOrFalse {
            HealthConnectPermissions.hasBackgroundReadAccess(context)
        }
        if (!backgroundReadsAvailable) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshAndCoachWorker>(
            repeatInterval = PERIODIC_INTERVAL_MINUTES,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build(),
        ).setInitialDelay(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).setInputData(
            workDataOf(REFRESH_PROVIDERS_INPUT to true),
        ).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun enqueueImmediate(
        refreshProviders: Boolean = false,
        requireDelivery: Boolean = false,
    ) {
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<RefreshAndCoachWorker>()
                .setInputData(
                    workDataOf(
                        REFRESH_PROVIDERS_INPUT to refreshProviders,
                        REQUIRE_DELIVERY_INPUT to requireDelivery,
                    ),
                )
                .build(),
        ).await()
    }

    suspend fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_WORK).await()
        workManager.cancelUniqueWork(IMMEDIATE_WORK).await()
    }

    companion object {
        const val REFRESH_PROVIDERS_INPUT = "refresh_providers"
        const val REQUIRE_DELIVERY_INPUT = "require_delivery"
        private const val PERIODIC_INTERVAL_MINUTES = 15L
        private const val PERIODIC_WORK = "metabolic_periodic_refresh"
        private const val IMMEDIATE_WORK = "metabolic_immediate_refresh"
    }
}

internal suspend fun backgroundReadAccessOrFalse(
    check: suspend () -> Boolean,
): Boolean = try {
    check()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    false
}
