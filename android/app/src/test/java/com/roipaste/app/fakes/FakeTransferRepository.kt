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
    var nextResult: Result<FetchedClip?> = Result.success(null)
    var nextBlock: CompletableDeferred<Result<FetchedClip?>>? = null
    var repeatNextResult: Boolean = false

    var healthCalls: Int = 0
    var statusCalls: Int = 0
    var sendCalls: Int = 0
    var fetchCalls: Int = 0
    var nextCalls: Int = 0
    private var nextResultConsumed: Boolean = false
    var lastSentText: String? = null
    var lastSince: Long? = null
    var lastTimeout: Int? = null

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

    override suspend fun awaitNextClip(since: Long, timeoutSeconds: Int): Result<FetchedClip?> {
        nextCalls += 1
        lastSince = since
        lastTimeout = timeoutSeconds
        val block = nextBlock
        if (block != null) {
            nextBlock = null
            return block.await()
        }
        if (!nextResultConsumed || repeatNextResult) {
            nextResultConsumed = true
            return nextResult
        }
        return CompletableDeferred<Result<FetchedClip?>>().await()
    }
}
