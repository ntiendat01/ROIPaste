package com.roipaste.app.domain

sealed class TransferError : Exception() {
    data object Unauthenticated : TransferError()

    data object Forbidden : TransferError()

    data object PayloadTooLarge : TransferError()

    data object Unavailable : TransferError()

    data object Disconnected : TransferError()

    data object Timeout : TransferError()

    data object InvalidResponse : TransferError()

    data class Protocol(val code: String) : TransferError()
}
