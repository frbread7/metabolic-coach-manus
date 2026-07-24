package com.young.metaboliccoach.core.data.provider

import com.young.metaboliccoach.core.model.GlucoseReading
import com.young.metaboliccoach.core.model.ProviderStatus

interface GlucoseProvider {
    val id: String
    suspend fun status(): ProviderStatus
    suspend fun readSince(startEpochMillis: Long): List<GlucoseReading>
}

