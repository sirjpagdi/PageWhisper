package com.pagewhisper.app.text

import com.pagewhisper.app.model.PronRule
import com.pagewhisper.app.model.PronSet

/** Pronunciation rules. Spec: FEATURES.md "Spec: Pronunciation rules". Identical behaviour to the web app. */
object Pron {
    const val MAX_PER_LIST = 300
    const val FROM_MAX = 60
    const val TO_MAX = 120

    fun sanitizeList(list: List<PronRule>): List<PronRule> {
        val out = ArrayList<PronRule>()
        val seen = HashSet<String>()
        for (r in list) {
            val from = r.from.trim().take(FROM_MAX)
            val to = r.to.take(TO_MAX)
            if (from.isEmpty()) continue
            val id = from.lowercase() + "|" + r.whole + "|" + r.matchCase
            if (!seen.add(id)) continue
            out.add(PronRule(from, to, r.whole, r.matchCase))
            if (out.size >= MAX_PER_LIST) break
        }
        return out
    }

    fun sanitize(p: PronSet): PronSet {
        val novels = LinkedHashMap<String, List<PronRule>>()
        for ((k, v) in p.novels.entries.take(200)) {
            val l = sanitizeList(v)
            if (l.isNotEmpty() && k.isNotEmpty()) novels[k] = l
        }
        return PronSet(sanitizeList(p.global), novels)
    }

    fun ruleId(r: PronRule) = r.from.lowercase() + "|" + r.whole + "|" + r.matchCase

    /** Union by rule id; the first list wins on a clash. */
    fun mergeLists(a: List<PronRule>, b: List<PronRule>): List<PronRule> {
        val seen = a.map { ruleId(it) }.toHashSet()
        val out = ArrayList(a)
        for (r in b) if (seen.add(ruleId(r))) out.add(r)
        return out
    }
}

private class Compiled(val re: Regex, val to: String, val matchCase: Boolean)

/** Compiled rules for one PronSet. Create a new one whenever the rules change. */
class PronEngine(private val set: PronSet) {
    private val cache = HashMap<String, List<Compiled>>()

    private fun compile(list: List<PronRule>): List<Compiled> =
        list.sortedByDescending { it.from.length }.map { r ->
            val body = Regex.escape(r.from)
            val src = if (r.whole) "(?<![\\p{L}\\p{N}_])$body(?![\\p{L}\\p{N}_])" else body
            val opts = if (r.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Compiled(Regex(src, opts), r.to, r.matchCase)
        }

    private fun rulesFor(key: String): List<Compiled> = cache.getOrPut(key) {
        val novel = if (key.isNotEmpty()) set.novels[key].orEmpty() else emptyList()
        compile(novel) + compile(set.global) // this novel first
    }

    fun apply(text: String, novelKey: String): String {
        var out = text
        for (r in rulesFor(novelKey)) {
            out = r.re.replace(out) { m ->
                val first = m.value.firstOrNull()
                // a capitalised match keeps a capital in the replacement ("Dao" -> "Dow")
                if (!r.matchCase && first != null && first.isUpperCase() && r.to.isNotEmpty() && r.to[0].isLowerCase())
                    r.to.replaceFirstChar { it.uppercaseChar() }
                else r.to
            }
        }
        return out
    }
}
