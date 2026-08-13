package com.young.metaboliccoach.wear.sync

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.young.metaboliccoach.core.domain.ActionDisplayDeadlinePolicy
import com.young.metaboliccoach.core.model.WatchState
import com.young.metaboliccoach.core.domain.effectiveRecommendation
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.wear.complication.ActivityComplicationService
import com.young.metaboliccoach.wear.complication.CoachComplicationService
import com.young.metaboliccoach.wear.complication.GlucoseComplicationService
import com.young.metaboliccoach.wear.data.SessionStore
import com.young.metaboliccoach.wear.data.WearCommandOutbox
import com.young.metaboliccoach.wear.data.WearStateStore
import com.young.metaboliccoach.wear.notification.WearCoachNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

/**
 * Idempotent entry point shared by the process listener and WearableListenerService.
 */
@Singleton
class WatchStateConsumer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: WearStateStore,
    private val sessionStore: SessionStore,
    private val commandOutbox: WearCommandOutbox,
    private val notifications: WearCoachNotificationManager,
) {
    private val mutex = Mutex()
    suspend fun handle(state: WatchState) = mutex.withLock {
        val current = store.state.first()
        if (
            !RemoteStateOrderPolicy.shouldAccept(
                current,
                state,
                sessionStore.hasPendingMutation(),
            )
        ) {
            return@withLock
        }
        if (RemoteDataResetPolicy.shouldReset(current, state)) {
            sessionStore.clearForDataReset()
            commandOutbox.clearForDataReset()
        } else {
            sessionStore.reconcile(state.activeSession, state.lastSessionCommandAck)
        }
        val replica = sessionStore.replica.first()
        store.save(state)
        val now = System.currentTimeMillis()
        val effectiveRecommendation = if (replica.blocksNewSession) {
            null
        } else {
            state.effectiveRecommendation(now)
        }
        notifications.showRecommendation(
            recommendation = effectiveRecommendation,
            nowEpochMillis = now,
            displayUntilEpochMillis =
                (effectiveRecommendation as? CoachRecommendation.Action)?.let { action ->
                    ActionDisplayDeadlinePolicy.displayUntilEpochMillis(
                        recommendation = action,
                        settings = state.settings,
                        nowEpochMillis = now,
                    )
                },
        )
        requestComplicationUpdates()
    }

    private fun requestComplicationUpdates() {
        listOf(
            GlucoseComplicationService::class.java,
            CoachComplicationService::class.java,
            ActivityComplicationService::class.java,
        ).forEach { service ->
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, service),
            ).requestUpdateAll()
        }
    }
}

internal object RemoteDataResetPolicy {
    fun shouldReset(
        current: WatchState?,
        incoming: WatchState,
    ): Boolean =
        incoming.dataResetId != null &&
            incoming.dataResetId != current?.dataResetId
}
