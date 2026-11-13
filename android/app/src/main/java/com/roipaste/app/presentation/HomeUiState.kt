package com.roipaste.app.presentation

import com.roipaste.app.domain.PairingPayload

data class HomeUiState(
    val screen: HomeScreenKind = HomeScreenKind.Home,
    val hasToken: Boolean = false,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val transport: String = "usb",
    val sending: Boolean = false,
    val fetching: Boolean = false,
    val previewVisible: Boolean = false,
    val previewText: String? = null,
    val previewByteCount: Int? = null,
    val syncPaused: Boolean = false,
    val tokenInput: String = "",
    val pairError: Boolean = false,
    val pairMethod: PairMethod = PairMethod.Usb,
    val wifiPayloadInput: String = "",
    val pendingWifi: PairingPayload? = null,
    val aliasInput: String = "Ace 5",
    val pairingBusy: Boolean = false,
    val banner: Banner? = null,
)

enum class PairMethod {
    Usb,
    Wifi,
}

enum class HomeScreenKind {
    Home,
    Pair,
    Settings,
}

enum class ConnectionState {
    Checking,
    Connected,
    Disconnected,
}

sealed class Banner {
    data object Ready : Banner()

    data class Sent(val atMillis: Long) : Banner()

    data object ClipboardEmpty : Banner()

    data object Disconnected : Banner()

    data object FocusRequired : Banner()

    data object Unsupported : Banner()

    data object TooLarge : Banner()

    data object SyncPaused : Banner()

    data object Unauthenticated : Banner()

    data object Forbidden : Banner()

    data object Timeout : Banner()

    data object SendFailed : Banner()

    data object FetchFailed : Banner()

    data object AccessDenied : Banner()

    data object NothingToCopy : Banner()

    data object Copied : Banner()

    data object CopyFailed : Banner()

    data object PinMismatch : Banner()

    data object InsecureTransport : Banner()

    data object PairingFailed : Banner()

    data object PairingExpired : Banner()
}
