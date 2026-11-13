package com.roipaste.app.di

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.roipaste.app.data.clipboard.AndroidClipboardGateway
import com.roipaste.app.data.clipboard.SystemClipboardSource
import com.roipaste.app.data.network.ApiClient
import com.roipaste.app.data.network.HttpClients
import com.roipaste.app.data.network.PairingClient
import com.roipaste.app.data.network.HttpTransferRepository
import com.roipaste.app.data.security.AndroidKeystoreAesGcmCipher
import com.roipaste.app.data.settings.DataStoreSessionStore
import com.roipaste.app.data.settings.sessionDataStore
import com.roipaste.app.domain.FetchClip
import com.roipaste.app.domain.MutableFocusChecker
import com.roipaste.app.domain.PauseSync
import com.roipaste.app.domain.SendClip
import com.roipaste.app.domain.ShareValidation
import com.roipaste.app.presentation.HomeViewModel
import com.roipaste.app.presentation.ShareViewModel
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    val mainFocus = MutableFocusChecker()

    private val appContext = context.applicationContext
    private val sessionStoreImpl = DataStoreSessionStore(
        dataStore = appContext.sessionDataStore,
        cipher = AndroidKeystoreAesGcmCipher(),
    )
    val sessionStore = sessionStoreImpl

    private val api = ApiClient(sessionStore) { HttpClients.forSession(sessionStore) }
    val pairingClient = PairingClient()
    val repository = HttpTransferRepository(api)
    val sendClip = SendClip(repository)
    val fetchClip = FetchClip(repository)
    val pauseSync = PauseSync(sessionStore)

    init {
        runBlocking { sessionStoreImpl.hydrate() }
    }

    fun homeViewModel(): HomeViewModel {
        val clipboard = AndroidClipboardGateway(
            source = systemClipboard(),
            focusChecker = mainFocus,
        )
        return HomeViewModel(
            sendClip,
            fetchClip,
            pauseSync,
            clipboard,
            sessionStore,
            repository,
            pairingClient,
            uiActive = { mainFocus.resumed },
        )
    }

    fun shareViewModel(validation: ShareValidation): ShareViewModel {
        return ShareViewModel(validation, sendClip, sessionStore)
    }

    fun homeFactory(): ViewModelProvider.Factory = factory { homeViewModel() }

    fun shareFactory(validation: ShareValidation): ViewModelProvider.Factory = factory { shareViewModel(validation) }

    private fun systemClipboard(): SystemClipboardSource {
        val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return SystemClipboardSource(manager, appContext)
    }

    private fun factory(create: () -> ViewModel): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        }
    }
}
