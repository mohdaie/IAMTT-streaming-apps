# IAMTT Web

A separate browser/PWA version of IAMTT. The Android streaming app and standalone downloader are unchanged.

## Included

- Read-only Google Drive connection through Google Identity Services.
- Browse My Drive/shared folders or add a folder URL; paginated recursive scanning of selected folders, including folder shortcuts.
- Movies and grouped TV shows recognised from filenames, season folders and release names; duplicate episodes prefer higher resolution.
- Automatic posters/details from TVmaze and Wikipedia, with a text fallback when metadata is unavailable.
- Browser-local profiles, search, resume history, recently added titles, episode lists and optional next-episode playback.
- Range streaming through a service worker. Private video bytes go directly from Google Drive to the browser and are never cached or buffered as a whole file. Access tokens stay in page/service-worker memory, never in URLs or localStorage; credentials are scoped per browser tab.
- Local file/folder playback; files stay on-device and must be reselected after reopening. Sidecar SRT, VTT and basic ASS/SSA subtitles, plus manual subtitle import.
- Responsive phone/desktop layouts, native player controls/full-screen, keyboard seeking and installable PWA shell.

## Google setup (once)

The Android OAuth client cannot be reused as a Web client. In the **same Google Cloud project**:

1. Enable Google Drive API.
2. Create **OAuth client ID → Web application**.
3. Add the app's exact origin to **Authorized JavaScript origins**, without a path or trailing slash. The app displays this in Settings → Set up Google sign-in.
4. Add your Google account as an OAuth test user while the project is in testing.
5. Paste the client ID into IAMTT Settings, save, and connect Google Drive. No client secret is required.

Google's browser token model needs reconnection after access tokens expire and after reopening the browser. IAMTT preserves viewing progress. Connecting a different Google account clears the previous account's selected folders and cached file list. Disconnect also clears that file list; Google grants themselves can be revoked in your Google account settings.

## Browser limits

MP4/H.264/AAC is the most compatible combination. MKV, AVI, HEVC, DTS/AC3 and other combinations may fail depending on the browser/device. There is **no transcoding server**. Native browser players determine support for embedded audio/subtitle tracks. Online OpenSubtitles retrieval, Android profile-photo imports and cross-device/Android history sync are not included in this first web version. ASS subtitle text is converted to WebVTT; advanced ASS styling/animation is not preserved.

Profiles, folder selections, metadata and viewing progress use **this browser's** storage, not shared/cloud storage. Google passwords, service-account private keys and OAuth client secrets are never requested. Video playback/Drive folders require real Google authorization; no sample media is passed off as a connected library.

The Sites deployment starts private. A private deployment and Google Drive authorization are separate access boundaries. Settings shows the actual current origin to register with Google.

## Development and hosting

No runtime packages or build step are needed. Serve `dist/` over HTTPS (localhost works for development); service workers cannot run from `file://`.

```sh
npm test
npm run check
python3 -m http.server 8080 --directory dist
```

Static assets, the manifest, the service worker and private-media routes adapt to the hosting path, including GitHub Pages project paths. The GitHub Pages workflow deploys only `web-app/dist/` after tests pass.

GitHub Pages URL: https://mohdaie.github.io/IAMTT-streaming-apps/

One-time repository setting: **Settings → Pages → Build and deployment → Source → GitHub Actions**. If Pages was not enabled when the workflow ran, rerun **Deploy IAMTT Web to GitHub Pages** after selecting this source.

For Google OAuth, authorize **https://mohdaie.github.io** (the origin only, without the repository path), then paste the Web client ID into IAMTT Settings. The HTML, CSS and JavaScript are public on GitHub Pages; each visitor still needs their own Google Drive grant, and tokens/media are not published in the repository.

Tests exercise filename parsing, catalogue grouping, subtitle conversion, Drive byte-range forwarding and per-tab token isolation. Browser/device playback and real Google OAuth must be checked with the user's account after client setup.
