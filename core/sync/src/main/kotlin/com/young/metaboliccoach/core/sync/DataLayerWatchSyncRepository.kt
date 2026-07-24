package com.young.metaboliccoach.core.sync

import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.WatchState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@Singleton
class DataLayerWatchSyncRepository @Inject constructor(
    private val transport: DataLayerTransport,
    private val codec: WatchStateCodec,
) : WatchSyncRepository {
    override fun observeWatchState(): Flow<WatchState?> =
        transport.observe(SyncPaths.CURRENT)
            .map { (_, data) -> codec.decode(data) }
            .distinctUntilChanged()

    override suspend fun publish(state: WatchState) {
        transport.put(
            path = SyncPaths.CURRENT,
            data = codec.encode(state),
            urgent = true,
        )
    }

    override suspend fun enqueue(command: QuickActionCommand) {
        transport.put(
            path = SyncPaths.action(command.id),
            data = codec.encode(command),
            urgent = true,
        )
    }

    override fun observeCommands(): Flow<QuickActionCommand> =
        transport.observe(SyncPaths.ACTION_PREFIX)
            .mapNotNull { (_, data) -> codec.decodeCommand(data) }
            .distinctUntilChanged()

    override suspend fun acknowledge(commandId: String) {
        transport.delete(SyncPaths.action(commandId))
    }
}

