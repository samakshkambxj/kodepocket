# Releasing

A release is a tag push. The `Release` workflow builds the APK, signs it,
generates release notes, and attaches the artifacts to a GitHub Release.

## 1. Signing key

Tag builds fall back to the debug key so they stay installable, but a real
release needs a private key. Create one once and keep it out of the repository:

```sh
keytool -genkeypair -v \
  -keystore kodepocket-release.p12 \
  -storetype PKCS12 \
  -alias kodepocket \
  -keyalg RSA -keysize 4096 -validity 10000
```

Store it and its passwords as repository secrets:

```sh
base64 -w0 kodepocket-release.p12 | gh secret set KODEPOCKET_KEYSTORE_BASE64
gh secret set KODEPOCKET_KEYSTORE_PASSWORD
gh secret set KODEPOCKET_KEY_ALIAS
gh secret set KODEPOCKET_KEY_PASSWORD
```

`KODEPOCKET_KEY_ALIAS` is the `-alias` value above (`kodepocket`). The
keystore must be PKCS#12; the build reads it as such. Both passwords may be
the same.

Back up the keystore somewhere safe. Losing it means existing installs cannot
be updated in place. Installations signed with the old debug fallback must be
uninstalled once.

## 2. Version

Use semver, prefixed with `v`:

```sh
git tag -a v0.1.0 -m "KodePocket v0.1.0"
git push origin v0.1.0
```

The workflow strips the `v` for `versionName` and uses the run number for
`versionCode`, so every release is strictly increasing.

Tags containing a hyphen (`v0.1.0-alpha.1`) are published as prereleases.

## 3. What gets published

- `kodepocket-<version>.apk`
- `mapping-<version>.txt`, when R8 produced a mapping
- `SHA256SUMS.txt`

Verify a download with:

```sh
sha256sum -c SHA256SUMS.txt
```

## 4. F-Droid

F-Droid builds from source and signs with its own key. Requirements once the
runtime lands:

- No proprietary dependencies or prebuilt binaries at build time unless they
  are reproducible and documented.
- A `fastlane/metadata/android/en-US/` listing.
- `NonFreeDep`/`NonFreeAssets` anti-feature entries if the runtime cannot be
  rebuilt from source.

The vendored opencode binary must have a documented, reproducible build before
submission. Until then, GitHub Releases are the supported channel.
