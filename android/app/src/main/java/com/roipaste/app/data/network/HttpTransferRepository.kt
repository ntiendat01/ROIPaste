package com.roipaste.app.data.network

import com.roipaste.app.domain.FetchedClip
import com.roipaste.app.domain.MacStatus
import com.roipaste.app.domain.SendAck
import com.roipaste.app.domain.TransferError
import com.roipaste.app.domain.TransferRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class HttpTransferRepository(
    private val api: ApiClient,
    private val json: Json = createJson(),
) : TransferRepository {
    override suspend fun health(): Result<Unit> {
        return when (val result = api.get("/v1/health", authenticated = false, retry = true)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(Unit)
        }
    }

    override suspend fun status(): Result<MacStatus> {
        return when (val result = api.get("/v1/status", authenticated = true, retry = true)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                val body = decode<StatusResponse>(result.body) ?: return Result.failure(TransferError.InvalidResponse)
                Result.success(MacStatus(body.paired, body.syncPaused, body.transport))
            }
        }
    }

    override suspend fun sendClip(text: String, messageId: String): Result<SendAck> {
        val payload = SendClipRequest(messageId, ORIGIN, KIND, text)
        val encoded = json.encodeToString(SendClipRequest.serializer(), payload)
        return when (val result = api.post("/v1/clip", encoded, authenticated = true)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                val body = decode<SendClipResponse>(result.body) ?: return Result.failure(TransferError.InvalidResponse)
                Result.success(SendAck(body.messageId, body.result))
            }
        }
    }

    override suspend fun fetchClip(): Result<FetchedClip?> {
        return when (val result = api.get("/v1/clip", authenticated = true, retry = true)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                if (result.code == 204) {
                    return Result.success(null)
                }
                val body = decode<ClipPayloadResponse>(result.body) ?: return Result.failure(TransferError.InvalidResponse)
                Result.success(toClip(body))
            }
        }
    }

    override suspend fun awaitNextClip(since: Long, timeoutSeconds: Int): Result<FetchedClip?> {
        val path = "/v1/clip/next?since=$since&timeout=$timeoutSeconds"
        val readTimeout = timeoutSeconds.toLong() + 5
        return when (val result = api.get(path, authenticated = true, retry = false, readTimeoutSeconds = readTimeout)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                if (result.code == 204) {
                    return Result.success(null)
                }
                val body = decode<ClipPayloadResponse>(result.body) ?: return Result.failure(TransferError.InvalidResponse)
                Result.success(toClip(body))
            }
        }
    }

    private fun toClip(body: ClipPayloadResponse): FetchedClip {
        return FetchedClip(body.messageId, body.origin, body.text, body.resolvedByteCount(), body.sequence)
    }

    private inline fun <reified T> decode(body: String?): T? {
        if (body.isNullOrBlank()) {
            return null
        }
        return try {
            json.decodeFromString<T>(body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val ORIGIN = "android"
        const val KIND = "text"

        fun createJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
