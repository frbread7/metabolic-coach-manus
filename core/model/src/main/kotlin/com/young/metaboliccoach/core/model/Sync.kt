package com.young.metaboliccoach.core.model

data class WatchState(
    val glucose: GlucoseReading?,
    val activity: ActivitySnapshot?,
    val recommendation: CoachRecommendation?,
    val settings: CoachSettings,
    val phoneBatteryPercent: Int?,
    val generatedAtEpochMillis: Long,
    val activeSession: InterventionSession? = null,
    val phoneInstanceId: String? = null,
    val stateRevision: Long? = null,
    val lastSessionCommandAck: SessionCommandAck? = null,
    val dataResetId: String? = null,
)

enum class QuickActionType {
    START_WALK,
    START_STAIRS,
    SNOOZE,
    MARK_COMPLETED,
}

data class QuickActionCommand(
    val id: String,
    val type: QuickActionType,
    val createdAtEpochMillis: Long,
    val sessionId: String? = null,
    val recommendationId: String? = null,
    val recommendationValidUntilEpochMillis: Long? = null,
    val recommendationReason: CoachReason? = null,
    val recommendationAlgorithmVersion: Int? = null,
    val recommendationCreatedAtEpochMillis: Long? = null,
    val triggerContextId: String? = null,
    val triggerAtEpochMillis: Long? = null,
    val glucoseSourceId: String? = null,
    val safetyReadingId: String? = null,
    val safetyReadingAtEpochMillis: Long? = null,
    val dataResetId: String? = null,
)

enum class SessionCommandOutcome {
    APPLIED,
    REJECTED_EXPIRED,
    REJECTED_UNSAFE,
    REJECTED_CONFLICT,
}

data class SessionCommandAck(
    val commandId: String,
    val sessionId: String?,
    val outcome: SessionCommandOutcome,
)
