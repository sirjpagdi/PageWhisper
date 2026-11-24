package com.pagewhisper.app.data

import com.pagewhisper.app.model.AppSettings
import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.model.PronRule
import com.pagewhisper.app.model.PronSet
import com.pagewhisper.app.model.ReadingStyle
import com.pagewhisper.app.model.SavedChapter
import com.pagewhisper.app.text.Extractor
import com.pagewhisper.app.text.Pron
import com.pagewhisper.app.tts.Voices
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

class BackupException(message: String) : Exception(message)

data class CleanNovel(
    val id: String, val title: String, val url: String, val key: String,
    val chapter: String, val updated: Long, val cover: String?,
)

data class CleanBackup(
    val novels: List<CleanNovel>,
    val chapters: List<SavedChapter>,
    val pron: PronSet,
    val reading: ReadingStyle?,
    val settings: AppSettings?,
    val from: String,
    val date: String,
) {
    val coverCount get() = novels.count { it.cover != null }
}

/** What is on this device now; `covers` maps a novel id to its cover as a data URL (or any non-empty marker). */
data class LocalData(
    val novels: List<Novel>,
    val covers: Map<String, String>,
    val chapters: List<SavedChapter>,
    val pron: PronSet,
)

/**
 * Backup file logic with no Android dependencies, so it can be unit tested.
 * Spec: FEATURES.md "Spec: Backup and restore". Everything in a file is untrusted and cleaned.
 */
object BackupCore {
    const val SCHEMA = 1
    const val MAX_BYTES = 60L * 1024 * 1024
    private const val COVER_MAX_CHARS = 400_000
    private val COVER_RE = Regex("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")
    private val ID_RE = Regex("^[A-Za-z0-9_-]{1,40}$")

    private val parser = Json { isLenient = true; ignoreUnknownKeys = true }

    // ---------- reading JSON safely ----------
    private fun JsonObject.p(k: String): JsonPrimitive? = this[k] as? JsonPrimitive
    private fun JsonObject.str(k: String): String? = p(k)?.takeIf { it.isString }?.content
    private fun JsonObject.num(k: String): Double? = p(k)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonObject.bool(k: String): Boolean? = p(k)?.takeIf { !it.isString }?.booleanOrNull
    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    private fun httpUrl(u: String?): Boolean =
        u != null && (u.startsWith("http://") || u.startsWith("https://")) && u.toHttpUrlOrNull() != null

    fun randomId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(10)

    // ---------- reading rules from JSON ----------
    private fun ruleList(a: JsonArray?): List<PronRule> {
        val out = ArrayList<PronRule>()
        for (e in a.orEmpty()) {
            val o = e as? JsonObject ?: continue
            val from = o.str("from") ?: continue
            val to = o.str("to") ?: continue
            out.add(PronRule(from, to, whole = o.bool("whole") != false, matchCase = o.bool("matchCase") == true))
        }
        return out
    }

    internal fun pronFrom(o: JsonObject?): PronSet {
        if (o == null) return PronSet()
        val novels = LinkedHashMap<String, List<PronRule>>()
        o.obj("novels")?.let { n -> for ((k, v) in n.entries.take(200)) novels[k] = ruleList(v as? JsonArray) }
        return Pron.sanitize(PronSet(ruleList(o.arr("global")), novels))
    }

    private fun readingFrom(o: JsonObject): ReadingStyle {
        val d = ReadingStyle()
        return ReadingStyle(
            font = o.str("font") ?: d.font,
            size = o.num("size")?.roundToInt() ?: d.size,
            line = o.num("line") ?: d.line,
            para = o.num("para") ?: d.para,
            pad = o.num("pad")?.roundToInt() ?: d.pad,
            ls = o.num("ls") ?: d.ls,
            align = o.str("align") ?: d.align,
            weight = o.num("weight")?.roundToInt() ?: d.weight,
            hyph = o.bool("hyph") ?: d.hyph,
            theme = o.str("theme") ?: d.theme,
            accent = o.str("accent") ?: d.accent,
            accentTone = o.num("accentTone")?.roundToInt() ?: d.accentTone,
            hl = o.str("hl") ?: d.hl,
            hlStyle = o.str("hlStyle") ?: d.hlStyle,
        ).sanitized()
    }

