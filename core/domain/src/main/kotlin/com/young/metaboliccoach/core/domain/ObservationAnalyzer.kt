package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.PersonalObservation
import com.young.metaboliccoach.core.model.PersonalObservationKind

class ObservationAnalyzer {
    fun analyze(
        sessions: List<InterventionSession>,
        exactSourceId: String,
        settings: CoachSettings,
        mealMarkers: List<MealMarker> = emptyList(),
    ): List<PersonalObservation> {
        require(exactSourceId.isNotBlank()) { "An exact glucose source is required." }
        val sourceSessions = sessions.filter {
            it.baselineGlucoseSourceId == exactSourceId &&
                it.followUpGlucoseSourceId == exactSourceId
        }
        val eligibleOutcomes = eligibleOutcomes(
            sessions = sourceSessions,
            mealMarkers = mealMarkers,
        )
        val effectObservations = InterventionType.entries.mapNotNull { type ->
            effectObservation(
                sessions = eligibleOutcomes.filter { it.session.type == type },
                minimumSamples = settings.minimumObservationSamples,
            )
        }
        val timingCandidates = timingCandidates(
            eligibleOutcomes = eligibleOutcomes,
            mealMarkers = mealMarkers,
            followUpDelayBucketMinutes = settings.followUpDelayBucketMinutes,
            baselineGlucoseBandMgDl = settings.baselineGlucoseBandMgDl,
        )
        val timingMinimum = maxOf(
            settings.minimumObservationSamples,
            settings.minimumTimingBucketSamples,
        )
        val interventionTiming = timingObservations(
            candidates = timingCandidates.filter {
                it.session.recommendationReason != CoachReason.POST_MEAL_WINDOW
            },
            minimumSamples = timingMinimum,
            minimumComparableBuckets = settings.minimumComparableTimingBuckets,
            bucketMinutes = settings.interventionTimingBucketMinutes,
            kind = PersonalObservationKind.INTERVENTION_TIMING,
            totalSessionCount = sourceSessions.size,
        )
        val postMealTiming = timingObservations(
            candidates = timingCandidates.filter {
                it.session.recommendationReason == CoachReason.POST_MEAL_WINDOW
            },
            minimumSamples = timingMinimum,
            minimumComparableBuckets = settings.minimumComparableTimingBuckets,
            bucketMinutes = settings.postMealTimingBucketMinutes,
            kind = PersonalObservationKind.POST_MEAL_ACTIVITY_TIMING,
            totalSessionCount = sourceSessions.size,
        )
        return effectObservations + interventionTiming + postMealTiming
    }

    private fun effectObservation(
        sessions: List<EligibleOutcome>,
        minimumSamples: Int,
    ): PersonalObservation? {
        if (sessions.size < minimumSamples) return null
        val changes = sessions.map(EligibleOutcome::changeMgDl).sorted()
        val median = changes.median()
        val medianDelayMinutes = sessions
            .map(EligibleOutcome::followUpDelayMinutes)
            .sorted()
            .median()
        val type = sessions.first().session.type
        val activityName = type.activityName()
        val direction = when {
            median < 0 -> "lower"
            median > 0 -> "higher"
            else -> "similar"
        }
        return PersonalObservation(
            interventionType = type,
            sampleCount = changes.size,
            medianChangeMgDl = median,
            text = "Your previous recorded $activityName were typically followed by $direction " +
                "glucose around $medianDelayMinutes minutes after completion. Other factors may " +
                "explain this pattern. This is a personal observation, not medical advice.",
            kind = PersonalObservationKind.ACTIVITY_EFFECT,
            excludedSampleCount = 0,
            sampleWindowStartEpochMillis =
                sessions.minOf(EligibleOutcome::startedAtEpochMillis),
            sampleWindowEndEpochMillis =
                sessions.maxOf(EligibleOutcome::followUpReadingAtEpochMillis),
        )
    }

