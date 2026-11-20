#!/usr/bin/env bash
# Downloads the pinned Hugo (extended) into site/.tools and the pinned Hextra theme into
# site/themes/hextra, verifying SHA-256. Idempotent. No system install, no Go, no Node.
# Sourced by build.sh / serve.sh; also usable on its own.
set -euo pipefail
SITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=versions.env
source "$SITE_DIR/versions.env"
HUGO_BIN="$SITE_DIR/.tools/hugo"

fetch_verified() { # url sha256 dest
  local url="$1" sha="$2" dest="$3"
  curl -fsSL --retry 3 -o "$dest" "$url"
  echo "$sha  $dest" | sha256sum -c --quiet - || { echo "Checksum mismatch for $url" >&2; rm -f "$dest"; exit 1; }
}

[ "$(uname -s)-$(uname -m)" = "Linux-x86_64" ] || echo "Warning: setup.sh fetches linux-amd64 Hugo; use your own hugo ${HUGO_VERSION} extended elsewhere." >&2

mkdir -p "$SITE_DIR/.tools" "$SITE_DIR/themes"

if ! "$HUGO_BIN" version 2>/dev/null | grep -q "v${HUGO_VERSION}"; then
  echo "Downloading Hugo extended ${HUGO_VERSION}..."
  tmp="$(mktemp -d)"
  fetch_verified "https://github.com/gohugoio/hugo/releases/download/v${HUGO_VERSION}/hugo_extended_${HUGO_VERSION}_linux-amd64.tar.gz" "$HUGO_SHA256" "$tmp/hugo.tgz"
  tar -xzf "$tmp/hugo.tgz" -C "$SITE_DIR/.tools" hugo
  rm -rf "$tmp"
fi

if [ "$(cat "$SITE_DIR/themes/hextra/.version" 2>/dev/null)" != "$HEXTRA_VERSION" ]; then
  echo "Downloading Hextra theme ${HEXTRA_VERSION}..."
  tmp="$(mktemp -d)"
  fetch_verified "https://github.com/imfing/hextra/archive/refs/tags/v${HEXTRA_VERSION}.tar.gz" "$HEXTRA_SHA256" "$tmp/hextra.tgz"
  rm -rf "$SITE_DIR/themes/hextra"; mkdir -p "$SITE_DIR/themes/hextra"
  tar -xzf "$tmp/hextra.tgz" -C "$SITE_DIR/themes/hextra" --strip-components=1
  echo "$HEXTRA_VERSION" > "$SITE_DIR/themes/hextra/.version"
  rm -rf "$tmp"
fi
