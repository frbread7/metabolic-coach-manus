package com.young.metaboliccoach.wear.data

import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome

enum class PendingSessionMutation {
    STARTED,
    COMPLETED,
}

data class WearSessionReplica(
    val activeSession: ActiveWearSession? = null,
    val pendingCommand: QuickActionCommand? = null,
    val pendingMutation: PendingSessionMutation? = null,
    val pendingTransported: Boolean = false,
    val completionTombstoneSessionId: String? = null,
    val syncMessage: String? = null,
) {
    val blocksNewSession: Boolean
        get() = activeSession != null || pendingCommand != null
}

object WearSessionReplicaReducer {
    fun queueStart(
        current: WearSessionReplica,
        session: ActiveWearSession,
        command: QuickActionCommand,
    ): WearSessionReplica {
        require(!current.blocksNewSession) { "A session lifecycle is already active or pending." }
        return WearSessionReplica(
            activeSession = session,
            pendingCommand = command,
            pendingMutation = PendingSessionMutation.STARTED,
        )
    }

    fun queueCompletion(
        current: WearSessionReplica,
        command: QuickActionCommand,
    ): WearSessionReplica {
        val session = requireNotNull(current.activeSession) { "No active session to complete." }
        require(
            current.pendingMutation != PendingSessionMutation.STARTED ||
                current.pendingTransported
        ) { "The start command must reach Data Layer before completion." }
        return WearSessionReplica(
            activeSession = null,
            pendingCommand = command,
            pendingMutation = PendingSessionMutation.COMPLETED,
            completionTombstoneSessionId = session.id,
        )
    }

    fun markTransported(
        current: WearSessionReplica,
        commandId: String,
    ): WearSessionReplica = if (current.pendingCommand?.id == commandId) {
        current.copy(pendingTransported = true, syncMessage = null)
    } else {
        current
    }

    fun markTransportError(
        current: WearSessionReplica,
        commandId: String,
    ): WearSessionReplica = if (current.pendingCommand?.id == commandId) {
        current.copy(pendingTransported = false, syncMessage = "Waiting to reconnect to phone")
    } else {
        current
    }

    fun reconcile(
        current: WearSessionReplica,
        remoteSession: InterventionSession?,
        ack: SessionCommandAck?,
    ): WearSessionReplica {
        val pending = current.pendingCommand
        val matchingAck = ack?.takeIf { it.commandId == pending?.id }
        if (matchingAck != null) {
            val remoteActive = remoteSession.toActiveWearSession()
            return when (matchingAck.outcome) {
                SessionCommandOutcome.APPLIED -> WearSessionReplica(
                    activeSession = remoteActive,
                )
                SessionCommandOutcome.REJECTED_EXPIRED -> WearSessionReplica(
                    activeSession = remoteActive,
                    syncMessage = "The offline action expired before the phone received it.",
                )
                SessionCommandOutcome.REJECTED_UNSAFE -> WearSessionReplica(
                    activeSession = remoteActive,
                    syncMessage = "The coached action was paused after glucose conditions changed.",
                )
                SessionCommandOutcome.REJECTED_CONFLICT -> WearSessionReplica(
                    activeSession = remoteActive,
                    syncMessage = "The phone kept a different active session.",
                )
            }
        }
        if (pending != null) return current
        return WearSessionReplica(activeSession = remoteSession.toActiveWearSession())
    }

    private fun InterventionSession?.toActiveWearSession(): ActiveWearSession? =
        this?.takeIf { it.status == InterventionStatus.STARTED }?.let {
            ActiveWearSession(
                id = it.id,
                type = it.type,
                startedAtEpochMillis = it.startedAtEpochMillis,
                durationMinutes = it.targetDurationMinutes,
                targetFloors = it.targetFloors,
            )
        }
}
