package com.young.metaboliccoach.core.model

data class NightscoutServerConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
)

data class NightscoutSettings(
    val servers: List<NightscoutServerConfig>,
    val activeServerId: String?,
    val pollingIntervalMinutes: Int,
    val connectionTimeoutSeconds: Int,
    val retryIntervalSeconds: Int,
    val maximumRetryAttempts: Int,
    val requireHttps: Boolean,
) {
    val configuredServers: List<NightscoutServerConfig>
        get() = servers.filter { it.baseUrl.isNotBlank() }

    val activeServer: NightscoutServerConfig?
        get() = configuredServers.firstOrNull { it.id == activeServerId }
}

object DefaultNightscoutSettings {
    fun create() = NightscoutSettings(
        servers = listOf(
            NightscoutServerConfig(
                id = "server-1",
                displayName = "Server 1",
                baseUrl = "",
            ),
            NightscoutServerConfig(
                id = "server-2",
                displayName = "Server 2",
                baseUrl = "",
            ),
        ),
        activeServerId = "server-1",
        pollingIntervalMinutes = 15,
        connectionTimeoutSeconds = 10,
        retryIntervalSeconds = 5,
        maximumRetryAttempts = 2,
        requireHttps = true,
    )
}
