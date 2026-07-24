package com.young.metaboliccoach.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.metaboliccoach.background.CommandHandlingResult
import com.young.metaboliccoach.background.PhoneDataMutationGate
import com.young.metaboliccoach.background.PhoneRefreshCoordinator
import com.young.metaboliccoach.background.QuickActionHandler
import com.young.metaboliccoach.background.SyncScheduler
import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachedExerciseActionPolicy
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.SettingsValidator
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DailySummary
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.PersonalObservation
import com.young.metaboliccoach.core.model.ProviderStatus
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.data.PersonalDataFileExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class PhoneUiState(
    val glucose: GlucoseReading? = null,
    val activity: ActivitySnapshot? = null,
    val recommendation: CoachRecommendation? = null,
    val summary: DailySummary? = null,
    val settings: CoachSettings = DefaultCoachSettings.create(),
    val providerStatus: ProviderStatus? = null,
    val availableGlucoseOrigins: List<GlucoseDataOrigin> = emptyList(),
    val observations: List<PersonalObservation> = emptyList(),
    val activeSession: InterventionSession? = null,
    val operationMessage: String? = null,
    val isOperationInProgress: Boolean = false,
    val nowEpochMillis: Long = 0,
)

private data class CurrentState(
    val glucose: GlucoseReading?,
    val activity: ActivitySnapshot?,
    val recommendation: CoachRecommendation?,
    val summary: DailySummary,
    val activeSession: InterventionSession?,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    private val activityRepository: ActivityRepository,
    private val coachingRepository: CoachingRepository,
    private val settingsRepository: SettingsRepository,
    private val quickActionHandler: QuickActionHandler,
    private val refreshCoordinator: PhoneRefreshCoordinator,
    private val syncScheduler: SyncScheduler,
    private val timeSource: CoachTimeSource,
    private val settingsValidator: SettingsValidator,
    private val personalDataFileExporter: PersonalDataFileExporter,
    private val mutationGate: PhoneDataMutationGate,
) : ViewModel() {
    private val operationMessage = MutableStateFlow<String?>(null)
    private val operationInProgress = MutableStateFlow(false)
    private val operationMutex = Mutex()

    private val current = combine(
        glucoseRepository.observeLatest(),
        activityRepository.observeToday(),
        coachingRepository.observeCurrentRecommendation(),
        coachingRepository.observeTodaySummary(),
        coachingRepository.observeActiveSession(),
    ) { glucose, activity, recommendation, summary, activeSession ->
        CurrentState(glucose, activity, recommendation, summary, activeSession)
    }

    private val baseUiState = combine(
        current,
        settingsRepository.observe(),
        glucoseRepository.observeProviderStatus(),
        glucoseRepository.observeAvailableOrigins(),
        coachingRepository.observePersonalObservations(),
    ) { current, settings, provider, origins, observations ->
        PhoneUiState(
            glucose = current.glucose,
            activity = current.activity,
            recommendation = current.recommendation.takeIf { current.activeSession == null },
            summary = current.summary,
            settings = settings,
            providerStatus = provider,
            availableGlucoseOrigins = origins,
            observations = observations,
            activeSession = current.activeSession,
        )
    }

    private val uiClock = baseUiState
        .map { (it.recommendation as? CoachRecommendation.Action)?.validUntilEpochMillis }
        .distinctUntilChanged()
        .flatMapLatest { validUntilEpochMillis ->
            merge(
                timeSource.minuteTicks(),
                flow {
                    val validUntil = validUntilEpochMillis ?: return@flow
                    val waitMillis = validUntil - timeSource.nowEpochMillis()
                    if (waitMillis > 0) delay(waitMillis)
                    emit(timeSource.nowEpochMillis())
                },
            )
        }

    val uiState = combine(
        baseUiState,
        operationMessage,
        operationInProgress,
        uiClock,
    ) { state, message, isWorking, now ->
        val recommendation = state.recommendation
        val effectiveRecommendation = if (
            recommendation is CoachRecommendation.Action &&
            (
                now >= recommendation.validUntilEpochMillis ||
                    !CoachedExerciseActionPolicy.canStart(
                        reading = state.glucose,
                        settings = state.settings,
                        nowEpochMillis = now,
                    )
            )
        ) {
            null
        } else {
            recommendation
        }
        state.copy(
            recommendation = effectiveRecommendation,
            operationMessage = message,
            isOperationInProgress = isWorking,
            nowEpochMillis = now,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PhoneUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        runOperation("Updated from configured providers.") {
            refreshCoordinator.refresh(refreshProviders = true)
        }
    }

    fun onHealthPermissionsChanged() {
        runOperation("Health Connect permissions updated.") {
            syncScheduler.configurePeriodic()
            refreshCoordinator.refresh(refreshProviders = true)
        }
    }

    fun saveSettings(settings: CoachSettings) {
        val errors = settingsValidator.validate(settings)
        if (errors.isNotEmpty()) {
            operationMessage.value = errors.joinToString(separator = "\n")
            return
        }
        runOperation("Settings saved.") {
            mutationGate.withLock {
                settingsRepository.update(settings)
            }
            syncScheduler.configurePeriodic()
            refreshCoordinator.refresh(refreshProviders = true)
        }
    }

    fun markMeal() {
        runOperation("Meal marked.") {
            val now = System.currentTimeMillis()
            mutationGate.withLock {
                coachingRepository.saveMealMarker(
                    MealMarker(UUID.randomUUID().toString(), now),
                )
            }
            refreshCoordinator.refresh(refreshProviders = false)
        }
    }

    fun exportData(uri: Uri) {
        runOperation("Sensitive health data exported to the selected document.") {
            personalDataFileExporter.export(uri)
        }
    }

    fun eraseLocalData() {
        runOperation {
            val result = refreshCoordinator.eraseLocalData()
            buildString {
                append("Local Metabolic Coach history and settings erased.")
                if (!result.watchResetPublished) {
                    append(" The watch reset is saved and will retry on a later sync.")
                }
                if (!result.backgroundWorkCancelled) {
                    append(
                        " Some background cancellation could not be confirmed; " +
                            "new source data may be collected again.",
                    )
                }
            }
        }
    }

    fun quickAction(type: QuickActionType, targetSessionId: String? = null) {
        runOperation("Action recorded.") {
            val commandId = UUID.randomUUID().toString()
            val recommendation = (uiState.value.recommendation as? CoachRecommendation.Action)
                ?.takeIf { action ->
                    when (type) {
                        QuickActionType.START_WALK ->
                            action.interventionType.name == "WALK"
                        QuickActionType.START_STAIRS ->
                            action.interventionType.name == "STAIRS"
                        else -> false
                    }
                }
            val result = mutationGate.withLock {
                val authoritativeRecommendation = recommendation?.let {
                    coachingRepository.rememberRecommendation(it)
                }
                val command = QuickActionCommand(
                    id = commandId,
                    type = type,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    sessionId = when (type) {
                        QuickActionType.START_WALK,
                        QuickActionType.START_STAIRS,
                        -> commandId
                        QuickActionType.MARK_COMPLETED -> targetSessionId
                        QuickActionType.SNOOZE -> null
                    },
                    recommendationId = authoritativeRecommendation?.id,
                    recommendationValidUntilEpochMillis =
                        authoritativeRecommendation?.validUntilEpochMillis,
                    recommendationReason = authoritativeRecommendation?.reason,
                    recommendationAlgorithmVersion =
                        authoritativeRecommendation?.algorithmVersion,
                    recommendationCreatedAtEpochMillis =
                        authoritativeRecommendation?.createdAtEpochMillis,
                    triggerContextId = authoritativeRecommendation?.triggerContextId,
                    triggerAtEpochMillis = authoritativeRecommendation?.triggerAtEpochMillis,
                )
                quickActionHandler.handle(command)
            }
            check(result == CommandHandlingResult.Applied) {
                "The requested action could not be applied."
            }
            refreshCoordinator.refresh(refreshProviders = false)
        }
    }

    private fun runOperation(
        successMessage: String,
        block: suspend () -> Unit,
    ) = runOperation {
        block()
        successMessage
    }

    private fun runOperation(
        block: suspend () -> String,
    ) {
        if (!operationMutex.tryLock()) {
            operationMessage.value = "Another operation is already in progress."
            return
        }
        operationInProgress.value = true
        viewModelScope.launch {
            operationMessage.value = "Working…"
            try {
                operationMessage.value = block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                operationMessage.value =
                    "Could not complete the operation (${error.javaClass.simpleName})."
            } finally {
                operationInProgress.value = false
                operationMutex.unlock()
            }
        }
    }
}
