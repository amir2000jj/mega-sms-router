package com.megasmsrouter

import com.megasmsrouter.domain.model.*
import com.megasmsrouter.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Uses a local MockWebServer only: no real tokens, no real codes. */
class NetworkTest {
    private lateinit var server: MockWebServer
    private val client = ApiClient()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { try { server.shutdown() } catch (_: Exception) {} }

    private fun dest(path: String = "/hook", block: (ApiDestination) -> ApiDestination = { it }) = block(
        ApiDestination(
            name = "test",
            url = server.url(path).toString(),
            allowHttp = true, // mock server is plain http on localhost
            connectTimeoutSec = 2,
            readTimeoutSec = 2
        )
    )

    private fun payload(d: ApiDestination, code: String = "000000", message: String = "msg") =
        MessagePayload(code, "TEST-SENDER", message, 1700000000000L, d.id, "mid-1")

    private fun send(d: ApiDestination, p: MessagePayload = payload(d)): NetworkResult =
        runBlocking { client.send(RequestBuilder.build(d, p), d) }

    @Test fun successAndJsonBodyAndHeaders() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val d = dest {
            it.copy(
                headers = listOf(HeaderEntry("X-Client", "MSR")),
                authType = AuthType.BEARER, authSecret = "fake-token"
            )
        }
        val r = send(d)
        assertTrue(r is NetworkResult.Success)
        val rec = server.takeRequest()
        assertEquals("POST", rec.method)
        assertEquals("MSR", rec.getHeader("X-Client"))
        assertEquals("Bearer fake-token", rec.getHeader("Authorization"))
        assertTrue(rec.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("000000", JSONObject(rec.body.readUtf8()).getString("code"))
    }

    @Test fun jsonEscapingKeepsBodyValid() {
        server.enqueue(MockResponse().setResponseCode(200))
        val d = dest { it.copy(bodyTemplate = "{\"msg\":\"{{message}}\",\"n\":[\"{{sender}}\"],\"k\":1}") }
        val nasty = "He said \"hi\"\nnew\\line \u202E سلام \$1 {{code}}"
        send(d, payload(d, message = nasty))
        val json = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(nasty, json.getString("msg"))
        assertEquals("TEST-SENDER", json.getJSONArray("n").getString(0))
        assertEquals(1, json.getInt("k"))
    }

