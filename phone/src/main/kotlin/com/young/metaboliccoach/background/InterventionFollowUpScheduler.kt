package com.young.metaboliccoach.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterventionFollowUpScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(context)
    }

    fun schedule(session: InterventionSession) {
        val dueAt = session.followUpDueAtEpochMillis ?: return
        if (
            session.status != InterventionStatus.COMPLETED ||
            session.followUpFinalizedAtEpochMillis != null
        ) {
            return
        }
        val delayMillis = (dueAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<InterventionFollowUpWorker>()
            .setInputData(
                workDataOf(InterventionFollowUpWorker.SESSION_ID_KEY to session.id),
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(FOLLOW_UP_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(session.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun scheduleAll(sessions: List<InterventionSession>) {
        sessions.forEach(::schedule)
    }

    suspend fun cancelAll(sessions: List<InterventionSession>) {
        sessions
            .map(InterventionSession::id)
            .distinct()
            .forEach { sessionId ->
                workManager.cancelUniqueWork(uniqueWorkName(sessionId)).await()
            }
        workManager.cancelAllWorkByTag(FOLLOW_UP_WORK_TAG).await()
    }

    private fun uniqueWorkName(sessionId: String) = "intervention-follow-up-$sessionId"

    private companion object {
        const val RETRY_DELAY_MINUTES = 5L
        const val FOLLOW_UP_WORK_TAG = "intervention-follow-up"
    }
}
