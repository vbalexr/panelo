---
title: Offline and recovery
weight: 4
---

Panelo is built to fix itself without anyone touching the device.

## When the site is unreachable

- Panelo shows the **last saved screenshot** of the page with an **"Offline, showing saved version"** bar at the bottom, and keeps retrying in the background.
- As soon as the site answers again, it returns to the live page on its own.
- Snapshots can be turned off, and their refresh interval changed, in the **Refresh** section.

## Crashes and frozen pages

- If the WebView renderer crashes, the WebView is recreated and the page reloaded.
- A **heartbeat** checks that the page still responds. A hung page is reloaded.
- Server errors (HTTP 5xx by default) are treated as errors and retried instead of being shown.

## What is not covered

If Android kills the whole app process (some manufacturers do this aggressively), the system launcher can appear until Home is pressed again. Exempt Panelo from battery optimisation and, at [lockdown level 3](../lockdown/), the system restarts it automatically. See the [FAQ](../faq/).
