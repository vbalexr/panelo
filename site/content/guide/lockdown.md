---
title: Lockdown levels
weight: 5
---

Choose how tightly the device is locked in **Lockdown**. Panelo detects what is available.

| Level | What it does | Needs |
|---|---|---|
| **1. Home app + immersive** | Panelo is the Home app, bars are hidden, it starts on boot. Users can still leave with system gestures or buttons. | Nothing special |
| **2. Screen pinning** | Adds Android screen pinning, so Home and Recents are blocked until unpinned. | Screen pinning enabled in Android settings (otherwise it silently stays at level 1) |
| **3. Device Owner** | Full lock: no pinning prompt, status bar and notification shade disabled, Home and Recents blocked, app forced as Home, optional permission auto-grants, scheduled reboot. | Set up once over USB on a factory-reset device |

Level 3 is the real kiosk lock, and also the hardest to undo if you lose the PIN. Read the [Device Owner setup guide](../../reference/device-owner/) before you start. It also explains how to remove Device Owner cleanly.
