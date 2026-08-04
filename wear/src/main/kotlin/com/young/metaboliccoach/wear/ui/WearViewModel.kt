package com.young.metaboliccoach.wear.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.young.metaboliccoach.core.domain.effectiveRecommendation
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.WatchState
import com.young.metaboliccoach.wear.data.ActiveWearSession
import com.young.metaboliccoach.wear.data.SessionStore
import com.young.metaboliccoach.wear.data.WearCommandOutbox
import com.young.metaboliccoach.wear.data.WearStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WearUiState(
    val watchState: WatchState = WatchState(
        glucose = null,
        activity = null,
        recommendation = null,
        settings = DefaultCoachSettings.create(),
        phoneBatteryPercent = null,
        generatedAtEpochMillis = 0,
    ),
    val activeSession: ActiveWearSession? = null,
    val watchBatteryPercent: Int? = null,
    val sessionSyncPending: Boolean = false,
    val syncMessage: String? = null,
)

internal enum class WearActionResultStatus {
    QUEUED,
    REJECTED,
}

internal data class WearActionResult(
    val type: QuickActionType,
    val status: WearActionResultStatus,
    val message: String,
)

@HiltViewModel
class WearViewModel @Inject constructor(
    application: Application,
    private val sessionStore: SessionStore,
    private val wearStateStore: WearStateStore,
    private val commandOutbox: WearCommandOutbox,
) : AndroidViewModel(application) {
    private val batteryPercent = batteryPercentFlow(application)
    private val minuteTicks = minuteTicks()
    private val actionMutex = Mutex()
    private val _actionResult = MutableStateFlow<WearActionResult?>(null)
    private var lastExternalRequestKey: String? = null

    internal val actionResult = _actionResult.asStateFlow()

    val uiState = combine(
        wearStateStore.state,
        sessionStore.replica,
        batteryPercent,
        minuteTicks,
    ) { state, replica, battery, now ->
        val rawState = state ?: WearUiState().watchState
        WearUiState(
            watchState = rawState.copy(
                recommendation = if (replica.blocksNewSession) {
                    null
                } else {
                    rawState.effectiveRecommendation(now)
                },
            ),
            activeSession = replica.activeSession,
            watchBatteryPercent = battery,
            sessionSyncPending = replica.pendingCommand != null,
            syncMessage = replica.syncMessage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WearUiState(),
    )

    fun perform(
        type: QuickActionType,
        recommendationId: String? = null,
        recommendationValidUntilEpochMillis: Long? = null,
    ) {
        schedulePerform(
            type = type,
            recommendationId = recommendationId,
            recommendationValidUntilEpochMillis = recommendationValidUntilEpochMillis,
            externalRequestKey = null,
        )
    }

    internal fun performExternal(
        requestKey: String,
        type: QuickActionType,
        recommendationId: String? = null,
        recommendationValidUntilEpochMillis: Long? = null,
    ) {
        schedulePerform(
            type = type,
            recommendationId = recommendationId,
            recommendationValidUntilEpochMillis = recommendationValidUntilEpochMillis,
            externalRequestKey = requestKey,
        )
    }

    private fun schedulePerform(
        type: QuickActionType,
        recommendationId: String?,
        recommendationValidUntilEpochMillis: Long?,
        externalRequestKey: String?,
    ) {
        viewModelScope.launch {
            actionMutex.withLock {
                if (
                    externalRequestKey != null &&
                    externalRequestKey == lastExternalRequestKey
                ) {
                    return@withLock
                }
                externalRequestKey?.let { lastExternalRequestKey = it }
                _actionResult.value = null
                try {
                    performLocked(
                        type = type,
                        recommendationId = recommendationId,
                        recommendationValidUntilEpochMillis =
                            recommendationValidUntilEpochMillis,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    reject(type, WearActionRejection.PERSISTENCE_FAILED)
                }
            }
        }
    }

    private suspend fun performLocked(
        type: QuickActionType,
        recommendationId: String?,
        recommendationValidUntilEpochMillis: Long?,
    ) {
        val now = System.currentTimeMillis()
        val commandId = UUID.randomUUID().toString()
        val replica = sessionStore.replica.first()
        val activeSession = replica.activeSession
        val persistedWatchState =
            wearStateStore.state.first() ?: uiState.value.watchState
        val effectiveRecommendation =
            persistedWatchState.effectiveRecommendation(now) as? CoachRecommendation.Action
        when (type) {
            QuickActionType.START_WALK,
            QuickActionType.START_STAIRS,
            -> {
                val rejection = WearActionPolicy.startRejection(
                    blocksNewSession = replica.blocksNewSession,
                    recommendationId = recommendationId,
                    recommendationValidUntilEpochMillis =
                        recommendationValidUntilEpochMillis,
                    effectiveRecommendationId = effectiveRecommendation?.id,
                    nowEpochMillis = now,
                )
                if (rejection != null) {
                    reject(type, rejection)
                    return
                }
                val isWalk = type == QuickActionType.START_WALK
                val session = ActiveWearSession(
                    id = commandId,
                    type = if (isWalk) InterventionType.WALK else InterventionType.STAIRS,
                    startedAtEpochMillis = now,
                    durationMinutes = persistedWatchState.settings.walkingDurationMinutes
                        .takeIf { isWalk },
                    targetFloors = persistedWatchState.settings.stairTargetFloors
                        .takeUnless { isWalk },
                )
                val queued = sessionStore.queueStart(
                    session,
                    QuickActionCommand(
                        id = commandId,
                        type = type,
                        createdAtEpochMillis = now,
                        sessionId = session.id,
                        recommendationId = recommendationId,
                        recommendationValidUntilEpochMillis =
                            recommendationValidUntilEpochMillis,
                        recommendationReason = effectiveRecommendation?.reason,
                        recommendationAlgorithmVersion =
                            effectiveRecommendation?.algorithmVersion,
                        recommendationCreatedAtEpochMillis =
                            effectiveRecommendation?.createdAtEpochMillis,
                        triggerContextId = effectiveRecommendation?.triggerContextId,
                        triggerAtEpochMillis =
                            effectiveRecommendation?.triggerAtEpochMillis,
                        glucoseSourceId = effectiveRecommendation?.glucoseSourceId,
                        safetyReadingId = effectiveRecommendation?.safetyReadingId,
                        safetyReadingAtEpochMillis =
                            effectiveRecommendation?.safetyReadingAtEpochMillis,
                        dataResetId = persistedWatchState.dataResetId,
                    ),
                )
                if (queued) {
                    accept(type, if (isWalk) "Walk started" else "Stair activity started")
                } else {
                    reject(type, WearActionRejection.SESSION_BUSY)
                }
            }
            QuickActionType.MARK_COMPLETED -> {
                val session = activeSession
                if (session == null) {
                    reject(type, WearActionRejection.NO_ACTIVE_SESSION)
                    return
                }
                val queued = sessionStore.queueCompletion(
                    QuickActionCommand(
                        id = commandId,
                        type = type,
                        createdAtEpochMillis = now,
                        sessionId = session.id,
                        dataResetId = persistedWatchState.dataResetId,
                    ),
                )
                if (queued) {
                    accept(type, "Activity completion saved")
                } else {
                    reject(type, WearActionRejection.START_NOT_TRANSPORTED)
                }
            }
            QuickActionType.SNOOZE -> {
                val recommendation = persistedWatchState.recommendation
                    as? CoachRecommendation.Action
                commandOutbox.enqueue(
                    QuickActionCommand(
                        id = commandId,
                        type = type,
                        createdAtEpochMillis = now,
                        recommendationId = recommendation?.id,
                        recommendationValidUntilEpochMillis =
                            recommendation?.validUntilEpochMillis,
                        recommendationReason = recommendation?.reason,
                        recommendationAlgorithmVersion = recommendation?.algorithmVersion,
                        recommendationCreatedAtEpochMillis =
                            recommendation?.createdAtEpochMillis,
                        triggerContextId = recommendation?.triggerContextId,
                        triggerAtEpochMillis = recommendation?.triggerAtEpochMillis,
                        glucoseSourceId = recommendation?.glucoseSourceId,
                        safetyReadingId = recommendation?.safetyReadingId,
                        safetyReadingAtEpochMillis =
                            recommendation?.safetyReadingAtEpochMillis,
                        dataResetId = persistedWatchState.dataResetId,
                    ),
                )
                wearStateStore.suppressRecommendation()
                accept(type, "Reminder snoozed")
            }
        }
    }

    private fun accept(type: QuickActionType, message: String) {
        _actionResult.value = WearActionResult(
            type = type,
            status = WearActionResultStatus.QUEUED,
            message = message,
        )
    }

    private fun reject(type: QuickActionType, rejection: WearActionRejection) {
        _actionResult.value = WearActionResult(
            type = type,
            status = WearActionResultStatus.REJECTED,
            message = rejection.message,
        )
    }

    private fun batteryPercentFlow(context: Context): Flow<Int?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(intent.batteryPercent())
            }
        }
        val initial = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(initial.batteryPercent())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun minuteTicks(): Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay((60_000L - now.mod(60_000L)).coerceAtLeast(1_000L))
        }
    }

    private fun Intent?.batteryPercent(): Int? {
        val level = this?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return null
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }
}
