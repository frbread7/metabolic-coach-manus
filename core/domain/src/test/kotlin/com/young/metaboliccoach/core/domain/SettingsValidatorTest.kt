package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.DefaultCoachSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {
    @Test
    fun `defaults are valid`() {
        assertTrue(SettingsValidator().validate(DefaultCoachSettings.create()).isEmpty())
    }

    @Test
    fun `rejects unsafe lifecycle timing settings`() {
        val invalid = DefaultCoachSettings.create().copy(
            interventionFollowUpMinutes = 0,
            quickActionExpiryMinutes = 4,
        )

        assertEquals(2, SettingsValidator().validate(invalid).size)
    }

    @Test
    fun `rejects out of range personal observation analysis settings`() {
        val invalid = DefaultCoachSettings.create().copy(
            minimumTimingBucketSamples = 1,
            minimumComparableTimingBuckets = 1,
            interventionTimingBucketMinutes = 0,
            postMealTimingBucketMinutes = 0,
            followUpDelayBucketMinutes = 0,
            baselineGlucoseBandMgDl = 4,
        )

        assertEquals(6, SettingsValidator().validate(invalid).size)
    }

    @Test
    fun `rejects out of range daily activity goals`() {
        val invalid = DefaultCoachSettings.create().copy(
            dailyStepGoal = 999,
            dailyFloorGoal = 501,
        )

        assertEquals(2, SettingsValidator().validate(invalid).size)
    }

    @Test
    fun `rejects invalid falling glucose pause rate`() {
        val invalid = DefaultCoachSettings.create().copy(
            exercisePauseFallRateMgDlPerMinute = 0.4,
        )

        assertTrue(
            SettingsValidator().validate(invalid)
                .any { it.contains("fall-rate", ignoreCase = true) },
        )
    }

    @Test
    fun `rejects target glucose bounds outside supported range`() {
        val invalid = DefaultCoachSettings.create().copy(
            targetLowerMgDl = 20,
            targetUpperMgDl = 600,
        )

        assertEquals(2, SettingsValidator().validate(invalid).size)
    }

    @Test
    fun `accepts a persisted Health Connect origin package`() {
        val configured = DefaultCoachSettings.create().copy(
            healthConnectGlucoseOriginPackage = "com.example.cgm",
        )

        assertTrue(SettingsValidator().validate(configured).isEmpty())
    }

    @Test
    fun `rejects invalid Health Connect origin delimiters`() {
        val invalid = DefaultCoachSettings.create().copy(
            healthConnectGlucoseOriginPackage = "com.example:remote",
        )

        assertTrue(
            SettingsValidator().validate(invalid)
                .any { it.contains("source package", ignoreCase = true) },
        )
    }
}
