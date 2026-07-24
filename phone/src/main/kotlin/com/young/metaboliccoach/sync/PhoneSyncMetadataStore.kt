package com.young.metaboliccoach.sync

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.core.model.SessionCommandAck
import com.young.metaboliccoach.core.model.SessionCommandOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.phoneSyncMetadataDataStore by preferencesDataStore("phone_sync_metadata")

data class PhoneSyncMetadata(
    val phoneInstanceId: String,
    val stateRevision: Long,
    val lastSessionCommandAck: SessionCommandAck?,
    val dataResetId: String?,
)

@Singleton
class PhoneSyncMetadataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun recordCommandAck(
        command: QuickActionCommand,
        outcome: SessionCommandOutcome,
    ) {
        val incoming = StoredSessionAck(
            ack = SessionCommandAck(
                commandId = command.id,
                sessionId = command.sessionId,
                outcome = outcome,
            ),
            commandType = command.type,
            commandCreatedAtEpochMillis = command.createdAtEpochMillis,
        )
        context.phoneSyncMetadataDataStore.edit { values ->
            values[PHONE_INSTANCE_ID] = values[PHONE_INSTANCE_ID] ?: UUID.randomUUID().toString()
            val history = decodeAckHistory(values)
                .filter {
                    it.commandCreatedAtEpochMillis == null ||
                        it.commandCreatedAtEpochMillis >=
                        System.currentTimeMillis() - MAX_ACK_HISTORY_AGE_MILLIS
                }
                .associateByTo(linkedMapOf()) { it.ack.commandId }
            val recorded = history[command.id] ?: incoming.also {
                history[command.id] = it
            }
            values[ACK_HISTORY] = history.values.mapTo(linkedSetOf(), ::encodeHistoryEntry)
            if (command.type.isSessionMutation()) {
                encodeAck(
                    values = values,
                    storedAck = SessionAckOrderingPolicy.select(
                        current = decodeStoredAck(values),
                        incoming = recorded,
                    ),
                )
            }
        }
    }

    suspend fun recordSessionAck(
        command: QuickActionCommand,
        outcome: SessionCommandOutcome,
    ) = recordCommandAck(command, outcome)

    suspend fun nextPublication(): PhoneSyncMetadata {
        lateinit var metadata: PhoneSyncMetadata
        context.phoneSyncMetadataDataStore.edit { values ->
            val instanceId =
                values[PHONE_INSTANCE_ID] ?: UUID.randomUUID().toString().also {
                    values[PHONE_INSTANCE_ID] = it
                }
            val revision = (values[STATE_REVISION] ?: 0L) + 1L
            values[STATE_REVISION] = revision
            metadata = PhoneSyncMetadata(
                phoneInstanceId = instanceId,
                stateRevision = revision,
                lastSessionCommandAck = decodeStoredAck(values)?.ack,
                dataResetId = values[DATA_RESET_ID],
            )
        }
        return metadata
    }

    suspend fun beginDataReset(
        resetId: String = UUID.randomUUID().toString(),
    ): PhoneSyncMetadata {
        lateinit var metadata: PhoneSyncMetadata
        context.phoneSyncMetadataDataStore.edit { values ->
            values.clear()
            val instanceId = UUID.randomUUID().toString()
            values[PHONE_INSTANCE_ID] = instanceId
            values[STATE_REVISION] = 1L
            values[DATA_RESET_ID] = resetId
            metadata = PhoneSyncMetadata(
                phoneInstanceId = instanceId,
                stateRevision = 1L,
                lastSessionCommandAck = null,
                dataResetId = resetId,
            )
        }
        return metadata
    }

    suspend fun isCommandFromCurrentDataEpoch(command: QuickActionCommand): Boolean {
        val currentResetId =
            context.phoneSyncMetadataDataStore.data.first()[DATA_RESET_ID]
        return CommandDataEpochPolicy.isCurrent(
            currentResetId = currentResetId,
            commandResetId = command.dataResetId,
        )
    }

    suspend fun commandAck(commandId: String): SessionCommandAck? {
        val values = context.phoneSyncMetadataDataStore.data.first()
        return decodeAckHistory(values)
            .firstOrNull { it.ack.commandId == commandId }
            ?.ack
    }

    suspend fun sessionAck(commandId: String): SessionCommandAck? = commandAck(commandId)

    suspend fun prerequisiteStartAck(sessionId: String?): SessionCommandAck? {
        sessionId ?: return null
        val values = context.phoneSyncMetadataDataStore.data.first()
        val candidates = decodeAckHistory(values)
            .filter {
                it.ack.sessionId == sessionId &&
                    it.commandType?.isStartMutation() != false
            }
            .toMutableList()
        decodeStoredAck(values)
            ?.takeIf {
                it.ack.sessionId == sessionId &&
                    it.commandType?.isStartMutation() != false
            }
            ?.let(candidates::add)
        return candidates.maxWithOrNull(
            compareBy<StoredSessionAck> {
                it.commandCreatedAtEpochMillis ?: Long.MIN_VALUE
            }.thenBy { it.ack.commandId },
        )?.ack
    }

    private fun decodeStoredAck(values: Preferences): StoredSessionAck? {
        val commandId = values[ACK_COMMAND_ID] ?: return null
        val outcome = values[ACK_OUTCOME]
            ?.let { runCatching { SessionCommandOutcome.valueOf(it) }.getOrNull() }
            ?: return null
        return StoredSessionAck(
            ack = SessionCommandAck(
                commandId = commandId,
                sessionId = values[ACK_SESSION_ID],
                outcome = outcome,
            ),
            commandType = values[ACK_COMMAND_TYPE]
                ?.let { runCatching { QuickActionType.valueOf(it) }.getOrNull() },
            commandCreatedAtEpochMillis = values[ACK_COMMAND_CREATED_AT],
        )
    }

    private fun decodeAckHistory(values: Preferences): List<StoredSessionAck> = buildList {
        values[ACK_HISTORY].orEmpty().mapNotNullTo(this, ::decodeHistoryEntry)
        decodeStoredAck(values)
            ?.takeIf { stored -> none { it.ack.commandId == stored.ack.commandId } }
            ?.let(::add)
    }

    private fun encodeHistoryEntry(storedAck: StoredSessionAck): String = listOf(
        encodeText(storedAck.ack.commandId),
        storedAck.ack.sessionId?.let(::encodeText).orEmpty(),
        storedAck.ack.outcome.name,
        storedAck.commandType?.name.orEmpty(),
        storedAck.commandCreatedAtEpochMillis?.toString().orEmpty(),
    ).joinToString(HISTORY_FIELD_SEPARATOR)

    private fun decodeHistoryEntry(encoded: String): StoredSessionAck? {
        val fields = encoded.split(HISTORY_FIELD_SEPARATOR, limit = HISTORY_FIELD_COUNT)
        if (fields.size != HISTORY_FIELD_COUNT) return null
        val commandId = decodeText(fields[0]) ?: return null
        val sessionId = fields[1].takeIf(String::isNotEmpty)?.let(::decodeText) ?: run {
            if (fields[1].isEmpty()) null else return null
        }
        val outcome = runCatching { SessionCommandOutcome.valueOf(fields[2]) }.getOrNull()
            ?: return null
        val commandType = fields[3].takeIf(String::isNotEmpty)?.let {
            runCatching { QuickActionType.valueOf(it) }.getOrNull() ?: return null
        }
        val createdAt = fields[4].takeIf(String::isNotEmpty)?.toLongOrNull()
        if (fields[4].isNotEmpty() && createdAt == null) return null
        return StoredSessionAck(
            ack = SessionCommandAck(commandId, sessionId, outcome),
            commandType = commandType,
            commandCreatedAtEpochMillis = createdAt,
        )
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
        )

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun encodeAck(
        values: androidx.datastore.preferences.core.MutablePreferences,
        storedAck: StoredSessionAck,
    ) {
        values[ACK_COMMAND_ID] = storedAck.ack.commandId
        storedAck.ack.sessionId
            ?.let { values[ACK_SESSION_ID] = it }
            ?: values.remove(ACK_SESSION_ID)
        values[ACK_OUTCOME] = storedAck.ack.outcome.name
        storedAck.commandType
            ?.let { values[ACK_COMMAND_TYPE] = it.name }
            ?: values.remove(ACK_COMMAND_TYPE)
        storedAck.commandCreatedAtEpochMillis
            ?.let { values[ACK_COMMAND_CREATED_AT] = it }
            ?: values.remove(ACK_COMMAND_CREATED_AT)
    }

    private companion object {
        val PHONE_INSTANCE_ID = stringPreferencesKey("phone_instance_id")
        val STATE_REVISION = longPreferencesKey("state_revision")
        val ACK_COMMAND_ID = stringPreferencesKey("ack_command_id")
        val ACK_SESSION_ID = stringPreferencesKey("ack_session_id")
        val ACK_OUTCOME = stringPreferencesKey("ack_outcome")
        val ACK_COMMAND_TYPE = stringPreferencesKey("ack_command_type")
        val ACK_COMMAND_CREATED_AT = longPreferencesKey("ack_command_created_at")
        val ACK_HISTORY = stringSetPreferencesKey("ack_history")
        val DATA_RESET_ID = stringPreferencesKey("data_reset_id")
        const val HISTORY_FIELD_SEPARATOR = "|"
        const val HISTORY_FIELD_COUNT = 5
        const val MAX_ACK_HISTORY_AGE_MILLIS = 10_080L * 60_000L
    }
}

