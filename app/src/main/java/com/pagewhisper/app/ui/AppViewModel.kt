package com.pagewhisper.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pagewhisper.app.data.BackupCore
import com.pagewhisper.app.data.BookChapter
import com.pagewhisper.app.data.BookMeta
import com.pagewhisper.app.data.Books
import com.pagewhisper.app.data.BackupException
import com.pagewhisper.app.data.BackupIO
import com.pagewhisper.app.data.CleanBackup
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.AppSettings
import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.model.PronRule
import com.pagewhisper.app.model.PronSet
import com.pagewhisper.app.model.SavedChapter
import com.pagewhisper.app.model.Section
import com.pagewhisper.app.net.Fetcher
import com.pagewhisper.app.net.TocFetcher
import com.pagewhisper.app.playback.Playback
import com.pagewhisper.app.text.ChapterItem
import com.pagewhisper.app.text.ChapterList
import com.pagewhisper.app.text.EpubReader
import com.pagewhisper.app.text.Extractor
import com.pagewhisper.app.text.PdfReader
import com.pagewhisper.app.text.PronEngine
import com.pagewhisper.app.text.TextTools
import com.pagewhisper.app.tts.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab { READ, OPEN, LIBRARY, SETTINGS }

sealed interface Sheet {
    data object Reading : Sheet
    data object Sleep : Sheet
    data object Bright : Sheet
    data class Pron(val novelScope: Boolean) : Sheet
    data class Cover(val id: String) : Sheet
    data class Toc(val id: String) : Sheet
    data object Restore : Sheet
}

