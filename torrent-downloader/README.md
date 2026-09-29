# IAMTT Downloader — standalone Android prototype

This directory is a complete independent Android project. It does not import,
read, or modify the repository's existing Google TV app, Gradle configuration,
Google Drive authentication, signing key, settings, or media library.

Package: `com.iamtt.downloader`. Android 9+ (API 28). ARM64, ARMv7 and x86_64.

## Use

1. Install the separate `IAMTT-Downloader.apk`.
2. Open **Addons → Add Cinemeta catalogue**.
3. Open **Configure Torrentio**. Leave Debrid Provider unset for raw torrent sources.
4. Tap Install in its configuration page and choose IAMTT Downloader, or copy
   its configured manifest URL and use **Paste addon link**.
5. Search in Discover, open **Find download sources**, choose **Download to phone**.
6. Downloads shows progress, speed, peers and pause/resume. Wi-Fi-only is on by default.
7. When complete, Play, Share, or **Save a copy** using the Android file picker.
   Drive is available there only if its installed document provider offers it.

Torrentio is a source addon; Cinemeta supplies search/catalogue data. Only movies
and raw SHA-1 torrent sources are supported in this first prototype. Direct HTTP,
debrid, HLS, series episode browsing and cloud-server downloads are not implemented.
No movie files, addon credentials or user data are bundled or uploaded to GitHub.

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
