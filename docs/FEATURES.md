# PageWhisper features and parity

This file is the spec of the Android app's features: names, ranges, defaults and behavior. Update it together with the feature.

## Status

| Feature | Status |
|---|---|
| Fetch a chapter by link (reader service, direct, proxy) | Done (OkHttp + Jsoup) |
| **Supertonic 3 voice engine** (10 voices, 31 languages) and hiding voices | Done (needs a phone to check the sound and speed) |
| **Prepared chapters**: next 3 chapters fetched, speech made while not listening, old ones deleted | Done (WorkManager; background job needs a phone to check) |
| **Books**: import EPUB and PDF into the library | Done (jsoup for EPUB, PDFBox for PDF; unit tested text rules) |
| Paste, clipboard, import `.txt`, share-sheet input | Done |
| Kokoro voices (28) and speed | Done (sherpa-onnx) |
| Sentence highlight, tap a sentence to jump | Done |
| Fullscreen reader | Done (immersive mode) |
| **Touch lock in fullscreen** (hold to unlock) | Done |
| Previous/next chapter, next link detection, auto-next | Done |
| Novel library with moving bookmarks | Done |
| Saved chapters | Done |
| Ambient sounds (9, generated on device) | Done |
| Keep screen on | Done (window flag) |
| Background / screen-off playback | Done (foreground service) |
| **Reading style** (fonts, spacing, alignment, themes) | Done |
| Browser fetch for bot-protected sites | Done (WebView as a tool) |
| **Backup and restore** (one JSON file) | Done |
| **Sleep timer** (timed or end of chapter, with fade-out) | Done |
| **Novel covers** (Library list only) | Done |
| **Chapter lists** (full table of contents per novel) | Done |
| **Brightness in fullscreen** | System brightness slider stays in sync with Android controls |
| **Keep screen on while locked** | Done |
| **Flat SVG icons, no emoji** | Done (vector drawables) |
| **Pronunciation fixes** (all novels or one novel) | Done |

---

## Spec: Reading style

Opened from the **Aa** button in the Read toolbar (also works in fullscreen). Changes apply live to the
reader and are saved immediately. A **Reset** button restores the defaults.

### Settings

| Key | Meaning | Range / values | Default |
|---|---|---|---|
| `font` | Typeface | `serif`, `sans`, `mono`, `literata`, `merriweather`, `lora`, `atkinson`, `lexend`, `dyslexic` | `serif` |
| `size` | Text size (sp) | 14 to 34, step 1 | 19 |
| `line` | Line height multiplier | 1.3 to 2.4, step 0.05 | 1.8 |
| `para` | Space after a paragraph, in em | 0.2 to 2.0, step 0.1 | 1.05 |
| `pad` | Side margins (px / dp) | 0 to 40, step 2 | 4 |
| `ls` | Letter spacing, in em | 0 to 0.12, step 0.01 | 0 |
| `align` | Text alignment | `left`, `justify`, `center` | `left` |
| `weight` | Text weight | 400 (Regular), 700 (Bold) | 400 |
| `hyph` | Hyphenate long words | true / false | false |
| `theme` | Color theme (whole app) | `dark`, `amoled`, `sepia`, `light` | `dark` |
| `hl` | Highlight color | `violet`, `gold`, `green`, `blue`, `pink`, `orange` | `violet` |
| `hlStyle` | Highlight style | `fill`, `underline`, `outline` | `fill` |

Out-of-range or unknown values are clamped or replaced by the default when loading (see `sanitizeReading`).

### Fonts

- System fonts: Serif, Sans, Mono (no download).
- Bundled fonts (work offline): Literata, Merriweather, Lora,
  Atkinson Hyperlegible, Lexend, OpenDyslexic. Regular and Bold, Latin plus Latin Extended (so pinyin tone marks render).
- **Android:** bundle these as font resources (all are open-licensed: OFL, plus OpenDyslexic's own open license; check each
  license text before a Play release).

### Themes

| Theme | Background | Text | Panel | Accent |
|---|---|---|---|---|
| Dark | `#0e0e16` | `#ecebf5` | `#171722` | `#8b5cf6` |
| AMOLED | `#000000` | `#e8e8f0` | `#0b0b10` | `#8b5cf6` |
| Sepia | `#f4ecd8` | `#120b04` | `#ebe0c6` | `#9a5b2e` |
| Light | `#fafafa` | `#050508` | `#ffffff` | `#6d3fe0` |

