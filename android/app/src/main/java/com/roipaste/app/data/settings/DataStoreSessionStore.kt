package com.roipaste.app.data.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roipaste.app.data.network.ApiClient
import com.roipaste.app.data.security.TokenCipher
import com.roipaste.app.domain.SessionStore
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "roipaste_session")

class DataStoreSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val defaultBaseUrl: String = ApiClient.DEFAULT_BASE_URL,
) : SessionStore {
    private val tokenCache = AtomicReference<String?>(null)

    @Volatile
    private var urlCache: String = defaultBaseUrl

    @Volatile
    private var pinCache: String? = null

    @Volatile
    private var transportCache: String = "usb"

    @Volatile
    private var deviceIdCache: String? = null

    override val token: Flow<String?> =
        dataStore.data
            .map { prefs -> decodeToken(prefs[TOKEN_BLOB]) }
            .distinctUntilChanged()
            .onEach { value -> tokenCache.set(value) }

    override val syncPaused: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SYNC_PAUSED] ?: false }.distinctUntilChanged()

    override val baseUrl: String
        get() = urlCache

    override fun currentToken(): String? = tokenCache.get()

    override fun currentPin(): String? = pinCache

    override fun currentTransport(): String = transportCache

    override fun currentDeviceId(): String? = deviceIdCache

    override suspend fun setToken(token: String?) {
        dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(TOKEN_BLOB)
            } else {
                val blob = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
                prefs[TOKEN_BLOB] = Base64.encodeToString(blob, Base64.NO_WRAP)
            }
        }
        tokenCache.set(token?.takeIf { it.isNotBlank() })
    }

    override suspend fun setSyncPaused(paused: Boolean) {
        dataStore.edit { prefs -> prefs[SYNC_PAUSED] = paused }
    }

    override suspend fun setBaseUrl(url: String) {
        val normalized = normalizeUrl(url)
        dataStore.edit { prefs -> prefs[BASE_URL] = normalized }
        urlCache = normalized
    }

    override suspend fun setUsbSession(token: String) {
        dataStore.edit { prefs ->
            val blob = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
            prefs[TOKEN_BLOB] = Base64.encodeToString(blob, Base64.NO_WRAP)
            prefs[BASE_URL] = defaultBaseUrl
            prefs.remove(PIN)
            prefs[TRANSPORT] = "usb"
        }
        tokenCache.set(token)
        urlCache = defaultBaseUrl
        pinCache = null
        transportCache = "usb"
    }

    override suspend fun setLanSession(
        credential: String,
        baseUrl: String,
        pin: String,
        deviceId: String,
        alias: String,
    ) {
        val normalized = normalizeUrl(baseUrl)
        require(normalized.startsWith("https://")) { "LAN session must use HTTPS" }
        dataStore.edit { prefs ->
            val blob = cipher.encrypt(credential.toByteArray(Charsets.UTF_8))
            prefs[TOKEN_BLOB] = Base64.encodeToString(blob, Base64.NO_WRAP)
            prefs[BASE_URL] = normalized
            prefs[PIN] = pin
            prefs[TRANSPORT] = "lan"
            prefs[DEVICE_ID] = deviceId
            prefs[ALIAS] = alias
        }
        tokenCache.set(credential)
        urlCache = normalized
        pinCache = pin
        transportCache = "lan"
        deviceIdCache = deviceId
    }

    override suspend fun clearPairing() {
        dataStore.edit { prefs ->
            prefs.remove(TOKEN_BLOB)
            prefs.remove(PIN)
            prefs.remove(DEVICE_ID)
            prefs.remove(ALIAS)
            prefs[BASE_URL] = defaultBaseUrl
            prefs[TRANSPORT] = "usb"
        }
        tokenCache.set(null)
        urlCache = defaultBaseUrl
        pinCache = null
        transportCache = "usb"
        deviceIdCache = null
    }

    suspend fun hydrate() {
        val prefs = dataStore.data.first()
        tokenCache.set(decodeToken(prefs[TOKEN_BLOB]))
        urlCache = prefs[BASE_URL]?.let { normalizeUrl(it) } ?: defaultBaseUrl
        pinCache = prefs[PIN]
        transportCache = prefs[TRANSPORT] ?: "usb"
        deviceIdCache = prefs[DEVICE_ID]
    }

    private fun decodeToken(encoded: String?): String? {
        if (encoded.isNullOrBlank()) {
            return null
        }
        return try {
            val plain = cipher.decrypt(Base64.decode(encoded, Base64.NO_WRAP))
            String(plain, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/')

    private companion object {
        val TOKEN_BLOB = stringPreferencesKey("token_blob")
        val SYNC_PAUSED = booleanPreferencesKey("sync_paused")
        val BASE_URL = stringPreferencesKey("base_url")
        val PIN = stringPreferencesKey("spki_pin")
        val TRANSPORT = stringPreferencesKey("transport")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ALIAS = stringPreferencesKey("device_alias")
    }
}
