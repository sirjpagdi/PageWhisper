package com.pagewhisper.app.ui

import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.PronRule
import com.pagewhisper.app.model.ReadingStyle
import com.pagewhisper.app.playback.Playback
import com.pagewhisper.app.text.ChapterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

fun fmtClock(ms: Long): String {
    val s = max(0L, ceil(ms / 1000.0).toLong())
    val h = s / 3600; val m = s % 3600 / 60; val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheets(vm: AppViewModel) {
    val c = LocalPw.current
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val s = sheet ?: return
    key(s) {
        ModalBottomSheet(
            onDismissRequest = { vm.closeSheet() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.panel, contentColor = c.text,
        ) {
            when (s) {
                Sheet.Reading -> ReadingSheet(vm)
                Sheet.Sleep -> SleepSheet(vm)
                Sheet.Bright -> BrightSheet(vm)
                is Sheet.Pron -> PronSheet(vm, s.novelScope)
                is Sheet.Cover -> CoverSheet(vm, s.id)
                is Sheet.Toc -> TocSheet(vm, s.id)
                Sheet.Restore -> RestoreSheet(vm)
            }
        }
    }
}

private val FONTS = listOf(
    "serif" to "Serif", "sans" to "Sans", "mono" to "Mono", "literata" to "Literata", "merriweather" to "Merriweather",
    "lora" to "Lora", "atkinson" to "Atkinson Hyperlegible", "lexend" to "Lexend", "dyslexic" to "OpenDyslexic",
    "ebgaramond" to "EB Garamond", "crimsonpro" to "Crimson Pro", "sourceserif4" to "Source Serif 4", "montserrat" to "Montserrat",
)

// ======================= reading style =======================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingSheet(vm: AppViewModel) {
    val c = LocalPw.current
    val ctx = LocalContext.current
    val r by Repo.reading.collectAsStateWithLifecycle()
    fun upd(f: (ReadingStyle) -> ReadingStyle) = Repo.updateReading(f)
    Column(Modifier.fillMaxHeight(0.9f).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SheetHeader("Reading style", onDone = { vm.closeSheet() }, extra = { SoftButton("Reset", { upd { ReadingStyle() } }) })
        val hl = highlightColor(r.hl)
        val previewHighlight = "Lanterns swayed above the narrow streets, and the smell of salt drifted up from the docks."
        val previewText = "The rain had been falling over the old harbor town for three days. $previewHighlight Mira pulled her coat tighter and stepped into the lamplight."
        var previewLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            buildAnnotatedString {
                append("The rain had been falling over the old harbor town for three days. ")
                withStyle(sentenceHighlightStyle(r.hlStyle, hl, if (r.weight == 700) FontWeight.Bold else FontWeight.Normal)) {
                    append(previewHighlight)
                }
                append(" Mira pulled her coat tighter and stepped into the lamplight.")
            },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(12.dp))
                .padding(horizontal = (r.pad + 10).dp, vertical = 8.dp)
                .then(when (r.hlStyle) {
                    "outline" -> Modifier.outlineTextRange(previewLayout, previewText.indexOf(previewHighlight), previewText.indexOf(previewHighlight) + previewHighlight.length, hl)
                    "underline" -> Modifier.underlineTextRange(previewLayout, previewText.indexOf(previewHighlight), previewText.indexOf(previewHighlight) + previewHighlight.length, hl)
                    else -> Modifier
                }),
            style = readingTextStyle(ctx, r, c.text), maxLines = 6, overflow = TextOverflow.Ellipsis,
            onTextLayout = { previewLayout = it },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label("Theme"); ThemeSwatches(r.theme) { id -> upd { it.copy(theme = id) } }
            Label("Accent colour")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in ReadingStyle.ACCENT_IDS) {
                    val swatch = shadeAccent(accentSwatch(id, r.theme), r.accentTone)
                    Box(Modifier.size(40.dp).clip(CircleShape).background(swatch).border(if (id == r.accent) 3.dp else 1.dp, if (id == r.accent) c.accent2 else c.line, CircleShape).clickable { upd { it.copy(accent = id) } })
                }
            }
            SliderRow("Accent shade (darker / lighter)", r.accentTone.toFloat(), -35f..35f, 69, if (r.accentTone == 0) "Default" else (if (r.accentTone > 0) "+" else "") + "${r.accentTone}%") { v -> upd { it.copy(accentTone = Math.round(v)) } }
            Hint("Theme default uses purple for Dark, AMOLED and Light, and deep orange for Sepia. Choose a colour to use it on every theme.")
            Label("Font")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                for ((id, name) in FONTS) SoftButton(name, { upd { it.copy(font = id) } }, Modifier.weight(1f), selected = r.font == id)
            }
            SliderRow("Text size", r.size.toFloat(), 14f..34f, 19, "${r.size} px") { v -> upd { it.copy(size = v.toInt()) } }
            SliderRow("Line spacing", r.line.toFloat(), 1.3f..2.4f, 21, "%.2f".format(r.line)) { v -> upd { it.copy(line = Math.round(v * 20) / 20.0) } }
            SliderRow("Paragraph spacing", r.para.toFloat(), 0.2f..2f, 17, "%.1f em".format(r.para)) { v -> upd { it.copy(para = Math.round(v * 10) / 10.0) } }
            SliderRow("Side margins", r.pad.toFloat(), 0f..40f, 19, "${r.pad} px") { v -> upd { it.copy(pad = (v / 2).toInt() * 2) } }
            SliderRow("Letter spacing", r.ls.toFloat(), 0f..0.12f, 11, "%.2f em".format(r.ls)) { v -> upd { it.copy(ls = Math.round(v * 100) / 100.0) } }
            Label("Alignment"); Segmented(listOf("left" to "Left", "justify" to "Justify", "center" to "Center"), r.align) { v -> upd { it.copy(align = v) } }
            Label("Text weight"); Segmented(listOf("400" to "Regular", "700" to "Bold"), r.weight.toString()) { v -> upd { it.copy(weight = v.toInt()) } }
            CheckRow("Hyphenate long words", r.hyph) { v -> upd { it.copy(hyph = v) } }
            Label("Highlight colour")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in ReadingStyle.HIGHLIGHT_IDS) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(highlightColor(id)).border(if (id == r.hl) 3.dp else 1.dp, if (id == r.hl) c.accent2 else c.line, CircleShape).clickable { upd { it.copy(hl = id) } })
                }
            }
            Label("Highlight style"); Segmented(listOf("fill" to "Fill", "underline" to "Underline", "outline" to "Outline"), r.hlStyle) { v -> upd { it.copy(hlStyle = v) } }
        }
    }
}

