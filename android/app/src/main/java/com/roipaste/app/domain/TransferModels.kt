package com.roipaste.app.domain

data class SendAck(
    val messageId: String,
    val result: String,
)

data class FetchedClip(
    val messageId: String,
    val origin: String,
    val text: String,
    val byteCount: Int,
)

data class MacStatus(
    val paired: Boolean,
    val syncPaused: Boolean,
    val transport: String,
)
