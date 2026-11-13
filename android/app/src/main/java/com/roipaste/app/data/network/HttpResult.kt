package com.roipaste.app.data.network

import com.roipaste.app.domain.TransferError

sealed class HttpResult {
    data class Success(val code: Int, val body: String?) : HttpResult()

    data class Failure(val error: TransferError) : HttpResult()
}
