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
import com.young.metaboliccoach.core.domain.CoachActionSuppression
import com.young.metaboliccoach.core.domain.CoachRuleEngine
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.ObservationAnalyzer
import com.young.metaboliccoach.core.domain.RapidRiseConfirmationPolicy
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachContext
import com.young.metaboliccoach.core.model.CoachReason
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
    private val glucoseRepository: GlucoseRepository,
    private val ruleEngine: CoachRuleEngine,
    private val observationAnalyzer: ObservationAnalyzer,
    private val timeSource: CoachTimeSource,
) : CoachingRepository {
    private data class DailyScope(
        val settings: CoachSettings,
        val dayStartEpochMillis: Long,
        val glucoseSourceId: String?,
    )

    private val selectedGlucoseAndSettings = combine(
        settingsRepository.observe(),
        glucoseRepository.observeLatest(),
    ) { settings, glucose ->
        settings to glucose
    }

    override fun observeCurrentRecommendation(): Flow<CoachRecommendation?> = combine(
        selectedGlucoseAndSettings,
        activityDao.observeLatest(),
        mealDao.observeLatest(),
        coachStateDao.observe(),
        timeSource.minuteTicks(),
    ) { (settings, glucose), activity, meal, state, now ->
        val currentState = state.forCurrentDay(now)
        val recentExactSourceReadings = glucose
            ?.takeIf { settings.walkingRemindersEnabled }
            ?.let { current ->
                glucoseRepository.readingsBetweenExactSource(
                    sourceId = current.sourceId,
                    startEpochMillis = (
                        current.measuredAtEpochMillis -
                            settings.staleReadingMinutes * MILLIS_PER_MINUTE
                        ).coerceAtLeast(0L),
                    endEpochMillis = current.measuredAtEpochMillis,
                )
            }.orEmpty()
        val previousGlucose = glucose?.let { current ->
            RapidRiseConfirmationPolicy.immediatePredecessor(
                readings = recentExactSourceReadings,
                latestReading = current,
            )
        }
        val evaluation = ruleEngine.evaluate(
            context = CoachContext(
                nowEpochMillis = now,
                minuteOfDay = Instant.ofEpochMilli(now)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .toSecondOfDay() / 60,
                glucose = glucose,
                activity = activity
                    ?.takeIf { it.dayStartEpochMillis == startOfToday(now) }
                    ?.toModel(),
                mostRecentMeal = meal?.toModel(),
                lastRecommendationAtEpochMillis =
                    currentState.lastRecommendationAtEpochMillis,
                lastRecommendationId = currentState.lastRecommendationId,
                snoozedUntilEpochMillis = currentState.snoozedUntilEpochMillis,
                notificationsSentToday = currentState.notificationsSentToday,
                consumedRecommendationId = currentState.consumedRecommendationId,
                previousGlucose = previousGlucose,
            ),
            settings = settings,
            allowedActionReasons = setOf(
                CoachReason.POST_MEAL_WINDOW,
                CoachReason.RAPID_GLUCOSE_RISE,
                CoachReason.PROLONGED_INACTIVITY,
            ),
        )
        val generated = evaluation.recommendation
        val inactivityAlreadyConsumed =
            generated is CoachRecommendation.Action &&
                generated.reason == CoachReason.PROLONGED_INACTIVITY &&
                interventionDao.getByRecommendationId(generated.id) != null
        val authoritativeInactivitySnapshot =
            (generated as? CoachRecommendation.Action)
                ?.takeIf { it.reason == CoachReason.PROLONGED_INACTIVITY }
                ?.let { recommendationSnapshotDao.getById(it.id)?.toModel() }
        val lastSnapshot = currentState.lastRecommendationId?.let {
            recommendationSnapshotDao.getById(it)?.toModel()
        }
        when {
            generated is CoachRecommendation.Information -> generated
            inactivityAlreadyConsumed -> null
            generated is CoachRecommendation.Action &&
                generated.reason == CoachReason.PROLONGED_INACTIVITY &&
                authoritativeInactivitySnapshot != null ->
                authoritativeInactivitySnapshot.takeIf { snapshot ->
                    currentState.lastRecommendationId == snapshot.id &&
                    snapshot.matchesCurrentCandidate(generated) &&
                        snapshot.validUntilEpochMillis > now
                }
            generated is CoachRecommendation.Action &&
                generated.id == currentState.consumedRecommendationId -> null
            generated is CoachRecommendation.Action &&
                lastSnapshot?.id == generated.id -> lastSnapshot.takeIf { snapshot ->
                    snapshot.matchesCurrentCandidate(generated) &&
                        snapshot.validUntilEpochMillis > now
                }
            generated is CoachRecommendation.Action &&
                lastSnapshot?.triggerContextId == generated.triggerContextId ->
                null
            generated is CoachRecommendation.Action -> generated
            (currentState.snoozedUntilEpochMillis ?: Long.MIN_VALUE) > now -> null
            currentState.lastRecommendationId == null -> null
            currentState.lastRecommendationId == currentState.consumedRecommendationId -> null
            evaluation.actionSuppression == CoachActionSuppression.DAILY_CAP &&
                currentState.snoozedUntilEpochMillis != null -> null
            evaluation.actionSuppression != CoachActionSuppression.COOLDOWN &&
                evaluation.actionSuppression != CoachActionSuppression.DAILY_CAP -> null
            else -> lastSnapshot?.takeIf { snapshot ->
                val candidate = evaluation.actionCandidate
                candidate != null &&
                    snapshot.id == candidate.id &&
                    snapshot.validUntilEpochMillis > now &&
                    snapshot.matchesCurrentCandidate(candidate)
            }
        }
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
        glucoseRepository.observeLatest(),
    ) { sessions, meals, settings, latestGlucose ->
        latestGlucose?.sourceId?.let { exactSourceId ->
            observationAnalyzer.analyze(
                sessions = sessions.map { it.toModel() },
                exactSourceId = exactSourceId,
                settings = settings,
                mealMarkers = meals.map { it.toModel() },
            )
        }.orEmpty()
    }

    override fun observeActiveSession(): Flow<InterventionSession?> =
        interventionDao.observeLatestActive().map { it?.toModel() }

    override suspend fun saveMealMarker(marker: MealMarker) {
        mealDao.upsert(marker.toEntity())
    }

    override suspend fun latestMealMarker(): MealMarker? = mealDao.latest()?.toModel()

    override suspend fun startSession(session: InterventionSession): InterventionSession {
        require(session.status == InterventionStatus.STARTED) {
            "A new intervention session must have STARTED status."
        }
        return interventionDao.startIfNoActive(session.toEntity()).toModel()
    }

    override suspend fun startSessionForRecommendation(
        session: InterventionSession,
        recommendationId: String,
        nowEpochMillis: Long,
    ): InterventionSession? {
        require(session.status == InterventionStatus.STARTED) {
            "A new intervention session must have STARTED status."
        }
        require(session.recommendationId == recommendationId) {
            "The session must identify the recommendation it consumes."
        }
        return interventionDao.startForRecommendationIfAvailable(
            candidate = session.toEntity(),
            recommendationId = recommendationId,
            currentDayStartEpochMillis = startOfToday(nowEpochMillis),
        )?.toModel()
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

    override suspend fun sessionForRecommendation(
        recommendationId: String,
    ): InterventionSession? = interventionDao.getByRecommendationId(recommendationId)?.toModel()

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
        val safetyProvenance = listOf(
            recommendation.glucoseSourceId,
            recommendation.safetyReadingId,
            recommendation.safetyReadingAtEpochMillis,
        )
        require(safetyProvenance.all { it != null } || safetyProvenance.all { it == null }) {
            "Recommendation safety-reading provenance must be stored together."
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

    override suspend fun publishedRecommendationSnapshot(
        recommendationId: String,
        nowEpochMillis: Long,
    ): CoachRecommendation.Action? {
        val current = coachStateDao.get().forCurrentDay(nowEpochMillis)
        if (
            current.lastRecommendationId != recommendationId ||
            current.consumedRecommendationId == recommendationId
        ) {
            return null
        }
        return recommendationSnapshotDao.getById(recommendationId)
            ?.toModel()
            ?.takeIf { it.validUntilEpochMillis > nowEpochMillis }
    }

    override suspend fun recordRecommendationPublished(
        recommendationId: String,
        nowEpochMillis: Long,
    ): Boolean {
        val authoritative = recommendationSnapshotDao.getById(recommendationId)
            ?: return false
        if (authoritative.validUntilEpochMillis <= nowEpochMillis) return false
        val current = coachStateDao.get().forCurrentDay(nowEpochMillis)
        val sameRecommendation = current.lastRecommendationId == recommendationId
        val deliveryCount = if (sameRecommendation) {
            current.deliveryCountForLastRecommendation
        } else {
            0
        }
        if (current.consumedRecommendationId == recommendationId || deliveryCount >= 2) {
            return false
        }
        if (
            sameRecommendation &&
            deliveryCount == 1 &&
            (
                current.snoozedUntilEpochMillis == null ||
                    current.snoozedUntilEpochMillis > nowEpochMillis
                )
        ) {
            return false
        }
        coachStateDao.upsert(
            current.copy(
                lastRecommendationAtEpochMillis = nowEpochMillis,
                lastRecommendationId = recommendationId,
                notificationsSentToday = current.notificationsSentToday + 1,
                deliveryCountForLastRecommendation = deliveryCount + 1,
                snoozedUntilEpochMillis = if (sameRecommendation) {
                    current.snoozedUntilEpochMillis
                } else {
                    null
                },
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
                deliveryCountForLastRecommendation = this?.deliveryCountForLastRecommendation ?: 0,
                consumedRecommendationId = this?.consumedRecommendationId,
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

    private fun CoachRecommendation.Action.matchesCurrentCandidate(
        candidate: CoachRecommendation.Action,
    ): Boolean {
        if (reason != candidate.reason || glucoseSourceId != candidate.glucoseSourceId) {
            return false
        }
        return when (reason) {
            CoachReason.RAPID_GLUCOSE_RISE ->
                triggerContextId == candidate.triggerContextId &&
                safetyReadingId == candidate.safetyReadingId &&
                safetyReadingAtEpochMillis == candidate.safetyReadingAtEpochMillis &&
                algorithmVersion == candidate.algorithmVersion
            CoachReason.PROLONGED_INACTIVITY ->
                id == candidate.id &&
                algorithmVersion == candidate.algorithmVersion &&
                triggerContextId == candidate.triggerContextId &&
                triggerAtEpochMillis == candidate.triggerAtEpochMillis
            else -> triggerContextId == candidate.triggerContextId
        }
    }

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
