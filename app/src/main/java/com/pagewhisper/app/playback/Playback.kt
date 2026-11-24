package com.pagewhisper.app.playback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.Section
import com.pagewhisper.app.text.TextTools
import com.pagewhisper.app.tts.EngineHolder
import com.pagewhisper.app.tts.ModelStore
import com.pagewhisper.app.tts.Voices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class SleepUi(
    val mode: String = "off", // off | time | chapter
    val endAt: Long = 0L,
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,
)

data class PlayerUi(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val status: String = "Load a chapter, then press play.",
    val error: Boolean = false,
    val sleep: SleepUi = SleepUi(),
    val title: String = "",
    val subtitle: String = "",
)

/**
 * The playback engine. It turns sections of text into speech ahead of time, streams the audio to the speaker,
 * follows the speaker position to highlight the right sentence, and runs the sleep timer and ambient sound.
 * A foreground service (PlaybackService) keeps it alive with the screen off.
 */
object Playback {
    // Speech is generated only when little audio is left in front of the speaker (same idea as the web app):
    // it keeps the phone cooler and saves battery. With the screen off a bigger reserve is kept, because the
    // system may slow the app down. Seconds of audio.
    private const val LOW_VISIBLE = 15.0
    private const val LOW_HIDDEN = 35.0

    /** True while one of the app's screens is on display; set by PageWhisperApp. */
    @Volatile var appVisible = true
    private class Clip(val index: Int, val pcm: FloatArray)

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    /** Short messages for the screen (toasts). */
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile var sections: List<Section> = emptyList()
        private set
    var onChapterFinished: (() -> Unit)? = null
    var onSkipPastEnd: (() -> Unit)? = null

    /** True while the next chapter is being loaded, so the ambient sound and the service keep going. */
    @Volatile var advancing = false

    @Volatile private var sleeping = false
    @Volatile private var fade = 1f
    @Volatile private var previewUntil = 0L

    private var job: Job? = null
    private var track: AudioTrack? = null
    private var runId = 0
    private var wake: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var resumeOnGain = false
    private var sleepJob: Job? = null

    fun init(application: Application) { app = application }

    // ================= chapter =================

    fun setChapter(title: String, subtitle: String, list: List<Section>) {
        stop()
        sections = list
        _ui.update { it.copy(index = 0, total = list.size, title = title, subtitle = subtitle, status = "${list.size} sections ready. Press play.", error = false) }
    }

    /** Replaces the sections (for example after a pronunciation change) without moving the position. */
    fun updateSections(list: List<Section>) {
        sections = list
        _ui.update { it.copy(total = list.size) }
    }

    /** Puts the position on a section without playing (for example when a saved chapter is reopened). */
    fun setIndex(i: Int) {
        _ui.update { it.copy(index = i.coerceIn(0, max(0, it.total - 1))) }
    }

    fun setStatus(text: String, error: Boolean = false) = _ui.update { it.copy(status = text, error = error) }

    // ================= transport =================

    fun play(from: Int = _ui.value.index) {
        val list = sections
        if (list.isEmpty()) return
        val start = from.coerceIn(0, list.lastIndex)
        cancelJob()
        val run = ++runId
        advancing = false; sleeping = false; resumeOnGain = false
        setFade(1f)
        _ui.update { it.copy(playing = true, paused = false, index = start, total = list.size, status = "Preparing the voice…", error = false) }
        acquireWake()
        startService()
        syncAmbient()
        job = scope.launch {
            try {
                runPlayback(start, run)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (run == runId) fail(e)
            }
        }
    }

    fun pause() {
        val u = _ui.value
        if (!u.playing || u.paused) return
        runCatching { track?.pause() }
        _ui.update { it.copy(paused = true, status = "Paused.") }
        syncAmbient()
    }

    fun resume() {
        val u = _ui.value
        if (!u.playing || !u.paused) return
        sleeping = false
        setFade(1f)
        requestFocus()
        runCatching { track?.play() }
        _ui.update { it.copy(paused = false, status = "Playing.") }
        syncAmbient()
    }

    fun toggle() {
        val u = _ui.value
        when {
            !u.playing -> play(u.index)
            u.paused -> resume()
            else -> pause()
        }
    }

    fun resumeOrPlay() {
        val u = _ui.value
        if (!u.playing) play(u.index) else if (u.paused) resume()
    }

    fun stop() {
        runId++
        advancing = false
        cancelJob()
        abandonFocus()
        releaseWake()
        _ui.update { it.copy(playing = false, paused = false, index = 0) }
        syncAmbient()
    }

