# KodePocket — Build Plan

**Status:** draft · **Target:** Android arm64 · **License:** MIT (pending)
**Repo:** https://github.com/samakshkambxj/kodepocket

KodePocket is an unofficial, native Android app that runs
[opencode](https://opencode.ai) entirely on the phone. No PC, no Termux, no
PRoot, no remote server.

---

## 1. Scope

### In scope for v0.1

- Native Kotlin + Jetpack Compose UI (single activity).
- opencode server and tools running inside the app sandbox.
- Sessions, prompts, streaming responses, tool approvals, diffs.
- One local workspace, import/export, git operations.
- Foreground service that survives screen-off for long agent runs.
- F-Droid-compatible, fully open source, reproducible build.

### Out of scope for v0.1

- iOS, tablets-specific layouts, x86 emulators.
- Bundled language servers (LSP) and Python toolchains.
- Cloud sync, accounts, telemetry.
- Play Store distribution (decided later, see risk R9).

---

## 2. Verified baseline

Measured on the development device on 2026-11-16, not assumed:

| Fact | Value |
|---|---|
| Device | `arm64-v8a`, Android API 37 |
| opencode | `v2.2.5`, 340 MB ELF (77 MB xz package) |
| Linked libraries | `libc`, `libm`, `libdl`, plus `libopencode-crhandler.so` |
| Minimum Android | API 28 (binary built for Android 28, NDK r27c) |
| Interpreter | `/system/bin/linker64`, PIE, Android-compatible |
| Server | `opencode serve --port N --hostname 127.0.0.1` starts, `/api/info` returns 200 |
| PTY | `capabilities.persistentPty: true` |
| Auth | HTTP Basic, username `opencode`, password from `OPENCODE_PASSWORD` |
| Bun | 1.4.4 publishes `bun-linux-aarch64-android` |
| npm | `opencode-ai` / `@opencode/cli` have **no** Android target |

Useful environment variables found in the binary:

```
OPENCODE_PASSWORD          set the server password ourselves
OPENCODE_DB                database location
OPENCODE_CONFIG_DIR        config location
OPENCODE_PTY_BIN           PTY helper override
OPENCODE_DISABLE_AUTOUPDATE
OPENCODE_LOG_LEVEL
OPENCODE_PRINT_LOGS
```

Implications:

1. The hard part is packaging and lifecycle, not the agent runtime.
2. Auth can be deterministic — no stdout scraping.
3. We must pin and vendor a bionic aarch64 build until upstream ships one.

---

## 3. Architecture

```
┌────────────────────────────── KodePocket APK ──────────────────────────────┐
│                                                                            │
│  :app (Compose, single Activity)                                           │
│    └── SessionList · Chat · Approvals · Diff · Terminal · Settings         │
│                                                                            │
│  :ui        design system, screens, view models                            │
│  :data      Ktor HTTP + SSE client, repositories, OpenAPI models           │
│  :core      domain models, result types, use cases                         │
│  :runtime   bootstrap, process supervisor, health probe, toolchain         │
│                                                                            │
│  ForegroundService ──spawns──▶ nativeLibraryDir/libopencode.so             │
│         │                          serve --hostname 127.0.0.1 --port N     │
│         │                                      │                           │
│         └──────────── HTTP/SSE ◀───────────────┘  (Basic auth, loopback)   │
│                                                                            │
│  jniLibs/arm64-v8a/  libopencode.so  libbash.so  libgit.so  librg.so       │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why spawn the binary instead of embedding `@opencode/sdk`

The SDK is JavaScript and would require embedding a JS engine plus the entire
server graph into the Kotlin process. Spawning the proven, self-contained
binary gives:

- crash isolation (agent crash ≠ app crash),
- the exact upstream behaviour (`opencode serve`),
- a clear upgrade path,
- far less Kotlin↔JS glue.

The trade-off is process lifecycle work, which we need anyway for Android.

### Process lifecycle

1. User taps **Start** (foreground, user-initiated).
2. Service creates an app-private environment (§5) and picks a free port.
3. `ProcessBuilder` runs `nativeLibraryDir/libopencode.so serve`.
4. Password comes from `OPENCODE_PASSWORD` (32 random bytes, base64url).
5. Poll `GET /api/info` with Basic auth until 200 (timeout 120 s; the Bun
   binary is large and cold starts slowly on low-end devices).
6. Publish `ServerEndpoint(url, password)` on a `StateFlow`.
7. On stop: `SIGTERM`, wait 5 s, `SIGKILL`; always release the wake lock.
8. Unexpected exit: surface the failure and the log tail. Automatic restart
   with backoff lands in M7.

---

## 4. Runtime packaging

- Binaries live in `app/src/main/jniLibs/arm64-v8a/` renamed to `lib*.so`.
  Android only executes from `nativeLibraryDir`; `filesDir` is blocked by W^X
  since Android 10.
- `android:extractNativeLibs="true"` and `useLegacyPackaging = true`.
- The runtime has a second ELF, `libopencode-crhandler.so`, loaded through a
  relative `RUNPATH` (`$ORIGIN/../lib/opencode`). Both files ship in
  `jniLibs`; the supervisor sets `LD_LIBRARY_PATH` to `nativeLibraryDir` so the
  linker finds the helper.
- `scripts/fetch-runtime.sh` downloads the pinned package, verifies the
  tarball and binary SHA-256, and installs both files. CI runs it before every
  build and caches the result.
- `OPENCODE_DISABLE_AUTOUPDATE=1`; updates ship as APKs.
- Version shown on the home screen and in the health-check result.

Initial runtime source: vendor the community bionic build
([Hope2333/opencode-termux](https://github.com/Hope2333/opencode-termux),
[guysoft/opencode-termux](https://github.com/guysoft/opencode-termux)), then
replace with our own reproducible build once the packaging is stable.

---

## 5. Environment contract

All paths under app-private storage, created on first run:

| Variable | Value |
|---|---|
| `HOME` | `files/home` |
| `TMPDIR` | `cache/tmp` |
| `XDG_CONFIG_HOME` | `files/home/.config` |
| `XDG_DATA_HOME` | `files/home/.local/share` |
| `OPENCODE_CONFIG_DIR` | `files/home/.config/opencode` |
| `OPENCODE_DB` | `files/home/.local/share/opencode/opencode.db` |
| `OPENCODE_PASSWORD` | 32 random bytes, base64url, regenerated per start |
| `PATH` | `files/toolchain/bin` + `nativeLibraryDir` + `/system/bin` |
| `LD_LIBRARY_PATH` | `files/toolchain/lib` + `nativeLibraryDir` |
| `SHELL` | `files/toolchain/bin/bash` |
| `TERMINFO` | `files/toolchain/share/terminfo` |
| `SSL_CERT_FILE`, `CURL_CA_BUNDLE`, `GIT_SSL_CAINFO` | `files/toolchain/etc/tls/cert.pem` |
| `GIT_EXEC_PATH` | `files/toolchain/libexec/git-core` |
| `GIT_TEMPLATE_DIR` | `files/toolchain/share/git-core/templates` |
| `GIT_PAGER`, `PAGER` | `cat` (a pager inside a PTY would block) |
| `GIT_TERMINAL_PROMPT` | `0` |
| `LANG` | `en_US.UTF-8` |
| `TERM` | `xterm-256color` |

No process may write outside the sandbox except through an explicit,
user-granted export.

---

## 6. Milestones

Each milestone is a mergeable, shippable increment with a green build.

### M0 — Guardrails

**Deliverables:** MIT `LICENSE`, `README.md`, `.gitignore`, `.editorconfig`,
Gradle wrapper, `libs.versions.toml`, empty module graph, CI running
`assembleDebug` + `lint` + unit tests.
**Acceptance:** fresh clone builds on CI; `./gradlew check` passes locally.

### M1 — Exec spike (go/no-go)

**Status:** implemented in `app/src/main/java/dev/kodepocket/runtime/` and
`scripts/fetch-runtime.sh`; device verification is the remaining acceptance
step.

**Deliverables:** runtime supervisor + Compose screen that starts the server,
shows `/api/info`, and stops it. Foreground service, wake lock, notification,
`OPENCODE_PASSWORD`, free-port selection.
**Acceptance:** cold start → version visible within 30 s on a mid-range phone;
survives 15 min with the screen off; stop leaves no child process; no ANR.
**Retires:** R1, R4.

### M2 — Toolchain bootstrap

**Status:** shipped in `v0.1.0-alpha.5` and verified on device: the self-check
reports bash 5.3.20, git 2.56.1, ripgrep 15.2.0, busybox 1.38.0 and curl 8.23.0
from the symlink farm.

**Deliverables:** bundle `bash`, `busybox`, `git`, `rg`, `curl` with their
shared libraries; first-run symlink farm; env contract; CA bundle and terminfo.
**Acceptance:** an agent session can `rg` across the workspace, edit files, and
run `git diff`; failures surface as readable tool errors, not crashes.

**How it ships.** Termux's aarch64 packages are plain bionic ELFs, so they run
unchanged on Android. `scripts/fetch-toolchain.sh` pins 20 packages by version
and SHA-256, installs the executables as `libkp_*.so` and the libraries under
sanitized `lib*.so` names into `jniLibs`, and writes
`assets/toolchain/manifest.json` plus the CA bundle, terminfo database and git
templates. At startup `Toolchain` rebuilds three symlink farms in
`files/toolchain`: `bin` (tools and busybox applets), `lib` (sonames the linker
asks for) and `libexec/git-core` (git's subcommands). Executing a symlink
resolves to `nativeLibraryDir`, where SELinux grants `execute_no_trans` on
`apk_data_file` to app domains. The pack grows the APK by roughly 12 MB.

### M3 — Client

**Deliverables:** Ktor client, Basic auth, SSE subscription with reconnect,
OpenAPI-derived models, error mapping, `ServerState` state machine.
**Acceptance:** create session, send prompt, receive streamed parts and tool
events, survive server restart.

### M4 — Product UI

**Deliverables:** session list, chat with streaming markdown, tool-approval
cards, diff viewer, model picker, provider sign-in deep links.
**Acceptance:** complete a real coding task on the phone without a terminal.

### M5 — Workspaces

**Deliverables:** workspace root, create/import/export, SAF import, `git init`,
clone, branch switch, file browser.
**Acceptance:** open a repo, run an agent task, review and export the diff.

### M6 — Terminal (optional for v0.1)

**Deliverables:** PTY tab using the server's PTY API, terminal renderer,
keyboard handling (Esc/Ctrl/arrows).
**Acceptance:** run `git status` and an interactive command from the terminal.

### M7 — Reliability and polish

**Deliverables:** Doze/battery handling, restart policy, crash-safe state,
log export with redaction, opt-in diagnostics, settings, update check.
**Acceptance:** 1-hour unattended run; recovery after force-stop; logs contain
no secrets.

### M8 — v0.1 release

**Deliverables:** signed reproducible APK, `THIRD_PARTY_NOTICES`, F-Droid
metadata, store copy with "unofficial" disclaimer, security review, tag
`v0.1.0`.
**Acceptance:** clean install on a fresh device; F-Droid build succeeds.

### Parallel track — upstream

File or join upstream work for an `opencode-android-arm64` platform package and
the Termux detection fix. If it lands, our vendoring shrinks to a
download step; the app structure is unchanged.

---

## 7. Testing

- **Unit:** supervisor state machine with a fake process, SSE parser, path/env
  builder, auth header, backoff.
- **Robolectric:** view models, formatting, navigation.
- **Instrumented:** device-only. CI x86 emulators cannot run an aarch64
  runtime, so instrumented tests run on a self-hosted arm64 runner or a local
  device. Not a blocker for merges, but required for releases.
- **Manual matrix:** API 28, 31, 33, 35, 37; 4 GB RAM device; screen-off run.
- **Log hygiene:** never log the password, prompts, file contents, or tokens.

---

## 8. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | W^X blocks exec | Fatal | `jniLibs` + `extractNativeLibs`; proven in M1 before building UI |
| R2 | Missing CLI tools break agent | High | Bundle `bash`/`git`/`rg`; smoke tests in M2 |
| R3 | Binary is ~340 MB | High | APK splits, asset delivery, expect ~100 MB download |
| R4 | Doze kills long runs | High | FGS + wake lock + battery prompt; M1 soak test |
| R5 | Localhost is shared by all apps | High | Random password per launch, loopback bind, no logging |
| R6 | JSC/WebKit LGPL static linking | Medium | Legal review; provide relinkable objects or written offer |
| R7 | arm64-only CI | Medium | Self-hosted arm64 runner; Robolectric for logic |
| R8 | Upstream changes break vendored binary | Medium | Pin versions, checksum, upgrade PRs with a soak test |
| R9 | Play policy on interpreters/FGS | Medium | F-Droid first; Play decision after v0.1 |
| R10 | Name/trademark confusion | Low | Distinct mark, "unofficial" everywhere, no upstream logo |

---

## 9. Commit protocol

- **Conventional Commits:** `type(scope): imperative summary`.
- **Types:** `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`,
  `chore`.
- **Scopes:** `app`, `runtime`, `data`, `ui`, `core`, `ci`, `docs`, `deps`.
- One logical change per commit. No mixed refactors. No WIP commits on `main`.
- Subject ≤ 72 chars, body explains *why* not *what*.
- Every commit builds and passes `./gradlew check`.
- Trunk-based: commit straight to `main`; no pull requests.
- Tags: `v0.1.0-alpha.1`, `v0.1.0`, annotated and signed when signing is set up.

Example sequence for M1:

```
feat(runtime): execute bundled server from nativeLibraryDir
feat(runtime): supervise the server process and probe /api/info
feat(app): add foreground service for the local server
feat(ui): show server health and version
test(runtime): cover endpoint auth and port parsing
```

---

## 10. Open questions

1. **Name:** `KodePocket` is the working name; `applicationId dev.kodepocket`.
   Renaming is cheap until the first release.
2. **Distribution:** F-Droid only, or F-Droid + Play? Affects FGS declarations
   and asset delivery.
3. **minSdk:** 28 (runtime requirement) — accept, or target 31+ for a smaller
   support matrix?
4. **LSP:** bundle a Node runtime later, or ship without LSP in v0.1?
5. **LGPL:** how do we satisfy JSC relinking obligations before publishing
   binaries?
