package com.pagewhisper.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.ReadingStyle
import com.pagewhisper.app.model.Section
import com.pagewhisper.app.playback.Playback

/** The text style for the chosen reading settings (used by the reader and by the preview in the style sheet). */
fun readingTextStyle(ctx: Context, r: ReadingStyle, color: Color): TextStyle = TextStyle(
    fontFamily = fontFamilyFor(ctx, r.font),
    fontSize = r.size.sp,
    lineHeight = (r.size * r.line).sp,
    textAlign = when (r.align) { "justify" -> TextAlign.Justify; "center" -> TextAlign.Center; else -> TextAlign.Start },
    letterSpacing = r.ls.em,
    fontWeight = if (r.weight == 700) FontWeight.Bold else FontWeight.Normal,
    color = color,
    hyphens = if (r.hyph) Hyphens.Auto else Hyphens.None,
    lineBreak = LineBreak.Paragraph,
)

fun sentenceHighlightStyle(style: String, color: Color, fontWeight: FontWeight?): SpanStyle {
    val highlight = when (style) {
        "underline" -> SpanStyle(textDecoration = TextDecoration.Underline, background = color.copy(alpha = 0.12f))
        "outline" -> SpanStyle(background = color.copy(alpha = 0.10f))
        else -> SpanStyle(background = color.copy(alpha = 0.34f))
    }
    return highlight.copy(fontWeight = fontWeight)
}

@Composable
fun ReaderScreen(vm: AppViewModel, listState: LazyListState) {
    val c = LocalPw.current
    val ctx = LocalContext.current
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val reading by Repo.reading.collectAsStateWithLifecycle()
    val settings by Repo.settings.collectAsStateWithLifecycle()
    val saved by Repo.chapters.collectAsStateWithLifecycle()
    val fs by vm.fullscreen.collectAsStateWithLifecycle()
    val ui by Playback.ui.collectAsStateWithLifecycle()
    val ch = chapter

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // header: title, pronunciation and save
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(ch?.title ?: "Your reading space", fontSize = if (fs) 16.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = c.text)
                if (!fs) Text(ch?.url?.ifEmpty { "Imported / pasted text" } ?: "Load a chapter to begin", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted)
            }
            IconSquare("speak", "Pronunciation fixes", ch != null) { vm.openSheet(Sheet.Pron(vm.currentNovelKey().isNotEmpty())) }
            val isSaved = ch != null && saved.any { it.url == ch.url && it.title == ch.title }
            IconSquare(if (isSaved) "star-fill" else "star", "Save chapter", ch != null) { vm.saveCurrentChapter() }
        }
        // toolbar
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolBtn(Modifier.weight(1f), text = "Aa", onClick = { vm.openSheet(Sheet.Reading) })
            ToolBtn(Modifier.weight(1f), text = "‹ Ch", enabled = ch != null && vm.hasPrev(), onClick = { vm.prev() })
            ToolBtn(Modifier.weight(1f), text = "Ch ›", enabled = ch != null, onClick = { vm.next() })
            ToolBtn(Modifier.weight(1f), text = "Auto", selected = settings.auto, onClick = { vm.update { it.copy(auto = !it.auto) }; vm.toast("Auto-play next chapter: " + if (!settings.auto) "on" else "off") })
            if (fs) {
                ToolBtn(Modifier.weight(1f), icon = "sun", selected = settings.brightness < 100, onClick = { vm.openSheet(Sheet.Bright) })
                ToolBtn(Modifier.weight(1f), icon = "lock", onClick = { vm.lock() })
            }
            ToolBtn(Modifier.weight(1f), icon = if (fs) "close" else "fs", onClick = { if (fs) vm.exitFullscreen() else vm.enterFullscreen() })
        }
        if (ch == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing loaded yet.\nFetch a chapter by link, paste its text, or open a saved one.", color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Box(Modifier.padding(top = 16.dp)) { PrimaryButton("Open a chapter", { vm.tab.value = Tab.OPEN }) }
            }
        } else {
            val paras = remember(sections) { sections.filter { it.para >= 0 }.groupBy { it.para }.toSortedMap().values.toList() }
            val style = remember(reading, c) { readingTextStyle(ctx, reading, c.text) }
            val hl = highlightColor(reading.hl)
            LaunchedEffect(ch) { listState.scrollToItem(0) }
            LaunchedEffect(ui.index, ui.playing) { // follow the narration
                if (ui.playing) sections.getOrNull(ui.index)?.let { listState.animateScrollToItem(it.para.coerceAtLeast(0), -150) }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = reading.pad.dp, end = reading.pad.dp, top = 8.dp, bottom = 48.dp)) {
                itemsIndexed(paras) { _, group ->
                    Paragraph(group, if (ui.playing || ui.index > 0) ui.index else -1, style, hl, reading.hlStyle, (reading.para * reading.size).dp) { Playback.play(it) }
                }
            }
        }
    }
}

@Composable
private fun Paragraph(group: List<Section>, active: Int, style: TextStyle, hl: Color, hlStyle: String, spacing: Dp, onTap: (Int) -> Unit) {
    val starts = remember(group) { var p = 0; group.map { val s = p; p += it.text.length + 1; s } }
    val annotated = remember(group, active, hl, hlStyle) {
        buildAnnotatedString {
            for (s in group) {
                val on = s.index == active
                val span = if (!on) null else sentenceHighlightStyle(hlStyle, hl, style.fontWeight)
                if (span != null) withStyle(span) { append(s.text) } else append(s.text)
                append(" ")
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated, style = style, onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = spacing).drawBehind {
            if (hlStyle == "outline" && active >= 0) {
                val sectionIndex = group.indexOfFirst { it.index == active }
                val start = starts.getOrNull(sectionIndex)
                val section = group.getOrNull(sectionIndex)
                val textLayout = layout
                if (start != null && section != null && textLayout != null) {
                    val lineRects = HashMap<Int, Rect>()
                    for (offset in start until start + section.text.length) {
                        val box = textLayout.getBoundingBox(offset)
                        val line = textLayout.getLineForOffset(offset)
                        val rect = Rect(box.left, box.top, box.right, box.bottom)
                        val existing = lineRects[line]
                        lineRects[line] = if (existing == null) rect else Rect(
                            minOf(existing.left, rect.left),
                            minOf(existing.top, rect.top),
                            maxOf(existing.right, rect.right),
                            maxOf(existing.bottom, rect.bottom),
                        )
                    }
                    val radius = 3.dp.toPx()
                    for (rect in lineRects.values) {
                        drawRoundRect(
                            color = hl,
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width, rect.height),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }.pointerInput(group) {
            detectTapGestures { pos -> // tap a sentence to start from there
                val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                val i = starts.indexOfLast { it <= off }
                if (i >= 0) onTap(group[i].index)
            }
        },
    )
}

@Composable
private fun IconSquare(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalPw.current
    Box(
        Modifier.padding(start = 8.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(c.panel2).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { PwIcon(icon, tint = c.text, size = 22.dp) }
}

@Composable
private fun ToolBtn(modifier: Modifier, text: String? = null, icon: String? = null, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val c = LocalPw.current
    Box(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.accent else c.panel2)
            .alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = if (selected) accentContentColor(c.accent) else c.text
        if (icon != null) PwIcon(icon, tint = contentColor, size = 20.dp)
        else Text(text.orEmpty(), fontSize = 13.sp, color = contentColor, maxLines = 1)
    }
}
