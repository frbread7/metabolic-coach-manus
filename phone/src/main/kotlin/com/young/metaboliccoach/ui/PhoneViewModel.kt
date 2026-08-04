package com.young.metaboliccoach.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
import com.young.metaboliccoach.core.domain.GlucoseHistoryRepository
import com.young.metaboliccoach.core.domain.GlycemicGoalPlanner
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.GlycemicPlanningMilestoneRepository
import com.young.metaboliccoach.core.domain.GLYCEMIC_MILESTONE_CALCULATION_CONTRACT_VERSION
import com.young.metaboliccoach.core.domain.HistoryExplorerPreferencesRepository
import com.young.metaboliccoach.core.domain.HistoryRangeResolution
import com.young.metaboliccoach.core.domain.HistoryRangeResolver
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
import com.young.metaboliccoach.core.model.GlucoseHistorySettings
import com.young.metaboliccoach.core.model.GlucoseHistoryStatus
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseChartResult
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestoneEvaluation
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.RollingGlycemicMetrics
import com.young.metaboliccoach.core.model.SelectedPeriodGmiResult
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

enum class HistoryExplorerLoadStatus {
    IDLE,
    LOADING,
    READY,
    ERROR,
}

data class HistoryExplorerUiState(
    val selectedPreset: HistoryPeriodPreset = HistoryPeriodPreset.HOURS_24,
    val range: HistoryRange? = null,
    val chart: GlucoseChartResult? = null,
    val selectedPeriodGmi: SelectedPeriodGmiResult? = null,
    val loadStatus: HistoryExplorerLoadStatus = HistoryExplorerLoadStatus.IDLE,
    val detail: String = "Open History to review locally stored readings.",
    val requestGeneration: Long = 0L,
    val customDraft: HistoryCustomDraftUiState = HistoryCustomDraftUiState(),
)

data class PhoneUiState(
    val glucose: GlucoseReading? = null,
    val activity: ActivitySnapshot? = null,
    val recommendation: CoachRecommendation? = null,
    val summary: DailySummary? = null,
    val settings: CoachSettings = DefaultCoachSettings.create(),
    val nightscoutSettings: NightscoutSettings = DefaultNightscoutSettings.create(),
    val providerStatus: ProviderStatus? = null,
    val glucoseHistory: GlucoseHistoryStatus = GlucoseHistoryStatus(),
    val availableGlucoseOrigins: List<GlucoseDataOrigin> = emptyList(),
    val observations: List<PersonalObservation> = emptyList(),
    val activeSession: InterventionSession? = null,
    val operationMessage: String? = null,
    val isOperationInProgress: Boolean = false,
    val nowEpochMillis: Long = 0,
    val glycemicPlannerSettings: GlycemicPlannerSettings = GlycemicPlannerSettings(),
    val glycemicMetrics: List<RollingGlycemicMetrics> = emptyList(),
    val glycemicGoalScenario: GlycemicGoalScenario? = null,
    val planningMilestones: List<GlycemicPlanningMilestone> = emptyList(),
    val selectedMilestoneId: String? = null,
    val selectedMilestoneEvaluation: GlycemicPlanningMilestoneEvaluation? = null,
    val milestoneMigrationNotice: Boolean = false,
    val historyExplorer: HistoryExplorerUiState = HistoryExplorerUiState(),
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
    val milestones: List<GlycemicPlanningMilestone>,
    val selectedMilestoneId: String?,
    val selectedEvaluation: GlycemicPlanningMilestoneEvaluation?,
    val migrationNotice: Boolean,
)

