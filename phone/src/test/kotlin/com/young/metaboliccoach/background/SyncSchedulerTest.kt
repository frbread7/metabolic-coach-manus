package com.young.metaboliccoach.background

import androidx.work.NetworkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `configured Nightscout schedules connected work without Health Connect background access`() {
        val policy = periodicRefreshPolicy(
            nightscoutConfigured = true,
            nightscoutPollingIntervalMinutes = 30,
            healthConnectBackgroundReadsAvailable = false,
        )

        assertTrue(policy.enabled)
        assertEquals(30, policy.intervalMinutes)
        assertEquals(NetworkType.CONNECTED, policy.networkType)
    }

    @Test
    fun `Nightscout polling is clamped to WorkManager minimum`() {
        val policy = periodicRefreshPolicy(
            nightscoutConfigured = true,
            nightscoutPollingIntervalMinutes = 5,
            healthConnectBackgroundReadsAvailable = false,
        )

        assertEquals(15, policy.intervalMinutes)
    }

    @Test
    fun `activity-only background refresh does not require network`() {
        val policy = periodicRefreshPolicy(
            nightscoutConfigured = false,
            nightscoutPollingIntervalMinutes = 60,
            healthConnectBackgroundReadsAvailable = true,
        )

        assertTrue(policy.enabled)
        assertEquals(NetworkType.NOT_REQUIRED, policy.networkType)
    }

    @Test
    fun `scheduler disables when no provider can refresh in background`() {
        val policy = periodicRefreshPolicy(
            nightscoutConfigured = false,
            nightscoutPollingIntervalMinutes = 15,
            healthConnectBackgroundReadsAvailable = false,
        )

        assertFalse(policy.enabled)
    }

    @Test
    fun `post meal work targets the configured meal-relative time`() {
        assertEquals(
            20 * 60_000L,
            postMealInitialDelayMillis(
                mealAtEpochMillis = 1_000_000L,
                delayMinutes = 30,
                nowEpochMillis = 1_600_000L,
            ),
        )
    }

    @Test
    fun `late post meal work runs immediately instead of adding another full delay`() {
        assertEquals(
            0L,
            postMealInitialDelayMillis(
                mealAtEpochMillis = 1_000_000L,
                delayMinutes = 5,
                nowEpochMillis = 1_400_000L,
            ),
        )
    }

    @Test
    fun `post meal failure retries only inside its window and attempt budget`() {
        assertTrue(
            shouldRetryPostMealWork(
                runAttemptCount = 0,
                nowEpochMillis = 1_000,
                expiresAtEpochMillis = 2_000,
            ),
        )
        assertFalse(
            shouldRetryPostMealWork(
                runAttemptCount = 2,
                nowEpochMillis = 1_000,
                expiresAtEpochMillis = 2_000,
            ),
        )
        assertFalse(
            shouldRetryPostMealWork(
                runAttemptCount = 0,
                nowEpochMillis = 2_000,
                expiresAtEpochMillis = 2_000,
            ),
        )
    }
}
