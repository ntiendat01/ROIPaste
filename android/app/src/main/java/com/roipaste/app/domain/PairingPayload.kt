package com.roipaste.app.domain

import java.net.URLDecoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class PairingPayload(
    val protocol: Int,
    val host: String,
    val port: Int,
    val pin: String,
    val enrollmentToken: String,
    val expiresAtEpochSeconds: Long?,
) {
    val baseUrl: String get() = "https://$host:$port"

    fun isExpired(nowEpochSeconds: Long): Boolean {
        val expires = expiresAtEpochSeconds ?: return false
        return nowEpochSeconds >= expires
    }
}

sealed class PairingParse {
    data class Valid(val payload: PairingPayload) : PairingParse()

    data object Malformed : PairingParse()

    data object MissingPin : PairingParse()

    data object Expired : PairingParse()

    data object UnsupportedVersion : PairingParse()
}

@Serializable
internal data class PairingJson(
    val v: Int = 0,
    val host: String = "",
    val port: Int = 0,
    val pin: String = "",
    val enrollment: String = "",
    val exp: Long? = null,
)

object PairingPayloadParser {
    private val PIN_PATTERN = Regex("^sha256/[A-Za-z0-9+/=]+$")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, nowEpochSeconds: Long): PairingParse {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return PairingParse.Malformed
        }
        val payload = if (trimmed.startsWith("{")) {
            parseJson(trimmed) ?: return inferJsonFailure(trimmed)
        } else {
            parseUri(trimmed) ?: return inferMissing(trimmed)
        }
        if (payload.protocol != 1) {
            return PairingParse.UnsupportedVersion
        }
        if (!PIN_PATTERN.matches(payload.pin)) {
            return if (payload.pin.isBlank()) PairingParse.MissingPin else PairingParse.Malformed
        }
        if (payload.host.isBlank() || payload.port !in 1..65535 || payload.enrollmentToken.isBlank()) {
            return PairingParse.Malformed
        }
        if (payload.isExpired(nowEpochSeconds)) {
            return PairingParse.Expired
        }
        return PairingParse.Valid(payload)
    }

    private fun inferJsonFailure(_raw: String): PairingParse = PairingParse.Malformed

    private fun inferMissing(raw: String): PairingParse {
        return if (!raw.contains("\"pin\"") && !raw.contains("pin=")) {
            PairingParse.MissingPin
        } else {
            PairingParse.Malformed
        }
    }

    private fun parseJson(raw: String): PairingPayload? {
        return try {
            val obj = json.decodeFromString(PairingJson.serializer(), raw)
            obj.toPayload()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUri(raw: String): PairingPayload? {
        val match = URI_PATTERN.matchEntire(raw) ?: return null
        val params = match.groupValues[1].split("&").mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) {
                null
            } else {
                decode(part.substring(0, index)) to decode(part.substring(index + 1))
            }
        }.toMap()
        return PairingPayload(
            protocol = params["v"]?.toIntOrNull() ?: 0,
            host = params["host"].orEmpty(),
            port = params["port"]?.toIntOrNull() ?: 0,
            pin = params["pin"].orEmpty(),
            enrollmentToken = params["enrollment"].orEmpty(),
            expiresAtEpochSeconds = params["exp"]?.toLongOrNull(),
        )
    }

    private fun PairingJson.toPayload(): PairingPayload {
        return PairingPayload(v, host, port, pin, enrollment, exp)
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    private val URI_PATTERN = Regex("^roipaste://pair\\?(.*)$", RegexOption.IGNORE_CASE)
}
