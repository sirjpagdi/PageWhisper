package com.pagewhisper.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OpenScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val fetch by vm.fetchUi.collectAsStateWithLifecycle()
    val book by vm.bookUi.collectAsStateWithLifecycle()
    val nextUrl by vm.nextUrl.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val pickBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importBook(uri) }
    val pickText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.loadFromUri(uri) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        PwCard("Fetch a chapter") {
            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Chapter web address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            PrimaryButton("Fetch chapter", { vm.fetchChapter(url) }, Modifier.fillMaxWidth(), enabled = !fetch.busy)
            StatusText(fetch.text, fetch.error)
            SoftButton("Read in browser (for sites that block fetching)", {
                if (url.isBlank()) vm.toast("Enter the chapter address first.") else vm.browser.value = url.trim()
            }, Modifier.fillMaxWidth())
            Hint("The page is read directly, then through a reader service. Sites with bot protection or a login may still block it. “Read in browser” opens the page here so you can pass the check yourself, then reads it. Pasting the text always works.")
        }
        PwCard("Import a book (EPUB or PDF)") {
            PrimaryButton("Choose an EPUB or PDF file", {
                pickBook.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream"))
            }, Modifier.fillMaxWidth(), enabled = !book.busy)
            StatusText(book.text, book.error)
            Hint("The book is added to your library with its chapters and cover, and stays on this phone. EPUB books keep their chapters and titles. For a PDF the text is rebuilt from the pages: paragraphs are joined, page numbers and running headers are dropped, and chapters come from the PDF's bookmarks or from headings like “Chapter 3”. Scanned PDFs have no text and cannot be read. You can also open an EPUB or PDF from your file manager with PageWhisper.")
        }
        PwCard("Paste or import text") {
            PrimaryButton("Paste from clipboard & load", {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                text = t
                vm.loadPasted(t, title, url, "Loaded from clipboard. Tap any sentence to start reading from there.")
            }, Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Chapter title") }, singleLine = true)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Chapter text") })
            PrimaryButton("Load chapter", { vm.loadPasted(text, title, url) }, Modifier.fillMaxWidth())
            SoftButton("Import a .txt file", { pickText.launch(arrayOf("text/plain")) }, Modifier.fillMaxWidth())
        }
        PwCard("Next chapter") {
            OutlinedTextField(
                nextUrl, { vm.nextUrl.value = it }, Modifier.fillMaxWidth(), label = { Text("Next chapter address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            SoftButton("Fetch", { vm.fetchChapter(nextUrl) }, Modifier.fillMaxWidth(), enabled = nextUrl.isNotBlank())
            Hint("Filled in from the novel's chapter list when it has one; otherwise from the page's next link, or guessed by adding 1 to the chapter number, so check it.")
        }
    }
}
