package com.young.metaboliccoach.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.young.metaboliccoach.R
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                COACH_CHANNEL,
                "Metabolic coaching",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Configurable walking and stair prompts"
                enableVibration(true)
            },
        )
    }

    fun showCoachPrompt(recommendation: CoachRecommendation.Action) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val startAction = when (recommendation.interventionType) {
            InterventionType.WALK -> QuickActionType.START_WALK
            InterventionType.STAIRS -> QuickActionType.START_STAIRS
        }
        val notification = NotificationCompat.Builder(context, COACH_CHANNEL)
            .setSmallIcon(R.drawable.ic_metabolic_coach)
            .setContentTitle(recommendation.title)
            .setContentText("Personal coaching based on your configured thresholds.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(
                (recommendation.validUntilEpochMillis - System.currentTimeMillis())
                    .coerceAtLeast(1L),
            )
            .addAction(
                0,
                recommendation.actionLabel,
                actionPendingIntent(startAction, REQUEST_START, recommendation),
            )
            .addAction(
                0,
                "Snooze",
                actionPendingIntent(QuickActionType.SNOOZE, REQUEST_SNOOZE, recommendation),
            )
            .build()
        NotificationManagerCompat.from(context).notify(COACH_NOTIFICATION_ID, notification)
    }

    fun clearCoachPrompt() {
        NotificationManagerCompat.from(context).cancel(COACH_NOTIFICATION_ID)
    }

    private fun actionPendingIntent(
        type: QuickActionType,
        requestCode: Int,
        recommendation: CoachRecommendation.Action? = null,
    ): PendingIntent {
        val intent = Intent(context, CoachActionReceiver::class.java)
            .setAction(ACTION_COACH_NOTIFICATION)
            .putExtra(EXTRA_QUICK_ACTION, type.name)
            .apply {
                recommendation?.let {
                    putExtra(EXTRA_RECOMMENDATION_ID, it.id)
                    putExtra(EXTRA_RECOMMENDATION_VALID_UNTIL, it.validUntilEpochMillis)
                    putExtra(EXTRA_RECOMMENDATION_REASON, it.reason.name)
                    putExtra(EXTRA_RECOMMENDATION_ALGORITHM_VERSION, it.algorithmVersion)
                    putExtra(EXTRA_RECOMMENDATION_CREATED_AT, it.createdAtEpochMillis)
                    putExtra(EXTRA_TRIGGER_CONTEXT_ID, it.triggerContextId)
                    putExtra(EXTRA_GLUCOSE_SOURCE_ID, it.glucoseSourceId)
                    putExtra(EXTRA_SAFETY_READING_ID, it.safetyReadingId)
                    it.safetyReadingAtEpochMillis?.let { readingAt ->
                        putExtra(EXTRA_SAFETY_READING_AT, readingAt)
                    }
                    it.triggerAtEpochMillis?.let { triggerAt ->
                        putExtra(EXTRA_TRIGGER_AT, triggerAt)
                    }
                }
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_COACH_NOTIFICATION =
            "com.young.metaboliccoach.action.COACH_NOTIFICATION"
        const val EXTRA_QUICK_ACTION = "quick_action"
        const val EXTRA_RECOMMENDATION_ID = "recommendation_id"
        const val EXTRA_RECOMMENDATION_VALID_UNTIL = "recommendation_valid_until"
        const val EXTRA_RECOMMENDATION_REASON = "recommendation_reason"
        const val EXTRA_RECOMMENDATION_ALGORITHM_VERSION =
            "recommendation_algorithm_version"
        const val EXTRA_RECOMMENDATION_CREATED_AT = "recommendation_created_at"
        const val EXTRA_TRIGGER_CONTEXT_ID = "trigger_context_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_GLUCOSE_SOURCE_ID = "glucose_source_id"
        const val EXTRA_SAFETY_READING_ID = "safety_reading_id"
        const val EXTRA_SAFETY_READING_AT = "safety_reading_at"
        private const val COACH_CHANNEL = "metabolic_coaching"
        private const val COACH_NOTIFICATION_ID = 138
        private const val REQUEST_START = 1
        private const val REQUEST_SNOOZE = 2
    }
}
