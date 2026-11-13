package com.roipaste.app.data.network

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Trusts only a server certificate whose SPKI SHA-256 matches [expectedPin].
 * There is no CA fallback and no trust-all path.
 *
 * Hostname checks are done separately by [PinnedHostnameVerifier]: identity is the
 * out-of-band SPKI pin, and the expected host is the IP/name confirmed during pairing.
 * This is required for IP literals with a durable self-signed cert whose SAN may
 * not list a later DHCP address.
 */
class SpkiPinTrustManager(
    private val expectedPin: String,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("Client authentication is not supported")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.isEmpty()) {
            throw CertificateException("Empty certificate chain")
        }
        if (!SpkiPins.matches(chain[0], expectedPin)) {
            throw CertificateException("SPKI pin mismatch")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

class PinnedHostnameVerifier(
    private val expectedHost: String,
) : javax.net.ssl.HostnameVerifier {
    override fun verify(hostname: String?, session: javax.net.ssl.SSLSession?): Boolean {
        if (hostname == null) {
            return false
        }
        if (hostname.equals(expectedHost, ignoreCase = true)) {
            return true
        }
        return isLoopback(hostname) && isLoopback(expectedHost)
    }

    private fun isLoopback(host: String): Boolean {
        return HttpClients.isLoopbackHost(host)
    }
}
