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
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.model.MealMarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val nightscoutSettingsRepository: NightscoutSettingsRepository,
) {
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(context)
    }

    suspend fun configurePeriodic() {
        val nightscoutSettings =
            nightscoutSettingsRepository.observeNightscoutSettings().first()
        val nightscoutConfigured = nightscoutSettings.activeServer != null
        val backgroundReadsAvailable = backgroundReadAccessOrFalse {
            HealthConnectPermissions.hasBackgroundReadAccess(context)
        }
        val policy = periodicRefreshPolicy(
            nightscoutConfigured = nightscoutConfigured,
            nightscoutPollingIntervalMinutes =
                nightscoutSettings.pollingIntervalMinutes.toLong(),
            healthConnectBackgroundReadsAvailable = backgroundReadsAvailable,
        )
        if (!policy.enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshAndCoachWorker>(
            repeatInterval = policy.intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(policy.networkType)
                .build(),
        ).setInitialDelay(
            policy.intervalMinutes,
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
        val networkType = if (
            refreshProviders &&
            nightscoutSettingsRepository.observeNightscoutSettings().first().activeServer != null
        ) {
            NetworkType.CONNECTED
        } else {
            NetworkType.NOT_REQUIRED
        }
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<RefreshAndCoachWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build(),
                )
                .setInputData(
                    workDataOf(
                        REFRESH_PROVIDERS_INPUT to refreshProviders,
                        REQUIRE_DELIVERY_INPUT to requireDelivery,
                    ),
                )
                .build(),
        ).await()
    }

    suspend fun schedulePostMealEvaluation(
        marker: MealMarker,
        delayMinutes: Int,
        windowMinutes: Int,
    ) {
        val delayMillis = postMealInitialDelayMillis(
            marker.occurredAtEpochMillis,
            delayMinutes,
            System.currentTimeMillis(),
        )
        val request = OneTimeWorkRequestBuilder<PostMealCoachWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    MEAL_ID_INPUT to marker.id,
                    POST_MEAL_EXPIRES_AT_INPUT to marker.occurredAtEpochMillis +
                        TimeUnit.MINUTES.toMillis(
                            (delayMinutes + windowMinutes).toLong(),
                        ),
                ),
            )
            .addTag(POST_MEAL_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            postMealWorkName(marker.id),
            ExistingWorkPolicy.REPLACE,
            request,
        ).await()
    }

    suspend fun cancelPostMealEvaluations() {
        workManager.cancelAllWorkByTag(POST_MEAL_WORK_TAG).await()
    }

    suspend fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_WORK).await()
        workManager.cancelUniqueWork(IMMEDIATE_WORK).await()
        cancelPostMealEvaluations()
    }

    companion object {
        const val REFRESH_PROVIDERS_INPUT = "refresh_providers"
        const val REQUIRE_DELIVERY_INPUT = "require_delivery"
        const val MEAL_ID_INPUT = "meal_id"
        const val POST_MEAL_EXPIRES_AT_INPUT = "post_meal_expires_at"
        private const val PERIODIC_WORK = "metabolic_periodic_refresh"
        private const val IMMEDIATE_WORK = "metabolic_immediate_refresh"
        private const val POST_MEAL_WORK_TAG = "metabolic_post_meal"

        private fun postMealWorkName(mealId: String) = "$POST_MEAL_WORK_TAG:$mealId"
    }
}

internal fun postMealInitialDelayMillis(
    mealAtEpochMillis: Long,
    delayMinutes: Int,
    nowEpochMillis: Long,
): Long = (
    mealAtEpochMillis + TimeUnit.MINUTES.toMillis(delayMinutes.toLong()) - nowEpochMillis
    ).coerceAtLeast(0L)

internal data class PeriodicRefreshPolicy(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val networkType: NetworkType,
)

internal fun periodicRefreshPolicy(
    nightscoutConfigured: Boolean,
    nightscoutPollingIntervalMinutes: Long,
    healthConnectBackgroundReadsAvailable: Boolean,
): PeriodicRefreshPolicy = when {
    nightscoutConfigured -> PeriodicRefreshPolicy(
        enabled = true,
        intervalMinutes = nightscoutPollingIntervalMinutes.coerceAtLeast(15),
        networkType = NetworkType.CONNECTED,
    )
    healthConnectBackgroundReadsAvailable -> PeriodicRefreshPolicy(
        enabled = true,
        intervalMinutes = 15,
        networkType = NetworkType.NOT_REQUIRED,
    )
    else -> PeriodicRefreshPolicy(
        enabled = false,
        intervalMinutes = 15,
        networkType = NetworkType.NOT_REQUIRED,
    )
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
