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
import com.young.metaboliccoach.core.data.db.toEntity
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
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
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
    fun `production repository emits inactivity walk even when stairs are enabled`() = runTest {
        val fixture = coachingFixture(
            readings = stableReadings(),
            activity = inactiveActivity(),
        )

        val recommendation = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action

        assertEquals(CoachReason.PROLONGED_INACTIVITY, recommendation.reason)
        assertEquals(InterventionType.WALK, recommendation.interventionType)
        assertNull(recommendation.targetFloors)
        assertEquals(4, recommendation.algorithmVersion)
    }

    @Test
    fun `production repository does not emit inactivity when walking is disabled`() = runTest {
        val fixture = coachingFixture(
            readings = stableReadings(),
            settings = DefaultCoachSettings.create().copy(
                walkingRemindersEnabled = false,
                stairRemindersEnabled = true,
            ),
            activity = inactiveActivity(),
        )

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `production repository does not emit inactivity from stale activity`() = runTest {
        val settings = DefaultCoachSettings.create()
        val fixture = coachingFixture(
            readings = stableReadings(),
            settings = settings,
            activity = inactiveActivity().copy(
                measuredAtEpochMillis = NOW - settings.staleReadingMinutes * 60_000L,
            ),
        )

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `safe glucose refresh retains immutable inactivity snapshot and id`() = runTest {
        val fixture = coachingFixture(
            readings = stableReadings(),
            activity = inactiveActivity(),
        )
        val original = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        fixture.repository.rememberRecommendation(original)
        fixture.repository.recordRecommendationPublished(original.id, NOW)

        fixture.glucose.setReadings(
            stableReadings() + stableReading("safe-refresh", NOW + 60_000L),
        )
        fixture.activity.value = inactiveActivity().copy(
            stepsToday = inactiveActivity().stepsToday + 100,
            measuredAtEpochMillis = NOW + 60_000L,
        )
        fixture.time.now = NOW + 60_000L

        val retained = fixture.repository.observeCurrentRecommendation().first()
        assertEquals(original.id, (retained as CoachRecommendation.Action).id)
        assertEquals(original, retained)
        assertEquals(original.validUntilEpochMillis, retained.validUntilEpochMillis)
    }

    @Test
    fun `inactivity snapshot is not UI-authoritative until publication is recorded`() = runTest {
        val fixture = coachingFixture(
            readings = stableReadings(),
            activity = inactiveActivity(),
        )
        val recommendation = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action

        fixture.repository.rememberRecommendation(recommendation)
        assertNull(
            fixture.repository.publishedRecommendationSnapshot(
                recommendationId = recommendation.id,
                nowEpochMillis = NOW,
            ),
        )

        assertEquals(
            true,
            fixture.repository.recordRecommendationPublished(recommendation.id, NOW),
        )
        assertEquals(
            recommendation,
            fixture.repository.publishedRecommendationSnapshot(
                recommendationId = recommendation.id,
                nowEpochMillis = NOW,
            ),
        )
    }

    @Test
    fun `legacy v2 stair snapshot cannot survive a current v4 candidate`() = runTest {
        val fixture = coachingFixture(
            readings = stableReadings(),
            activity = inactiveActivity(),
        )
        val current = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        val legacy = current.copy(
            validUntilEpochMillis = current.validUntilEpochMillis + 60_000L,
            interventionType = InterventionType.STAIRS,
            durationMinutes = null,
            targetFloors = DefaultCoachSettings.create().stairTargetFloors,
            algorithmVersion = 2,
        )
        fixture.repository.rememberRecommendation(legacy)
        fixture.repository.recordRecommendationPublished(legacy.id, NOW)

        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `movement source and threshold changes supersede inactivity identity`() = runTest {
        suspend fun replacement(
            activityChange: (ActivitySnapshotEntity) -> ActivitySnapshotEntity = { it },
            settingsChange: (CoachSettings) -> CoachSettings = { it },
        ): Pair<CoachRecommendation.Action, CoachRecommendation.Action> {
            val initialSettings = DefaultCoachSettings.create()
            val initialActivity = inactiveActivity()
            val fixture = coachingFixture(
                readings = stableReadings(),
                settings = initialSettings,
                activity = initialActivity,
            )
            val original = fixture.repository.observeCurrentRecommendation().first()
                as CoachRecommendation.Action
            fixture.repository.rememberRecommendation(original)
            fixture.repository.recordRecommendationPublished(original.id, NOW)

            val replacementAt =
                NOW + initialSettings.reminderCooldownMinutes * 60_000L
            fixture.glucose.setReadings(
                stableReadings() + stableReading("safe-refresh", replacementAt),
            )
            fixture.activity.value = activityChange(initialActivity).copy(
                measuredAtEpochMillis = replacementAt,
            )
            fixture.settings.set(settingsChange(initialSettings))
            fixture.time.now = replacementAt

            val next = fixture.repository.observeCurrentRecommendation().first()
                as CoachRecommendation.Action
            return original to next
        }

        val moved = replacement(
            activityChange = { activity ->
                activity.copy(
                    lastMovementAtEpochMillis =
                        requireNotNull(activity.lastMovementAtEpochMillis) + 60_000L,
                )
            },
        )
        val otherSource = replacement(
            activityChange = { activity ->
                activity.copy(sourceId = "health-connect:other")
            },
        )
        val otherThreshold = replacement(
            settingsChange = { settings ->
                settings.copy(prolongedInactivityMinutes = 61)
            },
        )

        assertEquals(false, moved.first.id == moved.second.id)
        assertEquals(false, otherSource.first.id == otherSource.second.id)
        assertEquals(false, otherThreshold.first.id == otherThreshold.second.id)
    }

    @Test
    fun `expired inactivity snapshot cannot be renewed by same episode`() = runTest {
        val settings = DefaultCoachSettings.create().copy(staleReadingMinutes = 2)
        val fixture = coachingFixture(
            readings = stableReadings(),
            settings = settings,
            activity = inactiveActivity(),
        )
        val original = fixture.repository.observeCurrentRecommendation().first()
            as CoachRecommendation.Action
        fixture.repository.rememberRecommendation(original)
        fixture.repository.recordRecommendationPublished(original.id, NOW)

        fixture.glucose.setReadings(
            stableReadings() + stableReading("safe-refresh", NOW + 2 * 60_000L),
        )
        fixture.activity.value = inactiveActivity().copy(
            measuredAtEpochMillis = NOW + 2 * 60_000L,
        )
        fixture.time.now = NOW + 2 * 60_000L

        assertEquals(original.id, fixture.repository.recommendationSnapshot(original.id)?.id)
        assertNull(fixture.repository.observeCurrentRecommendation().first())
    }

    @Test
    fun `consumed inactivity episode stays suppressed after coach state tracks another action`() =
        runTest {
            val fixture = coachingFixture(
                readings = stableReadings(),
                activity = inactiveActivity(),
            )
            val inactivity = fixture.repository.observeCurrentRecommendation().first()
                as CoachRecommendation.Action
            fixture.repository.rememberRecommendation(inactivity)
            fixture.repository.recordRecommendationPublished(inactivity.id, NOW)
            val consumedSession = InterventionSession(
                id = "consumed-inactivity-session",
                type = InterventionType.WALK,
                status = InterventionStatus.COMPLETED,
                startedAtEpochMillis = NOW,
                endedAtEpochMillis = NOW + 60_000L,
                targetDurationMinutes = inactivity.durationMinutes,
                targetFloors = null,
                baselineGlucoseMgDl = 140,
                glucoseAfterMgDl = null,
                recommendationId = inactivity.id,
                recommendationReason = inactivity.reason,
            ).toEntity()
            `when`(fixture.interventions.getByRecommendationId(inactivity.id))
                .thenReturn(consumedSession)

            val nextEvaluationAt = NOW + 60_000L
            fixture.glucose.setReadings(
                stableReadings() + stableReading("later-safe", nextEvaluationAt),
            )
            fixture.activity.value = inactiveActivity().copy(
                measuredAtEpochMillis = nextEvaluationAt,
            )
            fixture.time.now = nextEvaluationAt
            fixture.states.upsert(
                requireNotNull(fixture.states.get()).copy(
                    lastRecommendationAtEpochMillis =
                        nextEvaluationAt -
                            DefaultCoachSettings.create().reminderCooldownMinutes * 60_000L - 1L,
                    lastRecommendationId = "intervening-recommendation",
                    consumedRecommendationId = "intervening-recommendation",
                ),
            )

            assertNull(fixture.repository.observeCurrentRecommendation().first())
            assertEquals(inactivity.id, fixture.repository.recommendationSnapshot(inactivity.id)?.id)
        }

    @Test
    fun `expired inactivity episode stays suppressed after another prompt becomes last`() =
        runTest {
            val fixture = coachingFixture(
                readings = stableReadings(),
                activity = inactiveActivity(),
            )
            val inactivity = fixture.repository.observeCurrentRecommendation().first()
                as CoachRecommendation.Action
            fixture.repository.rememberRecommendation(inactivity)
            fixture.repository.recordRecommendationPublished(inactivity.id, NOW)

            val afterOriginalExpiry = inactivity.validUntilEpochMillis + 1L
            fixture.glucose.setReadings(
                stableReadings() + stableReading("fresh-after-expiry", afterOriginalExpiry),
            )
            fixture.activity.value = inactiveActivity().copy(
                measuredAtEpochMillis = afterOriginalExpiry,
            )
            fixture.time.now = afterOriginalExpiry
            fixture.states.upsert(
                requireNotNull(fixture.states.get()).copy(
                    lastRecommendationAtEpochMillis =
                        afterOriginalExpiry -
                            DefaultCoachSettings.create().reminderCooldownMinutes * 60_000L - 1L,
                    lastRecommendationId = "intervening-recommendation",
                    consumedRecommendationId = null,
                ),
            )

            assertNull(fixture.repository.observeCurrentRecommendation().first())
            assertEquals(inactivity.id, fixture.repository.recommendationSnapshot(inactivity.id)?.id)
        }

    @Test
    fun `unexpired inactivity episode cannot republish after another prompt becomes last`() =
        runTest {
            val settings = DefaultCoachSettings.create().copy(staleReadingMinutes = 120)
            val fixture = coachingFixture(
                readings = stableReadings(),
                settings = settings,
                activity = inactiveActivity(),
            )
            val inactivity = fixture.repository.observeCurrentRecommendation().first()
                as CoachRecommendation.Action
            fixture.repository.rememberRecommendation(inactivity)
            fixture.repository.recordRecommendationPublished(inactivity.id, NOW)

            val later = NOW + settings.reminderCooldownMinutes * 60_000L + 1L
            fixture.glucose.setReadings(
                stableReadings() + stableReading("fresh-later", later),
            )
            fixture.activity.value = inactiveActivity().copy(measuredAtEpochMillis = later)
            fixture.time.now = later
            fixture.states.upsert(
                requireNotNull(fixture.states.get()).copy(
                    lastRecommendationAtEpochMillis =
                        later - settings.reminderCooldownMinutes * 60_000L - 1L,
                    lastRecommendationId = "intervening-recommendation",
                    consumedRecommendationId = null,
                ),
            )

            assertEquals(true, inactivity.validUntilEpochMillis > later)
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
        val activities = MutableStateFlow(activity)
        `when`(activityDao.observeLatest()).thenReturn(activities)
        `when`(mealDao.observeLatest()).thenReturn(flowOf(null))
        val states = InMemoryCoachStateDao(null)
        val snapshots = InMemoryRecommendationSnapshotDao()
        val glucose = FakeGlucoseRepository(readings)
        val mutableSettings = FakeSettingsRepository(settings)
        val time = FakeTimeSource(NOW)
        return CoachingFixture(
            repository = CoachingRepositoryImpl(
                glucoseDao = mock(GlucoseDao::class.java),
                activityDao = activityDao,
                interventionDao = interventionDao,
                mealDao = mealDao,
                coachStateDao = states,
                recommendationSnapshotDao = snapshots,
                settingsRepository = mutableSettings,
                glucoseRepository = glucose,
                ruleEngine = CoachRuleEngine(),
                observationAnalyzer = ObservationAnalyzer(),
                timeSource = time,
            ),
            glucose = glucose,
            activity = activities,
            settings = mutableSettings,
            time = time,
            interventions = interventionDao,
            states = states,
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

    private fun stableReadings() = listOf(
        stableReading("older", NOW - 5 * 60_000L),
        stableReading("latest", NOW),
    )

    private fun stableReading(
        id: String,
        measuredAtEpochMillis: Long,
    ) = risingReading(id, measuredAtEpochMillis).copy(
        rateMgDlPerMinute = 0.0,
        trend = GlucoseTrend.STABLE,
    )

    private fun inactiveActivity() = ActivitySnapshotEntity(
        dayStartEpochMillis = Instant.ofEpochMilli(NOW)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        stepsToday = 0,
        floorsToday = 0.0,
        latestHeartRateBpm = null,
        activeCaloriesToday = null,
        lastMovementAtEpochMillis = NOW - 2 * 60 * 60_000L,
        measuredAtEpochMillis = NOW,
        sourceId = "health-connect",
        exerciseSessionCountToday = 0,
        exerciseDurationMinutesToday = 0,
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
        initial: CoachSettings = DefaultCoachSettings.create(),
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)

        fun set(settings: CoachSettings) {
            state.value = settings
        }

        override fun observe(): Flow<CoachSettings> = state
        override suspend fun update(settings: CoachSettings) {
            set(settings)
        }
        override suspend fun reset() {
            state.value = DefaultCoachSettings.create()
        }
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
        val activity: MutableStateFlow<ActivitySnapshotEntity?>,
        val settings: FakeSettingsRepository,
        val time: FakeTimeSource,
        val interventions: InterventionDao,
        val states: InMemoryCoachStateDao,
    )

    private companion object {
        const val NOW = 43_200_000L
    }
}
