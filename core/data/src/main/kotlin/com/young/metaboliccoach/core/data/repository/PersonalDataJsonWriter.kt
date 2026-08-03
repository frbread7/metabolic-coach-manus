package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings

internal sealed interface ExportValue {
    data object Null : ExportValue
    data class Integer(val value: Long) : ExportValue
    data class Decimal(val value: Double) : ExportValue
    data class Text(val value: String) : ExportValue
    data class Binary(val base64Value: String) : ExportValue
}

/**
 * Small streaming JSON writer for the user-owned export format.
 *
 * It deliberately avoids reflection and a serialization dependency. Property order is stable so
 * exports are easy to diff, while database rows are written one at a time to keep memory bounded.
 */
internal class PersonalDataJsonWriter(
    private val destination: Appendable,
) {
    private var tableCount = 0
    private var rowCount = 0
    private var tableOpen = false
    private var documentOpen = false

    fun beginDocument(
        exportedAtEpochMillis: Long,
        databaseSchemaVersion: Int,
        settings: CoachSettings,
        glycemicPlannerSettings: GlycemicPlannerSettings = GlycemicPlannerSettings(),
    ) {
        check(!documentOpen) { "An export document is already open." }
        tableCount = 0
        rowCount = 0
        tableOpen = false
        destination.append('{')
        writeNamedValue("format", "metabolic-coach-personal-data", first = true)
        writeNamedValue("schemaVersion", EXPORT_SCHEMA_VERSION)
        writeNamedValue("databaseSchemaVersion", databaseSchemaVersion)
        writeNamedValue("exportedAtEpochMillis", exportedAtEpochMillis)
        destination.append(",\"settings\":")
        writeSettings(settings)
        destination.append(",\"glycemicPlanner\":")
        writeGlycemicPlannerSettings(glycemicPlannerSettings)
        destination.append(",\"data\":{")
        documentOpen = true
    }

    fun beginTable(name: String) {
        check(documentOpen && !tableOpen) { "A table cannot be started in the current state." }
        if (tableCount > 0) destination.append(',')
        appendQuoted(name)
        destination.append(":[")
        tableOpen = true
        rowCount = 0
    }

    fun writeRow(
        columnNames: List<String>,
        values: List<ExportValue>,
    ) {
        check(tableOpen) { "A row requires an open table." }
        require(columnNames.size == values.size) {
            "Every exported column must have exactly one value."
        }
        if (rowCount > 0) destination.append(',')
        destination.append('{')
        columnNames.indices.forEach { index ->
            if (index > 0) destination.append(',')
            appendQuoted(columnNames[index])
            destination.append(':')
            writeExportValue(values[index])
        }
        destination.append('}')
        rowCount += 1
    }

    fun endTable() {
        check(tableOpen) { "No export table is open." }
        destination.append(']')
        tableOpen = false
        tableCount += 1
    }

    fun endDocument() {
        check(documentOpen && !tableOpen) {
            "The export document cannot close while a table is open."
        }
        destination.append("}}")
        documentOpen = false
    }

    private fun writeSettings(settings: CoachSettings) {
        val values = listOf(
            "glucoseProviderMode" to settings.glucoseProviderMode.name,
            "healthConnectGlucoseOriginPackage" to
                settings.healthConnectGlucoseOriginPackage,
            "glucoseUnit" to settings.glucoseUnit.name,
            "lowGlucoseThresholdMgDl" to settings.lowGlucoseThresholdMgDl,
            "targetLowerMgDl" to settings.targetLowerMgDl,
            "targetUpperMgDl" to settings.targetUpperMgDl,
            "rapidRiseThresholdMgDlPerMinute" to
                settings.rapidRiseThresholdMgDlPerMinute,
            "exercisePauseFallRateMgDlPerMinute" to
                settings.exercisePauseFallRateMgDlPerMinute,
            "staleReadingMinutes" to settings.staleReadingMinutes,
            "walkingDurationMinutes" to settings.walkingDurationMinutes,
            "stairTargetFloors" to settings.stairTargetFloors,
            "dailyStepGoal" to settings.dailyStepGoal,
            "dailyFloorGoal" to settings.dailyFloorGoal,
            "prolongedInactivityMinutes" to settings.prolongedInactivityMinutes,
            "postMealDelayMinutes" to settings.postMealDelayMinutes,
            "postMealWindowMinutes" to settings.postMealWindowMinutes,
            "reminderCooldownMinutes" to settings.reminderCooldownMinutes,
            "snoozeMinutes" to settings.snoozeMinutes,
            "maximumNotificationsPerDay" to settings.maximumNotificationsPerDay,
            "quietHoursStartMinuteOfDay" to settings.quietHoursStartMinuteOfDay,
            "quietHoursEndMinuteOfDay" to settings.quietHoursEndMinuteOfDay,
            "workingHoursStartMinuteOfDay" to settings.workingHoursStartMinuteOfDay,
            "workingHoursEndMinuteOfDay" to settings.workingHoursEndMinuteOfDay,
            "minimumObservationSamples" to settings.minimumObservationSamples,
            "minimumTimingBucketSamples" to settings.minimumTimingBucketSamples,
            "minimumComparableTimingBuckets" to
                settings.minimumComparableTimingBuckets,
            "interventionTimingBucketMinutes" to
                settings.interventionTimingBucketMinutes,
            "postMealTimingBucketMinutes" to settings.postMealTimingBucketMinutes,
            "followUpDelayBucketMinutes" to settings.followUpDelayBucketMinutes,
            "baselineGlucoseBandMgDl" to settings.baselineGlucoseBandMgDl,
            "interventionFollowUpMinutes" to settings.interventionFollowUpMinutes,
            "quickActionExpiryMinutes" to settings.quickActionExpiryMinutes,
            "walkingRemindersEnabled" to settings.walkingRemindersEnabled,
            "stairRemindersEnabled" to settings.stairRemindersEnabled,
            "postMealRemindersEnabled" to settings.postMealRemindersEnabled,
            "notificationsEnabled" to settings.notificationsEnabled,
            "theme" to settings.theme.name,
            "fontScale" to settings.fontScale,
        )
        destination.append('{')
        values.forEachIndexed { index, (name, value) ->
            writeNamedValue(name, value, first = index == 0)
        }
        destination.append('}')
    }

    private fun writeGlycemicPlannerSettings(settings: GlycemicPlannerSettings) {
        val values = listOf(
            "targetGmiPercent" to settings.targetGmiPercent,
            "targetProvenance" to settings.targetProvenance?.name,
            "horizonDays" to settings.horizon.days,
            "lowGlucoseThresholdMgDl" to settings.lowGlucoseThresholdMgDl,
            "veryLowGlucoseThresholdMgDl" to settings.veryLowGlucoseThresholdMgDl,
            "maximumLowGlucosePercent" to settings.maximumLowGlucosePercent,
            "maximumVeryLowGlucosePercent" to settings.maximumVeryLowGlucosePercent,
        )
        destination.append('{')
        values.forEachIndexed { index, (name, value) ->
            writeNamedValue(name, value, first = index == 0)
        }
        destination.append('}')
    }

    private fun writeNamedValue(
        name: String,
        value: Any?,
        first: Boolean = false,
    ) {
        if (!first) destination.append(',')
        appendQuoted(name)
        destination.append(':')
        writeValue(value)
    }

    private fun writeExportValue(value: ExportValue) {
        when (value) {
            ExportValue.Null -> destination.append("null")
            is ExportValue.Integer -> destination.append(value.value.toString())
            is ExportValue.Decimal -> writeValue(value.value)
            is ExportValue.Text -> appendQuoted(value.value)
            is ExportValue.Binary -> {
                destination.append("{\"encoding\":\"base64\",\"value\":")
                appendQuoted(value.base64Value)
                destination.append('}')
            }
        }
    }

    private fun writeValue(value: Any?) {
        when (value) {
            null -> destination.append("null")
            is String -> appendQuoted(value)
            is Boolean -> destination.append(value.toString())
            is Byte,
            is Short,
            is Int,
            is Long,
            -> destination.append(value.toString())
            is Float -> {
                if (value.isFinite()) destination.append(value.toString())
                else destination.append("null")
            }
            is Double -> {
                if (value.isFinite()) destination.append(value.toString())
                else destination.append("null")
            }
            else -> error("Unsupported JSON export value: ${value::class.java.name}")
        }
    }

    private fun appendQuoted(value: String) {
        destination.append('"')
        value.forEach { character ->
            when (character) {
                '"' -> destination.append("\\\"")
                '\\' -> destination.append("\\\\")
                '\b' -> destination.append("\\b")
                '\u000C' -> destination.append("\\f")
                '\n' -> destination.append("\\n")
                '\r' -> destination.append("\\r")
                '\t' -> destination.append("\\t")
                in '\u0000'..'\u001F',
                in '\uD800'..'\uDFFF',
                '\u2028',
                '\u2029',
                -> appendUnicodeEscape(character)
                else -> destination.append(character)
            }
        }
        destination.append('"')
    }

    private fun appendUnicodeEscape(character: Char) {
        destination.append("\\u")
        repeat(4) { shiftIndex ->
            val shift = (3 - shiftIndex) * 4
            destination.append(HEX_DIGITS[(character.code shr shift) and 0xF])
        }
    }

    private companion object {
        const val EXPORT_SCHEMA_VERSION = 2
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
