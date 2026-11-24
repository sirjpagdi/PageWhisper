# Android changelog

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
