---
title: Kiosk and navigation
weight: 3
---

## Allowlist

Panelo shows only your web app. Navigation is limited to the **start URL's host and its subdomains**, plus any **allowed hosts** you add. Links to other hosts are blocked and the page stays where it is. This can be turned off (`blockExternal`) in the config.

Camera, microphone, location, file upload and fullscreen video work for pages on allowed hosts.

## Zoom

- **Page zoom: 50 to 200 %.** Values below 100 % fit more content on the screen, handy for dashboards on small tablets. Touch positions are mapped correctly at every zoom level.
- **Text zoom: 50 to 300 %**, independent of page zoom.
- **Pinch zoom** is off by default.

## Orientation

Auto, portrait, landscape, the reversed variants, or sensor. Rotating does not reload the page.

## Idle and auto reload

- **Idle timeout:** after no touch for N seconds, Panelo returns to the start URL (useful for public displays).
- **Auto reload:** reload the page every N minutes. Both are off by default.

All options are in the [admin menu](../admin-menu/), or can be set [remotely](../../reference/remote-config/).
