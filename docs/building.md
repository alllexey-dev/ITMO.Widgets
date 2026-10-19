# Building your own copy

How anyone builds an installable app without the owner's keys, and how such a
build names itself. A fork needs no changes for any of it. The development
setup is in [getting-started](getting-started.md).

## Without building

Run the `dist` workflow (Actions -> dist -> Run workflow) in this repository or
in a fork. It needs no secrets and uploads two artifacts:

- `itmo-widgets-custom-apk`: the `github` release APK against production,
  signed with the runner's debug key;
- `itmo-widgets-unsigned-ipa`: an unsigned `.ipa` to re-sign with AltStore,
  SideStore or your own Apple ID.

Every published release also gets the unsigned `.ipa` attached by the same
workflow.

## Android

JDK 21 and the Android SDK (`ANDROID_HOME` or `sdk.dir` in `local.properties`);
nothing else.

```bash
./gradlew :app:assembleGithubRelease -Pitmo.selfSigned=true
# app/build/outputs/apk/github/release/app-github-release.apk
```

`-Pitmo.selfSigned=true` signs the release with your local debug key when
`keystore.properties` is absent; without it the release APK is unsigned and
does not install. Your key differs from the official one, so the APK does not
update an installed official build and the official one does not update it:
uninstall first. `./gradlew :app:assembleGithubDebug` is the debug build, which
talks to the development Backend.

## iOS

A Mac with Xcode 27.0 and XcodeGen 2.46.0 (the pins in `scripts/ios/env.sh`).

```bash
scripts/ios/archive.sh --unsigned
# iosApp/build/archive/ITMOWidgets-<version>-<build>-unsigned.ipa
```

Re-sign the `.ipa` with AltStore, SideStore or your own team. It has no
notification service extension: push needs the official Apple account
([ios](ios.md#degradations)).

## Build identity

Every build records where it comes from, so client statistics tell official
builds from custom ones and group both by the upstream version. Nothing here is
secret and nothing is a security boundary: Backend must not trust these values,
they only label.

| Field | Android `BuildConfig` | iOS Info.plist (app and notification service) | Values |
|---|---|---|---|
| Channel | `BUILD_CHANNEL` | `BuildChannel` | `official` or `custom` |
| Source | `BUILD_SOURCE` | `BuildSource` | `<owner>/<repo>` from `GITHUB_REPOSITORY` on GitHub Actions, else `local` |
| Commit | `BUILD_COMMIT` | `BuildCommit` | 12 hex digits of the commit; empty when unknown |
| Version | `VERSION_NAME` | `CFBundleShortVersionString` | the upstream version the build is based on |
| Build | `VERSION_CODE` | `CFBundleVersion` | see below |

- **Channel.** `official` only for the owner's signing: on Android when the
  release key's certificate has the official SHA-256
  `939e9bd52c89fbb7a9b3c6b8d0f0883ee2c9c12c6d8fed922251e2bdfbc1f071`, checked
  by `app/build.gradle.kts` at build time; on iOS when the owner's ignored
  `Signing.local.xcconfig` sets `ITMO_BUILD_CHANNEL = official`. Everything
  else is `custom`, including the unsigned `.ipa` of the `dist` workflow
  (`archive.sh --unsigned` always sets `custom`) and CI builds of this
  repository.
- **Commit.** Android stamps it in release-type builds only, so debug builds
  keep their compile cache (empty in debug). iOS stamps it in `archive.sh`
  builds, with `-dirty` for uncommitted changes; Xcode builds leave it empty.
- **Build numbers do not count commits.** Android's `versionCode` is set per
  release line in `app/build.gradle.kts`
  ([releases](product/releases.md#distribution)). The iOS `CFBundleVersion`
  from `archive.sh` is the commit time in Unix seconds: monotonic along
  `master`, and the same for one commit in every clone, shallow or a fork's;
  Xcode builds keep `1`.
- **Statistics.** The app sends these with its Backend calls in `X-Client`
  ([ADR 0034](https://github.com/alllexey-dev/ITMO.Widgets/pull/457)). The
  header's `channel` is the distribution (`github`, `play`, `sideload`, ...);
  the build channel above is a separate field.

## Workflows in a fork

The upstream workflows run in a fork without changes or secrets. Pushes (a
fork's sync with upstream) and scheduled runs build nothing outside
`alllexey-dev/ITMO.Widgets`; manual runs and the fork's own pull requests run
as they do upstream.
