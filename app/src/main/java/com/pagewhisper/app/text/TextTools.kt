package com.pagewhisper.app.text

import com.pagewhisper.app.model.Section

/** Pure text helpers; shared-tests.json keeps the shared text behavior in sync with the web app. */
object TextTools {
    private val NEWLINES3 = Regex("\\n{3,}")
    private val SPACE_BEFORE_NL = Regex("[ \\t]+\\n")
    private val WS = Regex("\\s+")
    private val MARKUP = Regex("[*_~#|^<>\\\\]+")
    private val BRACKETS = Regex("[\\[\\]]")
    private val HAS_TEXT = Regex("[\\p{L}\\p{N}]")
    private val SENTENCE = Regex("""[^.!?…。！？]+(?:[.!?…。！？]+["'”’»)\]]*)?""")

    fun clean(s: String): String =
        s.replace("\r", "").replace(SPACE_BEFORE_NL, "\n").replace(NEWLINES3, "\n\n").trim()

    /** What is actually sent to the voice: markup noise removed; empty if there is nothing to say. */
    fun speechText(s: String): String {
        val t = s.replace(MARKUP, " ").replace(BRACKETS, "").replace(WS, " ").trim()
        return if (HAS_TEXT.containsMatchIn(t)) t else ""
    }

    fun splitForSpeech(text: String, max: Int): List<String> {
        val sentences = SENTENCE.findAll(text).map { it.value }.toList().ifEmpty { listOf(text) }
        val out = ArrayList<String>()
        var current = ""
        for (raw in sentences) {
            var s = raw.trim()
            if (s.isEmpty()) continue
            if (current.isNotEmpty() && (current + " " + s).length > max) {
                out.add(current); current = ""
            }
            while (s.length > max) {
                var cut = s.lastIndexOf(' ', max)
                if (cut < max * 0.55) cut = max
                if (current.isNotEmpty()) { out.add(current); current = "" }
                out.add(s.substring(0, cut).trim())
                s = s.substring(cut).trim()
            }
            if (s.isNotEmpty()) current += (if (current.isNotEmpty()) " " else "") + s
        }
        if (current.isNotEmpty()) out.add(current)
        return out
    }

    /** Splits a chapter into spoken sections, keeping an optional title separate from visible body paragraphs. */
    fun buildSections(text: String, max: Int, pron: PronEngine?, novelKey: String, chapterTitle: String? = null): List<Section> {
        val out = ArrayList<Section>()
        val paras = text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val title = chapterTitle?.replace(WS, " ")?.trim()
            ?.takeIf { it.isNotEmpty() && it.lowercase() !in setOf("untitled chapter", "imported text", "pasted chapter", "shared chapter") }
            ?.takeUnless { heading -> paras.firstOrNull()?.replace(WS, " ")?.equals(heading, ignoreCase = true) == true }
            // The body already opens with its own chapter heading ("Chapter 12 ...") while the title is only the novel's
            // name: speak the body's heading and skip the title, so the novel title is never read in place of the chapter.
            ?.takeUnless { heading ->
                val first = paras.firstOrNull()?.replace(WS, " ").orEmpty()
                first.length <= 120 && Extractor.looksLikeChapter(first) && !Extractor.looksLikeChapter(heading)
            }
        var idx = 0
        title?.let { heading ->
            for (piece in splitForSpeech(heading, max)) {
                val spoken = speechText(pron?.apply(piece, novelKey) ?: piece)
                if (spoken.isNotEmpty()) out.add(Section(idx++, -1, piece, spoken))
            }
        }
        paras.forEachIndexed { pi, p ->
            for (piece in splitForSpeech(p, max)) {
                val spoken = speechText(pron?.apply(piece, novelKey) ?: piece)
                out.add(Section(idx++, pi, piece, spoken))
            }
        }
        return out
    }
}
