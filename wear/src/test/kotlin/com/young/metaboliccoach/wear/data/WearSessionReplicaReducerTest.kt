package com.young.metaboliccoach.wear.data

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSessionReplicaReducerTest {
    @Test
    fun `unacknowledged phone state cannot erase pending local start`() {
        val pending = WearSessionReplicaReducer.queueStart(
            WearSessionReplica(),
            activeSession(),
            startCommand(),
        )

        val reconciled = WearSessionReplicaReducer.reconcile(pending, null, null)

        assertEquals("session", reconciled.activeSession?.id)
        assertEquals("start-command", reconciled.pendingCommand?.id)
        assertEquals(startCommand(), reconciled.pendingCommand)
    }

    @Test
    fun `unacknowledged phone state cannot revive pending local completion`() {
        val pending = WearSessionReplicaReducer.queueCompletion(
            WearSessionReplica(
                activeSession = activeSession(),
                pendingCommand = startCommand(),
                pendingMutation = PendingSessionMutation.STARTED,
                pendingTransported = true,
            ),
            completionCommand(),
        )

        val reconciled = WearSessionReplicaReducer.reconcile(
            pending,
            remoteActiveSession(),
            null,
        )

        assertNull(reconciled.activeSession)
        assertEquals("session", reconciled.completionTombstoneSessionId)
    }

    @Test
    fun `matching applied start acknowledgement adopts authoritative session`() {
        val pending = WearSessionReplicaReducer.queueStart(
            WearSessionReplica(),
            activeSession(),
            startCommand(),
        )

        val reconciled = WearSessionReplicaReducer.reconcile(
            pending,
            remoteActiveSession(),
            ack("start-command", SessionCommandOutcome.APPLIED),
        )

        assertEquals("session", reconciled.activeSession?.id)
        assertNull(reconciled.pendingCommand)
    }

    @Test
    fun `matching applied completion acknowledgement clears tombstone`() {
        val pending = WearSessionReplicaReducer.queueCompletion(
            WearSessionReplica(activeSession = activeSession()),
            completionCommand(),
        )

        val reconciled = WearSessionReplicaReducer.reconcile(
            pending,
            null,
            ack("complete-command", SessionCommandOutcome.APPLIED),
        )

        assertNull(reconciled.activeSession)
        assertNull(reconciled.pendingCommand)
        assertNull(reconciled.completionTombstoneSessionId)
    }

    @Test
    fun `rejected conflicting start adopts phone session and exposes error`() {
        val pending = WearSessionReplicaReducer.queueStart(
            WearSessionReplica(),
            activeSession(),
            startCommand(),
        )
        val other = remoteActiveSession(id = "other")

        val reconciled = WearSessionReplicaReducer.reconcile(
            pending,
            other,
            ack("start-command", SessionCommandOutcome.REJECTED_CONFLICT),
        )

        assertEquals("other", reconciled.activeSession?.id)
        assertTrue(reconciled.syncMessage.orEmpty().contains("different"))
    }

    @Test
    fun `unsafe rejection clears optimistic session and exposes changed conditions`() {
        val pending = WearSessionReplicaReducer.queueStart(
            WearSessionReplica(),
            activeSession(),
            startCommand(),
        )

        val reconciled = WearSessionReplicaReducer.reconcile(
            pending,
            null,
            ack("start-command", SessionCommandOutcome.REJECTED_UNSAFE),
        )

        assertNull(reconciled.activeSession)
        assertTrue(reconciled.syncMessage.orEmpty().contains("conditions changed"))
    }

    @Test
    fun `completion converges after its prerequisite start is rejected`() {
        val pendingCompletion = WearSessionReplicaReducer.queueCompletion(
            WearSessionReplica(
                activeSession = activeSession(),
                pendingCommand = startCommand(),
                pendingMutation = PendingSessionMutation.STARTED,
                pendingTransported = true,
            ),
            completionCommand(),
        )
        val afterStartRejection = WearSessionReplicaReducer.reconcile(
            pendingCompletion,
            remoteActiveSession(id = "other"),
            ack("start-command", SessionCommandOutcome.REJECTED_CONFLICT),
        )

        assertEquals("complete-command", afterStartRejection.pendingCommand?.id)
        assertNull(afterStartRejection.activeSession)

        val converged = WearSessionReplicaReducer.reconcile(
            afterStartRejection,
            remoteActiveSession(id = "other"),
            ack("complete-command", SessionCommandOutcome.REJECTED_CONFLICT),
        )

        assertEquals("other", converged.activeSession?.id)
        assertNull(converged.pendingCommand)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `completion cannot replace a start that has not reached Data Layer`() {
        WearSessionReplicaReducer.queueCompletion(
            WearSessionReplica(
                activeSession = activeSession(),
                pendingCommand = startCommand(),
                pendingMutation = PendingSessionMutation.STARTED,
                pendingTransported = false,
            ),
            completionCommand(),
        )
    }

    private fun activeSession() = ActiveWearSession(
        id = "session",
        type = InterventionType.WALK,
        startedAtEpochMillis = 100,
        durationMinutes = 10,
        targetFloors = null,
    )

    private fun remoteActiveSession(id: String = "session") = InterventionSession(
        id = id,
        type = InterventionType.WALK,
        status = InterventionStatus.STARTED,
        startedAtEpochMillis = 100,
        endedAtEpochMillis = null,
        targetDurationMinutes = 10,
        targetFloors = null,
        baselineGlucoseMgDl = 140,
        glucoseAfterMgDl = null,
    )

    private fun startCommand() = QuickActionCommand(
        id = "start-command",
        type = QuickActionType.START_WALK,
        createdAtEpochMillis = 100,
        sessionId = "session",
        recommendationId = "RAPID_GLUCOSE_RISE:v3:${"a".repeat(64)}",
        recommendationValidUntilEpochMillis = 10_000,
        recommendationReason = CoachReason.RAPID_GLUCOSE_RISE,
        recommendationAlgorithmVersion = 3,
        recommendationCreatedAtEpochMillis = 90,
        triggerContextId = "rapid-pair:v3:${"a".repeat(64)}",
        triggerAtEpochMillis = 80,
        glucoseSourceId = "nightscout:server-a",
        safetyReadingId = "latest-reading",
        safetyReadingAtEpochMillis = 80,
    )

    private fun completionCommand() = QuickActionCommand(
        id = "complete-command",
        type = QuickActionType.MARK_COMPLETED,
        createdAtEpochMillis = 200,
        sessionId = "session",
    )

    private fun ack(commandId: String, outcome: SessionCommandOutcome) =
        SessionCommandAck(commandId, "session", outcome)
}
