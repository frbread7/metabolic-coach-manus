package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.model.NightscoutServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NightscoutSettingsJsonCodecTest {
    private val defaults = listOf(server(id = "default", displayName = "Default"))

    @Test
    fun `server list round trips without losing ordering or escaped characters`() {
        val servers = listOf(
            server(
                id = "family-1",
                displayName = "Family \"A\"",
                baseUrl = "https://one.example/ns",
            ),
            server(
                id = "staging_2",
                displayName = "Staging\nServer",
                baseUrl = "https://two.example",
            ),
        )

        val decoded = NightscoutSettingsJsonCodec.decodeServers(
            encoded = NightscoutSettingsJsonCodec.encodeServers(servers),
            defaults = defaults,
        )

        assertEquals(servers, decoded)
    }

    @Test
    fun `missing malformed and empty persisted values safely restore defaults`() {
        val encodedValues = listOf<String?>(
            null,
            "",
            "not-json",
            "{}",
            "[]",
            """[{"displayName":"Missing ID","baseUrl":"https://example.com"}]""",
        )

        encodedValues.forEach { encoded ->
            assertEquals(
                "Encoded value: $encoded",
                defaults,
                NightscoutSettingsJsonCodec.decodeServers(encoded, defaults),
            )
        }
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val encoded = """
            [
              {
                "id":"server-1",
                "displayName":"Primary",
                "baseUrl":"https://example.com",
                "futureAuthentication":{"type":"token"},
                "enabled":true
              }
            ]
        """.trimIndent()

        assertEquals(
            listOf(server()),
            NightscoutSettingsJsonCodec.decodeServers(encoded, defaults),
        )
    }

    @Test
    fun `malformed rows do not discard other valid server slots`() {
        val encoded = """
            [
              {"id":"valid","displayName":"Valid","baseUrl":"https://valid.example"},
              {"displayName":"Missing ID","baseUrl":"https://invalid.example"},
              "not-an-object"
            ]
        """.trimIndent()

        assertEquals(
            listOf(
                server(
                    id = "valid",
                    displayName = "Valid",
                    baseUrl = "https://valid.example",
                ),
            ),
            NightscoutSettingsJsonCodec.decodeServers(encoded, defaults),
        )
    }

    private fun server(
        id: String = "server-1",
        displayName: String = "Primary",
        baseUrl: String = "https://example.com",
    ) = NightscoutServerConfig(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
    )
}
