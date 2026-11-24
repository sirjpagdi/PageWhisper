package com.pagewhisper.app.text

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Chapter text, titles, links and cover candidates from web pages. Ported from the web app; keep them in step. */
object Extractor {
    data class Extracted(val title: String, val body: String, val next: String, val covers: List<String>)

    private const val NAVW =
        "(?:prev(?:ious)?(?:\\s+chapter)?|next(?:\\s+chapter)?|home|menu|index|contents|table of contents|chapter list|bookmark|report|login|sign in|register)"
    private const val NAVSEP = "[\\s|·»«›‹→←<>•-]*"
    private val NAV = Regex("^$NAVSEP$NAVW(?:$NAVSEP$NAVW)*$NAVSEP$", RegexOption.IGNORE_CASE)
    private val BAD_IMG = Regex("(logo|favicon|icon|avatar|sprite|placeholder|loading|blank|pixel|banner|emoji|\\.svg|\\.gif)", RegexOption.IGNORE_CASE)
    private val WS = Regex("\\s+")

    // ---------- titles and links (checked by shared-tests.json) ----------

    fun cleanTitle(t: String): String {
        val parts = t.replace(WS, " ").trim().split(Regex("\\s+[-–—|]\\s+")).toMutableList()
        fun siteish(s: String): Boolean =
            s.length <= 35 &&
                !Regex("\\bchapter\\b|\\bch\\.?\\s*\\d", RegexOption.IGNORE_CASE).containsMatchIn(s) &&
                (Regex("^free\\b", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
                    Regex("\\b(web\\s?novels?|light novels?|novels?)$", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
                    Regex("\\.(com|net|org|io|co)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
                    Regex("\\bread\\b.*\\bonline\\b", RegexOption.IGNORE_CASE).containsMatchIn(s))
        while (parts.size > 1 && siteish(parts.last())) parts.removeAt(parts.lastIndex)
        return parts.joinToString(" - ")
    }

    private val CHAPTER_WORD = Regex("(?:^|[^\\p{L}])(?:chapter|chap\\.?|ch\\.?|episode|ep\\.?|vol(?:ume)?\\.?)\\s*\\d", RegexOption.IGNORE_CASE)
    private val LEADING_NUMBER = Regex("^\\s*\\d{1,5}\\s*[-–—:.)]\\s*\\S")
    private val TITLE_SEP = Regex("\\s+[-–—|]\\s+")

    /** True for headings such as "Chapter 12: The Return", "Ch. 12 - Title" or "12. Title". */
    fun looksLikeChapter(s: String): Boolean = CHAPTER_WORD.containsMatchIn(s) || LEADING_NUMBER.containsMatchIn(s)

    /**
     * Picks the chapter heading ("Chapter 12 - Title") out of the headings of a page. Pages usually carry the novel title
     * in the first h1 and the chapter heading somewhere else, so the first heading is not trusted. [fallback] is used
     * when no heading looks like a chapter.
     */
    fun pickChapterTitle(candidates: List<String>, fallback: String): String {
        val cleaned = candidates.map { cleanTitle(it) }.filter { it.isNotBlank() && it.length <= 160 }
        val others = cleaned.filterNot { looksLikeChapter(it) }
        for (c in cleaned) {
            if (!looksLikeChapter(c)) continue
            val parts = c.split(TITLE_SEP)
            val i = parts.indexOfFirst { looksLikeChapter(it) }.coerceAtLeast(0)
            val tail = parts.drop(i).filterIndexed { k, part -> k == 0 || others.none { it.equals(part.trim(), ignoreCase = true) } }
            return tail.joinToString(" - ")
        }
        return fallback
    }

    fun hostOf(u: String): String = u.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: ""

    private fun segments(u: String): List<String> =
        u.toHttpUrlOrNull()?.pathSegments?.filter { it.isNotEmpty() } ?: emptyList()

    /** Chapters of one novel share a key: host + path without the trailing chapter number. */
    fun novelKey(u: String): String {
        if (u.toHttpUrlOrNull() == null) return ""
        val seg = segments(u).toMutableList()
        if (seg.size > 1 && seg.last().any { it.isDigit() }) seg.removeAt(seg.lastIndex)
        return hostOf(u) + "/" + seg.joinToString("/")
    }

    fun novelPageUrl(u: String): String {
        val url = u.toHttpUrlOrNull() ?: return u
        val seg = segments(u).toMutableList()
        if (seg.size > 1 && seg.last().any { it.isDigit() }) seg.removeAt(seg.lastIndex)
        return url.scheme + "://" + url.host + (if (url.port != okhttp3.HttpUrl.defaultPort(url.scheme)) ":" + url.port else "") + "/" + seg.joinToString("/")
    }

    fun titleFromUrl(u: String): String {
        if (u.toHttpUrlOrNull() == null) return "Untitled novel"
        val skip = Regex("^(novel|novels|book|books|read|chapter|series|title)$", RegexOption.IGNORE_CASE)
        val seg = segments(u).filter { it.isNotEmpty() && !Regex("^\\d+$").matches(it) && !skip.matches(it) }
        val raw = (seg.lastOrNull() ?: hostOf(u))
            .replace(Regex("[-_]+"), " ").replace(Regex("\\.\\w+$"), "").trim()
        return Regex("\\b\\w").replace(raw) { it.value.uppercase() }
    }

    /** Adds 1 to the last number in the path; "" when there is none. */
    fun guessNext(u: String): String {
        val url = u.toHttpUrlOrNull() ?: return ""
        val m = Regex("^(.*?)(\\d+)(\\D*)$").find(url.encodedPath) ?: return ""
        val next = m.groupValues[1] + (m.groupValues[2].toBigInteger() + java.math.BigInteger.ONE) + m.groupValues[3]
        return url.newBuilder().encodedPath(next).build().toString()
    }

    // ---------- from a web page ----------

    fun fromHtml(html: String, url: String): Extracted {
        val doc = Jsoup.parse(html, url)
        val h1 = doc.selectFirst("h1")?.text().orEmpty()
        val fallbackTitle = cleanTitle(if (h1.isNotBlank()) h1 else doc.title())
        val headings = ArrayList<String>()
        for (e in doc.select(".chapter-title, .chr-title, .chapter__title, .chapter-name, [class*=chapter-title], [class*=chapter_title], [class*=chr-title], .entry-title, h1, h2, h3, h4").take(14)) {
            e.text().replace(WS, " ").trim().takeIf { it.isNotEmpty() }?.let { headings.add(it) }
        }
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { headings.add(it) }
        if (doc.title().isNotBlank()) headings.add(doc.title())
        val title = pickChapterTitle(headings, fallbackTitle)
        val next = findNextInDoc(doc)
        val covers = coverFromDoc(doc)
        doc.select("script,style,noscript,nav,header,footer,aside,form,button,svg,iframe,.ads,.ad,.sidebar,.comments,#comments,[class*=advert]").remove()

        fun len(e: Element) = e.text().trim().length
        val sels = listOf("#chapter-content", ".chapter-content", "[itemprop=articleBody]", ".entry-content", ".reading-content", "#chr-content", "article", "main", "#content", ".content")
        var best: Element? = null
        for (s in sels) {
            val el = doc.selectFirst(s)
            if (el != null && len(el) >= 500) { best = el; break }
        }
        if (best == null) {
            var n = 0
            for (s in sels) for (el in doc.select(s)) {
                val l = len(el)
                if (l > n) { best = el; n = l }
            }
        }
        val root: Element = best ?: doc.body()
        val ps = root.select("p").map { it.text().replace(WS, " ").trim() }.filter { it.isNotEmpty() }
        val body: String = if (ps.joinToString(" ").length >= 250) {
            ps.joinToString("\n\n")
        } else {
            for (br in root.select("br")) br.replaceWith(TextNode("\n"))
            for (b in root.select("div,li,h1,h2,h3,h4,h5,h6,section")) b.appendText("\n")
            TextTools.clean(root.wholeText())
        }
        require(body.length >= 250) { "No chapter body found." }
        return Extracted(title, body, next, covers)
    }

    private fun httpOnly(u: String): String = if (u.startsWith("http://") || u.startsWith("https://")) u else ""

    fun findNextInDoc(doc: Document): String {
        doc.selectFirst("a[rel~=next],link[rel=next]")?.let {
            val u = httpOnly(it.absUrl("href"))
            if (u.isNotEmpty()) return u
        }
        val label = Regex("^(?:next(?:\\s+chapter)?|[→›»>]+)\\s*[→›»>]*$", RegexOption.IGNORE_CASE)
        for (a in doc.select("a[href]")) {
            val t = a.text().trim()
            if (t.length <= 30 && label.matches(t)) {
                val u = httpOnly(a.absUrl("href"))
                if (u.isNotEmpty()) return u
            }
        }
        return ""
    }

    // ---------- from the reader service (markdown) ----------

    fun findNextInMarkdown(md: String): String {
        val re = Regex("\\[([^\\]\\n]{0,40})\\]\\((https?://[^)\\s]+)[^)]*\\)")
        for (m in re.findAll(md)) {
            if (Regex("\\bnext\\b|^[\\s→›»>]+$", RegexOption.IGNORE_CASE).containsMatchIn(m.groupValues[1])) return m.groupValues[2]
        }
        return ""
    }

    fun fromMarkdown(md: String, url: String): Extracted {
        val m = RegexOption.MULTILINE
        val h1 = Regex("^#\\s+(.+)$", m).find(md)?.groupValues?.get(1)
        val t = Regex("^Title:\\s*(.+)$", m).find(md)?.groupValues?.get(1)
        val next = findNextInMarkdown(md)
        val covers = coverFromMarkdown(md)
        var b = md
            .replaceFirst(Regex("^Title:.*$", m), "")
            .replaceFirst(Regex("^URL Source:.*$", setOf(m, RegexOption.IGNORE_CASE)), "")
            .replaceFirst(Regex("^Published Time:.*$", setOf(m, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("^Warning:.*$", setOf(m, RegexOption.IGNORE_CASE)), "")
            .replaceFirst(Regex("^Markdown Content:\\s*$", setOf(m, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("^#{1,6}\\s*", m), "")
            .replace(Regex("^>\\s?", m), "")
            .replace(Regex("^\\s*[-*_=]{3,}\\s*$", m), "")
            .replace(Regex("(\\*\\*|__|~~)(.+?)\\1"), "$2")
            .replace(Regex("(^|[\\s(])[*_]([^*_\\n]+)[*_](?=[\\s).,!?:;]|$)"), "$1$2")
            .replace("`", "")
        b = b.split("\n").filter { !NAV.matches(it.trim()) }.joinToString("\n")
        val headings = Regex("^#{1,6}\\s+(.+)$", m).findAll(md).map { it.groupValues[1].trim() }.take(14).toMutableList()
        t?.let { headings.add(it.trim()) }
        val title = pickChapterTitle(headings, cleanTitle((h1 ?: t ?: hostOf(url)).trim()))
        return Extracted(title, TextTools.clean(b), next, covers)
    }

    // ---------- cover candidates ----------

    private fun coverFromDoc(doc: Document): List<String> {
        val out = ArrayList<String>()
        fun push(raw: String?) {
            val u = httpOnly((raw ?: "").trim())
            if (u.isNotEmpty() && u !in out && !BAD_IMG.containsMatchIn(u)) out.add(u)
        }
        for (s in listOf("meta[property=og:image:secure_url]", "meta[property=og:image]", "meta[name=og:image]", "meta[name=twitter:image]", "meta[property=twitter:image]", "link[rel=image_src]", "meta[itemprop=image]")) {
            val el = doc.selectFirst(s) ?: continue
            val raw = if (el.hasAttr("content")) el.attr("content") else el.attr("href")
            // relative addresses are resolved against the page
            push(if (raw.startsWith("http")) raw else runCatching { java.net.URI(doc.location()).resolve(raw).toString() }.getOrNull())
        }
        for (img in doc.select("img[itemprop=image],.book-cover img,.novel-cover img,.cover img,img.cover,[class*=cover] img,[class*=poster] img,.summary_image img,.book img")) {
            val raw = listOf("data-src", "data-lazy-src", "data-original", "src").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
            push(if (raw != null) runCatching { java.net.URI(doc.location()).resolve(raw).toString() }.getOrNull() else null)
            if (out.size >= 6) break
        }
        return out.take(6)
    }

    fun coverFromHtml(html: String, base: String): List<String> = coverFromDoc(Jsoup.parse(html, base))

    fun coverFromMarkdown(md: String): List<String> {
        val urls = Regex("!\\[[^\\]]*\\]\\((https?://[^)\\s]+)").findAll(md).map { it.groupValues[1] }.filter { !BAD_IMG.containsMatchIn(it) }.toList()
        return urls.distinct().sortedByDescending { Regex("cover", RegexOption.IGNORE_CASE).containsMatchIn(it) }.take(6)
    }
}
