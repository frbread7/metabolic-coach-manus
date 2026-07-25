package com.young.metaboliccoach.core.data.provider

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HealthConnectGlucoseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GlucoseProvider {
    override val id = PROVIDER_ID

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val readPermission = HealthPermission.getReadPermission(BloodGlucoseRecord::class)

    override fun handlesSource(sourceId: String): Boolean =
        sourceId.startsWith("$PROVIDER_ID:")

    override suspend fun status(): ProviderStatus {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            return ProviderStatus(
                providerId = id,
                displayName = "Health Connect glucose",
                availability = ProviderAvailability.APP_NOT_INSTALLED,
                detail = "Health Connect is unavailable on this phone.",
            )
        }
        val granted = client.permissionController.getGrantedPermissions()
        return if (readPermission in granted) {
            ProviderStatus(
                providerId = id,
                displayName = "Health Connect glucose",
                availability = ProviderAvailability.AVAILABLE,
                detail = "Ready to read glucose records shared with Health Connect.",
            )
        } else {
            ProviderStatus(
                providerId = id,
                displayName = "Health Connect glucose",
                availability = ProviderAvailability.PERMISSION_REQUIRED,
                detail = "Grant read access to blood glucose records.",
            )
        }
    }

    override suspend fun readSince(startEpochMillis: Long): List<GlucoseReading> {
        if (status().availability != ProviderAvailability.AVAILABLE) return emptyList()
        val timeRange = TimeRangeFilter.between(
            Instant.ofEpochMilli(startEpochMillis),
            Instant.now(),
        )
        val records = buildList {
            var pageToken: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = BloodGlucoseRecord::class,
                        timeRangeFilter = timeRange,
                        ascendingOrder = true,
                        pageSize = PAGE_SIZE,
                        pageToken = pageToken,
                    ),
                )
                addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)
        }.filter {
            it.level.inMilligramsPerDeciliter in
                MIN_GLUCOSE_MG_DL..MAX_GLUCOSE_MG_DL
        }.sortedBy { it.time }
        val now = System.currentTimeMillis()
        return records
            .groupBy { it.metadata.dataOrigin.packageName }
            .flatMap { (originPackage, originRecords) ->
                originRecords.map { record ->
                    GlucoseReading(
                        id = "$originPackage:${record.metadata.id}",
                        valueMgDl = record.level.inMilligramsPerDeciliter.roundToInt(),
                        trend = GlucoseTrend.UNKNOWN,
                        deltaMgDl = null,
                        rateMgDlPerMinute = null,
                        measuredAtEpochMillis = record.time.toEpochMilli(),
                        receivedAtEpochMillis = now,
                        sourceId = "$PROVIDER_ID:$originPackage",
                    )
                }.withCalculatedTrends()
            }
            .sortedWith(
                compareBy<GlucoseReading> { it.measuredAtEpochMillis }
                    .thenBy(GlucoseReading::id),
            )
    }

    override suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading> =
        readSince(startEpochMillis).filter { it.sourceId == sourceId }

    override suspend fun clearRuntimeCache() = Unit

    private fun List<GlucoseReading>.withCalculatedTrends(): List<GlucoseReading> =
        mapIndexed { index, reading ->
            val previous = getOrNull(index - 1) ?: return@mapIndexed reading
            val elapsedMinutes =
                (reading.measuredAtEpochMillis - previous.measuredAtEpochMillis) / 60_000.0
            if (elapsedMinutes <= 0.0) return@mapIndexed reading
            val delta = reading.valueMgDl - previous.valueMgDl
            val rate = delta / elapsedMinutes
            if (rate !in -MAX_ABS_RATE_MG_DL_PER_MINUTE..MAX_ABS_RATE_MG_DL_PER_MINUTE) {
                return@mapIndexed reading
            }
            reading.copy(
                deltaMgDl = delta,
                rateMgDlPerMinute = rate,
                trend = rate.toTrend(),
            )
        }

    private fun Double.toTrend(): GlucoseTrend = when {
        this <= -3.0 -> GlucoseTrend.RAPIDLY_FALLING
        this <= -2.0 -> GlucoseTrend.FALLING
        this <= -0.5 -> GlucoseTrend.SLIGHTLY_FALLING
        this < 0.5 -> GlucoseTrend.STABLE
        this < 2.0 -> GlucoseTrend.SLIGHTLY_RISING
        this < 3.0 -> GlucoseTrend.RISING
        else -> GlucoseTrend.RAPIDLY_RISING
    }

    companion object {
        const val PROVIDER_ID = "health_connect_glucose"
        private const val PAGE_SIZE = 1_000
        private const val MIN_GLUCOSE_MG_DL = 20.0
        private const val MAX_GLUCOSE_MG_DL = 600.0
        private const val MAX_ABS_RATE_MG_DL_PER_MINUTE = 20.0
    }
}
