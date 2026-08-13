package com.young.metaboliccoach.background

import android.content.Context
import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.domain.ActionDisplayDeadlinePolicy
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.InactivityConfirmationPolicy
import com.young.metaboliccoach.core.domain.PersonalDataRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.WatchState
import com.young.metaboliccoach.sync.PhoneSyncMetadata
import com.young.metaboliccoach.sync.PhoneSyncMetadataStore
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class PhoneRefreshCoordinatorTest {
    @Test
    fun `invalidated inactivity is omitted from watch and phone publication`() = runTest {
        val now = System.currentTimeMillis()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
            workingHoursStartMinuteOfDay = 0,
            workingHoursEndMinuteOfDay = 0,
        )
        val glucose = safeReading(now - 1_000L)
        val sourceActivity = inactiveActivity(now, settings)
        val recommendation = inactivityRecommendation(now, settings, glucose, sourceActivity)
        val fixture = fixture(
            recommendation = recommendation,
            glucose = glucose,
            activity = null,
            settings = settings,
            published = true,
        )

        fixture.coordinator.refresh(refreshProviders = false)

        val state = capturePublishedState(fixture.watchSync)
        assertNull(state.recommendation)
        assertEquals(1, invocations(fixture.notifications, "clearCoachPrompt").size)
        assertEquals(0, invocations(fixture.notifications, "showCoachPrompt").size)
        assertEquals(0, invocations(fixture.coaching, "recordRecommendationPublished").size)
    }

    @Test
    fun `watch publication and phone notification use the same effective action`() = runTest {
        val now = System.currentTimeMillis()
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
        )
        val glucose = safeReading(now - 1_000L)
        val recommendation = postMealRecommendation(now, glucose)
        val fixture = fixture(
            recommendation = recommendation,
            glucose = glucose,
            activity = null,
            settings = settings,
            published = true,
        )

        fixture.coordinator.refresh(refreshProviders = false)

        val state = capturePublishedState(fixture.watchSync)
        assertEquals(recommendation, state.recommendation)
        val recordCall = invocations(fixture.coaching, "recordRecommendationPublished").single()
        assertEquals(recommendation.id, recordCall.arguments[0])
        val showCall = invocations(fixture.notifications, "showCoachPrompt").single()
        assertEquals(recommendation, showCall.arguments[0])
        assertEquals(recommendation.validUntilEpochMillis, showCall.arguments[2])
        assertEquals(0, invocations(fixture.notifications, "clearCoachPrompt").size)
    }

    @Test
    fun `valid inactivity phone prompt uses bounded display deadline`() = runTest {
        val now = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val minuteOfDay = java.time.Instant.ofEpochMilli(now)
            .atZone(zoneId)
            .toLocalTime()
            .toSecondOfDay() / 60
        val quietStart = (minuteOfDay + 5) % MINUTES_PER_DAY
        val settings = DefaultCoachSettings.create().copy(
            quietHoursStartMinuteOfDay = quietStart,
            quietHoursEndMinuteOfDay = (quietStart + 60) % MINUTES_PER_DAY,
            workingHoursStartMinuteOfDay = 0,
            workingHoursEndMinuteOfDay = 0,
        )
        val glucose = safeReading(now - 1_000L)
        val activity = inactiveActivity(now, settings)
        val recommendation = inactivityRecommendation(now, settings, glucose, activity)
        val fixture = fixture(
            recommendation = recommendation,
            glucose = glucose,
            activity = activity,
            settings = settings,
            published = true,
        )

        fixture.coordinator.refresh(refreshProviders = false)

        val state = capturePublishedState(fixture.watchSync)
        assertEquals(recommendation, state.recommendation)
        val showCall = invocations(fixture.notifications, "showCoachPrompt").single()
        val publicationNow = showCall.arguments[1] as Long
        val expectedDeadline = ActionDisplayDeadlinePolicy.displayUntilEpochMillis(
            recommendation = recommendation,
            settings = settings,
            nowEpochMillis = publicationNow,
            zoneId = zoneId,
        )
        assertEquals(expectedDeadline, showCall.arguments[2])
        assertEquals(true, expectedDeadline < recommendation.validUntilEpochMillis)
    }

    private suspend fun fixture(
        recommendation: CoachRecommendation.Action,
        glucose: GlucoseReading,
        activity: ActivitySnapshot?,
        settings: com.young.metaboliccoach.core.model.CoachSettings,
        published: Boolean,
    ): Fixture {
        val context = Mockito.mock(Context::class.java)
        val glucoseRepository = Mockito.mock(GlucoseRepository::class.java)
        val activityRepository = Mockito.mock(ActivityRepository::class.java)
        val settingsRepository = Mockito.mock(SettingsRepository::class.java)
        val coachingRepository = Mockito.mock(CoachingRepository::class.java)
        val watchSyncRepository = Mockito.mock(WatchSyncRepository::class.java)
        val notifications = Mockito.mock(CoachNotificationManager::class.java)
        val followUps = Mockito.mock(InterventionFollowUpScheduler::class.java)
        val metadata = Mockito.mock(PhoneSyncMetadataStore::class.java)
        val syncScheduler = Mockito.mock(SyncScheduler::class.java)
        val commandProcessor = Mockito.mock(PhoneCommandProcessor::class.java)
        val personalData = Mockito.mock(PersonalDataRepository::class.java)

        Mockito.`when`(glucoseRepository.observeLatest()).thenReturn(flowOf(glucose))
        Mockito.`when`(activityRepository.observeToday()).thenReturn(flowOf(activity))
        Mockito.`when`(settingsRepository.observe()).thenReturn(flowOf(settings))
        Mockito.`when`(coachingRepository.pendingFollowUpSessions()).thenReturn(emptyList())
        Mockito.`when`(coachingRepository.latestActiveSession()).thenReturn(null)
        Mockito.`when`(coachingRepository.observeCurrentRecommendation())
            .thenReturn(flowOf(recommendation))
        Mockito.`when`(coachingRepository.rememberRecommendation(recommendation))
            .thenReturn(recommendation)
        Mockito.doReturn(published).`when`(coachingRepository)
            .recordRecommendationPublished(Mockito.anyString(), Mockito.anyLong())
        Mockito.`when`(metadata.nextPublication()).thenReturn(
            PhoneSyncMetadata(
                phoneInstanceId = "phone-instance",
                stateRevision = 1L,
                lastSessionCommandAck = null,
                dataResetId = null,
            ),
        )

        return Fixture(
            coordinator = PhoneRefreshCoordinator(
                context = context,
                glucoseRepository = glucoseRepository,
                activityRepository = activityRepository,
                settingsRepository = settingsRepository,
                coachingRepository = coachingRepository,
                watchSyncRepository = watchSyncRepository,
                notificationManager = notifications,
                followUpScheduler = followUps,
                syncMetadataStore = metadata,
                syncScheduler = syncScheduler,
                commandProcessor = commandProcessor,
                personalDataRepository = personalData,
                mutationGate = PhoneDataMutationGate(),
            ),
            coaching = coachingRepository,
            watchSync = watchSyncRepository,
            notifications = notifications,
        )
    }

    private suspend fun capturePublishedState(
        watchSyncRepository: WatchSyncRepository,
    ): WatchState =
        invocations(watchSyncRepository, "publish").single().arguments[0] as WatchState

    private fun invocations(mock: Any, methodName: String) =
        Mockito.mockingDetails(mock).invocations.filter { it.method.name == methodName }

    private fun safeReading(measuredAtEpochMillis: Long) = GlucoseReading(
        id = "safe-reading",
        valueMgDl = 140,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = "source-a",
    )

    private fun postMealRecommendation(
        nowEpochMillis: Long,
        reading: GlucoseReading,
    ) = CoachRecommendation.Action(
        reason = CoachReason.POST_MEAL_WINDOW,
        id = "post-meal",
        createdAtEpochMillis = nowEpochMillis - 1_000L,
        validUntilEpochMillis = nowEpochMillis + 60 * 60_000L,
        interventionType = InterventionType.WALK,
        title = "Walk?",
        actionLabel = "START WALK",
        durationMinutes = 10,
        targetFloors = null,
        triggerContextId = "meal",
        triggerAtEpochMillis = nowEpochMillis - 30 * 60_000L,
        glucoseSourceId = reading.sourceId,
        safetyReadingId = reading.id,
        safetyReadingAtEpochMillis = reading.measuredAtEpochMillis,
    )

    private fun inactivityRecommendation(
        nowEpochMillis: Long,
        settings: com.young.metaboliccoach.core.model.CoachSettings,
        reading: GlucoseReading,
        activity: ActivitySnapshot,
    ): CoachRecommendation.Action {
        val confirmation = requireNotNull(
            InactivityConfirmationPolicy.confirm(
                activity = activity,
                settings = settings,
                nowEpochMillis = nowEpochMillis,
                minuteOfDay = java.time.Instant.ofEpochMilli(nowEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .toSecondOfDay() / 60,
                zoneId = ZoneId.systemDefault(),
            ),
        )
        return CoachRecommendation.Action(
            reason = CoachReason.PROLONGED_INACTIVITY,
            id = confirmation.recommendationId,
            createdAtEpochMillis = nowEpochMillis - 1_000L,
            validUntilEpochMillis = minOf(
                reading.measuredAtEpochMillis + settings.staleReadingMinutes * 60_000L,
                confirmation.activityFreshUntilEpochMillis,
            ),
            interventionType = InterventionType.WALK,
            title = "Walk?",
            actionLabel = "START WALK",
            durationMinutes = settings.walkingDurationMinutes,
            targetFloors = null,
            algorithmVersion = InactivityConfirmationPolicy.ALGORITHM_VERSION,
            triggerContextId = confirmation.triggerIdentity,
            triggerAtEpochMillis = confirmation.thresholdCrossingAtEpochMillis,
            glucoseSourceId = reading.sourceId,
            safetyReadingId = reading.id,
            safetyReadingAtEpochMillis = reading.measuredAtEpochMillis,
        )
    }

    private fun inactiveActivity(
        nowEpochMillis: Long,
        settings: com.young.metaboliccoach.core.model.CoachSettings,
    ) = ActivitySnapshot(
        stepsToday = 1_000,
        floorsToday = 1.0,
        latestHeartRateBpm = 70,
        activeCaloriesToday = 100.0,
        lastMovementAtEpochMillis =
            nowEpochMillis - settings.prolongedInactivityMinutes * 60_000L,
        measuredAtEpochMillis = nowEpochMillis - 1_000L,
        sourceId = "activity-source",
    )

    private data class Fixture(
        val coordinator: PhoneRefreshCoordinator,
        val coaching: CoachingRepository,
        val watchSync: WatchSyncRepository,
        val notifications: CoachNotificationManager,
    )

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
