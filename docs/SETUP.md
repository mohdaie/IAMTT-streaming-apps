# IAMTT setup guide

This takes about 15 minutes once. You'll need a computer (for Google Cloud), your phone and the TV.

## 1. Create the app's Google identity (service account)

Do this on a computer, signed in with the Google account that owns your movies.

1. Open <https://console.cloud.google.com/> and create a new project, e.g. **IAMTT**.
2. Go to **APIs & Services → Library**, search for **Google Drive API** and click **Enable**.
3. Go to **IAM & Admin → Service Accounts → Create service account**.
   - Name: `iamtt-tv` (anything works).
   - Skip the optional "roles" and "user access" steps, then click **Done**.
4. Open the new service account → **Keys** tab → **Add key → Create new key → JSON**.
   A `.json` file downloads. This is the app's password, so keep it private and
   **never upload it to GitHub**.
5. Get that file onto your phone, for example by emailing it to yourself.

> The service account is a separate robot account. It can only see folders you
> explicitly share with it, and the app only asks for read-only access, so it can
> never change or delete anything in your Drive.

## 2. Install the app on the Google TV

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: **Settings → Apps → Security & restrictions → Unknown sources → Downloader → On**.
   (On some Skyworth models this is under **Settings → Privacy → Security & restrictions**.)
3. Open Downloader and enter:
   `https://github.com/mohdaie/IAMTT-streaming-apps/releases/latest/download/IAMTT.apk`
4. Install, then open **IAMTT** from the apps row.

To update later, repeat step 3. The new version installs over the old one and keeps your settings.

## 3. Connect your folders (on your phone)

1. Open IAMTT on the TV. It shows a QR code, an address and a 4-digit PIN.
2. With your phone on the **same Wi-Fi**, scan the QR code.
3. **Step 1 – key:** choose the `.json` file and tap **Upload**. The page shows the app's
   address, which looks like `iamtt-tv@your-project.iam.gserviceaccount.com`. Tap **Copy address**.
4. **Step 2 – share:** in the Google Drive app or website, share your **Movies** folder with that
   address as **Viewer**. Untick "Notify people". Do the same for your **TV Shows** folder.
5. Back on the setup page, tap **Find shared folders**, then **+ Movies** or **+ TV Shows**
   next to each one. You can also paste a folder link instead.
6. Scanning starts straight away and the TV shows the count as titles are found.
   When it's done, press **Done** on the TV.

Only the folders you add are scanned. To change them later, open **Settings** in the app.

## Recommended folder layout

Scanning works with any layout, but Phase 2 (posters and episode info) matches titles
from file names, so this layout gives the best results:

```
Movies/
  Inception (2010).mkv
  The Dark Knight (2008)/The Dark Knight (2008).mkv
TV Shows/
  Breaking Bad/
    Season 01/
      Breaking Bad S01E01.mkv
```

## Troubleshooting

| Problem | Fix |
|---|---|
| "Google Drive API isn't turned on" | Do step 1.2 in the same project the key came from, then wait a minute. |
| "Find shared folders" shows nothing | Sharing can take a minute to appear. Or paste the folder link instead. |
| "Folder not found" | The folder isn't shared with the app's address yet. |
| Can't create a key ("key creation is disabled") | Your Google account is managed by an organisation that blocks keys. Use a personal Gmail account for the Cloud project. The movies can stay where they are. Just share the folders with the new service account. |
| QR page won't open | The phone and TV must be on the same Wi-Fi, and some routers block devices from seeing each other ("AP/client isolation"). |
| Video stops with "Drive refused the stream" | Google limits how often one file can be downloaded per day. Try again later. |
| No sound on some files | The TV may not decode DTS/TrueHD. Use a soundbar with passthrough, or AAC/AC3 audio. |
