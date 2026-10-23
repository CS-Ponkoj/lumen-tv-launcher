# My Home: ad-free Google TV launcher

A tiny (~35 KB) home screen for Android / Google TV with a tinted glass look.
No ads, no recommendations, no INTERNET permission.

## Features
- Favorites row (long-press OK to pin, move, rename, hide)
- Recent apps row
- All apps grid with search
- Named HDMI input shortcuts (long-press to rename)
- Top bar: clock, date, Wi-Fi status, Search, Sleep timer, Play Store, Settings, HDMI
- Glass-style wallpapers or your own picture
- Optional accessibility service that makes the remote's Home button open My Home

## Install on your TV
1. Download `myhome.apk` from the [latest release](../../releases/latest).
2. Get it onto the TV, either:
   - **From a PC with ADB:** enable *Developer options* and *USB debugging* on the TV, then
     `adb connect <tv-ip>` and `adb install myhome.apk`, or
   - **On the TV:** install the *Downloader* app from the Play Store, allow it to install unknown apps,
     and open the release link.
3. Open **My Home** from your apps.
4. To make the remote's Home button open it: Settings → System → Accessibility →
   turn on **My Home - Home button** (or use ⋯ → Home button setup inside the app).

To undo: turn that service off, or uninstall the app.

Tested on a TCL Google TV (Android 11). Should work on Android TV / Google TV 9+.

## Build from source (no Gradle)
Needs JDK 17 and Android SDK `build-tools;34.0.0` + `platforms;android-30`.

```sh
sh build.sh              # outputs build/myhome.apk
adb install -r build/myhome.apk
```

Then on the TV: Settings → System → Accessibility → turn on **My Home - Home button**.

## License
MIT  see [LICENSE](LICENSE).
