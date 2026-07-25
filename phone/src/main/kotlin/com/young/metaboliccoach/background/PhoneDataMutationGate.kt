package com.young.metaboliccoach.background

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes phone-owned local data reads/mutations that must not cross an erase boundary.
 *
 * Potentially slow provider work remains inside the same safety boundary, but an ordinary local
 * operation can cancel it before waiting for the lock. Current providers are coroutine-cancellable,
 * so commands and erase do not wait through the configured network retry window.
 */
@Singleton
class PhoneDataMutationGate @Inject constructor() {
    private val mutex = Mutex()
    private val preemptibleQueue = Mutex()
    private val activePreemptibleJob = AtomicReference<Job?>(null)
    private val localWaiterCount = MutableStateFlow(0)

    suspend fun <T> withLock(block: suspend () -> T): T {
        localWaiterCount.update { it + 1 }
        try {
            val caller = currentCoroutineContext().job
            val active = activePreemptibleJob.get()
            check(active !== caller) {
                "A preemptible data operation cannot re-enter the mutation gate."
            }
            active?.cancel(PhoneDataOperationPreemptedException())
            return mutex.withLock { block() }
        } finally {
            localWaiterCount.update { it - 1 }
        }
    }

    suspend fun <T> withPreemptibleProviderLock(block: suspend () -> T): T =
        supervisorScope {
            async {
                preemptibleQueue.withLock {
                    val job = currentCoroutineContext().job
                    check(activePreemptibleJob.compareAndSet(null, job)) {
                        "Only one preemptible provider operation may be registered."
                    }
                    try {
                        localWaiterCount.first { it == 0 }
                        mutex.lock()
                        try {
                            block()
                        } finally {
                            activePreemptibleJob.compareAndSet(job, null)
                            mutex.unlock()
                        }
                    } finally {
                        activePreemptibleJob.compareAndSet(job, null)
                    }
                }
            }.await()
        }
}

class PhoneDataOperationPreemptedException :
    CancellationException("Provider refresh was preempted by a local data operation.")
