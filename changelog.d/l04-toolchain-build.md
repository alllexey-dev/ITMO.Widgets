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
- CI: `android-ci.yml` runs the parts of `verify.sh quick` and `full`
  (both lints), the Roborazzi screenshot comparison in three shards and the
  iOS klibs as parallel jobs on every PR and push to `v2.3/next` and
  `master`, behind the one `verify-quick` check, and each job keeps its own
  Gradle build cache line from `v2.3/next`; `android-ui.yml` runs every
  instrumented test on GitHub's emulators, `verify.sh ui all` in six shards
  and `ShellSuite` in two on a tall-cutout emulator, behind the
  `android-ui` check; `android-ship.yml` runs ship check stages 1, 3, 5
  and 6 on every push to `v2.3/next` and uploads `ship-<sha7>`;
  `android-nightly.yml` runs the platform instrumentation tests under
  Android Test Orchestrator on a Gradle Managed Device and an advisory
  dependency health report; Dependabot proposes updates.
- `verify.sh checks` runs the Gradle-free checks of `quick` alone, and
  `verify.sh shots all` and `verify.sh ui` take `--shard <i>/<n>` to run
  one CI shard locally.
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
- Ship check stage 5 prints the signer certificate of the v2.2 and the head
  `githubDebug` before installing and fails with both named when they
  differ; the cached v2.2 APK is named by its certificate and rebuilt with
  the current debug key when none matches, so `android-ship` no longer
  depends on the Actions cache keeping the key that signed it.
