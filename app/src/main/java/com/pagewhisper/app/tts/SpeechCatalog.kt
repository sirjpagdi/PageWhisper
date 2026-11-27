package com.pagewhisper.app.tts

import com.pagewhisper.app.model.AppSettings

class StVoice(val sid: Int, val id: String, val label: String)
class StLang(val code: String, val name: String)

/**
 * The voices and languages of the Supertonic 3 model. Its voice file holds ten voices in a fixed order: speaker 0 to 4 are
 * the female voices F1 to F5, speaker 5 to 9 the male voices M1 to M5. The language is sent as a short code with each
 * request. See FEATURES.md ("Spec: Voices and engines").
 *
 * Ids used for hiding a voice or language from the lists: a Kokoro voice is its own id ("af_heart"), a Supertonic voice is
 * "st:<speaker number>", a Supertonic language is "lang:<code>".
 */
object Supertonic {
    val voices: List<StVoice> = listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5").mapIndexed { i, n ->
        StVoice(i, "st:$i", (if (n.startsWith("F")) "Female " else "Male ") + n.substring(1))
    }

    val languages: List<StLang> = listOf(
        "ar" to "Arabic", "bg" to "Bulgarian", "hr" to "Croatian", "cs" to "Czech", "da" to "Danish", "nl" to "Dutch",
        "en" to "English", "et" to "Estonian", "fi" to "Finnish", "fr" to "French", "de" to "German", "el" to "Greek",
        "hi" to "Hindi", "hu" to "Hungarian", "id" to "Indonesian", "it" to "Italian", "ja" to "Japanese", "ko" to "Korean",
        "lv" to "Latvian", "lt" to "Lithuanian", "pl" to "Polish", "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian",
        "sk" to "Slovak", "sl" to "Slovenian", "es" to "Spanish", "sv" to "Swedish", "tr" to "Turkish", "uk" to "Ukrainian",
        "vi" to "Vietnamese",
    ).map { StLang(it.first, it.second) }

    fun voiceLabel(sid: Int): String = voices.getOrNull(sid)?.label ?: "Voice $sid"
    fun languageName(code: String): String = languages.firstOrNull { it.code == code }?.name ?: code
    fun languageId(code: String) = "lang:$code"
}

/** The lists a person sees, without the voices and languages they chose to hide. */
object Visible {
    fun kokoro(hidden: Collection<String>): List<Voice> = Voices.all.filter { it.id !in hidden }
    fun supertonicVoices(hidden: Collection<String>): List<StVoice> = Supertonic.voices.filter { it.id !in hidden }
    fun languages(hidden: Collection<String>): List<StLang> = Supertonic.languages.filter { Supertonic.languageId(it.code) !in hidden }
}

/** What the engine is asked to speak with: which engine, which speaker, and (Supertonic) which language. */
class SpeechChoice(val engine: String, val sid: Int, val lang: String) {
    /** Part of the key of saved speech, so speech of one engine, voice style or language is never used for another. */
    val tag: String get() = if (engine == "supertonic") "s-$lang" else "k"
}

fun speechChoiceOf(s: AppSettings): SpeechChoice =
    if (s.engine == "supertonic") SpeechChoice("supertonic", s.stVoice.coerceIn(0, 9), s.stLang)
    else SpeechChoice("kokoro", Voices.sidOf(s.voice), "")
