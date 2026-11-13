package com.roipaste.app.data.network

import com.roipaste.app.domain.PairingPayload
import com.roipaste.app.domain.TransferError
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingClientTest {
    @Test
    fun waitsForHumanApprovalBeyondNormalReadTimeout() {
        withServer { server, pin ->
            server.enqueue(
                MockResponse().setResponseCode(201)
                    .setHeadersDelay(6, TimeUnit.SECONDS)
                    .setBody("""{"device_id":"ace5","alias":"Ace 5","credential":"test-only"}"""),
            )
            val result = PairingClient().complete(payload(server, pin), "ace5", "Ace 5")
            assertTrue(result.isSuccess)
            assertEquals("test-only", result.getOrThrow().credential)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun wrongPinStillFailsClosed() {
        withServer { server, _ ->
            val other = HeldCertificate.Builder().commonName("other").build()
            val wrongPin = SpkiPins.sha256(other.certificate)
            val result = PairingClient().complete(payload(server, wrongPin), "ace5", "Ace 5")
            assertEquals(TransferError.PinMismatch, result.exceptionOrNull())
            assertEquals(0, server.requestCount)
        }
    }

    private fun payload(server: MockWebServer, pin: String): PairingPayload {
        val url = server.url("/")
        return PairingPayload(1, url.host, url.port, pin, "test-enrollment", null)
    }

    private fun withServer(block: (MockWebServer, String) -> Unit) {
        val cert = HeldCertificate.Builder().commonName("ROIPaste")
            .addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val server = MockWebServer()
        server.useHttps(tls.sslSocketFactory(), false)
        server.start()
        try {
            block(server, SpkiPins.sha256(cert.certificate))
        } finally {
            server.shutdown()
        }
    }
}
