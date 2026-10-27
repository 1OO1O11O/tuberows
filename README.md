# TizenTube Recommendations for Projectivy Launcher

Your YouTube **Subscriptions**, **Recommended** and **History** rows on the Projectivy Launcher home screen, with every video opening in **[TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt)**.

TizenTube Cobalt doesn't publish home-screen rows, so launchers like Projectivy have nothing to show for it. [SmartTube](https://github.com/yuliskov/SmartTube) does. This small Android TV app copies SmartTube's rows into rows of its own, and points every card at TizenTube instead.

> Unofficial. Not affiliated with YouTube, Google, TizenTube, SmartTube or Projectivy Launcher. On the TV, the app appears as **TizenTube Rows** (package `tv.tuberows.app`).

## What it does

1. **Reads SmartTube's home-screen rows** (Subscriptions, Recommended, History) from Android's TV provider, the same shared database launchers read from.
2. **Publishes its own copies of those rows**, with every card SmartTube provides (up to 100). Your launcher decides how many to show; Projectivy shows 30 per row. Subscriptions and Recommended are on by default. History is off by default; turn it on in the app if you want it.
3. **Opens every video in TizenTube Cobalt.** If TizenTube isn't installed, the video opens in SmartTube instead.
4. **Fills Watch Next** with up to 3 of your most recent History videos (*Continue*) and up to 5 new uploads from your subscriptions (*New*). This works even with the History row off, and can be turned off.
5. **Keeps the rows current:**
   - Copies SmartTube's changes about 20 seconds after SmartTube updates its rows.
   - Asks SmartTube to refresh 2 minutes after you open a video (so it shows in History), and when you open the app.
   - After the TV wakes, and every hour while the screen is on, asks SmartTube to refresh, unless SmartTube refreshed or was asked to in the last 30 minutes. Rows are usually fresh within a few minutes of turning the TV on.
   - Never asks SmartTube to refresh more than once every 10 minutes (the **Refresh SmartTube** button on the status screen is the only exception). SmartTube's own 15-minute refresh also keeps running.
   - Card text is SmartTube's own (channel, views, "11 minutes ago"). A row is updated when its videos change or its "x ago" wording does, so that text is at most one SmartTube refresh behind. The "x ago" check understands English; in other languages, text updates come with new videos.
   - Updates happen in place: only new, removed or changed cards are touched, so your launcher doesn't redraw the whole row or lose your place.
6. **Shows a status screen** when you open *TizenTube Rows*: row toggles, read access, which TizenTube and SmartTube it found, last sync, when it last asked SmartTube to refresh, when SmartTube's data last changed (with a warning if it hasn't for a day), and whether each app is exempt from battery optimisation, with the exact command to fix it if not.

## What it doesn't do

- **No internet access.** The app has no `INTERNET` permission and never contacts any server. Thumbnails are loaded by your launcher.
- **No Google sign-in, no accounts, no analytics.** Your YouTube data comes from SmartTube's sign-in.
- **No ad blocking of its own.** That's TizenTube's job.
- **No changes to SmartTube or TizenTube.** It only reads the rows SmartTube publishes, and asks SmartTube to refresh through the receiver SmartTube already uses at boot.
- **Nothing on the stock Google TV home screen.** Google TV doesn't show third-party channel rows. You need a launcher that does, such as Projectivy Launcher.

## Requirements

