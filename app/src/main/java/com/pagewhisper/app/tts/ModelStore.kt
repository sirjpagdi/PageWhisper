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

/** The downloadable Kokoro voice model. */
class ModelBundle(val id: String, val url: String, val dirName: String, val modelFile: String, val title: String, val size: String)

/**
 * Downloads and unpacks the Kokoro voice model into app-private storage (it is far too big to ship inside the app).
 * No resume yet.
 */
object ModelStore {
    val STANDARD = ModelBundle(
        "standard", "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2",
        "kokoro-multi-lang-v1_0", "model.onnx", "Standard", "about 350 MB",
    )

    fun dir(ctx: Context, b: ModelBundle) = File(ctx.filesDir, b.dirName)

    fun isReady(ctx: Context, b: ModelBundle): Boolean {
        val d = dir(ctx, b)
        return File(d, b.modelFile).exists() && File(d, "voices.bin").exists() && File(d, "tokens.txt").exists() &&
            File(d, "espeak-ng-data").isDirectory && File(d, ".complete").exists()
    }

    fun isReady(ctx: Context): Boolean = isReady(ctx, STANDARD)

    /** The model to speak with, or none if it is not installed. */
    fun active(ctx: Context): ModelBundle? = if (isReady(ctx, STANDARD)) STANDARD else null

    /** Removes what an earlier test version left behind: the int8 model and half-finished downloads. */
    fun removeLegacy(ctx: Context) {
        File(ctx.filesDir, "kokoro-int8-multi-lang-v1_0").deleteRecursively()
        ctx.filesDir.listFiles { f -> f.name.startsWith("download-") && f.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }
    }

    fun delete(ctx: Context, b: ModelBundle) { dir(ctx, b).deleteRecursively() }

    suspend fun download(ctx: Context, b: ModelBundle, onProgress: (done: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val root = ctx.filesDir
        // Unpack into a staging folder first, so a model that is already installed survives a failed download.
        val staging = File(root, "download-${b.id}.tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val prefix = staging.canonicalPath + File.separator
            val conn = (URL(b.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
            }
            val counting = CountingInputStream(conn.inputStream, conn.contentLengthLong, onProgress)
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counting, 1 shl 16))).use { tar ->
                while (true) {
                    coroutineContext.ensureActive()
                    val e = tar.nextEntry as? TarArchiveEntry ?: break
                    val out = File(staging, e.name)
                    if (!out.canonicalPath.startsWith(prefix)) continue // path traversal guard
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { tar.copyTo(it) }
                    }
                }
            }
            // The files sit in one top folder inside the archive (or directly in the staging folder).
            val candidates = listOf(staging) + (staging.listFiles()?.filter { it.isDirectory } ?: emptyList())
            val src = candidates.firstOrNull { File(it, b.modelFile).exists() }
                ?: throw IllegalStateException("The download did not contain ${b.modelFile}.")
            val target = dir(ctx, b)
            target.deleteRecursively()
            if (!src.renameTo(target)) src.copyRecursively(target, overwrite = true)
            File(target, ".complete").writeText("ok")
        } finally {
            staging.deleteRecursively()
        }
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
