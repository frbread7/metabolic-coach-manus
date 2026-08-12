package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachContext
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.MealMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachRuleEngineTest {
    private val engine = CoachRuleEngine()
    private val settings = DefaultCoachSettings.create()
    private val now = 1_700_000_000_000L

    @Test
    fun `rapid rise recommends configured walk`() {
        val recommendation = engine.recommend(
            context(glucose = glucose(value = 146, rate = 2.4)),
            settings,
        ) as CoachRecommendation.Action

        assertEquals(CoachReason.RAPID_GLUCOSE_RISE, recommendation.reason)
        assertEquals(InterventionType.WALK, recommendation.interventionType)
        assertEquals(settings.walkingDurationMinutes, recommendation.durationMinutes)
        assertTrue(recommendation.id.startsWith("RAPID_GLUCOSE_RISE:v3:"))
        assertTrue(recommendation.triggerContextId?.startsWith("rapid-pair:v3:") == true)
        assertEquals(now, recommendation.triggerAtEpochMillis)
        assertEquals(3, recommendation.algorithmVersion)
        assertEquals("test", recommendation.glucoseSourceId)
        assertEquals("reading", recommendation.safetyReadingId)
        assertEquals(
            now + settings.staleReadingMinutes * 60_000L,
            recommendation.validUntilEpochMillis,
        )
    }

    @Test
    fun `low reading pauses exercise recommendations`() {
        val recommendation = engine.recommend(
            context(glucose = glucose(value = settings.lowGlucoseThresholdMgDl - 1, rate = 3.0)),
            settings,
        )

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.LOW_GLUCOSE_SAFETY, recommendation?.reason)
    }

    @Test
    fun `low glucose safety remains visible when action reminders are disabled`() {
        val recommendation = engine.recommend(
            context(
                glucose = glucose(
                    value = settings.lowGlucoseThresholdMgDl - 1,
                    rate = 3.0,
                ),
                minuteOfDay = settings.quietHoursStartMinuteOfDay,
                snoozedUntil = now + 60_000,
            ),
            settings.copy(notificationsEnabled = false),
        )

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.LOW_GLUCOSE_SAFETY, recommendation?.reason)
    }

    @Test
    fun `rapidly falling glucose pauses post meal exercise above low threshold`() {
        val meal = MealMarker(
            id = "meal",
            occurredAtEpochMillis = now - settings.postMealDelayMinutes * 60_000L,
        )

        val recommendation = engine.recommend(
            context(
                glucose = glucose(
                    value = settings.lowGlucoseThresholdMgDl + 30,
                    rate = -settings.exercisePauseFallRateMgDlPerMinute,
                ),
                meal = meal,
            ),
            settings,
        )

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.FALLING_GLUCOSE_SAFETY, recommendation?.reason)
    }

    @Test
    fun `rapidly falling glucose pauses inactivity exercise above low threshold`() {
        val recommendation = engine.recommend(
            context(
                glucose = glucose(
                    value = settings.lowGlucoseThresholdMgDl + 30,
                    rate = -settings.exercisePauseFallRateMgDlPerMinute - 0.1,
                ),
                activity = inactiveActivity(),
            ),
            settings,
        )

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.FALLING_GLUCOSE_SAFETY, recommendation?.reason)
    }

    @Test
    fun `fall rate below configured pause boundary permits post meal coaching`() {
        val meal = MealMarker(
            id = "meal",
            occurredAtEpochMillis = now - settings.postMealDelayMinutes * 60_000L,
        )

        val recommendation = engine.recommend(
            context(
                glucose = glucose(
                    value = settings.lowGlucoseThresholdMgDl + 30,
                    rate = -settings.exercisePauseFallRateMgDlPerMinute + 0.1,
                ),
                meal = meal,
            ),
            settings,
        )

        assertEquals(CoachReason.POST_MEAL_WINDOW, recommendation?.reason)
        assertEquals(
            "A short walk may fit now",
            (recommendation as CoachRecommendation.Action).title,
        )
        val action = recommendation as CoachRecommendation.Action
        assertEquals(meal.id, action.triggerContextId)
        assertEquals(meal.occurredAtEpochMillis, action.triggerAtEpochMillis)
        assertEquals("test", action.glucoseSourceId)
        assertEquals("reading", action.safetyReadingId)
        assertEquals(now, action.safetyReadingAtEpochMillis)
    }

    @Test
    fun `post meal identity stays stable when a newer safe reading arrives`() {
        val meal = MealMarker(
            id = "meal-stable",
            occurredAtEpochMillis = now - settings.postMealDelayMinutes * 60_000L,
        )
        val first = engine.recommend(
            context(glucose = glucose(130, 0.0), meal = meal),
            settings,
            allowedActionReasons = setOf(CoachReason.POST_MEAL_WINDOW),
        ) as CoachRecommendation.Action
        val second = engine.recommend(
            context(
                glucose = glucose(132, 0.0).copy(
                    id = "new-reading",
                    measuredAtEpochMillis = now + 1_000L,
                ),
                meal = meal,
            ).copy(nowEpochMillis = now + 1_000L),
            settings,
            allowedActionReasons = setOf(CoachReason.POST_MEAL_WINDOW),
        ) as CoachRecommendation.Action

        assertEquals(first.id, second.id)
        assertEquals(2, first.algorithmVersion)
    }

    @Test
    fun `post meal production mode cannot emit rapid rise action`() {
        val recommendation = engine.recommend(
            context(glucose = glucose(146, 3.0)),
            settings,
            allowedActionReasons = setOf(CoachReason.POST_MEAL_WINDOW),
        )

        assertNull(recommendation)
    }

    @Test
    fun `rapid rise requires a confirming predecessor`() {
        val recommendation = engine.recommend(
            context(glucose = glucose(146, 3.0), previousGlucose = null),
            settings,
            allowedActionReasons = setOf(CoachReason.RAPID_GLUCOSE_RISE),
        )

        assertNull(recommendation)
    }

    @Test
    fun `post meal wins when rapid rise is also confirmed`() {
        val meal = MealMarker(
            id = "overlap-meal",
            occurredAtEpochMillis = now - settings.postMealDelayMinutes * 60_000L,
        )

        val recommendation = engine.recommend(
            context(glucose = glucose(146, 3.0), meal = meal),
            settings,
            allowedActionReasons = setOf(
                CoachReason.POST_MEAL_WINDOW,
                CoachReason.RAPID_GLUCOSE_RISE,
            ),
        )

        assertEquals(CoachReason.POST_MEAL_WINDOW, recommendation?.reason)
    }

    @Test
    fun `stale reading does not produce exercise action`() {
        val stale = glucose(value = 150, rate = 3.0).copy(
            measuredAtEpochMillis = now - (settings.staleReadingMinutes + 1) * 60_000L,
        )

        val recommendation = engine.recommend(context(glucose = stale), settings)

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.STALE_GLUCOSE_DATA, recommendation?.reason)
    }

    @Test
    fun `reading is stale exactly at configured boundary`() {
        val stale = glucose(value = 150, rate = 3.0).copy(
            measuredAtEpochMillis = now - settings.staleReadingMinutes * 60_000L,
        )

        val recommendation = engine.recommend(context(glucose = stale), settings)

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.STALE_GLUCOSE_DATA, recommendation?.reason)
    }

    @Test
    fun `future glucose timestamp pauses exercise coaching`() {
        val future = glucose(value = 150, rate = 3.0).copy(
            measuredAtEpochMillis = now + 1,
        )

        val recommendation = engine.recommend(context(glucose = future), settings)

        assertTrue(recommendation is CoachRecommendation.Information)
        assertEquals(CoachReason.STALE_GLUCOSE_DATA, recommendation?.reason)
    }

    @Test
    fun `post meal window recommends walk`() {
        val meal = MealMarker(
            id = "meal",
            occurredAtEpochMillis = now - settings.postMealDelayMinutes * 60_000L,
        )

        val recommendation = engine.recommend(
            context(glucose = glucose(value = 120, rate = 0.0), meal = meal),
            settings,
        )

        assertEquals(CoachReason.POST_MEAL_WINDOW, recommendation?.reason)
    }

    @Test
    fun `post meal window uses an exact exclusive millisecond end`() {
        val windowEndMillis =
            (settings.postMealDelayMinutes + settings.postMealWindowMinutes) * 60_000L
        val justInside = MealMarker(
            id = "inside",
            occurredAtEpochMillis = now - windowEndMillis + 1,
        )
        val atEnd = MealMarker(
            id = "end",
            occurredAtEpochMillis = now - windowEndMillis,
        )

        assertEquals(
            CoachReason.POST_MEAL_WINDOW,
            engine.recommend(
                context(glucose = glucose(value = 120, rate = 0.0), meal = justInside),
                settings,
            )?.reason,
        )
        assertNull(
            engine.recommend(
                context(glucose = glucose(value = 120, rate = 0.0), meal = atEnd),
                settings,
            ),
        )
    }

    @Test
    fun `future meal marker does not trigger post meal coaching`() {
        val futureMeal = MealMarker(id = "future", occurredAtEpochMillis = now + 1)

        assertNull(
            engine.recommend(
                context(glucose = glucose(value = 120, rate = 0.0), meal = futureMeal),
                settings.copy(postMealDelayMinutes = 0),
            ),
        )
    }

    @Test
    fun `prolonged inactivity recommends configured stairs`() {
        val recommendation = engine.recommend(
            context(glucose = glucose(value = 120, rate = 0.0), activity = inactiveActivity()),
            settings,
        ) as CoachRecommendation.Action

        assertEquals(InterventionType.STAIRS, recommendation.interventionType)
        assertEquals(settings.stairTargetFloors, recommendation.targetFloors)
        assertEquals(
            now,
            recommendation.triggerAtEpochMillis,
        )
    }

    @Test
    fun `prolonged inactivity falls back to walking when stair reminders are disabled`() {
        val recommendation = engine.recommend(
            context(glucose = glucose(value = 120, rate = 0.0), activity = inactiveActivity()),
            settings.copy(stairRemindersEnabled = false),
        ) as CoachRecommendation.Action

        assertEquals(CoachReason.PROLONGED_INACTIVITY, recommendation.reason)
        assertEquals(InterventionType.WALK, recommendation.interventionType)
        assertEquals(settings.walkingDurationMinutes, recommendation.durationMinutes)
    }

    @Test
    fun `prolonged inactivity is silent when both activity reminders are disabled`() {
        assertNull(
            engine.recommend(
                context(
                    glucose = glucose(value = 120, rate = 0.0),
                    activity = inactiveActivity(),
                ),
                settings.copy(
                    stairRemindersEnabled = false,
                    walkingRemindersEnabled = false,
                ),
            ),
        )
    }

    @Test
    fun `prolonged inactivity is limited to configured working hours`() {
        val beforeWork = settings.workingHoursStartMinuteOfDay - 1

        val recommendation = engine.recommend(
            context(
                glucose = glucose(value = 120, rate = 0.0),
                activity = inactiveActivity(),
                minuteOfDay = beforeWork,
            ),
            settings,
        )

        assertNull(recommendation)
    }

    @Test
    fun `quiet hours suppress reminder`() {
        val quietContext = context(
            glucose = glucose(value = 146, rate = 3.0),
            minuteOfDay = settings.quietHoursStartMinuteOfDay,
        )

        assertNull(engine.recommend(quietContext, settings))
    }

    @Test
    fun `cooldown suppresses repeated recommendation`() {
        val recent = now - (settings.reminderCooldownMinutes - 1) * 60_000L

        assertNull(
            engine.recommend(
                context(
                    glucose = glucose(value = 146, rate = 3.0),
                    lastRecommendationAt = recent,
                ),
                settings,
            ),
        )
    }

    @Test
    fun `new rapid pair cannot bypass global cooldown`() {
        val newer = glucose(value = 150, rate = 3.0).copy(
            id = "newer-reading",
            measuredAtEpochMillis = now,
        )
        val previous = glucose(value = 144, rate = 3.0).copy(
            id = "previous-reading",
            measuredAtEpochMillis = now - 5 * 60_000L,
        )

        assertNull(
            engine.recommend(
                context(
                    glucose = newer,
                    previousGlucose = previous,
                    lastRecommendationAt = now - 1_000L,
                    lastRecommendationId = "another-pair",
                ),
                settings,
                allowedActionReasons = setOf(CoachReason.RAPID_GLUCOSE_RISE),
            ),
        )
    }

    @Test
    fun `same rapid pair may redeliver once after snooze expires`() {
        val initial = engine.recommend(
            context(glucose = glucose(146, 3.0)),
            settings,
            allowedActionReasons = setOf(CoachReason.RAPID_GLUCOSE_RISE),
        ) as CoachRecommendation.Action

        val redelivery = engine.recommend(
            context(
                glucose = glucose(146, 3.0),
                lastRecommendationAt = now - 1_000L,
                lastRecommendationId = initial.id,
                snoozedUntil = now,
            ),
            settings,
            allowedActionReasons = setOf(CoachReason.RAPID_GLUCOSE_RISE),
        ) as CoachRecommendation.Action

        assertEquals(initial.id, redelivery.id)
    }

    @Test
    fun `snooze and daily limit suppress action reminders`() {
        val rising = glucose(value = 146, rate = 3.0)

        assertNull(
            engine.recommend(
                context(
                    glucose = rising,
                    snoozedUntil = now + 1,
                ),
                settings,
            ),
        )
        assertNull(
            engine.recommend(
                context(
                    glucose = rising,
                    notificationsSentToday = settings.maximumNotificationsPerDay,
                ),
                settings,
            ),
        )
    }

    @Test
    fun `overnight working hours include late night and exclude midday`() {
        val overnight = settings.copy(
            workingHoursStartMinuteOfDay = 22 * 60,
            workingHoursEndMinuteOfDay = 6 * 60,
            quietHoursStartMinuteOfDay = 0,
            quietHoursEndMinuteOfDay = 0,
        )
        val lateNight = context(
            glucose = glucose(value = 120, rate = 0.0),
            activity = inactiveActivity(),
            minuteOfDay = 23 * 60,
        )
        val midday = lateNight.copy(minuteOfDay = 12 * 60)

        assertEquals(
            CoachReason.PROLONGED_INACTIVITY,
            engine.recommend(lateNight, overnight)?.reason,
        )
        assertNull(engine.recommend(midday, overnight))
    }

    @Test
    fun `trend rate is used when numeric rate is unavailable`() {
        val trendOnly = glucose(value = 146, rate = 0.0).copy(
            rateMgDlPerMinute = null,
            trend = GlucoseTrend.RISING,
        )

        assertEquals(
            CoachReason.RAPID_GLUCOSE_RISE,
            engine.recommend(context(glucose = trendOnly), settings)?.reason,
        )
    }

    private fun context(
        glucose: GlucoseReading?,
        previousGlucose: GlucoseReading? = glucose?.copy(
            id = "previous-reading",
            measuredAtEpochMillis = glucose.measuredAtEpochMillis - 5 * 60_000L,
            receivedAtEpochMillis = glucose.receivedAtEpochMillis - 5 * 60_000L,
        ),
        activity: ActivitySnapshot? = null,
        meal: MealMarker? = null,
        minuteOfDay: Int = 12 * 60,
        lastRecommendationAt: Long? = null,
        lastRecommendationId: String? = null,
        snoozedUntil: Long? = null,
        notificationsSentToday: Int = 0,
    ) = CoachContext(
        nowEpochMillis = now,
        minuteOfDay = minuteOfDay,
        glucose = glucose,
        activity = activity,
        mostRecentMeal = meal,
        lastRecommendationAtEpochMillis = lastRecommendationAt,
        lastRecommendationId = lastRecommendationId,
        snoozedUntilEpochMillis = snoozedUntil,
        notificationsSentToday = notificationsSentToday,
        previousGlucose = previousGlucose,
    )

    private fun glucose(value: Int, rate: Double) = GlucoseReading(
        id = "reading",
        valueMgDl = value,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 4,
        rateMgDlPerMinute = rate,
        measuredAtEpochMillis = now,
        receivedAtEpochMillis = now,
        sourceId = "test",
    )

    private fun inactiveActivity() = ActivitySnapshot(
        stepsToday = 2_000,
        floorsToday = 1.0,
        latestHeartRateBpm = 70,
        activeCaloriesToday = 100.0,
        lastMovementAtEpochMillis =
            now - settings.prolongedInactivityMinutes * 60_000L,
        measuredAtEpochMillis = now,
        sourceId = "test",
    )
}
