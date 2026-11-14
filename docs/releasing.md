# Releasing

Sona is free software (GPLv3), and its releases are built in the open by GitHub Actions.

## The workflows

| Workflow | When | What |
|---|---|---|
| `ci.yml` | every pull request and push to `main` | format (`spotlessCheck`), unit tests, lint, and the debug APKs of both engines. The `CI` job is the one check to require in branch protection |
| `release.yml` | run by hand (Actions → Release → Run workflow), on a branch or tag | runs `ci.yml`, then builds the signed `full` APK and app bundle, checks that both are signed, and keeps them as the artifact `sona-release` |
| `dependabot.yml` | weekly | keeps the pinned actions, the version catalog and the Gradle wrapper current |

Every action is pinned to a commit; Dependabot moves the pins.

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

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts` and merge it into `main`.
2. Run the Release workflow on `main` (or on a tag).
3. Download `sona-release` from the run: the APK is for GitHub Releases and F-Droid-style
   distribution, the bundle for Google Play.
4. Publish the APK and the SHA-256 of the signing certificate, so people can verify it:
   `apksigner verify --print-certs sona-full-release.apk`.
