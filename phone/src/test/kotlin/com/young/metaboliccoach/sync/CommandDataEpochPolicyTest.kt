package com.young.metaboliccoach.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDataEpochPolicyTest {
    @Test
    fun `commands remain compatible before the first reset`() {
        assertTrue(
            CommandDataEpochPolicy.isCurrent(
                currentResetId = null,
                commandResetId = null,
            ),
        )
        assertTrue(
            CommandDataEpochPolicy.isCurrent(
                currentResetId = null,
                commandResetId = "watch-token",
            ),
        )
    }

    @Test
    fun `after reset only commands carrying the current token are accepted`() {
        assertTrue(
            CommandDataEpochPolicy.isCurrent(
                currentResetId = "current",
                commandResetId = "current",
            ),
        )
        assertFalse(
            CommandDataEpochPolicy.isCurrent(
                currentResetId = "current",
                commandResetId = null,
            ),
        )
        assertFalse(
            CommandDataEpochPolicy.isCurrent(
                currentResetId = "current",
                commandResetId = "previous",
            ),
        )
    }
}
