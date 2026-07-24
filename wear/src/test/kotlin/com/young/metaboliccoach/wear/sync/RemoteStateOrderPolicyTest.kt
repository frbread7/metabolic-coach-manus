package com.young.metaboliccoach.wear.sync

import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.WatchState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStateOrderPolicyTest {
    @Test
    fun `same phone accepts only a higher revision`() {
        val current = state("phone-a", 4)

        assertFalse(RemoteStateOrderPolicy.shouldAccept(current, state("phone-a", 3), false))
        assertFalse(RemoteStateOrderPolicy.shouldAccept(current, state("phone-a", 4), false))
        assertTrue(RemoteStateOrderPolicy.shouldAccept(current, state("phone-a", 5), false))
    }

    @Test
    fun `new phone instance starts a new revision epoch`() {
        assertTrue(
            RemoteStateOrderPolicy.shouldAccept(
                current = state("phone-a", 99),
                incoming = state("phone-b", 1),
                hasPendingMutation = true,
            ),
        )
    }

    @Test
    fun `legacy state cannot overwrite modern or pending state`() {
        val legacy = state(null, null)

        assertFalse(RemoteStateOrderPolicy.shouldAccept(state("phone-a", 1), legacy, false))
        assertFalse(RemoteStateOrderPolicy.shouldAccept(null, legacy, true))
        assertTrue(RemoteStateOrderPolicy.shouldAccept(null, legacy, false))
    }

    private fun state(instanceId: String?, revision: Long?) = WatchState(
        glucose = null,
        activity = null,
        recommendation = null,
        settings = DefaultCoachSettings.create(),
        phoneBatteryPercent = null,
        generatedAtEpochMillis = 1,
        phoneInstanceId = instanceId,
        stateRevision = revision,
    )
}