internal object CommandDataEpochPolicy {
    fun isCurrent(
        currentResetId: String?,
        commandResetId: String?,
    ): Boolean = currentResetId == null || currentResetId == commandResetId
}

internal data class StoredSessionAck(
    val ack: SessionCommandAck,
    val commandType: QuickActionType?,
    val commandCreatedAtEpochMillis: Long?,
)

internal object SessionAckOrderingPolicy {
    fun select(
        current: StoredSessionAck?,
        incoming: StoredSessionAck,
    ): StoredSessionAck {
        current ?: return incoming
        if (current.ack.commandId == incoming.ack.commandId) return current

        val sameSession = current.ack.sessionId != null &&
            current.ack.sessionId == incoming.ack.sessionId
        if (sameSession) {
            val currentStage = current.commandType?.sessionMutationStage()
            val incomingStage = incoming.commandType?.sessionMutationStage()
            if (currentStage != null && incomingStage != null && currentStage != incomingStage) {
                return if (incomingStage > currentStage) incoming else current
            }
        }

        val currentCreatedAt = current.commandCreatedAtEpochMillis
        val incomingCreatedAt = incoming.commandCreatedAtEpochMillis
        return if (
            currentCreatedAt != null &&
            incomingCreatedAt != null &&
            incomingCreatedAt < currentCreatedAt
        ) {
            current
        } else {
            incoming
        }
    }

    private fun QuickActionType.sessionMutationStage(): Int? = when (this) {
        QuickActionType.START_WALK,
        QuickActionType.START_STAIRS,
        -> 0
        QuickActionType.MARK_COMPLETED -> 1
        QuickActionType.SNOOZE -> null
    }
}

private fun QuickActionType.isStartMutation(): Boolean =
    this == QuickActionType.START_WALK || this == QuickActionType.START_STAIRS

private fun QuickActionType.isSessionMutation(): Boolean =
    isStartMutation() || this == QuickActionType.MARK_COMPLETED
