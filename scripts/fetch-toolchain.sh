#!/usr/bin/env bash
# Fetch the pinned Termux toolchain (bash, busybox, git, ripgrep, curl) and
# install it as native libraries. Termux binaries are bionic ELFs, so they run
# unchanged on Android; only their prefix differs, which LD_LIBRARY_PATH and
# the environment in RuntimeEnvironment take care of.
#
# Every package version and SHA-256 is pinned below. Bumping a version means
# updating the table and the library/alias lists when upstream changes them.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSET_DIR="$ROOT/app/src/main/assets/toolchain"
STAMP="$DEST_DIR/.toolchain"
TOOLCHAIN_VERSION="2026.11-t1"
REPO="https://packages.termux.dev/apt/termux-main"

# package|version|sha256|filename
PACKAGES='
bash|5.3.20|26b5b3ab5b0ac0e3b372ac1e7ddd89270519c1eb25b88fe4175485c2de8086b2|pool/main/b/bash/bash_5.3.20_aarch64.deb
busybox|1.38.0-1|1bb7f1d4c00cadd0e1117b6dd7110311b8bf749ef00b486e96cfdc11c98f8fd9|pool/main/b/busybox/busybox_1.38.0-1_aarch64.deb
git|2.56.1|3343f56fc99e2f0631a553a39789769495435cb07b6ffb3c71a7fcb7bd561e95|pool/main/g/git/git_2.56.1_aarch64.deb
ripgrep|15.2.0|38e28bc297000517b24702568a483eca7dc3323eb6bdccc9033f031776bdcc6c|pool/main/r/ripgrep/ripgrep_15.2.0_aarch64.deb
curl|8.23.0|e57a9975a828eb48790bec0da950795af165aa714f6a33d266c45a4e2898aa85|pool/main/c/curl/curl_8.23.0_aarch64.deb
ca-certificates|1:2026.11.05-1|12fc350fc5ad638338b06b04835bce897615f8c93f96d30ac396cdb39b850c6b|pool/main/c/ca-certificates/ca-certificates_1:2026.11.05-1_all.deb
readline|8.3.6|616cc73009bc0223f61caed8dd09455714455aa5ea5e180126e2f69c4c775136|pool/main/r/readline/readline_8.3.6_aarch64.deb
ncurses|6.6.20260307+really6.5.20250830|f44bbfdc3d42ec0217bffa978309390e59cea5a48a9a83226d4a496c42ad0b99|pool/main/n/ncurses/ncurses_6.6.20260307+really6.5.20250830_aarch64.deb
libandroid-support|29-1|f2f145d6135ad4843ac9670153be3e3944dc1e6f1736d46d2306c28f2b86f517|pool/main/liba/libandroid-support/libandroid-support_29-1_aarch64.deb
libandroid-selinux|14.0.0.11-1|00afd8c34087c2864737b51fd9d104dc5e955f6ec3c0f50c0c7ef5b4a56866b9|pool/main/liba/libandroid-selinux/libandroid-selinux_14.0.0.11-1_aarch64.deb
libiconv|1.19.1|a1c6323eff4f660e16ae6f5832356444b3269c19a4f61146355a49e7c4be5ed1|pool/main/libi/libiconv/libiconv_1.19.1_aarch64.deb
pcre2|10.49|c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724|pool/main/p/pcre2/pcre2_10.49_aarch64.deb
openssl|1:3.6.5|a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b|pool/main/o/openssl/openssl_1:3.6.5_aarch64.deb
libcurl|8.23.0|2011bb9709261bb3d5ecaa6672faa333be14cf48ad4af37031e3e365309557ac|pool/main/libc/libcurl/libcurl_8.23.0_aarch64.deb
libexpat|2.10.0|0e4411f01f6fb664f3f04f9420289472f9afc30db01ea1c5d1e57fd9fa2354be|pool/main/libe/libexpat/libexpat_2.10.0_aarch64.deb
libnghttp2|1.71.0|ae33e7b21e0636be8686b9b13a20e31267f047f69cb26b4befd70c9a9bae221e|pool/main/libn/libnghttp2/libnghttp2_1.71.0_aarch64.deb
libnghttp3|1.19.0|8bb6fa31f9281cd48047ec9a571cadf153538f5b38f0203ad7a4e221344b14a6|pool/main/libn/libnghttp3/libnghttp3_1.19.0_aarch64.deb
libngtcp2|1.26.0|8225ecde58104ef5aa234fa54a1bea5051a8a9ef2937e5ba996b3e8dda68c8eb|pool/main/libn/libngtcp2/libngtcp2_1.26.0_aarch64.deb
libssh2|1.11.1-2|1add4e0a926b848814e7e2f1817ea28b123c54cabd5bbc4e5cfd65291cbad84e|pool/main/libs/libssh2/libssh2_1.11.1-2_aarch64.deb
zlib|1.4|e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e|pool/main/z/zlib/zlib_1.4_aarch64.deb
'

