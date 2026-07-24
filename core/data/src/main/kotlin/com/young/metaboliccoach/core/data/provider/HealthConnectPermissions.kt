package com.young.metaboliccoach.core.data.provider

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

object HealthConnectPermissions {
    val foregroundReadPermissions = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    val backgroundReadPermission =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /**
     * Returns only permissions supported by the installed Health Connect provider.
     *
     * Background reads were added independently of the record permissions. Requesting that
     * permission on a provider that does not advertise the feature is invalid, so the app
     * degrades to foreground/manual refresh instead.
     */
    fun requestableReadPermissions(context: Context): Set<String> {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return emptySet()
        }
        val client = HealthConnectClient.getOrCreate(context)
        val supportsBackgroundReads = runCatching {
            client.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        return if (supportsBackgroundReads) {
            foregroundReadPermissions + backgroundReadPermission
        } else {
            foregroundReadPermissions
        }
    }

    suspend fun hasBackgroundReadAccess(context: Context): Boolean {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return false
        }
        val client = HealthConnectClient.getOrCreate(context)
        val supportsBackgroundReads = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return supportsBackgroundReads &&
            backgroundReadPermission in client.permissionController.getGrantedPermissions()
    }
}