private data class MilestoneInputs(
    val milestones: List<GlycemicPlanningMilestone>,
    val selectedMilestoneId: String?,
    val migrationNotice: Boolean,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val glucoseRepository: GlucoseRepository,
    private val glucoseHistoryRepository: GlucoseHistoryRepository,
    private val historyPreferencesRepository: HistoryExplorerPreferencesRepository,
    private val activityRepository: ActivityRepository,
    private val coachingRepository: CoachingRepository,
    private val settingsRepository: SettingsRepository,
    private val nightscoutSettingsRepository: NightscoutSettingsRepository,
    private val glycemicGoalRepository: GlycemicGoalRepository,
    private val milestoneRepository: GlycemicPlanningMilestoneRepository,
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
    private val historyCustomDraftStore = HistoryCustomDraftStore(
        savedStateHandle = savedStateHandle,
        defaultEndDate = Instant.ofEpochMilli(timeSource.nowEpochMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .minusDays(1),
    )
    private val historyExplorerState = MutableStateFlow(
        HistoryExplorerUiState(customDraft = historyCustomDraftStore.snapshot()),
    )
    private var historyLoadJob: Job? = null
    private var historyVisible = false
    private var visibleHistorySourceId: String? = null
    private val historyRequestGate = HistoryExplorerRequestGate()
    private val historyPresetInitializationGate = HistoryPresetInitializationGate()
    private val historyExplorerLoader = HistoryExplorerLoader(
        glucoseRepository = glucoseRepository,
        glycemicGoalRepository = glycemicGoalRepository,
        settingsRepository = settingsRepository,
    )

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

    private val baseUiStateWithoutHistory = combine(
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

    private val baseUiState = combine(
        baseUiStateWithoutHistory,
        glucoseHistoryRepository.observeStatus(),
    ) { state, history -> state.copy(glucoseHistory = history) }

    private val milestoneInputs = combine(
        milestoneRepository.observeMilestones(),
        milestoneRepository.observeSelectedMilestoneId(),
        milestoneRepository.observeMigrationNotice(),
    ) { milestones, selectedId, notice ->
        MilestoneInputs(milestones, selectedId, notice)
    }

    private val plannerState = combine(
        glucoseRepository.observeLatest(),
        glycemicGoalRepository.observeSettings(),
        settingsRepository.observe(),
        milestoneInputs,
    ) { _, plannerSettings, coachSettings, milestones ->
        plannerSettings to (coachSettings to milestones)
    }.flatMapLatest { (plannerSettings, configuration) ->
        val (coachSettings, milestoneInputs) = configuration
        flow {
            val readings = runCatching {
                val now = timeSource.nowEpochMillis()
                val selectedMilestoneTargetDate = milestoneInputs.milestones
                    .firstOrNull { it.id == milestoneInputs.selectedMilestoneId }
                    ?.targetDateEpochMillis
                val currentWindowStart =
                    now - GlycemicWindow.DAYS_90.durationMillis - 30 * 60_000L
                val selectedEvaluationWindowStart = selectedMilestoneTargetDate
                    ?.minus(GlycemicWindow.DAYS_90.durationMillis + 30 * 60_000L)
                glucoseRepository.readingsBetween(
                    startEpochMillis = minOf(
                        currentWindowStart,
                        selectedEvaluationWindowStart ?: currentWindowStart,
                    ),
                    endEpochMillis = now,
                )
            }.getOrDefault(emptyList())
            emitAll(
                timeSource.minuteTicks().map { now ->
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
                            veryLowGlucoseThresholdMgDl =
                                plannerSettings.veryLowGlucoseThresholdMgDl,
                        )
                    }
                    val selectedMilestone = milestoneInputs.milestones.firstOrNull {
                        it.id == milestoneInputs.selectedMilestoneId
                    }
                    val evaluation = selectedMilestone?.let {
                        GlycemicGoalPlanner.evaluatePlanningMilestone(
                            readings = readings,
                            milestone = it,
                            windowEndEpochMillis = now,
                            plannerSettings = plannerSettings,
                            targetLowerMgDl = coachSettings.targetLowerMgDl,
                            targetUpperMgDl = coachSettings.targetUpperMgDl,
                        )
                    }
                    PlannerState(
                        settings = plannerSettings,
                        metrics = metrics,
                        scenario = evaluation?.scenario,
                        milestones = milestoneInputs.milestones,
                        selectedMilestoneId = milestoneInputs.selectedMilestoneId,
                        selectedEvaluation = evaluation,
                        migrationNotice = milestoneInputs.migrationNotice,
                    )
                },
            )
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

    private val uiStateWithoutHistoryExplorer = combine(
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
            planningMilestones = planner.milestones,
            selectedMilestoneId = planner.selectedMilestoneId,
            selectedMilestoneEvaluation = planner.selectedEvaluation,
            milestoneMigrationNotice = planner.migrationNotice,
        )
    }

    val uiState = combine(
        uiStateWithoutHistoryExplorer,
        historyExplorerState,
    ) { state, history ->
        state.copy(
            historyExplorer = history.forActiveSource(state.glucose?.sourceId),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PhoneUiState(),
    )

    init {
        refresh()
    }

    fun setHistoryVisible(visible: Boolean, sourceId: String?) {
        historyVisible = visible
        visibleHistorySourceId = sourceId
        historyRequestGate.updateVisibility(visible, sourceId)
        if (!visible) {
            historyLoadJob?.cancel()
            return
        }
        val capturedSourceId = sourceId
        viewModelScope.launch {
            if (historyPresetInitializationGate.shouldReadPersistedPreset()) {
                val preset = historyPreferencesRepository.observeLastFixedPreset().first()
                historyPresetInitializationGate.acceptPersistedPreset(preset)?.let { accepted ->
                    historyExplorerState.value = historyExplorerState.value.copy(
                        selectedPreset = accepted,
                    )
                }
            }
            if (historyVisible && visibleHistorySourceId == capturedSourceId) {
                loadSelectedHistory(capturedSourceId)
            }
        }
    }

    fun selectHistoryPreset(preset: HistoryPeriodPreset) {
        historyPresetInitializationGate.recordUserSelection()
        if (preset == HistoryPeriodPreset.CUSTOM) {
            historyLoadJob?.cancel()
            historyRequestGate.invalidate()
            historyExplorerState.value = historyExplorerState.value.copy(
                selectedPreset = preset,
                range = null,
                chart = null,
                selectedPeriodGmi = null,
                loadStatus = HistoryExplorerLoadStatus.IDLE,
                detail = "Choose 14 to 90 completed local calendar days.",
            )
            return
        }
        historyExplorerState.value = historyExplorerState.value.copy(selectedPreset = preset)
        viewModelScope.launch {
            historyPreferencesRepository.updateLastFixedPreset(preset)
        }
        if (historyVisible) loadSelectedHistory(visibleHistorySourceId)
    }

    fun updateCustomHistoryStartDate(value: String) {
        historyExplorerState.value = historyExplorerState.value.copy(
            customDraft = historyCustomDraftStore.updateStartDate(value),
        )
    }

    fun updateCustomHistoryEndDate(value: String) {
        historyExplorerState.value = historyExplorerState.value.copy(
            customDraft = historyCustomDraftStore.updateEndDate(value),
        )
    }

    fun applyCustomHistoryRange() {
        val draft = historyCustomDraftStore.snapshot()
        val start = runCatching { LocalDate.parse(draft.startDateInput.trim()) }.getOrNull()
        val end = runCatching { LocalDate.parse(draft.endDateInput.trim()) }.getOrNull()
        if (start == null || end == null) {
            historyExplorerState.value = historyExplorerState.value.copy(
                customDraft = historyCustomDraftStore.updateError(
                    "Enter both dates as YYYY-MM-DD.",
                ),
            )
            return
        }
        selectCustomHistoryRange(start.toEpochDay(), end.toEpochDay())
    }

    private fun selectCustomHistoryRange(startDateEpochDay: Long, endDateEpochDay: Long) {
        historyPresetInitializationGate.recordUserSelection()
        val resolution = HistoryRangeResolver.resolveCustom(
            startDateEpochDay = startDateEpochDay,
            endDateEpochDay = endDateEpochDay,
            nowEpochMillis = timeSource.nowEpochMillis(),
            displayTimeZoneId = ZoneId.systemDefault().id,
        )
        when (resolution) {
            is HistoryRangeResolution.Invalid -> {
                historyLoadJob?.cancel()
                historyRequestGate.invalidate()
                historyExplorerState.value = historyExplorerState.value.copy(
                    selectedPreset = HistoryPeriodPreset.CUSTOM,
                    range = null,
                    chart = null,
                    selectedPeriodGmi = null,
                    loadStatus = HistoryExplorerLoadStatus.ERROR,
                    detail = resolution.detail,
                    customDraft = historyCustomDraftStore.updateError(resolution.detail),
                )
            }
            is HistoryRangeResolution.Resolved -> {
                historyExplorerState.value = historyExplorerState.value.copy(
                    selectedPreset = HistoryPeriodPreset.CUSTOM,
                    range = resolution.range,
                    customDraft = historyCustomDraftStore.updateError(null),
                )
                if (historyVisible) {
                    loadHistoryRange(visibleHistorySourceId, resolution.range)
                }
            }
        }
    }

    private fun loadSelectedHistory(sourceId: String?) {
        val currentState = historyExplorerState.value
        val range = if (currentState.selectedPreset == HistoryPeriodPreset.CUSTOM) {
            currentState.range ?: return
        } else {
            when (
                val resolution = HistoryRangeResolver.resolveFixed(
                    preset = currentState.selectedPreset,
                    nowEpochMillis = timeSource.nowEpochMillis(),
                    displayTimeZoneId = ZoneId.systemDefault().id,
                )
            ) {
                is HistoryRangeResolution.Invalid -> {
                    historyExplorerState.value = currentState.copy(
                        loadStatus = HistoryExplorerLoadStatus.ERROR,
                        detail = resolution.detail,
                    )
                    return
                }
                is HistoryRangeResolution.Resolved -> resolution.range
            }
        }
        loadHistoryRange(sourceId, range)
    }

    private fun loadHistoryRange(sourceId: String?, range: HistoryRange) {
        historyLoadJob?.cancel()
        historyRequestGate.invalidate()
        val capturedSourceId = sourceId?.takeIf(String::isNotBlank)
        if (capturedSourceId == null) {
            historyExplorerState.value = historyExplorerState.value.copy(
                range = range,
                chart = null,
                selectedPeriodGmi = null,
                loadStatus = HistoryExplorerLoadStatus.ERROR,
                detail = "No selected glucose source has local history.",
            )
            return
        }
        val token = historyRequestGate.begin(capturedSourceId)
        historyExplorerState.value = historyExplorerState.value.copy(
            range = range,
            chart = null,
            selectedPeriodGmi = null,
            loadStatus = HistoryExplorerLoadStatus.LOADING,
            detail = "Loading local history…",
            requestGeneration = token.generation,
        )
        historyLoadJob = viewModelScope.launch {
            try {
                val result = historyExplorerLoader.load(capturedSourceId, range)
                if (historyRequestGate.canPublish(token)) {
                    historyExplorerState.value = historyExplorerState.value.copy(
                        range = range,
                        chart = result.chart,
                        selectedPeriodGmi = result.selectedPeriodGmi,
                        loadStatus = HistoryExplorerLoadStatus.READY,
                        detail = result.chart.detail,
                        requestGeneration = token.generation,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (historyRequestGate.canPublish(token)) {
                    historyExplorerState.value = historyExplorerState.value.copy(
                        range = range,
                        chart = null,
                        selectedPeriodGmi = null,
                        loadStatus = HistoryExplorerLoadStatus.ERROR,
                        detail = "Local history could not be loaded.",
                        requestGeneration = token.generation,
                    )
                }
            }
        }
    }

    fun refresh() {
        runOperation("Updated from configured providers.") {
            refreshCoordinator.refresh(refreshProviders = true)
        }
    }

    fun saveGlucoseHistorySettings(settings: GlucoseHistorySettings) {
        runOperation("History settings saved. Confirm the policy to apply it.") {
            mutationGate.withLock {
                glucoseHistoryRepository.updateSettings(settings)
            }
        }
    }

    fun confirmGlucoseHistoryRetention() {
        runOperation("History retention policy applied.") {
            mutationGate.withLock {
                glucoseHistoryRepository.confirmRetentionPolicy()
            }
        }
    }

    fun backfillGlucoseHistory() {
        runOperation("Older glucose history downloaded.") {
            mutationGate.withLock {
                glucoseHistoryRepository.backfillNextChunk()
            }
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

    fun saveGlycemicPlannerSafetySettings(settings: GlycemicPlannerSettings) {
        runOperation("Planner safety settings saved.") {
            mutationGate.withLock {
                glycemicGoalRepository.updateSafetySettings(settings)
            }
        }
    }

    fun createPlanningMilestone(
        title: String?,
        targetGmiPercent: Double,
        targetProvenance: com.young.metaboliccoach.core.model.GlycemicTargetProvenance,
        horizonDays: Int,
    ) {
        runOperation("Planning milestone saved.") {
            mutationGate.withLock {
                val now = timeSource.nowEpochMillis()
                val horizon = requireNotNull(GlycemicWindow.fromDays(horizonDays))
                milestoneRepository.create(
                    GlycemicPlanningMilestone(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        targetGmiPercent = targetGmiPercent,
                        targetProvenance = targetProvenance,
                        targetDateEpochMillis = now + horizon.durationMillis,
                        originalHorizonDays = horizonDays,
                        lifecycleState = com.young.metaboliccoach.core.model.MilestoneLifecycleState.ACTIVE,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        archivedAtEpochMillis = null,
                        calculationContractVersion =
                            GLYCEMIC_MILESTONE_CALCULATION_CONTRACT_VERSION,
                    ),
                )
            }
        }
    }

    fun updatePlanningMilestone(
        existing: GlycemicPlanningMilestone,
        title: String?,
        targetGmiPercent: Double,
        targetProvenance: com.young.metaboliccoach.core.model.GlycemicTargetProvenance,
        horizonDays: Int,
        targetDateEpochMillis: Long,
    ) {
        runOperation("Planning milestone updated.") {
            mutationGate.withLock {
                milestoneRepository.update(
                    existing.copy(
                        title = title,
                        targetGmiPercent = targetGmiPercent,
                        targetProvenance = targetProvenance,
                        originalHorizonDays = horizonDays,
                        targetDateEpochMillis = targetDateEpochMillis,
                    ),
                )
            }
        }
    }

    fun selectPlanningMilestone(id: String) {
        runOperation("Planning milestone selected.") {
            mutationGate.withLock { milestoneRepository.select(id) }
        }
    }

    fun archivePlanningMilestone(id: String) {
        runOperation("Planning milestone archived.") {
            mutationGate.withLock {
                milestoneRepository.archive(id, timeSource.nowEpochMillis())
            }
        }
    }

    fun deletePlanningMilestone(id: String) {
        runOperation("Planning milestone deleted.") {
            mutationGate.withLock { milestoneRepository.delete(id) }
        }
    }

    fun dismissMilestoneMigrationNotice() {
        runOperation {
            mutationGate.withLock { milestoneRepository.dismissMigrationNotice() }
            ""
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
            syncScheduler.cancelPostMealEvaluations()
            if (settings.postMealRemindersEnabled) {
                coachingRepository.latestMealMarker()?.let { marker ->
                    val expiresAt = marker.occurredAtEpochMillis +
                        (settings.postMealDelayMinutes + settings.postMealWindowMinutes) * 60_000L
                    if (expiresAt > System.currentTimeMillis()) {
                        syncScheduler.schedulePostMealEvaluation(
                            marker,
                            settings.postMealDelayMinutes,
                            settings.postMealWindowMinutes,
                        )
                    }
                }
            }
            refreshCoordinator.refresh(refreshProviders = true)
        }
    }

    fun markMeal() {
        runOperation("Meal marked.") {
            val now = System.currentTimeMillis()
            val marker = MealMarker(UUID.randomUUID().toString(), now)
            val settings = mutationGate.withLock {
                coachingRepository.saveMealMarker(marker)
                settingsRepository.observe().first()
            }
            if (settings.postMealRemindersEnabled) {
                syncScheduler.cancelPostMealEvaluations()
                syncScheduler.schedulePostMealEvaluation(
                    marker,
                    settings.postMealDelayMinutes,
                    settings.postMealWindowMinutes,
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
                        QuickActionType.SNOOZE -> true
                        QuickActionType.MARK_COMPLETED -> false
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
                    glucoseSourceId = authoritativeRecommendation?.glucoseSourceId,
                    safetyReadingId = authoritativeRecommendation?.safetyReadingId,
                    safetyReadingAtEpochMillis =
                        authoritativeRecommendation?.safetyReadingAtEpochMillis,
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
