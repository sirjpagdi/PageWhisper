# PageWhisper for Android

A native Android reader that speaks web novel chapters with Kokoro voices that run **on your phone**. Kotlin and Jetpack Compose, no WebView in
the app's own screens, no account, no server. `FEATURES.md` holds the spec of its features.

> **0.5.0:** a Heat control setting (Fast, Balanced, Cool) with automatic fallback, a choice between the Standard and the Light (int8) voice
> model, justified card descriptions, and the web version's references removed. Builds on 0.4.0's bundled fonts, stronger text contrast,
> chapter title narration and brightness controls.
> See [the changelog](docs/CHANGELOG.md) for details.

## Getting started

1. Install the app and open it. Allow notifications (they carry the lock-screen controls).
2. Go to **Settings → Voice model → Download** (about 350 MB, use Wi-Fi). The model is stored on the phone and reading works offline afterwards.
3. Open a chapter: paste text, share a link to PageWhisper, import a `.txt` file, or use **Read in browser** for sites with bot protection.
4. Press play. For listening with the screen off, set the app's battery usage to **Unrestricted**.

## Fonts

Serif, Sans and Mono are built in. Medium-weight Montserrat is used throughout the app UI; the reader's font remains independently selectable.
The following bundled reader fonts work offline; variable fonts include multiple weights:

| Font | Bundled file(s) |
|---|---|
| Literata | `font_literata.ttf` |
| Merriweather | `font_merriweather.ttf` |
| Lora | `font_lora.ttf` |
| Atkinson Hyperlegible | `font_atkinson.ttf`, `font_atkinson_bold.ttf` |
| Lexend | `font_lexend.ttf` |
| OpenDyslexic | `font_dyslexic.otf`, `font_dyslexic_bold.otf` |
| EB Garamond | `font_ebgaramond.ttf` |
| Crimson Pro | `font_crimsonpro.ttf` |
| Source Serif 4 | `font_sourceserif4.ttf` |
| Montserrat | `font_montserrat_regular.ttf`, `_medium`, `_semibold`, `_bold` (static weights) |

All font licenses are included in `app/src/main/assets/font-licenses/`. The fonts add about 9.1 MB to the app.

## What is in the project

```
app/src/main/java/com/pagewhisper/app/
  PageWhisperApp.kt, MainActivity.kt        app start, share-sheet input
  model/Models.kt                           data classes
  text/   TextTools, Pronunciation, Extractor, ChapterList     pure logic, unit tested
  data/   Repo (library, covers, lists), JsonFile, BackupCore (pure), BackupIO
  net/    Fetcher (OkHttp), TocFetcher (chapter lists)
  tts/    ModelStore (download), TtsEngine (sherpa-onnx), Voices
  playback/ Playback (speech pipeline, highlight timing, sleep timer), PlaybackService (foreground + notification), Ambient (sound generators)
  ui/     AppViewModel, screens (Reader, Open, Library, Settings), Sheets, BrowserFetchScreen, Theme, Icons, Components
app/src/test/   SharedVectorsTest.kt + shared-tests.json (fixed cases for the text tools)
```

Choices worth knowing: the library is plain JSON files (no database); media controls use `MediaSessionCompat` (simple and stable; Media3 can come
later); the model is downloaded, never bundled.

## Features

Fetch by link, paste, clipboard, `.txt` import, share-sheet input; 28 Kokoro voices and speed; sentence highlight and tap
to jump; fullscreen; previous/next chapter with auto-next; novel library with moving bookmarks; saved chapters; covers (Library list only);
**chapter lists**; ambient sounds; sleep timer; touch lock with hold-to-unlock; reading style (fonts, spacing, alignment, four themes, highlight
styles); pronunciation rules; one-file backup and restore.

What Android adds:

- Playing with the screen off through a foreground service, a notification, lock-screen controls and headset buttons.
- Several CPU threads for speech, with a heat control (Fast, Balanced, Cool) and a choice of voice model (Standard or Light int8).
- Direct fetching, and **Read in browser** for sites with bot protection: pass the check yourself, then read the page.
- In-app and system brightness controls that stay in sync; keep-screen-on with `FLAG_KEEP_SCREEN_ON`; the back button is blocked while locked.
- Voice models stored once on the phone.

Not the same: the "outline" highlight style is drawn as an underline with a light fill (Compose spans cannot draw a ring around text); chapter lists
are not part of a backup (they are fetched again).

## Publishing on Google Play (later)

- The voice engine bundles **espeak-ng (GPL-3)**. The simplest Play route is to publish the app's source under GPL-3; the other is replacing the
  phonemizer with a permissive one. This is not legal advice; decide before a public release.
- Declare the foreground service type (media playback), fill in the data-safety form (nothing leaves the phone except the links you fetch and the
  one-time model download), and keep the app a neutral reader: it ships no list of novel sites; every link is the user's.
- Keep the target SDK at the current Play requirement.

## Troubleshooting

| Problem | Try |
|---|---|
| "The voice is not installed yet" | Settings → Voice model → Download |
| Phone gets warm | pick **Balanced** or **Long** chunk size in Settings → Voice; speech already rests between sections and slows down on its own when the phone reports heat |
| Chapter title missing or wrong | reopen the chapter from its link so the title is read again, or type a title when pasting |
| Stops with the screen off | battery usage → Unrestricted; some phones also need "autostart" |
| A site will not fetch | Open → Read in browser, or paste the text |