- **Android TV or Google TV**, Android 8.0 or later
- **[TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt)** (`io.gh.reisxd.tizentube.cobalt`)
- **[SmartTube](https://github.com/yuliskov/SmartTube)**, signed in to your YouTube account, with its Channels service on. That's the default; to check, go to SmartTube **Settings > Player > Developer options** and make sure **Disable Channels service** is *not* ticked. Sign SmartTube in to the same account as TizenTube, so history and recommendations match.
- **A launcher that shows app channels**, such as [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)
- **A computer with ADB** for one command (see below). Windows, macOS or Linux.

## Install

You can install the APK any way you like (Downloader, a file manager, or ADB). The app asks for its one permission itself: open **TizenTube Rows** and press **Grant access**.

**ADB is still needed once**, to exempt SmartTube from battery optimisation. Many TVs report that they're *not charging* even when plugged in, so Android throttles background apps there like a phone on battery. And because you'll never open SmartTube once this is set up, after about 8 days Android would limit its refreshes to roughly once a day, plugged in or not, and your rows would go stale. See [Why the battery exemption matters](#why-the-battery-exemption-matters).

### 1. Turn on Wireless debugging on the TV

1. **Settings > System > About**: select **Android TV OS build** seven times to unlock Developer options.
2. **Settings > System > Developer options**: turn on **Wireless debugging**. Note the **IP address and port** it shows.

Older Android TV versions without Wireless debugging use **Network debugging** or **ADB debugging** instead, on port `5555`.

### 2a. Windows: run the install script

1. Open [`tools/install.ps1`](tools/install.ps1) and click the **Download raw file** button (top right of the file). Read it first if you like.
2. Right-click the downloaded file and choose **Run with PowerShell** (on Windows 11 it's under **Show more options**).

If that option is missing, open PowerShell in the folder you saved it to and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

The script:

1. Finds `adb`, or offers to download Google's official platform-tools.
2. Downloads the latest release APK and checks its SHA-256.
3. Connects to the TV, and walks you through pairing if this computer hasn't been paired.
4. Installs the app, grants the permission, and sets the battery exemptions.
5. Opens the app once and tells you whether it found SmartTube's rows.

It doesn't change your PATH or anything else on your computer. If the TV shows an **Allow USB debugging?** prompt, tick **Always allow** and choose **Allow**. A log is saved to `%TEMP%\TizenTubeRecommendations\install.log`.

### 2b. Any computer: ADB commands

Download the APK from the [latest release](https://github.com/1OO1O11O/tuberows/releases/latest), then:

```sh
# Connect (use the IP:port shown under Wireless debugging)
adb connect 192.168.1.50:37451

# If that fails, pair first: on the TV choose "Pair device with pairing code",
# run this with the pairing IP:port, enter the code, then run adb connect again
adb pair 192.168.1.50:41923

# Install (use the file name you downloaded). -g grants the permission,
# so you can skip the Grant access button
adb install -r -g TizenTube-Recommendations-v1.2.1.apk

# Find SmartTube's package name (it differs between builds)
adb shell "pm list packages | grep -iE 'smarttube|teamsmart|liskovsoft'"

# Exempt SmartTube from battery optimisation (use the package name from the step above)
adb shell dumpsys deviceidle whitelist +org.smarttube.stable

# Optional: exempt TizenTube Rows too (opening cards already keeps it active)
adb shell dumpsys deviceidle whitelist +tv.tuberows.app

# Open the app once to start syncing
adb shell am start -n tv.tuberows.app/.MainActivity
```

Already installed the app without ADB? The status screen shows the exact exemption command for the SmartTube build on your TV.

Turn Wireless debugging off when you're done.

### 3. Show the rows in Projectivy Launcher

1. Open Projectivy **Settings > Channels**.
2. Enable the **TizenTube Rows** channels (Subscriptions, Recommended, and History if you turned it on).
3. Hide SmartTube's own channels, so you don't see each row twice.
4. Optional: enable Projectivy's **Watch Next** category to see the Continue and New entries.

## Why the battery exemption matters

Android's App Standby limits background work for apps you rarely open. Phones and tablets get a pass while charging, but many TVs report that they're **not charging** even on mains power, so the limits apply in full. On Android 13 and later, an app you haven't opened for about 8 days also lands in the *Restricted* bucket, where its background jobs run roughly once a day, even while charging. Refresh requests from this app don't count as you using SmartTube, so they can't prevent that.

The exemption simply restores SmartTube's normal behaviour: it refreshes on its own 15-minute schedule, the same load as anyone who uses SmartTube daily. Depending on your TV's standby mode, that may also run while the TV is in standby. This app only makes its wake-up and hourly refresh requests while the screen is on.

TizenTube Rows itself doesn't need the exemption: every card you open launches it, which keeps it active.

The exemption survives reboots. The status screen shows whether it's in place; if a firmware update or reset clears it, run the `deviceidle whitelist` command again.

To check an app's standby bucket: `adb shell am get-standby-bucket org.smarttube.stable` (`5` means exempt; `40` or higher means throttled).

## Row lengths

Each row shows what SmartTube publishes, which is shorter than what you can scroll inside SmartTube itself:

- **Subscriptions** is usually the longest, often more than Projectivy's 30.
- **Recommended** and **History** are often 10 to 20 videos. SmartTube's home-screen History comes from a shorter YouTube feed than its in-app History screen.

TizenTube Rows can't make a row longer than SmartTube publishes it without fetching from YouTube itself, which would mean an internet permission and a sign-in.

## Troubleshooting

| What you see | What to do |
|---|---|
| Status says **SmartTube rows not visible** | Open SmartTube once and check it's signed in and its Channels service is on (see Requirements). Then open TizenTube Rows again. |
| **Read access: MISSING** | Press **Grant access** on the status screen. If no prompt appears (Android stops asking after two refusals), run `adb shell pm grant tv.tuberows.app android.permission.READ_TV_LISTINGS` |
| **NOT exempt** under Battery exemption | Run the command the status screen shows, or run the install script again |
| **STALE** next to SmartTube data changed | SmartTube hasn't refreshed for a day. Check it's still signed in and exempt. |
| Rows don't appear in Projectivy | Projectivy **Settings > Channels**: enable the TizenTube Rows channels |
| An app tile in Projectivy does nothing | Projectivy may be showing a removed app. Restart the launcher or remove the tile. |
| Videos open in SmartTube | TizenTube Cobalt isn't installed, or was installed under a different package name |
| `adb connect` fails | Re-pair with `adb pair`, or use the script, which handles pairing. The connect port changes each time Wireless debugging is turned on. A timeout usually means the computer isn't on the same network as the TV. |
| Need logs | `adb logcat -d -s TubeRows` |

## Uninstall

```sh
adb shell dumpsys deviceidle whitelist -org.smarttube.stable   # your SmartTube package
adb shell dumpsys deviceidle whitelist -tv.tuberows.app
adb uninstall tv.tuberows.app
```

Android normally deletes an uninstalled app's rows and Watch Next entries by itself (on some TVs, only after the next restart). You can also uninstall from the TV's app settings.

## Limitations

- **It depends on how SmartTube publishes its rows.** If SmartTube changes that, rows may stop updating until this app is updated. A [weekly check](.github/workflows/upstream-check.yml) watches SmartTube's source for exactly this.
- **The content is SmartTube's.** Recommendations come from the account SmartTube is signed in to.
- **"x ago" text is a snapshot.** It's correct as of SmartTube's last refresh, so it can lag by up to about 15 minutes while the TV is on, and by however long the TV was off until the first refresh after waking.
- **Watch Next has no progress bars.** SmartTube's rows don't include playback position. TizenTube still resumes where you left off.

## Build it yourself

Pushes to `main` build a test APK, signed with a throwaway debug key if no signing secrets are set. Pushing a tag such as `v1.2.0`, or publishing a release on GitHub with a new `v1.2.0` tag, builds a release-signed APK and attaches it to the GitHub Release.

To sign your own builds, create a PKCS12 keystore and add two repository secrets:

```sh
keytool -genkeypair -alias release -keyalg RSA -keysize 3072 -validity 10950 \
  -storetype PKCS12 -keystore release.p12 -dname "CN=Your Name"
base64 -w0 release.p12   # paste the output into the KEYSTORE_B64 secret
```

| Secret | Value |
|---|---|
| `KEYSTORE_B64` | Base64 of the keystore |
| `KEYSTORE_PASSWORD` | The keystore password (also used as the key password) |

Keep the keystore safe. Updates must be signed with the same key, or users have to uninstall first. Never commit it; `.gitignore` already excludes `*.p12`, `*.jks` and `*.keystore`.

**Upstream check:** [`upstream-check.yml`](.github/workflows/upstream-check.yml) runs every Monday. It inspects SmartTube's latest release for everything this app relies on (the refresh receiver, row IDs and keys, and the video link format), and opens an issue if any of it changes. You can also run it by hand from the Actions tab. GitHub pauses scheduled workflows in repositories with no activity for 60 days; re-enable it from the Actions tab if that happens.

## Credits

- [TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt) by reisxd
- [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov
- [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu) by spocky

## Licence

[MIT](LICENSE)
