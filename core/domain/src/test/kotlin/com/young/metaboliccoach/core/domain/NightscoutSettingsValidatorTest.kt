package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.DefaultNightscoutSettings
import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightscoutSettingsValidatorTest {
    private val validator = NightscoutSettingsValidator()

    @Test
    fun `unconfigured defaults are structurally valid`() {
        assertTrue(validator.validate(DefaultNightscoutSettings.create()).isEmpty())
    }

    @Test
    fun `normalization adds https and canonicalizes the api path`() {
        val settings = configuredSettings(
            baseUrl = "  EXAMPLE.COM/nightscout/api/v1///  ",
        )

        val normalized = validator.normalize(settings)

        assertEquals(
            "https://example.com/nightscout",
            normalized.activeServer?.baseUrl,
        )
    }

    @Test
    fun `http requires an explicit opt out and remains http when allowed`() {
        val secure = configuredSettings(baseUrl = "http://example.com")
        val explicitHttp = secure.copy(requireHttps = false)

        assertTrue(
            validator.validate(secure)
                .any { it.contains("HTTPS is required", ignoreCase = true) },
        )
        assertTrue(validator.validate(explicitHttp).isEmpty())
        assertEquals(
            "http://example.com",
            validator.normalize(explicitHttp).activeServer?.baseUrl,
        )
    }

    @Test
    fun `credentials query parameters and fragments are rejected`() {
        val invalidUrls = listOf(
            "https://user:secret@example.com",
            "https://example.com?token=secret",
            "https://example.com/#fragment",
        )

        invalidUrls.forEach { baseUrl ->
            assertTrue(
                "Expected $baseUrl to be rejected",
                validator.validate(configuredSettings(baseUrl = baseUrl)).isNotEmpty(),
            )
        }
    }

    @Test
    fun `ports outside the TCP range are rejected`() {
        listOf(0, 65_536, 70_000).forEach { port ->
            val errors = validator.validate(
                configuredSettings(baseUrl = "https://example.com:$port"),
            )

            assertTrue(
                "Expected port $port to be rejected",
                errors.any { it.contains("port", ignoreCase = true) },
            )
        }
        assertTrue(
            validator.validate(
                configuredSettings(baseUrl = "https://example.com:65535"),
            ).isEmpty(),
        )
    }

    @Test
    fun `active server must reference a configured server`() {
        val settings = configuredSettings().copy(activeServerId = "missing")

        assertTrue(
            validator.validate(settings)
                .any { it.contains("active", ignoreCase = true) },
        )
    }

    @Test
    fun `duplicate and unsafe server ids are rejected`() {
        val duplicate = configuredSettings().copy(
            servers = listOf(
                server(id = "same", baseUrl = "https://one.example"),
                server(id = "same", baseUrl = "https://two.example"),
            ),
            activeServerId = "same",
        )
        val unsafe = configuredSettings().copy(
            servers = listOf(server(id = "family/member")),
            activeServerId = "family/member",
        )

        assertTrue(
            validator.validate(duplicate)
                .any { it.contains("unique", ignoreCase = true) },
        )
        assertTrue(
            validator.validate(unsafe)
                .any { it.contains("invalid ID", ignoreCase = true) },
        )
    }

    @Test
    fun `all configurable timing bounds are enforced`() {
        val invalid = configuredSettings().copy(
            pollingIntervalMinutes = NightscoutSettingsBounds.POLLING_INTERVAL_MINUTES.first - 1,
            connectionTimeoutSeconds =
                NightscoutSettingsBounds.CONNECTION_TIMEOUT_SECONDS.last + 1,
            retryIntervalSeconds = NightscoutSettingsBounds.RETRY_INTERVAL_SECONDS.first - 1,
            maximumRetryAttempts = NightscoutSettingsBounds.MAXIMUM_RETRY_ATTEMPTS.last + 1,
        )

        val errors = validator.validate(invalid)

        assertEquals(4, errors.size)
    }

    @Test
    fun `source identity ignores labels and equivalent url spelling`() {
        val first = server(
            id = "family",
            displayName = "Primary",
            baseUrl = "https://EXAMPLE.com/nightscout/api/v1/",
        )
        val renamed = first.copy(
            displayName = "Renamed",
            baseUrl = "https://example.com/nightscout",
        )

        assertEquals(first.sourceId(requireHttps = true), renamed.sourceId(requireHttps = true))
    }

    @Test
    fun `source identity isolates server slots and endpoints`() {
        val original = server(id = "server-1", baseUrl = "https://one.example")
        val otherSlot = original.copy(id = "server-2")
        val otherEndpoint = original.copy(baseUrl = "https://two.example")

        assertNotEquals(
            original.sourceId(requireHttps = true),
            otherSlot.sourceId(requireHttps = true),
        )
        assertNotEquals(
            original.sourceId(requireHttps = true),
            otherEndpoint.sourceId(requireHttps = true),
        )
        assertTrue(original.sourceId(requireHttps = true).startsWith("nightscout:server-1:"))
    }

    @Test
    fun `recommendation snapshots remain valid for non-source settings changes`() {
        val original = configuredSettings()
        val updated = original.copy(
            pollingIntervalMinutes = original.pollingIntervalMinutes + 1,
            servers = original.servers.map { it.copy(displayName = "Renamed") },
        )

        assertFalse(original.requiresRecommendationInvalidation(updated))
    }

    @Test
    fun `recommendation snapshots are invalidated when server or endpoint changes`() {
        val original = configuredSettings()
        val otherServer = original.copy(
            servers = original.servers + server(
                id = "server-2",
                baseUrl = "https://two.example",
            ),
            activeServerId = "server-2",
        )
        val changedEndpoint = original.copy(
            servers = listOf(
                original.servers.single().copy(baseUrl = "https://replacement.example"),
            ),
        )

        assertTrue(original.requiresRecommendationInvalidation(otherServer))
        assertTrue(original.requiresRecommendationInvalidation(changedEndpoint))
    }

    @Test
    fun `invalid persisted active source fails closed during invalidation comparison`() {
        val invalid = configuredSettings(baseUrl = "http://example.com")
        val configured = configuredSettings(baseUrl = "https://example.com")

        assertEquals(null, invalid.activeSourceIdentityOrNull())
        assertTrue(invalid.requiresRecommendationInvalidation(configured))
    }

    @Test
    fun `blank inactive slots do not require display names or selection`() {
        val settings = DefaultNightscoutSettings.create().copy(
            servers = listOf(
                NightscoutServerConfig(id = "empty", displayName = "", baseUrl = ""),
            ),
            activeServerId = null,
        )

        assertFalse(validator.validate(settings).isNotEmpty())
    }

    private fun configuredSettings(
        baseUrl: String = "https://example.com",
    ): NightscoutSettings = DefaultNightscoutSettings.create().copy(
        servers = listOf(server(baseUrl = baseUrl)),
        activeServerId = "server-1",
    )

    private fun server(
        id: String = "server-1",
        displayName: String = "Server",
        baseUrl: String = "https://example.com",
    ) = NightscoutServerConfig(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
    )
}
