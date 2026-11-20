---
title: Power features
weight: 6
---

All power features are optional and are hidden or disabled when the hardware is missing.

## Auto-dim

Uses the light sensor to adjust brightness between a minimum and maximum you set. Small changes (below 3 %) are ignored and changes are animated, so the screen does not flicker.

## Presence detection

Uses the **front camera** to detect motion and turns the screen off when nobody is around. Frames are analysed in memory at low resolution and **never stored or transmitted**. Touches count as presence. If the web app itself starts using the camera, presence detection pauses and resumes when the page is done.

## Screen-off modes

- **Soft (default):** a black overlay with brightness 0. The app stays in front, the camera keeps watching, and motion or a touch wakes it. On OLED screens this is true black; on LCD the backlight stays at its minimum.
- **Real:** locks the device. Needs Device Admin or Device Owner. The camera cannot run while the screen is really off, so presence cannot wake it: use the power button or the schedule.

## Schedules

Daily **off at** and **on at** times (HH:mm, may cross midnight). Panelo uses exact alarms so the switch happens on time. Inside the off window motion never wakes the screen; a touch wakes it for two minutes.

## Hardware limits

- Emulators do not behave like real sensors; real-device tuning of the light curve and camera detection depends on the hardware.
- Some manufacturers restrict background behaviour, cameras and alarms.
- Exact alarms use permissions that are suitable for sideloading; a Google Play release would need them revisited.