// ======================= sleep timer =======================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepSheet(vm: AppViewModel) {
    val c = LocalPw.current
    val ui by Playback.ui.collectAsStateWithLifecycle()
    val s by Repo.settings.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf(if (s.sleepLast > 0) s.sleepLast.toString() else "") }
    val sl = ui.sleep
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Sleep timer", onDone = { vm.closeSheet() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sl.mode != "off") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (sl.mode == "chapter") "End of chapter" else fmtClock(sl.remainingMs) + " left", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sl.mode == "time") SoftButton("+10 min", { Playback.addSleepMinutes(10); vm.toast("Added 10 minutes.") })
                        SoftButton("Turn off", { Playback.cancelSleep(); vm.toast("Sleep timer off."); vm.closeSheet() })
                    }
                }
            }
            Label("Stop after")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                for (m in listOf(5, 10, 15, 30, 45, 60, 90)) {
                    SoftButton("$m min", { vm.startSleep(m); vm.closeSheet() }, Modifier.weight(1f), selected = sl.mode == "time" && sl.totalMs == m * 60_000L)
                }
                SoftButton("End of chapter", { Playback.startSleepChapter(); vm.toast("Sleep timer: stops at the end of this chapter."); vm.closeSheet() }, Modifier.weight(2f), selected = sl.mode == "chapter")
            }
            Label("Custom (minutes)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("e.g. 20") })
                PrimaryButton("Start", { val m = custom.toIntOrNull(); if (m == null || m < 1) vm.toast("Enter the number of minutes, for example 20.") else { vm.startSleep(m); vm.closeSheet() } })
            }
            Hint("The volume fades out over the last 30 seconds, then playback pauses where you stopped, so you can carry on from the same sentence.")
        }
    }
}

