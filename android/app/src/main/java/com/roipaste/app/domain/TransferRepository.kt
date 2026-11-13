package com.roipaste.app.domain

interface TransferRepository {
    suspend fun health(): Result<Unit>

    suspend fun status(): Result<MacStatus>

    suspend fun sendClip(text: String, messageId: String): Result<SendAck>

    suspend fun fetchClip(): Result<FetchedClip?>
}
