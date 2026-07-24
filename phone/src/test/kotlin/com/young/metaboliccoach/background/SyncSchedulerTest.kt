package com.young.metaboliccoach.background

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class SyncSchedulerTest {
    @Test
    fun `background permission cancellation propagates without becoming denial`() = runTest {
        val cancellation = CancellationException("cancel permission check")

        try {
            backgroundReadAccessOrFalse { throw cancellation }
            fail("Expected cancellation to propagate.")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `background permission failure safely falls back to foreground refresh`() = runTest {
        assertFalse(
            backgroundReadAccessOrFalse {
                throw IllegalStateException("Health Connect unavailable")
            },
        )
    }
}