// ======================= brightness =======================
@Composable
private fun BrightSheet(vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val canWrite = remember(context) { mutableStateOf(Settings.System.canWrite(context)) }
    val brightness = remember(context) { mutableStateOf(readSystemBrightness(context)) }
    val adaptive = remember(context) { mutableStateOf(isAdaptiveBrightness(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val resolver = context.contentResolver
        fun refresh() {
            canWrite.value = Settings.System.canWrite(context)
            brightness.value = readSystemBrightness(context)
            adaptive.value = isAdaptiveBrightness(context)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            observer,
        )
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            resolver.unregisterContentObserver(observer)
        }
    }

    fun setSystemBrightness(percent: Int) {
        if (!Settings.System.canWrite(context)) {
            canWrite.value = false
            vm.toast("Allow PageWhisper to modify system settings to change brightness.")
            return
        }
        try {
            val resolver = context.contentResolver
            val mode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val value = (percent.coerceIn(1, 100) * 255f / 100f).roundToInt().coerceIn(1, 255)
            if (!Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)) {
                vm.toast("Android could not update system brightness.")
                return
            }
            if (mode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL &&
                !Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            ) {
                vm.toast("Android could not switch to manual brightness.")
                return
            }
            brightness.value = (value * 100f / 255f).roundToInt().coerceIn(1, 100)
            adaptive.value = false
        } catch (_: SecurityException) {
            canWrite.value = Settings.System.canWrite(context)
            vm.toast("Allow PageWhisper to modify system settings to change brightness.")
        }
    }

    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Brightness", onDone = { vm.closeSheet() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canWrite.value) {
                SliderRow("System brightness", brightness.value.toFloat(), 1f..100f, 98, "${brightness.value}%") {
                    setSystemBrightness(it.toInt())
                }
                Segmented(
                    listOf("25" to "25%", "50" to "50%", "75" to "75%", "100" to "100%"),
                    brightness.value.toString().takeIf { it in listOf("25", "50", "75", "100") } ?: "",
                ) { setSystemBrightness(it.toInt()) }
                Hint(
                    if (adaptive.value) {
                        "Moving this slider turns off adaptive brightness and sets a manual system level. It stays in sync with your phone's brightness controls."
                    } else {
                        "This changes system brightness. The phone's brightness controls and this slider stay in sync."
                    },
                )
            } else {
                PrimaryButton("Allow system brightness control", {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        vm.toast("System brightness access isn't available on this device.")
                    }
                }, Modifier.fillMaxWidth())
                Hint("Android requires this one-time access so PageWhisper and your phone's brightness controls can stay in sync.")
            }
        }
    }
}

private fun readSystemBrightness(context: android.content.Context): Int {
    val value = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128,
    )
    return (value * 100f / 255f).roundToInt().coerceIn(1, 100)
}

private fun isAdaptiveBrightness(context: android.content.Context): Boolean =
    Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

