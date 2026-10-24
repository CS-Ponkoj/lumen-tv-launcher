# My Home — ad-free Google TV launcher

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

## Build (no Gradle)
Needs JDK 17 and Android SDK `build-tools;34.0.0` + `platforms;android-30`.

```sh
sh build.sh              # outputs build/myhome.apk
adb install -r build/myhome.apk
```

Then on the TV: Settings → System → Accessibility → turn on **My Home - Home button**.
