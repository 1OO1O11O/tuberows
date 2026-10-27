# TizenTube Recommendations for Projectivy Launcher

Your YouTube **Subscriptions**, **Recommended** and **History** rows on the Projectivy Launcher home screen, with every video opening in **[TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt)**.

TizenTube Cobalt doesn't publish home-screen rows, so launchers like Projectivy have nothing to show for it. [SmartTube](https://github.com/yuliskov/SmartTube) does. This small Android TV app copies SmartTube's rows into rows of its own, and points every card at TizenTube instead.

> Unofficial. Not affiliated with YouTube, Google, TizenTube, SmartTube or Projectivy Launcher. On the TV, the app appears as **TizenTube Rows** (package `tv.tuberows.app`).

## What it does

1. **Reads SmartTube's home-screen rows** (Subscriptions, Recommended, History) from Android's TV provider, the same shared database launchers read from.
2. **Publishes its own copies of those rows**, up to 40 videos each. Subscriptions and Recommended are on by default; History is off by default; turn it on in the app if you want it.
3. **Opens every video in TizenTube Cobalt.** If TizenTube isn't installed, the video opens in SmartTube instead.
4. **Fills Watch Next** with up to 3 of your most recent History videos (*Continue*) and up to 5 new uploads from your subscriptions (*New*). This works even with the History row off, and can be turned off.
5. **Keeps the rows current:**
   - Copies SmartTube's changes about 20 seconds after SmartTube updates its rows.
   - Asks SmartTube to refresh 2 minutes after you open a video (so it shows in History), when you open the app, and once an hour while the screen is on.
   - Never asks SmartTube to refresh more than once every 10 minutes (the **Refresh SmartTube** button on the status screen is the only exception). SmartTube's own 15-minute refresh also keeps running.
   - Rows that haven't changed are not rewritten.
6. **Shows a status screen** when you open *TizenTube Rows*: row toggles, whether it has read access, whether TizenTube is installed, last sync, last SmartTube refresh, and whether both apps are exempt from battery optimisation.

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
- **A computer with ADB** for the one-time setup (Windows, macOS or Linux)

## Install

The setup needs ADB once: Android TV has no on-screen prompt for the permission that reads other apps' rows, and ADB is the reliable way to exempt apps from battery optimisation on a TV.

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

# Install (use the file name you downloaded), and grant the one permission the app needs
adb install -r -g TizenTube-Recommendations-v1.1.0.apk
adb shell pm grant tv.tuberows.app android.permission.READ_TV_LISTINGS

# Find SmartTube's package name (it differs between builds)
adb shell "pm list packages | grep -iE 'smarttube|teamsmart|liskovsoft'"

# Exempt both apps from battery optimisation, so background refreshes keep running
adb shell dumpsys deviceidle whitelist +tv.tuberows.app
adb shell dumpsys deviceidle whitelist +org.smarttube.stable   # use the package name from the step above

# Open the app once to start syncing
adb shell am start -n tv.tuberows.app/.MainActivity
```

Turn Wireless debugging off when you're done.

### 3. Show the rows in Projectivy Launcher

1. Open Projectivy **Settings > Channels**.
2. Enable the **TizenTube Rows** channels (Subscriptions, Recommended, and History if you turned it on).
3. Hide SmartTube's own channels, so you don't see each row twice.
4. Optional: enable Projectivy's **Watch Next** category to see the Continue and New entries.

## Why the battery exemption matters

Android's App Standby limits background work for apps you rarely open. Once this is set up you'll never open SmartTube, so without the exemption Android would cut its refreshes to as little as once a day, and your rows would go stale.

The exemption simply restores SmartTube's normal behaviour: it refreshes on its own 15-minute schedule, the same load as anyone who uses SmartTube daily. Depending on your TV's standby mode, that schedule may also run while the TV is in standby. This app's own hourly refresh request is skipped while the screen is off.

The exemption survives reboots. The status screen shows whether both are in place; if a firmware update or reset clears them, run the `deviceidle whitelist` commands again.

## Troubleshooting

| What you see | What to do |
|---|---|
| Status says **SmartTube rows not visible** | Open SmartTube once and check it's signed in and its Channels service is on (see Requirements). Then open TizenTube Rows again. |
| **Read access: MISSING** | `adb shell pm grant tv.tuberows.app android.permission.READ_TV_LISTINGS` |
| **Power: NOT exempt** | Run the two `dumpsys deviceidle whitelist` commands again, or run the install script again |
| Rows don't appear in Projectivy | Projectivy **Settings > Channels**: enable the TizenTube Rows channels |
| Videos open in SmartTube | TizenTube Cobalt isn't installed, or was installed under a different package name |
| `adb connect` fails | Re-pair with `adb pair`, or use the script, which handles pairing. The connect port changes each time Wireless debugging is turned on. |
| Need logs | `adb logcat -d -s TubeRows` |

## Uninstall

```sh
adb shell dumpsys deviceidle whitelist -tv.tuberows.app
adb shell dumpsys deviceidle whitelist -org.smarttube.stable   # your SmartTube package
adb uninstall tv.tuberows.app
```

Android normally deletes an uninstalled app's rows and Watch Next entries by itself (on some TVs, only after the next restart). You can also uninstall from the TV's app settings.

## Limitations

- **It depends on how SmartTube publishes its rows.** If SmartTube changes that, rows may stop updating until this app is updated.
- **The content is SmartTube's.** Recommendations come from the account SmartTube is signed in to.
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

## Credits

- [TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt) by reisxd
- [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov
- [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu) by spocky

## Licence

[MIT](LICENSE)
