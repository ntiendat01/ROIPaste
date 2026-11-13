package com.roipaste.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val protocol: Int,
)

@Serializable
data class StatusResponse(
    val paired: Boolean,
    @SerialName("sync_paused") val syncPaused: Boolean,
    val transport: String,
)

@Serializable
data class SendClipRequest(
    @SerialName("message_id") val messageId: String,
    val origin: String,
    val kind: String,
    val text: String,
)

@Serializable
data class SendClipResponse(
    val accepted: Boolean,
    @SerialName("message_id") val messageId: String,
    val result: String,
)

@Serializable
data class ClipPayloadResponse(
    @SerialName("message_id") val messageId: String,
    val origin: String,
    val kind: String,
    val text: String,
    @SerialName("byte_count") val byteCount: Int? = null,
    val sequence: Long = 0,
) {
    fun resolvedByteCount(): Int = byteCount ?: text.toByteArray(Charsets.UTF_8).size
}

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
)
