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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

data class RenderedHistoryViewport(
    val sourceId: String,
    val selectedRange: HistoryRange,
    val viewport: HistoryViewport,
    val chart: GlucoseChartResult,
)

enum class HistoryViewportLoadStatus {
    IDLE,
    DEBOUNCING,
    LOADING,
    READY,
    ERROR,
}

internal class HistoryViewportDebouncer(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = HISTORY_VIEWPORT_DEBOUNCE_MILLIS,
) {
    private var job: Job? = null

    fun submit(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(debounceMillis)
            block()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

internal const val HISTORY_VIEWPORT_DEBOUNCE_MILLIS = 200L

/**
 * Owns viewport debounce and stale-publication arbitration. Keeping this orchestration separate
 * makes the exact sequence deterministic under tests while the ViewModel remains the state owner.
 */
internal class HistoryViewportCoordinator(
    scope: CoroutineScope,
    private val loadViewport: suspend (
        sourceId: String,
        selectedRange: HistoryRange,
        viewport: HistoryViewport,
    ) -> RenderedHistoryViewport,
    private val readState: () -> HistoryExplorerUiState,
    private val writeState: (HistoryExplorerUiState) -> Unit,
    debounceMillis: Long = HISTORY_VIEWPORT_DEBOUNCE_MILLIS,
) {
    private val debouncer = HistoryViewportDebouncer(scope, debounceMillis)
    private val requestGate = HistoryViewportRequestGate()
    private var visible = false
    private var sourceId: String? = null

    fun updateVisibility(visible: Boolean, sourceId: String?) {
        val contextChanged = this.visible != visible || this.sourceId != sourceId
        requestGate.updateVisibility(visible, sourceId)
        if (contextChanged) debouncer.cancel()
        this.visible = visible
        this.sourceId = sourceId
    }

    fun invalidate() {
        debouncer.cancel()
        requestGate.invalidate()
    }

    fun request(
        sourceId: String,
        selectedRange: HistoryRange,
        viewport: HistoryViewport,
    ) {
        val token = requestGate.begin(sourceId, selectedRange, viewport)
        writeState(readState().withViewportRequest(viewport))
        debouncer.submit {
            try {
                if (!canPublish(token)) return@submit
                writeState(
                    readState().copy(
                        viewportLoadStatus = HistoryViewportLoadStatus.LOADING,
                        viewportDetail = "Updating visible chart…",
                    ),
                )
                val rendered = loadViewport(sourceId, selectedRange, viewport)
                if (canPublish(token)) {
                    writeState(readState().withRenderedViewport(rendered))
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (canPublish(token)) {
                    writeState(
                        readState().withViewportFailure("Visible chart could not be updated."),
                    )
                }
            }
        }
    }

    private fun canPublish(token: HistoryViewportRequestToken): Boolean {
        val state = readState()
        return requestGate.canPublish(
            token = token,
            currentSelectedRange = state.selectedRange,
            currentRequestedViewport = state.requestedViewport,
        )
    }
}

/**
 * Exact-source, local-only chart loader for an interactive viewport. It deliberately has no goal,
 * settings, provider, refresh, backfill, or retention dependency.
 */
internal class HistoryViewportLoader(
    private val glucoseRepository: GlucoseRepository,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun load(
        sourceId: String,
        selectedRange: HistoryRange,
        viewport: HistoryViewport,
    ): RenderedHistoryViewport {
        require(sourceId.isNotBlank())
        require(viewport.durationMillis > 0L)
        require(viewport.startEpochMillis >= selectedRange.startEpochMillis)
        require(viewport.endExclusiveEpochMillis <= selectedRange.endExclusiveEpochMillis)

        val queryStart = if (viewport.startEpochMillis <= HISTORY_QUERY_LEAD_MILLIS) {
            0L
        } else {
            viewport.startEpochMillis - HISTORY_QUERY_LEAD_MILLIS
        }
        val queryEndInclusive = viewport.endExclusiveEpochMillis - 1L
        val readings = glucoseRepository.readingsBetweenExactSource(
            sourceId = sourceId,
            startEpochMillis = queryStart,
            endEpochMillis = queryEndInclusive,
        )
        val viewportRange = selectedRange.copy(
            startEpochMillis = viewport.startEpochMillis,
            endExclusiveEpochMillis = viewport.endExclusiveEpochMillis,
        )
        val chart = withContext(computationDispatcher) {
            val computationJob = currentCoroutineContext()[Job]
            GlucoseTrendSeriesBuilder.buildViewport(
                readings = readings,
                sourceId = sourceId,
                range = viewportRange,
                cancellationCheck = {
                    computationJob?.ensureActive()
                    Unit
                },
            )
        }
        return RenderedHistoryViewport(
            sourceId = sourceId,
            selectedRange = selectedRange,
            viewport = viewport,
            chart = chart,
        )
    }

    private companion object {
        const val HISTORY_QUERY_LEAD_MILLIS = 20L * 60L * 1_000L
    }
}

internal data class HistoryViewportRequestToken(
    val generation: Long,
    val sourceId: String,
    val selectedRange: HistoryRange,
    val viewport: HistoryViewport,
)

/** Rejects delayed viewport results across range, source, visibility, and newer-intent changes. */
internal class HistoryViewportRequestGate {
    private var generation = 0L
    private var visible = false
    private var sourceId: String? = null

    fun updateVisibility(visible: Boolean, sourceId: String?) {
        if (this.visible != visible || this.sourceId != sourceId) generation += 1L
        this.visible = visible
        this.sourceId = sourceId
    }

    fun begin(
        sourceId: String,
        selectedRange: HistoryRange,
        viewport: HistoryViewport,
    ): HistoryViewportRequestToken {
        generation += 1L
        return HistoryViewportRequestToken(generation, sourceId, selectedRange, viewport)
    }

    fun invalidate() {
        generation += 1L
    }

    fun canPublish(
        token: HistoryViewportRequestToken,
        currentSelectedRange: HistoryRange?,
        currentRequestedViewport: HistoryViewport?,
    ): Boolean =
        visible &&
            token.generation == generation &&
            token.sourceId == sourceId &&
            token.selectedRange == currentSelectedRange &&
            token.viewport == currentRequestedViewport
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
    val resultSourceIds = listOfNotNull(
        renderedViewport?.sourceId,
        selectedPeriodGmi?.sourceId,
    ).distinct()
    if (resultSourceIds.isEmpty() || resultSourceIds.singleOrNull() == activeSourceId) return this
    return copy(
        requestedViewport = null,
        renderedViewport = null,
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
        viewportLoadStatus = HistoryViewportLoadStatus.IDLE,
        viewportDetail = "",
    )
}

internal fun HistoryExplorerUiState.withViewportRequest(
    viewport: HistoryViewport,
): HistoryExplorerUiState = copy(
    requestedViewport = viewport,
    viewportLoadStatus = HistoryViewportLoadStatus.DEBOUNCING,
    viewportDetail = "Updating visible chart…",
)

internal fun HistoryExplorerUiState.withRenderedViewport(
    rendered: RenderedHistoryViewport,
): HistoryExplorerUiState {
    if (selectedRange != rendered.selectedRange || requestedViewport != rendered.viewport) return this
    val expectedChartRange = rendered.selectedRange.copy(
        startEpochMillis = rendered.viewport.startEpochMillis,
        endExclusiveEpochMillis = rendered.viewport.endExclusiveEpochMillis,
    )
    if (rendered.sourceId != rendered.chart.sourceId || rendered.chart.range != expectedChartRange) {
        return this
    }
    return copy(
        renderedViewport = rendered,
        viewportLoadStatus = HistoryViewportLoadStatus.READY,
        viewportDetail = rendered.chart.detail,
    )
}

internal fun HistoryExplorerUiState.withViewportFailure(detail: String): HistoryExplorerUiState =
    copy(
        viewportLoadStatus = HistoryViewportLoadStatus.ERROR,
        viewportDetail = detail,
    )