    private fun settingsFrom(s: JsonObject?, brightness: Int?, sleepLast: Int?): AppSettings? {
        if (s == null && brightness == null && sleepLast == null) return null
        val d = AppSettings()
        val voice = s?.str("voice")?.takeIf { v -> Voices.all.any { it.id == v } } ?: d.voice
        val chunk = s?.p("chunk")?.content?.toIntOrNull()?.takeIf { it in listOf(160, 280, 380) } ?: d.chunk
        return AppSettings(
            voice = voice,
            rate = (s?.num("rate") ?: d.rate).coerceIn(0.7, 1.4),
            chunk = chunk,
            ambVol = (s?.num("ambVol") ?: d.ambVol).coerceIn(0.0, 1.0),
            auto = s?.bool("auto") ?: d.auto,
            awake = s?.bool("awake") ?: d.awake,
            lockAwake = s?.bool("lockAwake") ?: d.lockAwake,
            ambOnly = s?.bool("ambOnly") ?: d.ambOnly,
            amb = s?.str("amb")?.takeIf { it in AppSettings.AMBIENT_KEYS } ?: d.amb,
            brightness = brightness ?: d.brightness,
            sleepLast = sleepLast ?: d.sleepLast,
        ).sanitized()
    }

    // ---------- validate ----------
    fun validate(text: String): CleanBackup {
        val root = try { parser.parseToJsonElement(text) } catch (e: Exception) {
            throw BackupException("That file is not a valid backup (it could not be read as JSON).")
        }
        val o = root as? JsonObject
        if (o == null || o.str("app") != "PageWhisper") throw BackupException("That file is not a PageWhisper backup.")
        val schema = o.num("schema")
        if (schema == null || schema < 1) throw BackupException("That backup has no version number.")
        if (schema > SCHEMA) throw BackupException("That backup was made by a newer version of PageWhisper. Update the app first.")

        val novels = ArrayList<CleanNovel>()
        val ids = HashSet<String>()
        for (e in o.arr("novels").orEmpty().take(2000)) {
            val n = e as? JsonObject ?: continue
            val title = n.str("title") ?: continue
            val url = n.str("url")
            if (!httpUrl(url)) continue
            var id = n.str("id")?.takeIf { ID_RE.matches(it) } ?: randomId()
            while (id in ids) id = randomId()
            ids.add(id)
            val cover = n.str("cover")?.takeIf { it.length <= COVER_MAX_CHARS && COVER_RE.matches(it) }
            novels.add(
                CleanNovel(
                    id = id,
                    title = title.trim().take(200).ifEmpty { Extractor.titleFromUrl(url!!) },
                    url = url!!,
                    key = Extractor.novelKey(url),
                    chapter = n.str("chapter")?.take(200) ?: "",
                    updated = n.num("updated")?.toLong() ?: 0L,
                    cover = cover,
                ),
            )
        }
        val chapters = ArrayList<SavedChapter>()
        for (e in o.arr("savedChapters").orEmpty().take(2000)) {
            val c = e as? JsonObject ?: continue
            val body = c.str("text") ?: continue
            if (body.isBlank()) continue
            chapters.add(
                SavedChapter(
                    title = c.str("title")?.trim()?.take(200)?.ifEmpty { null } ?: "Untitled chapter",
                    text = body,
                    url = c.str("url")?.takeIf { httpUrl(it) } ?: "",
                    next = c.str("next")?.takeIf { httpUrl(it) } ?: "",
                ),
            )
        }
        val brightness = o.num("brightness")?.roundToInt()?.coerceIn(10, 100)
        val sleep = o.num("sleepLastMinutes")?.takeIf { it >= 1 }?.roundToInt()?.coerceAtMost(600)
        val from = "PageWhisper backup" +
            (o.str("appVersion")?.takeIf { it.isNotEmpty() }?.let { " v" + it.take(12) } ?: "") +
            (o.str("platform")?.takeIf { it.isNotEmpty() }?.let { " (" + it.take(12) + ")" } ?: "")
        return CleanBackup(
            novels = novels,
            chapters = chapters,
            pron = pronFrom(o.obj("pronunciations")),
            reading = o.obj("reading")?.let { readingFrom(it) },
            settings = settingsFrom(o.obj("settings"), brightness, sleep),
            from = from,
            date = o.str("exportedAt")?.take(10) ?: "",
        )
    }

