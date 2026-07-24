package com.young.metaboliccoach.sync

import com.young.metaboliccoach.background.CommandHandlingResult
import com.young.metaboliccoach.background.DeferredCompletionPolicy
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionAckOrderingPolicyTest {
    @Test
    fun `rejected start then completion then start replay publishes completion acknowledgement`() {
        val rejectedStart = storedAck(
            commandId = "start",
            commandType = QuickActionType.START_WALK,
            createdAtEpochMillis = 1_000L,
        )
        val completionCommand = QuickActionCommand(
            id = "complete",
            type = QuickActionType.MARK_COMPLETED,
            createdAtEpochMillis = 2_000L,
            sessionId = "session",
        )
        val completionResult = DeferredCompletionPolicy.resolve(
            command = completionCommand,
            result = CommandHandlingResult.Deferred,
            lastSessionAck = rejectedStart.ack,
        )
        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            completionResult,
        )
        val completion = storedAck(
            commandId = completionCommand.id,
            commandType = completionCommand.type,
            createdAtEpochMillis = completionCommand.createdAtEpochMillis,
        )

        val afterCompletion = SessionAckOrderingPolicy.select(rejectedStart, completion)
        val afterStartReplay = SessionAckOrderingPolicy.select(afterCompletion, rejectedStart)

        assertEquals(completion.ack, afterStartReplay.ack)
    }

    @Test
    fun `completion acknowledgement remains publishable after older start replay`() {
        val completion = storedAck(
            commandId = "complete",
            commandType = QuickActionType.MARK_COMPLETED,
            createdAtEpochMillis = 2_000L,
        )
        val replayedStart = storedAck(
            commandId = "start",
            commandType = QuickActionType.START_WALK,
            createdAtEpochMillis = 1_000L,
        )

        val publishable = SessionAckOrderingPolicy.select(
            current = completion,
            incoming = replayedStart,
        )

        assertEquals(completion.ack, publishable.ack)
    }

    @Test
    fun `completion supersedes start for the same session`() {
        val start = storedAck(
            commandId = "start",
            commandType = QuickActionType.START_WALK,
            createdAtEpochMillis = 1_000L,
        )
        val completion = storedAck(
            commandId = "complete",
            commandType = QuickActionType.MARK_COMPLETED,
            createdAtEpochMillis = 2_000L,
        )

        assertEquals(
            completion.ack,
            SessionAckOrderingPolicy.select(start, completion).ack,
        )
    }

    @Test
    fun `older unrelated acknowledgement cannot replace newer publication`() {
        val newer = storedAck(
            commandId = "newer",
            commandType = QuickActionType.START_WALK,
            createdAtEpochMillis = 2_000L,
            sessionId = "new-session",
        )
        val older = storedAck(
            commandId = "older",
            commandType = QuickActionType.MARK_COMPLETED,
            createdAtEpochMillis = 1_000L,
            sessionId = "old-session",
        )

        assertEquals(
            newer.ack,
            SessionAckOrderingPolicy.select(newer, older).ack,
        )
    }

    private fun storedAck(
        commandId: String,
        commandType: QuickActionType,
        createdAtEpochMillis: Long,
        sessionId: String = "session",
    ) = StoredSessionAck(
        ack = SessionCommandAck(
            commandId = commandId,
            sessionId = sessionId,
            outcome = SessionCommandOutcome.REJECTED_CONFLICT,
        ),
        commandType = commandType,
        commandCreatedAtEpochMillis = createdAtEpochMillis,
    )
}
