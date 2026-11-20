---
title: Getting started
weight: 1
---

## Requirements

- Android 8.0 (API 26) or newer, with an up-to-date **Android System WebView** (Settings, Apps, or via the Play Store).
- Optional hardware: light sensor (auto-dim), front camera (presence detection).

## Install

1. Download the latest APK from [GitHub Releases]({{< gh "releases" >}}).
2. Open it on the device and allow installing from this source when asked, or install over USB: `adb install -r panelo.apk`.
3. Open Panelo. The setup wizard starts.

## First-run wizard

The wizard has five short steps:

1. **Set admin PIN.** 4 to 8 digits. It protects the admin menu. You can press *Later*, but then the menu is unprotected until you set one.
2. **Start URL.** The web app the kiosk shows. Without a scheme, `https://` is assumed.
3. **Allowed hosts** (optional). Extra hosts the page may navigate to, separated by commas. The start URL's own host and its subdomains are always allowed.
4. **Default Home app.** Makes Panelo the launcher so it starts on boot and when Home is pressed. On Android 8 this opens the system "Default apps" screen; on newer versions you get the Home role dialog.
5. **Battery optimisation.** Exempts Panelo so the system does not kill it in the background.

When you finish, the web app appears fullscreen. Open the [admin menu](../admin-menu/) any time to change settings.

{{< callout type="info" >}}
Want to manage many devices? Enable [remote configuration](../../reference/remote-config/) and keep one JSON file on GitHub.
{{< /callout >}}
