# IAMTT streaming app

A personal Google TV app that streams movies and TV shows **directly from Google Drive**,
with no server or NAS. It only scans the Drive folders you choose.

```
Google Drive (only the folders you share)  ──Drive API, byte ranges──▶  IAMTT on Google TV
```

**Setup:** see [docs/SETUP.md](docs/SETUP.md).
**Download:** [latest APK](https://github.com/mohdaie/IAMTT-streaming-apps/releases/latest/download/IAMTT.apk)
(built automatically by GitHub Actions on every push to `main`).

## How it works

- **Sign in on the TV:** press **Sign in with Google** and pick an account that's on the TV
  (or add one). Google Play services grants the app read-only Drive access, so it can never
  change or delete anything. It only scans the folders you choose.
- **Choose folders from your phone:** the TV shows a QR code and PIN. Your phone opens a small setup
  page served by the TV on your Wi-Fi, where you browse your Drive and pick the Movies / TV Shows folders.
- **Stricter option:** instead of signing in, upload a Google *service account* key from the phone
  and share only your movie folders with it, so Google itself enforces that the app sees nothing else.
  The key is stored only on the TV, never in this repo or the APK.
- **Remote or touch:** made for a Google TV remote, but it also works with touch on Android phones
  and tablets, where the folder picker opens right inside the app.
- **Playback:** Media3 ExoPlayer streams from the Drive API with HTTP range requests, so
  playback starts fast, seeking works, and nothing is downloaded in full.

## Roadmap

- [x] **Phase 1** – setup page, folder-only scanning, basic library rows, streaming playback, resume position
- [ ] **Phase 2** – file-name parsing (title/year, SxxEyy) and TMDB posters, plots, cast
- [ ] **Phase 3** – Netflix/Nuvio-style home: hero banner, Continue Watching, Recently Added, genres, detail pages, search
- [ ] **Phase 4** – subtitles (.srt next to videos), audio track picker, auto-play next episode
- [ ] **Phase 5** – polish and performance for big libraries (incremental rescans)

## Building yourself

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration on a
Google TV emulator or device, or run `./gradlew assembleDebug`. `./gradlew testDebugUnitTest` runs
the UI tests on the JVM (Robolectric).

Tech: Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, OkHttp, kotlinx.serialization.
