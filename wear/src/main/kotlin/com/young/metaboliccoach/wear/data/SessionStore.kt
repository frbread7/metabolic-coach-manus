package com.young.metaboliccoach.wear.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.young.metaboliccoach.core.model.CoachReason
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("active_session")

data class ActiveWearSession(
    val id: String,
    val type: InterventionType,
    val startedAtEpochMillis: Long,
    val durationMinutes: Int?,
    val targetFloors: Int?,
)

@Singleton
class SessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val replica: Flow<WearSessionReplica> =
        context.sessionDataStore.data.map(::decode)

    val active: Flow<ActiveWearSession?> = replica.map { it.activeSession }

    val pendingCommand: Flow<QuickActionCommand?> =
        replica.map { it.pendingCommand }

    suspend fun queueStart(
        session: ActiveWearSession,
        command: QuickActionCommand,
    ): Boolean = updateIfPossible {
        WearSessionReplicaReducer.queueStart(it, session, command)
    }

    suspend fun queueCompletion(command: QuickActionCommand): Boolean = updateIfPossible {
        WearSessionReplicaReducer.queueCompletion(it, command)
    }

    suspend fun markTransported(commandId: String) {
        update {
            WearSessionReplicaReducer.markTransported(it, commandId)
        }
    }

    suspend fun markTransportError(commandId: String) {
        update {
            WearSessionReplicaReducer.markTransportError(it, commandId)
        }
    }

    suspend fun reconcile(
        session: InterventionSession?,
        ack: SessionCommandAck?,
    ) {
        update {
            WearSessionReplicaReducer.reconcile(it, session, ack)
        }
    }

    suspend fun hasPendingMutation(): Boolean =
        replica.first().pendingCommand != null

    private suspend fun updateIfPossible(
        transform: (WearSessionReplica) -> WearSessionReplica,
    ): Boolean {
        var updated = false
        context.sessionDataStore.edit { values ->
            val current = decode(values)
            val next = runCatching { transform(current) }.getOrNull() ?: return@edit
            encode(values, next)
            updated = true
        }
        return updated
    }

    private suspend fun update(transform: (WearSessionReplica) -> WearSessionReplica) {
        context.sessionDataStore.edit { values ->
            encode(values, transform(decode(values)))
        }
    }

    private fun decode(values: Preferences): WearSessionReplica {
        val activeSession = values[ACTIVE_TYPE]
            ?.let { runCatching { InterventionType.valueOf(it) }.getOrNull() }
            ?.let { type ->
                ActiveWearSession(
                    id = values[ACTIVE_ID] ?: return@let null,
                    type = type,
                    startedAtEpochMillis = values[ACTIVE_STARTED_AT] ?: return@let null,
                    durationMinutes = values[ACTIVE_DURATION],
                    targetFloors = values[ACTIVE_TARGET_FLOORS],
                )
            }
        val pendingCommand = values[PENDING_TYPE]
            ?.let { runCatching { QuickActionType.valueOf(it) }.getOrNull() }
            ?.let { type ->
                QuickActionCommand(
                    id = values[PENDING_ID] ?: return@let null,
                    type = type,
                    createdAtEpochMillis = values[PENDING_CREATED_AT] ?: return@let null,
                    sessionId = values[PENDING_SESSION_ID],
                    recommendationId = values[PENDING_RECOMMENDATION_ID],
                    recommendationValidUntilEpochMillis =
                        values[PENDING_RECOMMENDATION_VALID_UNTIL],
                    recommendationReason = values[PENDING_RECOMMENDATION_REASON]
                        ?.let { runCatching { CoachReason.valueOf(it) }.getOrNull() },
                    recommendationAlgorithmVersion =
                        values[PENDING_RECOMMENDATION_ALGORITHM_VERSION],
                    recommendationCreatedAtEpochMillis =
                        values[PENDING_RECOMMENDATION_CREATED_AT],
                    triggerContextId = values[PENDING_TRIGGER_CONTEXT_ID],
                    triggerAtEpochMillis = values[PENDING_TRIGGER_AT],
                    glucoseSourceId = values[PENDING_GLUCOSE_SOURCE_ID],
                    safetyReadingId = values[PENDING_SAFETY_READING_ID],
                    safetyReadingAtEpochMillis = values[PENDING_SAFETY_READING_AT],
                    dataResetId = values[PENDING_DATA_RESET_ID],
                )
            }
        val pendingMutation = values[PENDING_MUTATION]
            ?.let { runCatching { PendingSessionMutation.valueOf(it) }.getOrNull() }
        return WearSessionReplica(
            activeSession = activeSession,
            pendingCommand = pendingCommand,
            pendingMutation = pendingMutation,
            pendingTransported = values[PENDING_TRANSPORTED] ?: false,
            completionTombstoneSessionId = values[COMPLETION_TOMBSTONE],
            syncMessage = values[SYNC_MESSAGE],
        )
    }

    private fun encode(
        values: androidx.datastore.preferences.core.MutablePreferences,
        replica: WearSessionReplica,
    ) {
        values.clear()
        replica.activeSession?.let { session ->
            values[ACTIVE_ID] = session.id
            values[ACTIVE_TYPE] = session.type.name
            values[ACTIVE_STARTED_AT] = session.startedAtEpochMillis
            session.durationMinutes?.let { values[ACTIVE_DURATION] = it }
            session.targetFloors?.let { values[ACTIVE_TARGET_FLOORS] = it }
        }
        replica.pendingCommand?.let { command ->
            values[PENDING_ID] = command.id
            values[PENDING_TYPE] = command.type.name
            values[PENDING_CREATED_AT] = command.createdAtEpochMillis
            command.sessionId?.let { values[PENDING_SESSION_ID] = it }
            command.recommendationId?.let { values[PENDING_RECOMMENDATION_ID] = it }
            command.recommendationValidUntilEpochMillis?.let {
                values[PENDING_RECOMMENDATION_VALID_UNTIL] = it
            }
            command.recommendationReason?.let {
                values[PENDING_RECOMMENDATION_REASON] = it.name
            }
            command.recommendationAlgorithmVersion?.let {
                values[PENDING_RECOMMENDATION_ALGORITHM_VERSION] = it
            }
            command.recommendationCreatedAtEpochMillis?.let {
                values[PENDING_RECOMMENDATION_CREATED_AT] = it
            }
            command.triggerContextId?.let { values[PENDING_TRIGGER_CONTEXT_ID] = it }
            command.triggerAtEpochMillis?.let { values[PENDING_TRIGGER_AT] = it }
            command.glucoseSourceId?.let { values[PENDING_GLUCOSE_SOURCE_ID] = it }
            command.safetyReadingId?.let { values[PENDING_SAFETY_READING_ID] = it }
            command.safetyReadingAtEpochMillis?.let {
                values[PENDING_SAFETY_READING_AT] = it
            }
            command.dataResetId?.let { values[PENDING_DATA_RESET_ID] = it }
        }
        replica.pendingMutation?.let { values[PENDING_MUTATION] = it.name }
        values[PENDING_TRANSPORTED] = replica.pendingTransported
        replica.completionTombstoneSessionId?.let { values[COMPLETION_TOMBSTONE] = it }
        replica.syncMessage?.let { values[SYNC_MESSAGE] = it }
    }

    suspend fun clearForDataReset() {
        context.sessionDataStore.edit { values ->
            values.clear()
        }
    }

    private companion object {
        val ACTIVE_ID = stringPreferencesKey("active_id")
        val ACTIVE_TYPE = stringPreferencesKey("active_type")
        val ACTIVE_STARTED_AT = longPreferencesKey("active_started_at")
        val ACTIVE_DURATION = intPreferencesKey("active_duration")
        val ACTIVE_TARGET_FLOORS = intPreferencesKey("active_target_floors")
        val PENDING_ID = stringPreferencesKey("pending_id")
        val PENDING_TYPE = stringPreferencesKey("pending_type")
        val PENDING_CREATED_AT = longPreferencesKey("pending_created_at")
        val PENDING_SESSION_ID = stringPreferencesKey("pending_session_id")
        val PENDING_RECOMMENDATION_ID = stringPreferencesKey("pending_recommendation_id")
        val PENDING_RECOMMENDATION_VALID_UNTIL =
            longPreferencesKey("pending_recommendation_valid_until")
        val PENDING_RECOMMENDATION_REASON =
            stringPreferencesKey("pending_recommendation_reason")
        val PENDING_RECOMMENDATION_ALGORITHM_VERSION =
            intPreferencesKey("pending_recommendation_algorithm_version")
        val PENDING_RECOMMENDATION_CREATED_AT =
            longPreferencesKey("pending_recommendation_created_at")
        val PENDING_TRIGGER_CONTEXT_ID = stringPreferencesKey("pending_trigger_context_id")
        val PENDING_TRIGGER_AT = longPreferencesKey("pending_trigger_at")
        val PENDING_GLUCOSE_SOURCE_ID = stringPreferencesKey("pending_glucose_source_id")
        val PENDING_SAFETY_READING_ID = stringPreferencesKey("pending_safety_reading_id")
        val PENDING_SAFETY_READING_AT = longPreferencesKey("pending_safety_reading_at")
        val PENDING_DATA_RESET_ID = stringPreferencesKey("pending_data_reset_id")
        val PENDING_MUTATION = stringPreferencesKey("pending_mutation")
        val PENDING_TRANSPORTED = booleanPreferencesKey("pending_transported")
        val COMPLETION_TOMBSTONE = stringPreferencesKey("completion_tombstone")
        val SYNC_MESSAGE = stringPreferencesKey("sync_message")
    }
}
