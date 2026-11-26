package com.pagewhisper.app.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads the text of a PDF with PDFBox and hands it to [PdfText]. A PDF that is only pictures of pages (a scan) has no
 * text and is refused: reading it would need text recognition (OCR), which is not part of the app.
 */
object PdfReader {
    fun read(ctx: Context, file: File, fallbackTitle: String, onProgress: (done: Int, total: Int) -> Unit): ParsedBook {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        val doc = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } catch (e: InvalidPasswordException) {
            throw IllegalStateException("This PDF is protected by a password and cannot be read.")
        }
        return doc.use { d ->
            val total = d.numberOfPages
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val pages = ArrayList<List<String>>()
            for (p in 1..total) {
                onProgress(p, total)
                stripper.startPage = p
                stripper.endPage = p
                val text = try { stripper.getText(d) } catch (_: Exception) { "" }
                pages.add(text.lines().map { it.trim() }.filter { it.isNotEmpty() })
            }
            if (pages.sumOf { page -> page.sumOf { it.length } } < 200) {
                throw IllegalStateException("This PDF has no text layer (it is probably a scan). Reading it would need text recognition (OCR), which PageWhisper does not have yet.")
            }
            val paras = PdfText.paragraphs(PdfText.stripHeaders(pages))
            val chapters = PdfText.chapters(paras, outlineStarts(d), total)
            if (chapters.isEmpty()) throw IllegalStateException("No readable text was found in this PDF.")
            val info = d.documentInformation
            val title = info?.title?.trim().orEmpty().ifEmpty { fallbackTitle }
            val author = info?.author?.trim().orEmpty()
            ParsedBook(title, author, chapters, coverOf(file))
        }
    }

    private fun collect(parent: PDOutlineNode, d: PDDocument): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var c: PDOutlineItem? = parent.firstChild
        while (c != null) {
            val page = try { c.findDestinationPage(d) } catch (_: Exception) { null }
            val idx = if (page != null) d.pages.indexOf(page) else -1
            val title = (c.title ?: "").trim()
            if (idx >= 0 && title.isNotEmpty()) out.add(idx to title)
            c = c.nextSibling
        }
        return out
    }

    /** The PDF's bookmarks as (page index, title). A single root bookmark is replaced by its children. */
    private fun outlineStarts(d: PDDocument): List<Pair<Int, String>> = try {
        val outline = d.documentCatalog.documentOutline
        if (outline == null) emptyList<Pair<Int, String>>() else {
            var level = collect(outline, d)
            if (level.size < 3) {
                val first = outline.firstChild
                if (first != null) {
                    val sub = collect(first, d)
                    if (sub.size >= 3) level = sub
                }
            }
            level
        }
    } catch (_: Throwable) { emptyList<Pair<Int, String>>() }

    /** The first page drawn as a small picture, used as the cover. */
    private fun coverOf(file: File): ByteArray? {
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            if (renderer.pageCount == 0) return null
            val page = renderer.openPage(0)
            try {
                val w = 480
                val h = maxOf(1, (w * page.height.toFloat() / page.width).toInt())
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            } finally { page.close() }
        } catch (_: Throwable) { null } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { fd?.close() } catch (_: Throwable) {}
        }
    }
}