    fun skip(delta: Int) {
        val list = sections
        if (list.isEmpty()) return
        val u = _ui.value
        if (delta > 0 && u.index >= list.lastIndex && u.playing) { onSkipPastEnd?.invoke(); return }
        play((u.index + delta).coerceIn(0, list.lastIndex))
    }

    // ================= the loop =================

    private fun newTrack(sampleRate: Int, bufferBytes: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

    private suspend fun runPlayback(start: Int, run: Int) = coroutineScope {
        if (!ModelStore.isReady(app)) throw IllegalStateException("MODEL_MISSING")
        val engine = EngineHolder.get(app)
        if (run != runId) return@coroutineScope
        val sr = engine.sampleRate
        requestFocus()
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = newTrack(sr, max(minBuf, sr * 4)) // about 1 second of audio
        if (run != runId) { t.release(); return@coroutineScope }
        track = t
        t.setVolume(fade)
        if (!_ui.value.paused) t.play()
        _ui.update { it.copy(status = "Playing.") }

        val boundaries = ArrayList<LongArray>() // [first frame, section index]
        val written = java.util.concurrent.atomic.AtomicLong(0L) // frames handed to the speaker
        val queued = java.util.concurrent.atomic.AtomicLong(0L)  // frames generated but not handed over yet
        val clips = Channel<Clip>(Channel.UNLIMITED)
        fun bufferedSeconds(): Double {
            val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            return (max(0L, written.get() - head) + queued.get()) / sr.toDouble()
        }

        // Generates speech ahead of what is being played.
        val producer = launch(Dispatchers.Default) {
            try {
                for (i in start until sections.size) {
                    if (!isActive) break
                    // The first section starts at once; after that, wait until the audio in front of the speaker runs low.
                    if (i > start) {
                        while (isActive && run == runId && (_ui.value.paused || bufferedSeconds() >= (if (appVisible) LOW_VISIBLE else LOW_HIDDEN))) delay(250)
                        if (!isActive || run != runId) break
                    }
                    val sec = sections.getOrNull(i) ?: break
                    val settings = Repo.settings.value
                    val pcm = if (sec.speech.isBlank()) FloatArray(0)
                    else EngineHolder.generate(app, sec.speech, Voices.sidOf(settings.voice), settings.rate.toFloat())
                    queued.addAndGet(pcm.size.toLong())
                    clips.send(Clip(i, pcm))
                }
            } finally {
                clips.close()
            }
        }

        // Follows the speaker position so the highlighted sentence matches what is heard.
        val ticker = launch(Dispatchers.Default) {
            while (isActive) {
                val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                var idx = -1
                synchronized(boundaries) {
                    for (b in boundaries) { if (b[0] <= head) idx = b[1].toInt() else break }
                }
                if (idx >= 0 && idx != _ui.value.index && run == runId) _ui.update { it.copy(index = idx) }
                delay(80)
            }
        }

        withContext(Dispatchers.IO) {
            for (clip in clips) {
                if (!isActive || run != runId) break
                synchronized(boundaries) { boundaries.add(longArrayOf(written.get(), clip.index.toLong())) }
                var off = 0
                while (off < clip.pcm.size) {
                    val n = t.write(clip.pcm, off, clip.pcm.size - off, AudioTrack.WRITE_BLOCKING)
                    if (n < 0) throw IllegalStateException("audio write error $n")
                    off += n
                    written.addAndGet(n.toLong()); queued.addAndGet(-n.toLong())
                }
            }
        }
        // let the last audio finish
        var waited = 0
        while (isActive && run == runId && t.playbackHeadPosition.toLong() < written.get() && waited < 30_000) { delay(100); waited += 100 }
        ticker.cancel(); producer.cancel()
        finish(run)
    }

    private fun finish(run: Int) {
        if (run != runId) return
        releaseTrack(); abandonFocus(); releaseWake()
        val sleepEnd = _ui.value.sleep.mode == "chapter"
        advancing = !sleepEnd && Repo.settings.value.auto
        _ui.update { it.copy(playing = false, paused = false, index = 0, status = "Chapter finished.", error = false) }
        if (sleepEnd) sleepChapterDone() else if (advancing) onChapterFinished?.invoke()
        syncAmbient()
    }

    private fun fail(e: Throwable) {
        releaseTrack(); abandonFocus(); releaseWake()
        advancing = false
        val msg = if (e.message == "MODEL_MISSING") "The voice is not installed yet. Open Settings and download the voice model."
        else "Speech error: ${e.message ?: e.javaClass.simpleName}"
        _ui.update { it.copy(playing = false, paused = false, status = msg, error = true) }
        events.tryEmit(msg)
        syncAmbient()
    }

    private fun cancelJob() {
        job?.cancel(); job = null
        releaseTrack()
    }

    private fun releaseTrack() {
        val t = track
        track = null
        if (t != null) runCatching { t.pause(); t.flush(); t.release() }
    }

    // ================= pronunciation preview =================

    /** Speaks a short text with the current voice, separately from the chapter. */
    suspend fun preview(text: String) {
        val spoken = TextTools.speechText(text)
        if (spoken.isEmpty()) return
        pause()
        val st = Repo.settings.value
        val sr = EngineHolder.get(app).sampleRate
        val pcm = EngineHolder.generate(app, spoken, Voices.sidOf(st.voice), st.rate.toFloat())
        if (pcm.isEmpty()) return
        withContext(Dispatchers.IO) {
            val t = newTrack(sr, pcm.size * 4 + 4096)
            try {
                t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                t.play()
                delay(pcm.size * 1000L / sr + 300)
            } finally { t.release() }
        }
    }

    // ================= audio focus, wake lock, service =================

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (_ui.value.playing && !_ui.value.paused) { resumeOnGain = true; pause() } }
            AudioManager.AUDIOFOCUS_GAIN -> { if (resumeOnGain) { resumeOnGain = false; resume() } }
        }
    }

    private fun requestFocus() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener, main)
            .build().also { focus = it }
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        val req = focus ?: return
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(req)
        focus = null
    }

    private fun acquireWake() {
        if (wake?.isHeld == true) return
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PageWhisper:playback").apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun releaseWake() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }

    private fun startService() {
        ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
    }

    // ================= volume fade (sleep timer) =================

    private fun setFade(f: Float) {
        fade = f
        track?.setVolume(f)
        syncAmbient()
    }

    // ================= ambient sound =================

    fun syncAmbient() {
        val st = Repo.settings.value
        val u = _ui.value
        val narrating = (u.playing && !u.paused) || advancing
        val want = st.amb != "off" && !sleeping && (!st.ambOnly || narrating || System.currentTimeMillis() < previewUntil)
        Ambient.sync(scope, st.amb, (st.ambVol * fade).toFloat(), want) { syncAmbient() }
    }

    /** Plays the chosen ambient sound for a few seconds so you can hear it. */
    fun previewAmbient() {
        previewUntil = System.currentTimeMillis() + 7000
        sleeping = false
        syncAmbient()
        scope.launch { delay(7100); syncAmbient() }
    }

    // ================= sleep timer =================

    fun startSleep(minutes: Int): Int {
        val m = minutes.coerceIn(1, 600)
        val total = m * 60_000L
        setFade(1f); sleeping = false
        _ui.update { it.copy(sleep = SleepUi("time", SystemClock.elapsedRealtime() + total, total, total)) }
        sleepJob?.cancel()
        sleepJob = scope.launch { while (isActive) { sleepTick(); delay(1000) } }
        return m
    }

    fun startSleepChapter() {
        setFade(1f); sleeping = false
        sleepJob?.cancel(); sleepJob = null
        _ui.update { it.copy(sleep = SleepUi("chapter")) }
    }

    fun cancelSleep() {
        sleepJob?.cancel(); sleepJob = null
        _ui.update { it.copy(sleep = SleepUi()) }
        setFade(1f)
    }

    fun addSleepMinutes(m: Int) {
        val s = _ui.value.sleep
        if (s.mode != "time") return
        _ui.update { it.copy(sleep = s.copy(endAt = s.endAt + m * 60_000L, totalMs = s.totalMs + m * 60_000L)) }
    }

    // Counts real elapsed time, so it stays right even if the app is throttled in the background.
    private fun sleepTick() {
        val s = _ui.value.sleep
        if (s.mode != "time") return
        val r = s.endAt - SystemClock.elapsedRealtime()
        if (r <= 0) { sleepExpire(); return }
        val fadeMs = min(30_000L, s.totalMs / 4)
        val u = _ui.value
        if (r < fadeMs) { if (u.playing && !u.paused) setFade(max(0.03f, r / fadeMs.toFloat())) }
        else if (fade < 1f) setFade(1f)
        _ui.update { it.copy(sleep = it.sleep.copy(remainingMs = r)) }
    }

    private fun sleepExpire() {
        sleepJob?.cancel(); sleepJob = null
        _ui.update { it.copy(sleep = SleepUi()) }
        sleeping = true
        pause() // keeps your place
        syncAmbient()
        events.tryEmit("Sleep timer ended. Playback paused where you stopped.")
    }

    private fun sleepChapterDone() {
        _ui.update { it.copy(sleep = SleepUi()) }
        sleeping = true
        syncAmbient()
        events.tryEmit("Sleep timer: chapter finished.")
    }
}
