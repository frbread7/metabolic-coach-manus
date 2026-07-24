package com.young.metaboliccoach.background

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes phone-owned local data reads/mutations that must not cross an erase boundary.
 */
@Singleton
class PhoneDataMutationGate @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T =
        mutex.withLock {
            block()
        }
}
