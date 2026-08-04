package com.young.metaboliccoach.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CoachActionReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: QuickActionHandler
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var mutationGate: PhoneDataMutationGate

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CoachNotificationManager.ACTION_COACH_NOTIFICATION) return
        val type = intent.getStringExtra(CoachNotificationManager.EXTRA_QUICK_ACTION)
            ?.let { runCatching { QuickActionType.valueOf(it) }.getOrNull() }
            ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = QuickActionCommand(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    recommendationId =
                        intent.getStringExtra(CoachNotificationManager.EXTRA_RECOMMENDATION_ID),
                    recommendationValidUntilEpochMillis = intent.getLongExtra(
                        CoachNotificationManager.EXTRA_RECOMMENDATION_VALID_UNTIL,
                        Long.MIN_VALUE,
                    ).takeIf {
                        intent.hasExtra(
                            CoachNotificationManager.EXTRA_RECOMMENDATION_VALID_UNTIL,
                        )
                    },
                    recommendationReason = intent.getStringExtra(
                        CoachNotificationManager.EXTRA_RECOMMENDATION_REASON,
                    )?.let {
                        runCatching { CoachReason.valueOf(it) }.getOrNull()
                    },
                    recommendationAlgorithmVersion = intent.getIntExtra(
                        CoachNotificationManager.EXTRA_RECOMMENDATION_ALGORITHM_VERSION,
                        Int.MIN_VALUE,
                    ).takeIf {
                        intent.hasExtra(
                            CoachNotificationManager.EXTRA_RECOMMENDATION_ALGORITHM_VERSION,
                        )
                    },
                    recommendationCreatedAtEpochMillis = intent.getLongExtra(
                        CoachNotificationManager.EXTRA_RECOMMENDATION_CREATED_AT,
                        Long.MIN_VALUE,
                    ).takeIf {
                        intent.hasExtra(
                            CoachNotificationManager.EXTRA_RECOMMENDATION_CREATED_AT,
                        )
                    },
                    triggerContextId = intent.getStringExtra(
                        CoachNotificationManager.EXTRA_TRIGGER_CONTEXT_ID,
                    ),
                    triggerAtEpochMillis = intent.getLongExtra(
                        CoachNotificationManager.EXTRA_TRIGGER_AT,
                        Long.MIN_VALUE,
                    ).takeIf {
                        intent.hasExtra(CoachNotificationManager.EXTRA_TRIGGER_AT)
                    },
                    glucoseSourceId = intent.getStringExtra(
                        CoachNotificationManager.EXTRA_GLUCOSE_SOURCE_ID,
                    ),
                    safetyReadingId = intent.getStringExtra(
                        CoachNotificationManager.EXTRA_SAFETY_READING_ID,
                    ),
                    safetyReadingAtEpochMillis = intent.getLongExtra(
                        CoachNotificationManager.EXTRA_SAFETY_READING_AT,
                        Long.MIN_VALUE,
                    ).takeIf {
                        intent.hasExtra(CoachNotificationManager.EXTRA_SAFETY_READING_AT)
                    },
                )
                val result = mutationGate.withLock {
                    handler.handle(command)
                }
                if (result != CommandHandlingResult.Deferred) {
                    syncScheduler.enqueueImmediate(refreshProviders = false)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
