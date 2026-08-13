package com.young.metaboliccoach.background

import android.content.Context
import android.os.BatteryManager
import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.domain.ActionDisplayDeadlinePolicy
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.effectiveRecommendation
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.PersonalDataRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.WatchState
import com.young.metaboliccoach.sync.PhoneSyncMetadataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Owns the phone's refresh-coach-publish flow.
 *
 * Provider retrieval is cancellable and preemptible but still cannot cross an erase boundary.
 * Snapshot generation and publication remain serialized with commands and other local mutations.
 */
@Singleton
class PhoneRefreshCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val glucoseRepository: GlucoseRepository,
    private val activityRepository: ActivityRepository,
    private val settingsRepository: SettingsRepository,
    private val coachingRepository: CoachingRepository,
    private val watchSyncRepository: WatchSyncRepository,
    private val notificationManager: CoachNotificationManager,
    private val followUpScheduler: InterventionFollowUpScheduler,
    private val syncMetadataStore: PhoneSyncMetadataStore,
    private val syncScheduler: SyncScheduler,
    private val commandProcessor: PhoneCommandProcessor,
    private val personalDataRepository: PersonalDataRepository,
    private val mutationGate: PhoneDataMutationGate,
) {
    suspend fun refresh(refreshProviders: Boolean) {
        if (refreshProviders) {
            mutationGate.withPreemptibleProviderLock {
                coroutineScope {
                    val glucoseRefresh = async { glucoseRepository.refresh() }
                    val activityRefresh = async { activityRepository.refresh() }
                    glucoseRefresh.await()
                    activityRefresh.await()
                }
            }
        }

        mutationGate.withLock {
            followUpScheduler.scheduleAll(coachingRepository.pendingFollowUpSessions())
            val activeSession = coachingRepository.latestActiveSession()
            val generatedRecommendation = if (activeSession == null) {
                coachingRepository.observeCurrentRecommendation().first()
            } else {
                null
            }
            val recommendation = if (generatedRecommendation is CoachRecommendation.Action) {
                coachingRepository.rememberRecommendation(generatedRecommendation)
            } else {
                generatedRecommendation
            }
            val publicationNow = System.currentTimeMillis()
            val glucose = glucoseRepository.observeLatest().first()
            val activity = activityRepository.observeToday().first()
            val settings = settingsRepository.observe().first()
            val syncMetadata = syncMetadataStore.nextPublication()
            val capturedState = WatchState(
                glucose = glucose,
                activity = activity,
                recommendation = recommendation,
                settings = settings,
                phoneBatteryPercent = phoneBatteryPercent(),
                generatedAtEpochMillis = publicationNow,
                activeSession = activeSession,
                phoneInstanceId = syncMetadata.phoneInstanceId,
                stateRevision = syncMetadata.stateRevision,
                lastSessionCommandAck = syncMetadata.lastSessionCommandAck,
                dataResetId = syncMetadata.dataResetId,
            )
            val effectiveRecommendation = capturedState.effectiveRecommendation(publicationNow)
            watchSyncRepository.publish(
                capturedState.copy(recommendation = effectiveRecommendation),
            )

            if (effectiveRecommendation is CoachRecommendation.Action) {
                // The successful persistent watch-state publication is the canonical prompt
                // delivery. The phone notification below is an optional local mirror.
                val newlyPublished = coachingRepository.recordRecommendationPublished(
                    recommendationId = effectiveRecommendation.id,
                    nowEpochMillis = publicationNow,
                )
                if (newlyPublished) {
                    notificationManager.showCoachPrompt(
                        recommendation = effectiveRecommendation,
                        nowEpochMillis = publicationNow,
                        displayUntilEpochMillis =
                            ActionDisplayDeadlinePolicy.displayUntilEpochMillis(
                                recommendation = effectiveRecommendation,
                                settings = settings,
                                nowEpochMillis = publicationNow,
                            ),
                    )
                }
            } else {
                notificationManager.clearCoachPrompt()
            }
        }
    }

    suspend fun eraseLocalData(): LocalDataEraseResult = mutationGate.withLock {
        withContext(NonCancellable) {
            var backgroundWorkCancelled = true
            val pendingFollowUps = try {
                coachingRepository.pendingFollowUpSessions()
            } catch (_: Exception) {
                backgroundWorkCancelled = false
                emptyList()
            }
            try {
                syncScheduler.cancelAll()
            } catch (_: Exception) {
                backgroundWorkCancelled = false
            }
            try {
                followUpScheduler.cancelAll(pendingFollowUps)
            } catch (_: Exception) {
                backgroundWorkCancelled = false
            }

            val runtimeCachesCleared = try {
                glucoseRepository.clearRuntimeCaches()
                true
            } catch (_: Exception) {
                false
            }
            val resetMetadata = syncMetadataStore.beginDataReset()
            commandProcessor.clearDeferredForDataReset()
            personalDataRepository.eraseAll()
            notificationManager.clearCoachPrompt()

            val resetState = WatchState(
                glucose = null,
                activity = null,
                recommendation = null,
                settings = DefaultCoachSettings.create(),
                phoneBatteryPercent = phoneBatteryPercent(),
                generatedAtEpochMillis = System.currentTimeMillis(),
                activeSession = null,
                phoneInstanceId = resetMetadata.phoneInstanceId,
                stateRevision = resetMetadata.stateRevision,
                lastSessionCommandAck = null,
                dataResetId = resetMetadata.dataResetId,
            )
            val watchResetPublished = try {
                watchSyncRepository.publish(resetState)
                true
            } catch (_: Exception) {
                try {
                    syncScheduler.enqueueImmediate(
                        refreshProviders = false,
                        requireDelivery = true,
                    )
                } catch (_: Exception) {
                    // The reset token remains durable in metadata and every later publication.
                }
                false
            }
            LocalDataEraseResult(
                watchResetPublished = watchResetPublished,
                backgroundWorkCancelled = backgroundWorkCancelled,
                runtimeCachesCleared = runtimeCachesCleared,
            )
        }
    }

    private fun phoneBatteryPercent(): Int? =
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
}

data class LocalDataEraseResult(
    val watchResetPublished: Boolean,
    val backgroundWorkCancelled: Boolean,
    val runtimeCachesCleared: Boolean,
)
