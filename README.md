# PageWhisper for Android

A native Android reader that speaks web novel chapters with Kokoro voices that run **on your phone**. Kotlin and Jetpack Compose, no WebView in
the app's own screens, no account, no server. It is the native version of the web app ("PageWhisper Lab", `index.html`): every feature there is
here, and `FEATURES.md` is the spec for both.

> **0.2.0:** synced with PageWhisper Lab 0.13.3: accent colour and shade in the reading style, duration-based speech buffering
> (cooler, less battery), and a new launcher icon. Still not built or run; see below.
>
> **Status: written in full, not yet built or run.** I could not compile Android code where this was written, so expect a few small fixes on your
> first build (see "First build" below). What *was* checked is listed under "What was verified".

## Set up (about 20 minutes)

1. Install **Android Studio** (current stable) with JDK 17.
2. Open the repository root as a project. If Studio offers to upgrade the Android Gradle Plugin or Kotlin, accept it.
3. Put the **sherpa-onnx Android AAR** in `app/libs/` (see `app/libs/README.txt`). Get it from https://github.com/k2-fsa/sherpa-onnx/releases
   (file named like `sherpa-onnx-1.x.y.aar`). *No AAR in a release?* Use the `sherpa-onnx-v1.x.y-android.tar.bz2` instead: copy its `jniLibs/`
   to `app/src/main/` and the Kotlin files from the repo's `sherpa-onnx/kotlin-api/` into `app/src/main/java/com/k2fsa/sherpa/onnx/`.
4. Sync Gradle, then run on a **real phone** (arm64). Emulators need the `abiFilters` line removed in `app/build.gradle.kts`.
5. On first launch: allow notifications, then **Settings → Voice model → Download** (about 350 MB, use Wi-Fi). It is stored on the phone, and
   reading works offline afterwards.
6. Press play. For screen-off listening set the app's battery usage to **Unrestricted**.

## First build: the places most likely to need a touch

- `tts/TtsEngine.kt`: the sherpa-onnx Kotlin class and parameter names (`OfflineTtsKokoroModelConfig`, `lang`, `dictDir`...). Ctrl+click
  `OfflineTts` in the AAR and match its current constructor. This is the same note as in the spike project.
- `tts/Voices.kt`: the speaker number (`sid`) of each voice is assumed to be the alphabetical order of the voices (Heart = 3). If a voice sounds wrong,
  check the order on the model's page at k2-fsa.github.io.
- Compose API names move between versions (`ModalBottomSheet`, `FlowRow`, `LinearProgressIndicator(progress = { })`). The BOM in
  `gradle/libs.versions.toml` is a version that has them; if you upgrade and something is renamed, the error will say where.
- `ui/BrowserFetchScreen.kt`: `AndroidView(onRelease = ...)` needs a recent Compose UI.

## Fonts

Serif, Sans and Mono are built in. Montserrat is used throughout the app UI; the reader's font remains independently selectable.
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
| Montserrat | `font_montserrat.ttf` |

All font licenses are included in `app/src/main/assets/font-licenses/`. The fonts add about 9.1 MB to the app.

## What is in the project

```
app/src/main/java/com/pagewhisper/app/
  PageWhisperApp.kt, MainActivity.kt        app start, share-sheet input
  model/Models.kt                           data classes (same names and ranges as the web app)
  text/   TextTools, Pronunciation, Extractor, ChapterList     pure logic, unit tested
  data/   Repo (library, covers, lists), JsonFile, BackupCore (pure), BackupIO
  net/    Fetcher (OkHttp), TocFetcher (chapter lists)
  tts/    ModelStore (download), TtsEngine (sherpa-onnx), Voices
  playback/ Playback (speech pipeline, highlight timing, sleep timer), PlaybackService (foreground + notification), Ambient (sound generators)
  ui/     AppViewModel, screens (Reader, Open, Library, Settings), Sheets, BrowserFetchScreen, Theme, Icons, Components
app/src/test/   SharedVectorsTest.kt + shared-tests.json (the same cases the web app passes)
```

Choices worth knowing: the library is plain JSON files (no database); media controls use `MediaSessionCompat` (simple and stable; Media3 can come
later); the model is downloaded, never bundled.

## Features (all of the web version, plus what only Android can do)

Same as the web app: fetch by link, paste, clipboard, `.txt` import, share-sheet input; 28 Kokoro voices and speed; sentence highlight and tap
to jump; fullscreen; previous/next chapter with auto-next; novel library with moving bookmarks; saved chapters; covers (Library list only);
**chapter lists**; ambient sounds; sleep timer; touch lock with hold-to-unlock; reading style (fonts, spacing, alignment, four themes, highlight
styles); pronunciation rules; one-file backup and restore (the same file works in both apps).

Better on Android:

| | Web | Android |
|---|---|---|
| Playing with the screen off | best effort | foreground service, notification and lock-screen controls, headset buttons |
| Speech speed | one thread in the browser | several CPU threads |
| Fetching | blocked by browser rules; needs proxies | direct; no proxies |
| Sites with bot protection | not possible | **Read in browser**: pass the check yourself, then read the page |
| Brightness | dims below the system level | real window brightness |
| Keep screen on | wake lock with a fallback | `FLAG_KEEP_SCREEN_ON` |
| Back button while locked | cannot be blocked | blocked |
| Voice model | cached by the browser | stored once on the phone |

Not the same: the "outline" highlight style is drawn as an underline with a light fill (Compose spans cannot draw a ring around text); chapter lists
are not part of a backup (they are fetched again).

## Tests

`./gradlew testDebugUnitTest` runs `SharedVectorsTest`: pronunciation rules, sentence splitting, speech clean-up, titles, novel keys, next-link
guessing, chapter numbers, chapter picking and backup merging, all against `shared-tests.json`. Keep that file identical to the web project's copy.

### What was verified before you got this

- The shared vectors pass in the web app (62 cases) and in a line-for-line Java port of the Kotlin regex logic (28 cases: pronunciation,
  splitting, speech clean-up, titles). Kotlin's `Regex` is Java's, so this checks the part most likely to differ from JavaScript.
- The random generator used by the ambient sounds produces identical numbers to the web version.
- A cross-check of all source files: brackets balance, every call to another file's function exists, resources exist.
- **Not** verified: compilation, the sherpa-onnx calls, the Compose screens on a device, playback, and the foreground service. Those are the first
  things to try.

## Publishing on Google Play (later)

- The voice engine bundles **espeak-ng (GPL-3)**. The simplest Play route is to publish the app's source under GPL-3; the other is replacing the
  phonemizer with a permissive one. This is not legal advice; decide before a public release.
- Declare the foreground service type (media playback), fill in the data-safety form (nothing leaves the phone except the links you fetch and the
  one-time model download), and keep the app a neutral reader: it ships no list of novel sites; every link is the user's.
- Set the target SDK to the current requirement and raise `versionCode` for each upload.

## Troubleshooting

| Problem | Try |
|---|---|
| "The voice is not installed yet" | Settings → Voice model → Download |
| Nothing plays, error mentions sherpa | check the AAR is in `app/libs` and the names in `TtsEngine.kt` |
| A voice sounds like the wrong person | the `sid` order in `Voices.kt` |
| Stops with the screen off | battery usage → Unrestricted; some phones also need "autostart" |
| A site will not fetch | Open → Read in browser, or paste the text |
