package com.doughindson.karootunes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.util.LruCache
import com.doughindson.karootunes.AppScope
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.http.HttpTransport
import com.doughindson.karootunes.data.http.NetworkException
import com.doughindson.karootunes.data.settings.SettingsRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Album art and playlist thumbnails: memory (LRU by bytes) → disk → network. Each URL is downloaded
 * at most once at a time, and memory hits never wait for anything, so the widget can render freely.
 */
class ThumbnailCache(
    context: Context,
    private val transport: HttpTransport,
    private val settings: SettingsRepository,
    private val appScope: AppScope,
) {
    private val dir = File(context.cacheDir, "thumbnails")
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        appScope.launch(Dispatchers.IO) {
            dir.mkdirs()
            val files = dir.listFiles().orEmpty()
            // Older versions named files by Base64 of the URL.
            files.filterNot { it.name.matches(HASH_NAME) }.forEach { it.delete() }
            files.filter { it.exists() }.sortedByDescending { it.lastModified() }.drop(MAX_DISK_FILES).forEach { it.delete() }
        }
    }

    /** Instant, memory-only lookup. */
    fun cached(url: String): Bitmap? = memory.get(url)

    suspend fun get(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val load = inFlight.computeIfAbsent(url) {
            appScope.async(Dispatchers.IO) { load(url) }.also { job -> job.invokeOnCompletion { inFlight.remove(url) } }
        }
        return load.await()
    }

    fun prefetch(url: String) {
        if (memory.get(url) == null) appScope.launch { get(url) }
    }

    /** Emits the cached bitmap right away (possibly null), then the loaded one if it wasn't cached. */
    fun bitmap(url: String?): Flow<Bitmap?> = flow {
        if (url == null) {
            emit(null)
            return@flow
        }
        val cached = cached(url)
        emit(cached)
        if (cached == null) get(url)?.let { emit(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun load(url: String): Bitmap? {
        val file = File(dir, sha256(url))
        if (file.isFile) {
            BitmapFactory.decodeFile(file.path)?.let { bitmap ->
                file.setLastModified(System.currentTimeMillis())
                memory.put(url, bitmap)
                return bitmap
            }
        }
        if (!downloadAllowed()) return null

        return try {
            val response = transport.request("GET", url)
            if (!response.isSuccess) return null
            val bitmap = BitmapFactory.decodeByteArray(response.body, 0, response.body.size) ?: return null
            dir.mkdirs()
            val temp = File(dir, file.name + ".tmp")
            temp.writeBytes(response.body)
            temp.renameTo(file)
            memory.put(url, bitmap)
            bitmap
        } catch (e: NetworkException) {
            Log.d(TAG, "Thumbnail download failed: ${e.message}")
            null
        }
    }

    private suspend fun downloadAllowed(): Boolean {
        val onWifi = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return onWifi || settings.current().downloadThumbnailsViaCompanion
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MEMORY_BYTES = 16 * 1024 * 1024
        const val MAX_DISK_FILES = 1_000
        val HASH_NAME = Regex("[0-9a-f]{64}")
    }
}
