package com.young.metaboliccoach.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.FollowUpReadingSelector
import com.young.metaboliccoach.core.domain.FollowUpSelection
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.InterventionStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class InterventionFollowUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val coachingRepository: CoachingRepository,
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val mutationGate: PhoneDataMutationGate,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        mutationGate.withLock {
            doWorkLocked()
        }

    private suspend fun doWorkLocked(): Result {
        return try {
            val sessionId = inputData.getString(SESSION_ID_KEY) ?: return Result.failure()
            val session = coachingRepository.session(sessionId) ?: return Result.success()
            val dueAt = session.followUpDueAtEpochMillis ?: return Result.success()
            if (
                session.status != InterventionStatus.COMPLETED ||
                session.followUpFinalizedAtEpochMillis != null
            ) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            if (now < dueAt) return Result.retry()

            try {
                glucoseRepository.refresh()
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                // Cached exact-source readings may still be sufficient for deterministic finalization.
            }
            val settings = settingsRepository.observe().first()
            val toleranceMillis = settings.staleReadingMinutes * MILLIS_PER_MINUTE
            val windowEnd = minOf(now, dueAt + toleranceMillis)
            val baselineSourceId = session.baselineGlucoseSourceId
            if (baselineSourceId == null) {
                finalize(sessionId, null)
                return Result.success()
            }
            val readings = glucoseRepository.readingsBetweenExactSource(
                sourceId = baselineSourceId,
                startEpochMillis = dueAt - toleranceMillis,
                endEpochMillis = windowEnd,
            )
            when (
                val selection = FollowUpReadingSelector.select(
                    readings = readings,
                    exactSourceId = baselineSourceId,
                    dueAtEpochMillis = dueAt,
                    deadlineEpochMillis = dueAt + toleranceMillis,
                    nowEpochMillis = now,
                )
            ) {
                FollowUpSelection.Wait -> return Result.retry()
                is FollowUpSelection.Finalize -> finalize(sessionId, selection.reading)
            }
            Result.success()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private suspend fun finalize(
        sessionId: String,
        reading: com.young.metaboliccoach.core.model.GlucoseReading?,
    ) {
        if (
            coachingRepository.finalizeFollowUp(
                sessionId = sessionId,
                glucoseMgDl = reading?.valueMgDl,
                readingAtEpochMillis = reading?.measuredAtEpochMillis,
                readingId = reading?.id,
                sourceId = reading?.sourceId,
                finalizedAtEpochMillis = System.currentTimeMillis(),
            )
        ) {
            syncScheduler.enqueueImmediate(refreshProviders = false)
        }
    }

    companion object {
        const val SESSION_ID_KEY = "session_id"
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
