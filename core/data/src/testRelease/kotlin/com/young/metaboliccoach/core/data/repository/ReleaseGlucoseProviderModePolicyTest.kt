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
    fun `release build routes every legacy glucose mode to Nightscout`() {
        assertFalse(BuildConfig.DEBUG)
        GlucoseProviderMode.entries
            .filterNot { it == GlucoseProviderMode.NIGHTSCOUT }
            .forEach { legacyMode ->
                assertEquals(
                    "Legacy mode $legacyMode",
                    GlucoseProviderMode.NIGHTSCOUT,
                    legacyMode.supportedForCurrentBuild(),
                )
            }
    }

    @Test
    fun `release build preserves Nightscout mode`() {
        assertEquals(
            GlucoseProviderMode.NIGHTSCOUT,
            GlucoseProviderMode.NIGHTSCOUT.supportedForCurrentBuild(),
        )
    }

    @Test
    fun `release migration rewrites every persisted legacy selection`() = runTest {
        val migration = GlucoseProviderModeMigration()

        GlucoseProviderMode.entries
            .filterNot { it == GlucoseProviderMode.NIGHTSCOUT }
            .forEach { legacyMode ->
                val original = emptyPreferences().toMutablePreferences().apply {
                    this[glucoseProviderModePreferenceKey] = legacyMode.name
                }

                assertTrue("Legacy mode $legacyMode", migration.shouldMigrate(original))
                assertEquals(
                    GlucoseProviderMode.NIGHTSCOUT.name,
                    migration.migrate(original)[glucoseProviderModePreferenceKey],
                )
            }
    }

    @Test
    fun `release migration leaves absent and Nightscout selections alone`() = runTest {
        val absent = emptyPreferences()
        val selected = emptyPreferences().toMutablePreferences().apply {
            this[glucoseProviderModePreferenceKey] = GlucoseProviderMode.NIGHTSCOUT.name
        }
        val migration = GlucoseProviderModeMigration()

        assertFalse(migration.shouldMigrate(absent))
        assertFalse(migration.shouldMigrate(selected))
    }
}