    private fun timingObservations(
        candidates: List<TimingCandidate>,
        minimumSamples: Int,
        minimumComparableBuckets: Int,
        bucketMinutes: Int,
        kind: PersonalObservationKind,
        totalSessionCount: Int,
    ): List<PersonalObservation> {
        val cohortWinners = candidates
            .groupBy(TimingCandidate::cohort)
            .values
            .mapNotNull { cohort ->
                timingObservationForCohort(
                    cohort = cohort,
                    minimumSamples = minimumSamples,
                    minimumComparableBuckets = minimumComparableBuckets,
                    bucketMinutes = bucketMinutes,
                    kind = kind,
                    totalSessionCount = totalSessionCount,
                )
            }
        return cohortWinners
            .groupBy { it.observation.interventionType to it.observation.triggerReason }
            .values
            .map { observations ->
                observations.maxWith(
                    compareBy<TimingObservationCandidate> {
                        it.observation.comparisonSampleCount ?: 0
                    }.thenBy { it.observation.sampleCount }
                        .thenBy { it.observation.sampleWindowEndEpochMillis ?: Long.MIN_VALUE }
                        .thenByDescending {
                            it.observation.timingBucketStartMinutes ?: Int.MAX_VALUE
                        },
                ).observation
            }
            .sortedWith(
                compareBy<PersonalObservation> { it.interventionType.ordinal }
                    .thenBy { it.triggerReason?.ordinal ?: Int.MAX_VALUE },
            )
    }

    private fun timingObservationForCohort(
        cohort: List<TimingCandidate>,
        minimumSamples: Int,
        minimumComparableBuckets: Int,
        bucketMinutes: Int,
        kind: PersonalObservationKind,
        totalSessionCount: Int,
    ): TimingObservationCandidate? {
        val buckets = cohort
            .groupBy { candidate ->
                candidate.triggerDelayMinutes / bucketMinutes * bucketMinutes
            }
            .mapNotNull { (startMinute, values) ->
                values.takeIf { it.size >= minimumSamples }?.let {
                    val sortedChanges = it.map(TimingCandidate::changeMgDl).sorted()
                    TimingBucket(
                        startMinute = startMinute,
                        sessions = it,
                        medianChange = sortedChanges.median(),
                        lowerQuartile = sortedChanges.quartile(1),
                        upperQuartile = sortedChanges.quartile(3),
                    )
                }
            }
            .sortedBy(TimingBucket::startMinute)
        if (buckets.size < minimumComparableBuckets) return null
        val lowestMedian = buckets.minOf(TimingBucket::medianChange)
        val winners = buckets.filter { it.medianChange == lowestMedian }
        if (winners.size != 1) return null
        val winner = winners.single()
        val comparators = buckets.filterNot { it === winner }
        if (comparators.any { winner.upperQuartile >= it.lowerQuartile }) return null

        val representative = winner.sessions.first()
        val type = representative.session.type
        val reason = representative.session.recommendationReason ?: return null
        val algorithmVersion =
            representative.session.recommendationAlgorithmVersion ?: return null
        val endExclusive = winner.startMinute + bucketMinutes
        val comparedSessions = buckets.sumOf { it.sessions.size }
        val timingDescription = when (kind) {
            PersonalObservationKind.POST_MEAL_ACTIVITY_TIMING ->
                "${winner.startMinute}–${endExclusive - 1} minutes after a marked meal"
            PersonalObservationKind.INTERVENTION_TIMING ->
                "${winner.startMinute}–${endExclusive - 1} minutes after the recorded " +
                    "${reason.triggerLabel()} trigger"
            PersonalObservationKind.ACTIVITY_EFFECT ->
                error("Activity-effect observations do not use timing cohorts.")
        }
        val observation = PersonalObservation(
            interventionType = type,
            sampleCount = winner.sessions.size,
            medianChangeMgDl = winner.medianChange,
            text = "In comparable recorded ${type.activityName()}, the lowest observed median " +
                "baseline-to-follow-up glucose change occurred when started " +
                "$timingDescription (${winner.sessions.size} sessions). Other factors may " +
                "explain this pattern. This is not medical advice.",
            kind = kind,
            triggerReason = reason,
            timingBucketStartMinutes = winner.startMinute,
            timingBucketEndExclusiveMinutes = endExclusive,
            comparisonSampleCount = comparedSessions,
            excludedSampleCount = (totalSessionCount - cohort.size).coerceAtLeast(0),
            algorithmVersion = algorithmVersion,
            sampleWindowStartEpochMillis =
                buckets.minOf { bucket ->
                    bucket.sessions.minOf(TimingCandidate::startedAtEpochMillis)
                },
            sampleWindowEndEpochMillis =
                buckets.maxOf { bucket ->
                    bucket.sessions.maxOf(TimingCandidate::followUpReadingAtEpochMillis)
                },
        )
        return TimingObservationCandidate(observation)
    }