// ======================= pronunciation =======================
@Composable
private fun PronSheet(vm: AppViewModel, startNovel: Boolean) {
    val pron by Repo.pron.collectAsStateWithLifecycle()
    val status by vm.pronStatus.collectAsStateWithLifecycle()
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    val key = vm.currentNovelKey()
    var novelScope by remember { mutableStateOf(startNovel && key.isNotEmpty()) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var whole by remember { mutableStateOf(true) }
    var matchCase by remember { mutableStateOf(false) }
    val rules = if (novelScope) pron.novels[key].orEmpty() else pron.global
    val novelTitle = Repo.novelFor(chapter?.url.orEmpty())?.title ?: key
    Column(Modifier.fillMaxHeight(0.9f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Pronunciation", onDone = { vm.closeSheet() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoftButton("All novels", { novelScope = false }, Modifier.weight(1f), selected = !novelScope)
                SoftButton("This novel", { novelScope = true }, Modifier.weight(1f), enabled = key.isNotEmpty(), selected = novelScope)
            }
            Hint(if (novelScope) "Only for $novelTitle." else if (key.isNotEmpty()) "Applies to every novel." else "Applies to every novel. Load a chapter from a link to make rules for one novel only.")
            OutlinedTextField(from, { from = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Written as") }, singleLine = true, placeholder = { Text("Longxuan") })
            OutlinedTextField(to, { to = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Say it as") }, singleLine = true, placeholder = { Text("Long shwen") })
            CheckRow("Whole word only", whole) { whole = it }
            CheckRow("Match upper and lower case", matchCase) { matchCase = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Listen", { vm.previewSpeech(to.ifBlank { from }) }, Modifier.weight(1f), icon = "speak")
                PrimaryButton("Add rule", { if (from.isBlank()) vm.pronStatus.value = Status("Type the word as it is written first.", true) else if (vm.addRule(novelScope, PronRule(from.trim(), to, whole, matchCase))) { from = ""; to = "" } }, Modifier.weight(1f))
            }
            StatusText(status.text, status.error)
            if (rules.isEmpty()) Hint("No rules here yet.")
            for (r in rules) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalPw.current.bg).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(r.from) }; append(" → " + r.to.ifEmpty { "(silent)" } + (if (r.whole) "" else " · inside words") + (if (r.matchCase) " · match case" else "")) },
                        Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    SoftButton("Listen", { vm.previewSpeech(r.to.ifBlank { r.from }) })
                    Box(Modifier.width(6.dp))
                    SoftButton("Delete", { vm.deleteRule(novelScope, r) })
                }
            }
            Hint("Type the word the way it should sound, using ordinary spelling. A change is heard from the next section that is not prepared yet; stop and play again to hear it right away. Longer written forms are tried first, and rules for this novel come before the ones for all novels.")
        }
    }
}

