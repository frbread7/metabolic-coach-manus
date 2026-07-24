package com.young.metaboliccoach.wear.data

import com.young.metaboliccoach.core.model.QuickActionCommand
import com.young.metaboliccoach.core.model.QuickActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class WearCommandOutboxPolicyTest {
    @Test
    fun `enqueue is durable-order deterministic and idempotent by command id`() {
        val later = command("later", 2_000)
        val earlier = command("earlier", 1_000)

        val queued = WearCommandOutboxPolicy.add(
            WearCommandOutboxPolicy.add(listOf(later), earlier),
            earlier,
        )

        assertEquals(listOf("earlier", "later"), queued.map { it.id })
    }

    @Test
    fun `accepted command is removed without disturbing later work`() {
        val queued = listOf(command("first", 1_000), command("second", 2_000))

        assertEquals(
            listOf("second"),
            WearCommandOutboxPolicy.remove(queued, "first").map { it.id },
        )
    }

    private fun command(id: String, createdAt: Long) = QuickActionCommand(
        id = id,
        type = QuickActionType.SNOOZE,
        createdAtEpochMillis = createdAt,
    )
}
