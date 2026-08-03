package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDataJsonWriterTest {
    @Test
    fun `writer produces deterministic schema versioned JSON with escaped values`() {
        val first = writeSample()
        val second = writeSample()

        assertEquals(first, second)
        assertTrue(
            first.startsWith(
                "{\"format\":\"metabolic-coach-personal-data\"," +
                    "\"schemaVersion\":2,\"databaseSchemaVersion\":7," +
                    "\"exportedAtEpochMillis\":1234,\"settings\":{",
            ),
        )
        assertTrue(
            first.contains(
                "\"healthConnectGlucoseOriginPackage\":\"pkg\\\"\\\\\\n\\u2028\"",
            ),
        )
        assertTrue(
            first.endsWith(
                "\"data\":{\"glucose_readings\":[" +
                    "{\"id\":\"reading\\\"\\\\\\n\\u0001\"," +
                    "\"measuredAtEpochMillis\":42,\"rate\":null," +
                    "\"payload\":{\"encoding\":\"base64\",\"value\":\"AAE=\"}}]}}",
            ),
        )
    }

    @Test
    fun `writer supports empty tables without leaving invalid separators`() {
        val output = StringBuilder()
        PersonalDataJsonWriter(output).apply {
            beginDocument(
                exportedAtEpochMillis = 10,
                databaseSchemaVersion = 7,
                settings = DefaultCoachSettings.create(),
                glycemicPlannerSettings = GlycemicPlannerSettings(
                    targetGmiPercent = 7.0,
                    targetProvenance = GlycemicTargetProvenance.CLINICIAN_AGREED,
                    horizon = GlycemicWindow.DAYS_60,
                ),
            )
            beginTable("first")
            endTable()
            beginTable("second")
            endTable()
            endDocument()
        }

        assertTrue(output.endsWith("\"data\":{\"first\":[],\"second\":[]}}"))
        assertTrue(output.contains("\"glycemicPlanner\":{\"targetGmiPercent\":7.0"))
        assertTrue(output.contains("\"targetProvenance\":\"CLINICIAN_AGREED\""))
    }

    private fun writeSample(): String {
        val output = StringBuilder()
        PersonalDataJsonWriter(output).apply {
            beginDocument(
                exportedAtEpochMillis = 1_234,
                databaseSchemaVersion = 7,
                settings = DefaultCoachSettings.create().copy(
                    healthConnectGlucoseOriginPackage = "pkg\"\\\n\u2028",
                ),
            )
            beginTable("glucose_readings")
            writeRow(
                columnNames = listOf(
                    "id",
                    "measuredAtEpochMillis",
                    "rate",
                    "payload",
                ),
                values = listOf(
                    ExportValue.Text("reading\"\\\n\u0001"),
                    ExportValue.Integer(42),
                    ExportValue.Decimal(Double.NaN),
                    ExportValue.Binary(
                        Base64.getEncoder().encodeToString(byteArrayOf(0, 1)),
                    ),
                ),
            )
            endTable()
            endDocument()
        }
        return output.toString()
    }
}
