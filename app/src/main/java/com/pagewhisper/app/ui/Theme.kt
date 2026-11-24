package com.pagewhisper.app.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** The four themes. Colours are identical to the web app. */
data class PwColors(
    val bg: Color, val panel: Color, val panel2: Color, val line: Color, val text: Color, val muted: Color,
    val accent: Color, val accent2: Color, val input: Color, val red: Color, val green: Color, val light: Boolean,
)

private val darkBase = PwColors(
    bg = Color(0xFF0E0E16), panel = Color(0xFF171722), panel2 = Color(0xFF212131), line = Color(0xFF2E2E45),
    text = Color(0xFFECEBF5), muted = Color(0xFFA1A0B8), accent = Color(0xFF8B5CF6), accent2 = Color(0xFFC4B5FD),
    input = Color(0xFF0F0F19), red = Color(0xFFFF9A9A), green = Color(0xFF9ED6B0), light = false,
)

fun pwColors(theme: String): PwColors = when (theme) {
    "amoled" -> darkBase.copy(bg = Color.Black, panel = Color(0xFF0B0B10), panel2 = Color(0xFF16161F), line = Color(0xFF25253A), text = Color(0xFFE8E8F0), muted = Color(0xFF9A99B0), input = Color.Black)
    "sepia" -> PwColors(
        bg = Color(0xFFF4ECD8), panel = Color(0xFFEBE0C6), panel2 = Color(0xFFE2D5B5), line = Color(0xFFCDBD96), text = Color(0xFF4A3B27), muted = Color(0xFF7A6A4F),
        accent = Color(0xFF9A5B2E), accent2 = Color(0xFF7A4A1B), input = Color(0xFFF8F1DF), red = Color(0xFFA14A3A), green = Color(0xFF4F7A4A), light = true,
    )
    "light" -> PwColors(
        bg = Color(0xFFFAFAFA), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFEEF0F4), line = Color(0xFFD8DBE3), text = Color(0xFF1D1D27), muted = Color(0xFF626578),
        accent = Color(0xFF6D3FE0), accent2 = Color(0xFF5A2FC4), input = Color(0xFFF5F6F9), red = Color(0xFFB3392F), green = Color(0xFF2F7D4F), light = true,
    )
    else -> darkBase
}

/** Accent colours: [dark/amoled, light, sepia] x [main, secondary]. Same table as the web app (ACCENTS). */
private class AccentSet(val dark: Pair<Color, Color>, val light: Pair<Color, Color>, val sepia: Pair<Color, Color>)

private fun pair(a: Long, b: Long) = Color(a) to Color(b)

private val ACCENT_TABLE = mapOf(
    "purple" to AccentSet(pair(0xFF8B5CF6, 0xFFC4B5FD), pair(0xFF6D3FE0, 0xFF5A2FC4), pair(0xFF7740A0, 0xFF66318D)),
    "blue" to AccentSet(pair(0xFF3B82F6, 0xFF93C5FD), pair(0xFF2563EB, 0xFF1D4ED8), pair(0xFF315F9B, 0xFF244C80)),
    "teal" to AccentSet(pair(0xFF14B8A6, 0xFF5EEAD4), pair(0xFF0F766E, 0xFF115E59), pair(0xFF276C64, 0xFF20574F)),
    "green" to AccentSet(pair(0xFF22C55E, 0xFF86EFAC), pair(0xFF15803D, 0xFF166534), pair(0xFF4F7839, 0xFF3D602D)),
    "rose" to AccentSet(pair(0xFFF43F5E, 0xFFFDA4AF), pair(0xFFBE123C, 0xFF9F1239), pair(0xFFA13D50, 0xFF843044)),
    "orange" to AccentSet(pair(0xFFF97316, 0xFFFDBA74), pair(0xFFC2410C, 0xFF9A3412), pair(0xFF9A5B2E, 0xFF7A4A1B)),
)

/** Preview colour of an accent for a theme (the dots in the reading style panel). */
fun accentSwatch(id: String, theme: String): Color {
    val set = ACCENT_TABLE[id] ?: return pwColors(theme).accent
    return when (theme) { "sepia" -> set.sepia.first; "light" -> set.light.first; else -> set.dark.first }
}

/** Lightness shift in HSL (delta in percent points, -35..35), keeping hue and saturation. Same maths as shadeAccent() on the web. */
fun shadeAccent(color: Color, delta: Int): Color {
    if (delta == 0) return color
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val diff = max - min
    var h = 0f; var s = 0f; var l = (max + min) / 2f
    if (diff > 0f) {
        s = diff / (1f - Math.abs(2f * l - 1f))
        h = when (max) {
            r -> ((g - b) / diff) % 6f
            g -> (b - r) / diff + 2f
            else -> (r - g) / diff + 4f
        }
        h = (h * 60f + 360f) % 360f
    }
    l = (l + delta / 100f).coerceIn(0.12f, 0.88f)
    val c = (1f - Math.abs(2f * l - 1f)) * s
    val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

/** Theme colours with the reader's chosen accent colour and shade applied ("auto" keeps the theme's own accent). */
fun pwColors(theme: String, accent: String, tone: Int): PwColors {
    val base = pwColors(theme)
    val set = ACCENT_TABLE[accent]
    val (main, second) = when {
        set == null -> base.accent to base.accent2
        theme == "sepia" -> set.sepia
        theme == "light" -> set.light
        else -> set.dark
    }
    return base.copy(accent = shadeAccent(main, tone), accent2 = shadeAccent(second, tone))
}

val LocalPw = staticCompositionLocalOf { pwColors("dark") }

fun highlightColor(id: String): Color = when (id) {
    "gold" -> Color(0xFFEAB308)
    "green" -> Color(0xFF22C55E)
    "blue" -> Color(0xFF3B82F6)
    "pink" -> Color(0xFFEC4899)
    "orange" -> Color(0xFFF97316)
    else -> Color(0xFF8B5CF6)
}

@Composable
fun PageWhisperTheme(theme: String, accent: String = "auto", accentTone: Int = 0, content: @Composable () -> Unit) {
    val c = pwColors(theme, accent, accentTone)
    val scheme = if (c.light) {
        lightColorScheme(primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.text, surface = c.panel, onSurface = c.text,
            surfaceVariant = c.panel2, onSurfaceVariant = c.muted, outline = c.line, error = c.red)
    } else {
        darkColorScheme(primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.text, surface = c.panel, onSurface = c.text,
            surfaceVariant = c.panel2, onSurfaceVariant = c.muted, outline = c.line, error = c.red)
    }
    CompositionLocalProvider(LocalPw provides c) { MaterialTheme(colorScheme = scheme, content = content) }
}

/**
 * Serif, Sans and Mono are built in. The other fonts are used if their files are in app/src/main/res/font
 * (see README.md, "Fonts"); otherwise a similar built-in font is used.
 */
fun fontFamilyFor(ctx: Context, id: String): FontFamily {
    when (id) {
        "sans" -> return FontFamily.SansSerif
        "mono" -> return FontFamily.Monospace
        "serif" -> return FontFamily.Serif
    }
    val res = ctx.resources
    val regular = res.getIdentifier("font_$id", "font", ctx.packageName)
    if (regular == 0) return if (id in listOf("atkinson", "lexend", "dyslexic")) FontFamily.SansSerif else FontFamily.Serif
    val bold = res.getIdentifier("font_${id}_bold", "font", ctx.packageName)
    return if (bold != 0) FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold)) else FontFamily(Font(regular, FontWeight.Normal))
}
