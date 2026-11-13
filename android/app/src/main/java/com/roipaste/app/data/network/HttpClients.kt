package com.roipaste.app.data.network

import com.roipaste.app.domain.SessionStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import okhttp3.OkHttpClient

object HttpClients {
    fun default(): OkHttpClient = baseBuilder().build()

    fun forSession(store: SessionStore): OkHttpClient {
        val pin = store.currentPin()
        val url = store.baseUrl
        if (url.startsWith("http://")) {
            return default()
        }
        if (pin.isNullOrBlank()) {
            return default()
        }
        return pinned(pin, hostOf(url))
    }

    fun pinned(pin: String, expectedHost: String): OkHttpClient {
        val trustManager = SpkiPinTrustManager(pin)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustManager), null)
        return baseBuilder()
            .sslSocketFactory(context.socketFactory, trustManager)
            .hostnameVerifier(PinnedHostnameVerifier(expectedHost))
            .build()
    }

    fun isLoopbackHost(host: String): Boolean {
        return host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"
    }

    fun hostOf(url: String): String {
        val withoutScheme = url.substringAfter("://")
        val hostPort = withoutScheme.substringBefore("/")
        return hostPort.substringBefore("%").removePrefix("[").substringBefore("]").substringBefore(":")
    }

    private fun baseBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .connectTimeout(ApiClient.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ApiClient.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiClient.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
    }
}
