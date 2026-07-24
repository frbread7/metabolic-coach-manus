package com.young.metaboliccoach.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.young.metaboliccoach.background.PhoneCommandProcessor
import com.young.metaboliccoach.core.sync.SyncPaths
import com.young.metaboliccoach.core.sync.WatchStateCodec
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneDataLayerService : WearableListenerService() {
    @Inject lateinit var codec: WatchStateCodec
    @Inject lateinit var commandProcessor: PhoneCommandProcessor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            val item = event.dataItem
            if (
                event.type != DataEvent.TYPE_CHANGED ||
                item.uri.path?.startsWith(SyncPaths.ACTION_PREFIX) != true
            ) {
                return@forEach
            }
            val command = codec.decodeCommand(DataMapItem.fromDataItem(item).dataMap)
                ?: return@forEach
            scope.launch {
                commandProcessor.process(command)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
