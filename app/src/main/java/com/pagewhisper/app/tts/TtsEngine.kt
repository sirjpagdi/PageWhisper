package com.pagewhisper.app.tts

import android.content.Context
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
class SherpaEngine(ctx: Context, val threads: Int) {
    private val tts: OfflineTts
    val sampleRate: Int

    init {
        val d = ModelStore.dir(ctx).absolutePath
        val kokoro = OfflineTtsKokoroModelConfig(
            model = "$d/model.onnx",
            voices = "$d/voices.bin",
            tokens = "$d/tokens.txt",
            dataDir = "$d/espeak-ng-data",
            dictDir = "$d/dict",
            lexicon = "$d/lexicon-us-en.txt,$d/lexicon-zh.txt",
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

/** One shared engine. Speech is generated on a single thread because the model must not run twice at once. */
object EngineHolder {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "pw-tts") }.asCoroutineDispatcher()

    @Volatile private var engine: SherpaEngine? = null

    /**
     * CPU threads used for speech. Fewer threads on the efficient share of the cores keep the phone cooler: the audio
     * is made well ahead of playback anyway (see Playback), so speed is not lost, only the heat spike. Half of the
     * cores, at least 2 and at most 3.
     */
    @Volatile var threads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 3)

    /** Set by Playback when the phone reports it is getting hot; the engine then runs on the minimum of 2 threads. */
    @Volatile var hot: Boolean = false

    private fun wanted(): Int = if (hot) 2 else threads

    @Volatile private var builtAt = 0L

    private fun ensure(ctx: Context): SherpaEngine {
        val current = engine
        if (current != null) {
            val cooling = !hot && current.threads < threads && System.currentTimeMillis() - builtAt > 3 * 60_000L
            val overheating = hot && current.threads > 2
            if (!cooling && !overheating) return current
            // A different thread count is only applied here, between two sections, on the speech thread. Reloading the
            // model costs a few seconds, so going back up waits at least 3 minutes after the last reload.
            current.release()
            engine = null
        }
        check(ModelStore.isReady(ctx)) { "MODEL_MISSING" }
        return SherpaEngine(ctx.applicationContext, wanted()).also { engine = it; builtAt = System.currentTimeMillis() }
    }

    suspend fun get(ctx: Context): SherpaEngine = withContext(dispatcher) { ensure(ctx) }

    suspend fun generate(ctx: Context, text: String, sid: Int, speed: Float): FloatArray =
        withContext(dispatcher) { ensure(ctx).generate(text, sid, speed) }

    fun release() {
        engine?.release()
        engine = null
    }
}
