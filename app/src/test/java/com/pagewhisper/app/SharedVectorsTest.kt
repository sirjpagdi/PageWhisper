package com.pagewhisper.app

import androidx.compose.ui.graphics.Color
import com.pagewhisper.app.data.BackupCore
import com.pagewhisper.app.data.CleanBackup
import com.pagewhisper.app.data.CleanNovel
import com.pagewhisper.app.data.LocalData
import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.model.PronRule
import com.pagewhisper.app.model.PronSet
import com.pagewhisper.app.model.ReadingStyle
import com.pagewhisper.app.model.SavedChapter
import com.pagewhisper.app.text.ChapterList
import com.pagewhisper.app.text.Extractor
import com.pagewhisper.app.text.Pron
import com.pagewhisper.app.text.PronEngine
import com.pagewhisper.app.text.TextTools
import com.pagewhisper.app.ui.accentContentColor
import com.pagewhisper.app.ui.pwColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs every case in shared-tests.json. The web app passes the same file, so both apps behave identically.
 * (Copy of the file lives in src/test/resources; keep it in step with the one in the web project.)
 */
class SharedVectorsTest {
    private val root: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResourceAsStream("shared-tests.json")!!.bufferedReader().readText(),
    ).jsonObject

    private fun cases(k: String): JsonArray = root[k]!!.jsonArray
    private fun JsonElement.str() = jsonPrimitive.content
    private fun JsonElement.strOrNull() = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    private fun JsonElement.dbl(): Double? = if (this is JsonNull) null else jsonPrimitive.doubleOrNull

    private fun rules(a: JsonElement?): List<PronRule> = (a as? JsonArray).orEmpty().map {
        val o = it.jsonObject
        PronRule(o["from"]!!.str(), o["to"]!!.str(), o["whole"]?.jsonPrimitive?.booleanOrNull ?: true, o["matchCase"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    private fun pronSet(o: JsonObject): PronSet = PronSet(
        rules(o["global"]),
        o["novels"]!!.jsonObject.mapValues { rules(it.value) },
    )

    @Test fun pronunciation() {
        for (c in cases("pronunciation")) {
            val o = c.jsonObject
            val engine = PronEngine(pronSet(o["rules"]!!.jsonObject))
            assertEquals(o["name"]!!.str(), o["expected"]!!.str(), engine.apply(o["input"]!!.str(), o["key"]!!.str()))
        }
    }

    @Test fun newReaderFontsAreKeptBySanitizer() {
        for (font in listOf("ebgaramond", "crimsonpro", "sourceserif4", "montserrat")) {
            assertEquals(font, ReadingStyle(font = font).sanitized().font)
        }
    }

    @Test fun accentContentColorTracksAccentLightness() {
        assertEquals(Color.Black, accentContentColor(pwColors("dark", "purple", 35).accent))
        assertEquals(Color.White, accentContentColor(pwColors("dark", "purple", -35).accent))
    }

    @Test fun sanitizePron() {
        val o = root["sanitizePron"]!!.jsonObject
        val got = BackupCore.pronFrom(o["input"]!!.jsonObject)
        val want = pronSet(o["expected"]!!.jsonObject)
        assertEquals(want, got)
        assertEquals(want, Pron.sanitize(want))
    }

    @Test fun cleanTitle() { for (c in cases("cleanTitle")) assertEquals(c.jsonObject["input"]!!.str(), c.jsonObject["expected"]!!.str(), Extractor.cleanTitle(c.jsonObject["input"]!!.str())) }
    @Test fun novelKey() { for (c in cases("novelKey")) assertEquals(c.jsonObject["input"]!!.str(), c.jsonObject["expected"]!!.str(), Extractor.novelKey(c.jsonObject["input"]!!.str())) }
    @Test fun guessNext() { for (c in cases("guessNext")) assertEquals(c.jsonObject["input"]!!.str(), c.jsonObject["expected"]!!.str(), Extractor.guessNext(c.jsonObject["input"]!!.str())) }
    @Test fun titleFromUrl() { for (c in cases("titleFromUrl")) assertEquals(c.jsonObject["input"]!!.str(), c.jsonObject["expected"]!!.str(), Extractor.titleFromUrl(c.jsonObject["input"]!!.str())) }

    @Test fun splitForSpeech() {
        for (c in cases("splitForSpeech")) {
            val o = c.jsonObject
            assertEquals(o["expected"]!!.jsonArray.map { it.str() }, TextTools.splitForSpeech(o["text"]!!.str(), o["max"]!!.jsonPrimitive.content.toInt()))
        }
    }

    @Test fun speechText() { for (c in cases("speechText")) assertEquals(c.jsonObject["input"]!!.str(), c.jsonObject["expected"]!!.str(), TextTools.speechText(c.jsonObject["input"]!!.str())) }

    @Test fun chapterNumber() {
        for (c in cases("chapterNumber")) {
            val o = c.jsonObject
            assertEquals(o["url"]!!.str() + " " + o["text"]!!.str(), o["expected"]!!.dbl(), ChapterList.chapterNumber(o["url"]!!.str(), o["text"]!!.str()))
        }
    }

    @Test fun urlForNumber() {
        for (c in cases("urlForNumber")) {
            val o = c.jsonObject
            assertEquals(o["url"]!!.str(), o["expected"]!!.str(), ChapterList.urlForNumber(o["url"]!!.str(), o["n"]!!.jsonPrimitive.content.toInt()))
        }
    }

    @Test fun pickChapters() {
        for (c in cases("pickChapters")) {
            val o = c.jsonObject
            val links = o["links"]!!.jsonArray.map { ChapterList.Link(it.jsonObject["href"]!!.str(), it.jsonObject["text"]!!.str()) }
            val got = ChapterList.pickChapters(links, o["key"]!!.str())
            val want = o["expected"]!!.jsonArray.map { ChapterList.ChapterItemForTest(it.jsonObject["num"]!!.dbl(), it.jsonObject["title"]!!.str(), it.jsonObject["url"]!!.str()) }
            assertEquals(o["name"]!!.str(), want, got.map { ChapterList.ChapterItemForTest(it.num, it.title, it.url) })
        }
    }

    private fun novels(a: JsonArray) = a.map {
        val o = it.jsonObject
        Novel(o["id"]!!.str(), o["title"]!!.str(), o["url"]!!.str(), o["key"]!!.str(), o["chapter"]!!.str(), o["updated"]!!.jsonPrimitive.longOrNull ?: 0L)
    }
    private fun chapters(a: JsonArray) = a.map {
        val o = it.jsonObject
        SavedChapter(o["title"]!!.str(), o["text"]!!.str(), o["url"]!!.str(), o["next"]!!.str())
    }

    @Test fun backupMerge() {
        for (c in cases("backupMerge")) {
            val o = c.jsonObject
            val l = o["local"]!!.jsonObject
            val local = LocalData(novels(l["novels"]!!.jsonArray), l["covers"]!!.jsonObject.mapValues { it.value.str() }, chapters(l["chapters"]!!.jsonArray), pronSet(l["pron"]!!.jsonObject))
            val b = o["backup"]!!.jsonObject
            val backup = CleanBackup(
                novels = b["novels"]!!.jsonArray.map {
                    val n = it.jsonObject
                    CleanNovel(n["id"]!!.str(), n["title"]!!.str(), n["url"]!!.str(), n["key"]!!.str(), n["chapter"]!!.str(), n["updated"]!!.jsonPrimitive.longOrNull ?: 0L, n["cover"]!!.strOrNull())
                },
                chapters = chapters(b["savedChapters"]!!.jsonArray), pron = pronSet(b["pronunciations"]!!.jsonObject),
                reading = null, settings = null, from = "", date = "",
            )
            val got = BackupCore.merge(local, backup)
            val e = o["expected"]!!.jsonObject
            assertEquals(novels(e["novels"]!!.jsonArray), got.novels.map { it.copy(coverTry = 0L) })
            assertEquals(e["covers"]!!.jsonObject.mapValues { it.value.str() }, got.covers)
            assertEquals(chapters(e["chapters"]!!.jsonArray), got.chapters)
            assertEquals(pronSet(e["pron"]!!.jsonObject), got.pron)
        }
    }
}
