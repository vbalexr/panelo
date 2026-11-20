#!/usr/bin/env bash
# Production build into site/public. Optional: BASE_URL=https://... site/build.sh
# Fails on broken ref links and on any dead internal link (see check-links.py).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$here/setup.sh"
args=(--minify --gc --cleanDestinationDir --source "$here" --destination "$here/public")
[ -n "${BASE_URL:-}" ] && args+=(--baseURL "$BASE_URL")
"$here/.tools/hugo" "${args[@]}"
python3 "$here/check-links.py" "$here/public"
