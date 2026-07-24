package com.young.metaboliccoach.wear.sync

import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.WatchState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDataResetPolicyTest {
    @Test
    fun `a new reset token clears wear data once`() {
        val previous = state(resetId = "previous")
        val incoming = state(resetId = "current")

        assertTrue(RemoteDataResetPolicy.shouldReset(previous, incoming))
        assertFalse(RemoteDataResetPolicy.shouldReset(incoming, incoming))
    }

    @Test
    fun `legacy state without reset token never requests erasure`() {
        assertFalse(
            RemoteDataResetPolicy.shouldReset(
                current = state(resetId = "previous"),
                incoming = state(resetId = null),
            ),
        )
    }

    @Test
    fun `reset is honored even when cached watch state is unavailable`() {
        assertTrue(
            RemoteDataResetPolicy.shouldReset(
                current = null,
                incoming = state(resetId = "current"),
            ),
        )
    }

    private fun state(resetId: String?) = WatchState(
        glucose = null,
        activity = null,
        recommendation = null,
        settings = DefaultCoachSettings.create(),
        phoneBatteryPercent = null,
        generatedAtEpochMillis = 1,
        dataResetId = resetId,
    )
}
