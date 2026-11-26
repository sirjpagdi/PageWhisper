package com.pagewhisper.app.playback

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pagewhisper.app.data.AudioCache
import com.pagewhisper.app.data.Books
import com.pagewhisper.app.data.CachedChapter
import com.pagewhisper.app.data.ChapterCache
import com.pagewhisper.app.data.ChapterNav
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.Section
import com.pagewhisper.app.net.Fetcher
import com.pagewhisper.app.text.Extractor
import com.pagewhisper.app.text.PronEngine
import com.pagewhisper.app.text.TextTools
import com.pagewhisper.app.tts.EngineHolder
import com.pagewhisper.app.tts.ModelStore
import com.pagewhisper.app.tts.Voices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Prepares the next chapters in the background. See FEATURES.md ("Spec: Prepared chapters").
 *  - Text: while the app is used, the next [AHEAD] chapters of a web novel are fetched, so they open at once and read offline.
 *  - Speech: while the phone charges and nobody is listening, the speech for those chapters is made and saved, so playing
 *    them needs almost no processor power.
 *  - Clean-up: chapters more than [AHEAD] away from the open one lose their saved text and speech.
 */
object Prefetch {
    const val AHEAD = 3
    private const val WORK = "prerender"
    private const val MAX_AUDIO_BYTES = 600L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** The next chapters after [current], up to [AHEAD]: from the chapter list, else from the saved "next" links. */
    fun ahead(current: String, next: String): List<String> {
        val out = ArrayList<String>()
        var u = next.ifEmpty { ChapterNav.tocNeighbor(current, 1) }
        while (u.isNotEmpty() && out.size < AHEAD && u != current && u !in out) {
            out.add(u)
            val from = u
            u = ChapterNav.tocNeighbor(from, 1).ifEmpty { if (Books.isBookUrl(from)) "" else ChapterCache.get(from)?.next.orEmpty() }
        }
        return out
    }

    /** The chapters to keep: the open one, those ahead, and (when the chapter list knows them) the last few before it. */
    fun window(current: String, next: String): List<String> {
        if (current.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out.add(current)
        out.addAll(ahead(current, next))
        var p = ChapterNav.tocNeighbor(current, -1)
        var n = 0
        while (p.isNotEmpty() && n < AHEAD) { out.add(p); n++; p = ChapterNav.tocNeighbor(p, -1) }
        return out.toList()
    }

    /** Called when a chapter has been opened. */
    fun onChapterOpened(ctx: Context, url: String, next: String) {
        if (url.isEmpty()) return
        job?.cancel()
        val mode = Repo.settings.value.prefetch
        if (mode == "off") return
        job = scope.launch {
            fetchAhead(url, next)
            val keep = window(url, next)
            ChapterCache.keepOnly(keep)
            AudioCache.keepOnly(keep)
            AudioCache.trim(MAX_AUDIO_BYTES, url)
            if (mode == "audio") scheduleAudio(ctx) else cancelAudio(ctx)
        }
    }

    private suspend fun fetchAhead(current: String, next: String) {
        var u = next.ifEmpty { ChapterNav.tocNeighbor(current, 1) }
        var n = 0
        while (u.isNotEmpty() && n < AHEAD && currentCoroutineContext().isActive) {
            if (!Books.isBookUrl(u) && ChapterCache.get(u) == null) {
                val ex = Fetcher.chapter(u).getOrNull() ?: return // offline, blocked or no such chapter: stop quietly
                val nx = ChapterNav.tocNeighbor(u, 1).ifEmpty { ex.next }.ifEmpty { Extractor.guessNext(u) }
                ChapterCache.put(u, CachedChapter(ex.title, ex.body, nx))
                delay(1200) // be gentle with the site
            }
            n++
            val from = u
            u = ChapterNav.tocNeighbor(from, 1).ifEmpty { if (Books.isBookUrl(from)) "" else ChapterCache.get(from)?.next.orEmpty() }
        }
    }

    fun scheduleAudio(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<PrerenderWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build())
            .setInitialDelay(20, TimeUnit.SECONDS)
            .build()
        runCatching { WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request) }
    }

    fun cancelAudio(ctx: Context) {
        runCatching { WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(WORK) }
    }

    /** Throws away everything prepared. */
    fun clearAll(ctx: Context) {
        job?.cancel()
        cancelAudio(ctx)
        ChapterCache.clear()
        AudioCache.clear()
    }

    private fun isCharging(ctx: Context): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val s = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun textOf(url: String): Pair<String, String>? =
        if (Books.isBookUrl(url)) Books.chapter(url)?.let { it.title to it.text } else ChapterCache.get(url)?.let { it.title to it.body }

    /** The sections the player will make for a chapter, built the same way as when the chapter is opened. */
    private fun sectionsFor(url: String, title: String, text: String): List<Section> {
        val shown = title.trim().ifEmpty { "Untitled chapter" }
        val novel = Repo.novelFor(url)?.title?.trim().orEmpty()
        val spoken = if (novel.isNotEmpty() && shown.equals(novel, ignoreCase = true) && !Extractor.looksLikeChapter(shown)) "" else shown
        return TextTools.buildSections(TextTools.clean(text), Repo.settings.value.chunk, PronEngine(Repo.pron.value), Extractor.novelKey(url), spoken)
    }

    /**
     * Makes and saves the speech of the chapters ahead of the last opened one. Pauses while someone is listening, and
     * stops (to be tried again later) when the charger is unplugged or the phone gets hot. True when there is nothing
     * more to do now.
     */
    suspend fun renderAhead(ctx: Context): Boolean {
        val last = Repo.loadLast() ?: return true
        if (last.url.isEmpty() || Repo.settings.value.prefetch != "audio" || ModelStore.active(ctx) == null) return true
        for (u in ahead(last.url, last.next)) {
            val (title, text) = textOf(u) ?: return true
            for (sec in sectionsFor(u, title, text)) {
                if (sec.speech.isBlank()) continue
                val settings = Repo.settings.value
                val sid = Voices.sidOf(settings.voice)
                val rate = settings.rate.toFloat()
                val key = AudioCache.key(sid, rate, sec.speech)
                if (AudioCache.has(u, key)) continue
                while (true) {
                    if (!isCharging(ctx) || Playback.isHot()) return false
                    if (!Playback.isBusy()) break
                    delay(10_000) // someone is listening: leave the processor to the player
                }
                val pcm = EngineHolder.generate(ctx, sec.speech, sid, rate)
                if (pcm.isNotEmpty()) AudioCache.put(u, key, pcm)
                if (Repo.settings.value.prefetch != "audio") return true
            }
        }
        return true
    }
}

/** Runs [Prefetch.renderAhead] when the phone is charging (the system decides the moment). */
class PrerenderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        if (Prefetch.renderAhead(applicationContext)) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        Result.success()
    }
}
