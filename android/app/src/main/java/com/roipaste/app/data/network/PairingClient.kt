package com.roipaste.app.data.network

import com.roipaste.app.domain.PairingPayload
import com.roipaste.app.domain.TransferError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

interface PairCompleter {
    fun complete(payload: PairingPayload, deviceId: String, alias: String): Result<PairCompleteResponse>
}

class PairingClient(
    private val json: Json = HttpTransferRepository.createJson(),
) : PairCompleter {
    override fun complete(payload: PairingPayload, deviceId: String, alias: String): Result<PairCompleteResponse> {
        val body = json.encodeToString(
            PairCompleteRequest.serializer(),
            PairCompleteRequest(payload.enrollmentToken, deviceId, alias),
        )
        val request = Request.Builder()
            .url(payload.baseUrl + "/v1/pair/complete")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        // Human approval on the Mac can take up to 60 seconds.
        val client = HttpClients.pinned(payload.pin, payload.host).newBuilder()
            .readTimeout(65, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string()
                if (!response.isSuccessful) {
                    return Result.failure(ErrorMapper.fromStatus(response.code, text, json))
                }
                val parsed = text?.let { json.decodeFromString(PairCompleteResponse.serializer(), it) }
                    ?: return Result.failure(TransferError.InvalidResponse)
                Result.success(parsed)
            }
        } catch (_: SocketTimeoutException) {
            Result.failure(TransferError.Timeout)
        } catch (_: SSLPeerUnverifiedException) {
            Result.failure(TransferError.PinMismatch)
        } catch (_: CertificateException) {
            Result.failure(TransferError.PinMismatch)
        } catch (_: SSLException) {
            Result.failure(TransferError.PinMismatch)
        } catch (_: IOException) {
            Result.failure(TransferError.Disconnected)
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
data class PairCompleteRequest(
    @SerialName("enrollment_token") val enrollmentToken: String,
    @SerialName("device_id") val deviceId: String,
    val alias: String,
)

@Serializable
data class PairCompleteResponse(
    @SerialName("device_id") val deviceId: String,
    val alias: String,
    val credential: String,
    val transport: String = "lan",
)
