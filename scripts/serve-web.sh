#!/usr/bin/env bash
#
# Serve the built browser game on http://localhost:8712.
#
# Kotlin's own `wasmJsBrowserDevelopmentRun` starts a webpack dev server, which is the
# right tool while iterating on the Kotlin. This is for looking at what will actually be
# published: it serves `wasmJsBrowserDistribution`'s output, art and all, over plain HTTP
# with no bundler in the way.
#
# A real static server rather than opening `index.html` from disk, because a `file://`
# page cannot instantiate WebAssembly or fetch the art — both are blocked by the origin
# rules, and the failure looks like a game that loads and then draws nothing.
set -euo pipefail

port="${1:-8712}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dist="$root/shared/build/dist/wasmJs/productionExecutable"

if [[ ! -f "$dist/index.html" ]]; then
    echo "Nothing built yet. Run:" >&2
    echo "  ./gradlew :shared:wasmJsBrowserDistribution" >&2
    exit 1
fi

echo "Serving $dist on http://localhost:$port"
cd "$dist"
exec python3 -m http.server "$port"
