package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.data.db.ActivityDao
import com.young.metaboliccoach.core.data.db.CoachStateDao
import com.young.metaboliccoach.core.data.db.CoachStateEntity
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.InterventionDao
import com.young.metaboliccoach.core.data.db.MealDao
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotDao
import com.young.metaboliccoach.core.data.db.toEntity
import com.young.metaboliccoach.core.data.db.toModel
import com.young.metaboliccoach.core.domain.CoachRuleEngine
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.ObservationAnalyzer
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachContext
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DailySummary
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.PersonalObservation
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CoachingRepositoryImpl @Inject constructor(
    private val glucoseDao: GlucoseDao,
    private val activityDao: ActivityDao,
    private val interventionDao: InterventionDao,
    private val mealDao: MealDao,
    private val coachStateDao: CoachStateDao,
    private val recommendationSnapshotDao: RecommendationSnapshotDao,
    private val settingsRepository: SettingsRepository,
    private val ruleEngine: CoachRuleEngine,
    private val observationAnalyzer: ObservationAnalyzer,
    private val timeSource: CoachTimeSource,
) : CoachingRepository {
    private data class DailyScope(
        val settings: CoachSettings,
        val dayStartEpochMillis: Long,
        val glucoseSourceId: String?,
    )

    private val selectedGlucoseAndSettings = settingsRepository.observe().flatMapLatest { settings ->
        settings.selectedGlucoseSourcePrefix()?.let { sourcePrefix ->
            glucoseDao.observeLatestForSource(sourcePrefix).map { settings to it }
        } ?: flowOf(settings to null)
    }

    override fun observeCurrentRecommendation(): Flow<CoachRecommendation?> = combine(
        selectedGlucoseAndSettings,
        activityDao.observeLatest(),
        mealDao.observeLatest(),
        coachStateDao.observe(),
        timeSource.minuteTicks(),
    ) { (settings, glucose), activity, meal, state, now ->
        val currentState = state.forCurrentDay(now)
        ruleEngine.recommend(
            context = CoachContext(
                nowEpochMillis = now,
                minuteOfDay = Instant.ofEpochMilli(now)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .toSecondOfDay() / 60,
                glucose = glucose?.toModel(),
                activity = activity
                    ?.takeIf { it.dayStartEpochMillis == startOfToday(now) }
                    ?.toModel(),
                mostRecentMeal = meal?.toModel(),
                lastRecommendationAtEpochMillis =
                    currentState.lastRecommendationAtEpochMillis,
                snoozedUntilEpochMillis = currentState.snoozedUntilEpochMillis,
                notificationsSentToday = currentState.notificationsSentToday,
            ),
            settings = settings,
        )
    }

    override fun observeTodaySummary(): Flow<DailySummary> = combine(
        selectedGlucoseAndSettings,
        observeDayStart(),
    ) { (settings, latestGlucose), dayStart ->
        DailyScope(settings, dayStart, latestGlucose?.sourceId)
    }.flatMapLatest { scope ->
        val glucoseReadings = scope.glucoseSourceId?.let { sourceId ->
            glucoseDao.observeSinceExactSource(
                sourceId = sourceId,
                startEpochMillis = scope.dayStartEpochMillis,
            )
        } ?: flowOf(emptyList())
        combine(
            glucoseReadings,
            interventionDao.observeSince(scope.dayStartEpochMillis),
            activityDao.observeLatest(),
        ) { readings, sessions, activity ->
            val stablePercent = readings.takeIf { it.isNotEmpty() }?.let { values ->
                values.count {
                    it.valueMgDl in
                        scope.settings.targetLowerMgDl..scope.settings.targetUpperMgDl
                } * 100 / values.size
            }
            val todayActivity =
                activity?.takeIf { it.dayStartEpochMillis == scope.dayStartEpochMillis }
            DailySummary(
                dayStartEpochMillis = scope.dayStartEpochMillis,
                stableGlucosePercent = stablePercent,
                completedWalks = sessions.count {
                    it.type == InterventionType.WALK.name &&
                        it.status == InterventionStatus.COMPLETED.name
                },
                completedStairSessions = sessions.count {
                    it.type == InterventionType.STAIRS.name &&
                        it.status == InterventionStatus.COMPLETED.name
                },
                steps = todayActivity?.stepsToday ?: 0,
                floors = todayActivity?.floorsToday ?: 0.0,
                exerciseSessionCount = todayActivity?.exerciseSessionCountToday ?: 0,
                exerciseDurationMinutes = todayActivity?.exerciseDurationMinutesToday ?: 0,
            )
        }
    }

    override fun observePersonalObservations(): Flow<List<PersonalObservation>> = combine(
        interventionDao.observeAll(),
        mealDao.observeAll(),
        settingsRepository.observe(),
    ) { sessions, meals, settings ->
        observationAnalyzer.analyze(
            sessions = sessions.map { it.toModel() },
            settings = settings,
            mealMarkers = meals.map { it.toModel() },
        )
    }

    override fun observeActiveSession(): Flow<InterventionSession?> =
        interventionDao.observeLatestActive().map { it?.toModel() }

    override suspend fun saveMealMarker(marker: MealMarker) {
        mealDao.upsert(marker.toEntity())
    }

    override suspend fun startSession(session: InterventionSession): InterventionSession {
        require(session.status == InterventionStatus.STARTED) {
            "A new intervention session must have STARTED status."
        }
        return interventionDao.startIfNoActive(session.toEntity()).toModel()
    }

    override suspend fun completeSession(
        sessionId: String,
        endedAtEpochMillis: Long,
        followUpDueAtEpochMillis: Long,
    ): InterventionSession? {
        val current = interventionDao.getById(sessionId) ?: return null
        val effectiveEnd = endedAtEpochMillis.coerceAtLeast(current.startedAtEpochMillis)
        interventionDao.completeStarted(
            sessionId = sessionId,
            endedAtEpochMillis = effectiveEnd,
            followUpDueAtEpochMillis = followUpDueAtEpochMillis.coerceAtLeast(effectiveEnd),
        )
        return interventionDao.getById(sessionId)?.toModel()
    }

    override suspend fun session(sessionId: String): InterventionSession? =
        interventionDao.getById(sessionId)?.toModel()

    override suspend fun latestActiveSession(): InterventionSession? =
        interventionDao.latestActive()?.toModel()

    override suspend fun pendingFollowUpSessions(): List<InterventionSession> =
        interventionDao.pendingFollowUps().map { it.toModel() }

    override suspend fun finalizeFollowUp(
        sessionId: String,
        glucoseMgDl: Int?,
        readingAtEpochMillis: Long?,
        readingId: String?,
        sourceId: String?,
        finalizedAtEpochMillis: Long,
    ): Boolean {
        val readingFields = listOf(glucoseMgDl, readingAtEpochMillis, readingId, sourceId)
        require(readingFields.all { it == null } || readingFields.all { it != null }) {
            "Follow-up glucose provenance fields must be stored together."
        }
        return interventionDao.finalizeFollowUp(
            sessionId = sessionId,
            glucoseMgDl = glucoseMgDl,
            readingAtEpochMillis = readingAtEpochMillis,
            readingId = readingId,
            sourceId = sourceId,
            finalizedAtEpochMillis = finalizedAtEpochMillis,
        ) > 0
    }

    override suspend fun snooze(nowEpochMillis: Long) {
        val settings = settingsRepository.observe().first()
        val current = coachStateDao.get().forCurrentDay(nowEpochMillis)
        val requestedSnoozeUntil =
            nowEpochMillis + settings.snoozeMinutes * MILLIS_PER_MINUTE
        coachStateDao.upsert(
            current.copy(
                snoozedUntilEpochMillis = maxOf(
                    current.snoozedUntilEpochMillis ?: Long.MIN_VALUE,
                    requestedSnoozeUntil,
                ),
            ),
        )
    }

    override suspend fun rememberRecommendation(
        recommendation: CoachRecommendation.Action,
    ): CoachRecommendation.Action {
        require(recommendation.id.isNotBlank()) {
            "Recommendation ID must not be blank."
        }
        require(recommendation.validUntilEpochMillis > recommendation.createdAtEpochMillis) {
            "Recommendation validity must end after creation."
        }
        recommendationSnapshotDao.deleteExpiredBefore(
            recommendation.createdAtEpochMillis - RECOMMENDATION_RETENTION_MILLIS,
        )
        recommendationSnapshotDao.insertIfAbsent(recommendation.toEntity())
        return requireNotNull(recommendationSnapshotDao.getById(recommendation.id)) {
            "Recommendation snapshot could not be persisted."
        }.toModel()
    }

    override suspend fun recommendationSnapshot(
        recommendationId: String,
    ): CoachRecommendation.Action? =
        recommendationSnapshotDao.getById(recommendationId)?.toModel()

    override suspend fun recordRecommendationPublished(
        recommendationId: String,
        nowEpochMillis: Long,
    ): Boolean {
        val current = coachStateDao.get().forCurrentDay(nowEpochMillis)
        if (current.lastRecommendationId == recommendationId) return false
        coachStateDao.upsert(
            current.copy(
                lastRecommendationAtEpochMillis = nowEpochMillis,
                lastRecommendationId = recommendationId,
                notificationsSentToday = current.notificationsSentToday + 1,
            ),
        )
        return true
    }

    private fun CoachStateEntity?.forCurrentDay(nowEpochMillis: Long): CoachStateEntity {
        val today = startOfToday(nowEpochMillis)
        if (this == null || notificationDayStartEpochMillis != today) {
            return CoachStateEntity(
                lastRecommendationAtEpochMillis = this?.lastRecommendationAtEpochMillis,
                lastRecommendationId = this?.lastRecommendationId,
                snoozedUntilEpochMillis = this?.snoozedUntilEpochMillis,
                notificationDayStartEpochMillis = today,
                notificationsSentToday = 0,
            )
        }
        return this
    }

    private fun startOfToday(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun observeDayStart(): Flow<Long> = flow {
        var emittedDayStart: Long? = null
        while (true) {
            val now = timeSource.nowEpochMillis()
            val dayStart = startOfToday(now)
            if (dayStart != emittedDayStart) {
                emit(dayStart)
                emittedDayStart = dayStart
            }
            val nextDayStart = Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            delay(
                minOf(
                    (nextDayStart - now).coerceAtLeast(1_000L),
                    DAY_BOUNDARY_RECHECK_MILLIS,
                ),
            )
        }
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DAY_BOUNDARY_RECHECK_MILLIS = 15 * MILLIS_PER_MINUTE
        private const val RECOMMENDATION_RETENTION_MILLIS =
            7 * 24 * 60 * MILLIS_PER_MINUTE
    }
}
