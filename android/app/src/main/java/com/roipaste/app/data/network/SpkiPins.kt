package com.roipaste.app.data.network

import java.util.Base64
import java.security.MessageDigest
import java.security.cert.X509Certificate

object SpkiPins {
    const val PREFIX = "sha256/"

    fun sha256(certificate: X509Certificate): String {
        return encode(digest(certificate))
    }

    fun digest(certificate: X509Certificate): ByteArray {
        return sha256Bytes(certificate.publicKey.encoded)
    }

    fun decode(pin: String): ByteArray {
        val body = pin.removePrefix(PREFIX)
        return Base64.getDecoder().decode(body)
    }

    fun encode(digest: ByteArray): String {
        return PREFIX + Base64.getEncoder().encodeToString(digest)
    }

    fun matches(certificate: X509Certificate, expectedPin: String): Boolean {
        val expected = decode(expectedPin)
        val actual = digest(certificate)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun sha256Bytes(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }
}
