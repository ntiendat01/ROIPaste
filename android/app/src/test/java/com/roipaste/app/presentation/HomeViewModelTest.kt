package com.roipaste.app.presentation

import com.roipaste.app.data.clipboard.ClipboardRead
import com.roipaste.app.domain.FetchClip
import com.roipaste.app.domain.FetchedClip
import com.roipaste.app.domain.PauseSync
import com.roipaste.app.domain.SendClip
import com.roipaste.app.domain.TransferError
import com.roipaste.app.fakes.FakeClipboardGateway
import com.roipaste.app.fakes.FakeSessionStore
import com.roipaste.app.fakes.FakeTransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeTransferRepository
    private lateinit var clipboard: FakeClipboardGateway
    private lateinit var sessionStore: FakeSessionStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTransferRepository()
        clipboard = FakeClipboardGateway()
        sessionStore = FakeSessionStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            sendClip = SendClip(repository),
            fetchClip = FetchClip(repository),
            pauseSync = PauseSync(sessionStore),
            clipboard = clipboard,
            sessionStore = sessionStore,
            repository = repository,
            clock = { SENT_AT },
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun doesNotReadClipboardOnInit() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(0, clipboard.reads)
        assertEquals(ConnectionState.Connected, viewModel.state.value.connection)
    }

    @Test
    fun sendSuccessShowsSentBannerAndDoesNotWriteClipboard() = runTest(dispatcher) {
        clipboard.read = ClipboardRead.Text("Xin chào")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()
        assertEquals(Banner.Sent(SENT_AT), viewModel.state.value.banner)
        assertEquals("Xin chào", repository.lastSentText)
        assertEquals(0, clipboard.writes)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun sendFailureNeverShowsSent() = runTest(dispatcher) {
        clipboard.read = ClipboardRead.Text("hello")
        repository.sendResult = Result.failure(TransferError.Disconnected)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()
        assertEquals(Banner.Disconnected, viewModel.state.value.banner)
        assertFalse(viewModel.state.value.banner is Banner.Sent)
        assertEquals(0, clipboard.writes)
    }

    @Test
    fun sendWhenNotFocusedDoesNotCallRepository() = runTest(dispatcher) {
        clipboard.read = ClipboardRead.NotFocused
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()
        assertEquals(0, repository.sendCalls)
        assertEquals(Banner.FocusRequired, viewModel.state.value.banner)
    }

    @Test
    fun sendEmptyClipboardShowsEmptyBanner() = runTest(dispatcher) {
        clipboard.read = ClipboardRead.Empty
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()
        assertEquals(0, repository.sendCalls)
        assertEquals(Banner.ClipboardEmpty, viewModel.state.value.banner)
    }

    @Test
    fun fetchSuccessStoresPreviewButDoesNotWriteClipboard() = runTest(dispatcher) {
        repository.fetchResult = Result.success(FetchedClip("id", "mac", "from Mac", 8))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fetch()
        advanceUntilIdle()
        assertEquals("from Mac", viewModel.state.value.previewText)
        assertFalse(viewModel.state.value.previewVisible)
        assertEquals(0, clipboard.writes)
    }

    @Test
    fun fetchEmptyDoesNotWriteClipboard() = runTest(dispatcher) {
        repository.fetchResult = Result.success(null)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fetch()
        advanceUntilIdle()
        assertEquals(Banner.ClipboardEmpty, viewModel.state.value.banner)
        assertEquals(0, clipboard.writes)
    }

    @Test
    fun fetchFailureDoesNotOverwritePreviewOrClipboard() = runTest(dispatcher) {
        repository.fetchResult = Result.success(FetchedClip("id", "mac", "kept", 4))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fetch()
        advanceUntilIdle()
        repository.fetchResult = Result.failure(TransferError.Timeout)
        viewModel.fetch()
        advanceUntilIdle()
        assertEquals("kept", viewModel.state.value.previewText)
        assertEquals(Banner.Timeout, viewModel.state.value.banner)
        assertEquals(0, clipboard.writes)
    }

    @Test
    fun copyWritesFetchedTextAsSensitive() = runTest(dispatcher) {
        repository.fetchResult = Result.success(FetchedClip("id", "mac", "paste me", 8))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fetch()
        advanceUntilIdle()
        viewModel.copyToAndroid()
        assertEquals("paste me", clipboard.lastWritten)
        assertEquals(true, clipboard.lastSensitive)
        assertEquals(Banner.Copied, viewModel.state.value.banner)
    }

    @Test
    fun pausedSyncBlocksSendAndFetch() = runTest(dispatcher) {
        sessionStore = FakeSessionStore(paused = true)
        clipboard.read = ClipboardRead.Text("nope")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        viewModel.fetch()
        advanceUntilIdle()
        assertEquals(0, repository.sendCalls)
        assertEquals(0, repository.fetchCalls)
        assertEquals(Banner.SyncPaused, viewModel.state.value.banner)
    }

    @Test
    fun secondSendWhileSendingIsIgnored() = runTest(dispatcher) {
        clipboard.read = ClipboardRead.Text("once")
        val deferred = CompletableDeferred<Result<com.roipaste.app.domain.SendAck>>()
        repository.sendBlock = deferred
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sending)
        viewModel.send()
        advanceUntilIdle()
        assertEquals(1, repository.sendCalls)
        deferred.complete(repository.sendResult)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.sending)
        assertTrue(viewModel.state.value.banner is Banner.Sent)
    }

    @Test
    fun retryRepeatsFailedSend() = runTest(dispatcher) {
        clipboard.read = ClipboardRead.Text("hello")
        repository.sendResult = Result.failure(TransferError.Disconnected)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()
        repository.sendResult = Result.success(com.roipaste.app.domain.SendAck("msg-1", "clipboard_updated"))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, repository.sendCalls)
        assertTrue(viewModel.state.value.banner is Banner.Sent)
    }

    companion object {
        const val SENT_AT = 1_700_000_000_000L
    }
}
