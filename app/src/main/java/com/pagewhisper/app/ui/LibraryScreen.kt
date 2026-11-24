package com.pagewhisper.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.Novel
import com.pagewhisper.app.text.Extractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val c = LocalPw.current
    val novels by Repo.novels.collectAsStateWithLifecycle()
    val chapters by Repo.chapters.collectAsStateWithLifecycle()
    val status by vm.libraryStatus.collectAsStateWithLifecycle()
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    var addTitle by rememberSaveable { mutableStateOf("") }
    var addUrl by rememberSaveable { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Novel library", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text) }
        if (novels.isEmpty()) item { Hint("No novels yet. Load a chapter from a link and tap “Add current novel”, or add one by link below.") }
        items(novels.sortedByDescending { it.updated }, key = { it.id }) { NovelRow(it, vm) }
        item {
            PwCard {
                StatusText(status.text, status.error)
                SoftButton("Add current novel", { vm.addCurrentNovel() }, Modifier.fillMaxWidth(), enabled = !chapter?.url.isNullOrEmpty(), icon = "plus")
                SoftButton("Find missing covers", { vm.findMissingCovers() }, Modifier.fillMaxWidth())
                SoftButton("Update all chapter lists", { vm.updateAllTocs() }, Modifier.fillMaxWidth())
                Label("Add a novel by link")
                OutlinedTextField(addTitle, { addTitle = it }, Modifier.fillMaxWidth(), label = { Text("Novel title (optional)") }, singleLine = true)
                OutlinedTextField(
                    addUrl, { addUrl = it }, Modifier.fillMaxWidth(), label = { Text("Chapter or novel link") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                PrimaryButton("Add to library", { if (vm.addNovel(addTitle, addUrl)) { addTitle = ""; addUrl = "" } }, Modifier.fillMaxWidth())
                Hint("Keep links to novels from any site here and tap Continue to pick up where you left off. The bookmark moves automatically when you load a later chapter. Covers appear here only.")
            }
        }
        item { Text("Saved chapters", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text) }
        if (chapters.isEmpty()) item { Hint("Saved on this device only. Tap the star in the reader to save a chapter.") }
        items(chapters, key = { it.url + "|" + it.title }) { s ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = c.text)
                SoftButton("Open", { vm.openSaved(s) })
                Box(Modifier.width(6.dp))
                SoftButton("Delete", { Repo.deleteChapter(s) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelRow(n: Novel, vm: AppViewModel) {
    val c = LocalPw.current
    val ctx = LocalContext.current
    val coverVersion by Repo.coverVersion.collectAsStateWithLifecycle()
    val tocVersion by Repo.tocVersion.collectAsStateWithLifecycle()
    val cover by produceState<ImageBitmap?>(null, n.id, coverVersion) {
        value = withContext(Dispatchers.IO) { Repo.coverFile(n.id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    val count = remember(n.id, tocVersion) { Repo.tocCount(n.id) }
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.width(62.dp).height(88.dp).clip(RoundedCornerShape(8.dp)).background(c.panel2).border(1.dp, c.line, RoundedCornerShape(8.dp)).clickable { vm.openSheet(Sheet.Cover(n.id)) },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = cover
            if (bmp != null) Image(bmp, "Cover of ${n.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else PwIcon("book", tint = c.muted, size = 26.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(n.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.text)
            Text(
                Extractor.hostOf(n.url) + (if (n.chapter.isNotEmpty()) " · " + n.chapter else "") + (if (count > 0) " · %,d chapters".format(count) else ""),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryButton("Continue", { vm.continueNovel(n) })
                SoftButton("Chapters", { vm.openSheet(Sheet.Toc(n.id)) })
                SoftButton("Open in browser", { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                SoftButton("Edit link", { editing = true })
                SoftButton("Remove", { removing = true })
            }
        }
    }
    if (editing) {
        var v by remember { mutableStateOf(n.url) }
        AlertDialog(
            onDismissRequest = { editing = false }, title = { Text("Link for ${n.title}") },
            text = { OutlinedTextField(v, { v = it }, singleLine = true) },
            confirmButton = { TextButton({ if (v.startsWith("http")) Repo.updateNovelUrl(n.id, v.trim()); editing = false }) { Text("Save") } },
            dismissButton = { TextButton({ editing = false }) { Text("Cancel") } },
        )
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false }, title = { Text("Remove ${n.title}?") },
            text = { Text("Its cover and chapter list are removed too. Saved chapters are kept.") },
            confirmButton = { TextButton({ Repo.removeNovel(n.id); removing = false }) { Text("Remove") } },
            dismissButton = { TextButton({ removing = false }) { Text("Cancel") } },
        )
    }
}
