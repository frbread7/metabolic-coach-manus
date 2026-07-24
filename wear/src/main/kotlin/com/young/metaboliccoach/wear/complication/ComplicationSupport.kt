package com.young.metaboliccoach.wear.complication

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeRange
import java.time.Instant

fun textComplication(
    type: ComplicationType,
    shortText: String,
    longText: String,
    title: androidx.wear.watchface.complications.data.ComplicationText? = null,
    tapAction: PendingIntent? = null,
    validUntilEpochMillis: Long? = null,
): ComplicationData {
    val description = PlainComplicationText.Builder(longText).build()
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(shortText).build(),
            contentDescription = description,
        ).apply {
            title?.let(::setTitle)
            tapAction?.let(::setTapAction)
            validUntilEpochMillis?.let {
                setValidTimeRange(TimeRange.before(Instant.ofEpochMilli(it)))
            }
        }.build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(longText).build(),
            contentDescription = description,
        ).apply {
            title?.let(::setTitle)
            tapAction?.let(::setTapAction)
            validUntilEpochMillis?.let {
                setValidTimeRange(TimeRange.before(Instant.ofEpochMilli(it)))
            }
        }.build()
        else -> NoDataComplicationData()
    }
}