data class ChapterState(val title: String, val url: String, val text: String, val next: String)
data class Status(val text: String = "", val error: Boolean = false, val busy: Boolean = false)
data class ModelUi(val ready: Boolean, val downloading: Boolean = false, val progress: Float = 0f, val error: String = "")
data class RestoreUi(val backup: CleanBackup, val summary: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val tab = MutableStateFlow(Tab.READ)
    val sheet = MutableStateFlow<Sheet?>(null)
    val fullscreen = MutableStateFlow(false)
    val locked = MutableStateFlow(false)

    val chapter = MutableStateFlow<ChapterState?>(null)
    val sections = MutableStateFlow<List<Section>>(emptyList())
    val nextUrl = MutableStateFlow("")
    val fetchUi = MutableStateFlow(Status())
    val bookUi = MutableStateFlow(Status())
    val libraryStatus = MutableStateFlow(Status())
    val coverStatus = MutableStateFlow(Status())
    val tocStatus = MutableStateFlow(Status())
    val pronStatus = MutableStateFlow(Status())
    val backupStatus = MutableStateFlow(Status())
    val model = MutableStateFlow(ModelUi(ModelStore.isReady(app)))
    val restore = MutableStateFlow<RestoreUi?>(null)
    /** The address open in the in-app browser (used for sites that block automatic fetching), or null. */
    val browser = MutableStateFlow<String?>(null)

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private var pronEngine = PronEngine(Repo.pron.value)
    private val history = ArrayList<ChapterState>()
    private var pos = -1
    private var lastSaved = 0L
    private var lastSavedIndex = -1

    init {
        Playback.onChapterFinished = {
            viewModelScope.launch {
                if (!goNext(true)) { Playback.advancing = false; Playback.syncAmbient() }
            }
        }
        Playback.onSkipPastEnd = { viewModelScope.launch { goNext(true) } }
        viewModelScope.launch { Playback.events.collect { _toasts.emit(it) } }
        viewModelScope.launch { Repo.pron.collect { pronEngine = PronEngine(it); refreshSpeech() } }
        viewModelScope.launch(Dispatchers.IO) { ModelStore.removeLegacy(ctx) }
        viewModelScope.launch { Repo.settings.collect { Playback.syncAmbient() } }
        viewModelScope.launch {
            Playback.ui.collect { u -> // remember where you are
                val ch = chapter.value ?: return@collect
                val now = System.currentTimeMillis()
                if (u.index != lastSavedIndex && (now - lastSaved > 3000 || !u.playing || u.paused)) {
                    lastSaved = now
                    lastSavedIndex = u.index
                    Repo.saveLast(Repo.LastChapter(ch.title, ch.text, ch.url, ch.next, u.index))
                }
            }
        }
        Repo.loadLast()?.let { l ->
            loadChapter(l.title, l.text, l.url, l.next, silent = true)
            Playback.setIndex(l.index)
        }
    }

    fun toast(msg: String) { _toasts.tryEmit(msg) }
    private fun MutableStateFlow<Status>.set(text: String, error: Boolean = false, busy: Boolean = false) { value = Status(text, error, busy) }

    // ================= window state =================

    fun enterFullscreen() { fullscreen.value = true; tab.value = Tab.READ; sheet.value = null }
    fun exitFullscreen() { if (locked.value) return; fullscreen.value = false }
    fun lock() { if (fullscreen.value) { locked.value = true; sheet.value = null } }
    fun unlock() { locked.value = false }
    fun openSheet(s: Sheet) { if (!locked.value) sheet.value = s }
    fun closeSheet() { sheet.value = null }

    // ================= chapters =================

    private fun novelKeyOf(url: String) = if (url.isEmpty()) "" else Extractor.novelKey(url)

    /** The title to speak before the body: never the novel's own name, which says nothing about the chapter. */
    private fun spokenTitle(ch: ChapterState): String {
        val novel = Repo.novelFor(ch.url)?.title?.trim().orEmpty()
        return if (novel.isNotEmpty() && ch.title.trim().equals(novel, ignoreCase = true) && !Extractor.looksLikeChapter(ch.title)) "" else ch.title
    }

    private fun rebuild(ch: ChapterState) {
        val secs = TextTools.buildSections(ch.text, Repo.settings.value.chunk, pronEngine, novelKeyOf(ch.url), spokenTitle(ch))
        sections.value = secs
        Playback.setChapter(ch.title, Repo.novelFor(ch.url)?.title ?: "", secs)
    }

    /** A pronunciation change takes effect on sections that are not prepared yet. */
    private fun refreshSpeech() {
        val ch = chapter.value ?: return
        val secs = TextTools.buildSections(ch.text, Repo.settings.value.chunk, pronEngine, novelKeyOf(ch.url), spokenTitle(ch))
        sections.value = secs
        Playback.updateSections(secs)
    }

    fun rebuildForChunkChange() { chapter.value?.let { rebuild(it) } }

    fun loadChapter(title: String, text: String, url: String = "", next: String? = null, push: Boolean = true, silent: Boolean = false) {
        val clean = TextTools.clean(text)
        if (clean.isEmpty()) { fetchUi.set("No readable chapter text was found.", error = true); return }
        val nx = next ?: nextUrl.value
        val ch = ChapterState(title.trim().ifEmpty { "Untitled chapter" }, url, clean, nx)
        chapter.value = ch
        nextUrl.value = nx
        if (push) {
            while (history.size > pos + 1) history.removeAt(history.lastIndex)
            history.add(ch)
            if (history.size > 30) history.removeAt(0)
            pos = history.lastIndex
        }
        rebuild(ch)
        Repo.trackNovel(ch.title, url)?.let { maybeCover(it) }
        if (!silent) tab.value = Tab.READ
        Repo.saveLast(Repo.LastChapter(ch.title, ch.text, ch.url, ch.next, 0))
    }

    fun loadPasted(text: String, titleField: String, urlField: String, note: String = "") {
        val clip = TextTools.clean(text)
        if (clip.length < 50) { fetchUi.set("The text is empty or too short. Copy the chapter text first.", error = true); return }
        val url = urlField.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: ""
        val first = clip.lineSequence().firstOrNull().orEmpty()
        val title = titleField.trim().ifEmpty { if (first.length <= 80) first else "Pasted chapter" }
        loadChapter(title, clip, url, if (url.isNotEmpty()) tocNeighbor(url, 1).ifEmpty { Extractor.guessNext(url) } else "")
        if (note.isNotEmpty()) fetchUi.set(note)
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty() }
                val name = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".txt").orEmpty()
                loadChapter(name.ifEmpty { "Imported text" }, text)
            } catch (e: Throwable) { fetchUi.set("Could not read that file: ${e.message}", error = true) }
        }
    }

    /** "Share to PageWhisper": a chapter link, or selected chapter text. */
    fun handleShare(shared: String) {
        val link = Regex("https?://\\S+").find(shared)?.value
        val body = shared.replace(Regex("https?://\\S+"), "").trim()
        if (body.length >= 300) {
            val first = body.lineSequence().first().trim()
            loadChapter(if (first.length <= 80) first else "Shared chapter", body, link ?: "", if (link != null) Extractor.guessNext(link) else "")
        } else if (link != null) fetchChapter(link)
    }

    suspend fun fetchChapterAwait(url: String, autoplay: Boolean = false): Boolean {
        val u = url.trim()
        if (Books.isBookUrl(u)) return openBookChapter(u, autoplay)
        if (!(u.startsWith("http://") || u.startsWith("https://"))) { fetchUi.set("Enter a valid link starting with https://", error = true); return false }
        fetchUi.set("Fetching…", busy = true)
        val r = Fetcher.chapter(u)
        val ex = r.getOrNull()
        if (ex == null) {
            val msg = "This site blocks automatic fetching (bot protection or login). Use “Read in browser” on the Open tab, or paste the chapter text. Details: ${r.exceptionOrNull()?.message}"
            fetchUi.set(msg, error = true); toast("Could not fetch that chapter.")
            return false
        }
        val nx = tocNeighbor(u, 1).ifEmpty { ex.next }.ifEmpty { Extractor.guessNext(u) } // the chapter list beats a guess
        loadChapter(ex.title, ex.body, u, nx)
        fetchUi.set("Chapter fetched.")
        if (autoplay) Playback.play(0)
        return true
    }

    fun fetchChapter(url: String, autoplay: Boolean = false) { viewModelScope.launch { fetchChapterAwait(url, autoplay) } }

    // ---------- imported books (EPUB and PDF) ----------

    private suspend fun openBookChapter(url: String, autoplay: Boolean): Boolean {
        val data = withContext(Dispatchers.IO) { Books.chapter(url) }
        if (data == null) {
            fetchUi.set("This book's file is missing. Import it again.", error = true)
            toast("Could not open that chapter.")
            return false
        }
        loadChapter(data.title, data.text, url, tocNeighbor(url, 1))
        fetchUi.set("")
        if (autoplay) Playback.play(0)
        return true
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
        return fromProvider ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "")
    }

    /** Copies the picked file to a temporary file and tells what it is: "pdf" or "epub". */
    private fun copyForImport(uri: Uri, tmp: File): String {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open that file.")
        input.use { ins ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total > 400_000_000L) throw IllegalStateException("That file is too large (over 400 MB).")
                }
            }
        }
        val head = ByteArray(5)
        val got = tmp.inputStream().use { it.read(head) }
        val pdf = got >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()
        val zip = got >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        return if (pdf) "pdf" else if (zip) "epub" else throw IllegalStateException("That file is not an EPUB or a PDF.")
    }

    /** Imports an EPUB or PDF into the library as a book, then opens its first chapter. */
    fun importBook(uri: Uri) {
        if (bookUi.value.busy) return
        viewModelScope.launch {
            bookUi.set("Opening the file…", busy = true)
            val tmp = File(ctx.cacheDir, "import-" + Repo.newId() + ".tmp")
            try {
                val name = withContext(Dispatchers.IO) { displayName(uri) }
                val kind = withContext(Dispatchers.IO) { copyForImport(uri, tmp) }
                val base = name.substringBeforeLast('.').replace(Regex("[_]+"), " ").trim().ifEmpty { "Book" }
                bookUi.set("Reading the book…", busy = true)
                val book = withContext(Dispatchers.IO) {
                    if (kind == "pdf") PdfReader.read(ctx, tmp, base) { done, total -> bookUi.set("Reading page $done of $total…", busy = true) }
                    else EpubReader.read(tmp, base)
                }
                val id = Repo.newId()
                withContext(Dispatchers.IO) {
                    Books.save(BookMeta(id, book.title, book.author, kind, book.chapters.map { BookChapter(it.title, "") }), book.chapters.map { it.text })
                }
                val first = Books.url(id, 0)
                val novel = Repo.addNovel(book.title, first, book.chapters.first().title)
                val items = book.chapters.mapIndexed { i, c -> ChapterItem((i + 1).toDouble(), c.title, Books.url(id, i)) }
                Repo.saveToc(novel.id, "book://$id", items, "book", 1)
                book.cover?.let { raw -> runCatching { Repo.saveCover(novel.id, Fetcher.toCover(raw).jpeg) } }
                libraryStatus.set("Added “${book.title}” to your library.")
                fetchChapterAwait(first)
                val msg = "Imported “${book.title}”: ${items.size} chapter${if (items.size == 1) "" else "s"}."
                bookUi.set(msg)
                toast(msg)
            } catch (e: Throwable) {
                bookUi.set(if (e is IllegalStateException) e.message.orEmpty() else "Could not read that file: ${e.message}", error = true)
            } finally {
                tmp.delete()
            }
        }
    }

    // ---------- next / previous ----------

    fun tocNeighbor(url: String, delta: Int): String {
        if (url.isEmpty()) return ""
        val n = Repo.novelFor(url) ?: return ""
        val items = Repo.tocItems(n.id)
        val i = ChapterList.indexOf(items, url)
        return if (i >= 0 && i + delta in items.indices) items[i + delta].url else ""
    }

    fun hasPrev(): Boolean = pos > 0 || tocNeighbor(chapter.value?.url.orEmpty(), -1).isNotEmpty()

    private fun openHistory(p: Int, autoplay: Boolean) {
        val h = history.getOrNull(p) ?: return
        pos = p
        loadChapter(h.title, h.text, h.url, h.next, push = false)
        if (autoplay) Playback.play(0)
    }

    suspend fun goNext(autoplay: Boolean): Boolean {
        if (pos < history.size - 1) { openHistory(pos + 1, autoplay); return true }
        val u = tocNeighbor(chapter.value?.url.orEmpty(), 1).ifEmpty { nextUrl.value.trim() }
        if (u.isEmpty()) { toast("Paste the next chapter link first."); return false }
        return fetchChapterAwait(u, autoplay)
    }

    fun next() { viewModelScope.launch { goNext(Playback.ui.value.playing) } }

    fun prev() {
        val playing = Playback.ui.value.playing
        val t = tocNeighbor(chapter.value?.url.orEmpty(), -1)
        if (t.isNotEmpty()) { fetchChapter(t, playing); return }
        if (pos > 0) openHistory(pos - 1, playing) else toast("This is the first chapter in your history.")
    }

    // ================= library =================

    fun addNovel(title: String, url: String): Boolean {
        val u = url.trim()
        if (!(u.startsWith("http://") || u.startsWith("https://")) || Extractor.hostOf(u).isEmpty()) {
            libraryStatus.set("Enter a valid link starting with https://", error = true); return false
        }
        val existed = Repo.novelFor(u) != null
        val n = Repo.addNovel(title, u)
        libraryStatus.set(if (existed) "Updated this novel in your library." else "Added to your library.")
        if (!existed) fetchCover(n.id, silent = true)
        return true
    }

    fun addCurrentNovel() {
        val ch = chapter.value ?: return
        if (ch.url.isEmpty()) return
        if (Books.isBookUrl(ch.url)) { libraryStatus.set("Imported books are already in your library."); return }
        val name = ch.title.replace(Regex("\\s*[-–—:|]?\\s*(?:chapter|ch\\.?|episode|ep\\.?)\\s*\\d+.*$", RegexOption.IGNORE_CASE), "").trim()
        val existed = Repo.novelFor(ch.url) != null
        val n = Repo.addNovel(name, ch.url, ch.title)
        libraryStatus.set(if (existed) "Updated this novel in your library." else "Added to your library.")
        if (!existed) fetchCover(n.id, silent = true)
    }

    fun continueNovel(n: Novel) { fetchChapter(n.url) }

    // ---------- covers (Library list only) ----------

    private fun maybeCover(n: Novel) {
        if (Books.isBookUrl(n.url)) return
        if (Repo.hasCover(n.id)) return
        if (n.coverTry != 0L && System.currentTimeMillis() - n.coverTry < 7L * 24 * 3600 * 1000) return
        fetchCover(n.id, silent = true)
    }

    fun fetchCover(id: String, silent: Boolean = false) {
        viewModelScope.launch {
            val n = Repo.novels.value.firstOrNull { it.id == id } ?: return@launch
            if (Books.isBookUrl(n.url)) return@launch
            if (!silent) coverStatus.set("Looking for a cover…", busy = true)
            val c = try { Fetcher.findCover(n.url) } catch (_: Throwable) { null }
            if (c != null) {
                Repo.saveCover(id, c.jpeg)
                if (!silent) coverStatus.set("Cover found.")
            } else {
                Repo.markCoverTry(id)
                if (!silent) coverStatus.set("No cover found automatically. Choose an image from your device instead.", error = true)
            }
        }
    }

    fun findMissingCovers() {
        viewModelScope.launch {
            val missing = Repo.novels.value.filter { !Repo.hasCover(it.id) && !Books.isBookUrl(it.url) }
            if (missing.isEmpty()) { libraryStatus.set("Every novel already has a cover."); return@launch }
            var ok = 0
            missing.forEachIndexed { i, n ->
                libraryStatus.set("Looking for covers… ${i + 1} of ${missing.size}", busy = true)
                val c = try { Fetcher.findCover(n.url) } catch (_: Throwable) { null }
                if (c != null) { Repo.saveCover(n.id, c.jpeg); ok++ } else Repo.markCoverTry(n.id)
            }
            libraryStatus.set("Found $ok of ${missing.size}." + if (ok < missing.size) " Tap a cover to choose one yourself." else "", error = ok == 0)
        }
    }

    fun setCoverFromUri(id: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0) }
                Repo.saveCover(id, Fetcher.toCover(raw).jpeg)
                coverStatus.set("Cover saved.")
            } catch (e: Throwable) { coverStatus.set("Could not use that image: ${e.message}", error = true) }
        }
    }

    fun setCoverFromLink(id: String, link: String) {
        viewModelScope.launch {
            coverStatus.set("Fetching the image…", busy = true)
            try {
                Repo.saveCover(id, Fetcher.toCover(Fetcher.bytes(link.trim())).jpeg)
                coverStatus.set("Cover saved.")
            } catch (e: Throwable) { coverStatus.set("Could not use that image: ${e.message}", error = true) }
        }
    }

    fun removeCover(id: String) { Repo.removeCover(id); coverStatus.set("Cover removed.") }

    // ---------- chapter lists ----------

    fun updateToc(id: String) {
        viewModelScope.launch {
            val n = Repo.novels.value.firstOrNull { it.id == id } ?: return@launch
            if (Books.isBookUrl(n.url)) { tocStatus.set("An imported book has a fixed chapter list."); return@launch }
            tocStatus.set("Looking for the chapter list…", busy = true)
            val before = Repo.tocCount(id)
            try {
                val r = TocFetcher.fetch(n) { tocStatus.set(it, busy = true) }
                if (r.items.isEmpty()) {
                    tocStatus.set("Could not find a chapter list on this site. It may block fetching or load chapters with scripts. Use Build by number instead.", error = true)
                } else {
                    Repo.saveToc(id, Extractor.novelPageUrl(n.url), r.items, "fetched", r.pages)
                    val added = r.items.size - before
                    tocStatus.set("Found ${r.items.size} chapters" + (if (before > 0) " (${if (added > 0) "$added new" else "no new chapters"})" else "") + ".")
                }
            } catch (e: Throwable) { tocStatus.set("Could not read the list: ${e.message}", error = true) }
        }
    }

    fun buildToc(id: String, count: Int) {
        val n = Repo.novels.value.firstOrNull { it.id == id } ?: return
        if (Books.isBookUrl(n.url)) { tocStatus.set("An imported book has a fixed chapter list."); return }
        val items = TocFetcher.build(n, count)
        if (items == null) { tocStatus.set("This novel's link has no chapter number to build from.", error = true); return }
        Repo.saveToc(id, Extractor.novelPageUrl(n.url), items, "built", 0)
        tocStatus.set("Built a list of ${items.size} chapters.")
    }

    fun updateAllTocs() {
        viewModelScope.launch {
            val list = Repo.novels.value.filter { !Books.isBookUrl(it.url) }
            if (list.isEmpty()) { libraryStatus.set("Add a novel by link first.", error = true); return@launch }
            var ok = 0
            list.forEachIndexed { i, n ->
                libraryStatus.set("Updating chapter lists… ${i + 1} of ${list.size} (${n.title})", busy = true)
                try {
                    val r = TocFetcher.fetch(n) {}
                    if (r.items.isNotEmpty()) { Repo.saveToc(n.id, Extractor.novelPageUrl(n.url), r.items, "fetched", r.pages); ok++ }
                } catch (_: Throwable) {}
            }
            libraryStatus.set("Updated $ok of ${list.size} chapter lists." + if (ok < list.size) " Open a novel's Chapters to build one by number." else "", error = ok == 0)
        }
    }

    fun openChapterFromToc(url: String) { sheet.value = null; fetchChapter(url) }

    // ================= pronunciation =================

    fun currentNovelKey(): String = novelKeyOf(chapter.value?.url.orEmpty())

    fun addRule(novelScope: Boolean, rule: PronRule): Boolean {
        val key = if (novelScope) currentNovelKey() else ""
        if (novelScope && key.isEmpty()) { pronStatus.set("Load a chapter from a link to make a rule for one novel.", error = true); return false }
        val p = Repo.pron.value
        fun put(list: List<PronRule>): List<PronRule> {
            val i = list.indexOfFirst { com.pagewhisper.app.text.Pron.ruleId(it) == com.pagewhisper.app.text.Pron.ruleId(rule) }
            return if (i >= 0) list.toMutableList().also { it[i] = rule } else listOf(rule) + list
        }
        Repo.setPron(if (novelScope) p.copy(novels = p.novels + (key to put(p.novels[key].orEmpty()))) else p.copy(global = put(p.global)))
        pronStatus.set("Rule added.")
        return true
    }

    fun deleteRule(novelScope: Boolean, rule: PronRule) {
        val p = Repo.pron.value
        val key = if (novelScope) currentNovelKey() else ""
        Repo.setPron(
            if (novelScope) p.copy(novels = p.novels + (key to p.novels[key].orEmpty().filter { it != rule }))
            else p.copy(global = p.global.filter { it != rule }),
        )
    }

    fun previewSpeech(text: String) {
        if (text.isBlank()) { pronStatus.set("Type how it should sound first.", error = true); return }
        viewModelScope.launch {
            pronStatus.set("Preparing the voice… the first time can take a while.", busy = true)
            try { Playback.preview(text); pronStatus.set("") }
            catch (e: Throwable) { pronStatus.set(if (e.message == "MODEL_MISSING") "The voice is not installed yet. Download it in Settings." else "Could not play it: ${e.message}", error = true) }
        }
    }

    // ================= settings =================

    fun update(f: (AppSettings) -> AppSettings) = Repo.updateSettings(f)

    fun startSleep(minutes: Int) {
        val m = Playback.startSleep(minutes)
        Repo.updateSettings { it.copy(sleepLast = m) }
        toast("Sleep timer: $m min.")
    }

    // ================= backup =================

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = BackupIO.exportJson()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                backupStatus.set("Backup saved (${json.length / 1024} KB).")
            } catch (e: Throwable) { backupStatus.set("Could not make the backup: ${e.message}", error = true) }
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { s ->
                        val bytes = s.readBytes()
                        if (bytes.size > BackupCore.MAX_BYTES) throw BackupException("That file is too large to be a backup.")
                        bytes.toString(Charsets.UTF_8)
                    }.orEmpty()
                }
                val b = BackupCore.validate(text)
                val summary = "${b.from}${if (b.date.isNotEmpty()) ", saved ${b.date}" else ""}\n" +
                    "${b.novels.size} novel${if (b.novels.size == 1) "" else "s"} (${b.coverCount} with covers) · ${b.chapters.size} saved chapter${if (b.chapters.size == 1) "" else "s"} · ${b.pron.totalRules} pronunciation rule${if (b.pron.totalRules == 1) "" else "s"}"
                restore.value = RestoreUi(b, summary)
                sheet.value = Sheet.Restore
            } catch (e: BackupException) { backupStatus.set(e.message ?: "Not a valid backup.", error = true) }
            catch (e: Throwable) { backupStatus.set("Could not read that file: ${e.message}", error = true) }
        }
    }

    fun applyRestore(merge: Boolean) {
        val r = restore.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (merge) BackupIO.applyMerge(r.backup) else BackupIO.applyReplace(r.backup)
            restore.value = null
            backupStatus.set(if (merge) "Backup merged into your library." else "Backup restored.")
            sheet.value = null
        }
    }

    // ================= voice model =================

    fun downloadModel() {
        if (model.value.downloading) return
        viewModelScope.launch {
            model.value = ModelUi(ready = false, downloading = true)
            try {
                ModelStore.download(ctx, ModelStore.STANDARD) { done, total ->
                    model.value = ModelUi(ready = false, downloading = true, progress = if (total > 0) done.toFloat() / total else 0f)
                }
                model.value = ModelUi(ready = ModelStore.isReady(ctx))
            } catch (e: Throwable) {
                model.value = ModelUi(ready = ModelStore.isReady(ctx), error = e.message ?: "Download failed")
            }
        }
    }

    // ================= saved chapters =================

    fun saveCurrentChapter() {
        val ch = chapter.value ?: return
        Repo.saveChapter(SavedChapter(ch.title, ch.text, ch.url, ch.next))
        toast("Chapter saved on this device.")
    }

    fun openSaved(c: SavedChapter) { loadChapter(c.title, c.text, c.url, c.next) }
}
