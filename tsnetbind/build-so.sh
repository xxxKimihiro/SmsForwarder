#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-/opt/android-sdk/ndk/26.1.10909125}}"
PREBUILT="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API="${ANDROID_API:-26}"
OUT="$ROOT/app/src/main/jniLibs"
cd "$(dirname "$0")"

# 优先用本机已缓存的 Go 1.26（gomobile 编 AAR 时下过），系统 go 1.22 也行
if [[ -x "${HOME}/go/pkg/mod/golang.org/toolchain@v0.0.1-go1.26.7.linux-amd64/bin/go" ]]; then
  export PATH="${HOME}/go/pkg/mod/golang.org/toolchain@v0.0.1-go1.26.7.linux-amd64/bin:$PATH"
fi
if ! command -v go >/dev/null; then
  echo "go not found" >&2
  exit 1
fi
echo "using $(go version)"
if [[ ! -d "$NDK" ]]; then
  echo "NDK not found: $NDK" >&2
  exit 1
fi

build_abi() {
  local goarch="$1"
  local cc="$2"
  local outdir="$3"
  local extra="${4:-}"
  mkdir -p "$OUT/$outdir"
  echo "building $outdir ($goarch) with $cc"
  # shellcheck disable=SC2086
  env CGO_ENABLED=1 GOOS=android GOARCH="$goarch" $extra CC="$cc" \
    go build -buildmode=c-shared -trimpath \
    -ldflags="-s -w -extldflags -Wl,-soname,libtsnetbind.so" \
    -o "$OUT/$outdir/libtsnetbind.so" .
  rm -f "$OUT/$outdir/libtsnetbind.h"
  ls -lh "$OUT/$outdir/libtsnetbind.so"
}

build_abi arm64 "$PREBUILT/bin/aarch64-linux-android${API}-clang" arm64-v8a
build_abi arm "$PREBUILT/bin/armv7a-linux-androideabi${API}-clang" armeabi-v7a "GOARM=7"
echo "wrote $OUT/{arm64-v8a,armeabi-v7a}/libtsnetbind.so"
