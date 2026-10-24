# Lumen: an ad-free, glass-style Google TV launcher

A tiny (~60 KB) home screen for Android / Google TV with a frosted-glass look.
No ads, no recommendations, no tracking, and no INTERNET permission.

## Features
**Look**
- Showcase layout: full-screen wallpaper with your favourites in a frosted glass dock (or Classic rows)
- Built-in art wallpapers (Aurora, Sunset hills, Misty mountains, Moonlit sea), colour gradients,
  your own picture or a looping video, with an optional day/night switch
- Card styles (Modern, Premium, Capsule), accent colours, glass-glow or minimal focus border
- Minimal status bar: clock, Wi-Fi signal, data used today

**Apps**
- Favourites, Recent, your own categories (row or grid), All apps with search
- Long-press OK on an app to move it with the arrow keys, rename it, give it a custom banner or hide it
- Named HDMI input shortcuts (long-press to rename)

**Handy**
- Side settings panel (Menu button or the settings icon)
- Sleep timer, Wi-Fi details panel, OLED-safe clock screensaver, navigation sounds
- Optional accessibility service that makes the remote's Home button open Lumen

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
