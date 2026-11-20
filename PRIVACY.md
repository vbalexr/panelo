# Panelo Privacy Policy

_Last updated: 2026-11-20_

Panelo ("the app") is an open-source kiosk app for Android, developed by Alex Vargas ([vbalex.com](https://vbalex.com)). This policy explains what data the app handles. The short version: **Panelo does not collect, sell or share any personal data. It has no analytics, ads or tracking, and no developer servers.**

## 1. Data the developer collects

**None.** Panelo has no backend. Nothing is sent to the developer.

## 2. Data processed on the device

| Data | Why | Where it goes |
|---|---|---|
| **Settings** (start URL, allowed hosts, display and power options) | To run the kiosk as configured | Stored only in the app's private storage on the device |
| **Admin PIN** | To protect the admin menu | Stored only as a salted hash (PBKDF2), never in plain text |
| **Remote-config token** (optional) | To read a private configuration file | Encrypted with a key held in the Android Keystore; never logged or displayed |
| **Screenshots of the web page** (optional "cached version" feature) | To show the last page when the site is offline | Stored only in the app's private storage; can be turned off in settings |
| **App logs** | Troubleshooting | Stored on the device (size-limited, rotated); leave the device only if the administrator exports them via the share button |
| **Camera frames** (optional presence detection) | To detect motion in front of the device and turn the screen off when nobody is present | Processed in memory at low resolution; **never stored, recorded or transmitted** |
| **Light sensor readings** (optional auto-dim) | To adjust screen brightness | Used in memory only |

## 3. Network connections made by the app

Panelo connects only to:

1. **The web app configured by the administrator** (start URL and allowed hosts). That website runs in the system WebView and may collect data under **its own privacy policy**. Panelo does not control that website and is not responsible for it.
2. **The remote configuration URL**, if the administrator enables remote config (e.g. a file on GitHub). Only the configuration file is downloaded; no device data is uploaded. The host of that file (e.g. GitHub) may log standard request data, such as IP address and time, under its own policy.
3. **Reachability checks** to the configured start URL, used to detect when the site is back online.

## 4. Permissions

| Permission | Used for |
|---|---|
| Internet / network state | Loading the web app, remote config, offline detection |
| Camera | Only if the web app requests it (e.g. video calls or scanning), or if presence detection is enabled |
| Microphone | Only if the web app requests it |
| Location | Only if the web app requests it |
| Device administrator / Device Owner (optional) | Kiosk lockdown, screen lock, scheduled reboot, set up explicitly by the administrator |
| Run at startup, wake lock, exact alarms, battery-optimisation exemption | Starting at boot, keeping the kiosk running, screen on/off schedules |

Camera, microphone and location access requested by the web app is granted only to pages on the allowed hosts.

## 5. Children

Panelo is a device-management tool for administrators and is not directed at children. It does not knowingly collect any data from anyone.

## 6. Data retention and deletion

All data stays on the device. Clearing the app's data ("Clear all data" in the admin menu or Android Settings → Apps → Panelo → Storage) or uninstalling the app deletes it.

## 7. Open source

Panelo's source code is publicly available under the GNU GPL v3, so anyone can verify these statements.

## 8. Changes

Updates to this policy are published in this file, and its history is visible in the repository.

## 9. Contact

Questions about this policy: open an issue in the project repository or contact the developer via [vbalex.com](https://vbalex.com).
