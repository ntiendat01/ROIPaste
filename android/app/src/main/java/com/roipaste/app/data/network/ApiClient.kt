package com.roipaste.app.data.network

import com.roipaste.app.domain.SessionStore
import com.roipaste.app.domain.TransferError
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class ApiClient(
    private val sessionStore: SessionStore,
    private val clientProvider: () -> OkHttpClient,
) {
    fun get(
        path: String,
        authenticated: Boolean,
        retry: Boolean = true,
        readTimeoutSeconds: Long? = null,
    ): HttpResult {
        return execute(buildGet(path), authenticated, retryOnIo = retry, readTimeoutSeconds = readTimeoutSeconds)
    }

    fun post(path: String, jsonBody: String, authenticated: Boolean): HttpResult {
        return execute(buildPost(path, jsonBody), authenticated, retryOnIo = false)
    }

    private fun buildGet(path: String): Request = Request.Builder().url(url(path)).get().build()

    private fun buildPost(path: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url(path))
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun url(path: String): String = sessionStore.baseUrl.trimEnd('/') + path

    private fun execute(
        request: Request,
        authenticated: Boolean,
        retryOnIo: Boolean,
        readTimeoutSeconds: Long? = null,
    ): HttpResult {
        val blocked = rejectInsecure(request) ?: return authorizeAndCall(
            request,
            authenticated,
            retryOnIo,
            readTimeoutSeconds,
        )
        return blocked
    }

    private fun rejectInsecure(request: Request): HttpResult? {
        val httpUrl = request.url
        val isHttp = httpUrl.scheme == "http"
        val loopback = HttpClients.isLoopbackHost(httpUrl.host)
        if (isHttp && !loopback) {
            return HttpResult.Failure(TransferError.InsecureTransport)
        }
        if (isHttp && sessionStore.currentPin() != null) {
            return HttpResult.Failure(TransferError.InsecureTransport)
        }
        return null
    }

    private fun authorizeAndCall(
        request: Request,
        authenticated: Boolean,
        retryOnIo: Boolean,
        readTimeoutSeconds: Long? = null,
    ): HttpResult {
        val authorized = authorize(request, authenticated) ?: return HttpResult.Failure(TransferError.Unauthenticated)
        val first = call(authorized, readTimeoutSeconds)
        if (first != null) {
            return first
        }
        if (!retryOnIo) {
            return HttpResult.Failure(TransferError.Disconnected)
        }
        return call(authorized, readTimeoutSeconds) ?: HttpResult.Failure(TransferError.Disconnected)
    }

    private fun call(request: Request, readTimeoutSeconds: Long? = null): HttpResult? {
        return try {
            clientFor(readTimeoutSeconds).newCall(request).execute().use { response -> toResult(response) }
        } catch (_: SocketTimeoutException) {
            HttpResult.Failure(TransferError.Timeout)
        } catch (_: SSLPeerUnverifiedException) {
            HttpResult.Failure(TransferError.PinMismatch)
        } catch (_: CertificateException) {
            HttpResult.Failure(TransferError.PinMismatch)
        } catch (_: SSLException) {
            HttpResult.Failure(TransferError.PinMismatch)
        } catch (_: IOException) {
            null
        }
    }

    private fun toResult(response: Response): HttpResult {
        if (response.code == 204) {
            return HttpResult.Success(204, null)
        }
        val body = response.body?.string()
        if (response.isSuccessful) {
            return HttpResult.Success(response.code, body)
        }
        return HttpResult.Failure(ErrorMapper.fromStatus(response.code, body, JSON))
    }

    private fun authorize(request: Request, authenticated: Boolean): Request? {
        if (!authenticated) {
            return request
        }
        val token = sessionStore.currentToken() ?: return null
        return request.newBuilder().header("Authorization", "Bearer $token").build()
    }

    private fun clientFor(readTimeoutSeconds: Long?): OkHttpClient {
        val base = clientProvider()
        if (readTimeoutSeconds == null) {
            return base
        }
        return base.newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build()
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:5000"
        const val CONNECT_TIMEOUT_SECONDS = 3L
        const val READ_TIMEOUT_SECONDS = 5L
        const val LONG_POLL_READ_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val JSON = HttpTransferRepository.createJson()

        fun createClient(): OkHttpClient = HttpClients.default()
    }
}
