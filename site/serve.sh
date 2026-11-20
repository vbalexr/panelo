#!/usr/bin/env bash
# Live preview at http://localhost:8010/ (override: PORT=9000 site/serve.sh)
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$here/setup.sh"
exec "$here/.tools/hugo" server --source "$here" --port "${PORT:-8010}" --baseURL "http://localhost:${PORT:-8010}/" --appendPort=false --disableFastRender
