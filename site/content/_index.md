---
title: Panelo
layout: hextra-home
---

<div class="hx:mt-6 hx:mb-6">
{{< hextra/hero-badge link="license" >}}
  <span>GPL-3.0-or-later, open source</span>
  {{< icon name="arrow-circle-right" attributes="height=14" >}}
{{< /hextra/hero-badge >}}
</div>

<div class="hx:mt-4 hx:mb-6">
{{< hextra/hero-headline >}}
  Your web app. Fullscreen.&nbsp;<br class="hx:sm:block hx:hidden" />Always on.
{{< /hextra/hero-headline >}}
</div>

<div class="hx:mb-10">
{{< hextra/hero-subtitle >}}
  Panelo turns any Android phone or tablet into a dedicated,&nbsp;<br class="hx:sm:block hx:hidden" />self-healing kiosk for a single web app. About 2 MB.
{{< /hextra/hero-subtitle >}}
</div>

<div class="hx:mb-12 panelo-home-btns">
{{< hextra/hero-button text="Download APK" link="#download" >}}
{{< hextra/hero-button text="Read the docs" link="guide/" style="background:transparent;color:inherit;border:1px solid currentColor" >}}
</div>

<div class="panelo-hero-shot hx:mb-16">
  <figure class="device">
    <img src="images/admin-menu.png" alt="Panelo showing a web app fullscreen with the admin menu open and an offline notice at the bottom" loading="eager" />
  </figure>
</div>

## Why Panelo

{{< hextra/feature-grid cols="3" >}}
  {{< hextra/feature-card title="Boots straight into your app" subtitle="Panelo is the Home app: it starts after every reboot and shows your site fullscreen, with no browser bars and no URL typing." icon="play" >}}
  {{< hextra/feature-card title="Self-healing" subtitle="Recovers from WebView crashes, frozen pages and network errors. When the site is down it shows the last saved screenshot and keeps retrying." icon="refresh" >}}
  {{< hextra/feature-card title="Locked down" subtitle="Three levels, from a plain Home app to full Device Owner lock. Navigation is limited to an allowlist of hosts." icon="lock-closed" >}}
  {{< hextra/feature-card title="Hidden admin menu" subtitle="Swipe in from the right edge (or three fingers) and enter your PIN. Zoom, orientation, timeouts, logs and more." icon="adjustments" >}}
  {{< hextra/feature-card title="Remote config from GitHub" subtitle="Point Panelo at a JSON file on any HTTPS URL. Change settings, lock them, or send reload and reboot commands to a whole fleet." icon="cloud" link="reference/remote-config/" >}}
  {{< hextra/feature-card title="Power smart" subtitle="Auto-dim from the light sensor, screen off when nobody is around (camera motion only, nothing stored) and daily schedules." icon="sun" >}}
{{< /hextra/feature-grid >}}

## See it

<div class="panelo-shots">
  <figure class="device"><img src="images/admin-menu.png" alt="Admin menu with quick actions" loading="lazy" /><figcaption>Admin menu: quick actions, settings, logs</figcaption></figure>
  <figure class="device"><img src="images/lockdown.png" alt="Lockdown section showing Device Owner status" loading="lazy" /><figcaption>Lockdown levels up to Device Owner</figcaption></figure>
  <figure class="device"><img src="images/power.png" alt="Power section with auto-dim and presence detection" loading="lazy" /><figcaption>Auto-dim, presence detection, schedules</figcaption></figure>
</div>

<p class="panelo-note">Screenshots are from an Android emulator (tablet) with a test page as the kiosk content.</p>

<a id="download"></a>

## Get Panelo

<div class="panelo-download">
  <a class="panelo-dl-card primary" href="{{< gh "releases" >}}"><strong>Download the APK</strong><span>Latest release on GitHub. Android 8.0 or newer, about 2 MB.</span></a>
  <div class="panelo-dl-card soon"><strong>Google Play</strong><span>Coming soon.</span></div>
</div>

New here? Follow [Getting started](guide/getting-started/). Panelo is free software under the [GPL-3.0-or-later](license/), has no analytics or ads and collects no data ([privacy policy](privacy/)).
