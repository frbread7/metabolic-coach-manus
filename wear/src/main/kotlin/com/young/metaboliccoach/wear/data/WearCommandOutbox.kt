package com.young.metaboliccoach.wear.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataMap
import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.sync.WatchStateCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.commandOutboxDataStore by preferencesDataStore("wear_command_outbox")

@Singleton
class WearCommandOutbox @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val codec: WatchStateCodec,
) {
    val commands: Flow<List<QuickActionCommand>> =
        context.commandOutboxDataStore.data.map { values ->
            decode(values[COMMANDS].orEmpty())
        }

    suspend fun enqueue(command: QuickActionCommand) {
        context.commandOutboxDataStore.edit { values ->
            values[COMMANDS] = WearCommandOutboxPolicy.add(
                current = decode(values[COMMANDS].orEmpty()),
                command = command,
            ).mapTo(linkedSetOf(), ::encode)
        }
    }

    suspend fun remove(commandId: String) {
        context.commandOutboxDataStore.edit { values ->
            values[COMMANDS] = WearCommandOutboxPolicy.remove(
                current = decode(values[COMMANDS].orEmpty()),
                commandId = commandId,
            ).mapTo(linkedSetOf(), ::encode)
        }
    }

    suspend fun clearForDataReset() {
        context.commandOutboxDataStore.edit { values ->
            values.clear()
        }
    }

    private fun encode(command: QuickActionCommand): String =
        Base64.encodeToString(
            codec.encode(command).toByteArray(),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )

    private fun decode(entries: Set<String>): List<QuickActionCommand> =
        entries.mapNotNull { encoded ->
            runCatching {
                codec.decodeCommand(
                    DataMap.fromByteArray(
                        Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE),
                    ),
                )
            }.getOrNull()
        }.sortedWith(
            compareBy<QuickActionCommand> { it.createdAtEpochMillis }.thenBy { it.id },
        )

    private companion object {
        val COMMANDS = stringSetPreferencesKey("commands")
    }
}

internal object WearCommandOutboxPolicy {
    private const val MAX_COMMANDS = 32

    fun add(
        current: List<QuickActionCommand>,
        command: QuickActionCommand,
    ): List<QuickActionCommand> = (current + command)
        .associateBy { it.id }
        .values
        .sortedWith(compareBy<QuickActionCommand> { it.createdAtEpochMillis }.thenBy { it.id })
        .takeLast(MAX_COMMANDS)

    fun remove(
        current: List<QuickActionCommand>,
        commandId: String,
    ): List<QuickActionCommand> = current.filterNot { it.id == commandId }
}
