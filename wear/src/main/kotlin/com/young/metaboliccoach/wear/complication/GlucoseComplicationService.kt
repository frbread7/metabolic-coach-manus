package com.young.metaboliccoach.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.young.metaboliccoach.wear.MainActivity
import com.young.metaboliccoach.wear.data.WearStateStore
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class GlucoseComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var store: WearStateStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = store.state.first() ?: return NoDataComplicationData()
        val reading = state.glucose ?: return NoDataComplicationData()
        val age = TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            CountUpTimeReference(Instant.ofEpochMilli(reading.measuredAtEpochMillis)),
        ).build()
        val tapAction = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val displayValue = reading.displayValue(state.settings.glucoseUnit)
        val displayDelta = reading.displayDelta(state.settings.glucoseUnit)
        return textComplication(
            type = request.complicationType,
            shortText = buildString {
                append(displayValue)
                append(reading.trend.symbol)
                displayDelta?.let {
                    append(' ')
                    append(it)
                }
            },
            longText = buildString {
                append(displayValue)
                append(' ')
                append(reading.trend.symbol)
                reading.displayDelta(state.settings.glucoseUnit)?.let {
                    append(" • ")
                    append(it)
                }
            },
            title = age,
            tapAction = tapAction,
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        textComplication(type, "138↗ +8", "138 ↗ • +8")
}
