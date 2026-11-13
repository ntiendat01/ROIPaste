package com.roipaste.app.fakes

import com.roipaste.app.domain.FetchedClip
import com.roipaste.app.domain.MacStatus
import com.roipaste.app.domain.SendAck
import com.roipaste.app.domain.TransferRepository
import kotlinx.coroutines.CompletableDeferred

class FakeTransferRepository : TransferRepository {
    var healthResult: Result<Unit> = Result.success(Unit)
    var statusResult: Result<MacStatus> = Result.success(MacStatus(true, false, "usb"))
    var sendResult: Result<SendAck> = Result.success(SendAck("msg-1", "clipboard_updated"))
    var fetchResult: Result<FetchedClip?> = Result.success(null)
    var sendBlock: CompletableDeferred<Result<SendAck>>? = null

    var healthCalls: Int = 0
    var statusCalls: Int = 0
    var sendCalls: Int = 0
    var fetchCalls: Int = 0
    var lastSentText: String? = null

    override suspend fun health(): Result<Unit> {
        healthCalls += 1
        return healthResult
    }

    override suspend fun status(): Result<MacStatus> {
        statusCalls += 1
        return statusResult
    }

    override suspend fun sendClip(text: String, messageId: String): Result<SendAck> {
        sendCalls += 1
        lastSentText = text
        val block = sendBlock
        return block?.await() ?: sendResult
    }

    override suspend fun fetchClip(): Result<FetchedClip?> {
        fetchCalls += 1
        return fetchResult
    }
}
