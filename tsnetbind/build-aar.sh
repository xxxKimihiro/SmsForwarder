#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/libs/tsnetbind.aar"
cd "$(dirname "$0")"

if ! command -v go >/dev/null; then
  echo "go not found" >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
  echo "set ANDROID_HOME" >&2
  exit 1
fi
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "set ANDROID_NDK_HOME" >&2
  exit 1
fi

if ! command -v gomobile >/dev/null; then
  go install golang.org/x/mobile/cmd/gomobile@latest
  go install golang.org/x/mobile/cmd/gobind@latest
  export PATH="$(go env GOPATH)/bin:$PATH"
  gomobile init
fi

go mod tidy
mkdir -p "$(dirname "$OUT")"
gomobile bind -v -target=android/arm,android/arm64 -androidapi 26 -javapkg cn.kosync.tsnet -o "$OUT" .
echo "wrote $OUT"
