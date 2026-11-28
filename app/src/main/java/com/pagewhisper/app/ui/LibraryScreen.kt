package com.pagewhisper.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.data.Books
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.text.ChapterList
import com.pagewhisper.app.text.Extractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How far a novel has been read: [current] is the number of the chapter bookmarked (0 if unknown), [total] the chapters in its list. */
private class Prog(val current: Int, val total: Int) {
    val fraction: Float get() = if (total > 0 && current > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f
    val label: String get() = when {
        total <= 0 -> ""
        current > 0 -> "CH %,d/%,d".format(current, total)
        else -> "%,d chapters".format(total)
    }
}

@Composable
private fun rememberProgress(n: Novel): Prog {
    val tocVersion by Repo.tocVersion.collectAsStateWithLifecycle()
    return remember(n.id, n.url, tocVersion) {
        val items = Repo.tocItems(n.id)
        if (items.isEmpty()) Prog(0, 0) else Prog(ChapterList.indexOf(items, n.url) + 1, items.size)
    }
}

@Composable
private fun rememberCover(n: Novel): ImageBitmap? {
    val coverVersion by Repo.coverVersion.collectAsStateWithLifecycle()
    val cover by produceState<ImageBitmap?>(null, n.id, coverVersion) {
        value = withContext(Dispatchers.IO) { Repo.coverFile(n.id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    return cover
}

@Composable
private fun Chip(text: String, selected: Boolean, icon: String?, onClick: () -> Unit) {
    val c = LocalPw.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (selected) c.accent.copy(alpha = 0.16f) else c.panel)
            .border(1.dp, if (selected) c.accent else c.line, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) PwIcon(icon, tint = if (selected) c.accent else c.muted, size = 16.dp)
        Text(text, fontSize = 13.sp, color = if (selected) c.accent else c.muted)
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFD32F2F)).padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val c = LocalPw.current
    val ctx = LocalContext.current
    val novels by Repo.novels.collectAsStateWithLifecycle()
    val chapters by Repo.chapters.collectAsStateWithLifecycle()
    val settings by Repo.settings.collectAsStateWithLifecycle()
    val status by vm.libraryStatus.collectAsStateWithLifecycle()
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var booksOnly by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Novel?>(null) }
    var editing by remember { mutableStateOf<Novel?>(null) }
    var removing by remember { mutableStateOf<Novel?>(null) }

