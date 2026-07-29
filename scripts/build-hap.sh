#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
deveco_home="${DEVECOSTUDIO_HOME:-/Applications/DevEco-Studio.app/Contents}"
sdk_root="${DEVECO_SDK_HOME:-$deveco_home/sdk}"
node_home="${NODE_HOME:-$deveco_home/tools/node}"
hvigor="$deveco_home/tools/hvigor/bin/hvigorw.js"

if [[ "$sdk_root" == */default ]]; then
  printf 'DEVECO_SDK_HOME must be the SDK root, not the default subdirectory: %s\n' "$sdk_root" >&2
  exit 2
fi
[[ -x "$node_home/bin/node" ]] || { printf 'Node.js not found: %s\n' "$node_home/bin/node" >&2; exit 2; }
[[ -f "$hvigor" ]] || { printf 'Hvigor not found: %s\n' "$hvigor" >&2; exit 2; }

cd "$repo_root"
DEVECO_SDK_HOME="$sdk_root" NODE_HOME="$node_home" \
  PATH="$node_home/bin:$PATH" "$node_home/bin/node" "$hvigor" \
  --mode module -p product=default -p module=entry@default assembleHap \
  --analyze=normal --parallel --incremental --no-daemon
