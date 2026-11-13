package com.roipaste.app.data.network

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLException

class SpkiPinClientTest {
    @Test
    fun matchingPinSucceedsAndMismatchFailsClosed() {
        val serverCert = HeldCertificate.Builder()
            .commonName("ROIPaste")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val otherCert = HeldCertificate.Builder().commonName("other").build()
        val handshake = HandshakeCertificates.Builder().heldCertificate(serverCert).build()
        val server = MockWebServer()
        server.useHttps(handshake.sslSocketFactory(), false)
        server.enqueue(MockResponse().setBody("""{"status":"ok","protocol":1}"""))
        server.enqueue(MockResponse().setBody("""{"status":"ok","protocol":1}"""))
        server.start()
        try {
            val url = server.url("/v1/health")
            val request = Request.Builder().url(url).build()
            val pin = SpkiPins.sha256(serverCert.certificate)
            HttpClients.pinned(pin, url.host).newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }
            val otherPin = SpkiPins.sha256(otherCert.certificate)
            assertTrue(otherPin != pin)
            try {
                HttpClients.pinned(otherPin, url.host).newCall(request).execute().close()
                throw AssertionError("mismatched pin must fail")
            } catch (_: SSLException) {
                // fail closed
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun sameCertificateHasStablePin() {
        val cert = HeldCertificate.Builder().commonName("ROIPaste").build()
        assertEquals(SpkiPins.sha256(cert.certificate), SpkiPins.sha256(cert.certificate))
        assertTrue(SpkiPins.matches(cert.certificate, SpkiPins.sha256(cert.certificate)))
    }
}
