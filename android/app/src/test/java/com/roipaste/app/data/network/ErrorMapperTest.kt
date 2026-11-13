package com.roipaste.app.data.network

import com.roipaste.app.domain.TransferError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorMapperTest {
    private val json = HttpTransferRepository.createJson()

    @Test
    fun mapsKnownStatusCodes() {
        assertEquals(TransferError.Unauthenticated, ErrorMapper.fromStatus(401, null, json))
        assertEquals(TransferError.Forbidden, ErrorMapper.fromStatus(403, null, json))
        assertEquals(TransferError.PayloadTooLarge, ErrorMapper.fromStatus(413, null, json))
        assertEquals(TransferError.Unavailable, ErrorMapper.fromStatus(503, null, json))
    }

    @Test
    fun mapsEnvelopeCodeWhenStatusUnknown() {
        val body = """{"error":{"code":"RATE_LIMITED","message":"slow down"}}"""
        assertEquals(TransferError.Protocol("RATE_LIMITED"), ErrorMapper.fromStatus(429, body, json))
    }

    @Test
    fun invalidBodyYieldsInvalidResponse() {
        assertEquals(TransferError.InvalidResponse, ErrorMapper.fromStatus(500, "nope", json))
        assertNull(ErrorMapper.decodeEnvelope("nope", json))
    }
}
