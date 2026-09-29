# IAMTT Downloader — standalone Android prototype

This directory is a complete independent Android project. It does not import,
read, or modify the repository's existing Google TV app, Gradle configuration,
Google Drive authentication, signing key, settings, or media library.

Package: `com.iamtt.downloader`. Android 9+ (API 28). ARM64, ARMv7 and x86_64.

## Use

1. Install the separate `IAMTT-Downloader.apk`.
2. Open **Addons → Add Cinemeta catalogue**.
3. Add a compatible source addon you trust and are authorized to use.
4. In **Discover**, choose **Movies** or **TV Shows**.
5. Results show poster artwork and are sorted by **newest year first** by default.
   Use **Year** to filter and **Sort** to switch between newest/oldest.
6. For TV, open a show, choose a season and episode, then find sources for that
   episode. When a source provides a file index, IAMTT downloads only that selected
   episode even when the torrent contains a full season pack.
7. Downloads shows progress, speed, peers and pause/resume.
8. When complete, Play, Share, or **Save a copy** using the Android file picker.

Cinemeta supplies catalogue metadata and poster/episode artwork. The downloader
handles raw SHA-1 torrent sources selected by the user. Direct HTTP, debrid, HLS,
automatic cloud upload, and automatic whole-season downloading are not implemented.

### Recommended library folders

For media servers and the IAMTT streaming library, save copies with conventional
names so scanners can identify them reliably:

```
Movies/
  Dune Part Two (2024)/
    Dune Part Two (2024).mkv

TV Shows/
  Severance/
    Season 01/
      Severance - S01E01 - Good News About Hell.mkv
      Severance - S01E02 - Half Loop.mkv
    Season 02/
      Severance - S02E01 - Hello, Ms. Cobel.mkv
```

Use `S01E01` style episode numbers. MP4 and MKV are the safest library formats;
the downloader also accepts AVI, MOV, WebM, M4V, TS and M2TS when supplied by the
selected source.

## Storage and recovery

The app downloads the addon-selected `fileIdx`; without one, it selects the largest
video. It does not intentionally download every file in a pack. Shared boundary
pieces may occupy a small partfile. Only one torrent is active at a time. Jobs and
metadata persist; after process death, unfinished jobs reopen paused. Resume
rechecks existing pieces. Completion stops the torrent session for that job.

Videos initially use app-specific external storage. Uninstalling removes them.
Save a copy to keep them independently. Export requires keeping the app open;
an interrupted export may leave an incomplete destination copy. There is no
automatic cloud upload. Android's foreground-service time limits still apply;
the app pauses if Android ends its transfer window. Peer availability controls
whether a particular torrent can download.

Addon manifest URLs remain in private app storage with backup disabled. They may
contain tokens. The UI shows only the addon name and host. The app requests no
Google account permissions. Install only addon links you intend to trust.

## Build

Open **this directory**, not the repository root, in Android Studio, or use
JDK 17, Android SDK 35 and Gradle 8.11.1:

```sh
gradle -p torrent-downloader :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The independent `.github/workflows/downloader.yml` builds only this project and
uploads its own APK artifact. It does not publish or replace the existing IAMTT
release. The bundled prototype signing key is deliberately public and only for
personal test builds; it is separate from the existing app's key.

## Verification

Protocol tests cover configured addon URLs, search encoding, capabilities,
file indexes, tracker hints, multi-file selection and path traversal. CI compiles
the native-engine integration, runs the tests, and performs Android lint.
Real-device download, background transfer, network switching, pause/resume and
export still require a phone test before this should be called production-ready.

## Dependencies

libtorrent4j 2.1.0-39 (MIT), libtorrent (BSD), OkHttp (Apache-2.0), AndroidX (Apache-2.0).
The native distributions contain additional libraries; retain their upstream
license notices when distributing a production build.
