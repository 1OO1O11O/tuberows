Your SmartTube home-screen rows (Subscriptions, Recommended, History), mirrored for Projectivy Launcher, with every video opening in **TizenTube Cobalt**.

**Install:** see the [README](https://github.com/1OO1O11O/tuberows#install). The app asks for its one permission itself (press **Grant access**); one ADB command is needed to exempt SmartTube from battery optimisation. Updating from v1.1.0? Install over the top; your settings are kept.

**Requires:** Android TV / Google TV 8.0+, TizenTube Cobalt, SmartTube (signed in, Channels service on), and a launcher that shows app channels, such as Projectivy Launcher.

**What's new in v1.2.0**
- **Fresh rows after waking the TV:** unless SmartTube refreshed or was asked to in the last 30 minutes, it's asked as soon as the TV is on (usually within a few minutes), and every hour while the screen is on
- **Upload times that never go stale:** "1 hour ago" becomes "Uploaded ~7 pm, 27 Oct" (English)
- **Every card SmartTube provides** (up to 100 per row, was 40); your launcher decides how many to show
- **Status screen:** the exact battery-exemption command for the SmartTube build on your TV, when SmartTube's data last changed, and a warning if it hasn't for a day
- **Grant access in the app:** no ADB needed for the permission
- **Sturdier sync:** rows are only marked done after a complete write; SmartTube rows are matched by ID or channel key
- Unit tests in CI, and a weekly check that SmartTube hasn't changed anything this app relies on

**Unchanged:** no internet permission, no sign-in, no tracking. The app never contacts any server.
