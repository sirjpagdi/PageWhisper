package com.pagewhisper.app

import com.pagewhisper.app.text.EpubReader
import com.pagewhisper.app.text.PdfText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** EPUB reading (with small books built on the spot) and the PDF text rules. */
class BooksTest {
    private fun epub(files: Map<String, String>): File {
        val f = File.createTempFile("book", ".epub")
        f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, body) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return f
    }

    private val container = """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
        <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    private fun page(body: String) = """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>x</title></head><body>$body</body></html>"""

    @Test fun epub3WithNavGivesChaptersAndTitles() {
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Blue Moon</dc:title><dc:creator>A. Writer</dc:creator></metadata>
            <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val nav = page("""<nav xmlns:epub="http://www.idpf.org/2007/ops" epub:type="toc"><ol><li><a href="ch1.xhtml">Chapter 1: Dawn</a></li><li><a href="ch2.xhtml">Chapter 2: Dusk</a></li></ol></nav>""")
        val book = EpubReader.read(
            epub(mapOf(
                "META-INF/container.xml" to container, "OEBPS/content.opf" to opf, "OEBPS/nav.xhtml" to nav,
                "OEBPS/ch1.xhtml" to page("<h1>Chapter 1: Dawn</h1><p>The sun rose over the hills.</p><p>Birds sang.</p>"),
                "OEBPS/ch2.xhtml" to page("<h1>Chapter 2: Dusk</h1><p>The sun went down.</p>"),
            )),
            "fallback",
        )
        assertEquals("Blue Moon", book.title)
        assertEquals("A. Writer", book.author)
        assertEquals(listOf("Chapter 1: Dawn", "Chapter 2: Dusk"), book.chapters.map { it.title })
        assertEquals("Chapter 1: Dawn\n\nThe sun rose over the hills.\n\nBirds sang.", book.chapters[0].text)
        assertTrue(book.chapters[1].text.contains("The sun went down."))
    }

    @Test fun ncxAnchorsSplitOneFileIntoChapters() {
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>One File</dc:title></metadata>
            <manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="b" href="book.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine toc="ncx"><itemref idref="b"/></spine></package>"""
        val ncx = """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
            <navPoint id="n1"><navLabel><text>One</text></navLabel><content src="book.xhtml#a"/></navPoint>
            <navPoint id="n2"><navLabel><text>Two</text></navLabel><content src="book.xhtml#b"/></navPoint></navMap></ncx>"""
        val book = EpubReader.read(
            epub(mapOf(
                "META-INF/container.xml" to container, "OEBPS/content.opf" to opf, "OEBPS/toc.ncx" to ncx,
                "OEBPS/book.xhtml" to page("""<h2 id="a">One</h2><p>First text.</p><h2 id="b">Two</h2><p>Second text.</p>"""),
            )),
            "x",
        )
        assertEquals(listOf("One", "Two"), book.chapters.map { it.title })
        assertTrue(book.chapters[0].text.contains("First text."))
        assertFalse(book.chapters[0].text.contains("Second text."))
        assertTrue(book.chapters[1].text.contains("Second text."))
    }

    @Test fun aFileWithoutTitleJoinsTheChapterBefore() {
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
            <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="c1" href="a.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="b.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val book = EpubReader.read(
            epub(mapOf(
                "META-INF/container.xml" to container, "OEBPS/content.opf" to opf,
                "OEBPS/nav.xhtml" to page("""<nav epub:type="toc"><ol><li><a href="a.xhtml">Only chapter</a></li></ol></nav>"""),
                "OEBPS/a.xhtml" to page("<p>Part one of the chapter.</p>"), "OEBPS/b.xhtml" to page("<p>Part two continues here.</p>"),
            )),
            "x",
        )
        assertEquals(1, book.chapters.size)
        assertEquals("Only chapter", book.chapters[0].title)
        assertEquals("Part one of the chapter.\n\nPart two continues here.", book.chapters[0].text)
    }

    @Test fun protectedBooksAreRefused() {
        val f = epub(mapOf(
            "META-INF/container.xml" to container,
            "META-INF/encryption.xml" to """<encryption><EncryptedData><EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/></EncryptedData></encryption>""",
        ))
        try { EpubReader.read(f, "x"); fail("expected an error") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("DRM")) }
    }

    @Test fun addressesInsideTheZipAreResolved() {
        assertEquals("OEBPS/images/a b.jpg", EpubReader.resolve("OEBPS/text", "../images/a%20b.jpg#x"))
        assertEquals("ch1.xhtml", EpubReader.resolve("", "./ch1.xhtml"))
    }

    // ---------- PDF text rules ----------

    @Test fun pageNumbersAndRunningHeadersAreDropped() {
        val a = listOf("alpha beta gamma", "delta epsilon zeta", "eta theta iota", "kappa lambda mu", "nu xi omicron")
        val b = listOf("pi rho sigma", "tau upsilon phi", "chi psi omega", "one two three", "four five six")
        val pages = (0 until 5).map { p -> listOf("My Book Title", a[p], "middle one", "middle two", b[p], "${p + 1}") }
        val out = PdfText.stripHeaders(pages)
        assertEquals(listOf(a[0], "middle one", "middle two", b[0]), out[0])
        assertEquals(5, out.size)
    }

    @Test fun linesJoinIntoParagraphs() {
        val lines = listOf(
            "This is a long line that fills the whole width of the page and", "continues onto the next line of the same paragraph.", "Short end.",
            "A new paragraph starts here with a long line that fills the width of", "the page again and finishes.",
        )
        val paras = PdfText.paragraphs(listOf(lines))
        assertEquals(2, paras.size)
        assertTrue(paras[0].text.startsWith("This is a long line"))
        assertTrue(paras[0].text.endsWith("Short end."))
        assertTrue(paras[1].text.startsWith("A new paragraph"))
    }

    @Test fun hyphenatedWordsAreJoined() {
        val paras = PdfText.paragraphs(listOf(listOf("The garden was full of beauti-", "ful flowers that bloomed all summer.")))
        assertEquals(listOf("The garden was full of beautiful flowers that bloomed all summer."), paras.map { it.text })
    }

    @Test fun chaptersComeFromHeadingsInTheText() {
        val p = listOf(
            PdfText.Para("Chapter 1", 0), PdfText.Para("The Start", 0), PdfText.Para("Body one is here.", 0),
            PdfText.Para("Chapter 2", 1), PdfText.Para("Body two.", 1),
            PdfText.Para("Chapter 3", 2), PdfText.Para("Body three.", 2),
        )
        val ch = PdfText.chapters(p, emptyList(), 3)
        assertEquals(listOf("Chapter 1 - The Start", "Chapter 2", "Chapter 3"), ch.map { it.title })
        assertEquals("Body one is here.", ch[0].text)
        assertEquals("Body two.", ch[1].text)
    }

    @Test fun chaptersComeFromBookmarksWhenThereAreSome() {
        val p = (0 until 4).map { PdfText.Para("Text on page $it.", it) }
        val ch = PdfText.chapters(p, listOf(0 to "Intro", 2 to "Part Two"), 4)
        assertEquals(listOf("Intro", "Part Two"), ch.map { it.title })
        assertEquals("Text on page 0.\n\nText on page 1.", ch[0].text)
        assertEquals("Text on page 2.\n\nText on page 3.", ch[1].text)
    }

    @Test fun withoutAnyStructurePagesAreGroupedInTens() {
        val p = (0 until 25).map { PdfText.Para("Plain text on page $it.", it) }
        val ch = PdfText.chapters(p, emptyList(), 25)
        assertEquals(listOf("Pages 1\u201310", "Pages 11\u201320", "Pages 21\u201325"), ch.map { it.title })
    }
}
