package com.pagewhisper.app.text

/**
 * Turns the text lines of a PDF into readable paragraphs and chapters. A PDF stores where each line sits on a page, not
 * paragraphs, so this works from rules of thumb. No Android or PDF classes are used, so it is unit tested on the
 * computer. See FEATURES.md ("Spec: Books").
 */
object PdfText {
    class Para(val text: String, val page: Int)

    private val WS = Regex("\\s+")
    // A page number: digits, or a Roman numeral up to 399 ("ii", "xiv"), alone on the line, with optional "page" and dashes.
    private val PAGE_NUMBER = Regex(
        "^(?:page\\s*)?[-\u2013\u2014\\s]*(\\d{1,4}|(?=[ivxlc]{2,8}[-\u2013\u2014\\s]*$)c{0,3}(?:xc|xl|l?x{0,3})(?:ix|iv|v?i{0,3}))[-\u2013\u2014\\s]*$",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBER_WORD = "[0-9]+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty"
    private val CHAPTER_HEADING = Regex("^(?:chapter|part|book)\\s+(?:$NUMBER_WORD)\\b.{0,70}$", RegexOption.IGNORE_CASE)
    private val SECTION_HEADING = Regex("^(?:prologue|epilogue|preface|foreword|afterword|introduction|acknowledg(?:e)?ments)\\b.{0,40}$", RegexOption.IGNORE_CASE)
    private const val SENTENCE_END = ".!?:;\"\u201D\u2019)]\u00BB\u2026"

    private fun norm(line: String) = line.lowercase().replace(Regex("\\d+"), "#").replace(WS, " ").trim()

    /** Drops page numbers and the running headers and footers that repeat on many pages. */
    fun stripHeaders(pages: List<List<String>>): List<List<String>> {
        if (pages.size < 4) return pages.map { p -> p.filterIndexed { i, l -> !((i < 2 || i >= p.size - 2) && PAGE_NUMBER.matches(l)) } }
        val count = HashMap<String, Int>()
        for (p in pages) {
            val edge = (p.take(2) + p.takeLast(2)).map { norm(it) }.toSet()
            for (k in edge) count[k] = (count[k] ?: 0) + 1
        }
        val limit = maxOf(3, (pages.size * 0.3).toInt())
        return pages.map { p ->
            p.filterIndexed { i, l ->
                val atEdge = i < 2 || i >= p.size - 2
                !(atEdge && (PAGE_NUMBER.matches(l) || (count[norm(l)] ?: 0) >= limit))
            }
        }
    }

    fun isHeadingLine(l: String): Boolean {
        if (l.length > 80) return false
        if (CHAPTER_HEADING.matches(l) || SECTION_HEADING.matches(l)) return true
        val letters = l.count { it.isLetter() }
        return letters >= 3 && l.contains(' ') && l.length <= 60 && l == l.uppercase() && l.split(' ').size <= 8 && !l.endsWith(".")
    }

    /** Joins lines into paragraphs. A paragraph may run over a page break; it belongs to the page where it starts. */
    fun paragraphs(pages: List<List<String>>): List<Para> {
        val lengths = pages.flatten().map { it.length }.sorted()
        val typical = if (lengths.isEmpty()) 0 else lengths[(lengths.size * 3 / 4).coerceAtMost(lengths.size - 1)]
        val out = ArrayList<Para>()
        var cur = StringBuilder()
        var curPage = 0
        var prev = ""
        var curIsHeading = false
        fun flush() {
            val t = cur.toString().replace(WS, " ").trim()
            if (t.isNotEmpty()) out.add(Para(t, curPage))
            cur = StringBuilder()
        }
        for ((pi, lines) in pages.withIndex()) {
            for (line in lines) {
                if (cur.isEmpty()) { cur.append(line); curPage = pi; prev = line; curIsHeading = isHeadingLine(line); continue }
                val heading = isHeadingLine(line)
                val endsSentence = prev.isNotEmpty() && prev.last() in SENTENCE_END
                when {
                    curIsHeading || heading -> { flush(); cur.append(line); curPage = pi; curIsHeading = heading }
                    endsSentence && prev.length < typical * 0.7 -> { flush(); cur.append(line); curPage = pi; curIsHeading = false }
                    prev.length > 1 && prev.endsWith("-") && prev[prev.length - 2].isLetter() && line.first().isLowerCase() ->
                        { cur.setLength(cur.length - 1); cur.append(line) }
                    else -> cur.append(' ').append(line)
                }
                prev = line
            }
        }
        flush()
        return out
    }

    private fun isChapterStart(t: String) = t.length <= 80 && CHAPTER_HEADING.matches(t)

    /**
     * Splits paragraphs into chapters. In order of preference: the PDF's own bookmarks ([outline]: page index and title);
     * headings like "Chapter 3" found in the text (needs at least 3); blocks of 10 pages.
     */
    fun chapters(paras: List<Para>, outline: List<Pair<Int, String>>, pageCount: Int): List<ParsedChapter> {
        fun join(list: List<Para>) = list.joinToString("\n\n") { it.text }
        val out = ArrayList<ParsedChapter>()

        val starts = outline.sortedBy { it.first }.fold(ArrayList<Pair<Int, String>>()) { acc, p ->
            if (acc.isEmpty() || acc.last().first != p.first) acc.add(p); acc
        }
        if (starts.size >= 2) {
            val front = paras.filter { it.page < starts.first().first }
            if (join(front).length >= 200) out.add(ParsedChapter("Front matter", join(front)))
            for ((i, s) in starts.withIndex()) {
                val end = if (i + 1 < starts.size) starts[i + 1].first else Int.MAX_VALUE
                val body = join(paras.filter { it.page >= s.first && it.page < end })
                if (body.isNotBlank()) out.add(ParsedChapter(s.second.replace(WS, " ").trim().take(120), body))
            }
            if (out.isNotEmpty()) return out
        }

        val heads = paras.indices.filter { isChapterStart(paras[it].text) }
        if (heads.size >= 3) {
            val front = paras.subList(0, heads.first())
            if (join(front).length >= 300) out.add(ParsedChapter("Beginning", join(front)))
            for ((i, h) in heads.withIndex()) {
                val end = if (i + 1 < heads.size) heads[i + 1] else paras.size
                var title = paras[h].text
                var from = h + 1
                // A short line right after "Chapter 3" is usually its name.
                val next = paras.getOrNull(from)
                if (next != null && from < end && next.text.length <= 60 && !(next.text.last() in SENTENCE_END)) { title += " - " + next.text; from++ }
                val body = join(paras.subList(minOf(from, end), end))
                if (body.isNotBlank()) out.add(ParsedChapter(title.take(120), body))
            }
            if (out.isNotEmpty()) return out
        }

        val block = 10
        val pages = maxOf(pageCount, (paras.maxOfOrNull { it.page } ?: 0) + 1)
        var start = 0
        while (start < pages) {
            val end = minOf(start + block, pages)
            val body = join(paras.filter { it.page in start until end })
            if (body.isNotBlank()) out.add(ParsedChapter("Pages ${start + 1}\u2013$end", body))
            start = end
        }
        return out
    }
}
