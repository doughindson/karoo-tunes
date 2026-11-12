package com.doughindson.karootunes.data

import com.doughindson.karootunes.data.auth.PendingAuthorization
import com.doughindson.karootunes.data.auth.StoredToken
import com.doughindson.karootunes.data.auth.TokenStore
import com.doughindson.karootunes.data.http.HttpResponse
import com.doughindson.karootunes.data.http.HttpTransport
import com.doughindson.karootunes.data.http.NetworkException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class InMemoryTokenStore(initial: StoredToken? = null) : TokenStore {
    override val token = MutableStateFlow(initial)
    var pendingValue: PendingAuthorization? = null

    override suspend fun saveToken(token: StoredToken?) { this.token.value = token }
    override suspend fun pending() = pendingValue
    override suspend fun savePending(pending: PendingAuthorization?) { pendingValue = pending }
}

data class RecordedRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

/** Answers requests from a queue of scripted responses; `null` simulates no connection. */
class FakeTransport : HttpTransport {
    val requests = mutableListOf<RecordedRequest>()
    private val responses = ArrayDeque<(RecordedRequest) -> HttpResponse?>()

    fun respond(status: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        responses.addLast { HttpResponse(status, headers, body.encodeToByteArray()) }
    }

    fun failWithNetworkError() {
        responses.addLast { null }
    }

    override suspend fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?, timeout: Duration): HttpResponse {
        val request = RecordedRequest(method, url, headers, body?.decodeToString())
        requests += request
        val next = responses.removeFirstOrNull() ?: error("Unexpected request: $method $url")
        return next(request) ?: throw NetworkException("No connection")
    }
}

fun tokenJson(access: String, refresh: String? = null, expiresIn: Long = 3600) =
    """{"access_token":"$access","token_type":"Bearer","expires_in":$expiresIn${refresh?.let { ",\"refresh_token\":\"$it\"" } ?: ""}}"""

/** Like assertThrows, but the (inlined) block may call suspend functions. */
inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} to be thrown")
}
