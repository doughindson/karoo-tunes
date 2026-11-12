package com.doughindson.karootunes.data.auth

import android.util.Log
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.AppJson
import com.doughindson.karootunes.data.http.HttpResponse
import com.doughindson.karootunes.data.http.HttpTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** There is no usable Spotify session: never logged in, logged out, or the refresh token was revoked. */
class NotLoggedInException(message: String = "Not logged in to Spotify") : Exception(message)

/** Spotify's token endpoint answered with an error that does not mean the session is gone (e.g. 5xx). */
class AuthServiceException(val status: Int, message: String) : Exception(message)

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

@Serializable
internal data class TokenErrorResponse(
    val error: String? = null,
    @SerialName("error_description") val description: String? = null,
)

/**
 * Spotify OAuth (authorization code + PKCE) and access-token lifecycle.
 *
 * Refreshes are single-flight: concurrent callers that find an expiring token wait for one refresh.
 * Only an `invalid_grant` answer ends the session; network failures and server errors keep the
 * stored token so a dead zone mid-ride doesn't log the user out.
 */
class SpotifyAuth(
    private val transport: HttpTransport,
    private val store: TokenStore,
    private val clientId: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val refreshLock = Mutex()
    private val random = SecureRandom()

    val isLoggedIn: Flow<Boolean> = store.token.map { it != null }.distinctUntilChanged()

    /** Starts a login: remembers the PKCE state and returns the URL to open in a browser. */
    suspend fun beginAuthorization(): String {
        val pending = PendingAuthorization(state = randomToken(16), codeVerifier = randomToken(32))
        store.savePending(pending)
        return AUTHORIZE_URL + "?" + formEncode(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to REDIRECT_URI,
            "scope" to SCOPES,
            "code_challenge_method" to "S256",
            "code_challenge" to codeChallenge(pending.codeVerifier),
            "state" to pending.state,
        )
    }

    /** Finishes a login from the redirect's query parameters. */
    suspend fun completeAuthorization(code: String?, state: String?, error: String?) {
        val pending = store.pending()
        if (error != null) throw IllegalStateException("Spotify login was cancelled ($error)")
        requireNotNull(code) { "Spotify did not return an authorization code" }
        check(pending != null && pending.state == state) { "This login link has expired. Please try again." }

        val response = transport.request(
            "POST", TOKEN_URL, FORM_HEADERS,
            formEncode(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to clientId,
                "code_verifier" to pending.codeVerifier,
            ).encodeToByteArray(),
        )
        if (!response.isSuccess) {
            throw AuthServiceException(response.status, response.tokenError()?.description ?: "Login failed (HTTP ${response.status})")
        }
        store.saveToken(response.toStoredToken(previousRefreshToken = null))
        store.savePending(null)
        Log.i(TAG, "Logged in to Spotify")
    }

    /** A currently valid access token, refreshing it first if it is about to expire. */
    suspend fun accessToken(): String {
        val token = store.token.first() ?: throw NotLoggedInException()
        if (!token.isExpiring(now())) return token.accessToken
        return refreshLock.withLock {
            val current = store.token.first() ?: throw NotLoggedInException()
            if (current.isExpiring(now())) refresh(current).accessToken else current.accessToken
        }
    }

    /** Called when Spotify rejected [rejected] with 401. Refreshes unless another caller already did. */
    suspend fun refreshAfterRejection(rejected: String): String = refreshLock.withLock {
        val current = store.token.first() ?: throw NotLoggedInException()
        if (current.accessToken != rejected) current.accessToken else refresh(current).accessToken
    }

    suspend fun logout() {
        store.saveToken(null)
        store.savePending(null)
    }

    private suspend fun refresh(token: StoredToken): StoredToken {
        val refreshToken = token.refreshToken ?: run {
            store.saveToken(null)
            throw NotLoggedInException()
        }
        Log.i(TAG, "Refreshing Spotify access token")
        val response = transport.request(
            "POST", TOKEN_URL, FORM_HEADERS,
            formEncode(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to clientId,
            ).encodeToByteArray(),
        )

        if (response.isSuccess) {
            return response.toStoredToken(previousRefreshToken = refreshToken).also { store.saveToken(it) }
        }

        val error = response.tokenError()
        if (response.status == 400 && error?.error == "invalid_grant") {
            Log.w(TAG, "Spotify refresh token was rejected; logging out")
            store.saveToken(null)
            throw NotLoggedInException("Spotify session expired. Please log in again.")
        }
        throw AuthServiceException(response.status, error?.description ?: "Could not refresh Spotify login (HTTP ${response.status})")
    }

    private fun HttpResponse.toStoredToken(previousRefreshToken: String?): StoredToken {
        val parsed = AppJson.decodeFromString(TokenResponse.serializer(), bodyText())
        return StoredToken(
            accessToken = parsed.accessToken,
            // Spotify may omit the refresh token on refresh; the previous one then stays valid.
            refreshToken = parsed.refreshToken ?: previousRefreshToken,
            expiresAtMs = now() + parsed.expiresIn * 1000,
            scope = parsed.scope,
        )
    }

    private fun HttpResponse.tokenError(): TokenErrorResponse? =
        runCatching { AppJson.decodeFromString(TokenErrorResponse.serializer(), bodyText()) }.getOrNull()

    private fun randomToken(bytes: Int): String =
        ByteArray(bytes).also(random::nextBytes).let(base64Url::encodeToString)

    private fun codeChallenge(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    companion object {
        const val REDIRECT_URI = "karootunes://oauth2redirect"
        private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val SCOPES = "playlist-read-private playlist-read-collaborative user-library-read " +
            "user-library-modify user-read-playback-state user-read-currently-playing " +
            "user-modify-playback-state user-read-playback-position"
        private val FORM_HEADERS = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        private val base64Url = Base64.getUrlEncoder().withoutPadding()

        internal fun formEncode(vararg params: Pair<String, String>): String =
            params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8").replace("+", "%20")}" }
    }
}
