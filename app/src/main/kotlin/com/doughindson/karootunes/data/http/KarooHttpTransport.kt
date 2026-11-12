package com.doughindson.karootunes.data.http

import android.util.Log
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.karoo.KarooGateway
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Sends requests through the Karoo companion app when running on a Karoo (the Karoo has no
 * internet of its own), and directly otherwise (emulator, phone, Wi-Fi debugging).
 */
class KarooHttpTransport(private val karoo: KarooGateway) : HttpTransport {

    private val directClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout)
            expectSuccess = false
        }
    }

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeout: Duration,
    ): HttpResponse {
        val response = if (karoo.connected.value) viaKaroo(method, url, headers, body, timeout) else direct(method, url, headers, body, timeout)
        return HttpResponse(response.status, response.headers, response.body.gunzipIfNeeded())
    }

    private suspend fun viaKaroo(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeout: Duration,
    ): HttpResponse {
        val responses = callbackFlow {
            val consumerId = karoo.service.addConsumer(
                OnHttpResponse.MakeHttpRequest(method = method, url = url, headers = headers, body = body, waitForConnection = false),
                onError = { message: String -> close(NetworkException(message)) },
                onEvent = { event: OnHttpResponse ->
                    (event.state as? HttpResponseState.Complete)?.let {
                        trySend(it)
                        close()
                    }
                },
            )
            awaitClose { karoo.service.removeConsumer(consumerId) }
        }

        val complete = try {
            withTimeout(timeout) { responses.first() }
        } catch (e: TimeoutCancellationException) {
            throw NetworkException("Request timed out", e)
        }

        // The companion reports connectivity failures as an error with no real HTTP status.
        if (complete.statusCode !in 100..599) {
            throw NetworkException(complete.error ?: "No connection")
        }
        return HttpResponse(complete.statusCode, complete.headers, complete.body ?: ByteArray(0))
    }

    private suspend fun direct(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeout: Duration,
    ): HttpResponse = try {
        val response = directClient.request(url) {
            this.method = HttpMethod.parse(method)
            headers.forEach { (key, value) -> header(key, value) }
            if (body != null) setBody(body)
            timeout { requestTimeoutMillis = timeout.inWholeMilliseconds }
        }
        HttpResponse(
            response.status.value,
            response.headers.entries().associate { it.key to it.value.joinToString(",") },
            response.readRawBytes(),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Direct request to $url failed", e)
        throw NetworkException(e.message ?: "Request failed", e)
    }
}
