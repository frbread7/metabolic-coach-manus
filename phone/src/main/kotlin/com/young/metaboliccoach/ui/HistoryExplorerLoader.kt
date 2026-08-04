package com.young.metaboliccoach.ui

import androidx.lifecycle.SavedStateHandle
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.GlucoseTrendSeriesBuilder
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.SelectedPeriodGmiCalculator
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.GlucoseChartResult
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.SelectedPeriodGmiResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal data class HistoryExplorerLoadResult(
    val chart: GlucoseChartResult,
    val selectedPeriodGmi: SelectedPeriodGmiResult,
)

/** Local-only loader. This boundary intentionally has no provider, refresh, or backfill dependency. */
internal class HistoryExplorerLoader(
    private val glucoseRepository: GlucoseRepository,
    private val glycemicGoalRepository: GlycemicGoalRepository,
    private val settingsRepository: SettingsRepository,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun load(sourceId: String, range: HistoryRange): HistoryExplorerLoadResult {
        val queryStart = (range.startEpochMillis - HISTORY_QUERY_LEAD_MILLIS).coerceAtLeast(0L)
        val readings = glucoseRepository.readingsBetweenExactSource(
            sourceId = sourceId,
            startEpochMillis = queryStart,
            endEpochMillis = range.endExclusiveEpochMillis,
        )
        val plannerSettings = glycemicGoalRepository.observeSettings().first()
        val coachSettings = settingsRepository.observe().first()
        return withContext(computationDispatcher) {
            val computationJob = currentCoroutineContext()[Job]
            val cancellationCheck: () -> Unit = {
                computationJob?.ensureActive()
                Unit
            }
            HistoryExplorerLoadResult(
                chart = GlucoseTrendSeriesBuilder.build(
                    readings = readings,
                    sourceId = sourceId,
                    range = range,
                    cancellationCheck = cancellationCheck,
                ),
                selectedPeriodGmi = SelectedPeriodGmiCalculator.calculate(
                    readings = readings,
                    sourceId = sourceId,
                    range = range,
                    plannerSettings = plannerSettings,
                    targetLowerMgDl = coachSettings.targetLowerMgDl,
                    targetUpperMgDl = coachSettings.targetUpperMgDl,
                    cancellationCheck = cancellationCheck,
                ),
            )
        }
    }

    private companion object {
        const val HISTORY_QUERY_LEAD_MILLIS = 20L * 60L * 1_000L
    }
}

internal data class HistoryExplorerRequestToken(
    val generation: Long,
    val sourceId: String,
)

/** Prevents results from a previous range, source, or hidden History screen from publishing. */
internal class HistoryExplorerRequestGate {
    private var generation = 0L
    private var visible = false
    private var sourceId: String? = null

    fun updateVisibility(visible: Boolean, sourceId: String?) {
        if (this.visible != visible || this.sourceId != sourceId) {
            generation += 1L
        }
        this.visible = visible
        this.sourceId = sourceId
    }

    fun begin(sourceId: String): HistoryExplorerRequestToken {
        generation += 1L
        return HistoryExplorerRequestToken(generation, sourceId)
    }

    fun invalidate() {
        generation += 1L
    }

    fun canPublish(token: HistoryExplorerRequestToken): Boolean =
        visible && token.generation == generation && token.sourceId == sourceId
}

/** Ensures a delayed DataStore read cannot replace the user's first explicit period choice. */
internal class HistoryPresetInitializationGate {
    private var initialized = false

    fun shouldReadPersistedPreset(): Boolean = !initialized

    fun acceptPersistedPreset(preset: HistoryPeriodPreset): HistoryPeriodPreset? =
        preset.takeIf { !initialized }?.also { initialized = true }

    fun recordUserSelection() {
        initialized = true
    }
}

data class HistoryCustomDraftUiState(
    val startDateInput: String = "",
    val endDateInput: String = "",
    val inputError: String? = null,
)

/** Keeps an uncommitted custom range across navigation and activity recreation only. */
internal class HistoryCustomDraftStore(
    private val savedStateHandle: SavedStateHandle,
    defaultEndDate: LocalDate,
) {
    init {
        if (!savedStateHandle.contains(START_DATE_KEY)) {
            savedStateHandle[START_DATE_KEY] = defaultEndDate.minusDays(13).toString()
        }
        if (!savedStateHandle.contains(END_DATE_KEY)) {
            savedStateHandle[END_DATE_KEY] = defaultEndDate.toString()
        }
    }

    fun snapshot(): HistoryCustomDraftUiState = HistoryCustomDraftUiState(
        startDateInput = savedStateHandle[START_DATE_KEY] ?: "",
        endDateInput = savedStateHandle[END_DATE_KEY] ?: "",
        inputError = savedStateHandle[INPUT_ERROR_KEY],
    )

    fun updateStartDate(value: String): HistoryCustomDraftUiState {
        savedStateHandle[START_DATE_KEY] = value.take(MAX_DATE_INPUT_LENGTH)
        savedStateHandle.remove<String>(INPUT_ERROR_KEY)
        return snapshot()
    }

    fun updateEndDate(value: String): HistoryCustomDraftUiState {
        savedStateHandle[END_DATE_KEY] = value.take(MAX_DATE_INPUT_LENGTH)
        savedStateHandle.remove<String>(INPUT_ERROR_KEY)
        return snapshot()
    }

    fun updateError(value: String?): HistoryCustomDraftUiState {
        if (value == null) {
            savedStateHandle.remove<String>(INPUT_ERROR_KEY)
        } else {
            savedStateHandle[INPUT_ERROR_KEY] = value
        }
        return snapshot()
    }

    private companion object {
        const val START_DATE_KEY = "history.custom.start_date"
        const val END_DATE_KEY = "history.custom.end_date"
        const val INPUT_ERROR_KEY = "history.custom.input_error"
        const val MAX_DATE_INPUT_LENGTH = 32
    }
}

/** Hides an old source result synchronously, before Compose launches the replacement request. */
internal fun HistoryExplorerUiState.forActiveSource(activeSourceId: String?): HistoryExplorerUiState {
    val resultSourceIds = listOfNotNull(chart?.sourceId, selectedPeriodGmi?.sourceId).distinct()
    if (resultSourceIds.isEmpty() || resultSourceIds.singleOrNull() == activeSourceId) return this
    return copy(
        chart = null,
        selectedPeriodGmi = null,
        loadStatus = if (activeSourceId.isNullOrBlank()) {
            HistoryExplorerLoadStatus.ERROR
        } else {
            HistoryExplorerLoadStatus.LOADING
        },
        detail = if (activeSourceId.isNullOrBlank()) {
            "No selected glucose source has local history."
        } else {
            "Loading local history…"
        },
    )
}