    private fun eligibleOutcomes(
        sessions: List<InterventionSession>,
        mealMarkers: List<MealMarker>,
    ): List<EligibleOutcome> = sessions
        .asSequence()
        .filter { it.status == InterventionStatus.COMPLETED }
        .mapNotNull { session ->
            val before = session.baselineGlucoseMgDl ?: return@mapNotNull null
            val after = session.glucoseAfterMgDl ?: return@mapNotNull null
            val lowThreshold = session.lowGlucoseThresholdMgDlAtStart
                ?: return@mapNotNull null
            if (after < lowThreshold) return@mapNotNull null
            val baselineReadingId = session.baselineGlucoseReadingId
                ?: return@mapNotNull null
            val baselineAt = session.baselineGlucoseMeasuredAtEpochMillis
                ?: return@mapNotNull null
            val baselineSource = session.baselineGlucoseSourceId
                ?: return@mapNotNull null
            val endedAt = session.endedAtEpochMillis ?: return@mapNotNull null
            val dueAt = session.followUpDueAtEpochMillis ?: return@mapNotNull null
            val readingAt = session.followUpReadingAtEpochMillis ?: return@mapNotNull null
            val followUpReadingId = session.followUpGlucoseReadingId
                ?: return@mapNotNull null
            val followUpSource = session.followUpGlucoseSourceId
                ?: return@mapNotNull null
            val finalizedAt = session.followUpFinalizedAtEpochMillis
                ?: return@mapNotNull null
            if (baselineReadingId == followUpReadingId) return@mapNotNull null
            if (baselineSource != followUpSource) return@mapNotNull null
            if (
                baselineAt > session.startedAtEpochMillis ||
                endedAt < session.startedAtEpochMillis ||
                dueAt < endedAt ||
                readingAt < dueAt ||
                finalizedAt < readingAt
            ) {
                return@mapNotNull null
            }
            val overlapsAnotherSession = sessions.any { other ->
                other.id != session.id &&
                    other.startedAtEpochMillis <= readingAt &&
                    (other.endedAtEpochMillis ?: Long.MAX_VALUE) >=
                    session.startedAtEpochMillis
            }
            if (overlapsAnotherSession) return@mapNotNull null
            val hasMealAfterStart = mealMarkers.any {
                it.occurredAtEpochMillis > session.startedAtEpochMillis &&
                    it.occurredAtEpochMillis <= readingAt
            }
            if (hasMealAfterStart) return@mapNotNull null
            EligibleOutcome(
                session = session,
                changeMgDl = after - before,
                followUpDelayMinutes =
                    ((readingAt - endedAt) / MILLIS_PER_MINUTE).toInt(),
                startedAtEpochMillis = session.startedAtEpochMillis,
                followUpReadingAtEpochMillis = readingAt,
            )
        }
        .toList()

