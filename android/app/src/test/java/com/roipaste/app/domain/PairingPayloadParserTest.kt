package com.roipaste.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadParserTest {
    private val now = 1_700_000_000L
    private val validJson =
        """{"v":1,"host":"192.168.1.20","port":5000,"pin":"sha256/ABCDEFGHIJKLMNOPQRSTUV==","enrollment":"one-time-token","exp":1700000600}"""

    @Test
    fun parsesJsonPayload() {
        val result = PairingPayloadParser.parse(validJson, now)
        assertTrue(result is PairingParse.Valid)
        val payload = (result as PairingParse.Valid).payload
        assertEquals("192.168.1.20", payload.host)
        assertEquals(5000, payload.port)
        assertEquals("one-time-token", payload.enrollmentToken)
        assertEquals("https://192.168.1.20:5000", payload.baseUrl)
    }

    @Test
    fun parsesUriPayload() {
        val uri =
            "roipaste://pair?v=1&host=192.168.1.20&port=5000&pin=sha256/ABCDEFGHIJKLMNOPQRSTUV==&enrollment=one-time-token&exp=1700000600"
        val result = PairingPayloadParser.parse(uri, now)
        assertTrue(result is PairingParse.Valid)
    }

    @Test
    fun expiredPayload() {
        val json = validJson.replace("1700000600", "1699999999")
        assertEquals(PairingParse.Expired, PairingPayloadParser.parse(json, now))
    }

    @Test
    fun malformedJson() {
        assertEquals(PairingParse.Malformed, PairingPayloadParser.parse("{not-json", now))
        assertEquals(PairingParse.Malformed, PairingPayloadParser.parse("", now))
    }

    @Test
    fun missingPin() {
        val json = """{"v":1,"host":"192.168.1.20","port":5000,"enrollment":"token","exp":1700000600}"""
        assertEquals(PairingParse.MissingPin, PairingPayloadParser.parse(json, now))
    }

    @Test
    fun unsupportedVersion() {
        val json = validJson.replace("\"v\":1", "\"v\":2")
        assertEquals(PairingParse.UnsupportedVersion, PairingPayloadParser.parse(json, now))
    }
}