Muted text is `#2e2314` in Sepia and `#22242b` in Light.

### Accent colour and shade (reading style keys `accent`, `accentTone`)

`accent` is `auto` (the theme's own accent, above), `purple`, `blue`, `teal`, `green`, `rose` or `orange`; `accentTone` is an
integer from -35 to 35 (percentage points of HSL lightness added to the accent, hue and saturation kept; the result is kept
between 12% and 88% lightness). The accent applies to buttons, the progress bar, selected chips and the nav bar, and is
separate from the highlight colour. Solid accent controls choose black or white foreground text by relative luminance.
The app UI uses medium-weight Montserrat; the reader font remains independently selectable. Colours (main / secondary) per theme group:

| Accent | Dark and AMOLED | Light | Sepia |
|---|---|---|---|
| purple | `#8b5cf6` / `#c4b5fd` | `#6d3fe0` / `#5a2fc4` | `#7740a0` / `#66318d` |
| blue | `#3b82f6` / `#93c5fd` | `#2563eb` / `#1d4ed8` | `#315f9b` / `#244c80` |
| teal | `#14b8a6` / `#5eead4` | `#0f766e` / `#115e59` | `#276c64` / `#20574f` |
| green | `#22c55e` / `#86efac` | `#15803d` / `#166534` | `#4f7839` / `#3d602d` |
| rose | `#f43f5e` / `#fda4af` | `#be123c` / `#9f1239` | `#a13d50` / `#843044` |
| orange | `#f97316` / `#fdba74` | `#c2410c` / `#9a3412` | `#9a5b2e` / `#7a4a1b` |

### Speech buffering

Speech is generated only when the audio in front of the speaker is running low: after the first section, a new one is made
when less than 15 s remain (35 s while the screen is off or the app is hidden). This keeps the device cooler and saves battery.
On Android, the chapter title is spoken before the body when it is not already the first body paragraph. The title intro is not duplicated in the reader text.

### Highlight colors (RGB)

violet `139,92,246` · gold `234,179,8` · green `34,197,94` · blue `59,130,246` · pink `236,72,153` · orange `249,115,22`

- `fill`: background at 34% opacity
- `underline`: 3 px underline in the color, no background
- `outline`: 2 px ring in the color plus a 10% background

### Android mapping

| Setting | Compose |
|---|---|
| `font` | Bundled reader `FontFamily` from `res/font` (Montserrat is the app-wide UI font; variable fonts use `wght`) |
| `size` | `TextStyle.fontSize` in `sp` |
| `line` | `lineHeight = fontSize * line` |
| `para` | spacing between paragraphs: `para * fontSize` |
| `pad` | horizontal `padding` |
| `ls` | `letterSpacing` in `em` |
| `align` | `TextAlign.Start` / `Justify` / `Center` |
| `weight` | `FontWeight.Normal` / `Bold` |
| `hyph` | `Hyphens.Auto` (with `LineBreak.Paragraph`) |
| `theme` | Material 3 `ColorScheme` per theme |
| `hl`, `hlStyle` | `SpanStyle` (`background` / `textDecoration` + color) on the active sentence |

Storage: one `DataStore` entry per key above. Backup JSON uses the same key names:

```json
{ "reading": { "font": "literata", "size": 20, "line": 1.8, "para": 1.05, "pad": 4, "ls": 0,
               "align": "left", "weight": 400, "hyph": false, "theme": "dark", "hl": "violet", "hlStyle": "fill" } }
```

### Acceptance checks

- Dragging any slider changes the text immediately, without moving to another screen.
- Themes recolor the whole app, and the system status bar color matches.
- Reopening the app keeps every setting; Reset restores the table above.
- The highlighted sentence stays readable in all four themes and all three highlight styles.


---

## Spec: Touch lock (fullscreen only)

Stops accidental touches while reading or listening in the fullscreen view (pocket, palm, kids).

### Behavior

- A **lock button** appears in the fullscreen toolbar (between Auto and Exit). It is not shown outside fullscreen.
- **Locked:** every touch is swallowed. The reader, toolbar, player bar and the Reading style panel cannot be used,
  scrolled or tapped. The text still follows the narration automatically.
- The controls behind the lock are dimmed so it is obvious they are off.
- **Unlock:** a round lock button at the bottom right. **Press and hold for 1.1 seconds**; a ring fills while holding.
  Releasing early cancels. A normal tap never unlocks.
- The unlock button is faint after 3.5 s without touches. Any touch brightens it and shows the hint
  "Locked. Press and hold the lock button to unlock."
- A short vibration confirms locking, and a double pulse confirms unlocking (where the device supports it).
- **Still works while locked:** narration, notification and lock-screen controls, Bluetooth and headset buttons,
  volume keys, ambient sound.
- **Exiting fullscreen is blocked while locked.** If the system ends fullscreen anyway (for example a back gesture), the
  app stays in the fullscreen view and stays locked; unlock first, then use the exit button.
- Not remembered across restarts: the app always opens unlocked.
- Accessibility: the content behind the lock is hidden from screen readers and the keyboard too (`inert`).

### Constants

| Name | Value |
|---|---|
| Hold time to unlock | 1100 ms |
| Hint / button wake time | 3500 ms |
| Dimmed controls opacity | 0.3 |
| Idle unlock button opacity | 0.4 |

### Android mapping

| Behavior | Compose / Android |
|---|---|
| Swallow touches | full-screen overlay using `pointerInput` that consumes every `PointerEvent` |
| Hold to unlock | `detectTapGestures(onPress = ...)` with a 1.1 s timer; `CircularProgressIndicator` ring |
| Block back button | `BackHandler(enabled = locked) { }` |
| Hide content from TalkBack | `Modifier.clearAndSetSemantics { }` on the content, or `semantics { invisibleToUser() }` |
| Haptics | `LocalHapticFeedback` or `VibrationEffect` |
| Keep playing | foreground service and MediaSession are unaffected |
| Optional later | `FLAG_KEEP_SCREEN_ON` while locked; auto-lock after N seconds in fullscreen |

### Acceptance checks

- Tapping the player buttons, text, toolbar and tabs while locked does nothing.
- A 0.5 s hold does not unlock; a full hold does.
- Locking and unlocking never interrupts narration.
- Ending fullscreen by a back gesture while locked leaves the app locked.
- After unlocking, every control works and the exit button leaves fullscreen.


---

## Design rule: icons

- No emoji anywhere. Every icon is a flat, single-color vector: 24 x 24 grid, 1.8 stroke, round caps and joins,
  `currentColor` (filled shapes only for play, pause, stop, skip and the saved star).
- Names used in the app: play, pause, prev, next, stop, lock, moon, sun, fs, close, star, star-fill, plus, book,
  and the ambient set (off, rain, heavy, storm, ocean, insects, forest, wind, fire, brown).
- **Android:** one vector drawable per name (Material Symbols outlined style is a close match).

---

## Spec: Sleep timer

A moon button in the player bar opens the timer panel. The button shows the time left, or "End".

- **Presets:** 5, 10, 15, 30, 45, 60, 90 minutes, a custom value (1 to 600 minutes), and **End of chapter**.
- **Active timer:** the panel shows the countdown with **+10 min** and **Turn off**. The last chosen custom value is remembered.
- **Fade:** over the last `min(30 s, total / 4)` the volume (narration and ambient together) falls from 100% to 3%.
- **When it ends:** playback is **paused where you stopped** (not reset), ambient sound stops, and a message appears.
  Pressing play restores full volume and carries on.
- **End of chapter:** playback stops when the chapter finishes, and auto-next does not start the next chapter.
- It counts real clock time (an end timestamp), so it stays accurate if the page is throttled in the background.
- Pausing or stopping does not cancel the timer; it simply runs out.

| Android mapping | |
|---|---|
| Timer | end timestamp on `SystemClock.elapsedRealtime()`, checked from the playback service |
| Fade | ramp the player volume (`Player.volume`) and the ambient volume |
| Expiry | `player.pause()`; the notification and lock-screen controls stay available |

---

## Spec: Novel covers (Library only)

Covers appear **only** in the Library's list of saved novels, never in the reader, the player or notifications.

- **Where it looks:** the novel's own page (the chapter link without its chapter number), then the saved link.
  For each page: the page itself, then a public proxy, then the reader service.
- **Candidates:** `og:image`, `twitter:image`, `image_src`, then images inside cover-like elements
  (`.cover`, `.book-cover`, `[class*=poster]`, `itemprop=image`...). Logos, icons, banners, avatars, sprites, `.svg`
  and `.gif` are skipped.
- **Choosing:** the first image that is portrait (height at least 1.1 times its width); a wide image is used only if nothing else is found.
- **Storing:** shrunk to at most 240 x 340, JPEG quality 0.82 (about 5 to 25 KB), kept in `pw-covers` keyed by novel id.
  If the image host blocks cross-site reads, an image proxy (`wsrv.nl`) is tried.
- **When:** automatically when a novel is added; again at most once a week for novels that still have none;
  **Find missing covers** in the Library; or **Find cover automatically** in the cover panel.
- **Manual:** tap a cover (or the book placeholder) to open the cover panel: find automatically, choose from the device,
  use an image link, or remove. Sites that block fetching are handled by choosing an image.
- Removing a novel removes its cover.

| Android mapping | |
|---|---|
| Fetching | OkHttp + Jsoup, same candidate rules, no CORS limits so the proxy is not needed |
| Storing | image files in app storage (not base64), keyed by novel id |
| Display | Coil `AsyncImage` in the Library list only |
| Picking | `ActivityResultContracts.PickVisualMedia` |

---

## Spec: Brightness (fullscreen)

A sun button appears in the fullscreen toolbar (before the lock button) and opens brightness controls.

- The in-app slider changes system brightness, and changes made in Android's brightness controls are reflected in the app.
  Android's "Modify system settings" access is required. Adjusting the slider switches adaptive brightness to manual mode.

---

## Spec: Keep screen on

The screen is kept on when **either** applies:

1. "Keep screen on while the app is open" is on (Settings), or
2. the touch lock is on and "Keep screen on while the screen is locked" is on (Settings, **on by default**).

- **Status:** Settings shows whether it is active.
  The lock screen hint says "The screen stays on." only when it really is.
- Unlocking, switching the setting off, or leaving the condition releases it immediately.
- **Android:** `FLAG_KEEP_SCREEN_ON` on the window while the condition holds. It is reliable, so there is no fallback.

### Acceptance checks

- Locking the screen starts keep-awake without touching the Settings toggle; unlocking releases it.
- If the browser drops the wake lock, the app gets it again within about a second.
- In fullscreen, the brightness button is visible and adjusts system brightness.
- Outside fullscreen there is no app-level dimming.
- Dimming also applies while locked, and survives a reload.


---

## Spec: Pronunciation rules

Rewrite the **spoken** text only. The text on screen never changes.

### Rule

| Field | Meaning | Limits |
|---|---|---|
| `from` | the word as written | 1 to 60 characters, trimmed |
| `to` | how it should be said (can be empty = say nothing) | 0 to 120 characters |
| `whole` | whole word only (default true) | boolean |
| `matchCase` | respect upper/lower case (default false) | boolean |

Stored as `{ "global": [rule...], "novels": { "<novelKey>": [rule...] } }`. At most 300 rules per list and 200 novels.
Rules with the same `from` (ignoring case), `whole` and `matchCase` in one list are the same rule: adding again replaces it.

### Applying (identical on both platforms)

1. Rules for the current novel come first, then the global rules. Inside each list, **longer `from` first**.
2. For each rule, in order, replace every match in the text with `to` (literal text, no special characters).
3. `whole` = the match must not touch a letter, digit or underscore on either side (Unicode letters and digits count).
   Without `whole` the match may be inside a word.
4. Matching ignores case unless `matchCase`.
5. If case is ignored, the matched text starts with a capital letter and `to` starts with a lowercase letter,
   the first letter of `to` is capitalised ("Dao" with rule dao -> dow gives "Dow").
6. The result then goes through the normal speech clean-up (markup characters removed).
7. Rules are applied to each sentence group before it is spoken. Sections already prepared are not redone, so a change is
   heard from the next unprepared section; stopping and playing again hears it immediately.

`novelKey` is the same key the Library uses: host without `www.` plus the path without its last segment if that segment contains a digit.
Text pasted with no link has no novel, so only global rules apply to it. If a link is in the address box when text is pasted,
the text belongs to that link's novel.

### Screens

- Settings card **Pronunciation** and a speaker button in the reader header both open the rules panel.
  The reader button starts on "This novel"; Settings starts on "All novels".
- The panel has: scope switch, written-as, say-it-as, whole word, match case, **Listen** (speaks the "say it as" text with the
  current voice, without disturbing the chapter position), **Add rule**, and the list (Listen and delete per rule).
- Test vectors: `shared-tests.json` -> `pronunciation`, `sanitizePron`. Both apps must produce the same output.

| Android mapping | |
|---|---|
| Rules | data class + `Regex` with `RegexOption.IGNORE_CASE` and Unicode lookarounds `(?<![\p{L}\p{N}_])` |
| Listen | a short separate `AudioTrack` stream; pause the narration first |
| Where | in the chunk builder, just before the speech clean-up |

---

## Spec: Backup and restore

One JSON file holds everything the app owns.

### File (`schema` 1)

```json
{
  "app": "PageWhisper", "schema": 1, "appVersion": "0.12.0", "platform": "android", "exportedAt": "2026-11-17T10:00:00Z",
  "reading": { "font": "serif", "size": 19, "line": 1.8, "para": 1.05, "pad": 4, "ls": 0, "align": "left",
               "weight": 400, "hyph": false, "theme": "dark", "hl": "violet", "hlStyle": "fill" },
  "settings": { "voice": "af_heart", "engine": "auto", "rate": 1, "chunk": "280", "ambVol": 0.35,
                "auto": false, "awake": false, "lockAwake": true, "ambOnly": true, "amb": "off" },
  "brightness": 100,
  "sleepLastMinutes": 30,
  "novels": [ { "id": "k3j9x1", "title": "Blue Moon", "url": "https://...", "chapter": "Chapter 12",
                "updated": 1763350000000, "cover": "data:image/jpeg;base64,..." } ],
  "savedChapters": [ { "title": "...", "text": "...", "url": "https://...", "next": "https://..." } ],
  "pronunciations": { "global": [ { "from": "Longxuan", "to": "Long shwen", "whole": true, "matchCase": false } ], "novels": { } }
}
```

- Every section is optional. `cover` is a small JPEG data URL (or null). Android may store covers as files but exports them in this form.
- `engine` is an old setting that is ignored.
- Dates in `updated` are milliseconds since 1970.

### Export

- **Back up everything** downloads `pagewhisper-backup-YYYY-MM-DD.json`. Where the browser can share files, **Share the backup file** also appears.
- The last backup time is shown. (Android: Storage Access Framework `CreateDocument`.)

### Import (nothing in the file is trusted)

1. Refuse files over 60 MB, files that are not JSON, files whose `app` is not `PageWhisper`, and files with a `schema` above the app's.
2. Clean everything: novels need a valid `http(s)` link (and get a fresh safe id if theirs is odd or repeated); at most 2000 novels
   and 2000 saved chapters; saved chapters need text; links must be `http(s)`; covers must be JPEG, PNG or WebP data URLs up to
   400,000 characters (anything else, including SVG, is dropped but the novel is kept); settings and reading style are
   checked against their allowed values and ranges; brightness 10 to 100; sleep minutes 1 to 600.
3. Show a summary (counts, app version, date) and ask **Merge** (default) or **Replace everything**.

**Merge:** novels are matched by `novelKey(url)`; the one with the larger `updated` supplies title, link and bookmark;
a cover is taken from the newer one, or from the backup when the local novel has none. Nothing is deleted.
Saved chapters are added when no chapter with the same link and title exists. Pronunciation rules are combined (a local rule wins
over a backup rule with the same key). Settings, reading style, brightness and the sleep time are left alone.

**Replace:** every section is replaced by the backup's (a missing section is cleared); the app then restarts.
If storage runs out part-way, the previous data is put back.

Test vectors: `shared-tests.json` -> `backupMerge`. Both apps must produce the same merged result.

### Acceptance checks

- Export, wipe the browser data, restore with Replace: library, covers, saved chapters, rules, theme, voice and brightness all return.
- Merge never duplicates a novel and keeps a newer local bookmark.
- A hostile file (script in a title, `javascript:` links, SVG or oversized covers, out-of-range settings) is cleaned, never executed.
- Titles from a backup are always shown as plain text.


---

## Spec: Voices and engines

Two voice engines run through sherpa-onnx; the person picks one in Settings → Voice.

### Engines
- **Kokoro** (default): the full-precision multilingual model (about 350 MB); the app offers its 28 English voices.
- **Supertonic 3**: the int8 package that sherpa-onnx publishes (`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`, about 150 MB): 10 voices (Female 1 to 5, Male 1 to 5; speakers 0 to 9) and 31 languages. The language of the text is chosen separately and sent with every request.
- Each engine has its own model, downloaded once from Settings → Voice model, kept on the phone and usable offline. A model that is not the one in use can be removed to free space.
- Supertonic needs a sherpa-onnx AAR of version 1.13.2 or newer in `app/libs`.
- The audio of the two engines has different sample rates; the player always uses the rate the engine reports. Saved speech is keyed by engine, language, speaker, speed and text, so it is never mixed up.
- Switching engine, voice or language applies from the next section. Heat control, chunk size, speed, preparing chapters and pronunciation rules work for both.

### Hiding voices and languages
- Settings → Voice → "Choose which voices to show": a list of ticks for the 28 Kokoro voices, the 10 Supertonic voices and the 31 Supertonic languages. Unticked ones disappear from the lists; nothing is deleted and they can be ticked again at any time.
- At least one item stays ticked in each list. A voice that is chosen but hidden keeps working until another is picked.
- Stored in the settings as ids: a Kokoro voice is its name, a Supertonic voice is `st:<speaker>`, a language is `lang:<code>`. Not part of backups.

### Languages (Supertonic)
Arabic ar, Bulgarian bg, Croatian hr, Czech cs, Danish da, Dutch nl, English en, Estonian et, Finnish fi, French fr, German de, Greek el, Hindi hi, Hungarian hu, Indonesian id, Italian it, Japanese ja, Korean ko, Latvian lv, Lithuanian lt, Polish pl, Portuguese pt, Romanian ro, Russian ru, Slovak sk, Slovenian sl, Spanish es, Swedish sv, Turkish tr, Ukrainian uk, Vietnamese vi.

### Acceptance checks
- 28, 10 and 31 entries; hidden ids leave the lists; the choice follows the engine; bad settings are repaired; saved speech keys differ by engine and language (`VoiceCatalogTest`, `PreparedTest`).
- Not covered by tests (needs a phone): downloading and loading the Supertonic model, how the voices sound, speed on a real phone.

---
## Spec: Prepared chapters

The app prepares the chapters after the one being read, so they open at once, work without a connection, and play without making the phone work hard.

### Setting (Settings → Prepare next chapters)
- **Off:** nothing is prepared; anything prepared is deleted.
- **Text:** the next 3 chapters are fetched in the background.
- **Text + speech (default):** as Text, and the speech for those chapters is made and saved.
- "Clear prepared chapters" deletes everything prepared.

### Text
- Each time a chapter is opened, the next 3 chapters are found (the novel's chapter list, else the "next" links) and any not yet saved are fetched, one at a time with a short pause. A failure (offline, blocked, no such chapter) stops quietly.
- A saved chapter opens without a request. Imported books need no fetching; their speech is prepared the same way.
- Saved in the app's cache folder, one file per chapter.

### Speech
- Made by a background job (WorkManager) that the system starts whether or not the phone is charging, as long as the battery is not low. It starts 20 seconds after a chapter is opened and is replaced when another chapter is opened.
- It makes the speech section by section with the current voice, speed, chunk size and pronunciation rules, and saves it as 16-bit audio. A section is found again by voice, speed and its exact spoken text, so changing any of them makes new speech and never plays the wrong audio.
- It waits while someone is listening (the player has the processor), and stops, to try again later, when the phone reports heat. It also stops when the phone is not charging and the battery is under 30%.
- The player uses saved speech for a section when it has it. Speech that the player makes itself is also saved, so replays and the previous chapter need no new work.

### Clean-up
- Whenever a chapter is opened, saved text and speech of chapters that are not the open one, one of the next 3, or (when the chapter list knows them) one of the previous 3, are deleted.
- The saved speech is kept under 600 MB: the least recently used chapters go first, never the open one.
- Android may empty the cache folder at any time; nothing depends on it.

### Acceptance checks
- Saved samples read back within 0.0002 of the original; a section is found by voice, speed and text only (`PreparedTest`).
- Not covered by tests (needs a phone): the background job, the battery and heat conditions and fetching ahead.

---
## Spec: Books (EPUB and PDF)

A book is imported from a file, kept on the phone, and read like a novel. Nothing leaves the phone.

### Import
- **Open tab → Import a book** (system file picker, types `application/epub+zip`, `application/pdf`), or "Open with PageWhisper" on such a file (intent filter on VIEW). The file's contents decide the kind, not its name: `%PDF` is a PDF, `PK` is an EPUB.
- The file is copied to the cache first (limit 400 MB), read, and the copy is deleted. The status line shows "Reading page n of N" for a PDF.
- On success the book is saved, added to the Library, and its first chapter opens. On failure the message says why: not an EPUB or PDF, protected (DRM or password), a scan without text, nothing readable.

### Storage
- `files/books/<id>/meta.json` (id, title, author, kind, chapters: title and file) and `c<n>.txt` per chapter.
- In the Library a book is a novel with address `book://<id>/<n>` and key `book:<id>`. Its chapter list is saved as an ordinary chapter list (numbers 1 to N), so bookmarks, the Chapters sheet, previous/next, auto-next and Continue work unchanged.
- Removing the novel deletes the book's files, cover and list.
- Not in a backup. "Replace everything" on restore removes imported books; "Merge" keeps them.
- Not offered for books: Open in browser, Edit link, Update chapter list, Build by number, finding covers online.

### EPUB (`EpubReader`)
- Order: the book's spine. Titles: the EPUB 3 nav file, else the NCX file.
- A file listed at several anchors is split at those anchors. A file with no title and no heading is joined to the chapter before it; a short file with no title (cover or title page) is skipped.
- Skipped inside text: scripts, styles, images, ruby notes, footnote and endnote parts, note marks, page-break markers.
- Text is taken from paragraphs, headings, list items, quotes and table cells, in order. A chapter's heading is its first paragraph, so it is read once (title and first paragraph are not repeated).
- The cover is the book's cover image, scaled like any cover.
- DRM (`xmlenc#aes` in `encryption.xml`) is refused. Font obfuscation is not DRM.

### PDF (`PdfReader`, `PdfText`)
- Text per page by PDFBox, sorted by position.
- Page numbers (digits, or Roman numerals up to 399, alone on a line at the top or bottom) and lines that repeat at the edge of 30% of the pages (minimum 3) are dropped.
- Lines become paragraphs: a sentence-ending line that is clearly shorter than the usual line ends a paragraph; a heading line (`Chapter 3`, `Prologue`, or a short all-capitals line of two or more words) stands alone; `word-` + lowercase start joins the word. A paragraph may continue across a page break.
- Chapters, in order of preference: the PDF's bookmarks (top level, or the second level when the top has a single root); headings `Chapter/Part/Book <number>` found in the text, at least 3, with a short unpunctuated line after them added to the title; blocks of 10 pages titled "Pages a-b".
- Cover: the first page drawn small. Title and author come from the file's information, else the file name.
- Refused: password-protected PDFs, and PDFs with almost no text (scans). Text recognition (OCR) is not part of the app.

### Acceptance checks
- A small EPUB 3 gives its chapters with their titles; an NCX book splits one file at anchors; an untitled file joins the chapter before; a DRM book is refused (`BooksTest`).
- Page numbers and repeated headers are removed; lines join into paragraphs; hyphenated words rejoin; chapters come from bookmarks, from headings, or from blocks of 10 pages (`BooksTest`).
- Not covered by tests (needs a phone): the file picker, PDFBox reading a real PDF, covers.

---
## Spec: Chapter lists (table of contents)

Every novel in the Library can have a complete chapter list. It powers the **Chapters** sheet and makes Previous / Next
follow the real chapter order instead of guessing a link.

### Getting the list

- **Update chapter list** (per novel, in its Chapters sheet) and **Update all chapter lists** (Library) read the list from the
  novel's own page: the saved link without its chapter number (`novelPageUrl`).
- Each page is read directly, then through the reader service. The first that shows chapters is used.
- **Extra pages:** links found inside pagination areas (`.pagination`, `.pager`, `[class*=pagination]`, `[class*=paging]`,
  `rel=next`) are followed if they stay on the same site and under the novel's path (or are `page` links). If the first page gives fewer than 5 chapters, a
  link whose text is "chapter list", "all chapters", "table of contents", "view all" or "full list" is followed too.
  Limits: 40 pages and 6,000 chapters per novel.
- **Choosing chapter links** (`pickChapters`, checked by `shared-tests.json`):
  1. Take links on the same site (a leading `www.` is ignored).
  2. Keep those under the novel's path whose text or address names a chapter (`Chapter 12`, `ch-12`, `/12`), but never the list page
     itself (`chapter-list`, `chapters`, `all-chapters`, `table-of-contents`, `toc`).
  3. If fewer than 3 are found, accept any same-site link whose text names a chapter and whose address contains `chapter` or a digit.
  4. Remove repeats (ignoring `#` parts and a trailing `/`). The title is the link text (spaces tidied, at most 120 characters), or `Chapter N`.
- **Chapter number** (`chapterNumber`): "Chapter 12", "Ch. 12", "Episode 12" or "Ep 12" in the title wins; otherwise the last number in the last
  part of the address; otherwise none.
- **Order:** if at least 80% have numbers, oldest first by number (stable); otherwise the order on the page.

### Build by number (for sites that block fetching or hide the list behind scripts)

The user enters the highest chapter number N. The app builds chapters 1 to N by replacing the last number in the saved chapter link
(`urlForNumber`), titled `Chapter k`. It asks before replacing a list read from the site. The limit is 6,000.

### Storage

Per novel: `{ base, updated, source: "fetched" | "built", pages, items: [[number|null, title, relativeLink]...] }`.
Links under `base` are stored relative to it, others with a `!` prefix. If storage runs out, the oldest other lists are dropped first.
Lists are **not** part of a backup (they are quick to fetch again); a Replace restore clears them.

### Chapters sheet

- Header shows the novel and "N chapters · read from the site / built by number · date".
- **Update chapter list**, **Build by number**, a number box with **Go**, **Current** (back to the current chapter), and a
  switch for **Oldest first / Newest first** (a new order starts from its top).
- Rows: the current chapter is outlined; earlier chapters are dimmed as read. Tapping a row opens that chapter in the reader;
  a small button opens it in the browser.
- Only a window of 250 rows is drawn, around the current chapter, with **Show earlier / Show more** (250 at a time).
- After an update the message says how many are new.
- Library rows show "N chapters".

### Reader

- **Next / Previous** use the list when the current chapter is in it (matched by address, or by number). Otherwise Next uses the
  link found on the page, then a guess (add 1 to the number), and Previous uses your viewing history.
- The "next chapter" box is filled from the list when possible. Auto-next uses the same rule.

| Android mapping | |
|---|---|
| Parsing | Jsoup, same selectors and rules; Markdown links from the reader service |
| Storage | one JSON file per novel (not one big file); same compact form is fine, backup excludes it |
| Sheet | `LazyColumn` (real virtualisation, so no window needed), `ModalBottomSheet` |
| Updating | a coroutine with progress; **Update all** in a foreground-safe worker |

### Acceptance checks

- A novel with a paginated list yields every chapter once, oldest first; sidebar "latest" links and "Reviews" do not become chapters.
- A blocked site gives a clear message and Build by number produces the full range from the link pattern.
- The reader-service fallback produces the same list in order.
- A 1,200-chapter list opens instantly and loads more rows quickly.
- Next goes to the list's next chapter; Previous goes to the list's previous one; the bookmark follows.
- Removing a novel removes its list; lists survive a reload.