// ======================= cover =======================
@Composable
private fun CoverSheet(vm: AppViewModel, id: String) {
    val c = LocalPw.current
    val novels by Repo.novels.collectAsStateWithLifecycle()
    val version by Repo.coverVersion.collectAsStateWithLifecycle()
    val status by vm.coverStatus.collectAsStateWithLifecycle()
    val novel = novels.firstOrNull { it.id == id }
    val bmp by produceState<ImageBitmap?>(null, id, version) {
        value = withContext(Dispatchers.IO) { Repo.coverFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.setCoverFromUri(id, uri) }
    var linkDialog by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader(novel?.title ?: "Cover", onDone = { vm.closeSheet() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(120.dp).height(168.dp).clip(RoundedCornerShape(12.dp)).background(c.panel2).border(1.dp, c.line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                val b = bmp
                if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { PwIcon("book", tint = c.muted, size = 34.dp); Text("No cover yet", fontSize = 13.sp, color = c.muted) }
            }
            PrimaryButton("Find cover automatically", { vm.fetchCover(id) }, Modifier.fillMaxWidth(), enabled = !status.busy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Choose from device", { pick.launch("image/*") }, Modifier.weight(1f))
                SoftButton("Use image link", { linkDialog = true }, Modifier.weight(1f))
            }
            SoftButton("Remove cover", { vm.removeCover(id) }, Modifier.fillMaxWidth(), enabled = bmp != null)
            StatusText(status.text, status.error)
            Hint("PageWhisper looks for the cover on the novel's page. Sites that block fetching can't be checked, so for those choose an image from your device. Covers are shrunk and kept on this phone, and they appear only in your Library list.")
        }
    }
    if (linkDialog) {
        var v by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { linkDialog = false }, title = { Text("Link to the cover image") },
            text = { OutlinedTextField(v, { v = it }, singleLine = true) },
            confirmButton = { TextButton({ if (v.startsWith("http")) vm.setCoverFromLink(id, v); linkDialog = false }) { Text("Use") } },
            dismissButton = { TextButton({ linkDialog = false }) { Text("Cancel") } },
        )
    }
}

// ======================= chapter list =======================
@Composable
private fun TocSheet(vm: AppViewModel, id: String) {
    val c = LocalPw.current
    val ctx = LocalContext.current
    val novels by Repo.novels.collectAsStateWithLifecycle()
    val version by Repo.tocVersion.collectAsStateWithLifecycle()
    val status by vm.tocStatus.collectAsStateWithLifecycle()
    val novel = novels.firstOrNull { it.id == id } ?: return
    val items = remember(id, version) { Repo.tocItems(id) }
    val meta = remember(id, version) { Repo.tocMeta(id) }
    var asc by remember { mutableStateOf(true) }
    val view = remember(items, asc) { if (asc) items else items.reversed() }
    val cur = remember(view, novel.url) { ChapterList.indexOf(view, novel.url) }
    val curNum = if (cur >= 0) view[cur].num else ChapterList.chapterNumber(novel.url, "")
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, cur - 3))
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf("") }
    var jumpMsg by remember { mutableStateOf("") }
    var buildDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxHeight(0.9f)) {
        SheetHeader(novel.title, onDone = { vm.closeSheet() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Hint(meta?.let { "%,d chapters · %s on %s".format(it.count, if (it.source == "built") "built by number" else "read from the site", java.text.DateFormat.getDateInstance().format(java.util.Date(it.updated))) } ?: "No chapter list yet. Tap Update chapter list.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Update chapter list", { vm.updateToc(id) }, Modifier.weight(1f), enabled = !status.busy)
                SoftButton("Build by number", { buildDialog = true })
            }
            StatusText(status.text.ifEmpty { jumpMsg }, status.error || (status.text.isEmpty() && jumpMsg.isNotEmpty()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(jump, { jump = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), singleLine = true, enabled = items.isNotEmpty(), placeholder = { Text("Jump to chapter number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                SoftButton("Go", {
                    val n = jump.toDoubleOrNull()
                    val i = view.indexOfFirst { it.num == n }
                    if (n == null || i < 0) jumpMsg = "No chapter $jump in this list." else { jumpMsg = ""; scope.launch { listState.animateScrollToItem(i) } }
                }, enabled = items.isNotEmpty())
                SoftButton("Current", { if (cur >= 0) scope.launch { listState.animateScrollToItem(max(0, cur - 3)) } }, enabled = cur >= 0)
            }
            Segmented(listOf("asc" to "Oldest first", "desc" to "Newest first"), if (asc) "asc" else "desc") { asc = it == "asc"; scope.launch { listState.scrollToItem(0) } }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(view, key = { i, row -> "$i|${row.url}" }) { i, it ->
                val read = curNum != null && it.num != null && it.num < curNum
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).alpha(if (read) 0.55f else 1f)
                        .border(1.dp, if (i == cur) c.accent else c.line, RoundedCornerShape(12.dp)).clickable { vm.openChapterFromToc(it.url) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(it.title, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.text)
                    Box(Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(4.dp)) { PwIcon("external", tint = c.muted, size = 18.dp) }
                }
            }
        }
    }
    if (buildDialog) {
        var n by remember { mutableStateOf(max(items.maxOfOrNull { it.num ?: 0.0 }?.toInt() ?: 0, (curNum?.toInt() ?: 0) + 100).toString()) }
        AlertDialog(
            onDismissRequest = { buildDialog = false }, title = { Text("Highest chapter number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it.filter(Char::isDigit).take(4) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text("Builds chapters 1 to this number from the chapter link pattern" + if (items.isNotEmpty()) " and replaces the current list." else ".", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton({ n.toIntOrNull()?.takeIf { it >= 1 }?.let { vm.buildToc(id, it) }; buildDialog = false }) { Text("Build") } },
            dismissButton = { TextButton({ buildDialog = false }) { Text("Cancel") } },
        )
    }
}

// ======================= restore =======================
@Composable
private fun RestoreSheet(vm: AppViewModel) {
    val r by vm.restore.collectAsStateWithLifecycle()
    var merge by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    val ui = r ?: return
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Restore a backup", "Cancel", onDone = { vm.restore.value = null; vm.closeSheet() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ui.summary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalPw.current.bg).padding(12.dp))
            Label("How to restore")
            Segmented(listOf("merge" to "Merge", "replace" to "Replace everything"), if (merge) "merge" else "replace") { merge = it == "merge" }
            Hint(if (merge) "Adds what is missing and keeps what you have. For a novel in both, the newer bookmark wins. Your settings are left alone." else "Removes what is on this phone now and puts the backup in its place, including settings and reading style.")
            PrimaryButton(if (merge) "Merge into my library" else "Replace everything", { if (merge) vm.applyRestore(true) else confirm = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false }, title = { Text("Replace everything?") },
            text = { Text("This removes what is on this phone now and cannot be undone.") },
            confirmButton = { TextButton({ confirm = false; vm.applyRestore(false) }) { Text("Replace") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}
