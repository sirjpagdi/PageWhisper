package com.pagewhisper.app.tts

/**
 * The 28 English Kokoro voices. `sid` is the speaker number inside the multilingual model's voices.bin,
 * which is sorted by voice name. Check it against the model's page on k2-fsa.github.io if a voice sounds wrong.
 */
data class Voice(val id: String, val label: String, val sid: Int)

object Voices {
    private val order = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
        "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
    )

    private val labels = linkedMapOf(
        "af_heart" to "American · Heart (female)", "af_bella" to "American · Bella (female)", "af_nicole" to "American · Nicole (female)",
        "af_sarah" to "American · Sarah (female)", "af_nova" to "American · Nova (female)", "af_sky" to "American · Sky (female)",
        "af_kore" to "American · Kore (female)", "af_aoede" to "American · Aoede (female)", "af_alloy" to "American · Alloy (female)",
        "af_jessica" to "American · Jessica (female)", "af_river" to "American · River (female)",
        "am_michael" to "American · Michael (male)", "am_fenrir" to "American · Fenrir (male)", "am_puck" to "American · Puck (male)",
        "am_echo" to "American · Echo (male)", "am_eric" to "American · Eric (male)", "am_liam" to "American · Liam (male)",
        "am_onyx" to "American · Onyx (male)", "am_adam" to "American · Adam (male)", "am_santa" to "American · Santa (male)",
        "bf_emma" to "British · Emma (female)", "bf_isabella" to "British · Isabella (female)", "bf_alice" to "British · Alice (female)",
        "bf_lily" to "British · Lily (female)", "bm_george" to "British · George (male)", "bm_fable" to "British · Fable (male)",
        "bm_lewis" to "British · Lewis (male)", "bm_daniel" to "British · Daniel (male)",
    )

    val all: List<Voice> = labels.map { (id, label) -> Voice(id, label, order.indexOf(id).coerceAtLeast(0)) }

    fun sidOf(id: String): Int = order.indexOf(id).let { if (it >= 0) it else 3 }
    fun labelOf(id: String): String = labels[id] ?: id
}
