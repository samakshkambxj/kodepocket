# KodePocket

**opencode, in your pocket.** A native Android app that runs the
[opencode](https://opencode.ai) coding agent on your phone — no PC, no Termux,
no PRoot, no remote server.

<p align="left">
  <img alt="Android 9+" src="https://img.shields.io/badge/Android-9%2B%20·%20arm64-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Material 3 Expressive" src="https://img.shields.io/badge/Material%203-Expressive-006A6A">
  <img alt="License MIT" src="https://img.shields.io/badge/license-MIT-blue">
</p>

> **Status:** M1. The app bundles the opencode runtime, executes it from
> `nativeLibraryDir`, and supervises it with a foreground service. Chat and
> tool approvals are the next milestones.

## Why

The existing Android clients connect to an opencode instance running on a
computer. KodePocket runs the real server and tools inside the app sandbox, so
the phone is the computer.

## How it works

```
Compose UI ──HTTP/SSE──▶ opencode serve (127.0.0.1, Basic auth)
                              │
                              └── bundled aarch64 binary in jniLibs
```

The opencode binary is shipped inside the APK as a native library, because
Android 10+ forbids executing files from app-writable storage. A foreground
service owns the process, keeps a wake lock during long runs, and shows an
ongoing notification with a stop action. The app's network security config
permits cleartext only to `127.0.0.1`/`localhost`, and each launch gets a fresh
random Basic-auth password.

The runtime also needs a small helper (`libopencode-crhandler.so`). Both ELFs
are extracted from `jniLibs`, and the supervisor points `LD_LIBRARY_PATH` at
`nativeLibraryDir` so the linker resolves the helper.

See [docs/PLAN.md](docs/PLAN.md) for the full architecture, milestones, risks,
and commit conventions.

## Install

Grab the newest APK from
[Releases](https://github.com/samakshkambxj/kodepocket/releases). It is built
for arm64 devices on Android 9 and newer, and it is about 110 MB because the
runtime ships inside the APK.

Alphas before `v0.1.0-alpha.4` were signed with a throwaway debug key. If you
installed one, uninstall it first; releases from `.4` on upgrade in place.

## Build

Requirements: JDK 17+, Android SDK 37, `curl`, `xz`, and an arm64 device for
anything beyond UI work.

```sh
./scripts/fetch-runtime.sh   # downloads the pinned 77 MB runtime package
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

`app/src/main/jniLibs/` is gitignored; the fetch script verifies both the
package and binary SHA-256 before copying. CI caches the result between runs.

CI builds every push and pull request. Releases are built from tags:

```sh
git tag -a v0.1.0 -m "KodePocket v0.1.0"
git push origin v0.1.0
```

The release workflow publishes a signed APK and `SHA256SUMS.txt` to GitHub
Releases. See [docs/RELEASING.md](docs/RELEASING.md) for signing secrets.

## Roadmap

| Milestone | What lands |
|---|---|
| M1 ✅ | Bundled runtime, exec from `nativeLibraryDir`, foreground service |
| M2 | `bash`, `git`, and `rg` toolchain |
| M3 | HTTP/SSE client and server state machine |
| M4 | Sessions, chat, tool approvals, diffs |
| M5–M6 | Workspaces and terminal |
| M7–M8 | Reliability hardening and first public release |

## License

MIT. KodePocket is an unofficial client and is not affiliated with the opencode
project. opencode itself is MIT-licensed; bundled third-party notices will ship
with the runtime.
