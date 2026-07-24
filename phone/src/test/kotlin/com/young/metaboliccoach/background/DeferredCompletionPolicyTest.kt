package com.young.metaboliccoach.background

import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredCompletionPolicyTest {
    @Test
    fun `completion inherits rejection of its prerequisite start`() {
        val outcome = DeferredCompletionPolicy.resolve(
            command = completion(),
            result = CommandHandlingResult.Deferred,
            lastSessionAck = SessionCommandAck(
                commandId = "start",
                sessionId = "session",
                outcome = SessionCommandOutcome.REJECTED_CONFLICT,
            ),
        )

        assertEquals(
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT),
            outcome,
        )
    }

    @Test
    fun `completion remains deferred after applied prerequisite until session is visible`() {
        val outcome = DeferredCompletionPolicy.resolve(
            command = completion(),
            result = CommandHandlingResult.Deferred,
            lastSessionAck = SessionCommandAck(
                commandId = "start",
                sessionId = "session",
                outcome = SessionCommandOutcome.APPLIED,
            ),
        )

        assertEquals(CommandHandlingResult.Deferred, outcome)
    }

    @Test
    fun `another session rejection cannot terminate this completion`() {
        val outcome = DeferredCompletionPolicy.resolve(
            command = completion(),
            result = CommandHandlingResult.Deferred,
            lastSessionAck = SessionCommandAck(
                commandId = "other-start",
                sessionId = "other",
                outcome = SessionCommandOutcome.REJECTED_EXPIRED,
            ),
        )

        assertEquals(CommandHandlingResult.Deferred, outcome)
    }

    private fun completion() = QuickActionCommand(
        id = "complete",
        type = QuickActionType.MARK_COMPLETED,
        createdAtEpochMillis = 200,
        sessionId = "session",
    )
}
