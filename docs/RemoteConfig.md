# Remote configuration

Panelo can pull a JSON file from a URL and apply it to the kiosk: change the start URL, zoom, timeouts, PIN and so on, and trigger one-shot actions (reload, clear cache, clear data, reboot) without touching the device.

Turn it on in the admin panel: **Remote config** > enable, URL, optional token, poll interval. "Pull now" (also in Quick actions) fetches immediately.

## Supported URLs

Any **https** URL that returns JSON (plain `http` works only in debug builds, for local testing):

- GitHub raw file: `https://raw.githubusercontent.com/OWNER/REPO/BRANCH/path/kiosk.json`
- A normal GitHub file page `https://github.com/OWNER/REPO/blob/BRANCH/path/kiosk.json` (converted to the raw URL automatically)
- Gist raw: `https://gist.githubusercontent.com/USER/ID/raw/kiosk.json`
- GitHub Pages or your own server: `https://example.com/kiosk.json`

The file must be at most 256 KB. The app sends `If-None-Match` / `If-Modified-Since` so an unchanged file costs a `304`.

## Example (full)

```json
{
  "schemaVersion": 1,
  "startUrl": "https://app.example.com/kiosk",
  "allowedHosts": ["app.example.com", "cdn.example.com"],
  "orientation": "landscape",
  "pageZoom": 80,
  "textZoom": 100,
  "autoReloadMin": 0,
  "idleTimeoutSec": 300,
  "snapshotEnabled": true,
  "keepScreenOn": true,
  "autoDim": { "enabled": true, "min": 0.1, "max": 1.0 },
  "presence": { "enabled": false, "noPresenceTimeoutSec": 120 },
  "screenOffMode": "soft",
  "schedule": { "offAt": "22:00", "onAt": "07:00" },
  "remote": { "pollIntervalMin": 15 },
  "lockedKeys": ["startUrl", "allowedHosts"],
  "reloadNonce": 3
}
```

## Example (minimal)

Only `schemaVersion` is required. Everything else is optional:

```json
{ "schemaVersion": 1, "pageZoom": 70 }
```

## Merge semantics

The remote file is a **partial overlay** on the device's current config:

- Only keys present in the file change anything. Missing keys keep their current (local or earlier remote) value.
- Nested objects (`autoDim`, `presence`, `schedule`, `remote`) merge per key: `{"autoDim": {"max": 0.8}}` changes only `autoDim.max`.
- Lists (`allowedHosts`, `errorHttpCodes`, `lockedKeys`, `httpAuth`) and scalars are replaced as a whole.
- Unknown keys are ignored (and logged), so newer files work on older app versions.
- `remote.enabled` and `remote.url` in the file are ignored: a remote file cannot switch itself off or redirect the device elsewhere. `remote.pollIntervalMin` is accepted.
- The file is applied atomically. If anything is invalid (bad JSON, wrong type, out-of-range value, wrong `schemaVersion`) nothing is applied, the previous config stays, and the error is shown in the panel (Remote config > Result) and logged.
- If the file does not change anything it is reported as "applied" with no changed keys.

## Field reference

All names are exactly as in `KioskConfig`. Defaults apply when neither the device nor any file set the key.

| Name | Type | Range / values | Default | What it does |
|---|---|---|---|---|
| `schemaVersion` | int | must be `1` (required in remote files) | 1 | Format version. |
| `startUrl` | string | http(s) URL or empty | empty | Page the kiosk shows. May be changed remotely. |
| `allowedHosts` | string[] | non-blank hosts | `[]` | Hosts the web app may navigate to; empty = start URL host and its subdomains. |
| `blockExternal` | bool | | true | Block navigation to other hosts. |
| `pageZoom` | int | 50..200 | 100 | Page zoom in percent (scales the viewport, so full-height pages still fill the screen). |
| `textZoom` | int | 50..300 | 100 | WebView text zoom in percent. |
| `pinchZoom` | bool | | false | Allow pinch zoom. |
| `autoplay` | bool | | true | Media plays without a tap. |
| `userAgent` | string | | empty | User agent override (empty = default). |
| `errorHttpCodes` | int[] | each 100..599 | 500..599 | Main-frame HTTP statuses treated as errors. |
| `httpAuth` | object[] | `{host, username, password}` | `[]` | HTTP auth credentials per host. |
| `downloadsEnabled` | bool | | false | Allow downloads. |
| `orientation` | string | `auto`, `portrait`, `landscape`, `reversePortrait`, `reverseLandscape`, `sensor` | `auto` | Screen orientation. |
| `keepScreenOn` | bool | | true | Keep the screen awake. |
| `immersive` | bool | | true | Hide system bars. |
| `edgeHandle` | bool | | false | Show a small handle for the admin swipe. |
| `autoReloadMin` | int | >= 0 (0 = off) | 0 | Periodic reload. |
| `idleTimeoutSec` | int | >= 0 (0 = off) | 0 | Return to start URL after no touch. |
| `snapshotEnabled` | bool | | true | Keep an offline snapshot image. |
| `snapshotIntervalMin` | int | 1..1440 | 15 | Snapshot refresh interval. |
| `heartbeatIntervalSec` | int | 0 or 5..3600 | 30 | Hung-page check interval (0 = off). |
| `heartbeatTimeoutSec` | int | 2..300 | 10 | Time to answer a heartbeat. |
| `autoDim.enabled` | bool | | false | Ambient-light dimming. |
| `autoDim.min` / `autoDim.max` | float | 0..1, min <= max | 0.1 / 1.0 | Brightness range. |
| `autoDim.followSystem` | bool | | false | Do not override brightness. |
| `presence.enabled` | bool | | false | Camera presence detection. |
| `presence.noPresenceTimeoutSec` | int | >= 10 | 120 | Seconds without motion or touch before screen-off. |
| `presence.sensitivity` | float | 0..1 | 0.5 | Motion sensitivity (1 = most sensitive). |
| `screenOffMode` | string | `soft`, `real` | `soft` | Black overlay or real lock. `real` needs Device Admin/Owner, otherwise soft is used. |
| `schedule.offAt` / `schedule.onAt` | string or null | `HH:mm` | null | Daily screen off / on times; both are needed, windows may cross midnight. |
| `lockdownLevel` | int | 1..3 | 1 | Basic / screen pinning / Device Owner. |
| `allowPowerMenu` | bool | | false | Level 3: power-button menu. |
| `remote.pollIntervalMin` | int | >= 5 | 15 | Poll interval (`remote.enabled` and `remote.url` are local only). |
| `lockedKeys` | string[] | top-level key names | `[]` | Keys that cannot be changed on the device (see below). |
| `reloadNonce`, `clearCacheNonce`, `clearDataNonce`, `rebootNonce` | int | | 0 | One-shot commands (see below). |
| `adminPinHash`, `adminPinSalt` | string | base64, 32 / 16 bytes, set together | empty | Admin PIN (see below). |

