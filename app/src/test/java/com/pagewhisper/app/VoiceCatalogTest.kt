package com.pagewhisper.app

import com.pagewhisper.app.model.AppSettings
import com.pagewhisper.app.tts.Supertonic
import com.pagewhisper.app.tts.Visible
import com.pagewhisper.app.tts.Voices
import com.pagewhisper.app.tts.speechChoiceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The voice lists, hiding voices and languages, and what the engine is asked to speak with. */
class VoiceCatalogTest {
    @Test fun theCatalogsHaveTheDocumentedSizes() {
        assertEquals(28, Voices.all.size)
        assertEquals(10, Supertonic.voices.size)
        assertEquals(31, Supertonic.languages.size)
        assertEquals(31, Supertonic.languages.map { it.code }.toSet().size)
        assertEquals("Female 1", Supertonic.voices[0].label)
        assertEquals("Male 5", Supertonic.voices[9].label)
    }

    @Test fun hiddenVoicesAndLanguagesLeaveTheLists() {
        val hidden = listOf("af_heart", "st:3", "lang:de")
        assertFalse(Visible.kokoro(hidden).any { it.id == "af_heart" })
        assertEquals(27, Visible.kokoro(hidden).size)
        assertEquals(9, Visible.supertonicVoices(hidden).size)
        assertFalse(Visible.languages(hidden).any { it.code == "de" })
        assertEquals(30, Visible.languages(hidden).size)
    }

    @Test fun theChoiceFollowsTheEngine() {
        val kokoro = speechChoiceOf(AppSettings())
        assertEquals("kokoro", kokoro.engine)
        assertEquals(3, kokoro.sid) // af_heart
        assertEquals("k", kokoro.tag)
        val st = speechChoiceOf(AppSettings(engine = "supertonic", stVoice = 6, stLang = "fr"))
        assertEquals(6, st.sid)
        assertEquals("fr", st.lang)
        assertEquals("s-fr", st.tag)
    }

    @Test fun badSettingsAreRepaired() {
        val fixed = AppSettings(engine = "other", stVoice = 99, stLang = "zz", hiddenVoices = listOf("a", "a", "b")).sanitized()
        assertEquals("kokoro", fixed.engine)
        assertEquals(9, fixed.stVoice)
        assertEquals("en", fixed.stLang)
        assertEquals(listOf("a", "b"), fixed.hiddenVoices)
        assertTrue(AppSettings().voice.isNotEmpty())
    }
}
