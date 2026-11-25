package com.pagewhisper.app.model

import kotlinx.serialization.Serializable

/** One pronunciation rule. See FEATURES.md ("Spec: Pronunciation rules"). */
@Serializable
data class PronRule(
    val from: String,
    val to: String,
    val whole: Boolean = true,
    val matchCase: Boolean = false,
)

@Serializable
data class PronSet(
    val global: List<PronRule> = emptyList(),
    val novels: Map<String, List<PronRule>> = emptyMap(),
) {
    val totalRules: Int get() = global.size + novels.values.sumOf { it.size }
}

@Serializable
data class Novel(
    val id: String,
    val title: String,
    val url: String,
    val key: String,
    val chapter: String = "",
    val updated: Long = 0L,
    val coverTry: Long = 0L,
)

@Serializable
data class SavedChapter(
    val title: String,
    val text: String,
    val url: String = "",
    val next: String = "",
)

/** Reading style: keys, ranges and defaults are listed in FEATURES.md. */
@Serializable
data class ReadingStyle(
    val font: String = "serif",
    val size: Int = 19,
    val line: Double = 1.8,
    val para: Double = 1.05,
    val pad: Int = 4,
    val ls: Double = 0.0,
    val align: String = "left",
    val weight: Int = 400,
    val hyph: Boolean = false,
    val theme: String = "dark",
    val accent: String = "auto",
    val accentTone: Int = 0,
    val hl: String = "violet",
    val hlStyle: String = "fill",
) {
    fun sanitized(): ReadingStyle = ReadingStyle(
        font = if (font in FONT_IDS) font else "serif",
        size = size.coerceIn(14, 34),
        line = line.coerceIn(1.3, 2.4),
        para = para.coerceIn(0.2, 2.0),
        pad = pad.coerceIn(0, 40),
        ls = ls.coerceIn(0.0, 0.12),
        align = if (align in listOf("left", "justify", "center")) align else "left",
        weight = if (weight == 700) 700 else 400,
        hyph = hyph,
        theme = if (theme in THEME_IDS) theme else "dark",
        accent = if (accent in ACCENT_IDS) accent else "auto",
        accentTone = accentTone.coerceIn(-35, 35),
        hl = if (hl in HIGHLIGHT_IDS) hl else "violet",
        hlStyle = if (hlStyle in listOf("fill", "underline", "outline")) hlStyle else "fill",
    )

    companion object {
        val FONT_IDS = listOf(
            "serif", "sans", "mono", "literata", "merriweather", "lora", "atkinson", "lexend", "dyslexic",
            "ebgaramond", "crimsonpro", "sourceserif4", "montserrat",
        )
        val THEME_IDS = listOf("dark", "amoled", "sepia", "light")
        val ACCENT_IDS = listOf("auto", "purple", "blue", "teal", "green", "rose", "orange")
        val HIGHLIGHT_IDS = listOf("violet", "gold", "green", "blue", "pink", "orange")
    }
}

@Serializable
data class AppSettings(
    val voice: String = "af_heart",
    val rate: Double = 1.0,
    val chunk: Int = 280,
    val ambVol: Double = 0.35,
    val auto: Boolean = false,
    val awake: Boolean = false,
    val lockAwake: Boolean = true,
    val ambOnly: Boolean = true,
    val amb: String = "off",
    val brightness: Int = 100,
    val sleepLast: Int = 0,
    val heat: String = "cool",
    val voiceModel: String = "standard",
) {
    fun sanitized(): AppSettings = AppSettings(
        voice = voice,
        rate = rate.coerceIn(0.7, 1.4),
        chunk = if (chunk in listOf(160, 280, 380)) chunk else 280,
        ambVol = ambVol.coerceIn(0.0, 1.0),
        auto = auto, awake = awake, lockAwake = lockAwake, ambOnly = ambOnly,
        amb = if (amb in AMBIENT_KEYS) amb else "off",
        brightness = brightness.coerceIn(10, 100),
        sleepLast = if (sleepLast in 1..600) sleepLast else 0,
        heat = if (heat in HEAT_MODES) heat else "cool",
        voiceModel = if (voiceModel in listOf("standard", "light")) voiceModel else "standard",
    )

    companion object {
        val HEAT_MODES = listOf("fast", "balanced", "cool")
        val AMBIENT_KEYS = listOf("off", "rain", "heavy", "storm", "ocean", "insects", "forest", "wind", "fire", "brown")
    }
}

/** A sentence group that is spoken and highlighted as one unit. */
data class Section(
    val index: Int,
    val para: Int,
    val text: String,
    val speech: String,
)
