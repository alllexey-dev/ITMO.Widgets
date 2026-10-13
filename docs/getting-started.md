# Getting started

From a clean machine to the app running on an Android emulator and an iOS
simulator. Read [architecture.md](architecture.md) next.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | The scripts pick it with `/usr/libexec/java_home -v 21`. The Gradle daemon runs on JetBrains JDK 21 (`gradle/gradle-daemon-jvm.properties`) and Gradle downloads it if it is missing. Code compiles to JVM 17 bytecode |
| Android SDK | platform 37 (compileSdk), minSdk 26, targetSdk 36 | Point `ANDROID_HOME` at it, or put `sdk.dir=...` into `local.properties` (Android Studio writes it) |
| Android Studio | a version that supports AGP 9.3 and Kotlin 2.4 | Optional for command-line work |
| Xcode | 27.0 (build 27A266a) | iOS only; pinned in `scripts/ios/env.sh`, CI uses exactly this one |
| XcodeGen | 2.46.0 | iOS only; `brew install xcodegen` or the GitHub release CI uses |
| iOS simulator | iPhone 17, iOS 27.0 runtime | Deployment target is iOS 26.1 |
| MyItmoApi checkout | optional | Only to change the library itself, see below |

`scripts/ios/env.sh` warns when your Xcode or XcodeGen differs from the pin.
It still builds; CI does not.

## Clone and open

```bash
git clone https://github.com/alllexey-dev/ITMO.Widgets.git
cd ITMO.Widgets
```

Open the repository root in Android Studio. The first sync downloads Gradle,
the JetBrains JDK and every dependency. MyItmoApi comes from Maven Central, so
no other repository is needed.

## Android: first build and run

The app has two flavours with one `applicationId`: `github` (APK for GitHub
Releases) and `play` (AAB for Google Play). For everyday work use
`githubDebug`.

```bash
./gradlew :app:assembleGithubDebug          # app/build/outputs/apk/github/debug/app-github-debug.apk
./gradlew :app:installGithubDebug           # install on the running emulator or device
./gradlew :app:assemblePlayDebug            # the play flavour
```

In Android Studio pick the `githubDebug` build variant and run the `app`
configuration.

Debug builds talk to the dev Backend `https://dev.widgets.alllexey.dev`, never
to production. To get past the sign-in screen:

- sign in with ITMO.ID (the official page opens inside the app);
- or use «Другой способ входа» and paste a refresh token from the
  `auth.refresh_token.itmoId` cookie of my.itmo.ru;
- or tap the logo five times for the demo session with fictional data
  ([demo session](features/demo.md)).

Debug builds also have developer tools on the profile tab
([debug tools](features/debug.md)).

## iOS: first build and run

```bash
xcodegen generate --spec iosApp/project.yml
open iosApp/ITMOWidgets.xcodeproj
```

Run the `ITMOWidgets` scheme on an iPhone simulator. The project file is
generated and ignored; regenerate it after pulling changes to
`iosApp/project.yml` or after adding Swift files.

The app target has a Run Script that builds the Kotlin framework with
`./gradlew :shared:ios:embedAndSignAppleFrameworkForXcode` before Swift
compiles. The first build downloads Kotlin/Native (about 1 GB into
`~/.konan`) and takes a while. Builds are unsigned by default, which is
enough for the simulator. Debug builds call the dev Backend.

`scripts/ios/test.sh` does the same generation and build from the command line
and runs the tests ([testing.md](testing.md#ios)).

## Everyday commands

Run from the repository root.

```bash
./gradlew verifyQuick                                 # what CI's android-ci runs: all unit and host tests, Konsist, lintGithubDebug, both debug APKs
./gradlew :app:testGithubDebugUnitTest                # app JVM tests
./gradlew :shared:feature-schedule:testAndroidHostTest # one shared module's tests on the JVM
./gradlew :konsist:test                               # architecture rules
./gradlew :app:lintGithubDebug                        # lint (verifyFull adds lintPlayDebug)
./gradlew verifyIosKlibs                              # iOS compile check, no Xcode needed
scripts/verify.sh shots feature-schedule              # compare one module's screenshots with its baselines
scripts/verify.sh shots feature-schedule --record     # re-record after a deliberate UI change
scripts/verify.sh shots all                           # every module, as CI does
scripts/check-docs.sh                                 # dead paths and links in the docs
scripts/ios/test.sh                                   # iOS build, unit and snapshot tests
```

Emulator tests, iOS UI tests and how CI splits all this are in
[testing.md](testing.md).

## Working on MyItmoApi

The app takes MyItmoApi (`dev.alllexey:my-itmo-api-kmp`) from Maven Central
at the version in `gradle/libs.versions.toml`. To build the app against local
library changes, clone [my-itmo-api](https://github.com/alllexey-dev/my-itmo-api)
and point `MYITMOAPI_DIR` at it:

```bash
MYITMOAPI_DIR=~/src/my-itmo-api ./gradlew verifyQuick
```

Gradle then includes the checkout as a composite build and ignores the catalog
version. Nothing is published to `~/.m2`. Commit only a version bump to a
released artifact.

## Secrets and local files

Nothing secret is needed for debug builds. Never commit or paste any of these
files; they are ignored by Git.

| File | Purpose |
|---|---|
| `local.properties` | `sdk.dir` of the Android SDK |
| `keystore.properties` | Release signing; copy `keystore.properties.example` and fill it in. Without it release outputs are unsigned |
| `app-keystore.jks` | The release key that `keystore.properties` names |
| `iosApp/Config/Signing.local.xcconfig` | iOS signing: `DEVELOPMENT_TEAM`, `ITMO_CODE_SIGN_IDENTITY`, `ITMO_ENTITLEMENTS_VARIANT = .signed`. Only for device builds and archives |

`app/google-services.json` is Firebase client configuration and stays tracked.
Use synthetic data in fixtures and screenshots, never real tokens or user
data, and never test against production data.