    @Test fun formEncodedBody() {
        server.enqueue(MockResponse().setResponseCode(200))
        val d = dest { it.copy(bodyType = BodyType.FORM, bodyTemplate = "code={{code}}&who={{sender}}") }
        send(d, payload(d, code = "123456"))
        val rec = server.takeRequest()
        assertTrue(rec.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        assertEquals("code=123456&who=TEST-SENDER", rec.body.readUtf8())
    }

    @Test fun plainTextBody() {
        server.enqueue(MockResponse().setResponseCode(200))
        val d = dest { it.copy(bodyType = BodyType.TEXT, bodyTemplate = "code={{code}}") }
        send(d, payload(d, code = "424242"))
        val rec = server.takeRequest()
        assertTrue(rec.getHeader("Content-Type")!!.startsWith("text/plain"))
        assertEquals("code=424242", rec.body.readUtf8())
    }

    @Test fun getUsesQueryParametersNotBody() {
        server.enqueue(MockResponse().setResponseCode(200))
        val d = dest { it.copy(method = HttpMethod.GET, bodyType = BodyType.FORM, bodyTemplate = "code={{code}}&s={{sender}}") }
        send(d, payload(d, code = "777777"))
        val rec = server.takeRequest()
        assertEquals("GET", rec.method)
        assertEquals(0L, rec.bodySize)
        assertEquals("777777", rec.requestUrl!!.queryParameter("code"))
        assertEquals("TEST-SENDER", rec.requestUrl!!.queryParameter("s"))
    }

    @Test fun allHttpMethodsWork() {
        for (m in HttpMethod.values()) {
            server.enqueue(MockResponse().setResponseCode(200))
            val d = dest { it.copy(method = m, bodyType = if (m == HttpMethod.GET) BodyType.NONE else BodyType.JSON) }
            assertTrue("method $m", send(d) is NetworkResult.Success)
            assertEquals(m.name, server.takeRequest().method)
        }
    }

    @Test fun emptyBodyForPostWithNoneType() {
        server.enqueue(MockResponse().setResponseCode(200))
        val d = dest { it.copy(bodyType = BodyType.NONE) }
        assertTrue(send(d) is NetworkResult.Success)
        assertEquals(0L, server.takeRequest().bodySize)
    }

    @Test fun basicAndApiKeyAuth() {
        server.enqueue(MockResponse().setResponseCode(200))
        send(dest { it.copy(authType = AuthType.BASIC, authUser = "user", authSecret = "pass") })
        assertEquals("Basic dXNlcjpwYXNz", server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(200))
        send(dest { it.copy(authType = AuthType.API_KEY, apiKeyHeader = "X-API-Key", authSecret = "k-123") })
        assertEquals("k-123", server.takeRequest().getHeader("X-API-Key"))
    }

    @Test fun httpStatusesAreClassified() {
        for (code in listOf(400, 401, 403, 404, 429, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val r = send(dest())
            assertTrue("code $code", r is NetworkResult.HttpError)
            assertEquals(code, (r as NetworkResult.HttpError).code)
            server.takeRequest()
        }
    }

    @Test fun readTimeoutIsReported() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val r = send(dest { it.copy(readTimeoutSec = 1) })
        assertTrue(r is NetworkResult.Failure)
        assertEquals(FailureKind.TIMEOUT, (r as NetworkResult.Failure).kind)
    }

    @Test fun unreachableServerIsFailureNotCrash() {
        val d = dest()
        val request = RequestBuilder.build(d, payload(d))
        server.shutdown()
        val r = client.executeBlocking(request, d)
        assertTrue(r is NetworkResult.Failure)
        val kind = (r as NetworkResult.Failure).kind
        assertTrue(kind == FailureKind.OFFLINE || kind == FailureKind.IO || kind == FailureKind.TIMEOUT)
    }

    @Test fun invalidResponseBodyCanFailSuccess() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>garbage</html>"))
        val r = send(dest { it.copy(successContains = "\"ok\":true") })
        assertTrue(r is NetworkResult.HttpError)
        assertTrue((r as NetworkResult.HttpError).bodyMismatch)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))
        assertTrue(send(dest { it.copy(successContains = "\"ok\":true") }) is NetworkResult.Success)
    }

    @Test fun httpBlockedUnlessExplicitlyAllowed() {
        val d = dest { it.copy(allowHttp = false) }
        val r = client.executeBlocking(RequestBuilder.build(d, payload(d)), d)
        assertTrue(r is NetworkResult.Failure)
        assertEquals(FailureKind.BLOCKED, (r as NetworkResult.Failure).kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun idempotencyKeyHeader() {
        server.enqueue(MockResponse().setResponseCode(200))
        val d = dest()
        runBlocking { client.send(RequestBuilder.build(d, payload(d), "mid-1:dest"), d) }
        assertEquals("mid-1:dest", server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test fun oneDestinationFailingDoesNotBlockAnother() {
        val good = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(500))
            good.enqueue(MockResponse().setResponseCode(200))
            val d1 = dest()
            val d2 = ApiDestination(name = "good", url = good.url("/ok").toString(), allowHttp = true)
            val results = listOf(d1, d2).map { d -> send(d) }
            assertTrue(results[0] is NetworkResult.HttpError)
            assertTrue(results[1] is NetworkResult.Success)
            assertEquals(1, server.requestCount)
            assertEquals(1, good.requestCount)
        } finally {
            good.shutdown()
        }
    }

    @Test fun summaryNeverContainsSecrets() {
        val r = NetworkResult.Failure(FailureKind.IO, "SocketException", 5)
        assertEquals("IO", r.summary())
        assertEquals("HTTP 200", NetworkResult.Success(200, 1).summary())
    }
}
