# Build and tooling

- The build moved to Gradle 9.7.0, AGP 9.3.3 with built-in Kotlin, Kotlin
  2.4.20 and Hilt 2.60.1 on KSP instead of kapt; the app compiles against API
  37 with minSdk 26 and targetSdk 36, and the configuration cache is on.
- `settings.gradle.kts` declares `:app`, 15 `:shared:*` Kotlin Multiplatform
  modules with iOS targets and `:konsist`; `build-logic` holds the convention
  plugins and `gradle/libs.versions.toml` pins every v2.3 library.
- MyItmoApi 2.x builds from source as a composite build at the commit in
  `gradle/myitmoapi.ref` (`-PmyItmoApiDir` or `MYITMOAPI_DIR`); release
  builds take it from Maven Central with `-PmyItmoApiFromCentral=true`. Maven
  Local is gone from the build.
- `scripts/verify.sh` is the one build entry point (`quick`, `full`,
  `klibs`, `shots`, `ui`, `ship`, `run`); `scripts/slot.sh` shares the
  machine between parallel builds and `scripts/emulator.sh` starts clean pool
  emulators, so instrumented tests never run on a phone.
- CI: `android-ci.yml` runs `verify.sh quick`, the Roborazzi screenshot
  comparison of every module and the iOS klibs on every PR and push to
  `v2.3/next` and `master`; `android-nightly.yml` runs the platform
  instrumentation tests under Android Test Orchestrator on a Gradle Managed
  Device and an advisory dependency health report; Dependabot proposes
  updates.
- The QR encoder is a Kotlin port pinned by golden matrices, Google Sheets
  grids parse with Ksoup and avatars load with Coil 3, with the same output
  as before; OkHttp is 5.5.0.
- One lint configuration covers `:app` and the shared modules, and
  `.editorconfig` mirrors the project code style.
- `verify.sh ui` (and ship-check stage 4) reads every run of every
  instrumentation test from the device's TestRunner log
  (`scripts/ui-report.py`): a ShellSuite member that fails inside the suite
  and passes standalone afterwards now fails the run and is listed with its
  suite, run number and shell, where Gradle's reports kept only the last run.
- `scripts/ship-check.sh` reads stages 2, 4, 5 and 6 from the CI checks
  `verify-quick`, `android-ui` and `android-ship` of the head SHA (`--wait`
  polls until they finish) and runs only the version and release build
  stages locally; `--local` keeps the all-local run, and `summary.md` links
  the CI run of each stage. `scripts/test-ship-check.sh` covers it against a
  `gh` stub.
