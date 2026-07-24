package com.young.metaboliccoach.core.data.provider

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectActivityDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ActivityDataProvider {
    override val id = PROVIDER_ID

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val floorsPermission = HealthPermission.getReadPermission(FloorsClimbedRecord::class)
    private val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val exercisePermission =
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val activeCaloriesPermission =
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val essentialPermissions = setOf(stepsPermission, floorsPermission)
    private val allActivityPermissions = setOf(
        stepsPermission,
        floorsPermission,
        heartRatePermission,
        exercisePermission,
        activeCaloriesPermission,
    )

    override suspend fun status(): ProviderStatus {
        val sdkAvailable =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        if (!sdkAvailable) {
            return ProviderStatus(
                providerId = id,
                displayName = "Health Connect activity",
                availability = ProviderAvailability.APP_NOT_INSTALLED,
                detail = "Health Connect is unavailable on this phone.",
            )
        }
        val granted = client.permissionController.getGrantedPermissions()
        val permitted = granted.containsAll(essentialPermissions)
        val backgroundGranted = HealthConnectPermissions.backgroundReadPermission in granted
        val optionalGranted =
            granted.intersect(allActivityPermissions - essentialPermissions).size
        return ProviderStatus(
            providerId = id,
            displayName = "Health Connect activity",
            availability = if (permitted) {
                ProviderAvailability.AVAILABLE
            } else {
                ProviderAvailability.PERMISSION_REQUIRED
            },
            detail = if (permitted) {
                if (backgroundGranted) {
                    "Ready for background reads; $optionalGranted of 3 optional activity types granted."
                } else {
                    "Ready for manual reads; $optionalGranted of 3 optional activity types granted."
                }
            } else {
                "Grant steps and floors permissions; other activity permissions are optional."
            },
        )
    }

    override suspend fun readToday(now: Instant): ActivitySnapshot? {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return null
        }
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(essentialPermissions)) return null
        val zoneId = ZoneId.systemDefault()
        val start = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
        val filter = TimeRangeFilter.between(start, now)
        val aggregateMetrics = buildSet {
            add(StepsRecord.COUNT_TOTAL)
            add(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
            if (activeCaloriesPermission in granted) {
                add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
        }
        val aggregate = client.aggregate(
            AggregateRequest(
                metrics = aggregateMetrics,
                timeRangeFilter = filter,
            ),
        )
        val heartRates = if (heartRatePermission in granted) {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = filter,
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            ).records
        } else {
            emptyList()
        }
        val steps = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = filter,
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records
        val exercises = if (exercisePermission in granted) {
            readExerciseSessions(filter)
        } else {
            emptyList()
        }
        val exerciseSummary = summarizeExerciseSessions(
            exercises.map {
                ExerciseWindow(
                    startEpochMillis = it.startTime.toEpochMilli(),
                    endEpochMillis = it.endTime.toEpochMilli(),
                )
            },
        )

        return aggregate.toSnapshot(
            latestHeartRate = heartRates.firstOrNull()
                ?.samples
                ?.maxByOrNull { it.time }
                ?.beatsPerMinute,
            lastMovementAt = listOfNotNull(
                steps.firstOrNull()?.endTime,
                exerciseSummary.latestEndEpochMillis?.let(Instant::ofEpochMilli),
            ).maxOrNull()?.toEpochMilli(),
            exerciseSummary = exerciseSummary,
            now = now,
        )
    }

    private suspend fun readExerciseSessions(
        filter: TimeRangeFilter,
    ): List<ExerciseSessionRecord> = buildList {
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = filter,
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
    }

    private fun AggregationResult.toSnapshot(
        latestHeartRate: Long?,
        lastMovementAt: Long?,
        exerciseSummary: ExerciseDaySummary,
        now: Instant,
    ) = ActivitySnapshot(
        stepsToday = this[StepsRecord.COUNT_TOTAL] ?: 0,
        floorsToday = this[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0,
        latestHeartRateBpm = latestHeartRate,
        activeCaloriesToday =
            this[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
        lastMovementAtEpochMillis = lastMovementAt,
        measuredAtEpochMillis = now.toEpochMilli(),
        sourceId = PROVIDER_ID,
        exerciseSessionCountToday = exerciseSummary.sessionCount,
        exerciseDurationMinutesToday = exerciseSummary.durationMinutes,
    )

    companion object {
        const val PROVIDER_ID = "health_connect_activity"
        private const val PAGE_SIZE = 1_000
    }
}

internal data class ExerciseWindow(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

internal data class ExerciseDaySummary(
    val sessionCount: Int,
    val durationMinutes: Long,
    val latestEndEpochMillis: Long?,
)

internal fun summarizeExerciseSessions(windows: List<ExerciseWindow>): ExerciseDaySummary {
    val valid = windows.filter { it.endEpochMillis >= it.startEpochMillis }
    return ExerciseDaySummary(
        sessionCount = valid.size,
        durationMinutes = valid.sumOf {
            (it.endEpochMillis - it.startEpochMillis) / 60_000L
        },
        latestEndEpochMillis = valid.maxOfOrNull(ExerciseWindow::endEpochMillis),
    )
}
