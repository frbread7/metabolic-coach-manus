package com.young.metaboliccoach.background

import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import com.young.metaboliccoach.sync.PhoneSyncMetadataStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito

class PhoneCommandProcessorTest {
    @Test
    fun `terminally acknowledged replay is not handled again`() = runTest {
        val command = QuickActionCommand(
            id = "start",
            type = QuickActionType.START_WALK,
            createdAtEpochMillis = 1_000L,
            sessionId = "session",
        )
        val quickActionHandler = Mockito.mock(QuickActionHandler::class.java)
        val watchSyncRepository = Mockito.mock(WatchSyncRepository::class.java)
        val metadataStore = Mockito.mock(PhoneSyncMetadataStore::class.java)
        val syncScheduler = Mockito.mock(SyncScheduler::class.java)
        Mockito.`when`(metadataStore.isCommandFromCurrentDataEpoch(command)).thenReturn(true)
        Mockito.`when`(metadataStore.commandAck(command.id)).thenReturn(
            SessionCommandAck(
                commandId = command.id,
                sessionId = command.sessionId,
                outcome = SessionCommandOutcome.REJECTED_UNSAFE,
            ),
        )
        val processor = PhoneCommandProcessor(
            quickActionHandler = quickActionHandler,
            watchSyncRepository = watchSyncRepository,
            syncMetadataStore = metadataStore,
            syncScheduler = syncScheduler,
            mutationGate = PhoneDataMutationGate(),
        )

        processor.process(command)

        Mockito.verify(quickActionHandler, Mockito.never()).handle(command)
        Mockito.verify(syncScheduler).enqueueImmediate(
            refreshProviders = false,
            requireDelivery = true,
        )
        Mockito.verify(watchSyncRepository).acknowledge(command.id)
    }

    @Test
    fun `acknowledged snooze replay is not applied a second time`() = runTest {
        val command = QuickActionCommand(
            id = "snooze",
            type = QuickActionType.SNOOZE,
            createdAtEpochMillis = 1_000L,
        )
        val quickActionHandler = Mockito.mock(QuickActionHandler::class.java)
        val watchSyncRepository = Mockito.mock(WatchSyncRepository::class.java)
        val metadataStore = Mockito.mock(PhoneSyncMetadataStore::class.java)
        val syncScheduler = Mockito.mock(SyncScheduler::class.java)
        Mockito.`when`(metadataStore.isCommandFromCurrentDataEpoch(command)).thenReturn(true)
        Mockito.`when`(metadataStore.commandAck(command.id)).thenReturn(
            SessionCommandAck(
                commandId = command.id,
                sessionId = null,
                outcome = SessionCommandOutcome.APPLIED,
            ),
        )
        val processor = PhoneCommandProcessor(
            quickActionHandler = quickActionHandler,
            watchSyncRepository = watchSyncRepository,
            syncMetadataStore = metadataStore,
            syncScheduler = syncScheduler,
            mutationGate = PhoneDataMutationGate(),
        )

        processor.process(command)

        Mockito.verify(quickActionHandler, Mockito.never()).handle(command)
        Mockito.verify(syncScheduler).enqueueImmediate(
            refreshProviders = false,
            requireDelivery = false,
        )
        Mockito.verify(watchSyncRepository).acknowledge(command.id)
    }

    @Test
    fun `command from an erased data epoch is rejected without applying it`() = runTest {
        val command = QuickActionCommand(
            id = "stale-start",
            type = QuickActionType.START_WALK,
            createdAtEpochMillis = 1_000L,
            sessionId = "stale-session",
            dataResetId = "previous-reset",
        )
        val quickActionHandler = Mockito.mock(QuickActionHandler::class.java)
        val watchSyncRepository = Mockito.mock(WatchSyncRepository::class.java)
        val metadataStore = Mockito.mock(PhoneSyncMetadataStore::class.java)
        val syncScheduler = Mockito.mock(SyncScheduler::class.java)
        Mockito.`when`(metadataStore.isCommandFromCurrentDataEpoch(command)).thenReturn(false)
        val processor = PhoneCommandProcessor(
            quickActionHandler = quickActionHandler,
            watchSyncRepository = watchSyncRepository,
            syncMetadataStore = metadataStore,
            syncScheduler = syncScheduler,
            mutationGate = PhoneDataMutationGate(),
        )

        processor.process(command)

        Mockito.verify(quickActionHandler, Mockito.never()).handle(command)
        Mockito.verify(metadataStore).recordCommandAck(
            command,
            SessionCommandOutcome.REJECTED_EXPIRED,
        )
        Mockito.verify(syncScheduler).enqueueImmediate(
            refreshProviders = false,
            requireDelivery = true,
        )
        Mockito.verify(watchSyncRepository).acknowledge(command.id)
    }
}
