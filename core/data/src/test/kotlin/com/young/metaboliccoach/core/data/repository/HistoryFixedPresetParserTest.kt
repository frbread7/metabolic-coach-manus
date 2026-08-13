package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFixedPresetParserTest {
    @Test
    fun `six and twelve hour presets restore by their stable enum names`() {
        assertEquals(
            HistoryPeriodPreset.HOURS_6,
            parseFixedHistoryPreset(HistoryPeriodPreset.HOURS_6.name),
        )
        assertEquals(
            HistoryPeriodPreset.HOURS_12,
            parseFixedHistoryPreset(HistoryPeriodPreset.HOURS_12.name),
        )
    }

    @Test
    fun `existing fixed presets remain backward compatible`() {
        listOf(
            HistoryPeriodPreset.HOURS_24,
            HistoryPeriodPreset.DAYS_7,
            HistoryPeriodPreset.DAYS_14,
            HistoryPeriodPreset.DAYS_30,
            HistoryPeriodPreset.DAYS_90,
        ).forEach { preset ->
            assertEquals(preset, parseFixedHistoryPreset(preset.name))
        }
    }

    @Test
    fun `custom unknown blank and missing values use the safe default`() {
        listOf(null, "", "hours_6", "REMOVED_PRESET", HistoryPeriodPreset.CUSTOM.name)
            .forEach { stored ->
                assertEquals(HistoryPeriodPreset.HOURS_24, parseFixedHistoryPreset(stored))
            }
    }
}
