package com.young.metaboliccoach.core.data.repository

import com.young.metaboliccoach.core.model.NightscoutServerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object NightscoutSettingsJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun encodeServers(servers: List<NightscoutServerConfig>): String =
        buildJsonArray {
            servers.forEach { server ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(server.id))
                        put("displayName", JsonPrimitive(server.displayName))
                        put("baseUrl", JsonPrimitive(server.baseUrl))
                    },
                )
            }
        }.toString()

    fun decodeServers(
        encoded: String?,
        defaults: List<NightscoutServerConfig>,
    ): List<NightscoutServerConfig> {
        if (encoded.isNullOrBlank()) return defaults
        return runCatching {
            json.parseToJsonElement(encoded).jsonArray.mapNotNull { element ->
                (element as? JsonObject)?.toServerConfig()
            }
        }.getOrNull()
            ?.takeIf(List<NightscoutServerConfig>::isNotEmpty)
            ?: defaults
    }

    private fun JsonObject.toServerConfig(): NightscoutServerConfig? {
        val id = string("id") ?: return null
        return NightscoutServerConfig(
            id = id,
            displayName = string("displayName").orEmpty(),
            baseUrl = string("baseUrl").orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull
}
