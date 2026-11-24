package com.pagewhisper.app.data

import android.content.Context
import com.pagewhisper.app.model.AppSettings
import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.model.PronSet
import com.pagewhisper.app.model.ReadingStyle
import com.pagewhisper.app.model.SavedChapter
import com.pagewhisper.app.text.ChapterItem
import com.pagewhisper.app.text.Extractor
import com.pagewhisper.app.text.Pron
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable

/** Everything the user owns: novels, covers, saved chapters, pronunciation rules and settings. */
object Repo {
    private lateinit var dir: File
    private lateinit var coversDir: File
    private lateinit var novelsFile: JsonFile<List<Novel>>
    private lateinit var chaptersFile: JsonFile<List<SavedChapter>>
    private lateinit var pronFile: JsonFile<PronSet>
    private lateinit var readingFile: JsonFile<ReadingStyle>
    private lateinit var settingsFile: JsonFile<AppSettings>
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _novels = MutableStateFlow<List<Novel>>(emptyList())
    private val _chapters = MutableStateFlow<List<SavedChapter>>(emptyList())
    private val _pron = MutableStateFlow(PronSet())
    private val _reading = MutableStateFlow(ReadingStyle())
    private val _settings = MutableStateFlow(AppSettings())
    private val _coverVersion = MutableStateFlow(0)

    val novels: StateFlow<List<Novel>> = _novels.asStateFlow()
    val chapters: StateFlow<List<SavedChapter>> = _chapters.asStateFlow()
    val pron: StateFlow<PronSet> = _pron.asStateFlow()
    val reading: StateFlow<ReadingStyle> = _reading.asStateFlow()
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    /** Changes whenever a cover file changes, so lists know to reload thumbnails. */
    val coverVersion: StateFlow<Int> = _coverVersion.asStateFlow()

    fun init(context: Context) {
        dir = File(context.filesDir, "library").also { it.mkdirs() }
        coversDir = File(dir, "covers").also { it.mkdirs() }
        novelsFile = JsonFile(File(dir, "novels.json"), ListSerializer(Novel.serializer()), emptyList())
        chaptersFile = JsonFile(File(dir, "chapters.json"), ListSerializer(SavedChapter.serializer()), emptyList())
        pronFile = JsonFile(File(dir, "pron.json"), PronSet.serializer(), PronSet())
        readingFile = JsonFile(File(dir, "reading.json"), ReadingStyle.serializer(), ReadingStyle())
        settingsFile = JsonFile(File(dir, "settings.json"), AppSettings.serializer(), AppSettings())
        _novels.value = novelsFile.read()
        _chapters.value = chaptersFile.read()
        _pron.value = Pron.sanitize(pronFile.read())
        _reading.value = readingFile.read().sanitized()
        _settings.value = settingsFile.read().sanitized()
    }

