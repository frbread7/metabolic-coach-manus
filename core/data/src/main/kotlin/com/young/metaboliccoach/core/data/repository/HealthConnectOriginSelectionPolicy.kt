package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.provider.HealthConnectGlucoseProvider
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading

internal data class HealthConnectOriginSelection(
    val availableOrigins: List<GlucoseDataOrigin>,
    val selectedPackageName: String?,
    val autoSelectedPackageName: String?,
    val selectedReadings: List<GlucoseReading>,
    val requiresUserSelection: Boolean,
)

internal object HealthConnectOriginSelectionPolicy {
    fun select(
        readings: List<GlucoseReading>,
        configuredPackageName: String?,
    ): HealthConnectOriginSelection {
        val readingsByPackage = readings
            .mapNotNull { reading ->
                reading.healthConnectOriginPackage()?.let { it to reading }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
        val availableOrigins = readingsByPackage
            .map { (packageName, originReadings) ->
                GlucoseDataOrigin(
                    packageName = packageName,
                    latestReadingAtEpochMillis =
                        originReadings.maxOf(GlucoseReading::measuredAtEpochMillis),
                )
            }
            .sortedBy(GlucoseDataOrigin::packageName)
        val normalizedConfiguration = configuredPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val autoSelectedPackageName = availableOrigins
            .singleOrNull()
            ?.packageName
            ?.takeIf { normalizedConfiguration == null }
        val selectedPackageName = normalizedConfiguration ?: autoSelectedPackageName
        return HealthConnectOriginSelection(
            availableOrigins = availableOrigins,
            selectedPackageName = selectedPackageName,
            autoSelectedPackageName = autoSelectedPackageName,
            selectedReadings = selectedPackageName
                ?.let { readingsByPackage[it].orEmpty() }
                .orEmpty()
                .sortedWith(
                    compareBy<GlucoseReading> { it.measuredAtEpochMillis }
                        .thenBy(GlucoseReading::id),
                ),
            requiresUserSelection =
                normalizedConfiguration == null && availableOrigins.size > 1,
        )
    }
}

internal fun GlucoseReading.healthConnectOriginPackage(): String? {
    val prefix = "${HealthConnectGlucoseProvider.PROVIDER_ID}:"
    return sourceId
        .takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.takeIf(String::isNotBlank)
}
