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

    suspend fun readSince(startEpochMillis: Long): List<GlucoseReading>
    suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading>
    suspend fun clearRuntimeCache()
}
