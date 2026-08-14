package com.young.metaboliccoach

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.young.metaboliccoach.core.model.GlucoseChartBucket
import com.young.metaboliccoach.core.model.GlucoseChartResult
import com.young.metaboliccoach.core.model.GlucoseChartSegment
import com.young.metaboliccoach.core.model.GlucoseChartStatus
import com.young.metaboliccoach.core.model.GlucoseUnit
import com.young.metaboliccoach.core.model.HistoryCoverage
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.ui.HistoryExplorerLoadStatus
import com.young.metaboliccoach.ui.HistoryExplorerUiState
import com.young.metaboliccoach.ui.HistoryViewport
import com.young.metaboliccoach.ui.HistoryViewportLoadStatus
import com.young.metaboliccoach.ui.RenderedHistoryViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryExplorerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accessible_controls_expose_boundary_states_and_textual_window() {
        var zoomInClicks = 0
        composeRule.setContent {
            MaterialTheme {
                HistoryExplorerScreen(
                    state = state(),
                    glucoseUnit = GlucoseUnit.MG_DL,
                    onSelectPreset = {},
                    onCustomStartChanged = {},
                    onCustomEndChanged = {},
                    onApplyCustomRange = {},
                    onTransformViewport = { _, _, _, _ -> },
                    onZoomInViewport = { zoomInClicks += 1 },
                    onZoomOutViewport = {},
                    onResetViewport = {},
                    onRetryViewport = {},
                    contentPadding = PaddingValues(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Zoom in history chart").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithContentDescription("Zoom out history chart").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Reset history chart").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Selected period: Last 24 hours").assertIsDisplayed()
        composeRule.onNodeWithText("Visible chart window").assertIsDisplayed()
        composeRule.onNodeWithText("1970-01-01 00:00 to 1970-01-02 00:00")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Visible window 1970-01-01 00:00 to 1970-01-02 00:00. " +
                "Chart shows the full selected period.",
            substring = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Zoom in history chart").performClick()
        composeRule.runOnIdle { assertEquals(1, zoomInClicks) }
    }

    @Test
    fun pending_viewport_keeps_rendered_labels_and_gestures_emit_transform_intent() {
        var transforms = 0
        val requested = HistoryViewport(6L * HOUR_MILLIS, 18L * HOUR_MILLIS)
        lateinit var listState: LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            MaterialTheme {
                HistoryExplorerScreen(
                    state = state(
                        requestedViewport = requested,
                        viewportLoadStatus = HistoryViewportLoadStatus.DEBOUNCING,
                    ),
                    glucoseUnit = GlucoseUnit.MG_DL,
                    onSelectPreset = {},
                    onCustomStartChanged = {},
                    onCustomEndChanged = {},
                    onApplyCustomRange = {},
                    onTransformViewport = { _, _, _, _ -> transforms += 1 },
                    onZoomInViewport = {},
                    onZoomOutViewport = {},
                    onResetViewport = {},
                    onRetryViewport = {},
                    contentPadding = PaddingValues(),
                    listState = listState,
                )
            }
        }

        composeRule.onNodeWithText("Updating visible chart…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1970-01-01 00:00 to 1970-01-02 00:00")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performScrollTo()
        val beforeHorizontal = composeRule.runOnIdle { listPosition(listState) }
        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertTrue(transforms > 0) }
        val afterHorizontal = transforms
        composeRule.runOnIdle { assertEquals(beforeHorizontal, listPosition(listState)) }

        val beforeVertical = composeRule.runOnIdle { listPosition(listState) }
        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertEquals(afterHorizontal, transforms) }
        composeRule.runOnIdle { assertTrue(listPosition(listState) > beforeVertical) }

        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performScrollTo()
        val beforePinch = composeRule.runOnIdle { listPosition(listState) }
        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performTouchInput {
            val nearCenterLeft = Offset(center.x - 24f, center.y)
            val nearCenterRight = Offset(center.x + 24f, center.y)
            val farLeft = Offset(center.x - 72f, center.y)
            val farRight = Offset(center.x + 72f, center.y)
            pinch(nearCenterLeft, farLeft, nearCenterRight, farRight)
        }
        composeRule.runOnIdle { assertTrue(transforms > afterHorizontal) }
        composeRule.runOnIdle { assertEquals(beforePinch, listPosition(listState)) }
        composeRule.onNodeWithText("1970-01-01 00:00 to 1970-01-02 00:00")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun no_data_window_retains_transform_surface_and_vertical_parent_scroll() {
        var transforms = 0
        lateinit var listState: LazyListState
        val visible = HistoryViewport(6L * HOUR_MILLIS, 18L * HOUR_MILLIS)
        composeRule.setContent {
            listState = rememberLazyListState()
            MaterialTheme {
                HistoryExplorerScreen(
                    state = state(
                        requestedViewport = visible,
                        renderedViewport = visible,
                        chart = noDataChart(selectedRange(), visible),
                    ),
                    glucoseUnit = GlucoseUnit.MG_DL,
                    onSelectPreset = {},
                    onCustomStartChanged = {},
                    onCustomEndChanged = {},
                    onApplyCustomRange = {},
                    onTransformViewport = { _, _, _, _ -> transforms += 1 },
                    onZoomInViewport = {},
                    onZoomOutViewport = {},
                    onResetViewport = {},
                    onRetryViewport = {},
                    contentPadding = PaddingValues(),
                    listState = listState,
                )
            }
        }

        composeRule.onNodeWithText("No local readings in this visible window.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertTrue(transforms > 0) }
        val afterHorizontal = transforms
        val beforeVertical = composeRule.runOnIdle { listPosition(listState) }
        composeRule.onNodeWithTag(HISTORY_CHART_TEST_TAG).performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(afterHorizontal, transforms)
            assertTrue(listPosition(listState) > beforeVertical)
        }
    }

    private fun state(
        requestedViewport: HistoryViewport = viewport(),
        renderedViewport: HistoryViewport = viewport(),
        viewportLoadStatus: HistoryViewportLoadStatus = HistoryViewportLoadStatus.READY,
        chart: GlucoseChartResult = chart(selectedRange()),
    ): HistoryExplorerUiState {
        val selectedRange = selectedRange()
        return HistoryExplorerUiState(
            selectedPreset = HistoryPeriodPreset.HOURS_24,
            selectedRange = selectedRange,
            requestedViewport = requestedViewport,
            renderedViewport = RenderedHistoryViewport(
                sourceId = SOURCE,
                selectedRange = selectedRange,
                viewport = renderedViewport,
                chart = chart,
            ),
            loadStatus = HistoryExplorerLoadStatus.READY,
            viewportLoadStatus = viewportLoadStatus,
            viewportDetail = "Ready",
        )
    }

    private fun selectedRange() = HistoryRange(
        preset = HistoryPeriodPreset.HOURS_24,
        startEpochMillis = 0L,
        endExclusiveEpochMillis = DAY_MILLIS,
        displayTimeZoneId = "UTC",
        calendarDayCount = 1,
        includesPartialLatestDay = true,
    )

    private fun viewport() = HistoryViewport(0L, DAY_MILLIS)

    private fun chart(range: HistoryRange): GlucoseChartResult {
        val bucket = GlucoseChartBucket(
            startEpochMillis = HOUR_MILLIS,
            endExclusiveEpochMillis = HOUR_MILLIS + 1L,
            firstMgDl = 120.0,
            lastMgDl = 120.0,
            minimumMgDl = 120.0,
            maximumMgDl = 120.0,
            timeWeightedMeanMgDl = 120.0,
            validDurationMillis = 0L,
        )
        return GlucoseChartResult(
            sourceId = SOURCE,
            range = range,
            segments = listOf(GlucoseChartSegment(listOf(bucket), true, true)),
            coverage = HistoryCoverage(DAY_MILLIS, 0L, 0.0, 1, DAY_MILLIS),
            latestMeasurementAtEpochMillis = HOUR_MILLIS,
            status = GlucoseChartStatus.AVAILABLE,
            detail = "Ready",
        )
    }

    private fun noDataChart(
        range: HistoryRange,
        viewport: HistoryViewport,
    ) = GlucoseChartResult(
        sourceId = SOURCE,
        range = range.copy(
            startEpochMillis = viewport.startEpochMillis,
            endExclusiveEpochMillis = viewport.endExclusiveEpochMillis,
        ),
        segments = emptyList(),
        coverage = HistoryCoverage(viewport.durationMillis, 0L, 0.0, 1, viewport.durationMillis),
        latestMeasurementAtEpochMillis = null,
        status = GlucoseChartStatus.NO_DATA,
        detail = "No locally stored readings are available for this period.",
    )

    private fun listPosition(state: LazyListState): Long =
        state.firstVisibleItemIndex.toLong() * 1_000_000L + state.firstVisibleItemScrollOffset

    private companion object {
        const val SOURCE = "nightscout:test"
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
