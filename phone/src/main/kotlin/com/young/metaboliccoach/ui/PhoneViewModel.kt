package com.young.metaboliccoach.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.metaboliccoach.background.CommandHandlingResult
import com.young.metaboliccoach.background.PhoneDataMutationGate
import com.young.metaboliccoach.background.PhoneDataOperationPreemptedException
import com.young.metaboliccoach.background.PhoneRefreshCoordinator
import com.young.metaboliccoach.background.QuickActionHandler
import com.young.metaboliccoach.background.SyncScheduler
import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachedExerciseActionPolicy
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.GlycemicGoalPlanner
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsValidator
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.SettingsValidator
import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DailySummary
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.DefaultNightscoutSettings
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.RollingGlycemicMetrics
import com.young.metaboliccoach.core.model.GlycemicGoalScenario
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.MealMarker
import com.young.metaboliccoach.core.model.NightscoutSettings
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
    val nightscoutSettings: NightscoutSettings = DefaultNightscoutSettings.create(),
    val providerStatus: ProviderStatus? = null,
    val availableGlucoseOrigins: List<GlucoseDataOrigin> = emptyList(),
    val observations: List<PersonalObservation> = emptyList(),
    val activeSession: InterventionSession? = null,
    val operationMessage: String? = null,
    val isOperationInProgress: Boolean = false,
    val nowEpochMillis: Long = 0,
    val glycemicPlannerSettings: GlycemicPlannerSettings = GlycemicPlannerSettings(),
    val glycemicMetrics: List<RollingGlycemicMetrics> = emptyList(),
    val glycemicGoalScenario: GlycemicGoalScenario? = null,
)

private data class CurrentState(
    val glucose: GlucoseReading?,
    val activity: ActivitySnapshot?,
    val recommendation: CoachRecommendation?,
    val summary: DailySummary,
    val activeSession: InterventionSession?,
)

private data class PhoneConfiguration(
    val coach: CoachSettings,
    val nightscout: NightscoutSettings,
)

private data class PlannerState(
    val settings: GlycemicPlannerSettings,
    val metrics: List<RollingGlycemicMetrics>,
    val scenario: GlycemicGoalScenario?,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    private val activityRepository: ActivityRepository,
    private val coachingRepository: CoachingRepository,
    private val settingsRepository: SettingsRepository,
    private val nightscoutSettingsRepository: NightscoutSettingsRepository,
    private val glycemicGoalRepository: GlycemicGoalRepository,
    private val quickActionHandler: QuickActionHandler,
    private val refreshCoordinator: PhoneRefreshCoordinator,
    private val syncScheduler: SyncScheduler,
    private val timeSource: CoachTimeSource,
    private val settingsValidator: SettingsValidator,
    private val nightscoutSettingsValidator: NightscoutSettingsValidator,
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

    private val configuration = combine(
        settingsRepository.observe(),
        nightscoutSettingsRepository.observeNightscoutSettings(),
    ) { coach, nightscout ->
        PhoneConfiguration(coach, nightscout)
    }

    private val baseUiState = combine(
        current,
        configuration,
        glucoseRepository.observeProviderStatus(),
        glucoseRepository.observeAvailableOrigins(),
        coachingRepository.observePersonalObservations(),
    ) { current, configuration, provider, origins, observations ->
        PhoneUiState(
            glucose = current.glucose,
            activity = current.activity,
            recommendation = current.recommendation.takeIf { current.activeSession == null },
            summary = current.summary,
            settings = configuration.coach,
            nightscoutSettings = configuration.nightscout,
            providerStatus = provider,
            availableGlucoseOrigins = origins,
            observations = observations,
            activeSession = current.activeSession,
        )
    }

    private val plannerState = combine(
        glucoseRepository.observeLatest(),
        glycemicGoalRepository.observeSettings(),
        nightscoutSettingsRepository.observeNightscoutSettings(),
        settingsRepository.observe(),
    ) { _, plannerSettings, _, coachSettings -> plannerSettings to coachSettings }
        .flatMapLatest { (plannerSettings, coachSettings) ->
            flow {
                val now = timeSource.nowEpochMillis()
                val readings = runCatching {
                    glucoseRepository.readingsBetween(
                        startEpochMillis = now - GlycemicWindow.DAYS_90.durationMillis - 30 * 60_000L,
                        endEpochMillis = now,
                    )
                }.getOrDefault(emptyList())
                val metrics = listOf(
                    GlycemicWindow.DAYS_30,
                    GlycemicWindow.DAYS_60,
                    GlycemicWindow.DAYS_90,
                ).map { window ->
                    GlycemicGoalPlanner.calculateRollingMetrics(
                        readings = readings,
                        window = window,
                        windowEndEpochMillis = now,
                        targetLowerMgDl = coachSettings.targetLowerMgDl,
                        targetUpperMgDl = coachSettings.targetUpperMgDl,
                        lowGlucoseThresholdMgDl = plannerSettings.lowGlucoseThresholdMgDl,
                        veryLowGlucoseThresholdMgDl = plannerSettings.veryLowGlucoseThresholdMgDl,
                    )
                }
                val scenario = plannerSettings.targetGmiPercent?.let { target ->
                    GlycemicGoalPlanner.calculateGoalScenario(
                        readings = readings,
                        horizon = plannerSettings.horizon,
                        windowEndEpochMillis = now,
                        targetGmiPercent = target,
                        plannerSettings = plannerSettings,
                        targetLowerMgDl = coachSettings.targetLowerMgDl,
                        targetUpperMgDl = coachSettings.targetUpperMgDl,
                    )
                }
                emit(PlannerState(plannerSettings, metrics, scenario))
            }
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
        plannerState,
        operationMessage,
        operationInProgress,
        uiClock,
    ) { state, planner, message, isWorking, now ->
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
            glycemicPlannerSettings = planner.settings,
            glycemicMetrics = planner.metrics,
            glycemicGoalScenario = planner.scenario,
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

    fun saveGlycemicPlannerSettings(settings: GlycemicPlannerSettings) {
        runOperation("Glycemic planner settings saved.") {
            mutationGate.withLock {
                glycemicGoalRepository.updateSettings(settings)
            }
        }
    }

    fun saveSettings(
        settings: CoachSettings,
        nightscoutSettings: NightscoutSettings,
    ) {
        val errors = settingsValidator.validate(settings)
        val nightscoutErrors = nightscoutSettingsValidator.validate(nightscoutSettings)
        if (errors.isNotEmpty() || nightscoutErrors.isNotEmpty()) {
            operationMessage.value = (errors + nightscoutErrors).joinToString(separator = "\n")
            return
        }
        runOperation("Settings saved.") {
            mutationGate.withLock {
                val normalizedNightscout =
                    nightscoutSettingsValidator.normalize(nightscoutSettings)
                settingsRepository.update(
                    settings.copy(
                        glucoseProviderMode =
                            com.young.metaboliccoach.core.model.GlucoseProviderMode.NIGHTSCOUT,
                    ),
                )
                nightscoutSettingsRepository.updateNightscoutSettings(normalizedNightscout)
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
                if (!result.runtimeCachesCleared) {
                    append(
                        " A provider memory cache could not be confirmed cleared; " +
                            "close the app before reconfiguring a source.",
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
            } catch (_: PhoneDataOperationPreemptedException) {
                operationMessage.value = "Refresh paused for a newer local data operation."
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
