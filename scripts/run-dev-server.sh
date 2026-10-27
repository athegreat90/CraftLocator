#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
if [[ -f /workspace/.craftlocator-tools/activate.sh ]]; then
  source /workspace/.craftlocator-tools/activate.sh
fi
mkdir -p runs/server
if [[ ! -e runs/server/server.properties ]]; then
  cat > runs/server/server.properties <<'PROPERTIES'
server-ip=127.0.0.1
server-port=25565
view-distance=4
simulation-distance=4
online-mode=true
PROPERTIES
fi
exec ./gradlew runServer --no-daemon --console=plain
