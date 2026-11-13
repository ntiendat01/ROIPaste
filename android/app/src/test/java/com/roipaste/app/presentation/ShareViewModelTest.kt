package com.roipaste.app.presentation

import com.roipaste.app.domain.SendClip
import com.roipaste.app.domain.ShareValidation
import com.roipaste.app.domain.TransferError
import com.roipaste.app.fakes.FakeSessionStore
import com.roipaste.app.fakes.FakeTransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeTransferRepository
    private lateinit var sessionStore: FakeSessionStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTransferRepository()
        sessionStore = FakeSessionStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(validation: ShareValidation): ShareViewModel {
        return ShareViewModel(
            validation = validation,
            sendClip = SendClip(repository),
            sessionStore = sessionStore,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun doesNotSendUntilUserConfirms() = runTest(dispatcher) {
        val viewModel = createViewModel(ShareValidation.Valid("shared text"))
        advanceUntilIdle()
        assertEquals(0, repository.sendCalls)
        assertFalse(viewModel.state.value.sent)
    }

    @Test
    fun confirmSendPostsText() = runTest(dispatcher) {
        val viewModel = createViewModel(ShareValidation.Valid("shared text"))
        viewModel.confirmSend()
        advanceUntilIdle()
        assertEquals("shared text", repository.lastSentText)
        assertTrue(viewModel.state.value.sent)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun confirmSendFailureDoesNotMarkSent() = runTest(dispatcher) {
        repository.sendResult = Result.failure(TransferError.Disconnected)
        val viewModel = createViewModel(ShareValidation.Valid("shared text"))
        viewModel.confirmSend()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.sent)
        assertEquals(Banner.Disconnected, viewModel.state.value.error)
    }

    @Test
    fun invalidShareNeverSends() = runTest(dispatcher) {
        val viewModel = createViewModel(ShareValidation.UnsupportedType)
        viewModel.confirmSend()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.invalid)
        assertEquals(0, repository.sendCalls)
    }

    @Test
    fun missingTokenRequiresPairingAndDoesNotSend() = runTest(dispatcher) {
        sessionStore = FakeSessionStore(token = null)
        val viewModel = createViewModel(ShareValidation.Valid("shared text"))
        assertTrue(viewModel.state.value.needsPairing)
        viewModel.confirmSend()
        advanceUntilIdle()
        assertEquals(0, repository.sendCalls)
    }
}
