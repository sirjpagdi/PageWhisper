package com.pagewhisper.app.tts

import android.content.Context
import android.os.Process
import java.io.File
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Kokoro through sherpa-onnx. If a parameter name below does not compile, Ctrl+click OfflineTts in the AAR
 * and match its current constructor (the same note as in the spike project).
 */
class SherpaEngine(ctx: Context, val threads: Int, val background: Boolean, val bundle: ModelBundle) {
    private val tts: OfflineTts
    val sampleRate: Int

    init {
        val dir = ModelStore.dir(ctx, bundle)
        val d = dir.absolutePath
        // Only name the optional files the bundle really has.
        val lexicons = listOf("lexicon-us-en.txt", "lexicon-zh.txt").filter { File(dir, it).exists() }.joinToString(",") { "$d/$it" }
        val kokoro = OfflineTtsKokoroModelConfig(
            model = "$d/${bundle.modelFile}",
            voices = "$d/voices.bin",
            tokens = "$d/tokens.txt",
            dataDir = "$d/espeak-ng-data",
            dictDir = if (File(dir, "dict").isDirectory) "$d/dict" else "",
            lexicon = lexicons,
            lang = "en-us",
        )
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = threads, debug = false, provider = "cpu"),
            maxNumSentences = 1,
        )
        tts = OfflineTts(config = config)
        sampleRate = tts.sampleRate()
    }

    fun generate(text: String, sid: Int, speed: Float): FloatArray = tts.generate(text = text, sid = sid, speed = speed).samples

    fun release() = tts.release()
}

/**
 * One shared engine. Speech is generated on a single thread because the model must not run twice at once.
 *
 * Heat control. The audio is only generated as fast as it is played, so the heat of long reading depends on how much
 * energy each second of speech costs. Three profiles, chosen in Settings:
 *  - fast: up to 3 threads at normal priority (quickest, warmest);
 *  - balanced: 2 threads at normal priority;
 *  - cool: 2 threads kept on the phone's efficient cores (background priority, inherited by the engine's worker
 *    threads), with more audio kept in reserve. If the voice cannot keep up (the speaker runs dry), Playback calls
 *    [escalate] and the profile moves one step towards fast until the app is restarted or the setting is changed.
 */
object EngineHolder {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "pw-tts") }.asCoroutineDispatcher()

    @Volatile private var engine: SherpaEngine? = null

    private val ORDER = listOf("cool", "balanced", "fast")
    private val maxThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 3)

    /** Where the chosen profile and the chosen voice model come from (set by Playback). */
    @Volatile var modeProvider: () -> String = { "cool" }
    @Volatile var modelProvider: () -> String = { "standard" }
    @Volatile private var mode = "cool"
    @Volatile private var escalation = 0
    @Volatile private var urgent = false
    @Volatile private var builtAt = 0L
    @Volatile private var lastEscalation = 0L

    /** Set by Playback when the phone reports it is getting hot; the engine then runs on at most 2 threads. */
    @Volatile var hot: Boolean = false

    private fun syncMode() {
        val m = modeProvider()
        if (m != mode) { mode = m; escalation = 0; urgent = true }
    }

    /** The profile in use: the chosen one, moved towards "fast" once for each time the voice could not keep up. */
    fun currentMode(): String {
        syncMode()
        return ORDER[(ORDER.indexOf(mode).coerceAtLeast(0) + escalation).coerceAtMost(ORDER.lastIndex)]
    }

    /** Moves one step towards fast. False if it is already fast or a step was taken less than 30 seconds ago. */
    fun escalate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastEscalation < 30_000L || currentMode() == "fast") return false
        escalation++; urgent = true; lastEscalation = now
        return true
    }

    private fun wantThreads(m: String): Int = (if (m == "fast") maxThreads else 2).let { if (hot) minOf(it, 2) else it }

    private fun ensure(ctx: Context): SherpaEngine {
        val m = currentMode()
        val threads = wantThreads(m)
        val bg = m == "cool"
        val bundle = ModelStore.active(ctx, modelProvider()) ?: throw IllegalStateException("MODEL_MISSING")
        val current = engine
        if (current != null) {
            val sameModel = current.bundle.id == bundle.id
            if (sameModel && current.threads == threads && current.background == bg) { urgent = false; return current }
            // Moving to a cooler setup applies at once. Moving back to a hotter one after cooling down waits 3 minutes
            // (reloading the model costs a few seconds), unless it is a rescue or the person changed a setting.
            val hotter = threads > current.threads || (!bg && current.background)
            if (sameModel && hotter && !urgent && System.currentTimeMillis() - builtAt < 3 * 60_000L) return current
            current.release()
            engine = null
        }
        // The engine's worker threads are created here and inherit this thread's priority and CPU group.
        runCatching { Process.setThreadPriority(if (bg) Process.THREAD_PRIORITY_BACKGROUND else Process.THREAD_PRIORITY_DEFAULT) }
        urgent = false
        return SherpaEngine(ctx.applicationContext, threads, bg, bundle).also { engine = it; builtAt = System.currentTimeMillis() }
    }

    suspend fun get(ctx: Context): SherpaEngine = withContext(dispatcher) { ensure(ctx) }

    suspend fun generate(ctx: Context, text: String, sid: Int, speed: Float): FloatArray =
        withContext(dispatcher) { ensure(ctx).generate(text, sid, speed) }

    fun release() {
        engine?.release()
        engine = null
    }
}
