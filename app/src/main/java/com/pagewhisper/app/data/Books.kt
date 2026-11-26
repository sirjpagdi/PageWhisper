package com.pagewhisper.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

/** One chapter of an imported book: its title and the file that holds its text. */
@Serializable
data class BookChapter(val title: String, val file: String)

/** An imported EPUB or PDF. [kind] is "epub" or "pdf". */
@Serializable
data class BookMeta(
    val id: String,
    val title: String,
    val author: String = "",
    val kind: String = "epub",
    val chapters: List<BookChapter> = emptyList(),
)

class BookChapterData(val title: String, val text: String)

/**
 * Imported books live on the phone only, in files/books/<id>/ (meta.json and one text file per chapter). In the library
 * a book is a novel whose chapter addresses look like book://<id>/<number>, so chapter lists, bookmarks, auto-next and
 * reading aloud work for it like for any other novel. See FEATURES.md ("Spec: Books").
 */
object Books {
    private const val SCHEME = "book://"
    private lateinit var root: File
    private val cache = HashMap<String, BookMeta?>()

    fun init(ctx: Context) {
        root = File(ctx.filesDir, "books").also { it.mkdirs() }
    }

    fun isBookUrl(u: String) = u.startsWith(SCHEME)

    fun url(id: String, index: Int) = "$SCHEME$id/$index"

    /** The book id and chapter number of a book address, or null. */
    fun parse(u: String): Pair<String, Int>? {
        if (!isBookUrl(u)) return null
        val rest = u.removePrefix(SCHEME)
        val slash = rest.indexOf('/')
        val id = if (slash < 0) rest else rest.substring(0, slash)
        val index = if (slash < 0) 0 else rest.substring(slash + 1).toIntOrNull() ?: 0
        return if (id.isEmpty()) null else id to index
    }

    private fun dirOf(id: String) = File(root, id.filter { it.isLetterOrDigit() })

    fun meta(id: String): BookMeta? = synchronized(cache) {
        cache[id] ?: runCatching {
            AppJson.decodeFromString(BookMeta.serializer(), File(dirOf(id), "meta.json").readText())
        }.getOrNull()?.also { cache[id] = it }
    }

    fun chapter(url: String): BookChapterData? {
        val (id, index) = parse(url) ?: return null
        val c = meta(id)?.chapters?.getOrNull(index) ?: return null
        val f = File(dirOf(id), c.file)
        return if (f.exists()) BookChapterData(c.title, f.readText()) else null
    }

    /** "EPUB · Author", or just "EPUB". */
    fun label(url: String): String {
        val m = parse(url)?.let { meta(it.first) } ?: return "Book"
        return m.kind.uppercase() + if (m.author.isNotBlank()) " · " + m.author else ""
    }

    fun kindLabel(url: String): String = parse(url)?.let { meta(it.first) }?.kind?.uppercase() ?: "book"

    /** Writes the book to disk. [texts] has one entry per chapter of [meta]; the file names are set here. */
    fun save(meta: BookMeta, texts: List<String>): BookMeta {
        val dir = dirOf(meta.id)
        dir.deleteRecursively()
        dir.mkdirs()
        val chapters = meta.chapters.mapIndexed { i, c ->
            val name = "c$i.txt"
            File(dir, name).writeText(texts[i])
            BookChapter(c.title, name)
        }
        val saved = meta.copy(chapters = chapters)
        File(dir, "meta.json").writeText(AppJson.encodeToString(BookMeta.serializer(), saved))
        synchronized(cache) { cache[meta.id] = saved }
        return saved
    }

    fun delete(id: String) {
        dirOf(id).deleteRecursively()
        synchronized(cache) { cache.remove(id) }
    }
}
