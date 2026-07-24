package com.young.metaboliccoach.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.young.metaboliccoach.core.data.provider.XdripGlucoseIngestor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class XdripBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var ingestor: XdripGlucoseIngestor
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var mutationGate: PhoneDataMutationGate

    override fun onReceive(context: Context, intent: Intent) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            sentFromPackage != XDRIP_PACKAGE
        ) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accepted = mutationGate.withLock {
                    ingestor.ingest(intent)
                }
                if (accepted) {
                    syncScheduler.enqueueImmediate(refreshProviders = false)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val XDRIP_PACKAGE = "com.eveningoutpost.dexdrip"
    }
}
