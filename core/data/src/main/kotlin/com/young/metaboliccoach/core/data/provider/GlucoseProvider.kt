package com.young.metaboliccoach.core.data.provider

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.GlucoseProviderState
import com.young.metaboliccoach.core.model.ProviderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GlucoseProvider {
    val id: String
    fun handlesSource(sourceId: String): Boolean
    fun observeState(): Flow<GlucoseProviderState> = flowOf(GlucoseProviderState.Idle)
    suspend fun status(): ProviderStatus

    /**
     * Retrieves the provider's current snapshot when that capability is available.
     * Providers that only support a single read path leave the default empty result and are
     * handled through [readHistorySince].
     */
    suspend fun readCurrent(): List<GlucoseReading> = emptyList()

    /**
     * Retrieves the requested history after any current snapshot has been published.
     * The default preserves the original provider contract for existing implementations.
     */
    suspend fun readHistorySince(startEpochMillis: Long): List<GlucoseReading> =
        readSince(startEpochMillis)

    /**
     * Retrieves one bounded historical range without changing the current-reading state.
     * Providers may use their native range endpoint; the fallback preserves compatibility for
     * providers that only expose a since-based query.
     */
    suspend fun readHistoryRange(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<GlucoseReading> = readHistorySince(startEpochMillis).filter {
        it.measuredAtEpochMillis in startEpochMillis..endEpochMillis
    }

    suspend fun readSince(startEpochMillis: Long): List<GlucoseReading>
    suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading>
    suspend fun clearRuntimeCache()
}
