package com.pagewhisper.app.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads and unpacks the Kokoro voice model into app-private storage (it is far too big to ship inside the app).
 * No resume yet. If a smaller int8 bundle is published on the same release page, point MODEL_URL and DIR_NAME at it.
 */
object ModelStore {
    const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
    const val DIR_NAME = "kokoro-multi-lang-v1_0"

    fun dir(ctx: Context) = File(ctx.filesDir, DIR_NAME)

    fun isReady(ctx: Context): Boolean {
        val d = dir(ctx)
        return File(d, "model.onnx").exists() && File(d, "voices.bin").exists() && File(d, "tokens.txt").exists() &&
            File(d, "espeak-ng-data").isDirectory && File(d, ".complete").exists()
    }

    suspend fun download(ctx: Context, onProgress: (done: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val root = ctx.filesDir
        val prefix = root.canonicalPath + File.separator
        dir(ctx).deleteRecursively()
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
        }
        val counting = CountingInputStream(conn.inputStream, conn.contentLengthLong, onProgress)
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counting, 1 shl 16))).use { tar ->
            while (true) {
                coroutineContext.ensureActive()
                val e = tar.nextEntry as? TarArchiveEntry ?: break
                val out = File(root, e.name)
                if (!out.canonicalPath.startsWith(prefix)) continue // path traversal guard
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { tar.copyTo(it) }
                }
            }
        }
        File(dir(ctx), ".complete").writeText("ok")
    }
}

private class CountingInputStream(src: InputStream, private val total: Long, private val cb: (Long, Long) -> Unit) : FilterInputStream(src) {
    private var done = 0L
    private var last = 0L
    private fun tick(n: Int) {
        if (n <= 0) return
        done += n
        val now = System.nanoTime()
        if (now - last > 250_000_000L) { last = now; cb(done, total) }
    }
    override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { tick(it) }
}
