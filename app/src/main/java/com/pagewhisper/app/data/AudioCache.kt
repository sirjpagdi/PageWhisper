package com.pagewhisper.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal fun sha1Hex(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * Speech that has already been made, kept on disk so playing it again costs almost no processor power (and so no heat).
 * One folder per chapter, one file per section of text. A section is found by its engine and language, voice, speed and exact spoken text,
 * so a changed voice, speed or pronunciation rule simply makes new speech. It lives in the app's cache folder, which
 * Android may empty when storage is short; nothing depends on it. See FEATURES.md ("Spec: Prepared chapters").
 */
object AudioCache {
    private lateinit var root: File

    fun init(ctx: Context) {
        root = File(ctx.cacheDir, "audio").also { it.mkdirs() }
    }

    private fun chapterName(url: String) = sha1Hex(url).take(16)

    private fun dirOf(url: String) = File(root, chapterName(url))

    fun key(tag: String, sid: Int, rate: Float, text: String): String = sha1Hex("$tag|$sid|$rate|$text")

    /** 16-bit little-endian samples: half the size of the engine's float samples, with no audible difference. */
    internal fun encode(pcm: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in pcm) b.putShort((f.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        return b.array()
    }

    internal fun decode(bytes: ByteArray): FloatArray {
        val s = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val out = FloatArray(s.remaining())
        for (i in out.indices) out[i] = s.get(i) / 32768f
        return out
    }

    fun has(url: String, key: String): Boolean = runCatching { File(dirOf(url), "$key.pcm").exists() }.getOrDefault(false)

    fun get(url: String, key: String): FloatArray? = runCatching {
        val f = File(dirOf(url), "$key.pcm")
        if (f.exists()) { dirOf(url).setLastModified(System.currentTimeMillis()); decode(f.readBytes()) } else null
    }.getOrNull()

    fun put(url: String, key: String, pcm: FloatArray) {
        runCatching {
            val d = dirOf(url)
            d.mkdirs()
            val tmp = File(d, "$key.tmp")
            tmp.writeBytes(encode(pcm))
            tmp.renameTo(File(d, "$key.pcm"))
            d.setLastModified(System.currentTimeMillis())
        }
    }

    /** Deletes the speech of every chapter that is not in [urls]. */
    fun keepOnly(urls: Collection<String>) {
        val keep = urls.map { chapterName(it) }.toSet()
        runCatching { root.listFiles()?.filter { it.isDirectory && it.name !in keep }?.forEach { it.deleteRecursively() } }
    }

    fun clear() {
        runCatching { root.listFiles()?.forEach { it.deleteRecursively() } }
    }

    fun totalBytes(): Long = runCatching { root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    /** Keeps the cache under [maxBytes] by deleting the least recently used chapters, never the one for [protectUrl]. */
    fun trim(maxBytes: Long, protectUrl: String) {
        runCatching {
            var total = totalBytes()
            if (total <= maxBytes) return
            val protect = chapterName(protectUrl)
            for (d in root.listFiles().orEmpty().filter { it.isDirectory && it.name != protect }.sortedBy { it.lastModified() }) {
                if (total <= maxBytes) break
                val size = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                d.deleteRecursively()
                total -= size
            }
        }
    }
}

/** A chapter fetched ahead of time, so it opens at once and can be read without a connection. */
@Serializable
data class CachedChapter(val title: String, val body: String, val next: String = "")

object ChapterCache {
    private lateinit var root: File

    fun init(ctx: Context) {
        root = File(ctx.cacheDir, "chapters").also { it.mkdirs() }
    }

    private fun nameOf(url: String) = sha1Hex(url).take(16) + ".json"

    fun get(url: String): CachedChapter? = runCatching {
        val f = File(root, nameOf(url))
        if (f.exists()) AppJson.decodeFromString(CachedChapter.serializer(), f.readText()) else null
    }.getOrNull()

    fun put(url: String, c: CachedChapter) {
        runCatching { File(root, nameOf(url)).writeText(AppJson.encodeToString(CachedChapter.serializer(), c)) }
    }

    fun keepOnly(urls: Collection<String>) {
        val keep = urls.map { nameOf(it) }.toSet()
        runCatching { root.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() } }
    }

    fun clear() {
        runCatching { root.listFiles()?.forEach { it.delete() } }
    }
}

/** Finding the chapter before or after another one in a novel's chapter list. */
object ChapterNav {
    fun tocNeighbor(url: String, delta: Int): String {
        if (url.isEmpty()) return ""
        val n = Repo.novelFor(url) ?: return ""
        val items = Repo.tocItems(n.id)
        val i = com.pagewhisper.app.text.ChapterList.indexOf(items, url)
        return if (i >= 0 && i + delta in items.indices) items[i + delta].url else ""
    }
}
