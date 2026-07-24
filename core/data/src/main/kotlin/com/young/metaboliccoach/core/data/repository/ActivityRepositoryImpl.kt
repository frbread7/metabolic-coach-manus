package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.ActivityDao
import com.young.metaboliccoach.core.data.db.ActivitySnapshotEntity
import com.young.metaboliccoach.core.data.db.toModel
import com.young.metaboliccoach.core.data.provider.HealthConnectActivityDataSource
import com.young.metaboliccoach.core.data.provider.SamsungHealthPartnerDataProvider
import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.model.ActivitySnapshot
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val activityDao: ActivityDao,
    healthConnect: HealthConnectActivityDataSource,
    samsungHealthPartner: SamsungHealthPartnerDataProvider,
) : ActivityRepository {
    private val providers = listOf(healthConnect, samsungHealthPartner)

    override fun observeToday(): Flow<ActivitySnapshot?> =
        activityDao.observeLatest().map { entity ->
            entity
                ?.takeIf { it.dayStartEpochMillis == startOfToday(System.currentTimeMillis()) }
                ?.toModel()
        }

    override suspend fun refresh() {
        val snapshot = providers.firstNotNullOfOrNull { provider ->
            try {
                provider.readToday()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
        } ?: return
        val dayStart = Instant.ofEpochMilli(snapshot.measuredAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        activityDao.upsert(
            ActivitySnapshotEntity(
                dayStartEpochMillis = dayStart,
                stepsToday = snapshot.stepsToday,
                floorsToday = snapshot.floorsToday,
                latestHeartRateBpm = snapshot.latestHeartRateBpm,
                activeCaloriesToday = snapshot.activeCaloriesToday,
                lastMovementAtEpochMillis = snapshot.lastMovementAtEpochMillis,
                measuredAtEpochMillis = snapshot.measuredAtEpochMillis,
                sourceId = snapshot.sourceId,
                exerciseSessionCountToday = snapshot.exerciseSessionCountToday,
                exerciseDurationMinutesToday = snapshot.exerciseDurationMinutesToday,
            ),
        )
    }

    private fun startOfToday(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
