package com.young.metaboliccoach.background

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDataMutationGateTest {
    @Test
    fun `second data operation cannot cross the active operation`() = runTest {
        val gate = PhoneDataMutationGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async {
            gate.withLock {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()
        val second = async {
            gate.withLock {
                secondEntered.complete(Unit)
            }
        }

        testScheduler.runCurrent()
        assertFalse(secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertTrue(secondEntered.isCompleted)
    }

    @Test
    fun `local operation preempts provider work before entering the safety boundary`() = runTest {
        val gate = PhoneDataMutationGate()
        val providerEntered = CompletableDeferred<Unit>()
        val localEntered = CompletableDeferred<Unit>()

        val provider = async {
            try {
                gate.withPreemptibleProviderLock {
                    providerEntered.complete(Unit)
                    awaitCancellation()
                }
                "completed"
            } catch (_: PhoneDataOperationPreemptedException) {
                "preempted"
            }
        }
        providerEntered.await()
        val local = async {
            gate.withLock {
                localEntered.complete(Unit)
            }
        }

        local.await()

        assertEquals("preempted", provider.await())
        assertFalse(provider.isCancelled)
        assertTrue(localEntered.isCompleted)
    }

    @Test
    fun `queued provider cannot overtake local operation after active provider is preempted`() =
        runTest {
            val gate = PhoneDataMutationGate()
            val firstProviderEntered = CompletableDeferred<Unit>()
            val secondProviderEntered = CompletableDeferred<Unit>()
            val localEntered = CompletableDeferred<Unit>()
            val releaseLocal = CompletableDeferred<Unit>()

            val firstProvider = async {
                try {
                    gate.withPreemptibleProviderLock {
                        firstProviderEntered.complete(Unit)
                        awaitCancellation()
                    }
                    "completed"
                } catch (_: PhoneDataOperationPreemptedException) {
                    "preempted"
                }
            }
            firstProviderEntered.await()
            val secondProvider = async {
                gate.withPreemptibleProviderLock {
                    secondProviderEntered.complete(Unit)
                }
            }
            testScheduler.runCurrent()
            assertFalse(secondProviderEntered.isCompleted)

            val local = async {
                gate.withLock {
                    localEntered.complete(Unit)
                    releaseLocal.await()
                }
            }
            localEntered.await()

            assertEquals("preempted", firstProvider.await())
            assertFalse(secondProviderEntered.isCompleted)
            releaseLocal.complete(Unit)
            local.await()
            secondProvider.await()
            assertTrue(secondProviderEntered.isCompleted)
        }
}
