package com.young.metaboliccoach.ui

import com.young.metaboliccoach.core.model.HistoryRange
import kotlin.math.abs
import kotlin.math.roundToLong

/** Transient phone-only half-open UTC chart interval. It is never persisted. */
data class HistoryViewport(
    val startEpochMillis: Long,
    val endExclusiveEpochMillis: Long,
) {
    val durationMillis: Long
        get() = checkedDuration(startEpochMillis, endExclusiveEpochMillis) ?: 0L
}

/** Pure deterministic viewport math; no storage, provider, clock, or UI dependency. */
internal object HistoryViewportMath {
    const val MINIMUM_DURATION_MILLIS = 30L * 60L * 1_000L

    fun full(selectedRange: HistoryRange): HistoryViewport? =
        checkedDuration(
            selectedRange.startEpochMillis,
            selectedRange.endExclusiveEpochMillis,
        )?.takeIf { it > 0L }?.let {
            HistoryViewport(
                selectedRange.startEpochMillis,
                selectedRange.endExclusiveEpochMillis,
            )
        }

    fun reset(viewport: HistoryViewport, selectedRange: HistoryRange): HistoryViewport =
        full(selectedRange) ?: viewport

    fun zoom(
        viewport: HistoryViewport,
        selectedRange: HistoryRange,
        zoomScale: Double,
        focalFraction: Double,
    ): HistoryViewport {
        val selected = full(selectedRange) ?: return viewport
        if (!viewport.isInside(selected) || !zoomScale.isFinite() || zoomScale <= 0.0) {
            return viewport
        }
        if (!focalFraction.isFinite() || focalFraction !in 0.0..1.0) return viewport

        val selectedDuration = selected.durationMillis
        val currentDuration = viewport.durationMillis
        val minimumDuration = minOf(MINIMUM_DURATION_MILLIS, selectedDuration)
        val proposedDuration = (currentDuration.toDouble() / zoomScale)
            .takeIf(Double::isFinite)
            ?.roundToLong()
            ?: return viewport
        val newDuration = proposedDuration.coerceIn(minimumDuration, selectedDuration)
        if (newDuration == currentDuration) return viewport

        val focalOffset = currentDuration.toDouble() * focalFraction
        val proposedStart = viewport.startEpochMillis.toDouble() + focalOffset -
            newDuration.toDouble() * focalFraction
        if (!proposedStart.isFinite() || proposedStart < Long.MIN_VALUE || proposedStart > Long.MAX_VALUE) {
            return viewport
        }
        val maximumStart = checkedSubtract(selected.endExclusiveEpochMillis, newDuration)
            ?: return viewport
        val newStart = proposedStart.roundToLong().coerceIn(
            selected.startEpochMillis,
            maximumStart,
        )
        val newEnd = checkedAdd(newStart, newDuration) ?: return viewport
        return HistoryViewport(newStart, newEnd)
    }

    fun pan(
        viewport: HistoryViewport,
        selectedRange: HistoryRange,
        pixelDeltaX: Double,
        chartWidthPixels: Double,
    ): HistoryViewport {
        val selected = full(selectedRange) ?: return viewport
        if (!viewport.isInside(selected)) return viewport
        if (!pixelDeltaX.isFinite() || !chartWidthPixels.isFinite() || chartWidthPixels <= 0.0) {
            return viewport
        }
        val timeDelta = -pixelDeltaX / chartWidthPixels * viewport.durationMillis.toDouble()
        if (!timeDelta.isFinite() || timeDelta < Long.MIN_VALUE || timeDelta > Long.MAX_VALUE) {
            return viewport
        }
        val roundedDelta = timeDelta.roundToLong()
        if (roundedDelta == 0L) return viewport
        val proposedStart = checkedAdd(viewport.startEpochMillis, roundedDelta) ?: return viewport
        val maximumStart = checkedSubtract(
            selected.endExclusiveEpochMillis,
            viewport.durationMillis,
        ) ?: return viewport
        val newStart = proposedStart.coerceIn(selected.startEpochMillis, maximumStart)
        val newEnd = checkedAdd(newStart, viewport.durationMillis) ?: return viewport
        return HistoryViewport(newStart, newEnd)
    }

    fun zoomIn(viewport: HistoryViewport, selectedRange: HistoryRange): HistoryViewport =
        zoom(viewport, selectedRange, zoomScale = 2.0, focalFraction = 0.5)

    fun zoomOut(viewport: HistoryViewport, selectedRange: HistoryRange): HistoryViewport =
        zoom(viewport, selectedRange, zoomScale = 0.5, focalFraction = 0.5)

    fun isHorizontalIntent(deltaX: Double, deltaY: Double): Boolean =
        deltaX.isFinite() &&
            deltaY.isFinite() &&
            abs(deltaX) > 0.0 &&
            abs(deltaX) >= abs(deltaY) * HORIZONTAL_DOMINANCE_RATIO

    private fun HistoryViewport.isInside(selected: HistoryViewport): Boolean =
        durationMillis > 0L &&
            startEpochMillis >= selected.startEpochMillis &&
            endExclusiveEpochMillis <= selected.endExclusiveEpochMillis

    private const val HORIZONTAL_DOMINANCE_RATIO = 1.25
}

private fun checkedDuration(start: Long, endExclusive: Long): Long? =
    checkedSubtract(endExclusive, start)?.takeIf { it > 0L }

private fun checkedAdd(left: Long, right: Long): Long? =
    runCatching { Math.addExact(left, right) }.getOrNull()

private fun checkedSubtract(left: Long, right: Long): Long? =
    runCatching { Math.subtractExact(left, right) }.getOrNull()
