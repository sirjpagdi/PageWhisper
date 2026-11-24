# Android changelog

## 0.3.0
- Reading fonts: bundled the advertised font families for offline use and added EB Garamond, Crimson Pro, Source Serif 4, and Montserrat.
  Montserrat now styles the app UI at a heavier medium weight; reader font selection remains independent.
- Readability: stronger Light and Sepia text contrast; accent-filled buttons and selected controls choose black or white text to
  match the accent shade.
- Reader: reset to the first paragraph when opening a different chapter, and speak the chapter title before its body without
  repeating a title already at the start of the body.
- About screen: added the “Developed by Sir_JPagdi” credit.
- Setup documentation now points to the repository root as the Android Studio project.
- versionCode 3, versionName 0.3.0.

## 0.2.0 (synced with PageWhisper Lab 0.13.3)
- Reading style: accent colour (Theme default, purple, blue, teal, green, rose, orange) and accent shade slider (-35..35).
  Stored in `ReadingStyle.accent` / `accentTone`; read from and written to the shared backup file.
- Speech buffering: generates the next section only when under 15 s of audio is queued (35 s with the screen off),
  instead of a fixed 4 sections ahead. Less heat and battery use.
- New launcher icon: adaptive icon with separate background and foreground layers, plus a themed (monochrome) layer
  for Android 13+. `play_store_icon_512.png` is the Play Store listing icon.
- versionCode 2, versionName 0.2.0.

## 0.1.0
- First full source drop (not built).
