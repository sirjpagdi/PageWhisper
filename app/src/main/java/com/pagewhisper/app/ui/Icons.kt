package com.pagewhisper.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Flat vector icons, drawn from the same path data as the web app. No emoji anywhere. */
object PwIcons {
    private class P(val d: String, val fill: Boolean = false)

    private fun circ(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private val defs: Map<String, List<P>> = mapOf(
        "play" to listOf(P("M8 5.6v12.8a1 1 0 0 0 1.52.85l10.2-6.4a1 1 0 0 0 0-1.7L9.52 4.75A1 1 0 0 0 8 5.6z", true)),
        "pause" to listOf(P("M6.3 5h4v14h-4zM13.7 5h4v14h-4z", true)),
        "prev" to listOf(P("M5 5h2.6v14H5z", true), P("M19 6.2v11.6a.9.9 0 0 1-1.42.74l-8.1-5.8a.9.9 0 0 1 0-1.48l8.1-5.8A.9.9 0 0 1 19 6.2z", true)),
        "next" to listOf(P("M16.4 5H19v14h-2.6z", true), P("M5 6.2v11.6a.9.9 0 0 0 1.42.74l8.1-5.8a.9.9 0 0 0 0-1.48l-8.1-5.8A.9.9 0 0 0 5 6.2z", true)),
        "stop" to listOf(P("M6 6h12v12H6z", true)),
        "lock" to listOf(P("M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5V13a2.5 2.5 0 0 1 2.5-2.5z"), P("M8 10.5V8a4 4 0 0 1 8 0v2.5"), P(circ(12f, 15.2f, 1.2f), true)),
        "moon" to listOf(P("M20 14.4A8 8 0 1 1 9.6 4a6.4 6.4 0 0 0 10.4 10.4z")),
        "fs" to listOf(P("M4 9V5.8A1.8 1.8 0 0 1 5.8 4H9M15 4h3.2A1.8 1.8 0 0 1 20 5.8V9M20 15v3.2a1.8 1.8 0 0 1-1.8 1.8H15M9 20H5.8A1.8 1.8 0 0 1 4 18.2V15")),
        "close" to listOf(P("M6 6l12 12M18 6L6 18")),
        "star" to listOf(P("M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z")),
        "star-fill" to listOf(P("M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z", true)),
        "plus" to listOf(P("M12 5v14M5 12h14")),
        "book" to listOf(P("M12 6.5C10.5 5 8 4.5 4 4.5v13c4 0 6.5.5 8 2 1.5-1.5 4-2 8-2v-13c-4 0-6.5.5-8 2zM12 6.5v13")),
        "list" to listOf(P("M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01")),
        "external" to listOf(P("M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")),
        "speak" to listOf(P("M4 9.5h3.2L12 5.5v13l-4.8-4H4z"), P("M15.5 9.2a4 4 0 0 1 0 5.6M18.2 6.6a8 8 0 0 1 0 10.8")),
        "sun" to listOf(P(circ(12f, 12f, 4f)), P("M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M18.5 5.5l-1.7 1.7M7.2 16.8l-1.7 1.7")),
        "tab-open" to listOf(P("M12 4v10M8 10.5l4 4 4-4M5 19h14")),
        "tab-library" to listOf(P("M5 4v16M10 4v16M15 5.5l4 14.5")),
        "tab-settings" to listOf(P("M4 6h8M17 6h3M4 12h3M12 12h8M4 18h10M19 18h1"), P(circ(14.5f, 6f, 2f)), P(circ(9.5f, 12f, 2f)), P(circ(16.5f, 18f, 2f))),
        "amb-off" to listOf(P("M4 9.5h3.2L12 5.5v13l-4.8-4H4z"), P("M16 9.5l4.5 5M20.5 9.5l-4.5 5")),
        "amb-rain" to listOf(P("M7 14a4 4 0 0 1-.4-7.98 5.5 5.5 0 0 1 10.6 1.1A3.4 3.4 0 0 1 17 14z"), P("M9 17.5l-.8 2M15 17.5l-.8 2")),
        "amb-heavy" to listOf(P("M7 13a4 4 0 0 1-.4-7.98 5.5 5.5 0 0 1 10.6 1.1A3.4 3.4 0 0 1 17 13z"), P("M7.5 16l-1.2 3.5M11 16l-1.2 3.5M14.5 16l-1.2 3.5M18 16l-1.2 3.5")),
        "amb-storm" to listOf(P("M7 13.5a4 4 0 0 1-.4-7.98 5.5 5.5 0 0 1 10.6 1.1A3.4 3.4 0 0 1 17 13.5z"), P("M12.8 15l-2.4 3.4h3.4l-2.2 3.6")),
        "amb-ocean" to listOf(P("M3 8.5c2 0 2.2-2 4.5-2S10 8.5 12 8.5s2.5-2 4.5-2S19 8.5 21 8.5M3 13.5c2 0 2.2-2 4.5-2s2.5 2 4.5 2 2.5-2 4.5-2 2.5 2 4.5 2M3 18.5c2 0 2.2-2 4.5-2s2.5 2 4.5 2 2.5-2 4.5-2 2.5 2 4.5 2")),
        "amb-insects" to listOf(P("M12 8.4a3.7 5.4 0 1 0 0 10.8 3.7 5.4 0 0 0 0-10.8z"), P(circ(12f, 6.4f, 1.9f)), P("M11 4.7L9.6 3.2M13 4.7l1.4-1.5M12 8.5v10.7M8.3 11.4H5M8.3 14.6H4.6M8.7 18l-3 2.2M15.7 11.4H19M15.7 14.6h3.8M15.3 18l3 2.2")),
        "amb-forest" to listOf(P("M12 3l4.6 6.5h-2.7L18 15.5H6l4.1-6H7.4z"), P("M12 15.5V21")),
        "amb-wind" to listOf(P("M3 8.5h10.5a2.6 2.6 0 1 0-2.5-3.3M3 12.5h14.5a2.6 2.6 0 1 1-2.5 3.3M3 16.5h6.5")),
        "amb-fire" to listOf(P("M12 3c.9 3.4 5.2 5.6 5.2 10.2a5.2 5.2 0 0 1-10.4 0c0-2.1 1-3.3 2.1-4.4.3 1.6 1 2.1 2 2.3C10.6 8.3 10.9 5.6 12 3z")),
        "amb-brown" to listOf(P("M3 12h2.6l1.5-4 2.4 9 3-13 2.5 11 1.5-3H21")),
    )

    private val cache = HashMap<String, ImageVector>()

    fun get(name: String): ImageVector = cache.getOrPut(name) { build(name) }

    private fun build(name: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (p in defs[name].orEmpty()) {
            val nodes = PathParser().parsePathString(p.d).toNodes()
            if (p.fill) b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            else b.addPath(pathData = nodes, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }
}

@Composable
fun PwIcon(name: String, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Icon(PwIcons.get(name), contentDescription = null, modifier = modifier.size(size), tint = tint)
}
