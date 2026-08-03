package com.young.metaboliccoach.core.data.provider.nightscout

import com.young.metaboliccoach.core.model.NightscoutServerConfig
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import java.time.Instant

class OkHttpNightscoutApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetch uses v1 sgv endpoint history count and conditional headers`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Last-Modified", "Wed, 22 Jul 2026 10:00:00 GMT")
                .body("""[{"sgv":138,"date":1700000000000}]""")
                .build(),
        )
        val client = client()

        val response = client.fetchEntries(
            server = nightscoutServer(path = "/family-nightscout"),
            connectionTimeoutSeconds = 10,
            ifModifiedSince = "Wed, 22 Jul 2026 09:55:00 GMT",
        )

        assertEquals(200, response.statusCode)
        assertEquals("""[{"sgv":138,"date":1700000000000}]""", response.body)
        assertEquals("Wed, 22 Jul 2026 10:00:00 GMT", response.lastModified)
        val request = server.takeRequest()
        assertEquals(
            "/family-nightscout/api/v1/entries/sgv.json",
            request.url.encodedPath,
        )
        assertEquals("300", request.url.queryParameter("count"))
        assertEquals("application/json", request.headers["Accept"])
        assertEquals(
            "Wed, 22 Jul 2026 09:55:00 GMT",
            request.headers["If-Modified-Since"],
        )
    }

    @Test
    fun `range fetch adds bounded date filters and a safe count`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("[]")
                .build(),
        )

        client().fetchEntriesInRange(
            server = nightscoutServer(),
            connectionTimeoutSeconds = 10,
            startEpochMillis = 1_700_000_000_000L,
            endEpochMillis = 1_700_600_000_000L,
            count = 2_500,
        )

        val request = server.takeRequest()
        assertEquals("2500", request.url.queryParameter("count"))
        assertEquals(
            Instant.ofEpochMilli(1_700_000_000_000L).toString(),
            request.url.queryParameter("find[dateString][${'$'}gte]"),
        )
        assertEquals(
            Instant.ofEpochMilli(1_700_600_000_000L).toString(),
            request.url.queryParameter("find[dateString][${'$'}lte]"),
        )
    }

    @Test
    fun `not modified response has no response body`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(304)
                .addHeader("Last-Modified", "cached-version")
                .build(),
        )

        val response = client().fetchEntries(
            server = nightscoutServer(),
            connectionTimeoutSeconds = 10,
            ifModifiedSince = "cached-version",
        )

        assertEquals(304, response.statusCode)
        assertNull(response.body)
        assertEquals("cached-version", response.lastModified)
    }

    @Test
    fun `future authenticator can add a secret header without changing provider code`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(200).body("[]").build())
            val client = client(
                authenticator = object : NightscoutRequestAuthenticator {
                    override fun authenticate(
                        request: Request.Builder,
                        server: NightscoutServerConfig,
                    ): Request.Builder = request.header("API-SECRET", "future-token")
                },
            )

            client.fetchEntries(
                server = nightscoutServer(),
                connectionTimeoutSeconds = 10,
                ifModifiedSince = null,
            )

            val request = server.takeRequest()
            assertEquals("future-token", request.headers["API-SECRET"])
            assertNull(request.url.queryParameter("token"))
            assertTrue(request.url.toString().startsWith("http://"))
        }

    @Test
    fun `redirect responses are returned without contacting the redirect target`() = runTest {
        val redirectTarget = MockWebServer()
        redirectTarget.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", redirectTarget.url("/capture"))
                    .build(),
            )

            val response = client().fetchEntries(
                server = nightscoutServer(),
                connectionTimeoutSeconds = 10,
                ifModifiedSince = null,
            )

            assertEquals(302, response.statusCode)
            assertEquals(1, server.requestCount)
            assertEquals(0, redirectTarget.requestCount)
        } finally {
            redirectTarget.close()
        }
    }

    @Test
    fun `response larger than the configured safety limit is rejected`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("small")
                .setHeader("Content-Length", 1_048_577)
                .build(),
        )

        expectThrows(NightscoutResponseTooLargeException::class.java) {
            client().fetchEntries(
                server = nightscoutServer(),
                connectionTimeoutSeconds = 10,
                ifModifiedSince = null,
            )
        }
    }

    private fun client(
        authenticator: NightscoutRequestAuthenticator =
            NoOpNightscoutRequestAuthenticator(),
    ) = OkHttpNightscoutApiClient(
        sharedClient = OkHttpClient(),
        authenticator = authenticator,
    )

    private fun nightscoutServer(
        path: String = "",
    ) = NightscoutServerConfig(
        id = "server-1",
        displayName = "Server 1",
        baseUrl = server.url(path.ifEmpty { "/" }).toString().trimEnd('/'),
    )

    private suspend fun <T : Throwable> expectThrows(
        type: Class<T>,
        block: suspend () -> Unit,
    ): T {
        try {
            block()
            fail("Expected ${type.simpleName}")
        } catch (error: Throwable) {
            if (!type.isInstance(error)) {
                throw AssertionError(
                    "Expected ${type.simpleName}, got ${error.javaClass.simpleName}",
                    error,
                )
            }
            return checkNotNull(type.cast(error))
        }
        error("Unreachable")
    }
}
