---
title: FAQ and troubleshooting
weight: 7
---

## The page is white or says it is blocked

The start URL redirected to, or the page loads resources from, a host that is not allowed. Open the admin menu, **Web**, and add the missing hosts to **Allowed hosts**. Check the **About** log viewer for the blocked host.

## The right-edge swipe does not open the menu

With **gesture navigation**, Android can take edge swipes for Back. Panelo reserves part of the edge, but a limited band only. Swipe within that band, or use the **three-finger swipe to the left** anywhere on the screen. Switching the system to 3-button navigation also works.

## I cannot uninstall Panelo

If Panelo is Device Owner, Android blocks uninstalling it. Open the admin menu, **Lockdown**, **Remove Device Owner**, then uninstall normally. See [Device Owner setup](../../reference/device-owner/#6-how-to-remove-device-owner).

## I lost the PIN

At level 1 or 2, reinstalling or clearing app data resets Panelo. At level 3 the only way out is a factory reset. Keep the PIN safe, or provide a [PIN hash by remote config](../../reference/remote-config/#admin-pin).

## "WebView too old" warning

Panelo uses the system WebView. Update **Android System WebView** (and Chrome) from the Play Store or the manufacturer. Very old WebViews (Chrome 69 and 74 were tested) work but are not recommended.

## The kiosk stops after a few hours, or the launcher appears

Some manufacturers (Xiaomi, Huawei, Samsung and others) kill background apps or limit autostart. Allow the battery optimisation exemption in the wizard, enable "autostart" or "run in background" for Panelo in the vendor settings, and consider [level 3](../lockdown/).

## Remote config does not apply

See the troubleshooting table in [Remote configuration](../../reference/remote-config/#troubleshooting). Raw GitHub files can be cached for about 5 minutes.

## Does Panelo collect data?

No. See the [privacy policy](../../privacy/).
