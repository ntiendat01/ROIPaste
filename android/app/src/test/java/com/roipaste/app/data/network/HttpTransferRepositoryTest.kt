package com.roipaste.app.data.network

import com.roipaste.app.domain.TransferError
import com.roipaste.app.fakes.FakeSessionStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpTransferRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun postClipSendsBearerTokenAndJsonBody() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"accepted":true,"message_id":"msg-1","result":"clipboard_updated"}"""))
        val result = repository().sendClip("hello", "msg-1").getOrThrow()
        assertEquals("msg-1", result.messageId)
        assertEquals("clipboard_updated", result.result)
        val recorded = server.takeRequest()
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertEquals("/v1/clip", recorded.path)
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("\"text\":\"hello\""))
    }

    @Test
    fun getClipUnauthorizedMapsToUnauthenticated() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"code":"UNAUTHENTICATED","message":"missing"}}"""),
        )
        val result = repository().fetchClip()
        assertEquals(TransferError.Unauthenticated, result.exceptionOrNull())
    }

    @Test
    fun postOversizeMapsToPayloadTooLarge() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(413)
                .setBody("""{"error":{"code":"PAYLOAD_TOO_LARGE","message":"too big"}}"""),
        )
        val result = repository().sendClip("huge", "msg-2")
        assertEquals(TransferError.PayloadTooLarge, result.exceptionOrNull())
    }

    @Test
    fun getClipNoContentReturnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = repository().fetchClip().getOrThrow()
        assertNull(result)
    }

    @Test
    fun getClipParsesPayload() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"message_id":"m","origin":"mac","kind":"text","text":"from Mac","byte_count":8}""",
            ),
        )
        val clip = repository().fetchClip().getOrThrow()
        assertEquals("from Mac", clip?.text)
        assertEquals(8, clip?.byteCount)
        assertEquals("mac", clip?.origin)
    }

    @Test
    fun invalidEnvelopeMapsToInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("not-json"))
        val result = repository().fetchClip()
        assertEquals(TransferError.InvalidResponse, result.exceptionOrNull())
    }

    @Test
    fun timeoutMapsToTimeout() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = repository(readTimeoutMs = 150).fetchClip()
        assertEquals(TransferError.Timeout, result.exceptionOrNull())
    }

    @Test
    fun getRetriesOnceAfterDisconnect() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("""{"status":"ok","protocol":1}"""))
        val result = repository().health()
        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun postDoesNotRetryAfterDisconnect() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("""{"accepted":true,"message_id":"msg-1","result":"clipboard_updated"}"""))
        val result = repository().sendClip("hello", "msg-1")
        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingTokenIsUnauthenticatedWithoutRequest() = runBlocking {
        val result = repository(token = null).fetchClip()
        assertEquals(TransferError.Unauthenticated, result.exceptionOrNull())
        assertEquals(0, server.requestCount)
    }

    private fun repository(token: String? = "secret", readTimeoutMs: Long = 500): HttpTransferRepository {
        val store = FakeSessionStore(token = token, baseUrl = server.url("/").toString().trimEnd('/'))
        val client = OkHttpClient.Builder()
            .connectTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return HttpTransferRepository(ApiClient(store) { client })
    }

    @Test
    fun awaitNextClipParsesSequenceAndTimeoutQuery() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"message_id":"m","origin":"mac","kind":"text","text":"push","sequence":4}""",
            ),
        )
        val result = repository().awaitNextClip(2, 25).getOrThrow()
        assertEquals("push", result?.text)
        assertEquals(4L, result?.sequence)
        assertEquals(4, result?.byteCount)
        assertEquals("/v1/clip/next?since=2&timeout=25", server.takeRequest().path)
    }

    @Test
    fun awaitNextClipTimeoutBodyIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertNull(repository().awaitNextClip(0, 0).getOrThrow())
    }

}