    // ---------- settings ----------

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        val v = f(_settings.value).sanitized()
        _settings.value = v
        io.launch { settingsFile.write(v) }
    }

    fun updateReading(f: (ReadingStyle) -> ReadingStyle) {
        val v = f(_reading.value).sanitized()
        _reading.value = v
        io.launch { readingFile.write(v) }
    }

    fun setPron(p: PronSet) {
        val v = Pron.sanitize(p)
        _pron.value = v
        io.launch { pronFile.write(v) }
    }

    // ---------- novels ----------

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun saveNovels(list: List<Novel>) {
        _novels.value = list
        io.launch { novelsFile.write(list) }
    }

    /** Adds a novel, or updates the one with the same key. Returns it. */
    fun addNovel(title: String, url: String, chapter: String = ""): Novel {
        val key = Extractor.novelKey(url)
        val list = _novels.value.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        val novel = Novel(
            id = if (i >= 0) list[i].id else newId(),
            title = title.trim().ifEmpty { if (i >= 0) list[i].title else Extractor.titleFromUrl(url) },
            url = url,
            key = key,
            chapter = chapter.ifEmpty { if (i >= 0) list[i].chapter else "" },
            updated = System.currentTimeMillis(),
            coverTry = if (i >= 0) list[i].coverTry else 0L,
        )
        if (i >= 0) list[i] = novel else list.add(0, novel)
        saveNovels(list)
        return novel
    }

    /** Loading any chapter of a saved novel moves its bookmark to that chapter. Returns the novel if there is one. */
    fun trackNovel(chapterTitle: String, url: String): Novel? {
        if (url.isEmpty()) return null
        val key = Extractor.novelKey(url)
        val list = _novels.value.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return null
        list[i] = list[i].copy(url = url, chapter = chapterTitle, updated = System.currentTimeMillis())
        saveNovels(list)
        return list[i]
    }

    fun novelFor(url: String): Novel? = if (url.isEmpty()) null else Extractor.novelKey(url).let { k -> _novels.value.firstOrNull { it.key == k } }

    fun updateNovelUrl(id: String, url: String) {
        saveNovels(_novels.value.map { if (it.id == id) it.copy(url = url, key = Extractor.novelKey(url), updated = System.currentTimeMillis()) else it })
    }

    fun markCoverTry(id: String) {
        saveNovels(_novels.value.map { if (it.id == id) it.copy(coverTry = System.currentTimeMillis()) else it })
    }

    fun removeNovel(id: String) {
        removeCover(id)
        removeToc(id)
        saveNovels(_novels.value.filter { it.id != id })
    }

    // ---------- covers (files, shown in the Library list only) ----------

    fun coverFile(id: String) = File(coversDir, "$id.jpg")
    fun hasCover(id: String) = coverFile(id).exists()

    fun saveCover(id: String, jpeg: ByteArray) {
        coverFile(id).writeBytes(jpeg)
        _coverVersion.update { it + 1 }
    }

    fun removeCover(id: String) {
        coverFile(id).delete()
        _coverVersion.update { it + 1 }
    }

    // ---------- saved chapters ----------

    fun saveChapter(c: SavedChapter) {
        val list = _chapters.value.toMutableList()
        val i = list.indexOfFirst { it.url == c.url && it.title == c.title }
        if (i >= 0) list[i] = c else list.add(0, c)
        _chapters.value = list
        io.launch { chaptersFile.write(list) }
    }

    fun deleteChapter(c: SavedChapter) {
        val list = _chapters.value.filter { !(it.url == c.url && it.title == c.title) }
        _chapters.value = list
        io.launch { chaptersFile.write(list) }
    }

    // ---------- bulk replace, used by Restore ----------

    fun replaceAll(
        novels: List<Novel>,
        chapters: List<SavedChapter>,
        pron: PronSet,
        reading: ReadingStyle?,
        settings: AppSettings?,
    ) {
        _novels.value = novels
        _chapters.value = chapters
        _pron.value = pron
        reading?.let { _reading.value = it }
        settings?.let { _settings.value = it }
        io.launch {
            novelsFile.write(novels); chaptersFile.write(chapters); pronFile.write(pron)
            reading?.let { readingFile.write(it) }
            settings?.let { settingsFile.write(it) }
        }
    }

    fun coverIds(): Set<String> = coversDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()

    fun clearAllCovers() {
        coversDir.listFiles()?.forEach { it.delete() }
        _coverVersion.update { it + 1 }
    }

    // ---------- chapter lists (one small file per novel; not part of a backup) ----------

    @Serializable
    private data class TocItem(val n: Double? = null, val t: String, val u: String)

    @Serializable
    private data class TocData(val base: String, val updated: Long, val source: String, val pages: Int, val items: List<TocItem>)

    data class TocMeta(val count: Int, val updated: Long, val source: String)

    private val tocCache = HashMap<String, List<ChapterItem>>()
    private val _tocVersion = MutableStateFlow(0)
    val tocVersion: StateFlow<Int> = _tocVersion.asStateFlow()
    private fun tocFile(id: String) = File(File(dir, "toc").also { it.mkdirs() }, "$id.json")
    private fun tocJson(id: String) = JsonFile(tocFile(id), TocData.serializer(), TocData("", 0, "", 0, emptyList()))

    @Synchronized
    fun tocItems(id: String): List<ChapterItem> = tocCache.getOrPut(id) {
        val d = tocJson(id).read()
        d.items.map { ChapterItem(it.n, it.t, if (it.u.startsWith("!")) it.u.substring(1) else d.base + it.u) }
    }

    fun tocMeta(id: String): TocMeta? {
        if (!tocFile(id).exists()) return null
        val items = tocItems(id)
        val d = tocJson(id).read()
        return if (items.isEmpty()) null else TocMeta(items.size, d.updated, d.source)
    }

    fun tocCount(id: String): Int = if (tocFile(id).exists()) tocItems(id).size else 0

    @Synchronized
    fun saveToc(id: String, base: String, items: List<ChapterItem>, source: String, pages: Int) {
        val b = base.trimEnd('/')
        val packed = items.map { TocItem(it.num, it.title, if (it.url.startsWith("$b/")) it.url.substring(b.length) else "!" + it.url) }
        tocJson(id).write(TocData(b, System.currentTimeMillis(), source, pages, packed))
        tocCache[id] = items
        _tocVersion.update { it + 1 }
    }

    @Synchronized
    fun removeToc(id: String) {
        tocFile(id).delete(); tocCache.remove(id)
        _tocVersion.update { it + 1 }
    }

    @Synchronized
    fun clearAllTocs() {
        File(dir, "toc").listFiles()?.forEach { it.delete() }
        tocCache.clear()
        _tocVersion.update { it + 1 }
    }

    // ---------- the chapter that was open last time ----------

    @Serializable
    data class LastChapter(val title: String = "", val text: String = "", val url: String = "", val next: String = "", val index: Int = 0)

    private val lastFile by lazy { JsonFile(File(dir, "last.json"), LastChapter.serializer(), LastChapter()) }
    fun loadLast(): LastChapter? = lastFile.read().takeIf { it.text.isNotEmpty() }
    fun saveLast(l: LastChapter) { io.launch { lastFile.write(l) } }
}
