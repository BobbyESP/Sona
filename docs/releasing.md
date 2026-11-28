# Releasing

Sona is free software (GPLv3), and its releases are built in the open by GitHub Actions.

## The workflows

| Workflow | When | What |
|---|---|---|
| `ci.yml` | every pull request and push to `main` | format (`spotlessCheck`), unit tests, lint, and the debug APKs of both engines (format first; the rest run in parallel after it). The `CI` job is the one check to require in branch protection |
| `release.yml` | run by hand (Actions → Release → Run workflow), on a branch or tag | runs `ci.yml`, then builds the signed `full` APKs (per ABI and universal) and app bundle, checks that both are signed, and keeps them as the artifact `sona-release` |
| `dependabot.yml` | weekly | keeps the actions, the version catalog and the Gradle wrapper current |

Every action is used by its major version tag (`@v7`); Dependabot proposes the next major.

## The signing key

The release key is a PKCS12 keystore with one RSA 4096 key (alias `sona`), valid 10,000 days.
**It cannot be replaced**: Android only updates an app that is signed by the same key. Keep a copy
of the keystore and its passwords somewhere safe and apart from the repository (a password
manager). Never commit it: `*.jks` and `*.keystore` are ignored.

The build reads it from the environment, and a release built without it is left unsigned:

| Variable | Meaning |
|---|---|
| `SIGNING_KEY_STORE_PATH` | path of the keystore file |
| `SIGNING_STORE_PASSWORD` | the keystore's password |
| `SIGNING_KEY_ALIAS` | `sona` |
| `SIGNING_KEY_PASSWORD` | the key's password (the same as the keystore's) |

In GitHub, they are the repository secrets `SIGNING_KEY_STORE_BASE64` (the keystore, in base64),
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. `release.yml` stops if
one is missing.

To build a signed release on your own machine:

```bash
SIGNING_KEY_STORE_PATH=/path/to/sona-release.jks SIGNING_STORE_PASSWORD=… \
SIGNING_KEY_ALIAS=sona SIGNING_KEY_PASSWORD=… ./gradlew :app:assembleFullRelease
```

## Making a release

1. Set `currentVersion` in the root `build.gradle.kts` and merge it into `main`. It is the only
   place: `versionName` and `versionCode` come from it. The code packs the stage in
   (`MmmPPsBB`: major, minor, patch, stage, build), so an alpha, a beta, a release candidate and
   the release of one version are ordered. `Version.Beta(1, 0, 0, build = 2)` is `1.0.0-beta.2`,
   code 10000102; `Version.Stable(1, 0, 0)` is `1.0.0`, code 10000300.
2. Run the Release workflow on `main` (or on a tag).
3. Download `sona-release` from the run. The build makes four APKs: one per ABI
   (`*-arm64-v8a-release.apk` for current phones, `*-armeabi-v7a-release.apk` for older 32-bit ARM
   phones, `*-x86_64-release.apk` for emulators and Chromebooks) and `*-universal-release.apk`,
   which holds all of them, for whoever does not know their phone's architecture (it is the
   largest). They are for GitHub Releases and F-Droid-style distribution; the bundle is for Google
   Play, which splits it by itself.
4. Publish the APKs and the SHA-256 of the signing certificate, so people can verify it:
   `apksigner verify --print-certs <apk>` (all four share one key). Say in the release notes which
   file to pick: when unsure, the universal one.

## Screenshots

The pictures of the app (for the course, the README, the release notes) are made by the app itself,
on a phone, with no server: the `demo` flavor runs on the fake engine, and
`app/src/androidTestDemo/.../ScreenshotTour.kt` drives it through the welcome, adding an account, the
keypad, a call, a call that fails and says why, a video call, Recents, Contacts, Messages with a
conversation, Settings and Diagnostics. It finds every node by the app's own strings, so it follows
the language.

```bash
scripts/screenshots.sh            # every variant
scripts/screenshots.sh es-dark    # only one
```

It needs a phone or an emulator with debugging on, **unlocked and awake** for the few minutes the run
takes (`ANDROID_SERIAL` chooses one when several are connected). Per variant (`es-light`,
`es-dark`: Spanish, light and dark theme) the script clears the demo app's data, sets its language
and the system theme, runs the tour with `am instrument`, and replaces
`assets/screenshots/<variant>/` with the full-screen `NN-name.png` files, status bar and blur
included. The status bar is in the system's demo mode (12:00, full battery and signal, no
notifications), and the app is exempt from battery optimization, so the readiness checklist is green. The
theme, demo mode, that exemption and the app's language are put back at the end, even if the run
fails; when the tour fails, the pictures taken so far and `failure.png` (the screen it stopped at)
stay in `build/screenshots/<variant>/` and `assets/` is left as it was.

The tour is an instrumented test (`./gradlew :app:connectedDemoDebugAndroidTest` also runs it,
leaving the pictures in the app's external files); CI only builds it
(`:app:assembleDemoDebugAndroidTest`) so it keeps compiling. When a screen's strings or flow change,
the tour is what fails: fix it there. Large font is not covered yet.
