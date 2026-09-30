# IAMTT setup guide

This takes about 15 minutes once. You'll need a computer (for Google Cloud), the TV and your phone.

## 1. Let the app sign in with Google (Google Cloud, once)

Google only lets an app ask for Drive access if it's registered in a Google Cloud project.
You register your own copy once. Nothing needs to be downloaded or copied onto the TV.

Do this on a computer, signed in with any Google account (ideally the one that has your movies).

1. Open <https://console.cloud.google.com/> and create a new project, e.g. **IAMTT**.
2. Go to **APIs & Services → Library**, search for **Google Drive API** and click **Enable**.
3. Go to **APIs & Services → OAuth consent screen** (newer consoles call it **Google Auth Platform**)
   and click **Get started**:
   - App name: `IAMTT`. Support and contact email: yours.
   - Audience: **External**.
   - Under **Audience → Test users**, add the Gmail address that has your movies.
4. Go to **Credentials → Create credentials → OAuth client ID** (or **Clients → Create client**):
   - Application type: **Android**
   - Package name: `com.iamtt.streaming`
   - SHA-1 certificate fingerprint:
     ```
     83:A4:01:FA:6C:69:0D:70:C4:38:4C:56:84:14:2A:27:3F:2C:13:9A
     ```
   - Click **Create**. There's nothing to download.

> **Stay signed in for good (recommended):** while the app is in "Testing", Google may ask
> you to sign in again every 7 days. To avoid that, go to **Audience** and click
> **Publish app**. You don't have to submit it for Google's verification: an unverified app
> works for up to 100 accounts. When you sign in, you'll see a "Google hasn't verified this
> app" warning once: tap **Advanced → Go to IAMTT**. It's your own app.

The app only asks for **read-only** Drive access, so it can never change or delete anything.

## 2. Install the app on the Google TV

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: **Settings → Apps → Security & restrictions → Unknown sources → Downloader → On**.
   (On some Skyworth models this is under **Settings → Privacy → Security & restrictions**.)
3. Open Downloader and enter:
   `https://github.com/mohdaie/IAMTT-streaming-apps/releases/latest/download/IAMTT.apk`
4. Install, then open **IAMTT** from the apps row.

To update later, repeat step 3. The new version installs over the old one and keeps your settings.

## 3. Sign in on the TV

1. Open IAMTT. On the setup screen, press **Sign in with Google**.
2. Pick the Google account that has your movies. If it's the only account on the TV, this
   step is skipped. If your movies are in a different account, choose **Add account** to add it.
3. Google asks to let IAMTT **see your Google Drive files**. Press **Allow**.

The TV now shows "Signed in as …". To use another account later, open **Settings** in the
app and press **Switch account**.

## 4. Choose your folders (on your phone)

1. With your phone on the **same Wi-Fi**, scan the QR code on the TV (or type the address and PIN).
2. Under **Choose your folders**, browse **My Drive** or **Shared with me**. Tap a folder to
   look inside it, then tap **+ Add** next to the folder you want (or inside it, on "This folder").
   Shows and movies can share a folder: IAMTT sorts them by file name.
   You can also paste a folder link instead.
3. Scanning starts straight away and the TV shows the count as titles are found.
   When it's done, press **Done** on the TV.

Only the folders you add are scanned. To change them later, open **Settings** in the app.

## On an Android phone or tablet

IAMTT also works with touch. Open the APK link from step 2 in the phone's browser and install it.
Then tap **Sign in with Google**, followed by **Choose folders here**: the folder picker opens inside
the app (no Wi-Fi needed). Pick your folders, then tap **Done** at the top and **Done** again.

## Naming your files

IAMTT works out by itself which files are TV episodes and which are movies, from their names,
whatever folder they're in. It then finds posters, descriptions and episode names automatically
(TVmaze for shows, Wikipedia for movies). These names work best:

- Episodes: `Show Name - S01E01 - Episode Title.mkv`, `Show.Name.S01E01.1080p.mkv`, `Show 1x01.mkv`,
  or `Show Name/Season 1/Episode 1.mkv`
- Movies: `Movie Title (2010).mkv` or `Movie.Title.2010.1080p.mkv` (the year helps find the right poster)
- Subtitles: put `.srt`, `.vtt` or `.ass` files next to the video with the same name, optionally with a
  language, e.g. `Movie Title (2010).en.srt` and `Movie Title (2010).ms.srt`, or in a `Subs` folder beside it.
  They're switched on automatically; use the subtitles button in the player to change or turn them off.

For example:

```
Movies/
  Inception (2010).mkv
  The Dark Knight (2008)/The Dark Knight (2008).mkv
TV Shows/
  Breaking Bad/
    Season 01/
      Breaking Bad S01E01.mkv
```

## Advanced: use a service account instead of signing in

Signing in gives the app read-only access to your whole Drive, although it only ever scans
the folders you choose. If you'd rather Google itself limit the app to specific folders, use a
*service account*: a separate robot account that can only see folders you share with it.
This replaces step 1 and step 3 above.

1. In the same Cloud project (with the Drive API enabled), go to
   **IAM & Admin → Service Accounts → Create service account**. Name it e.g. `iamtt-tv`,
   skip the optional steps and click **Done**.
2. Open it → **Keys → Add key → Create new key → JSON**. Keep the downloaded file private
   and **never upload it to GitHub**. Get it onto your phone, e.g. by emailing it to yourself.
3. Open the setup page from the TV's QR code, open **Advanced: use a service account key instead**,
   choose the `.json` file and tap **Upload**. Copy the address it shows
   (`iamtt-tv@your-project.iam.gserviceaccount.com`).
4. In Google Drive, share your Movies and TV Shows folders with that address as **Viewer**
   (untick "Notify people"), then tap **Refresh** on the setup page and add them.

Uploading a key replaces the Google sign-in, and pressing **Sign in with Google** on the TV
replaces the key.

## Troubleshooting

| Problem | Fix |
|---|---|
| "Google sign-in isn't set up for this app yet", or `UNREGISTERED_ON_API_CONSOLE` | The Android OAuth client is missing or has a typo. Check the package name and SHA-1 in step 1.4, that its type is **Android**, and that it's in the same project as the consent screen. Then wait 5 minutes. |
| "Access blocked: IAMTT has not completed the Google verification process" | Add your Gmail as a test user (step 1.3) or publish the app. |
| "Google hasn't verified this app" | Expected for your own app. Tap **Advanced → Go to IAMTT**. |
| "Google sign-in … has expired" | Open **Settings** and press **Switch account**. If this happens weekly, publish the app (see step 1). |
| "The Android client with this package name and SHA-1 already exists" | Someone else registered this app in their own Cloud project. The app's package name needs changing in the code. |
| "Google Drive API isn't turned on" | Do step 1.2 in the same Cloud project, then wait a minute. |
| A folder is missing when browsing | Folders other people shared with you are under **Shared with me**. Folders in a shared drive aren't listed: paste the folder link instead. |
| "Folder not found" | The signed-in account can't open that folder. With a service account, share the folder with its address first. |
| QR page won't open | The phone and TV must be on the same Wi-Fi, and some routers block devices from seeing each other ("AP/client isolation"). |
| Video stops with "Drive refused the stream" | Google limits how often one file can be downloaded per day. Try again later. |
| No sound on some files | The TV may not decode DTS/TrueHD. Use a soundbar with passthrough, or AAC/AC3 audio. |
