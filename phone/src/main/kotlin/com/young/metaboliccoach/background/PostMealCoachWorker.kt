package com.young.metaboliccoach.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.young.metaboliccoach.core.domain.CoachingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class PostMealCoachWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val coachingRepository: CoachingRepository,
    private val refreshCoordinator: PhoneRefreshCoordinator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val expectedMealId = inputData.getString(SyncScheduler.MEAL_ID_INPUT)
            ?: return Result.failure()
        val expiresAtEpochMillis = inputData.getLong(
            SyncScheduler.POST_MEAL_EXPIRES_AT_INPUT,
            Long.MIN_VALUE,
        )
        if (expiresAtEpochMillis == Long.MIN_VALUE) return Result.failure()
        if (System.currentTimeMillis() >= expiresAtEpochMillis) return Result.success()
        if (coachingRepository.latestMealMarker()?.id != expectedMealId) {
            return Result.success()
        }
        return try {
            refreshCoordinator.refresh(refreshProviders = true)
            Result.success()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            if (
                shouldRetryPostMealWork(
                    runAttemptCount = runAttemptCount,
                    nowEpochMillis = System.currentTimeMillis(),
                    expiresAtEpochMillis = expiresAtEpochMillis,
                )
            ) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}

internal fun shouldRetryPostMealWork(
    runAttemptCount: Int,
    nowEpochMillis: Long,
    expiresAtEpochMillis: Long,
): Boolean = runAttemptCount + 1 < MAX_POST_MEAL_RUN_ATTEMPTS &&
    nowEpochMillis < expiresAtEpochMillis

private const val MAX_POST_MEAL_RUN_ATTEMPTS = 3
