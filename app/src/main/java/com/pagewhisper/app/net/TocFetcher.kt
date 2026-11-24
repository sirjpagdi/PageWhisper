package com.pagewhisper.app.net

import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.text.ChapterItem
import com.pagewhisper.app.text.ChapterList
import com.pagewhisper.app.text.Extractor

/** Reads a novel's complete chapter list from its own page (and its extra pages). */
object TocFetcher {
    const val MAX_PAGES = 40

    data class Result(val items: List<ChapterItem>, val pages: Int)

    private suspend fun page(url: String, key: String): ChapterList.PageLinks {
        var best: ChapterList.PageLinks? = null
        val tries: List<suspend () -> ChapterList.PageLinks> = listOf(
            { ChapterList.linksFromHtml(Fetcher.text(url), url) },
            { ChapterList.linksFromMarkdown(Fetcher.text("https://r.jina.ai/$url", mapOf("Accept" to "text/plain"))) },
        )
        for (t in tries) {
            try {
                val res = t()
                if (ChapterList.pickChapters(res.chapterLinks, key).isNotEmpty()) return res
                if (best == null) best = res
            } catch (_: Throwable) {}
        }
        return best ?: throw java.io.IOException("page not available")
    }

    suspend fun fetch(novel: Novel, say: (String) -> Unit): Result {
        val base = Extractor.novelPageUrl(novel.url)
        val key = novel.key
        val home = Extractor.hostOf(base)
        val novelPath = "/" + key.substringAfter('/', "")
        val queue = ArrayDeque(listOf(base))
        val seen = hashSetOf(ChapterList.normUrl(base))
        val found = LinkedHashMap<String, ChapterItem>()
        var pages = 0
        while (queue.isNotEmpty() && pages < MAX_PAGES && found.size < ChapterList.MAX_ITEMS) {
            val pg = queue.removeFirst()
            pages++
            say("Reading page $pages… ${found.size} chapters so far")
            val res = try { page(pg, key) } catch (_: Throwable) { if (pages == 1) break else continue }
            for (it in ChapterList.pickChapters(res.chapterLinks, key)) found.putIfAbsent(ChapterList.normUrl(it.url), it)
            val more = (if (pages == 1 && found.size < 5) res.allLinks else emptyList()) + res.pageLinks
            for (l in more) {
                val k = ChapterList.normUrl(l)
                val u = l.let { runCatching { java.net.URI(it) }.getOrNull() }
                val ok = Extractor.hostOf(l) == home && u != null && ((u.path ?: "").startsWith(novelPath) || Regex("page", RegexOption.IGNORE_CASE).containsMatchIn((u.query ?: "") + (u.path ?: "")))
                if (ok && seen.add(k)) queue.addLast(l)
            }
        }
        return Result(ChapterList.sortChapters(found.values.toList()), pages)
    }

    /** For sites that block fetching: chapters 1..n made from the saved chapter link's pattern. */
    fun build(novel: Novel, n: Int): List<ChapterItem>? {
        if (ChapterList.urlForNumber(novel.url, 1).isEmpty()) return null
        return (1..n.coerceIn(1, ChapterList.MAX_ITEMS)).map { ChapterItem(it.toDouble(), "Chapter $it", ChapterList.urlForNumber(novel.url, it)) }
    }
}
