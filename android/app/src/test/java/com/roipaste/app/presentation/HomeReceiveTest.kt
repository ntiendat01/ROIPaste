package com.roipaste.app.presentation

import com.roipaste.app.domain.FetchedClip
import com.roipaste.app.domain.TransferError
import com.roipaste.app.fakes.FakeClipboardGateway
import com.roipaste.app.fakes.FakeTransferRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeReceiveTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun receiveFlowUpdatesPreview() = runTest(dispatcher) {
        val env = env()
        val clip = FetchedClip("m1", "mac", "from Mac", 8, sequence = 3)
        val pending = CompletableDeferred<Result<FetchedClip?>>()
        env.repository.nextBlock = pending
        env.receive.start()
        runCurrent()
        pending.complete(Result.success(clip))
        runCurrent()
        assertEquals("from Mac", env.state.value.previewText)
        assertEquals(8, env.state.value.previewByteCount)
        assertEquals(false, env.state.value.previewVisible)
        assertEquals(0, env.clipboard.writes)
        env.receive.stop()
    }

    @Test
    fun autoCopyOnlyWhenForegroundAndToggleOn() = runTest(dispatcher) {
        val env = env()
        env.state.value = env.state.value.copy(autoCopyWhileOpen = true)
        env.repository.nextResult = Result.success(FetchedClip("m2", "mac", "copy me", 7, 1))
        env.receive.start()
        runCurrent()
        assertEquals(1, env.clipboard.writes)
        assertEquals("copy me", env.clipboard.lastWritten)
        assertEquals(Banner.Copied, env.state.value.banner)
        env.receive.stop()
    }

    @Test
    fun doesNotAutoCopyWhenBackgrounded() = runTest(dispatcher) {
        val env = env()
        env.state.value = env.state.value.copy(autoCopyWhileOpen = true)
        env.foreground = false
        env.repository.nextResult = Result.success(FetchedClip("m3", "mac", "stay", 4, 1))
        env.receive.start()
        runCurrent()
        env.receive.fetchLatestOnResume()
        runCurrent()
        assertEquals(0, env.clipboard.writes)
        assertNull(env.state.value.previewText)
        env.receive.stop()
    }

    @Test
    fun fetchLatestOnResumeAppliesHeldItemWithoutCopyWhenToggleOff() = runTest(dispatcher) {
        val env = env()
        env.repository.nextResult = Result.success(FetchedClip("m4", "mac", "held", 4, 9))
        env.receive.fetchLatestOnResume()
        runCurrent()
        assertEquals("held", env.state.value.previewText)
        assertEquals(0, env.clipboard.writes)
    }

    @Test
    fun backoffStopsWhenUiInactive() = runTest(dispatcher) {
        val env = env()
        env.repository.nextResult = Result.failure(TransferError.Disconnected)
        env.repository.repeatNextResult = true
        env.receive.start()
        runCurrent()
        assertEquals(1, env.repository.nextCalls)
        env.foreground = false
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, env.repository.nextCalls)
        env.receive.stop()
    }

    @Test
    fun backoffDelaysAreBounded() = runTest(dispatcher) {
        val env = env()
        env.repository.nextResult = Result.failure(TransferError.Disconnected)
        env.repository.repeatNextResult = true
        env.receive.start()
        runCurrent()
        val afterFirst = env.repository.nextCalls
        assertEquals(1, afterFirst)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(afterFirst, env.repository.nextCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(afterFirst + 1, env.repository.nextCalls)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(afterFirst + 2, env.repository.nextCalls)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(afterFirst + 3, env.repository.nextCalls)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(afterFirst + 4, env.repository.nextCalls)
        env.receive.stop()
    }

    private fun TestScope.env(): Harness {
        val repository = FakeTransferRepository()
        val clipboard = FakeClipboardGateway()
        val state = MutableStateFlow(HomeUiState(connection = ConnectionState.Connected))
        var foreground = true
        val receive = HomeReceive(
            repository = repository,
            clipboard = clipboard,
            state = state,
            scope = this,
            ioDispatcher = dispatcher,
            uiActive = { foreground },
        )
        return Harness(repository, clipboard, state, receive, { foreground }, { foreground = it })
    }

    private class Harness(
        val repository: FakeTransferRepository,
        val clipboard: FakeClipboardGateway,
        val state: MutableStateFlow<HomeUiState>,
        val receive: HomeReceive,
        private val getForeground: () -> Boolean,
        private val setForeground: (Boolean) -> Unit,
    ) {
        var foreground: Boolean
            get() = getForeground()
            set(value) = setForeground(value)
    }
}