# source file in lib/|soname the linker asks for|jniLibs name
LIBS='
libandroid-selinux.so|libandroid-selinux.so|libandroid_selinux.so
libandroid-support.so|libandroid-support.so|libandroid_support.so
libbusybox.so.1.38.0|libbusybox.so.1.38.0|libbusybox.so
libcrypto.so.3|libcrypto.so.3|libcrypto.so
libcurl.so|libcurl.so|libcurl.so
libexpat.so.1.13.1|libexpat.so.1|libexpat.so
libiconv.so|libiconv.so|libiconv.so
libncursesw.so.6.5|libncursesw.so.6|libncursesw.so
libnghttp2.so|libnghttp2.so|libnghttp2.so
libnghttp3.so|libnghttp3.so|libnghttp3.so
libngtcp2.so|libngtcp2.so|libngtcp2.so
libngtcp2_crypto_ossl.so|libngtcp2_crypto_ossl.so|libngtcp2_crypto_ossl.so
libpcre2-8.so|libpcre2-8.so|libpcre2_8.so
libreadline.so.8.3|libreadline.so.8|libreadline.so
libssh2.so|libssh2.so|libssh2.so
libssl.so.3|libssl.so.3|libssl.so
libz.so.1.4|libz.so.1|libz.so
'

# executable in bin/|jniLibs name
BINS='
bash|libkp_bash.so
busybox|libkp_busybox.so
git|libkp_git.so
rg|libkp_rg.so
curl|libkp_curl.so
'

if [[ -f "$STAMP" && "$(cat "$STAMP")" == "$TOOLCHAIN_VERSION" &&
      -f "$ASSET_DIR/manifest.json" && -f "$ASSET_DIR/cert.pem" &&
      -d "$ASSET_DIR/terminfo" && -d "$ASSET_DIR/git/templates" ]]; then
    echo "toolchain already present: $DEST_DIR"
    exit 0
fi

PREFIX_PATH="data/data/com.termux/files/usr"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "downloading $TOOLCHAIN_VERSION toolchain"
mkdir -p "$TMP/debs" "$TMP/root"
while IFS='|' read -r name version sha filename; do
    [[ -z "$name" ]] && continue
    deb="$TMP/debs/$name.deb"
    curl -fsSL --retry 3 -o "$deb" "$REPO/$filename"
    printf '%s  %s\n' "$sha" "$deb" | sha256sum -c --quiet -
    member="$(ar t "$deb" | grep '^data\.tar')"
    extract_flags=(-x -C "$TMP/root" --no-same-owner)
    case "$member" in
        *.xz) extract_flags=(-xJ -C "$TMP/root" --no-same-owner) ;;
        *.gz) extract_flags=(-xz -C "$TMP/root" --no-same-owner) ;;
        *.zst) extract_flags=(--zstd -x -C "$TMP/root" --no-same-owner) ;;
    esac
    ar p "$deb" "$member" | tar "${extract_flags[@]}"
done <<< "$PACKAGES"

SRC="$TMP/root/$PREFIX_PATH"
mkdir -p "$DEST_DIR" "$ASSET_DIR/git"
# The runtime installer owns libopencode*.so; everything libkp_* belongs here.
rm -f "$DEST_DIR"/libkp_*.so

echo "installing native libraries"
while IFS='|' read -r source soname jni; do
    [[ -z "$source" ]] && continue
    install -m 0755 "$SRC/lib/$source" "$DEST_DIR/$jni"
done <<< "$LIBS"

echo "installing executables"
while IFS='|' read -r tool jni; do
    [[ -z "$tool" ]] && continue
    install -m 0755 "$SRC/bin/$tool" "$DEST_DIR/$jni"
