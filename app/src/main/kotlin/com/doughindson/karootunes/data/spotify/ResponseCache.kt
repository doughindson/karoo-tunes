package com.doughindson.karootunes.data.spotify

import java.io.File
import java.security.MessageDigest

/**
 * Raw JSON responses for library lists (playlists, saved songs, shows, …). They rarely change, and
 * on a Karoo every request goes through the phone, so cached pages make browsing fast. Entries never
 * expire; pull-to-refresh clears a list's [prefix].
 */
class ResponseCache(private val dir: File, private val maxEntries: Int = 500) {
    init {
        dir.mkdirs()
        // Older versions kept one whole-map file per list directly in the cache root.
        dir.parentFile?.listFiles { file -> file.name.startsWith("webapi_") }?.forEach { it.delete() }
    }

    @Synchronized
    fun read(prefix: String, key: String): String? =
        fileFor(prefix, key).takeIf { it.isFile }?.let { file -> runCatching { file.readText() }.getOrNull() }

    @Synchronized
    fun write(prefix: String, key: String, json: String) {
        val target = fileFor(prefix, key)
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        trim()
    }

    @Synchronized
    fun clear(prefix: String) {
        val start = sanitize(prefix) + "__"
        dir.listFiles { file -> file.name.startsWith(start) }?.forEach { it.delete() }
    }

    private fun trim() {
        val files = dir.listFiles() ?: return
        if (files.size <= maxEntries) return
        files.sortedBy { it.lastModified() }.take(files.size - maxEntries).forEach { it.delete() }
    }

    private fun fileFor(prefix: String, key: String) = File(dir, "${sanitize(prefix)}__${sha256(key).take(32)}.json")

    private fun sanitize(prefix: String) = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
