package com.young.metaboliccoach.core.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.young.metaboliccoach.core.model.GlucoseProviderMode

internal val glucoseProviderModePreferenceKey =
    stringPreferencesKey("glucose_provider_mode")

internal fun GlucoseProviderMode.supportedForCurrentBuild(): GlucoseProviderMode =
    GlucoseProviderMode.NIGHTSCOUT

internal class GlucoseProviderModeMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[glucoseProviderModePreferenceKey]?.let {
            it != GlucoseProviderMode.NIGHTSCOUT.name
        } == true

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[glucoseProviderModePreferenceKey] =
                GlucoseProviderMode.NIGHTSCOUT.name
        }

    override suspend fun cleanUp() = Unit
}
