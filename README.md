# Panelo

**Panelo turns an Android phone or tablet into a dedicated, self-healing kiosk for a single web app.**

It replaces the home screen, starts on boot, shows your web app fullscreen with no browser UI, recovers on its own from crashes and network loss, and can be managed remotely from a JSON file (e.g. hosted on GitHub). Built on the system WebView for native performance, with an APK of about 2 MB.

## Features

- **Home app / auto-start:** runs as the device launcher, so it comes up automatically after every boot.
- **Fullscreen web app:** no address bar, no URL typing; navigation is limited to an allowlist of hosts. The page can still use camera, microphone, location, file upload and fullscreen video.
- **Self-healing:**
  - Recovers automatically from WebView crashes, frozen pages and network or server errors.
  - When the site can't be reached, it shows the last saved screenshot with an "Offline — showing saved version" bar and keeps retrying.
- **Three lockdown levels:**
  1. Home app + immersive mode
  2. Screen pinning
  3. Full lock with Device Owner ([setup guide](docs/DeviceOwnerSetup.md))
- **Hidden admin menu:** swipe in from the right edge (or swipe left with three fingers) and enter the PIN.
- **Settings:** zoom (50–200 %, so more content can fit on screen), orientation, refresh intervals, idle timeout, clear cache or data.
- **Remote configuration:** optional. Panelo polls a JSON file on any HTTPS URL (GitHub raw, Gist, Pages, private repos with a token). Settings can be locked, commands sent remotely (reload, clear cache or data, reboot), and invalid files are rejected with a warning ([docs](docs/RemoteConfig.md)).
- **Power features:**
  - Auto-dimming from the light sensor.
  - Presence detection with the front camera (motion only, no images stored), turning the screen off when nobody is around.
  - Daily on/off schedules.

## Requirements

- Android 8.0 (API 26) or newer, with an up-to-date Android System WebView.
- Optional hardware: light sensor (auto-dim), front camera (presence detection).

## Building

The toolchain is pinned with [SDKMAN!](https://sdkman.io) (`.sdkmanrc`: Temurin JDK 21).

```bash
sdk env install                 # installs the pinned JDK
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # path to your Android SDK
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintDebug
./gradlew assembleRelease       # needs your own signing config
```

Install on a device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, open Panelo and follow the setup wizard.

## Documentation

| Document | Contents |
|---|---|
| [docs/DeviceOwnerSetup.md](docs/DeviceOwnerSetup.md) | Level 3 (Device Owner) setup and removal |
| [docs/RemoteConfig.md](docs/RemoteConfig.md) | Remote config format, fields, GitHub setup |

## Contributing

Panelo is an open-source project and contributions are welcome: issues, fixes and features. Automated or AI-only pull requests are not accepted.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the AI / LLM policy, coding conventions, licensing of contributions and the checklist to run before opening a PR.

## Privacy

Panelo collects no data, and has no analytics, ads or tracking. See the [Privacy Policy](PRIVACY.md).

## License

Copyright (C) 2026 Alex Vargas

Panelo is free software, released under the [GNU General Public License v3.0 or later](LICENSE) (`GPL-3.0-or-later`):

- You may use, study, modify and redistribute it, including commercially.
- If you distribute Panelo or a modified version (e.g. on an app store), you must release the **complete source code under the same license** and keep the copyright and license notices (attribution).

The **Panelo name and logo are not covered by the license**. Forks must use a different name and icon.

## Website

Documentation: https://vbalexr.github.io/panelo/ (built from [`site/`](site/) and `docs/`).