    val shown = remember(novels, favOnly, booksOnly, settings.libSort) {
        val list = novels.filter { (!favOnly || it.fav) && (!booksOnly || Books.isBookUrl(it.url)) }
        when (settings.libSort) {
            "az" -> list.sortedBy { it.title.lowercase() }
            "za" -> list.sortedByDescending { it.title.lowercase() }
            else -> list.sortedByDescending { it.updated }
        }
    }
    val last = novels.maxByOrNull { it.updated }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (last != null) item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(last, vm) }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOUR LIBRARY · ${shown.size}", Modifier.weight(1f), fontSize = 13.sp, letterSpacing = 1.sp, color = c.muted)
                    Box {
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.panel).clickable { sortMenu = true }.padding(10.dp)) { PwIcon("sort", tint = c.text, size = 20.dp) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            for ((id, label) in listOf("recent" to "Recently read", "az" to "Title A–Z", "za" to "Title Z–A")) {
                                DropdownMenuItem(text = { Text(label, color = if (settings.libSort == id) c.accent else c.text) }, onClick = { vm.update { it.copy(libSort = id) }; sortMenu = false })
                            }
                        }
                    }
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.panel)) {
                        for ((grid, icon) in listOf(true to "grid", false to "list")) {
                            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (settings.libGrid == grid) c.panel2 else Color.Transparent).clickable { vm.update { it.copy(libGrid = grid) } }.padding(10.dp)) {
                                PwIcon(icon, tint = if (settings.libGrid == grid) c.accent else c.muted, size = 20.dp)
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Favourites", favOnly, "heart") { favOnly = !favOnly }
                    Chip("Books", booksOnly, "book") { booksOnly = !booksOnly }
                }
            }

            if (novels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Hint("No novels yet. Tap the + button to add one by link, or to import an EPUB or PDF book. You can also load a chapter and tap “Add current novel” below.") }
            else if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Hint("Nothing matches these filters.") }

            if (settings.libGrid) {
                items(shown, key = { it.id }) { n -> NovelCell(n, vm) { menuFor = n } }
            } else {
                items(shown, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { n -> NovelRow(n, vm) { menuFor = n } }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                PwCard {
                    StatusText(status.text, status.error)
                    SoftButton("Add current novel", { vm.addCurrentNovel() }, Modifier.fillMaxWidth(), enabled = !chapter?.url.isNullOrEmpty(), icon = "plus")
                    SoftButton("Find missing covers", { vm.findMissingCovers() }, Modifier.fillMaxWidth())
                    SoftButton("Update all chapter lists", { vm.updateAllTocs() }, Modifier.fillMaxWidth())
                    Hint("Tap a novel to continue where you left off; press and hold for more (chapters, favourite, cover, link, remove). The bookmark moves automatically when you load a later chapter.")
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { Text("Saved chapters", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text) }
            if (chapters.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Hint("Saved on this device only. Tap the star in the reader to save a chapter.") }
            items(chapters, key = { it.url + "|" + it.title }, span = { GridItemSpan(maxLineSpan) }) { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = c.text)
                    SoftButton("Open", { vm.openSaved(s) })
                    Box(Modifier.width(6.dp))
                    SoftButton("Delete", { Repo.deleteChapter(s) })
                }
            }
        }

        FloatingActionButton(
            onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            shape = CircleShape, containerColor = c.accent, contentColor = Color.White,
        ) { PwIcon("plus", tint = Color.White, size = 26.dp) }
    }

    if (adding) AddNovelDialog(vm, chapter?.url.isNullOrEmpty().not()) { adding = false }

    menuFor?.let { n ->
        val isBook = Books.isBookUrl(n.url)
        AlertDialog(
            onDismissRequest = { menuFor = null }, title = { Text(n.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftButton("Continue reading", { menuFor = null; vm.continueNovel(n) }, Modifier.fillMaxWidth())
                    SoftButton("Chapters", { menuFor = null; vm.openSheet(Sheet.Toc(n.id)) }, Modifier.fillMaxWidth())
                    SoftButton(if (n.fav) "Remove from favourites" else "Add to favourites", { Repo.toggleFavourite(n.id); menuFor = null }, Modifier.fillMaxWidth())
                    SoftButton("Change cover", { menuFor = null; vm.openSheet(Sheet.Cover(n.id)) }, Modifier.fillMaxWidth())
                    if (!isBook) SoftButton("Open in browser", { menuFor = null; ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth())
                    if (!isBook) SoftButton("Edit link", { menuFor = null; editing = n }, Modifier.fillMaxWidth())
                    SoftButton("Remove", { menuFor = null; removing = n }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton({ menuFor = null }) { Text("Close") } },
        )
    }
    editing?.let { n ->
        var v by remember(n.id) { mutableStateOf(n.url) }
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text("Link for ${n.title}") },
            text = { OutlinedTextField(v, { v = it }, singleLine = true) },
            confirmButton = { TextButton({ if (v.startsWith("http")) Repo.updateNovelUrl(n.id, v.trim()); editing = null }) { Text("Save") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
    removing?.let { n ->
        AlertDialog(
            onDismissRequest = { removing = null }, title = { Text("Remove ${n.title}?") },
            text = { Text(if (Books.isBookUrl(n.url)) "The book, its cover and chapter list are removed from this phone. Saved chapters are kept. You can import the file again later." else "Its cover and chapter list are removed too. Saved chapters are kept.") },
            confirmButton = { TextButton({ Repo.removeNovel(n.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel") } },
        )
    }
}

/** The novel read most recently, with its progress; tap to continue. */
@Composable
private fun ContinueCard(n: Novel, vm: AppViewModel) {
    val c = LocalPw.current
    val cover = rememberCover(n)
    val prog = rememberProgress(n)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(16.dp)).clickable { vm.continueNovel(n) }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(60.dp).height(86.dp).clip(RoundedCornerShape(10.dp)).background(c.panel2), contentAlignment = Alignment.Center) {
            val bmp = cover
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else PwIcon("book", tint = c.muted, size = 26.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("CONTINUE READING", fontSize = 11.sp, letterSpacing = 1.sp, color = c.accent)
            Text(n.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.text)
            if (n.chapter.isNotEmpty()) Text(n.chapter, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted)
            if (prog.total > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { prog.fraction }, Modifier.weight(1f), color = c.accent, trackColor = c.line)
                Text("${(prog.fraction * 100).toInt()}%", fontSize = 12.sp, color = c.muted)
            }
        }
    }
}

/** A cover with the title and progress below it. Tap: continue. Press and hold: more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelCell(n: Novel, vm: AppViewModel, onMenu: () -> Unit) {
    val c = LocalPw.current
    val cover = rememberCover(n)
    val prog = rememberProgress(n)
    val isBook = Books.isBookUrl(n.url)
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { vm.continueNovel(n) }, onLongClick = onMenu), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(c.panel2).border(1.dp, c.line, RoundedCornerShape(14.dp))) {
            val bmp = cover
            if (bmp != null) Image(bmp, "Cover of ${n.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PwIcon("book", tint = c.muted, size = 32.dp) }
            if (isBook) Badge(Books.kindLabel(n.url), Modifier.align(Alignment.TopStart).padding(6.dp))
            if (n.fav) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                PwIcon("heart-fill", tint = Color(0xFFFF4D5E), size = 15.dp)
            }
        }
        Text(n.title, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = c.text)
        if (prog.label.isNotEmpty()) Text(prog.label, fontSize = 12.sp, color = c.muted)
    }
}

/** The list view of a novel: cover, title, where it stands, and a heart. Tap: continue. Press and hold, or the dots: more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelRow(n: Novel, vm: AppViewModel, onMenu: () -> Unit) {
    val c = LocalPw.current
    val cover = rememberCover(n)
    val prog = rememberProgress(n)
    val isBook = Books.isBookUrl(n.url)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = { vm.continueNovel(n) }, onLongClick = onMenu).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(54.dp).height(78.dp).clip(RoundedCornerShape(8.dp)).background(c.panel2), contentAlignment = Alignment.Center) {
            val bmp = cover
            if (bmp != null) Image(bmp, "Cover of ${n.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else PwIcon("book", tint = c.muted, size = 24.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(n.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = c.text)
            Text(
                (if (isBook) Books.label(n.url) else Extractor.hostOf(n.url)) + (if (prog.label.isNotEmpty()) " · " + prog.label else ""),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted,
            )
            if (prog.total > 0) LinearProgressIndicator(progress = { prog.fraction }, Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
        }
        Box(Modifier.clip(CircleShape).clickable { Repo.toggleFavourite(n.id) }.padding(6.dp)) {
            PwIcon(if (n.fav) "heart-fill" else "heart", tint = if (n.fav) Color(0xFFFF4D5E) else c.muted, size = 22.dp)
        }
        Box(Modifier.clip(CircleShape).clickable(onClick = onMenu).padding(6.dp)) { PwIcon("more", tint = c.muted, size = 22.dp) }
    }
}

/** The + button: add a novel by link, add the novel open in the reader, or import an EPUB or PDF book. */
@Composable
private fun AddNovelDialog(vm: AppViewModel, canAddCurrent: Boolean, onClose: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val pickBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { vm.importBook(uri); onClose() } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add to library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Novel title (optional)") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Chapter or novel link") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                PrimaryButton("Add by link", { if (vm.addNovel(title, url)) { title = ""; url = ""; onClose() } }, Modifier.fillMaxWidth())
                SoftButton("Add the novel open in the reader", { vm.addCurrentNovel(); onClose() }, Modifier.fillMaxWidth(), enabled = canAddCurrent, icon = "plus")
                SoftButton("Import an EPUB or PDF book", { pickBook.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")) }, Modifier.fillMaxWidth(), icon = "book")
            }
        },
        confirmButton = { TextButton(onClose) { Text("Close") } },
    )
}
