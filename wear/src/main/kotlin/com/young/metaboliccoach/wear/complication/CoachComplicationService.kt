package com.young.metaboliccoach.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.young.metaboliccoach.core.domain.ActionDisplayDeadlinePolicy
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.domain.effectiveRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.wear.MainActivity
import com.young.metaboliccoach.wear.QuickActionActivity
import com.young.metaboliccoach.wear.data.WearStateStore
import com.young.metaboliccoach.wear.data.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class CoachComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var store: WearStateStore
    @Inject lateinit var sessionStore: SessionStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = store.state.first() ?: return NoDataComplicationData()
        val replica = sessionStore.replica.first()
        if (replica.activeSession != null) {
            return textComplication(
                type = request.complicationType,
                shortText = "DONE?",
                longText = "Mark activity completed",
                tapAction = PendingIntent.getActivity(
                    this,
                    22,
                    QuickActionActivity.intent(this, QuickActionType.MARK_COMPLETED),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        if (replica.pendingCommand != null) {
            return textComplication(
                type = request.complicationType,
                shortText = "SYNC",
                longText = replica.syncMessage ?: "Syncing activity with phone",
                tapAction = PendingIntent.getActivity(
                    this,
                    21,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        val now = System.currentTimeMillis()
        val recommendation =
            state.effectiveRecommendation(now)
                ?: return NoDataComplicationData()
        val action = recommendation as? CoachRecommendation.Action
        val pendingIntent = if (action != null) {
            val type = if (action.interventionType == InterventionType.WALK) {
                QuickActionType.START_WALK
            } else {
                QuickActionType.START_STAIRS
            }
            PendingIntent.getActivity(
                this,
                20,
                QuickActionActivity.intent(
                    this,
                    type,
                    recommendationId = action.id,
                    recommendationValidUntilEpochMillis = action.validUntilEpochMillis,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                this,
                21,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return textComplication(
            type = request.complicationType,
            shortText = when (action?.interventionType) {
                InterventionType.WALK -> "WALK?"
                InterventionType.STAIRS -> "STAIRS?"
                null -> "CHECK"
            },
            longText = when (recommendation) {
                is CoachRecommendation.Action -> recommendation.title
                is CoachRecommendation.Information -> recommendation.title
            },
            tapAction = pendingIntent,
            validUntilEpochMillis = action?.let {
                ActionDisplayDeadlinePolicy.displayUntilEpochMillis(
                    recommendation = it,
                    settings = state.settings,
                    nowEpochMillis = now,
                )
            },
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        textComplication(type, "WALK?", "Walk now?")
}
