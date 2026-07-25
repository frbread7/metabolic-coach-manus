package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DailySummary
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.PersonalObservation
import com.young.metaboliccoach.core.model.ProviderStatus
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.WatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GlucoseRepository {
    fun observeLatest(): Flow<GlucoseReading?>
    fun observeProviderStatus(): Flow<ProviderStatus>
    fun observeProviderState(): Flow<GlucoseProviderState> =
        flowOf(GlucoseProviderState.Idle)
    fun observeAvailableOrigins(): Flow<List<GlucoseDataOrigin>>
    suspend fun readingsBetween(startEpochMillis: Long, endEpochMillis: Long): List<GlucoseReading>
    suspend fun readingsBetweenExactSource(
        sourceId: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReading>
    suspend fun refresh()
    suspend fun refreshExactSource(sourceId: String)
    suspend fun clearRuntimeCaches()
}

interface ActivityRepository {
    fun observeToday(): Flow<ActivitySnapshot?>
    suspend fun refresh()
}

interface SettingsRepository {
    fun observe(): Flow<CoachSettings>
    suspend fun update(settings: CoachSettings)
    suspend fun reset()
}

interface NightscoutSettingsRepository {
    fun observeNightscoutSettings(): Flow<NightscoutSettings>
    suspend fun updateNightscoutSettings(settings: NightscoutSettings)
}

interface PersonalDataRepository {
    suspend fun writeJsonExport(
        exportedAtEpochMillis: Long,
        destination: Appendable,
    )

    suspend fun eraseAll()
}

interface CoachingRepository {
    fun observeCurrentRecommendation(): Flow<CoachRecommendation?>
    fun observeTodaySummary(): Flow<DailySummary>
    fun observePersonalObservations(): Flow<List<PersonalObservation>>
    fun observeActiveSession(): Flow<InterventionSession?>
    suspend fun saveMealMarker(marker: MealMarker)
    suspend fun startSession(session: InterventionSession): InterventionSession
    suspend fun completeSession(
        sessionId: String,
        endedAtEpochMillis: Long,
        followUpDueAtEpochMillis: Long,
    ): InterventionSession?
    suspend fun session(sessionId: String): InterventionSession?
    suspend fun latestActiveSession(): InterventionSession?
    suspend fun pendingFollowUpSessions(): List<InterventionSession>
    suspend fun finalizeFollowUp(
        sessionId: String,
        glucoseMgDl: Int?,
        readingAtEpochMillis: Long?,
        readingId: String?,
        sourceId: String?,
        finalizedAtEpochMillis: Long,
    ): Boolean
    suspend fun snooze(nowEpochMillis: Long)
    suspend fun rememberRecommendation(
        recommendation: CoachRecommendation.Action,
    ): CoachRecommendation.Action
    suspend fun recommendationSnapshot(
        recommendationId: String,
    ): CoachRecommendation.Action?
    suspend fun recordRecommendationPublished(
        recommendationId: String,
        nowEpochMillis: Long,
    ): Boolean
}

interface WatchSyncRepository {
    fun observeWatchState(): Flow<WatchState?>
    suspend fun publish(state: WatchState)
    suspend fun enqueue(command: QuickActionCommand)
    fun observeCommands(): Flow<QuickActionCommand>
    suspend fun acknowledge(commandId: String)
}