    private fun timingCandidates(
        eligibleOutcomes: List<EligibleOutcome>,
        mealMarkers: List<MealMarker>,
        followUpDelayBucketMinutes: Int,
        baselineGlucoseBandMgDl: Int,
    ): List<TimingCandidate> {
        val candidates = eligibleOutcomes.mapNotNull { eligible ->
            val session = eligible.session
            val recommendationId = session.recommendationId
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val reason = session.recommendationReason ?: return@mapNotNull null
            val algorithmVersion = session.recommendationAlgorithmVersion
                ?.takeIf { it > 0 } ?: return@mapNotNull null
            val recommendationCreatedAt =
                session.recommendationCreatedAtEpochMillis ?: return@mapNotNull null
            val recommendationValidUntil =
                session.recommendationValidUntilEpochMillis ?: return@mapNotNull null
            val triggerContextId = session.triggerContextId
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val triggerAt = session.triggerAtEpochMillis ?: return@mapNotNull null
            val baselineRate = session.baselineEffectiveRateMgDlPerMinute
                ?.takeIf(Double::isFinite) ?: return@mapNotNull null
            val lowThreshold = session.lowGlucoseThresholdMgDlAtStart
                ?: return@mapNotNull null
            val followUpGlucose = session.glucoseAfterMgDl ?: return@mapNotNull null
            val dueAt = session.followUpDueAtEpochMillis ?: return@mapNotNull null
            val endedAt = session.endedAtEpochMillis ?: return@mapNotNull null
            if (
                recommendationId.isEmpty() ||
                recommendationCreatedAt > session.startedAtEpochMillis ||
                recommendationValidUntil <= session.startedAtEpochMillis ||
                triggerAt > session.startedAtEpochMillis ||
                followUpGlucose < lowThreshold
            ) {
                return@mapNotNull null
            }
            val mealAtTrigger = mealMarkers.firstOrNull {
                it.id == triggerContextId && it.occurredAtEpochMillis == triggerAt
            }
            if (reason == CoachReason.POST_MEAL_WINDOW && mealAtTrigger == null) {
                return@mapNotNull null
            }
            val additionalMeal = mealMarkers.any {
                it.occurredAtEpochMillis > triggerAt &&
                    it.occurredAtEpochMillis <= eligible.followUpReadingAtEpochMillis
            }
            if (additionalMeal) return@mapNotNull null
            val triggerDelayMinutes =
                ((session.startedAtEpochMillis - triggerAt) / MILLIS_PER_MINUTE).toInt()
            val plannedFollowUpMinutes = ((dueAt - endedAt) / MILLIS_PER_MINUTE).toInt()
            val baseline = session.baselineGlucoseMgDl ?: return@mapNotNull null
            TimingCandidate(
                session = session,
                changeMgDl = eligible.changeMgDl,
                triggerDelayMinutes = triggerDelayMinutes,
                startedAtEpochMillis = eligible.startedAtEpochMillis,
                followUpReadingAtEpochMillis = eligible.followUpReadingAtEpochMillis,
                cohort = TimingCohort(
                    type = session.type,
                    reason = reason,
                    algorithmVersion = algorithmVersion,
                    exactSourceId = session.baselineGlucoseSourceId ?: return@mapNotNull null,
                    targetDurationMinutes = session.targetDurationMinutes,
                    targetFloors = session.targetFloors,
                    plannedFollowUpMinutes = plannedFollowUpMinutes,
                    actualFollowUpDelayBucket =
                        eligible.followUpDelayMinutes / followUpDelayBucketMinutes,
                    baselineGlucoseBand = baseline / baselineGlucoseBandMgDl,
                    baselineRateDirection = baselineRate.direction(),
                    lowGlucoseThresholdMgDl = lowThreshold,
                ),
                triggerDeduplicationKey =
                    "$reason|$algorithmVersion|${session.type}|$triggerContextId",
            )
        }
        return candidates
            .groupBy(TimingCandidate::triggerDeduplicationKey)
            .values
            .map { duplicates ->
                duplicates.minWith(
                    compareBy<TimingCandidate>(TimingCandidate::startedAtEpochMillis)
                        .thenBy { it.session.id },
                )
            }
    }

    private data class EligibleOutcome(
        val session: InterventionSession,
        val changeMgDl: Int,
        val followUpDelayMinutes: Int,
        val startedAtEpochMillis: Long,
        val followUpReadingAtEpochMillis: Long,
    )

    private data class TimingCandidate(
        val session: InterventionSession,
        val changeMgDl: Int,
        val triggerDelayMinutes: Int,
        val startedAtEpochMillis: Long,
        val followUpReadingAtEpochMillis: Long,
        val cohort: TimingCohort,
        val triggerDeduplicationKey: String,
    )

    private data class TimingCohort(
        val type: InterventionType,
        val reason: CoachReason,
        val algorithmVersion: Int,
        val exactSourceId: String,
        val targetDurationMinutes: Int?,
        val targetFloors: Int?,
        val plannedFollowUpMinutes: Int,
        val actualFollowUpDelayBucket: Int,
        val baselineGlucoseBand: Int,
        val baselineRateDirection: Int,
        val lowGlucoseThresholdMgDl: Int,
    )

    private data class TimingBucket(
        val startMinute: Int,
        val sessions: List<TimingCandidate>,
        val medianChange: Int,
        val lowerQuartile: Int,
        val upperQuartile: Int,
    )

    private data class TimingObservationCandidate(
        val observation: PersonalObservation,
    )

    private fun List<Int>.median(): Int {
        val middle = size / 2
        return if (size % 2 == 1) {
            this[middle]
        } else {
            (this[middle - 1] + this[middle]) / 2
        }
    }

    private fun List<Int>.quartile(numerator: Int): Int =
        this[(lastIndex * numerator) / 4]

    private fun Double.direction(): Int = when {
        this < 0.0 -> -1
        this > 0.0 -> 1
        else -> 0
    }

    private fun InterventionType.activityName(): String = when (this) {
        InterventionType.WALK -> "walks"
        InterventionType.STAIRS -> "stair sessions"
    }

    private fun CoachReason.triggerLabel(): String = when (this) {
        CoachReason.RAPID_GLUCOSE_RISE -> "rapid-rise"
        CoachReason.PROLONGED_INACTIVITY -> "inactivity"
        CoachReason.POST_MEAL_WINDOW -> "marked-meal"
        CoachReason.LOW_GLUCOSE_SAFETY,
        CoachReason.FALLING_GLUCOSE_SAFETY,
        CoachReason.STALE_GLUCOSE_DATA,
        -> "coaching"
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
