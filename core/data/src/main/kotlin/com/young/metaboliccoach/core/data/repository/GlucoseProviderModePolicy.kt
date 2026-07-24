package com.young.metaboliccoach.core.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.young.metaboliccoach.core.data.BuildConfig
import com.young.metaboliccoach.core.model.GlucoseProviderMode

internal val glucoseProviderModePreferenceKey =
    stringPreferencesKey("glucose_provider_mode")

internal fun GlucoseProviderMode.supportedForCurrentBuild(
    unofficialXdripEnabled: Boolean = BuildConfig.DEBUG,
): GlucoseProviderMode = if (
    this == GlucoseProviderMode.XDRIP_BROADCAST &&
    !unofficialXdripEnabled
) {
    GlucoseProviderMode.HEALTH_CONNECT
} else {
    this
}

internal class GlucoseProviderModeMigration(
    private val unofficialXdripEnabled: Boolean = BuildConfig.DEBUG,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[glucoseProviderModePreferenceKey] ==
        GlucoseProviderMode.XDRIP_BROADCAST.name &&
            !unofficialXdripEnabled

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[glucoseProviderModePreferenceKey] =
                GlucoseProviderMode.HEALTH_CONNECT.name
        }

    override suspend fun cleanUp() = Unit
}
