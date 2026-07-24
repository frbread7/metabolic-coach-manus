package com.young.metaboliccoach.background

import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachedExerciseActionPolicy
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionStatus
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class QuickActionHandler @Inject constructor(
    private val coachingRepository: CoachingRepository,
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository,
    private val followUpScheduler: InterventionFollowUpScheduler,
    private val timeSource: CoachTimeSource,
) {
    suspend fun handle(command: QuickActionCommand): CommandHandlingResult {
        val now = timeSource.nowEpochMillis()
        val settings = settingsRepository.observe().first()
        val maximumAgeMillis =
            settings.quickActionExpiryMinutes * MILLIS_PER_MINUTE
        val commandExpired = command.createdAtEpochMillis < now - maximumAgeMillis
        if (commandExpired && command.type != QuickActionType.MARK_COMPLETED) {
            return CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED)
        }
        val interventionType = when (command.type) {
            QuickActionType.START_WALK -> InterventionType.WALK
            QuickActionType.START_STAIRS -> InterventionType.STAIRS
            QuickActionType.SNOOZE,
            QuickActionType.MARK_COMPLETED,
            -> null
        }
        val recommendation = command.recommendationId?.let { recommendationId ->
            coachingRepository.recommendationSnapshot(recommendationId)
                ?: return CommandHandlingResult.Rejected(
                    SessionCommandOutcome.REJECTED_EXPIRED,
                )
        }
        if (
            recommendation != null &&
            (
                command.createdAtEpochMillis < recommendation.createdAtEpochMillis ||
                    command.createdAtEpochMillis >= recommendation.validUntilEpochMillis
            )
        ) {
            return CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED)
        }
        if (
            recommendation != null &&
            (
                interventionType != recommendation.interventionType ||
                    !command.matches(recommendation)
            )
        ) {
            return CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT)
        }
        val eventAt = command.createdAtEpochMillis.coerceAtMost(now)
        val baseline = interventionType?.let {
            glucoseNear(
                eventAtEpochMillis = eventAt,
                toleranceMinutes = settings.staleReadingMinutes,
            )
        }
        if (
            recommendation != null &&
            interventionType != null &&
            !CoachedExerciseActionPolicy.canStart(
                reading = baseline,
                settings = settings,
                nowEpochMillis = eventAt,
            )
        ) {
            return CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_UNSAFE)
        }

        return when (command.type) {
            QuickActionType.START_WALK ->
                startSession(
                    command,
                    InterventionType.WALK,
                    eventAt,
                    settings,
                    baseline,
                    recommendation,
                )
            QuickActionType.START_STAIRS ->
                startSession(
                    command,
                    InterventionType.STAIRS,
                    eventAt,
                    settings,
                    baseline,
                    recommendation,
                )
            QuickActionType.SNOOZE -> {
                coachingRepository.snooze(eventAt)
                CommandHandlingResult.Applied
            }
            QuickActionType.MARK_COMPLETED ->
                completeSession(
                    command = command,
                    eventAt = eventAt,
                    followUpMinutes = settings.interventionFollowUpMinutes,
                    commandExpired = commandExpired,
                )
        }
    }

    private suspend fun startSession(
        command: QuickActionCommand,
        type: InterventionType,
        eventAt: Long,
        settings: CoachSettings,
        baseline: GlucoseReading?,
        recommendation: CoachRecommendation.Action?,
    ): CommandHandlingResult {
        val sessionId = command.sessionId ?: command.id
        coachingRepository.latestActiveSession()?.let { active ->
            return if (active.id == sessionId) {
                CommandHandlingResult.Applied
            } else {
                CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT)
            }
        }
        val stored = coachingRepository.startSession(
            InterventionSession(
                id = sessionId,
                type = type,
                status = InterventionStatus.STARTED,
                startedAtEpochMillis = eventAt,
                endedAtEpochMillis = null,
                targetDurationMinutes = if (type == InterventionType.WALK) {
                    recommendation?.durationMinutes ?: settings.walkingDurationMinutes
                } else {
                    null
                },
                targetFloors = if (type == InterventionType.STAIRS) {
                    recommendation?.targetFloors ?: settings.stairTargetFloors
                } else {
                    null
                },
                baselineGlucoseMgDl = baseline?.valueMgDl,
                baselineGlucoseReadingId = baseline?.id,
                baselineGlucoseMeasuredAtEpochMillis = baseline?.measuredAtEpochMillis,
                baselineGlucoseSourceId = baseline?.sourceId,
                glucoseAfterMgDl = null,
                recommendationId = recommendation?.id,
                recommendationReason = recommendation?.reason,
                recommendationAlgorithmVersion = recommendation?.algorithmVersion,
                recommendationCreatedAtEpochMillis = recommendation?.createdAtEpochMillis,
                recommendationValidUntilEpochMillis = recommendation?.validUntilEpochMillis,
                triggerContextId = recommendation?.triggerContextId,
                triggerAtEpochMillis = recommendation?.triggerAtEpochMillis,
                baselineEffectiveRateMgDlPerMinute = baseline?.let {
                    it.rateMgDlPerMinute ?: it.trend.approximateRateMgDlPerMinute
                },
                lowGlucoseThresholdMgDlAtStart = settings.lowGlucoseThresholdMgDl,
            ),
        )
        return if (stored.id == sessionId) {
            CommandHandlingResult.Applied
        } else {
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT)
        }
    }

    private suspend fun completeSession(
        command: QuickActionCommand,
        eventAt: Long,
        followUpMinutes: Int,
        commandExpired: Boolean,
    ): CommandHandlingResult {
        val sessionId = command.sessionId ?: return CommandHandlingResult.Applied
        val target = coachingRepository.session(sessionId) ?: return if (commandExpired) {
            CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_EXPIRED)
        } else {
            CommandHandlingResult.Deferred
        }
        if (target.status == InterventionStatus.COMPLETED) {
            return CommandHandlingResult.Applied
        }
        if (target.status != InterventionStatus.STARTED) {
            return CommandHandlingResult.Rejected(SessionCommandOutcome.REJECTED_CONFLICT)
        }
        val effectiveEnd = eventAt.coerceAtLeast(target.startedAtEpochMillis)
        val completed = coachingRepository.completeSession(
            sessionId = sessionId,
            endedAtEpochMillis = effectiveEnd,
            followUpDueAtEpochMillis =
                effectiveEnd + followUpMinutes * MILLIS_PER_MINUTE,
        ) ?: return CommandHandlingResult.Deferred
        followUpScheduler.schedule(completed)
        return CommandHandlingResult.Applied
    }

    private suspend fun glucoseNear(
        eventAtEpochMillis: Long,
        toleranceMinutes: Int,
    ): GlucoseReading? {
        val toleranceMillis = toleranceMinutes * MILLIS_PER_MINUTE
        return glucoseRepository.readingsBetween(
            startEpochMillis = eventAtEpochMillis - toleranceMillis,
            endEpochMillis = eventAtEpochMillis,
        ).maxWithOrNull(
            compareBy<GlucoseReading> { it.measuredAtEpochMillis }.thenBy { it.id },
        )
    }

    private fun QuickActionCommand.matches(
        recommendation: CoachRecommendation.Action,
    ): Boolean =
        recommendationValidUntilEpochMillis
            ?.let { it == recommendation.validUntilEpochMillis } != false &&
            recommendationReason?.let { it == recommendation.reason } != false &&
            recommendationAlgorithmVersion
                ?.let { it == recommendation.algorithmVersion } != false &&
            recommendationCreatedAtEpochMillis
                ?.let { it == recommendation.createdAtEpochMillis } != false &&
            triggerContextId?.let { it == recommendation.triggerContextId } != false &&
            triggerAtEpochMillis?.let { it == recommendation.triggerAtEpochMillis } != false

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

sealed interface CommandHandlingResult {
    data object Applied : CommandHandlingResult
    data object Deferred : CommandHandlingResult
    data class Rejected(val outcome: SessionCommandOutcome) : CommandHandlingResult
}
