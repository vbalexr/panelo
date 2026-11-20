---
title: Admin menu
weight: 2
---

The admin menu is hidden so users of the kiosk never see it.

## Opening it

- **Swipe from the right edge** of the screen toward the left. Only touches that start within a thin strip at the right edge are taken; everything else goes to the web app.
- **Fallback: swipe left with three fingers** anywhere on the screen. Use this if system gesture navigation steals the edge swipe.

Then enter your PIN. The panel closes itself after a couple of minutes without use.

## PIN and lockout

After **5 wrong attempts** entry is locked for **60 seconds** (the lock survives app restarts). The PIN is stored only as a salted hash. You can change it in **Security**. If you lose it and are at lockdown level 3, a factory reset is the only way back, so keep it safe.

## Sections

| Section | What is in it |
|---|---|
| **Quick actions** | Reload, go to start URL, clear cache, clear all data (logs the web app out), pull remote config, exit kiosk temporarily |
| **Web** | Start URL, allowed hosts, page zoom, text zoom, pinch zoom, autoplay, user agent, error HTTP codes |
| **Display** | Orientation, keep screen on, immersive mode |
| **Refresh** | Auto reload, idle timeout, snapshot, heartbeat |
| **Power** | Auto-dim, presence detection, screen-off mode, schedule |
| **Lockdown** | Level 1/2/3, Device Admin, Device Owner, default Home |
| **Remote config** | Enable, URL, token, poll interval, last result, Pull now |
| **Security** | Change PIN |
| **About** | Version, WebView version, device info, log viewer, export logs |

{{< callout type="info" >}}
**Exit kiosk temporarily** lets you reach Android Settings. The kiosk comes back the next time Home is pressed or the device reboots.
{{< /callout >}}