done <<< "$BINS"

# git resolves subcommands through GIT_EXEC_PATH, and the single git binary is
# reachable under many names. Mirror Termux's own layout: every libexec entry
# that is a link to bin/git becomes a name pointing at our libkp_git.so. The
# HTTP transport is a separate binary in git 2.56.
git_aliases=()
for entry in "$SRC"/libexec/git-core/*; do
    name="$(basename "$entry")"
    target="$(readlink -f "$entry")"
    case "$target" in
        "$(readlink -f "$SRC/bin/git")")
            git_aliases+=("$name|libkp_git.so")
            ;;
        "$(readlink -f "$SRC/libexec/git-core/git-remote-http")")
            git_aliases+=("$name|libkp_git_remote_http.so")
            ;;
    esac
done
install -m 0755 "$SRC/libexec/git-core/git-remote-http" "$DEST_DIR/libkp_git_remote_http.so"

# bin/ also carries a few aliases (git-upload-pack and friends).
while IFS= read -r entry; do
    name="$(basename "$entry")"
    target="$(readlink -f "$entry")"
    case "$name" in
        bash|busybox|git|rg|curl) continue ;;
    esac
    # Only git's own helpers belong in GIT_EXEC_PATH; ash and friends are
    # already applets on PATH.
    case "$name" in
        git*) ;;
        *) continue ;;
    esac
    case "$target" in
        "$(readlink -f "$SRC/bin/git")") git_aliases+=("$name|libkp_git.so") ;;
    esac
done < <(find "$SRC/bin" -maxdepth 1 -type l)

# One JSON key per name; libexec and bin overlap on git-upload-pack.
git_aliases=($(printf '%s\n' "${git_aliases[@]}" | sort -u))

echo "copying certificate bundle and terminal database"
install -m 0644 "$SRC/etc/tls/cert.pem" "$ASSET_DIR/cert.pem"
rm -rf "$ASSET_DIR/terminfo"
mkdir -p "$ASSET_DIR/terminfo"
cp -R "$SRC/share/terminfo/." "$ASSET_DIR/terminfo/"
rm -rf "$ASSET_DIR/git/templates"
mkdir -p "$ASSET_DIR/git/templates"
cp -R "$SRC/share/git-core/templates/." "$ASSET_DIR/git/templates/"

echo "writing manifest"
{
    printf '{\n  "version": "%s",\n' "$TOOLCHAIN_VERSION"
    printf '  "bin": {'
    first=1
    while IFS='|' read -r tool jni; do
        [[ -z "$tool" ]] && continue
        [[ $first -eq 1 ]] || printf ','
        first=0
        printf '\n    "%s": "%s"' "$tool" "$jni"
    done <<< "$BINS"
    printf '\n  },\n  "lib": {'
    first=1
    while IFS='|' read -r _ soname jni; do
        [[ -z "$soname" ]] && continue
        [[ $first -eq 1 ]] || printf ','
        first=0
        printf '\n    "%s": "%s"' "$soname" "$jni"
    done <<< "$LIBS"
    printf '\n  },\n  "gitExec": {'
    first=1
    for pair in "${git_aliases[@]}"; do
        [[ $first -eq 1 ]] || printf ','
        first=0
        printf '\n    "%s": "%s"' "${pair%%|*}" "${pair##*|}"
    done
    printf '\n  },\n  "applets": ['
    first=1
    for applet in $(sed 's/#.*//' "$ROOT/scripts/busybox-applets.txt" | tr -s ' \n' '\n' | grep -v '^$' | sort -u); do
        [[ $first -eq 1 ]] || printf ','
        first=0
        printf '\n    "%s"' "$applet"
    done
    printf '\n  ]\n}\n'
} > "$ASSET_DIR/manifest.json"

echo "$TOOLCHAIN_VERSION" > "$STAMP"

echo "toolchain $TOOLCHAIN_VERSION installed:"
du -sh "$DEST_DIR" "$ASSET_DIR"
applets=$(sed 's/#.*//' "$ROOT/scripts/busybox-applets.txt" | tr -s ' \n' '\n' | grep -cv '^$' || true)
echo "  ${#git_aliases[@]} git aliases, $applets busybox applets"
