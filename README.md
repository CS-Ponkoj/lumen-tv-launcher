# Lumen: an ad-free, glass-style Google TV launcher

A small (~360 KB) home screen for Android / Google TV with a frosted-glass look.
No ads, no recommendations, no tracking, and no INTERNET permission.

## Features
Lumen 2 has one look, **Winter Snowfall**: a misty forest with gently falling snow, laid out like Google TV.

- **Info panel** (top left): clock and date, optional live weather, a status line
  (room · Wi-Fi · data used today · sleep timer) and a greeting
- **Pills** (top right): Inputs, Wi-Fi, Settings
- **Favourites tray** with the selected app's name in large type, then **Recent** and **All apps**
- Long-press OK on an app: add to favourites, move, rename, hide, app info
- Settings panel: weather city and units, room name, clock format, hidden apps, input names,
  sleep timer, screensaver, navigation sounds
- Optional accessibility service so the remote's Home button opens Lumen
- Internet is used **only** for the optional weather (Open-Meteo; only your city is sent)

Looking for the themes, wallpapers and card styles of Lumen 1? They live on the
[`v1` branch](../../tree/v1).

## Install on your TV
1. Download `lumen.apk` from the [latest release](../../releases/latest).
2. Get it onto the TV, either:
   - **From a PC with ADB:** enable *Developer options* and *USB debugging* on the TV, then
     `adb connect <tv-ip>` and `adb install lumen.apk`, or
   - **On the TV:** install the *Downloader* app from the Play Store, allow it to install unknown apps,
     and open the release link.
3. Open **Lumen** from your apps.
4. To make the remote's Home button open it: Settings → System → Accessibility →
   turn on **Lumen - Home button** (or use the side panel → Home button setup).

To undo: turn that service off, or uninstall the app.

Tested on a TCL Google TV (Android 11). Should work on Android TV / Google TV 9+.

## Build from source (no Gradle)
Needs JDK 17 and Android SDK `build-tools;34.0.0` + `platforms;android-30`.

```sh
sh build.sh              # outputs build/lumen.apk
adb install -r build/lumen.apk
```

Then on the TV: Settings → System → Accessibility → turn on **Lumen - Home button**.

## License
MIT  see [LICENSE](LICENSE).
