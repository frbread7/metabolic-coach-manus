package com.young.metaboliccoach.core.domain

import kotlinx.coroutines.flow.Flow

interface CoachTimeSource {
    fun nowEpochMillis(): Long
    fun minuteTicks(): Flow<Long>
}
