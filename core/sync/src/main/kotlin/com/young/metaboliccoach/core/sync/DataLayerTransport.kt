package com.young.metaboliccoach.core.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

interface DataLayerTransport {
    fun observe(pathPrefix: String): Flow<Pair<String, DataMap>>
    suspend fun put(path: String, data: DataMap, urgent: Boolean)
    suspend fun delete(pathPrefix: String)
}

@Singleton
class PlayServicesDataLayerTransport @Inject constructor(
    @ApplicationContext context: Context,
) : DataLayerTransport {
    private val client = Wearable.getDataClient(context)

    override fun observe(pathPrefix: String): Flow<Pair<String, DataMap>> = callbackFlow {
        val filterUri = wearableUri(pathPrefix)
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                val item = event.dataItem
                if (
                    event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED &&
                    item.uri.path?.startsWith(pathPrefix) == true
                ) {
                    trySend(item.uri.path.orEmpty() to DataMapItem.fromDataItem(item).dataMap)
                }
            }
        }
        try {
            client.addListener(listener, filterUri, DataClient.FILTER_PREFIX).awaitResult()
            val items = client.getDataItems(filterUri, DataClient.FILTER_PREFIX).awaitResult()
            try {
                items.forEach { item ->
                    if (item.uri.path?.startsWith(pathPrefix) == true) {
                        trySend(item.uri.path.orEmpty() to DataMapItem.fromDataItem(item).dataMap)
                    }
                }
            } finally {
                items.release()
            }
            awaitClose()
        } finally {
            client.removeListener(listener)
        }
    }

    override suspend fun put(path: String, data: DataMap, urgent: Boolean) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putAll(data)
        }.asPutDataRequest().apply {
            if (urgent) setUrgent()
        }
        client.putDataItem(request).awaitResult()
    }

    override suspend fun delete(pathPrefix: String) {
        client.deleteDataItems(wearableUri(pathPrefix), DataClient.FILTER_PREFIX).awaitResult()
    }

    private fun wearableUri(pathPrefix: String): Uri = Uri.Builder()
        .scheme("wear")
        .authority("*")
        .path(pathPrefix)
        .build()

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener(continuation::resume)
            addOnFailureListener(continuation::resumeWithException)
            addOnCanceledListener(continuation::cancel)
        }
}
