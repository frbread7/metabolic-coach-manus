package com.young.metaboliccoach.wear.ui

internal enum class WearActionRejection(val message: String) {
    SESSION_BUSY("An activity is already active or syncing."),
    EXPIRED_RECOMMENDATION("This coaching prompt has expired."),
    STALE_RECOMMENDATION("This coaching prompt is no longer current."),
    NO_ACTIVE_SESSION("There is no active activity to complete."),
    START_NOT_TRANSPORTED("Wait for the activity start to sync, then try again."),
    PERSISTENCE_FAILED("Couldn't save this action. Try again."),
}

internal object WearActionPolicy {
    fun startRejection(
        blocksNewSession: Boolean,
        recommendationId: String?,
        recommendationValidUntilEpochMillis: Long?,
        effectiveRecommendationId: String?,
        nowEpochMillis: Long,
    ): WearActionRejection? = when {
        blocksNewSession -> WearActionRejection.SESSION_BUSY
        recommendationId != null &&
            (
                recommendationValidUntilEpochMillis == null ||
                    nowEpochMillis >= recommendationValidUntilEpochMillis
            ) -> WearActionRejection.EXPIRED_RECOMMENDATION
        recommendationId != null && recommendationId != effectiveRecommendationId ->
            WearActionRejection.STALE_RECOMMENDATION
        else -> null
    }
}

