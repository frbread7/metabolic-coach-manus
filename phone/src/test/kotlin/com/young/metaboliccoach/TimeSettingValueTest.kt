package com.young.metaboliccoach

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSettingValueTest {
    @Test
    fun `changing hour preserves the exact minute`() {
        assertEquals(23 * 60 + 47, minuteOfDayWithHour(8 * 60 + 47, 23))
    }

    @Test
    fun `changing minute preserves the exact hour`() {
        assertEquals(8 * 60 + 59, minuteOfDayWithMinute(8 * 60 + 15, 59))
    }

    @Test
    fun `time components are clamped to a valid minute of day`() {
        assertEquals(47, minuteOfDayWithHour(47, -1))
        assertEquals(23 * 60 + 59, minuteOfDayWithMinute(23 * 60, 99))
    }
}
