package com.roipaste.app.data.network

import com.roipaste.app.domain.TransferError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Response

object ErrorMapper {
    fun fromResponse(response: Response, json: Json): TransferError {
        val body = try {
            response.body?.string()
        } catch (_: Exception) {
            null
        }
        return fromStatus(response.code, body, json)
    }

    fun fromStatus(code: Int, body: String?, json: Json): TransferError {
        val envelope = decodeEnvelope(body, json)
        val protocolCode = envelope?.error?.code
        return when (code) {
            401 -> TransferError.Unauthenticated
            403 -> TransferError.Forbidden
            413 -> TransferError.PayloadTooLarge
            503 -> TransferError.Unavailable
            else -> if (protocolCode != null) TransferError.Protocol(protocolCode) else TransferError.InvalidResponse
        }
    }

    fun decodeEnvelope(body: String?, json: Json): ErrorEnvelope? {
        if (body.isNullOrBlank()) {
            return null
        }
        return try {
            json.decodeFromString(ErrorEnvelope.serializer(), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
