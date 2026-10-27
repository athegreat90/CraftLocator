#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
if [[ -f /workspace/.craftlocator-tools/activate.sh ]]; then
  source /workspace/.craftlocator-tools/activate.sh
fi
minecraft_version=${1:-$(sed -n 's/^minecraft_version=//p' gradle.properties | tr -d '\r')}
python3 scripts/build-targets.py --lines | grep -Fxq -- "$minecraft_version" || { echo 'Unsupported Minecraft target' >&2; exit 1; }
server_dir="runs/$minecraft_version/server"
mkdir -p "$server_dir"
if [[ ! -e "$server_dir/server.properties" ]]; then
  cat > "$server_dir/server.properties" <<'PROPERTIES'
server-ip=127.0.0.1
server-port=25565
view-distance=4
simulation-distance=4
online-mode=true
PROPERTIES
fi
exec ./gradlew "-Pminecraft_version=$minecraft_version" runServer --no-daemon --console=plain
