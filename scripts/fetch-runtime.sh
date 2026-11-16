#!/usr/bin/env bash
# Fetch the pinned opencode aarch64 runtime and place it where AGP picks up
# native libraries. Both files are executed from nativeLibraryDir, which is the
# only app-owned path Android lets us exec on API 29+.
set -euo pipefail

VERSION="2.2.5-2"
URL="https://github.com/Hope2333/opencode-termux/releases/download/Push261104/opencode-2.2.5-2-aarch64.pkg.tar.xz"
PKG_SHA256="b4bdaf929954552e98a9a2a2e6dc878f1d6521b2e37a17cd42f81ad5b0b9fa33"
BIN_SHA256="af51da7e202f9efd8fc0733e49a6d6be7586f75e4ca1a265ebc07f06f21dfdf0"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
BIN_DEST="$DEST_DIR/libopencode.so"
HELPER_DEST="$DEST_DIR/libopencode-crhandler.so"

if [[ -f "$BIN_DEST" && -f "$HELPER_DEST" ]]; then
    echo "runtime already present: $DEST_DIR"
    exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "downloading opencode $VERSION runtime"
curl -fL --retry 3 --progress-bar -o "$TMP/runtime.pkg.tar.xz" "$URL"

echo "$PKG_SHA256  $TMP/runtime.pkg.tar.xz" | sha256sum -c -

tar -xJf "$TMP/runtime.pkg.tar.xz" -C "$TMP"

BIN_SRC="$TMP/data/data/com.termux/files/usr/bin/opencode"
HELPER_SRC="$TMP/data/data/com.termux/files/usr/lib/opencode/libopencode-crhandler.so"

echo "$BIN_SHA256  $BIN_SRC" | sha256sum -c -

mkdir -p "$DEST_DIR"
cp "$BIN_SRC" "$BIN_DEST"
cp "$HELPER_SRC" "$HELPER_DEST"
chmod 0755 "$BIN_DEST"

echo "runtime $VERSION installed:"
echo "  $BIN_DEST ($(du -h "$BIN_DEST" | cut -f1))"
echo "  $HELPER_DEST ($(du -h "$HELPER_DEST" | cut -f1))"
