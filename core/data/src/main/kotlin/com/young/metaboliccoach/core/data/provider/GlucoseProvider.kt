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
    suspend fun readSince(startEpochMillis: Long): List<GlucoseReading>
    suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading>
    suspend fun clearRuntimeCache()
}
