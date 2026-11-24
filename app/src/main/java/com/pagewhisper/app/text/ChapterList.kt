package com.pagewhisper.app.text

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

data class ChapterItem(val num: Double?, val title: String, val url: String)

/** Chapter lists (table of contents). Same rules as the web app; checked by shared-tests.json. */
object ChapterList {
    data class Link(val href: String, val text: String)
    data class PageLinks(val chapterLinks: List<Link>, val pageLinks: List<String>, val allLinks: List<String>)

    private val CH_TEXT = Regex("(?:chapter|ch\\.?|episode|ep\\.?)\\s*#?\\s*\\d", RegexOption.IGNORE_CASE)
    private val NUM_IN_TEXT = Regex("(?:chapter|ch\\.?|episode|ep\\.?)\\s*#?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val LIST_PAGE = Regex("/(chapter-?list|chapters?|all-chapters|table-of-contents|toc)/?$", RegexOption.IGNORE_CASE)
    private val LOOKS = Regex("chapter|[-_/]ch[-_]?\\d|/\\d+/?$", RegexOption.IGNORE_CASE)
    private val CHAPTERISH_PATH = Regex("chapter|\\d", RegexOption.IGNORE_CASE)
    const val MAX_ITEMS = 6000

    /** Same fields as ChapterItem; exists so tests can compare lists plainly. */
    data class ChapterItemForTest(val num: Double?, val title: String, val url: String)

    fun normUrl(u: String): String {
        val url = u.toHttpUrlOrNull() ?: return u
        return url.newBuilder().fragment(null).build().toString().removeSuffix("/")
    }

    fun numberText(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    /** "Chapter 12" in the text wins; otherwise the last number in the last part of the address. */
    fun chapterNumber(url: String, text: String): Double? {
        NUM_IN_TEXT.find(text)?.let { return it.groupValues[1].toDouble() }
        val seg = url.toHttpUrlOrNull()?.pathSegments?.filter { it.isNotEmpty() }?.lastOrNull() ?: return null
        return Regex("(\\d+)(?!.*\\d)").find(seg)?.groupValues?.get(1)?.toDouble()
    }

    fun urlForNumber(url: String, n: Int): String {
        val u = url.toHttpUrlOrNull() ?: return ""
        val m = Regex("^(.*?)(\\d+)(\\D*)$").find(u.encodedPath) ?: return ""
        return u.newBuilder().encodedPath(m.groupValues[1] + n + m.groupValues[3]).query(null).fragment(null).build().toString()
    }

    /** Oldest first when most have numbers (stable); otherwise as found. */
    fun sortChapters(items: List<ChapterItem>): List<ChapterItem> {
        val withNum = items.count { it.num != null }
        return if (items.isNotEmpty() && withNum.toDouble() / items.size >= 0.8) items.sortedBy { it.num ?: 1e9 } else items.toList()
    }

    private class Parsed(val href: String, val path: String, val text: String)

    fun pickChapters(links: List<Link>, key: String): List<ChapterItem> {
        val slash = key.indexOf('/')
        val host = if (slash < 0) key else key.substring(0, slash)
        val novelPath = "/" + (if (slash < 0) "" else key.substring(slash + 1))
        val parsed = ArrayList<Parsed>()
        for (l in links) {
            val u = l.href.toHttpUrlOrNull() ?: continue
            if (Extractor.hostOf(l.href) != host) continue
            parsed.add(Parsed(u.newBuilder().fragment(null).build().toString(), u.encodedPath, l.text.replace(Regex("\\s+"), " ").trim()))
        }
        fun looks(l: Parsed) = CH_TEXT.containsMatchIn(l.text) || LOOKS.containsMatchIn(l.path)
        var pool = parsed.filter {
            !LIST_PAGE.containsMatchIn(it.path) && it.path.startsWith("$novelPath/") && it.path.length > novelPath.length + 1 &&
                (looks(it) || chapterNumber(it.href, it.text) != null)
        }
        if (pool.size < 3) {
            pool = parsed.filter { !LIST_PAGE.containsMatchIn(it.path) && CH_TEXT.containsMatchIn(it.text) && CHAPTERISH_PATH.containsMatchIn(it.path) }
        }
        val seen = HashSet<String>()
        val items = ArrayList<ChapterItem>()
        for (l in pool) {
            if (!seen.add(normUrl(l.href))) continue
            val num = chapterNumber(l.href, l.text)
            val title = l.text.ifEmpty { if (num != null) "Chapter " + numberText(num) else "Chapter" }.take(120)
            items.add(ChapterItem(num, title, l.href))
        }
        return sortChapters(items)
    }

    /** Where a chapter address sits in a list: by address first, then by chapter number. -1 if it is not there. */
    fun indexOf(items: List<ChapterItem>, url: String): Int {
        val k = normUrl(url)
        val i = items.indexOfFirst { normUrl(it.url) == k }
        if (i >= 0) return i
        val num = chapterNumber(url, "") ?: return -1
        return items.indexOfFirst { it.num == num }
    }

    // ---------- reading links from a page ----------

    fun linksFromHtml(html: String, base: String): PageLinks {
        val doc = Jsoup.parse(html, base)
        fun toLinks(els: List<org.jsoup.nodes.Element>) = els.mapNotNull { a ->
            val h = a.absUrl("href")
            if (h.startsWith("http://") || h.startsWith("https://")) Link(h, a.text()) else null
        }
        val box = doc.selectFirst("#chapter-list,.chapter-list,.list-chapter,#list-chapter,.chapters,.wp-manga-chapter,ul.chapter-list,[class*=chapter-list],[class*=chapterlist],[id*=chapter-list]")
        val inBox = box?.select("a[href]").orEmpty()
        val chapterLinks = if (inBox.size >= 3) toLinks(inBox) else toLinks(doc.select("a[href]"))
        val pageLinks = doc.select(".pagination a[href],.pager a[href],[class*=pagination] a[href],[class*=paging] a[href],[aria-label*=agination] a[href],a[rel~=next][href],link[rel=next][href]")
            .map { it.absUrl("href") }.filter { it.startsWith("http") }
        val all = Regex("(chapter list|all chapters|table of contents|view all|full list)", RegexOption.IGNORE_CASE)
        val allLinks = doc.select("a[href]").filter { all.containsMatchIn(it.text().trim()) }.map { it.absUrl("href") }.filter { it.startsWith("http") }
        return PageLinks(chapterLinks, pageLinks, allLinks)
    }

    fun linksFromMarkdown(md: String): PageLinks {
        val out = Regex("(?<!!)\\[([^\\]]{0,160})\\]\\((https?://[^)\\s]+)[^)]*\\)").findAll(md).map { Link(it.groupValues[2], it.groupValues[1]) }.toList()
        return PageLinks(
            out,
            out.map { it.href }.filter { Regex("[?&]page=\\d+|/page/\\d+").containsMatchIn(it) },
            out.filter { Regex("(chapter list|all chapters|table of contents|view all)", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }.map { it.href },
        )
    }
}
