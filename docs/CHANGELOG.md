# Android changelog

## 0.6.0
- **EPUB and PDF books.** Open → Import a book, or "Open with PageWhisper" from a file manager. The book is added to the Library with its
  cover and chapters and read aloud like any novel: Continue, the Chapters sheet, previous/next, auto-next, highlight, pronunciation rules
  and the sleep timer all work. Books work offline.
- EPUB: chapters and titles from the table of contents (EPUB 3 nav or NCX), files split at anchors, footnote marks and page breaks skipped,
  cover included. DRM-protected books are refused with a clear message.
- PDF: text rebuilt from the pages (paragraphs joined, hyphenated words rejoined, page numbers and running headers dropped); chapters from the
  PDF's bookmarks, else from "Chapter 3"-style headings, else from blocks of 10 pages; the first page becomes the cover. Scanned PDFs (no text)
  and password-protected PDFs are refused. New dependency: PDFBox for Android (Apache-2.0).
- Books stay on the phone and are not part of backups; "Replace everything" on restore removes them, "Merge" keeps them.
- Library: a book shows its kind and author, and hides the web-only buttons (Open in browser, Edit link, Update chapter list, Build by number).
- Unit tests for the EPUB reader and the PDF text rules (`BooksTest`).
- The Light (int8) voice model option is removed: it sounded off. Its files and half-finished downloads are deleted the first time the app
  starts, and Settings → Voice model is back to the single full-precision model. The safer download (unpacked in a staging folder first) stays.
- versionCode 6, versionName 0.6.0.

## 0.5.0
- Heat control setting (Settings → Voice): Fast, Balanced or Cool, Cool being the default. Cool keeps the voice engine's threads on
  the phone's efficient cores with a larger audio reserve. If the speaker runs dry, the app steps itself towards a faster setting
  and says so, so reading never stays broken. The setting is not part of backups.
- Voice model choice (Settings → Voice model): Standard (full precision, about 350 MB) or Light (the official int8 build of the same
  model, about 130 MB, lighter on memory and cooler). Each downloads on its own into a staging folder, so an installed model survives
  a failed download. A switch applies from the next section; if the chosen model is not installed, the other one is used. An unused
  model can be removed from the same card.
- The web version ("PageWhisper Lab") is retired; references to it are removed from the app text, code comments and docs.
- Card descriptions on the Open, Library, Settings and About screens are justified.
- Stale files removed from the package: the old Thin-weight variable Montserrat font and the APK.
- versionCode 5, versionName 0.5.0.

## 0.4.0
- Readability: Sepia and Light text and secondary text are now close to black (Sepia and Light text now near black), with
  firmer borders. Cause of the faint look: the bundled Montserrat file was a variable font whose default weight is Thin, so the whole
  UI drew hairline-thin on some phones. It is replaced by static Regular, Medium, SemiBold and Bold files, and UI body text uses Medium.
- Chapter narration: the page's headings are now searched for the real chapter heading ("Chapter 12 - Title") instead of trusting
  the first `h1`, which is usually the novel's name. If the body already opens with its own chapter heading, that is read and the
  novel title is skipped. The novel's own name is never spoken as the chapter title.
- Cooler speech: 2 to 3 CPU threads instead of up to 6; a thermal watch (Android 10+) that drops to 2 threads and adds short rests
  between sections when the phone reports a moderate or worse thermal state; the wake lock is released while paused; slower polling
  while paused or with the screen off; a 2 second audio buffer for fewer wake-ups; the notification and saved position are no longer
  rebuilt on every sleep-timer tick.
- About card: more about the app, privacy, voice, reading and credits.
- README: build and setup instructions removed.
- The APK is no longer included in the source package, and files sit at the root of the zip.
- Reading style: underline now uses the selected highlight color in both preview and reader, and the highlighted text is bolded.
- Fullscreen brightness now synchronizes the in-app slider with system brightness controls. Android's required system-settings access is explained, and the slider switches adaptive brightness to manual mode when adjusted.
- Chapter fetching removes injected arrow-key navigation and comment-rules text from HTML, reader-service, and in-app browser extraction.
- versionCode 4, versionName 0.4.0.

## 0.3.0
- Reading fonts: bundled the advertised font families for offline use and added EB Garamond, Crimson Pro, Source Serif 4, and Montserrat.
  Montserrat now styles the app UI at a heavier medium weight; reader font selection remains independent.
- Readability: stronger Light and Sepia text contrast; accent-filled buttons and selected controls choose black or white text to
  match the accent shade.
- Reader: reset to the first paragraph when opening a different chapter, and speak the chapter title before its body without
  repeating a title already at the start of the body.
- About screen: added the “Developed by Sir_JPagdi” credit.
- Setup documentation now points to the repository root as the Android Studio project.
- Fixes: reading-style preview now reflects regular/bold weight and the selected sentence highlight style; pronunciation previews
  start the audio track before streaming generated samples, preventing long writes from stalling silently.
- versionCode 3, versionName 0.3.0.

## 0.2.0
- Reading style: accent colour (Theme default, purple, blue, teal, green, rose, orange) and accent shade slider (-35..35).
  Stored in `ReadingStyle.accent` / `accentTone`; read from and written to the shared backup file.
- Speech buffering: generates the next section only when under 15 s of audio is queued (35 s with the screen off),
  instead of a fixed 4 sections ahead. Less heat and battery use.
- New launcher icon: adaptive icon with separate background and foreground layers, plus a themed (monochrome) layer
  for Android 13+. `play_store_icon_512.png` is the Play Store listing icon.
- versionCode 2, versionName 0.2.0.

## 0.1.0
- First full source drop (not built).
