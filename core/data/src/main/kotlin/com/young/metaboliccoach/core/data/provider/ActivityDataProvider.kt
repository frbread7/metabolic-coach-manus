package com.young.metaboliccoach.core.data.provider

import com.young.metaboliccoach.core.model.ActivitySnapshot
import com.young.metaboliccoach.core.model.ProviderStatus
import java.time.Instant

interface ActivityDataProvider {
    val id: String
    suspend fun status(): ProviderStatus
    suspend fun readToday(now: Instant = Instant.now()): ActivitySnapshot?
}
