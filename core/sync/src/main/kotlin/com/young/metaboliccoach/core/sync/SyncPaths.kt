package com.young.metaboliccoach.core.sync

object SyncPaths {
    const val ROOT = "/metabolic/v1"
    const val CURRENT = "$ROOT/current"
    const val ACTION_PREFIX = "$ROOT/action/"
    const val ACTION_ACK_PREFIX = "$ROOT/action-ack/"

    fun action(commandId: String) = "$ACTION_PREFIX$commandId"
    fun actionAck(commandId: String) = "$ACTION_ACK_PREFIX$commandId"
}

object SyncSchema {
    const val VERSION = 1
}

