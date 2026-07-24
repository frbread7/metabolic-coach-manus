package com.young.metaboliccoach.wear.sync

import com.young.metaboliccoach.core.model.WatchState

object RemoteStateOrderPolicy {
    fun shouldAccept(
        current: WatchState?,
        incoming: WatchState,
        hasPendingMutation: Boolean,
    ): Boolean {
        val incomingInstance = incoming.phoneInstanceId
        val incomingRevision = incoming.stateRevision
        if (incomingInstance == null || incomingRevision == null) {
            return !hasPendingMutation && current?.stateRevision == null
        }
        val currentInstance = current?.phoneInstanceId
        val currentRevision = current?.stateRevision
        if (currentInstance == null || currentRevision == null) return true
        if (incomingInstance != currentInstance) return true
        return incomingRevision > currentRevision
    }
}
