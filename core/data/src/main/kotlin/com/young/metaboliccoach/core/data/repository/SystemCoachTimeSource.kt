package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.domain.CoachTimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class SystemCoachTimeSource @Inject constructor() : CoachTimeSource {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun minuteTicks(): Flow<Long> = flow {
        while (true) {
            val now = nowEpochMillis()
            emit(now)
            val untilNextMinute = MILLIS_PER_MINUTE - now.mod(MILLIS_PER_MINUTE)
            delay(untilNextMinute.coerceAtLeast(1_000L))
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
