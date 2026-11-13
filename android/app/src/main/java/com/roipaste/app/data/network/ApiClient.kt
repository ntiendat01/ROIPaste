package com.roipaste.app.data.network

import com.roipaste.app.domain.SessionStore
import com.roipaste.app.domain.TransferError
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ApiClient(
    private val sessionStore: SessionStore,
    private val client: OkHttpClient,
) {
    fun get(path: String, authenticated: Boolean, retry: Boolean = true): HttpResult {
        return execute(buildGet(path), authenticated, retryOnIo = retry)
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

    private fun execute(request: Request, authenticated: Boolean, retryOnIo: Boolean): HttpResult {
        val authorized = authorize(request, authenticated) ?: return HttpResult.Failure(TransferError.Unauthenticated)
        val first = call(authorized)
        if (first != null) {
            return first
        }
        if (!retryOnIo) {
            return HttpResult.Failure(TransferError.Disconnected)
        }
        return call(authorized) ?: HttpResult.Failure(TransferError.Disconnected)
    }

    /**
     * Returns a mapped HTTP result, or null when the failure is a retryable I/O error
     * (not a timeout). Timeouts are never retried so the UI can show a timeout state.
     */
    private fun call(request: Request): HttpResult? {
        return try {
            client.newCall(request).execute().use { response ->
                toResult(response)
            }
        } catch (_: SocketTimeoutException) {
            HttpResult.Failure(TransferError.Timeout)
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
        return request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:5000"
        const val CONNECT_TIMEOUT_SECONDS = 3L
        const val READ_TIMEOUT_SECONDS = 5L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val JSON = HttpTransferRepository.createJson()

        fun createClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
        }
    }
}
