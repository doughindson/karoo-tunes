package com.doughindson.karootunes.data.http

import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    val isSuccess: Boolean get() = status in 200..299

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun bodyText(): String = body.decodeToString()
}

/** The request never got an HTTP response: no connection, timeout, or the companion app is unreachable. */
class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Makes HTTP requests. Throws [NetworkException] when no response arrives; any HTTP status is a normal return. */
interface HttpTransport {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        timeout: Duration = 30.seconds,
    ): HttpResponse
}

private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())

/** Spotify honours `Accept-Encoding: gzip`, and the Karoo proxy passes the compressed bytes through untouched. */
internal fun ByteArray.gunzipIfNeeded(): ByteArray =
    if (size >= 2 && this[0] == GZIP_MAGIC[0] && this[1] == GZIP_MAGIC[1]) {
        GZIPInputStream(inputStream()).use { it.readBytes() }
    } else {
        this
    }
