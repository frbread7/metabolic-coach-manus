package com.young.metaboliccoach.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class RefreshAndCoachWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val refreshCoordinator: PhoneRefreshCoordinator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            refreshCoordinator.refresh(
                refreshProviders = inputData.getBoolean(
                    SyncScheduler.REFRESH_PROVIDERS_INPUT,
                    true,
                ),
            )
            Result.success()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            if (
                inputData.getBoolean(SyncScheduler.REQUIRE_DELIVERY_INPUT, false) ||
                runAttemptCount < MAX_RETRY_COUNT
            ) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }
}
