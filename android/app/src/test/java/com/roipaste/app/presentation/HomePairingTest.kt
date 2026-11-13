package com.roipaste.app.presentation

import com.roipaste.app.data.network.PairCompleteResponse
import com.roipaste.app.data.network.PairCompleter
import com.roipaste.app.domain.PairingPayload
import com.roipaste.app.domain.TransferError
import com.roipaste.app.fakes.FakeClipboardGateway
import com.roipaste.app.fakes.FakeSessionStore
import com.roipaste.app.fakes.FakeTransferRepository
import com.roipaste.app.domain.FetchClip
import com.roipaste.app.domain.PauseSync
import com.roipaste.app.domain.SendClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomePairingTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var completer: RecordingPairCompleter

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionStore = FakeSessionStore(token = null)
        completer = RecordingPairCompleter()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            sendClip = SendClip(FakeTransferRepository()),
            fetchClip = FetchClip(FakeTransferRepository()),
            pauseSync = PauseSync(sessionStore),
            clipboard = FakeClipboardGateway(),
            sessionStore = sessionStore,
            repository = FakeTransferRepository(),
            pairingClient = completer,
            clock = { 1_700_000_000_000L },
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun validPayloadMovesToConfirmState() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectPairMethod(PairMethod.Wifi)
        viewModel.updateWifiPayload(VALID_JSON)
        viewModel.submitWifiPayload()
        assertTrue(viewModel.state.value.pendingWifi != null)
        assertEquals(0, completer.calls)
    }

    @Test
    fun expiredPayloadDoesNotConfirm() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateWifiPayload(VALID_JSON.replace("2700000600", "1600000000"))
        viewModel.submitWifiPayload()
        assertEquals(Banner.PairingExpired, viewModel.state.value.banner)
        assertNull(viewModel.state.value.pendingWifi)
    }

    @Test
    fun confirmStoresLanSession() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateWifiPayload(VALID_JSON)
        viewModel.submitWifiPayload()
        viewModel.confirmWifiPair()
        advanceUntilIdle()
        assertEquals(1, completer.calls)
        assertEquals("lan-cred", sessionStore.currentToken())
        assertEquals("sha256/ABCDEFGHIJKLMNOPQRSTUV==", sessionStore.currentPin())
        assertEquals("lan", sessionStore.currentTransport())
        assertTrue(sessionStore.baseUrl.startsWith("https://"))
        assertEquals(HomeScreenKind.Home, viewModel.state.value.screen)
    }

    @Test
    fun pinMismatchDoesNotStoreCredential() = runTest(dispatcher) {
        completer.result = Result.failure(TransferError.PinMismatch)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateWifiPayload(VALID_JSON)
        viewModel.submitWifiPayload()
        viewModel.confirmWifiPair()
        advanceUntilIdle()
        assertNull(sessionStore.currentToken())
        assertNull(sessionStore.currentPin())
        assertEquals(Banner.PinMismatch, viewModel.state.value.banner)
    }

    @Test
    fun unpairClearsStoredSession() = runTest(dispatcher) {
        sessionStore = FakeSessionStore(token = "old")
        val viewModel = createViewModel()
        advanceUntilIdle()
        // Can't setLan without suspend from test easily; use confirm path
        viewModel.updateWifiPayload(VALID_JSON)
        viewModel.submitWifiPayload()
        viewModel.confirmWifiPair()
        advanceUntilIdle()
        viewModel.unpair()
        advanceUntilIdle()
        assertEquals(1, sessionStore.clearCount)
        assertNull(sessionStore.currentToken())
        assertNull(sessionStore.currentPin())
        assertEquals("usb", sessionStore.currentTransport())
    }

    companion object {
        const val VALID_JSON =
            """{"v":1,"host":"192.168.1.20","port":5000,"pin":"sha256/ABCDEFGHIJKLMNOPQRSTUV==","enrollment":"one-time-token","exp":2700000600}"""
    }
}

class RecordingPairCompleter : PairCompleter {
    var calls: Int = 0
    var result: Result<PairCompleteResponse> =
        Result.success(PairCompleteResponse("ace5", "Ace 5", "lan-cred", "lan"))

    override fun complete(
        payload: PairingPayload,
        deviceId: String,
        alias: String,
    ): Result<PairCompleteResponse> {
        calls += 1
        return result
    }
}