## lockedKeys

`lockedKeys` lists top-level key names (for example `"startUrl"`, `"autoDim"`, `"pageZoom"`). While remote config is enabled, the panel greys those fields ("Managed remotely") and local edits to them are rejected. Remote files can still change them. Locking `"adminPinHash"` also locks the salt. Locking `"remote"` locks the whole Remote section, including the switch that turns remote config off, so use it with care.

## One-shot commands (nonces)

Bump a nonce to run a command once on every device:

| Nonce | Action |
|---|---|
| `reloadNonce` | Reload the page |
| `clearCacheNonce` | Clear the web cache |
| `clearDataNonce` | Clear cache, cookies and storage (logs the web app out), then reload |
| `rebootNonce` | Reboot the device (only when Panelo is Device Owner; otherwise logged and skipped) |

A command runs when its value **differs from the last value the device saw**. The **first** fetch from a given URL only records the values and runs nothing, so enrolling a new device against a file that already says `clearDataNonce: 7` does not wipe it. Changing the remote URL counts as a new enrolment. If the file changes while the app is not in the foreground, reload/clear commands run as soon as the app is shown again.

## Admin PIN

Remote files never carry a PIN, only its hash. Set both `adminPinHash` and `adminPinSalt` or neither. A plaintext field such as `adminPin` makes the file invalid.

Easiest: panel > Security > **Copy PIN hash for remote config** copies a snippet of the current hash and salt.

To generate one for a new PIN (PBKDF2-HMAC-SHA256, 120000 iterations, 32-byte key, 16-byte random salt, both base64; the PIN is UTF-8). This produces exactly what the app computes (verified against the app):

```python
import base64, hashlib, os, sys
pin = sys.argv[1]
salt = os.urandom(16)
key = hashlib.pbkdf2_hmac("sha256", pin.encode("utf-8"), salt, 120000, 32)
print('"adminPinHash": "%s",\n"adminPinSalt": "%s"' % (base64.b64encode(key).decode(), base64.b64encode(salt).decode()))
```

## Private GitHub repository

1. GitHub > Settings > Developer settings > Fine-grained personal access tokens > Generate.
2. Repository access: **Only select repositories** and pick the one repo. Permissions: **Contents: Read-only**. Set an expiry you can live with.
3. In the panel, set the URL to the file's normal GitHub URL (`https://github.com/OWNER/REPO/blob/main/kiosk.json` or the raw URL), paste the token and Save. The token is write-only: the panel only ever shows "Token saved". It is stored encrypted with an Android Keystore AES-256-GCM key, never in the config file or logs, and sent only over https and only to the host it was configured for.

With a token, Panelo calls the GitHub API: `https://api.github.com/repos/OWNER/REPO/contents/PATH?ref=BRANCH` with `Authorization: Bearer <token>`, `Accept: application/vnd.github.raw` and `X-GitHub-Api-Version: 2022-11-28`. The branch is the first path segment (branch names containing `/` are not supported; `refs/heads/BRANCH` is understood). Without a token the same URLs are fetched from `raw.githubusercontent.com`.

## Caching delay

`raw.githubusercontent.com` is served through a CDN that caches for about 5 minutes (sometimes longer), so an edit may take that long to show up even with "Pull now". The minimum poll interval is therefore 5 minutes. The API route used with a token is not cached this way. Foreground polling uses the configured interval (with +-10 % jitter); when the app is not in the foreground a WorkManager job polls at most every 15 minutes (Android may delay it further).

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Result "FAILED: HTTP 404" | Wrong URL/branch, or a private repo without a token. |
| "FAILED: HTTP 401/403" | Token expired, wrong repo, or missing Contents: Read permission. |
| "FAILED: remote URL must be https" | Use an https URL. |
| "INVALID: not valid JSON" | Syntax error, or the URL returns an HTML page (use the raw URL). |
| "INVALID: ... must be ..." | A value is out of range; see the field table. Nothing was applied. |
| "INVALID: schemaVersion" | `"schemaVersion": 1` is missing. |
| Edit not visible | CDN cache (about 5 min), or the poll interval has not elapsed; press Pull now. |
| Field cannot be edited | It is in `lockedKeys`. |
| Nonce did nothing | First sight only records it; bump the value again. |

The log viewer in About > Log shows `remote` events (`applied`, `not_modified`, `invalid`, `failed`, `ignored_keys`, `commands_due`).
