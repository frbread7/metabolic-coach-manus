package com.young.metaboliccoach.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.young.metaboliccoach.wear.MainActivity
import com.young.metaboliccoach.wear.data.WearStateStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class ActivityComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var store: WearStateStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val activity = store.state.first()?.activity ?: return NoDataComplicationData()
        val stepText = if (activity.stepsToday >= 1_000) {
            "${"%.1f".format(activity.stepsToday / 1_000.0)}k"
        } else {
            activity.stepsToday.toString()
        }
        val tapAction = PendingIntent.getActivity(
            this,
            11,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return textComplication(
            type = request.complicationType,
            shortText = "$stepText/${activity.floorsToday.toInt()}F",
            longText = "${activity.stepsToday} steps • ${"%.1f".format(activity.floorsToday)} floors",
            tapAction = tapAction,
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        textComplication(type, "4.2k/6F", "4,200 steps • 6 floors")
}
