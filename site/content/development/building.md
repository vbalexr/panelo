---
title: Building
weight: 3
---

## Build the app

The toolchain is pinned with [SDKMAN!](https://sdkman.io) (`.sdkmanrc`: Temurin JDK 21).

```bash
sdk env install                 # installs the pinned JDK
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # path to your Android SDK
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintDebug
./gradlew assembleRelease       # needs your own signing config
```

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, open Panelo and follow the setup wizard.

## Contributing

Contributions are welcome. Read the [Contributing](../contributing/) page first: it covers the AI / LLM policy, the coding conventions and the checklist before opening a PR. Source: [the repository]({{< gh >}}).

## This website

The site is built with [Hugo](https://gohugo.io) (extended) and the [Hextra](https://imfing.github.io/hextra/) theme. Everything lives in the `site/` folder. The reference and development pages are **not copied**: `docs/*.md`, the root `CONTRIBUTING.md` and `PRIVACY.md` are mounted into the site at build time, so edit those files in place.

```bash
site/serve.sh     # live preview at http://localhost:8010/ (downloads pinned Hugo and theme on first run)
site/build.sh     # production build into site/public
```

| Where | What |
|---|---|
| `site/hugo.toml` | Configuration, menu, mounts of `docs/`, `CONTRIBUTING.md` and `PRIVACY.md`, titles of mounted pages |
| `site/content/` | Site-only pages (landing, guide, license, this page) |
| `site/static/`, `site/assets/` | Logo, favicon, screenshots, CSS |
| `site/versions.env` | Pinned Hugo and Hextra versions with checksums |
| `.github/workflows/pages.yml` | Build and deploy to GitHub Pages |

### Publishing on GitHub Pages

1. In `site/hugo.toml` replace `OWNER` (in `baseURL`, `[params.panelo]` and the GitHub menu link) with your GitHub user or organisation.
2. In the repository go to **Settings, Pages, Source: GitHub Actions**.
3. Push to `main`. The workflow `pages.yml` builds and deploys the site.

The site will be at `https://OWNER.github.io/panelo/`. The **privacy policy URL for Google Play** is `https://OWNER.github.io/panelo/privacy/`.

### Custom domain

To serve the site from a domain such as `panelo.vbalex.com`:

1. Add a DNS `CNAME` record from `panelo.vbalex.com` to `OWNER.github.io`.
2. In **Settings, Pages, Custom domain** enter the domain and enable *Enforce HTTPS*.
3. Set `baseURL = "https://panelo.vbalex.com/"` in `site/hugo.toml`.

No `CNAME` file is needed: with a GitHub Actions deployment the domain is configured in the repository settings. The privacy URL then becomes `https://panelo.vbalex.com/privacy/`.
