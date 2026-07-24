package com.young.metaboliccoach.core.data.repository

import androidx.datastore.preferences.core.emptyPreferences
import com.young.metaboliccoach.core.data.BuildConfig
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseGlucoseProviderModePolicyTest {
    @Test
    fun `release build replaces persisted xdrip mode with health connect`() {
        assertFalse(BuildConfig.DEBUG)
        assertEquals(
            GlucoseProviderMode.HEALTH_CONNECT,
            GlucoseProviderMode.XDRIP_BROADCAST.supportedForCurrentBuild(),
        )
    }

    @Test
    fun `release build preserves supported provider modes`() {
        assertEquals(
            GlucoseProviderMode.HEALTH_CONNECT,
            GlucoseProviderMode.HEALTH_CONNECT.supportedForCurrentBuild(),
        )
        assertEquals(
            GlucoseProviderMode.CARESENS_PARTNER,
            GlucoseProviderMode.CARESENS_PARTNER.supportedForCurrentBuild(),
        )
    }

    @Test
    fun `release migration rewrites a persisted debug xdrip selection`() = runTest {
        val original = emptyPreferences().toMutablePreferences().apply {
            this[glucoseProviderModePreferenceKey] =
                GlucoseProviderMode.XDRIP_BROADCAST.name
        }
        val migration = GlucoseProviderModeMigration()

        assertTrue(migration.shouldMigrate(original))
        assertEquals(
            GlucoseProviderMode.HEALTH_CONNECT.name,
            migration.migrate(original)[glucoseProviderModePreferenceKey],
        )
    }
}
