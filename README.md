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

- **Access:** a Google *service account* with read-only Drive access. You share only your
  Movies / TV Shows folders with it, so Google itself enforces that the app sees nothing else.
- **Setup from your phone:** the TV shows a QR code and PIN. Your phone opens a small setup page
  served by the TV on your Wi-Fi, where you upload the key and pick folders. The key is stored
  only on the TV, never in this repo or the APK.
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
Google TV emulator or device, or run `./gradlew assembleDebug`.

Tech: Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, OkHttp, kotlinx.serialization.
