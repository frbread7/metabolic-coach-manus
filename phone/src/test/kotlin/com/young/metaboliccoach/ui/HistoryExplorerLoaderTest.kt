package com.young.metaboliccoach.ui

import androidx.lifecycle.SavedStateHandle
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseChartResult
import com.young.metaboliccoach.core.model.GlucoseChartStatus
import com.young.metaboliccoach.core.model.HistoryCoverage
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.SelectedPeriodGmiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class HistoryExplorerLoaderTest {
    @Test
    fun `loader reads exact local source without refresh or provider access`() = runTest {
        val glucoseRepository = Mockito.mock(GlucoseRepository::class.java)
        val goalRepository = Mockito.mock(GlycemicGoalRepository::class.java)
        val settingsRepository = Mockito.mock(SettingsRepository::class.java)
        val range = range()
        val queryStart = range.startEpochMillis - 20L * 60L * 1_000L
        val exactSourceId = "xdrip_broadcast:sender"
        val readings = listOf(
            reading("first", range.startEpochMillis, exactSourceId),
            reading("second", range.startEpochMillis + 5L * 60L * 1_000L, exactSourceId),
        )
        Mockito.`when`(
            glucoseRepository.readingsBetweenExactSource(
                exactSourceId,
                queryStart,
                range.endExclusiveEpochMillis,
            ),
        ).thenReturn(readings)
        Mockito.`when`(goalRepository.observeSettings()).thenReturn(
            flowOf(GlycemicPlannerSettings()),
        )
        Mockito.`when`(settingsRepository.observe()).thenReturn(
            flowOf(DefaultCoachSettings.create()),
        )
        val loader = HistoryExplorerLoader(
            glucoseRepository = glucoseRepository,
            glycemicGoalRepository = goalRepository,
            settingsRepository = settingsRepository,
            computationDispatcher = Dispatchers.Unconfined,
        )

        val result = loader.load(exactSourceId, range)

        assertEquals(exactSourceId, result.chart.sourceId)
        Mockito.verify(glucoseRepository).readingsBetweenExactSource(
            exactSourceId,
            queryStart,
            range.endExclusiveEpochMillis,
        )
        Mockito.verify(glucoseRepository, Mockito.never()).readingsBetween(
            Mockito.anyLong(),
            Mockito.anyLong(),
        )
        Mockito.verify(glucoseRepository, Mockito.never()).refresh()
        Mockito.verify(glucoseRepository, Mockito.never()).refreshExactSource(Mockito.anyString())
    }

    @Test
    fun `newer range token prevents older result publication`() {
        val gate = HistoryExplorerRequestGate()
        gate.updateVisibility(true, "source-a")
        val older = gate.begin("source-a")
        val newer = gate.begin("source-a")

        assertFalse(gate.canPublish(older))
        assertTrue(gate.canPublish(newer))
    }

    @Test
    fun `rapid six twelve and twenty four hour selections publish only the final request`() {
        val gate = HistoryExplorerRequestGate()
        gate.updateVisibility(true, "source-a")

        val sixHours = gate.begin("source-a")
        val twelveHours = gate.begin("source-a")
        val twentyFourHours = gate.begin("source-a")

        assertFalse(gate.canPublish(sixHours))
        assertFalse(gate.canPublish(twelveHours))
        assertTrue(gate.canPublish(twentyFourHours))
    }

    @Test
    fun `six and twelve hour loads remain exact source local only`() = runTest {
        val glucoseRepository = Mockito.mock(GlucoseRepository::class.java)
        val goalRepository = Mockito.mock(GlycemicGoalRepository::class.java)
        val settingsRepository = Mockito.mock(SettingsRepository::class.java)
        val exactSourceId = "nightscout:active"
        Mockito.`when`(goalRepository.observeSettings()).thenReturn(
            flowOf(GlycemicPlannerSettings()),
        )
        Mockito.`when`(settingsRepository.observe()).thenReturn(
            flowOf(DefaultCoachSettings.create()),
        )
        val loader = HistoryExplorerLoader(
            glucoseRepository = glucoseRepository,
            glycemicGoalRepository = goalRepository,
            settingsRepository = settingsRepository,
            computationDispatcher = Dispatchers.Unconfined,
        )

        listOf(
            HistoryPeriodPreset.HOURS_6 to 6L,
            HistoryPeriodPreset.HOURS_12 to 12L,
        ).forEach { (preset, hours) ->
            val range = HistoryRange(
                preset = preset,
                startEpochMillis = 1_000_000_000L,
                endExclusiveEpochMillis = 1_000_000_000L + hours * 60L * 60L * 1_000L,
                displayTimeZoneId = "UTC",
                calendarDayCount = 1,
                includesPartialLatestDay = true,
            )
            val queryStart = range.startEpochMillis - 20L * 60L * 1_000L
            Mockito.`when`(
                glucoseRepository.readingsBetweenExactSource(
                    exactSourceId,
                    queryStart,
                    range.endExclusiveEpochMillis,
                ),
            ).thenReturn(emptyList())

            val result = loader.load(exactSourceId, range)

            assertEquals(GlucoseChartStatus.NO_DATA, result.chart.status)
            assertEquals(
                SelectedPeriodGmiAvailability.INSUFFICIENT_DURATION,
                result.selectedPeriodGmi.availability,
            )
            Mockito.verify(glucoseRepository).readingsBetweenExactSource(
                exactSourceId,
                queryStart,
                range.endExclusiveEpochMillis,
            )
        }
        Mockito.verify(glucoseRepository, Mockito.never()).readingsBetween(
            Mockito.anyLong(),
            Mockito.anyLong(),
        )
        Mockito.verify(glucoseRepository, Mockito.never()).refresh()
        Mockito.verify(glucoseRepository, Mockito.never()).refreshExactSource(Mockito.anyString())
    }

    @Test
    fun `source switch prevents previous source publication`() {
        val gate = HistoryExplorerRequestGate()
        gate.updateVisibility(true, "source-a")
        val previousSource = gate.begin("source-a")
        gate.updateVisibility(true, "source-b")
        val currentSource = gate.begin("source-b")

        assertFalse(gate.canPublish(previousSource))
        assertTrue(gate.canPublish(currentSource))
    }

    @Test
    fun `source switch away and back cannot reauthorize an old token`() {
        val gate = HistoryExplorerRequestGate()
        gate.updateVisibility(true, "source-a")
        val oldSourceA = gate.begin("source-a")

        gate.updateVisibility(true, "source-b")
        gate.updateVisibility(true, "source-a")

        assertFalse(gate.canPublish(oldSourceA))
    }

    @Test
    fun `leaving History prevents in flight publication`() {
        val gate = HistoryExplorerRequestGate()
        gate.updateVisibility(true, "source-a")
        val token = gate.begin("source-a")

        gate.updateVisibility(false, "source-a")

        assertFalse(gate.canPublish(token))
    }

    @Test
    fun `custom draft invalidation prevents previous fixed result publication`() {
        val gate = HistoryExplorerRequestGate()
        gate.updateVisibility(true, "source-a")
        val fixedRange = gate.begin("source-a")

        gate.invalidate()

        assertFalse(gate.canPublish(fixedRange))
    }

    @Test
    fun `user selection wins over delayed persisted preset`() {
        val gate = HistoryPresetInitializationGate()
        assertTrue(gate.shouldReadPersistedPreset())

        gate.recordUserSelection()

        assertNull(gate.acceptPersistedPreset(HistoryPeriodPreset.DAYS_90))
        assertFalse(gate.shouldReadPersistedPreset())
    }

    @Test
    fun `old source result is hidden before replacement request publishes`() {
        val range = range()
        val oldResult = GlucoseChartResult(
            sourceId = "source-a",
            range = range,
            segments = emptyList(),
            coverage = HistoryCoverage(range.durationMillis, 0L, 0.0, 1, range.durationMillis),
            latestMeasurementAtEpochMillis = null,
            status = GlucoseChartStatus.NO_DATA,
            detail = "old",
        )
        val state = HistoryExplorerUiState(
            range = range,
            chart = oldResult,
            loadStatus = HistoryExplorerLoadStatus.READY,
        )

        val sourceSafe = state.forActiveSource("source-b")

        assertNull(sourceSafe.chart)
        assertEquals(HistoryExplorerLoadStatus.LOADING, sourceSafe.loadStatus)
    }

    @Test
    fun `custom draft and error survive navigation and activity recreation`() {
        val savedStateHandle = SavedStateHandle()
        val initialStore = HistoryCustomDraftStore(
            savedStateHandle = savedStateHandle,
            defaultEndDate = LocalDate.parse("2026-08-03"),
        )

        initialStore.updateStartDate("2026-06-01")
        initialStore.updateEndDate("not-a-date")
        initialStore.updateError("Enter both dates as YYYY-MM-DD.")

        // Leaving and re-entering History reads the same ViewModel-backed draft.
        assertEquals("2026-06-01", initialStore.snapshot().startDateInput)
        // Activity recreation receives the retained SavedStateHandle.
        val recreatedStore = HistoryCustomDraftStore(
            savedStateHandle = savedStateHandle,
            defaultEndDate = LocalDate.parse("2026-08-04"),
        )
        val restored = recreatedStore.snapshot()
        assertEquals("2026-06-01", restored.startDateInput)
        assertEquals("not-a-date", restored.endDateInput)
        assertEquals("Enter both dates as YYYY-MM-DD.", restored.inputError)
    }

    @Test
    fun `custom draft bounds pasted date input before saved state`() {
        val store = HistoryCustomDraftStore(
            savedStateHandle = SavedStateHandle(),
            defaultEndDate = LocalDate.parse("2026-08-03"),
        )

        val stored = store.updateStartDate("2".repeat(1_000))

        assertEquals(32, stored.startDateInput.length)
    }

    private fun range() = HistoryRange(
        preset = HistoryPeriodPreset.DAYS_14,
        startEpochMillis = 1_000_000_000L,
        endExclusiveEpochMillis = 1_000_000_000L + 14L * 24L * 60L * 60L * 1_000L,
        displayTimeZoneId = "UTC",
        calendarDayCount = 14,
        includesPartialLatestDay = true,
    )

    private fun reading(id: String, measuredAt: Long, sourceId: String) = GlucoseReading(
        id = id,
        valueMgDl = 120,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = measuredAt,
        receivedAtEpochMillis = measuredAt,
        sourceId = sourceId,
    )
}
