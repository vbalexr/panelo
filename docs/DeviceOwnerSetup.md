# Device Owner setup (lockdown level 3)

Level 3 turns Panelo into the **Device Owner** of the device. This is what makes the kiosk really locked: no pinning prompt, status bar and notification shade disabled, Home/Recents blocked, the app forced as the Home app, and optional permission auto-grants.

Everything is done once, over USB, with `adb`. No MDM, no Play Store, no Google account needed.

## 1. Why a factory reset and no accounts

Android only lets an app become Device Owner on a device that is still "unprovisioned":

- no Google (or other) accounts are signed in, and
- no secondary users / work profiles exist.

On a device that is already in use this is impossible by design, so the practical way is: **factory reset**, skip adding an account during the setup wizard (choose "Skip" / offline setup; Wi-Fi is fine), then run the command below before adding anything else.

## 2. Prepare the device

1. Finish the setup wizard without signing in to any account.
2. Open **Settings > About phone** and tap **Build number** 7 times to enable Developer options.
3. Open **Settings > System > Developer options** and enable **USB debugging**.
4. Connect the device by USB and accept the "Allow USB debugging?" prompt.
5. On a computer with `adb` (Android platform-tools): `adb devices` must list the device as `device`.

## 3. Install the APK

```
adb install -r app-debug.apk
```

Do not open or configure the app yet (opening it is harmless, but do not add accounts).

## 4. Make it Device Owner

```
adb shell dpm set-device-owner com.vbalex.android.panelo/.admin.AdminReceiver
```

Expected output: `Success: Device owner set to package ...`.

Then open Panelo, swipe from the right edge, enter the PIN, open **Lockdown**, choose **3 - Device Owner**. The status lines show `Device Owner: yes`. The app locks immediately and re-applies the policies after each start / reboot.

## 5. Common errors

| Error | Cause | Fix |
|---|---|---|
| `Not allowed to set the device owner because there are already some accounts on the device` | An account (Google, Samsung, ...) exists | Remove all accounts under Settings > Passwords & accounts, or (more reliable) factory reset and skip account setup |
| `... already several users on the device` / `Not allowed ... there are already some users` | Secondary users, guest or work profile | Settings > System > Multiple users: delete all but the owner; remove work profiles |
| `Trying to set the device owner, but device owner is already set` | Another app (or this one) is already Device Owner | Remove the old owner first (see section 6), or factory reset |
| `Not allowed to set the device owner because the device is already provisioned` (older Android) | Setup wizard completed with an account | Factory reset and skip account setup |
| `java.lang.SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_DEVICE_ADMINS` | Some OEMs (Xiaomi/MIUI/HyperOS, some Oppo/Vivo) need an extra toggle | Enable **USB debugging (Security settings)** in Developer options (needs a signed-in Mi account on some firmware), then retry |
| `Unknown admin: ComponentInfo{...}` | Wrong component name or app not installed | Check the exact component `com.vbalex.android.panelo/.admin.AdminReceiver` and that the APK is installed |

## 6. How to remove Device Owner

**Recommended:** open the admin panel > **Lockdown** > **Remove Device Owner** and confirm. The app releases all policies (Home preference, status bar, keyguard, lock task) and gives up Device Owner. The app can then be uninstalled normally.

`adb shell dpm remove-active-admin com.vbalex.android.panelo/.admin.AdminReceiver` only works for apps built with `android:testOnly="true"`. Panelo release/debug builds are not test-only, so that command fails with `Attempt to remove non-test admin`. Do **not** rely on it.

If you lose the PIN or the app no longer opens at level 3, the only other way out is a factory reset (recovery mode or Settings > Reset). So keep the PIN safe, and use **Exit kiosk temporarily** in the panel if you only need to get to system Settings.

## 7. Behaviour notes

- **Exit kiosk temporarily** stops lock task and lifts the Home preference until the app is started again (Home press or reboot).
- **Power menu:** by default the power-button menu is blocked in lock task. Enable "Allow power-button menu" in the Lockdown section to permit power off/restart.
- **Keyguard:** the policies disable the keyguard; this fails if a PIN/pattern is set on the device. Remove the screen lock for a true kiosk.
- **Auto-granted permissions:** camera, microphone, location are granted to the app. Android 12+ may refuse sensor permission grants on some builds; the panel/log shows policies that failed (`device_owner` / `policy_failed`).
- **Android versions:** needs Android 8.0+. `setLockTaskFeatures` (power menu option) needs Android 9+; on 8.x the default lock-task UI applies. Android 11+ may ask the user to confirm some permission prompts once.
- **OEMs:** Samsung (Knox), Xiaomi, Huawei and some carrier builds restrict or ignore parts of the policies (status bar, keyguard). Test on the real device. Some devices need Developer options "Disable permission monitoring" off/on to allow adb provisioning.
- **After boot:** the app starts as Home app and through a `BOOT_COMPLETED` receiver, then enters lock task without a prompt.
