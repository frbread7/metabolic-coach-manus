package com.young.metaboliccoach.ui

import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseTrend
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.SelectedPeriodGmiAvailability
import com.young.metaboliccoach.core.model.SelectedPeriodGmiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewportLoaderTest {
    @Test
    fun `viewport load queries exact source with lead and half open endpoint`() = runTest {
        val repository = Mockito.mock(GlucoseRepository::class.java)
        val selectedRange = selectedRange()
        val viewport = HistoryViewport(
            startEpochMillis = selectedRange.startEpochMillis + 30L * DAY_MILLIS,
            endExclusiveEpochMillis = selectedRange.startEpochMillis +
                30L * DAY_MILLIS + 12L * HOUR_MILLIS,
        )
        val queryStart = viewport.startEpochMillis - 20L * MINUTE_MILLIS
        val queryEndInclusive = viewport.endExclusiveEpochMillis - 1L
        val rows = listOf(
            reading("lead", viewport.startEpochMillis - 5L * MINUTE_MILLIS),
            reading("first", viewport.startEpochMillis),
            reading("second", viewport.startEpochMillis + 5L * MINUTE_MILLIS),
            reading("exclusive-end", viewport.endExclusiveEpochMillis),
            reading("other-source", viewport.startEpochMillis + MINUTE_MILLIS, "other"),
        )
        Mockito.`when`(
            repository.readingsBetweenExactSource(SOURCE, queryStart, queryEndInclusive),
        ).thenReturn(rows)
        val loader = HistoryViewportLoader(repository, Dispatchers.Unconfined)

        val result = loader.load(SOURCE, selectedRange, viewport)

        assertEquals(SOURCE, result.sourceId)
        assertEquals(selectedRange, result.selectedRange)
        assertEquals(viewport, result.viewport)
        assertEquals(viewport.startEpochMillis + 5L * MINUTE_MILLIS, result.chart.latestMeasurementAtEpochMillis)
        assertTrue(result.chart.segments.flatMap { it.buckets }.all {
            it.startEpochMillis >= viewport.startEpochMillis &&
                it.startEpochMillis < viewport.endExclusiveEpochMillis
        })
        Mockito.verify(repository).readingsBetweenExactSource(
            SOURCE,
            queryStart,
            queryEndInclusive,
        )
        Mockito.verify(repository, Mockito.never()).readingsBetween(
            Mockito.anyLong(),
            Mockito.anyLong(),
        )
        Mockito.verify(repository, Mockito.never()).refresh()
        Mockito.verify(repository, Mockito.never()).refreshExactSource(Mockito.anyString())
    }

    @Test
    fun `zoomed long period rebuilds raw viewport detail instead of parent buckets`() = runTest {
        val repository = Mockito.mock(GlucoseRepository::class.java)
        val selectedRange = selectedRange()
        val viewport = HistoryViewport(
            startEpochMillis = selectedRange.startEpochMillis + 40L * DAY_MILLIS,
            endExclusiveEpochMillis = selectedRange.startEpochMillis +
                40L * DAY_MILLIS + 12L * HOUR_MILLIS,
        )
        val rows = buildList {
            var timestamp = viewport.startEpochMillis
            var index = 0
            while (timestamp < viewport.endExclusiveEpochMillis) {
                add(reading("row-$index", timestamp))
                timestamp += 5L * MINUTE_MILLIS
                index += 1
            }
        }
        Mockito.`when`(
            repository.readingsBetweenExactSource(
                SOURCE,
                viewport.startEpochMillis - 20L * MINUTE_MILLIS,
                viewport.endExclusiveEpochMillis - 1L,
            ),
        ).thenReturn(rows)
        val loader = HistoryViewportLoader(repository, Dispatchers.Unconfined)

        val result = loader.load(SOURCE, selectedRange, viewport)
        val buckets = result.chart.segments.flatMap { it.buckets }

        assertTrue(buckets.size > 100)
        assertTrue(buckets.all { it.endExclusiveEpochMillis - it.startEpochMillis == 1L })
        assertEquals(HistoryPeriodPreset.DAYS_90, result.chart.range.preset)
        assertEquals(viewport.startEpochMillis, result.chart.range.startEpochMillis)
        assertEquals(viewport.endExclusiveEpochMillis, result.chart.range.endExclusiveEpochMillis)
    }

    @Test
    fun `viewport request gate rejects stale range source viewport and visibility`() {
        val gate = HistoryViewportRequestGate()
        val selected = selectedRange()
        val firstViewport = HistoryViewport(
            selected.startEpochMillis,
            selected.startEpochMillis + 12L * HOUR_MILLIS,
        )
        val secondViewport = HistoryViewport(
            selected.startEpochMillis + HOUR_MILLIS,
            selected.startEpochMillis + 13L * HOUR_MILLIS,
        )
        gate.updateVisibility(true, SOURCE)
        val first = gate.begin(SOURCE, selected, firstViewport)
        val second = gate.begin(SOURCE, selected, secondViewport)

        assertFalse(gate.canPublish(first, selected, firstViewport))
        assertFalse(gate.canPublish(second, selected, firstViewport))
        assertTrue(gate.canPublish(second, selected, secondViewport))

        gate.updateVisibility(true, "other")
        assertFalse(gate.canPublish(second, selected, secondViewport))
        gate.updateVisibility(false, "other")
        assertFalse(gate.canPublish(second, selected, secondViewport))
    }

    @Test
    fun `source switch away and back cannot reauthorize an old viewport request`() {
        val gate = HistoryViewportRequestGate()
        val selected = selectedRange()
        val viewport = HistoryViewport(
            selected.startEpochMillis,
            selected.startEpochMillis + HOUR_MILLIS,
        )
        gate.updateVisibility(true, SOURCE)
        val old = gate.begin(SOURCE, selected, viewport)

        gate.updateVisibility(true, "other")
        gate.updateVisibility(true, SOURCE)

        assertFalse(gate.canPublish(old, selected, viewport))
    }

    @Test
    fun `debouncer waits two hundred milliseconds and runs only final request`() = runTest {
        val completed = mutableListOf<String>()
        val debouncer = HistoryViewportDebouncer(this)

        debouncer.submit { completed += "old" }
        advanceTimeBy(HISTORY_VIEWPORT_DEBOUNCE_MILLIS - 1L)
        runCurrent()
        assertTrue(completed.isEmpty())

        debouncer.submit { completed += "final" }
        advanceTimeBy(HISTORY_VIEWPORT_DEBOUNCE_MILLIS - 1L)
        runCurrent()
        assertTrue(completed.isEmpty())
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf("final"), completed)
    }

    @Test
    fun `requested and rendered identities change atomically without clearing GMI`() {
        val selected = selectedRange()
        val oldViewport = HistoryViewport(
            selected.startEpochMillis,
            selected.startEpochMillis + 12L * HOUR_MILLIS,
        )
        val newViewport = HistoryViewport(
            selected.startEpochMillis + HOUR_MILLIS,
            selected.startEpochMillis + 7L * HOUR_MILLIS,
        )
        val oldChart = emptyChart(selected, oldViewport)
        val oldRendered = RenderedHistoryViewport(SOURCE, selected, oldViewport, oldChart)
        val gmi = SelectedPeriodGmiResult(
            sourceId = SOURCE,
            range = selected,
            availability = SelectedPeriodGmiAvailability.INSUFFICIENT_COVERAGE,
            detail = "unchanged",
        )
        val initial = HistoryExplorerUiState(
            selectedRange = selected,
            requestedViewport = oldViewport,
            renderedViewport = oldRendered,
            selectedPeriodGmi = gmi,
        )

        val pending = initial.withViewportRequest(newViewport)

        assertEquals(newViewport, pending.requestedViewport)
        assertEquals(oldRendered, pending.renderedViewport)
        assertEquals(gmi, pending.selectedPeriodGmi)
        assertEquals(HistoryViewportLoadStatus.DEBOUNCING, pending.viewportLoadStatus)

        val mismatched = oldRendered.copy(viewport = oldViewport)
        assertEquals(pending, pending.withRenderedViewport(mismatched))

        val newRendered = RenderedHistoryViewport(
            SOURCE,
            selected,
            newViewport,
            emptyChart(selected, newViewport),
        )
        val published = pending.withRenderedViewport(newRendered)

        assertEquals(newRendered, published.renderedViewport)
        assertEquals(newViewport, published.requestedViewport)
        assertEquals(gmi, published.selectedPeriodGmi)
        assertEquals(HistoryViewportLoadStatus.READY, published.viewportLoadStatus)
        assertEquals(
            newRendered,
            published.withViewportFailure("failed").renderedViewport,
        )
        assertEquals(gmi, published.withViewportFailure("failed").selectedPeriodGmi)
    }

    @Test
    fun `coordinator rejects cancellation ignoring obsolete completion and preserves GMI`() = runTest {
        val selected = selectedRange()
        val full = HistoryViewport(selected.startEpochMillis, selected.endExclusiveEpochMillis)
        val first = HistoryViewport(
            selected.startEpochMillis,
            selected.startEpochMillis + 12L * HOUR_MILLIS,
        )
        val final = HistoryViewport(
            selected.startEpochMillis + HOUR_MILLIS,
            selected.startEpochMillis + 7L * HOUR_MILLIS,
        )
        val gmi = selectedPeriodGmi(selected)
        val originalRendered = rendered(selected, full)
        var state = HistoryExplorerUiState(
            selectedRange = selected,
            requestedViewport = full,
            renderedViewport = originalRendered,
            selectedPeriodGmi = gmi,
        )
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val loaded = mutableListOf<HistoryViewport>()
        val coordinator = HistoryViewportCoordinator(
            scope = this,
            loadViewport = { _, _, viewport ->
                loaded += viewport
                if (viewport == first) {
                    firstStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirst.await() }
                }
                rendered(selected, viewport)
            },
            readState = { state },
            writeState = { state = it },
        )
        coordinator.updateVisibility(true, SOURCE)

        coordinator.request(SOURCE, selected, first)
        advanceTimeBy(HISTORY_VIEWPORT_DEBOUNCE_MILLIS)
        runCurrent()
        firstStarted.await()

        coordinator.request(SOURCE, selected, final)
        assertEquals(final, state.requestedViewport)
        assertEquals(originalRendered, state.renderedViewport)
        assertEquals(gmi, state.selectedPeriodGmi)
        advanceTimeBy(HISTORY_VIEWPORT_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(final, state.renderedViewport?.viewport)
        assertEquals(gmi, state.selectedPeriodGmi)
        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(listOf(first, final), loaded)
        assertEquals(final, state.renderedViewport?.viewport)
        assertEquals(gmi, state.selectedPeriodGmi)
    }

    @Test
    fun `coordinator invalidation and source switch reject an in flight result`() = runTest {
        val selected = selectedRange()
        val full = HistoryViewport(selected.startEpochMillis, selected.endExclusiveEpochMillis)
        val requested = HistoryViewport(
            selected.startEpochMillis,
            selected.startEpochMillis + 6L * HOUR_MILLIS,
        )
        val originalRendered = rendered(selected, full)
        var state = HistoryExplorerUiState(
            selectedRange = selected,
            requestedViewport = full,
            renderedViewport = originalRendered,
            selectedPeriodGmi = selectedPeriodGmi(selected),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = HistoryViewportCoordinator(
            scope = this,
            loadViewport = { _, _, viewport ->
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                rendered(selected, viewport)
            },
            readState = { state },
            writeState = { state = it },
        )
        coordinator.updateVisibility(true, SOURCE)
        coordinator.request(SOURCE, selected, requested)
        advanceTimeBy(HISTORY_VIEWPORT_DEBOUNCE_MILLIS)
        runCurrent()
        started.await()

        coordinator.updateVisibility(true, "other")
        coordinator.invalidate()
        release.complete(Unit)
        runCurrent()

        assertEquals(originalRendered, state.renderedViewport)
        assertEquals(requested, state.requestedViewport)
        assertEquals(HistoryViewportLoadStatus.LOADING, state.viewportLoadStatus)
    }

    @Test
    fun `render reducer rejects mismatched source and chart range bundles`() {
        val selected = selectedRange()
        val viewport = HistoryViewport(
            selected.startEpochMillis,
            selected.startEpochMillis + 6L * HOUR_MILLIS,
        )
        val initial = HistoryExplorerUiState(
            selectedRange = selected,
            requestedViewport = viewport,
            selectedPeriodGmi = selectedPeriodGmi(selected),
        )
        val valid = rendered(selected, viewport)

        assertEquals(initial, initial.withRenderedViewport(valid.copy(sourceId = "other")))
        assertEquals(
            initial,
            initial.withRenderedViewport(
                valid.copy(
                    chart = valid.chart.copy(
                        range = valid.chart.range.copy(
                            endExclusiveEpochMillis = viewport.endExclusiveEpochMillis - 1L,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(valid, initial.withRenderedViewport(valid).renderedViewport)
    }

    private fun selectedRange() = HistoryRange(
        preset = HistoryPeriodPreset.DAYS_90,
        startEpochMillis = 1_000_000_000L,
        endExclusiveEpochMillis = 1_000_000_000L + 90L * DAY_MILLIS,
        displayTimeZoneId = "UTC",
        calendarDayCount = 90,
        includesPartialLatestDay = true,
    )

    private fun reading(
        id: String,
        measuredAtEpochMillis: Long,
        sourceId: String = SOURCE,
    ) = GlucoseReading(
        id = id,
        valueMgDl = 140,
        trend = GlucoseTrend.STABLE,
        deltaMgDl = 0,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = sourceId,
    )

    private fun emptyChart(
        selectedRange: HistoryRange,
        viewport: HistoryViewport,
    ) = com.young.metaboliccoach.core.model.GlucoseChartResult(
        sourceId = SOURCE,
        range = selectedRange.copy(
            startEpochMillis = viewport.startEpochMillis,
            endExclusiveEpochMillis = viewport.endExclusiveEpochMillis,
        ),
        segments = emptyList(),
        coverage = com.young.metaboliccoach.core.model.HistoryCoverage(
            viewport.durationMillis,
            0L,
            0.0,
            0,
            0L,
        ),
        latestMeasurementAtEpochMillis = null,
        status = com.young.metaboliccoach.core.model.GlucoseChartStatus.NO_DATA,
        detail = "ready",
    )

    private fun rendered(
        selectedRange: HistoryRange,
        viewport: HistoryViewport,
    ) = RenderedHistoryViewport(
        sourceId = SOURCE,
        selectedRange = selectedRange,
        viewport = viewport,
        chart = emptyChart(selectedRange, viewport),
    )

    private fun selectedPeriodGmi(selectedRange: HistoryRange) = SelectedPeriodGmiResult(
        sourceId = SOURCE,
        range = selectedRange,
        availability = SelectedPeriodGmiAvailability.INSUFFICIENT_COVERAGE,
        detail = "unchanged",
    )

    private companion object {
        const val SOURCE = "nightscout:active"
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60L * MINUTE_MILLIS
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
