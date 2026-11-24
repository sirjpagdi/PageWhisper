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
class SherpaEngine(ctx: Context, threads: Int) {
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

    /** CPU threads used for speech. */
    @Volatile var threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

    private fun ensure(ctx: Context): SherpaEngine {
        engine?.let { return it }
        check(ModelStore.isReady(ctx)) { "MODEL_MISSING" }
        return SherpaEngine(ctx.applicationContext, threads).also { engine = it }
    }

    suspend fun get(ctx: Context): SherpaEngine = withContext(dispatcher) { ensure(ctx) }

    suspend fun generate(ctx: Context, text: String, sid: Int, speed: Float): FloatArray =
        withContext(dispatcher) { ensure(ctx).generate(text, sid, speed) }

    fun release() {
        engine?.release()
        engine = null
    }
}