    // ---------- merge (pure; same result as the web app) ----------
    fun merge(local: LocalData, b: CleanBackup): LocalData {
        val novels = local.novels.toMutableList()
        val covers = local.covers.toMutableMap()
        val used = novels.map { it.id }.toHashSet()
        for (n in b.novels) {
            val i = novels.indexOfFirst { it.key == n.key }
            if (i < 0) {
                var id = n.id
                while (id in used) id = randomId()
                used.add(id)
                novels.add(Novel(id = id, title = n.title, url = n.url, key = n.key, chapter = n.chapter, updated = n.updated))
                n.cover?.let { covers[id] = it }
            } else {
                val l = novels[i]
                if (n.updated > l.updated) {
                    novels[i] = l.copy(title = n.title, url = n.url, chapter = n.chapter, updated = n.updated)
                    n.cover?.let { covers[l.id] = it }
                } else if (n.cover != null && covers[l.id] == null) {
                    covers[l.id] = n.cover
                }
            }
        }
        val have = local.chapters.map { it.url + "|" + it.title }.toHashSet()
        val chapters = local.chapters.toMutableList()
        for (c in b.chapters) if (have.add(c.url + "|" + c.title)) chapters.add(c)

        val novelRules = LinkedHashMap(local.pron.novels)
        for ((k, v) in b.pron.novels) novelRules[k] = Pron.mergeLists(novelRules[k].orEmpty(), v)
        val pron = Pron.sanitize(PronSet(Pron.mergeLists(local.pron.global, b.pron.global), novelRules))
        return LocalData(novels, covers, chapters, pron)
    }

    // ---------- build ----------
    /** `coverDataUrl(id)` returns the novel's cover as a data URL, or null. */
    fun build(
        appVersion: String,
        reading: ReadingStyle,
        settings: AppSettings,
        novels: List<Novel>,
        chapters: List<SavedChapter>,
        pron: PronSet,
        coverDataUrl: (String) -> String?,
        exportedAt: String = java.time.Instant.now().toString(),
    ): String {
        val el: JsonElement = buildJsonObject {
            put("app", "PageWhisper")
            put("schema", SCHEMA)
            put("appVersion", appVersion)
            put("platform", "android")
            put("exportedAt", exportedAt)
            put("reading", AppJson.encodeToJsonElement(ReadingStyle.serializer(), reading))
            put("settings", buildJsonObject {
                put("voice", settings.voice)
                put("rate", settings.rate)
                put("chunk", settings.chunk.toString())
                put("ambVol", settings.ambVol)
                put("auto", settings.auto)
                put("awake", settings.awake)
                put("lockAwake", settings.lockAwake)
                put("ambOnly", settings.ambOnly)
                put("amb", settings.amb)
            })
            put("brightness", settings.brightness)
            if (settings.sleepLast > 0) put("sleepLastMinutes", settings.sleepLast) else put("sleepLastMinutes", JsonNull)
            put("novels", buildJsonArray {
                for (n in novels) add(buildJsonObject {
                    put("id", n.id); put("title", n.title); put("url", n.url)
                    put("chapter", n.chapter); put("updated", n.updated)
                    val c = coverDataUrl(n.id)
                    if (c != null) put("cover", c) else put("cover", JsonNull)
                })
            })
            put("savedChapters", buildJsonArray {
                for (c in chapters) add(buildJsonObject {
                    put("title", c.title); put("text", c.text); put("url", c.url); put("next", c.next)
                })
            })
            put("pronunciations", AppJson.encodeToJsonElement(PronSet.serializer(), pron))
        }
        return el.toString()
    }
}
