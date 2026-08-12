package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.ActivityDao
import com.young.metaboliccoach.core.data.db.ActivitySnapshotEntity
import com.young.metaboliccoach.core.data.db.CoachStateDao
import com.young.metaboliccoach.core.data.db.CoachStateEntity
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.InterventionDao
import com.young.metaboliccoach.core.data.db.MealDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotEntity
import com.young.metaboliccoach.core.domain.CoachRuleEngine
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.ObservationAnalyzer
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.ProviderAvailability
import com.young.metaboliccoach.core.model.ProviderStatus
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.InterventionType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RecommendationSnapshotPersistenceTest {
    @Test
    fun `retry with the same id returns the original authoritative snapshot`() = runTest {
        val snapshots = InMemoryRecommendationSnapshotDao()
        val repository = CoachingRepositoryImpl(
            glucoseDao = mock(GlucoseDao::class.java),
            activityDao = mock(ActivityDao::class.java),
            interventionDao = mock(InterventionDao::class.java),
            mealDao = mock(MealDao::class.java),
            coachStateDao = mock(CoachStateDao::class.java),
            recommendationSnapshotDao = snapshots,
            settingsRepository = mock(SettingsRepository::class.java),
            glucoseRepository = mock(GlucoseRepository::class.java),
            ruleEngine = CoachRuleEngine(),
            observationAnalyzer = ObservationAnalyzer(),
            timeSource = mock(CoachTimeSource::class.java),
        )
        val original = recommendation(createdAtEpochMillis = 1_000)
        val retry = original.copy(
            createdAtEpochMillis = 2_000,
            validUntilEpochMillis = 62_000,
            durationMinutes = 12,
        )

        assertEquals(original, repository.rememberRecommendation(original))
        assertEquals(original, repository.rememberRecommendation(retry))
        assertEquals(original, repository.recommendationSnapshot(original.id))
    }

    @Test
    fun `expired immutable snapshot cannot be published again after snooze`() = runTest {
        val snapshots = InMemoryRecommendationSnapshotDao()
        val repository = CoachingRepositoryImpl(
            glucoseDao = mock(GlucoseDao::class.java),
            activityDao = mock(ActivityDao::class.java),
            interventionDao = mock(InterventionDao::class.java),
            mealDao = mock(MealDao::class.java),
            coachStateDao = mock(CoachStateDao::class.java),
            recommendationSnapshotDao = snapshots,
            settingsRepository = mock(SettingsRepository::class.java),
            glucoseRepository = mock(GlucoseRepository::class.java),
            ruleEngine = CoachRuleEngine(),
            observationAnalyzer = ObservationAnalyzer(),
            timeSource = mock(CoachTimeSource::class.java),
        )
        val original = recommendation(createdAtEpochMillis = 1_000)
        repository.rememberRecommendation(original)

        assertEquals(
            false,
            repository.recordRecommendationPublished(
                original.id,
                original.validUntilEpochMillis,
            ),
        )
    }

    @Test
    fun `publishing a new recommendation clears an old recommendations snooze`() = runTest {
        val snapshots = InMemoryRecommendationSnapshotDao()
        val states = InMemoryCoachStateDao(
            CoachStateEntity(
                lastRecommendationAtEpochMillis = 500,
                lastRecommendationId = "old-recommendation",
                snoozedUntilEpochMillis = 900,
                notificationDayStartEpochMillis = 0,
                notificationsSentToday = 1,
                deliveryCountForLastRecommendation = 1,
            ),
        )
        val repository = CoachingRepositoryImpl(
            glucoseDao = mock(GlucoseDao::class.java),
            activityDao = mock(ActivityDao::class.java),
            interventionDao = mock(InterventionDao::class.java),
            mealDao = mock(MealDao::class.java),
            coachStateDao = states,
            recommendationSnapshotDao = snapshots,
            settingsRepository = mock(SettingsRepository::class.java),
            glucoseRepository = mock(GlucoseRepository::class.java),
            ruleEngine = CoachRuleEngine(),
            observationAnalyzer = ObservationAnalyzer(),
            timeSource = mock(CoachTimeSource::class.java),
        )
        val next = recommendation(createdAtEpochMillis = 1_000).copy(id = "next")
        repository.rememberRecommendation(next)

        assertEquals(true, repository.recordRecommendationPublished(next.id, 1_001))
        assertEquals(null, states.get()?.snoozedUntilEpochMillis)
        assertEquals(false, repository.recordRecommendationPublished(next.id, 1_002))
    }

    @Test
    fun `published rapid recommendation remains authoritative during cooldown`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW)
        val fixture = coachingFixture(listOf(older, latest))

        val generated = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        assertEquals(CoachReason.RAPID_GLUCOSE_RISE, generated.reason)
        fixture.repository.rememberRecommendation(generated)
        assertEquals(
            true,
            fixture.repository.recordRecommendationPublished(generated.id, NOW),
        )

        assertEquals(
            generated,
            fixture.repository.observeCurrentRecommendation().first(),
        )
        assertEquals(
            false,
            fixture.repository.recordRecommendationPublished(generated.id, NOW + 1),
        )
    }

    @Test
    fun `daily cap retains visible prompt but blocks expired-snooze redelivery`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW)
        val settings = DefaultCoachSettings.create().copy(
            maximumNotificationsPerDay = 1,
            snoozeMinutes = 1,
            staleReadingMinutes = 30,
        )
        val fixture = coachingFixture(listOf(older, latest), settings)
        val generated = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        fixture.repository.rememberRecommendation(generated)
        assertEquals(
            true,
            fixture.repository.recordRecommendationPublished(generated.id, NOW),
        )

        assertEquals(
            generated,
            fixture.repository.observeCurrentRecommendation().first(),
        )

        fixture.repository.snooze(NOW)
        fixture.time.now = NOW + 60_000L

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `newer nonqualifying reading invalidates retained rapid recommendation`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW)
        val fixture = coachingFixture(listOf(older, latest))
        val generated = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        fixture.repository.rememberRecommendation(generated)
        fixture.repository.recordRecommendationPublished(generated.id, NOW)

        fixture.glucose.setReadings(
            listOf(
                older,
                latest,
                latest.copy(
                    id = "stable-latest",
                    measuredAtEpochMillis = NOW + 5 * 60_000L,
                    receivedAtEpochMillis = NOW + 5 * 60_000L,
                    rateMgDlPerMinute = 0.0,
                    trend = GlucoseTrend.STABLE,
                ),
            ),
        )
        fixture.time.now = NOW + 5 * 60_000L

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `new confirmed pair cannot revive old rapid snapshot during cooldown`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW)
        val fixture = coachingFixture(listOf(older, latest))
        val generated = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        fixture.repository.rememberRecommendation(generated)
        fixture.repository.recordRecommendationPublished(generated.id, NOW)

        fixture.glucose.setReadings(
            listOf(
                older,
                latest,
                risingReading("new-latest", NOW + 5 * 60_000L),
            ),
        )
        fixture.time.now = NOW + 5 * 60_000L

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `source switch invalidates retained rapid recommendation`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L)
        val latest = risingReading("latest", NOW)
        val fixture = coachingFixture(listOf(older, latest))
        val generated = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        fixture.repository.rememberRecommendation(generated)
        fixture.repository.recordRecommendationPublished(generated.id, NOW)

        fixture.glucose.setReadings(
            listOf(
                risingReading("other-older", NOW, "nightscout:server-b"),
                risingReading("other-latest", NOW + 5 * 60_000L, "nightscout:server-b"),
            ),
        )
        fixture.time.now = NOW + 5 * 60_000L

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `production repository does not emit inactivity or stair actions`() = runTest {
        val older = risingReading("older", NOW - 5 * 60_000L).copy(
            rateMgDlPerMinute = 0.0,
            trend = GlucoseTrend.STABLE,
        )
        val latest = risingReading("latest", NOW).copy(
            rateMgDlPerMinute = 0.0,
            trend = GlucoseTrend.STABLE,
        )
        val dayStart = Instant.ofEpochMilli(NOW)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val activity = ActivitySnapshotEntity(
            dayStartEpochMillis = dayStart,
            stepsToday = 0,
            floorsToday = 0.0,
            latestHeartRateBpm = null,
            activeCaloriesToday = null,
            lastMovementAtEpochMillis = NOW - 4 * 60 * 60_000L,
            measuredAtEpochMillis = NOW,
            sourceId = "health-connect",
            exerciseSessionCountToday = 0,
            exerciseDurationMinutesToday = 0,
        )
        val fixture = coachingFixture(
            readings = listOf(older, latest),
            activity = activity,
        )

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    private fun coachingFixture(
        readings: List<GlucoseReading>,
        settings: CoachSettings = DefaultCoachSettings.create(),
        activity: ActivitySnapshotEntity? = null,
    ): CoachingFixture {
        val activityDao = mock(ActivityDao::class.java)
        val interventionDao = mock(InterventionDao::class.java)
        val mealDao = mock(MealDao::class.java)
        `when`(activityDao.observeLatest()).thenReturn(flowOf(activity))
        `when`(mealDao.observeLatest()).thenReturn(flowOf(null))
        val states = InMemoryCoachStateDao(null)
        val snapshots = InMemoryRecommendationSnapshotDao()
        val glucose = FakeGlucoseRepository(readings)
        val time = FakeTimeSource(NOW)
        return CoachingFixture(
            repository = CoachingRepositoryImpl(
                glucoseDao = mock(GlucoseDao::class.java),
                activityDao = activityDao,
                interventionDao = interventionDao,
                mealDao = mealDao,
                coachStateDao = states,
                recommendationSnapshotDao = snapshots,
                settingsRepository = FakeSettingsRepository(settings),
                glucoseRepository = glucose,
                ruleEngine = CoachRuleEngine(),
                observationAnalyzer = ObservationAnalyzer(),
                timeSource = time,
            ),
            glucose = glucose,
            time = time,
        )
    }

    private fun risingReading(
        id: String,
        measuredAtEpochMillis: Long,
        sourceId: String = "nightscout:server-a",
    ) = GlucoseReading(
        id = id,
        valueMgDl = 140,
        trend = GlucoseTrend.RAPIDLY_RISING,
        deltaMgDl = 10,
        rateMgDlPerMinute = 3.0,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = sourceId,
    )

    private fun recommendation(createdAtEpochMillis: Long) = CoachRecommendation.Action(
        reason = CoachReason.RAPID_GLUCOSE_RISE,
        id = "rapid-rise:reading-1",
        createdAtEpochMillis = createdAtEpochMillis,
        validUntilEpochMillis = createdAtEpochMillis + 60_000,
        interventionType = InterventionType.WALK,
        title = "Glucose is rising. Walk now?",
        actionLabel = "START WALK",
        durationMinutes = 10,
        targetFloors = null,
        algorithmVersion = 1,
        triggerContextId = "reading-1",
        triggerAtEpochMillis = 500,
    )

    private class InMemoryRecommendationSnapshotDao : RecommendationSnapshotDao {
        private val snapshots = linkedMapOf<String, RecommendationSnapshotEntity>()

        override suspend fun getById(
            recommendationId: String,
        ): RecommendationSnapshotEntity? = snapshots[recommendationId]

        override suspend fun insertIfAbsent(snapshot: RecommendationSnapshotEntity): Long =
            if (snapshots.putIfAbsent(snapshot.id, snapshot) == null) 1L else -1L

        override suspend fun deleteExpiredBefore(cutoffEpochMillis: Long) {
            snapshots.entries.removeAll {
                it.value.validUntilEpochMillis < cutoffEpochMillis
            }
        }

        override suspend fun deleteAll() {
            snapshots.clear()
        }
    }

    private class InMemoryCoachStateDao(
        initial: CoachStateEntity?,
    ) : CoachStateDao {
        private val state = MutableStateFlow(initial)

        override fun observe(): Flow<CoachStateEntity?> = state

        override suspend fun get(): CoachStateEntity? = state.value

        override suspend fun upsert(state: CoachStateEntity) {
            this.state.value = state
        }
    }

    private class FakeSettingsRepository(
        private val settings: CoachSettings = DefaultCoachSettings.create(),
    ) : SettingsRepository {
        override fun observe(): Flow<CoachSettings> = flowOf(settings)
        override suspend fun update(settings: CoachSettings) = Unit
        override suspend fun reset() = Unit
    }

    private class FakeGlucoseRepository(
        readings: List<GlucoseReading>,
    ) : GlucoseRepository {
        private val readings = MutableStateFlow(readings)

        fun setReadings(value: List<GlucoseReading>) {
            readings.value = value
        }

        override fun observeLatest(): Flow<GlucoseReading?> = readings.map { values ->
            values.maxWithOrNull(
                compareBy<GlucoseReading> { it.measuredAtEpochMillis }
                    .thenByDescending { it.id },
            )
        }

        override fun observeProviderStatus(): Flow<ProviderStatus> = flowOf(
            ProviderStatus("fake", "Fake", ProviderAvailability.AVAILABLE, "Ready"),
        )

        override fun observeAvailableOrigins(): Flow<List<GlucoseDataOrigin>> =
            flowOf(emptyList())

        override suspend fun readingsBetween(
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReading> = readings.value.filter {
            it.measuredAtEpochMillis in startEpochMillis..endEpochMillis
        }

        override suspend fun readingsBetweenExactSource(
            sourceId: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReading> = readingsBetween(startEpochMillis, endEpochMillis)
            .filter { it.sourceId == sourceId }

        override suspend fun refresh() = Unit
        override suspend fun refreshExactSource(sourceId: String) = Unit
        override suspend fun clearRuntimeCaches() = Unit
    }

    private class FakeTimeSource(var now: Long) : CoachTimeSource {
        override fun nowEpochMillis(): Long = now
        override fun minuteTicks(): Flow<Long> = flowOf(now)
    }

    private data class CoachingFixture(
        val repository: CoachingRepositoryImpl,
        val glucose: FakeGlucoseRepository,
        val time: FakeTimeSource,
    )

    private companion object {
        const val NOW = 43_200_000L
    }
}
