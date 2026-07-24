package com.young.metaboliccoach.wear

import android.app.Application
import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.wear.data.SessionStore
import com.young.metaboliccoach.wear.data.WearCommandOutbox
import com.young.metaboliccoach.wear.sync.WatchStateConsumer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

@HiltAndroidApp
class WearApplication : Application() {
    @Inject lateinit var watchSyncRepository: WatchSyncRepository
    @Inject lateinit var stateConsumer: WatchStateConsumer
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var commandOutbox: WearCommandOutbox

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            watchSyncRepository.observeWatchState()
                .filterNotNull()
                .onEach(stateConsumer::handle)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        delay(
                            ((attempt + 1) * BASE_RETRY_DELAY_MILLIS)
                                .coerceAtMost(MAX_RETRY_DELAY_MILLIS),
                        )
                        true
                    }
                }
                .collect()
        }
        applicationScope.launch {
            sessionStore.replica
                .mapNotNull { replica ->
                    replica.pendingCommand?.takeIf { !replica.pendingTransported }
                }
                .onEach { command ->
                    try {
                        watchSyncRepository.enqueue(command)
                        sessionStore.markTransported(command.id)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        sessionStore.markTransportError(command.id)
                        throw error
                    }
                }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        delay(
                            ((attempt + 1) * BASE_RETRY_DELAY_MILLIS)
                                .coerceAtMost(MAX_RETRY_DELAY_MILLIS),
                        )
                        true
                    }
                }
                .collect()
        }
        applicationScope.launch {
            commandOutbox.commands
                .mapNotNull(List<QuickActionCommand>::firstOrNull)
                .distinctUntilChanged()
                .onEach { command ->
                    watchSyncRepository.enqueue(command)
                    commandOutbox.remove(command.id)
                }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        delay(
                            ((attempt + 1) * BASE_RETRY_DELAY_MILLIS)
                                .coerceAtMost(MAX_RETRY_DELAY_MILLIS),
                        )
                        true
                    }
                }
                .collect()
        }
    }

    private companion object {
        const val BASE_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
    }
}
