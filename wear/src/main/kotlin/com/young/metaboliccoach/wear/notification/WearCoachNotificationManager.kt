package com.young.metaboliccoach.wear.notification

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
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.wear.QuickActionActivity
import com.young.metaboliccoach.wear.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearCoachNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Metabolic coaching",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun showRecommendation(recommendation: CoachRecommendation?) {
        val action = recommendation as? CoachRecommendation.Action
        if (action == null) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val type = when (action.interventionType) {
            InterventionType.WALK -> QuickActionType.START_WALK
            InterventionType.STAIRS -> QuickActionType.START_STAIRS
        }
        val startIntent = PendingIntent.getActivity(
            context,
            1,
            QuickActionActivity.intent(
                context,
                type,
                recommendationId = action.id,
                recommendationValidUntilEpochMillis = action.validUntilEpochMillis,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getActivity(
            context,
            2,
            QuickActionActivity.intent(context, QuickActionType.SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_metabolic_coach)
            .setContentTitle(action.title)
            .setContentText("Based on your configured thresholds")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter(
                (action.validUntilEpochMillis - System.currentTimeMillis()).coerceAtLeast(1L),
            )
            .addAction(0, action.actionLabel, startIntent)
            .addAction(0, "Snooze", snoozeIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL = "wear_metabolic_coaching"
        private const val NOTIFICATION_ID = 138
    }
}
