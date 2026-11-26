package com.pagewhisper.app.text

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ParsedChapter(val title: String, val text: String)

/** A book read from a file. [cover] is the raw image bytes of the cover, if the book has one. */
class ParsedBook(val title: String, val author: String, val chapters: List<ParsedChapter>, val cover: ByteArray?)

/**
 * Reads an EPUB (a zip of XHTML files) into chapters of plain text. No Android classes are used, so it is unit tested on
 * the computer. See FEATURES.md ("Spec: Books").
 *
 * Order and titles: the reading order of the book (the "spine") gives the files; the table of contents (EPUB 3 nav file,
 * or the older NCX file) gives the chapter titles. A file that the contents list in several places (by anchor) is split
 * at those anchors. A file with no title of its own and no heading is joined to the chapter before it.
 */
object EpubReader {
    private const val MAX_ENTRY = 25_000_000L
    private val WS = Regex("\\s+")
    private val NOTE_MARK = Regex("^[\\[(]?[\\d*\u2020\u2021]+[\\])]?$")
    private val TEXT_TAGS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "figcaption", "td", "th", "dt", "dd")
    private val DROP_TAGS = setOf("script", "style", "svg", "rt", "rp", "nav")

    private class Item(val id: String, val href: String, val type: String, val props: String) {
        fun has(p: String) = props.split(' ').contains(p)
    }

    private class Para(val order: Int, val text: String, val el: Element)
    private class Blocks(val paras: List<Para>, val anchors: Map<String, Int>, val heading: String)
    private class TocEntry(val fragment: String, val title: String)

    private fun decode(s: String): String = try { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { s }

    /** Joins a folder and a relative address inside the zip, resolving "." and "..". */
    internal fun resolve(baseDir: String, href: String): String {
        val raw = decode(href.substringBefore('#'))
        val joined = if (raw.startsWith("/")) raw.removePrefix("/") else if (baseDir.isEmpty()) raw else "$baseDir/$raw"
        val parts = ArrayList<String>()
        for (p in joined.split('/')) {
            if (p.isEmpty() || p == ".") continue
            if (p == "..") { if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) } else parts.add(p)
        }
        return parts.joinToString("/")
    }

    fun read(file: File, fallbackTitle: String): ParsedBook {
        return ZipFile(file).use { zip ->
            val names = HashMap<String, ZipEntry>()
            for (e in zip.entries().toList()) names[e.name.lowercase()] = e
            fun bytes(path: String): ByteArray? {
                val e = names[path.lowercase()] ?: return null
                if (e.isDirectory || e.size > MAX_ENTRY) return null
                return zip.getInputStream(e).use { it.readBytes() }
            }
            fun text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)

            val encryption = text("META-INF/encryption.xml")
            if (encryption != null && encryption.contains("xmlenc#aes", ignoreCase = true)) {
                throw IllegalStateException("This book is protected (DRM) and cannot be read.")
            }

            var opfPath = text("META-INF/container.xml")
                ?.let { Jsoup.parse(it, "", Parser.xmlParser()).getElementsByTag("rootfile").firstOrNull()?.attr("full-path") }
                .orEmpty()
            if (opfPath.isEmpty()) opfPath = names.values.map { it.name }.firstOrNull { it.endsWith(".opf", ignoreCase = true) }.orEmpty()
            val opfText = if (opfPath.isEmpty()) null else text(opfPath)
            if (opfText == null) throw IllegalStateException("This file is not a valid EPUB (no package file).")
            val opf = Jsoup.parse(opfText, "", Parser.xmlParser())
            val baseDir = opfPath.substringBeforeLast('/', "")

            val items = opf.getElementsByTag("item").map { Item(it.attr("id"), it.attr("href"), it.attr("media-type"), it.attr("properties")) }
            val byId = items.associateBy { it.id }
            val title = opf.getElementsByTag("dc:title").firstOrNull()?.text()?.trim().orEmpty().ifEmpty { fallbackTitle }
            val author = opf.getElementsByTag("dc:creator").map { it.text().trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            val spine = opf.getElementsByTag("itemref").mapNotNull { r ->
                byId[r.attr("idref")]?.let { item -> item to !r.attr("linear").equals("no", ignoreCase = true) }
            }

            val coverItem = items.firstOrNull { it.has("cover-image") }
                ?: opf.getElementsByTag("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content")?.let { byId[it] }
                ?: items.firstOrNull { it.type.startsWith("image/") && it.id.contains("cover", ignoreCase = true) }
            val cover = coverItem?.let { bytes(resolve(baseDir, it.href)) }

            // ----- table of contents: file path -> its entries -----
            val toc = LinkedHashMap<String, MutableList<TocEntry>>()
            fun addToc(dir: String, href: String, label: String) {
                if (href.isBlank()) return
                val path = resolve(dir, href)
                toc.getOrPut(path) { ArrayList() }.add(TocEntry(decode(href.substringAfter('#', "")), label.replace(WS, " ").trim()))
            }
            val navItem = items.firstOrNull { it.has("nav") }
            if (navItem != null) {
                val navPath = resolve(baseDir, navItem.href)
                text(navPath)?.let { html ->
                    val navs = Jsoup.parse(html).select("nav")
                    val nav = navs.firstOrNull { it.attr("epub:type").contains("toc") } ?: navs.firstOrNull()
                    nav?.select("a[href]")?.forEach { addToc(navPath.substringBeforeLast('/', ""), it.attr("href"), it.text()) }
                }
            }
            if (toc.isEmpty()) {
                val ncx = items.firstOrNull { it.type == "application/x-dtbncx+xml" }
                    ?: opf.getElementsByTag("spine").firstOrNull()?.attr("toc")?.let { byId[it] }
                if (ncx != null) {
                    val ncxPath = resolve(baseDir, ncx.href)
                    text(ncxPath)?.let { xml ->
                        for (np in Jsoup.parse(xml, "", Parser.xmlParser()).getElementsByTag("navPoint")) {
                            val label = np.children().firstOrNull { it.tagName().equals("navLabel", ignoreCase = true) }
                                ?.getElementsByTag("text")?.firstOrNull()?.text().orEmpty()
                            val src = np.children().firstOrNull { it.tagName().equals("content", ignoreCase = true) }?.attr("src").orEmpty()
                            addToc(ncxPath.substringBeforeLast('/', ""), src, label)
                        }
                    }
                }
            }

            // ----- chapters -----
            val chapters = ArrayList<ParsedChapter>()
            for ((item, linear) in spine) {
                if (item.has("nav")) continue
                if (!(item.type.contains("html") || item.type.contains("xml"))) continue
                val path = resolve(baseDir, item.href)
                val entries = toc[path].orEmpty()
                if (!linear && entries.isEmpty()) continue
                val raw = bytes(path) ?: continue
                val doc = Jsoup.parse(ByteArrayInputStream(raw), null, "")
                val blocks = blocksOf(doc, entries.map { it.fragment }.filter { it.isNotEmpty() }.toSet())
                if (blocks.paras.isEmpty()) continue

                val points = ArrayList<Pair<Int, String>>()
                for (e in entries.map { (if (it.fragment.isEmpty()) 0 else (blocks.anchors[it.fragment] ?: 0)) to it.title }.sortedBy { it.first }) {
                    if (points.none { it.first == e.first }) points.add(e)
                }
                if (points.isEmpty() || points.first().first > 0) points.add(0, 0 to "")

                for ((k, p) in points.withIndex()) {
                    val end = if (k + 1 < points.size) points[k + 1].first else blocks.paras.size
                    if (p.first >= end) continue
                    val body = blocks.paras.subList(p.first, end).joinToString("\n\n") { it.text }
                    var name = p.second
                    if (name.isBlank() && p.first == 0) name = blocks.heading
                    if (name.isBlank()) {
                        // No title of its own: it continues the chapter before it.
                        val last = chapters.lastOrNull()
                        if (last != null) { chapters[chapters.lastIndex] = ParsedChapter(last.title, last.text + "\n\n" + body); continue }
                        if (body.length < 60) continue
                        name = "Beginning"
                    }
                    if (entries.isEmpty() && body.length < 60) continue // a title page or a blank page
                    chapters.add(ParsedChapter(name, body))
                }
            }
            if (chapters.isEmpty()) throw IllegalStateException("No readable text was found in this book.")
            ParsedBook(title, author, chapters, cover)
        }
    }

    /** The paragraphs of one page of the book, in order, and where the anchors in [wanted] are among them. */
    private fun blocksOf(doc: Document, wanted: Set<String>): Blocks {
        val body = doc.body() ?: return Blocks(emptyList(), emptyMap(), "")
        for (e in body.getAllElements().toList()) {
            if (e === body) continue
            val epub = e.attr("epub:type")
            val role = e.attr("role")
            val drop = e.tagName() in DROP_TAGS ||
                epub.contains("noteref") || epub.contains("pagebreak") || epub.contains("footnote") || epub.contains("endnote") ||
                role.contains("doc-noteref") || role.contains("doc-pagebreak") || role.contains("doc-footnote") ||
                (e.tagName() == "sup" && NOTE_MARK.matches(e.text().trim()))
            if (drop) e.remove()
        }
        val all = body.getAllElements().toList()
        val order = IdentityHashMap<Element, Int>()
        all.forEachIndexed { i, e -> order[e] = i }

        val paras = ArrayList<Para>()
        body.ownText().replace(WS, " ").trim().let { if (it.any { c -> c.isLetterOrDigit() }) paras.add(Para(0, it, body)) }
        for (e in all) {
            if (e.tagName() !in TEXT_TAGS) continue
            val hasBlockInside = e.getAllElements().any { it !== e && it.tagName() in TEXT_TAGS }
            val clean = (if (hasBlockInside) e.ownText() else e.text()).replace(WS, " ").trim()
            if (clean.any { it.isLetterOrDigit() }) paras.add(Para(order[e] ?: 0, clean, e))
        }

        val anchors = HashMap<String, Int>()
        if (wanted.isNotEmpty()) {
            for (e in all) {
                val ids = listOf(e.id(), e.attr("name")).filter { it.isNotEmpty() && it in wanted }
                if (ids.isEmpty()) continue
                val o = order[e] ?: continue
                var idx = paras.indexOfFirst { it.el === e || it.el.parents().contains(e) || e.parents().contains(it.el) }
                if (idx < 0) idx = paras.indexOfFirst { it.order > o }
                if (idx >= 0) for (id in ids) anchors.putIfAbsent(id, idx)
            }
        }
        val heading = body.select("h1, h2, h3").firstOrNull()?.text()?.replace(WS, " ")?.trim().orEmpty().takeIf { it.length in 1..120 }.orEmpty()
        return Blocks(paras, anchors, heading)
    }
}
