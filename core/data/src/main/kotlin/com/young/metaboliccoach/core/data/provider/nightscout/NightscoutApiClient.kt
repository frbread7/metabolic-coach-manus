package com.young.metaboliccoach.core.data.provider.nightscout

import com.young.metaboliccoach.core.model.NightscoutServerConfig
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

data class NightscoutHttpResponse(
    val statusCode: Int,
    val body: String?,
    val lastModified: String?,
)

class NightscoutResponseTooLargeException :
    IllegalStateException("Nightscout response exceeded the size limit.")

interface NightscoutApiClient {
    suspend fun fetchEntries(
        server: NightscoutServerConfig,
        connectionTimeoutSeconds: Int,
        ifModifiedSince: String?,
    ): NightscoutHttpResponse

    /**
     * Fetches a bounded date range. Older test doubles can use the default implementation while
     * providers progressively adopt historical backfill.
     */
    suspend fun fetchEntriesInRange(
        server: NightscoutServerConfig,
        connectionTimeoutSeconds: Int,
        startEpochMillis: Long,
        endEpochMillis: Long,
        count: Int,
    ): NightscoutHttpResponse = fetchEntries(
        server = server,
        connectionTimeoutSeconds = connectionTimeoutSeconds,
        ifModifiedSince = null,
    )
}

interface NightscoutRequestAuthenticator {
    fun authenticate(
        request: Request.Builder,
        server: NightscoutServerConfig,
    ): Request.Builder
}

@Singleton
class NoOpNightscoutRequestAuthenticator @Inject constructor() :
    NightscoutRequestAuthenticator {
    override fun authenticate(
        request: Request.Builder,
        server: NightscoutServerConfig,
    ): Request.Builder = request
}

@Singleton
class OkHttpNightscoutApiClient @Inject constructor(
    private val sharedClient: OkHttpClient,
    private val authenticator: NightscoutRequestAuthenticator,
) : NightscoutApiClient {
    override suspend fun fetchEntries(
        server: NightscoutServerConfig,
        connectionTimeoutSeconds: Int,
        ifModifiedSince: String?,
    ): NightscoutHttpResponse = executeRequest(
        server = server,
        connectionTimeoutSeconds = connectionTimeoutSeconds,
        ifModifiedSince = ifModifiedSince,
        startEpochMillis = null,
        endEpochMillis = null,
        count = DEFAULT_HISTORY_ENTRY_COUNT,
    )

    override suspend fun fetchEntriesInRange(
        server: NightscoutServerConfig,
        connectionTimeoutSeconds: Int,
        startEpochMillis: Long,
        endEpochMillis: Long,
        count: Int,
    ): NightscoutHttpResponse = executeRequest(
        server = server,
        connectionTimeoutSeconds = connectionTimeoutSeconds,
        ifModifiedSince = null,
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
        count = count,
    )

    private suspend fun executeRequest(
        server: NightscoutServerConfig,
        connectionTimeoutSeconds: Int,
        ifModifiedSince: String?,
        startEpochMillis: Long?,
        endEpochMillis: Long?,
        count: Int,
    ): NightscoutHttpResponse {
        val url = server.baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("api/v1/entries/sgv.json")
            .addQueryParameter("count", count.coerceIn(1, MAX_HISTORY_ENTRY_COUNT).toString())
            .apply {
                startEpochMillis?.let {
                    addQueryParameter("find[dateString][${'$'}gte]", Instant.ofEpochMilli(it).toString())
                }
                endEpochMillis?.let {
                    addQueryParameter("find[dateString][${'$'}lte]", Instant.ofEpochMilli(it).toString())
                }
            }
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
        ifModifiedSince?.let { requestBuilder.header("If-Modified-Since", it) }
        val request = authenticator.authenticate(requestBuilder, server).build()
        val requestClient = sharedClient.newBuilder()
            .callTimeout(connectionTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .connectTimeout(connectionTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(connectionTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        return requestClient.newCall(request).awaitResponse()
    }

    private suspend fun Call.awaitResponse(): NightscoutHttpResponse =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            runCatching {
                                NightscoutHttpResponse(
                                    statusCode = response.code,
                                    body = if (response.code == HTTP_NOT_MODIFIED) {
                                        null
                                    } else {
                                        response.body.readBoundedUtf8()
                                    },
                                    lastModified = response.header("Last-Modified"),
                                )
                            }.onSuccess { result ->
                                if (continuation.isActive) continuation.resume(result)
                            }.onFailure { error ->
                                if (continuation.isActive) {
                                    continuation.resumeWithException(error)
                                }
                            }
                        }
                    }
                },
            )
        }

    private fun okhttp3.ResponseBody.readBoundedUtf8(): String {
        val declaredLength = contentLength()
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw NightscoutResponseTooLargeException()
        }
        val source = source()
        val hasAtLeastLimitPlusOne = source.request(MAX_RESPONSE_BYTES + 1)
        if (hasAtLeastLimitPlusOne) {
            throw NightscoutResponseTooLargeException()
        }
        return source.readUtf8()
    }

    private companion object {
        const val DEFAULT_HISTORY_ENTRY_COUNT = 300
        const val MAX_HISTORY_ENTRY_COUNT = 2_500
        const val HTTP_NOT_MODIFIED = 304
        const val MAX_RESPONSE_BYTES = 1_048_576L
    }
}
