package com.doughindson.karootunes.data

import com.doughindson.karootunes.data.auth.AuthServiceException
import com.doughindson.karootunes.data.auth.NotLoggedInException
import com.doughindson.karootunes.data.auth.PendingAuthorization
import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.data.auth.StoredToken
import com.doughindson.karootunes.data.http.NetworkException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthTest {
    private val now = 1_000_000L
    private val transport = FakeTransport()

    private fun auth(store: InMemoryTokenStore) = SpotifyAuth(transport, store, clientId = "client", now = { now })
    private fun validToken() = StoredToken("access", "refresh", expiresAtMs = now + 3_600_000)
    private fun expiredToken() = StoredToken("old-access", "refresh", expiresAtMs = now - 1)

    @Test
    fun `valid token is used without refreshing`() = runTest {
        assertEquals("access", auth(InMemoryTokenStore(validToken())).accessToken())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `expiring token is refreshed and the old refresh token kept when none is returned`() = runTest {
        val store = InMemoryTokenStore(expiredToken())
        transport.respond(200, tokenJson("new-access"))

        assertEquals("new-access", auth(store).accessToken())
        assertEquals("refresh", store.token.value?.refreshToken)
        assertEquals(now + 3_600_000, store.token.value?.expiresAtMs)
        assertTrue(transport.requests.single().body!!.contains("grant_type=refresh_token"))
    }

    @Test
    fun `network failure during refresh keeps the session`() = runTest {
        val store = InMemoryTokenStore(expiredToken())
        transport.failWithNetworkError()

        expectThrows<NetworkException> { auth(store).accessToken() }
        assertEquals(expiredToken(), store.token.value)
    }

    @Test
    fun `server error during refresh keeps the session`() = runTest {
        val store = InMemoryTokenStore(expiredToken())
        transport.respond(503)

        expectThrows<AuthServiceException> { auth(store).accessToken() }
        assertNotNull(store.token.value)
    }

    @Test
    fun `revoked refresh token logs out`() = runTest {
        val store = InMemoryTokenStore(expiredToken())
        transport.respond(400, """{"error":"invalid_grant","error_description":"Refresh token revoked"}""")

        expectThrows<NotLoggedInException> { auth(store).accessToken() }
        assertNull(store.token.value)
    }

    @Test
    fun `concurrent callers share one refresh`() = runTest {
        val store = InMemoryTokenStore(expiredToken())
        transport.respond(200, tokenJson("new-access"))
        val auth = auth(store)

        val tokens = List(5) { async { auth.accessToken() } }.awaitAll()

        assertEquals(List(5) { "new-access" }, tokens)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `rejected token is refreshed only if nobody else already did`() = runTest {
        val store = InMemoryTokenStore(validToken())
        // Someone else already replaced "stale": no request needed.
        assertEquals("access", auth(store).refreshAfterRejection("stale"))
        assertTrue(transport.requests.isEmpty())

        transport.respond(200, tokenJson("fresh"))
        assertEquals("fresh", auth(store).refreshAfterRejection("access"))
    }

    @Test
    fun `login saves pending state and completes only with the matching state`() = runTest {
        val store = InMemoryTokenStore()
        val auth = auth(store)

        val url = auth.beginAuthorization()
        val pending = store.pendingValue!!
        assertTrue(url.contains("state=${pending.state}"))
        assertTrue(url.contains("code_challenge_method=S256"))

        expectThrows<IllegalStateException> { auth.completeAuthorization("code", "wrong-state", null) }
        assertNull(store.token.value)

        transport.respond(200, tokenJson("access", "refresh"))
        auth.completeAuthorization("code", pending.state, null)
        assertEquals("access", store.token.value?.accessToken)
        assertNull(store.pendingValue)
        assertTrue(transport.requests.single().body!!.contains("code_verifier=${pending.codeVerifier}"))
    }

    @Test
    fun `login survives the app restarting between redirect and completion`() = runTest {
        val store = InMemoryTokenStore().apply { pendingValue = PendingAuthorization("s", "verifier") }
        transport.respond(200, tokenJson("access", "refresh"))

        // A fresh SpotifyAuth instance, as after process death.
        auth(store).completeAuthorization("code", "s", null)

        assertEquals("access", store.token.value?.accessToken)
    }
}
