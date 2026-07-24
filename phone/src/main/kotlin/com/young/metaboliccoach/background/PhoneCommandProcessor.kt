package com.young.metaboliccoach.background

import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import com.young.metaboliccoach.sync.PhoneSyncMetadataStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PhoneCommandProcessor @Inject constructor(
    private val quickActionHandler: QuickActionHandler,
    private val watchSyncRepository: WatchSyncRepository,
    private val syncMetadataStore: PhoneSyncMetadataStore,
    private val syncScheduler: SyncScheduler,
    private val mutationGate: PhoneDataMutationGate,
) {
    private val started = AtomicBoolean(false)
    private val mutex = Mutex()
    private val deferred = linkedMapOf<String, QuickActionCommand>()

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            watchSyncRepository.observeCommands()
                .onEach(::process)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        delay(retryDelayMillis(attempt))
                        true
                    }
                }
                .collect()
        }
        scope.launch {
            while (true) {
                delay(DEFERRED_RECHECK_MILLIS)
                try {
                    mutationGate.withLock {
                        mutex.withLock {
                            drainDeferred()
                        }
                    }
                } catch (cause: CancellationException) {
                    throw cause
                } catch (_: Throwable) {
                    // The persistent DataItem remains authoritative; retry on the next lease.
                }
            }
        }
    }

    suspend fun process(command: QuickActionCommand) {
        mutationGate.withLock {
            mutex.withLock {
                if (handleAndAcknowledge(command)) {
                    deferred.remove(command.id)
                } else {
                    deferred[command.id] = command
                }
                drainDeferred()
            }
        }
    }

    /**
     * Called only while [PhoneDataMutationGate] is held by the erase coordinator.
     */
    suspend fun clearDeferredForDataReset() {
        mutex.withLock {
            deferred.clear()
        }
    }

    private suspend fun handleAndAcknowledge(command: QuickActionCommand): Boolean {
        if (!syncMetadataStore.isCommandFromCurrentDataEpoch(command)) {
            syncMetadataStore.recordCommandAck(
                command = command,
                outcome = SessionCommandOutcome.REJECTED_EXPIRED,
            )
            requestStateDeliveryAndAcknowledge(command)
            return true
        }
        if (syncMetadataStore.commandAck(command.id) != null) {
            requestStateDeliveryAndAcknowledge(command)
            return true
        }
        val initialResult = quickActionHandler.handle(command)
        val result = if (
            initialResult == CommandHandlingResult.Deferred &&
            command.type == QuickActionType.MARK_COMPLETED
        ) {
            DeferredCompletionPolicy.resolve(
                command = command,
                result = initialResult,
                lastSessionAck = syncMetadataStore.prerequisiteStartAck(command.sessionId),
            )
        } else {
            initialResult
        }
        if (result == CommandHandlingResult.Deferred) return false
        val outcome = when (result) {
            CommandHandlingResult.Applied ->
                SessionCommandOutcome.APPLIED
            is CommandHandlingResult.Rejected -> result.outcome
            CommandHandlingResult.Deferred -> error("Deferred commands are not terminal.")
        }
        syncMetadataStore.recordCommandAck(command = command, outcome = outcome)
        requestStateDeliveryAndAcknowledge(command)
        return true
    }

    private suspend fun requestStateDeliveryAndAcknowledge(command: QuickActionCommand) {
        syncScheduler.enqueueImmediate(
            refreshProviders = false,
            requireDelivery = command.type.isSessionMutation(),
        )
        watchSyncRepository.acknowledge(command.id)
    }

    private suspend fun drainDeferred() {
        deferred.values
            .sortedBy(QuickActionCommand::createdAtEpochMillis)
            .forEach { command ->
                if (handleAndAcknowledge(command)) {
                    deferred.remove(command.id)
                }
            }
    }

    private fun retryDelayMillis(attempt: Long): Long =
        ((attempt + 1) * BASE_RETRY_DELAY_MILLIS).coerceAtMost(MAX_RETRY_DELAY_MILLIS)

    private companion object {
        const val BASE_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
        const val DEFERRED_RECHECK_MILLIS = 60_000L
    }
}

internal object DeferredCompletionPolicy {
    fun resolve(
        command: QuickActionCommand,
        result: CommandHandlingResult,
        lastSessionAck: SessionCommandAck?,
    ): CommandHandlingResult {
        if (
            result != CommandHandlingResult.Deferred ||
            command.type != QuickActionType.MARK_COMPLETED ||
            command.sessionId == null
        ) {
            return result
        }
        val prerequisiteOutcome = lastSessionAck
            ?.takeIf { it.sessionId == command.sessionId }
            ?.outcome
            ?: return result
        return if (prerequisiteOutcome == SessionCommandOutcome.APPLIED) {
            result
        } else {
            CommandHandlingResult.Rejected(prerequisiteOutcome)
        }
    }
}

private fun QuickActionType.isSessionMutation(): Boolean =
    this == QuickActionType.START_WALK ||
        this == QuickActionType.START_STAIRS ||
        this == QuickActionType.MARK_COMPLETED
