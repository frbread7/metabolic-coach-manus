package com.young.metaboliccoach.core.domain

import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import java.net.URI
import java.security.MessageDigest

object NightscoutSettingsBounds {
    const val MAXIMUM_SERVERS = 8
    const val MAXIMUM_SERVER_ID_LENGTH = 64
    const val MAXIMUM_DISPLAY_NAME_LENGTH = 80
    const val MAXIMUM_URL_LENGTH = 2_048
    val POLLING_INTERVAL_MINUTES = 15..1_440
    val CONNECTION_TIMEOUT_SECONDS = 2..60
    val RETRY_INTERVAL_SECONDS = 5..60
    val MAXIMUM_RETRY_ATTEMPTS = 0..3
}

class NightscoutSettingsValidator {
    fun validate(settings: NightscoutSettings): List<String> = buildList {
        if (settings.servers.size > NightscoutSettingsBounds.MAXIMUM_SERVERS) {
            add("At most ${NightscoutSettingsBounds.MAXIMUM_SERVERS} Nightscout servers are supported.")
        }
        if (settings.servers.isEmpty()) {
            add("Keep at least one Nightscout server slot.")
        }
        val duplicateIds = settings.servers
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            add("Nightscout server IDs must be unique.")
        }
        settings.servers.forEachIndexed { index, server ->
            if (!server.id.isValidServerId()) {
                add("Nightscout server ${index + 1} has an invalid ID.")
            }
            if (server.baseUrl.isNotBlank()) {
                if (
                    server.displayName.isBlank() ||
                    server.displayName.length >
                    NightscoutSettingsBounds.MAXIMUM_DISPLAY_NAME_LENGTH ||
                    server.displayName.any(Char::isISOControl)
                ) {
                    add("Nightscout server ${index + 1} needs a valid display name.")
                }
                runCatching {
                    NightscoutUrlNormalizer.normalize(
                        baseUrl = server.baseUrl,
                        requireHttps = settings.requireHttps,
                    )
                }.exceptionOrNull()?.let {
                    add("Nightscout server ${index + 1} URL is invalid: ${it.message}")
                }
            }
        }
        val configured = settings.configuredServers
        if (
            configured.isNotEmpty() &&
            configured.none { it.id == settings.activeServerId }
        ) {
            add("Select an active configured Nightscout server.")
        }
        if (
            settings.pollingIntervalMinutes !in
            NightscoutSettingsBounds.POLLING_INTERVAL_MINUTES
        ) {
            add("Nightscout polling interval must be between 15 and 1440 minutes.")
        }
        if (
            settings.connectionTimeoutSeconds !in
            NightscoutSettingsBounds.CONNECTION_TIMEOUT_SECONDS
        ) {
            add("Nightscout connection timeout must be between 2 and 60 seconds.")
        }
        if (
            settings.retryIntervalSeconds !in
            NightscoutSettingsBounds.RETRY_INTERVAL_SECONDS
        ) {
            add("Nightscout retry interval must be between 5 and 60 seconds.")
        }
        if (
            settings.maximumRetryAttempts !in
            NightscoutSettingsBounds.MAXIMUM_RETRY_ATTEMPTS
        ) {
            add("Nightscout retry attempts must be between 0 and 3.")
        }
    }

    fun normalize(settings: NightscoutSettings): NightscoutSettings {
        val errors = validate(settings)
        require(errors.isEmpty()) { errors.joinToString(separator = " ") }
        return settings.copy(
            servers = settings.servers.map { server ->
                server.copy(
                    displayName = server.displayName.trim(),
                    baseUrl = server.baseUrl.takeIf { it.isNotBlank() }?.let {
                        NightscoutUrlNormalizer.normalize(
                            baseUrl = it,
                            requireHttps = settings.requireHttps,
                        )
                    }.orEmpty(),
                )
            },
        )
    }

    private fun String.isValidServerId(): Boolean =
        isNotBlank() &&
            length <= NightscoutSettingsBounds.MAXIMUM_SERVER_ID_LENGTH &&
            all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

object NightscoutUrlNormalizer {
    fun normalize(
        baseUrl: String,
        requireHttps: Boolean,
    ): String {
        val trimmed = baseUrl.trim()
        require(trimmed.isNotBlank()) { "URL is required." }
        require(trimmed.length <= NightscoutSettingsBounds.MAXIMUM_URL_LENGTH) {
            "URL is too long."
        }
        require(trimmed.none(Char::isWhitespace)) { "URL must not contain whitespace." }

        val withScheme = if ("://" in trimmed) {
            trimmed
        } else {
            "${if (requireHttps) "https" else "http"}://$trimmed"
        }
        val uri = runCatching { URI(withScheme) }
            .getOrElse { throw IllegalArgumentException("URL syntax is not supported.") }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || (!requireHttps && scheme == "http")) {
            if (requireHttps) {
                "HTTPS is required."
            } else {
                "Only HTTP or HTTPS is supported."
            }
        }
        require(uri.host?.isNotBlank() == true) { "A host is required." }
        require(uri.port == -1 || uri.port in 1..65_535) {
            "Port must be between 1 and 65535."
        }
        require(uri.rawUserInfo == null) { "Credentials must not be included in the URL." }
        require(uri.rawQuery == null) { "Query parameters are not allowed." }
        require(uri.rawFragment == null) { "Fragments are not allowed." }

        val normalizedPath = uri.rawPath
            .orEmpty()
            .trimEnd('/')
        require(!normalizedPath.contains("/api/v1/", ignoreCase = true)) {
            "Use the server root URL or an URL ending at /api/v1."
        }
        val apiRootPath = if (normalizedPath.endsWith("/api/v1", ignoreCase = true)) {
            normalizedPath.dropLast("/api/v1".length)
        } else {
            normalizedPath
        }.trimEnd('/')
        return URI(
            scheme,
            null,
            uri.host.lowercase(),
            uri.port,
            apiRootPath.ifBlank { null },
            null,
            null,
        ).toASCIIString()
    }
}

fun NightscoutServerConfig.sourceId(requireHttps: Boolean): String {
    val canonicalUrl = NightscoutUrlNormalizer.normalize(baseUrl, requireHttps)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonicalUrl.toByteArray(Charsets.UTF_8))
        .take(SOURCE_FINGERPRINT_BYTES)
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    return "nightscout:$id:$digest"
}

fun NightscoutSettings.activeSourceIdentityOrNull(): String? =
    activeServer?.let { server ->
        runCatching { server.sourceId(requireHttps) }.getOrNull()
    }

fun NightscoutSettings.requiresRecommendationInvalidation(
    updated: NightscoutSettings,
): Boolean = activeSourceIdentityOrNull() != updated.activeSourceIdentityOrNull()

private const val SOURCE_FINGERPRINT_BYTES = 12
