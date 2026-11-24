package com.pagewhisper.app.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pagewhisper.app.text.Extractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Network access. No browser limits (CORS) apply on Android, so most sites can be read directly. */
object Fetcher {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en").apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    suspend fun bytes(url: String, maxBytes: Int = 8_000_000): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            body.byteStream().use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("image too large")
                }
            }
            out.toByteArray()
        }
    }

    /** The page itself first, then the reader service (it can often get past simple blocks). */
    suspend fun chapter(url: String): Result<Extractor.Extracted> {
        val errors = ArrayList<String>()
        try {
            return Result.success(Extractor.fromHtml(text(url), url))
        } catch (e: Throwable) {
            errors.add("direct: ${e.message}")
        }
        try {
            val md = text("https://r.jina.ai/$url", mapOf("Accept" to "text/plain"))
            val r = Extractor.fromMarkdown(md, url)
            if (r.body.length >= 250) return Result.success(r)
            errors.add("reader: too little text")
        } catch (e: Throwable) {
            errors.add("reader: ${e.message}")
        }
        return Result.failure(IOException(errors.joinToString(" · ")))
    }

    // ---------- covers ----------

    suspend fun coverCandidates(pageUrl: String): List<String> {
        try {
            val c = Extractor.coverFromHtml(text(pageUrl), pageUrl)
            if (c.isNotEmpty()) return c
        } catch (_: Throwable) {}
        try {
            val c = Extractor.coverFromMarkdown(text("https://r.jina.ai/$pageUrl", mapOf("Accept" to "text/plain")))
            if (c.isNotEmpty()) return c
        } catch (_: Throwable) {}
        return emptyList()
    }

    /** An image as a small JPEG (at most 240 x 340). Returns the bytes and the size. */
    data class Cover(val jpeg: ByteArray, val width: Int, val height: Int)

    fun toCover(raw: ByteArray): Cover {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth < 60 || bounds.outHeight < 60) throw IOException("image too small")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480 && bounds.outHeight / (sample * 2) >= 680) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("not an image")
        val s = minOf(240f / bmp.width, 340f / bmp.height, 1f)
        val w = maxOf(1, (bmp.width * s).toInt())
        val h = maxOf(1, (bmp.height * s).toInt())
        val scaled = if (w == bmp.width && h == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Cover(out.toByteArray(), w, h)
    }

    /** Finds a cover for a novel page: a portrait image is preferred; a wide one is kept only as a last resort. */
    suspend fun findCover(chapterUrl: String): Cover? {
        for (page in listOf(Extractor.novelPageUrl(chapterUrl), chapterUrl).distinct()) {
            var wide: Cover? = null
            for (u in coverCandidates(page)) {
                val cover = try {
                    toCover(bytes(u))
                } catch (_: Throwable) {
                    try { toCover(bytes("https://wsrv.nl/?url=" + java.net.URLEncoder.encode(u, "UTF-8") + "&w=480&output=jpg&q=85")) } catch (_: Throwable) { null }
                } ?: continue
                if (cover.height >= cover.width * 1.1) return cover
                if (wide == null) wide = cover
            }
            if (wide != null) return wide
        }
        return null
    }
}
