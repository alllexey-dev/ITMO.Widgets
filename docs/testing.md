# Testing

What kinds of tests exist, where they live, how to run and re-record them,
and what CI runs. Commands run from the repository root. Fixtures, previews
and screenshots use synthetic data only.

## Kinds of tests

| Kind | Where | Runs on |
|---|---|---|
| Shared unit and UI tests | `shared/<module>/src/commonTest` | JVM through `androidHostTest`, and the iOS simulator through `iosTest` |
| Shared host tests | `shared/<module>/src/androidHostTest` | JVM with Robolectric: data sources over Ktor `MockEngine`, Compose UI tests, Koin `verify()` (`<X>ModuleTest`) |
| Shared iOS tests | `shared/<module>/src/iosTest` | iOS simulator (`iosSimulatorArm64Test`): iOS ports, the widget timeline writer |
| JVM screenshot tests | `*ScreenshotTest` in `androidHostTest` (shared) and `app/src/test` | JVM with Roborazzi; baselines in `<module>/screenshots/` |
| App unit tests | `app/src/test` (all variants), `app/src/testDebug`, `app/src/testGithub` | JVM: Android ports, workers, the Koin graph (`di/bridge/`), `StableIdentifiersTest`, string and icon catalogs |
| Konsist | `konsist/src/test` | JVM, reads the sources of `app/` and `shared/` |
| Build-logic tests | `build-logic/strings/src/test` | JVM, the string export |
| Instrumented tests | `app/src/androidTest`, `app/src/androidTestPlay` | Android emulator: the shell, widgets, the tile, Keystore, WorkManager, notifications, the 2.2 upgrade |
| iOS unit tests | `iosApp/Tests/UnitTests` (target `ITMOWidgetsTests`) | iOS simulator: router, intents, Keychain, widget snapshots, stable identifiers |
| iOS snapshot tests | `iosApp/Tests/SnapshotTests` | iOS simulator, swift-snapshot-testing; references in `__Snapshots__/` |
| iOS UI tests | `iosApp/Tests/UITests` | XCUITest on the simulator |

Conventions:

- JVM tests are the default; reach for an emulator only for what the JVM
  cannot do.
- Fakes over mocks. Fakes of `:shared:core` contracts live once in
  `shared/core/src/testFixtures/kotlin`, which the core and feature modules
  compile into their tests; a feature's own fakes stay in its tests.
- Time is a fixed `Clock` or `AcademicTimeProvider`, never the wall clock.
- View models are created inside the test body after the main dispatcher is
  set (`MainDispatcherRule` in `app/src/test`).

## JVM tests

```bash
./gradlew verifyQuick                                   # everything below except screenshots, plus lintGithubDebug and both debug APKs
./gradlew verifyFull                                    # verifyQuick plus lintPlayDebug
./gradlew :shared:feature-schedule:testAndroidHostTest  # one shared module (commonTest and androidHostTest)
./gradlew :app:testGithubDebugUnitTest                  # app tests of the github flavour
./gradlew :app:testPlayDebugUnitTest                    # app tests of the play flavour
./gradlew :konsist:test                                 # architecture rules
```

Add `--tests '*ScheduleRepositoryImplTest'` to run one class.

## Screenshot tests

Each module renders every `@Preview` of its `commonMain` with Roborazzi on
Robolectric: one line, `class <X>ScreenshotTest : PreviewScreenshotTest()`, in
`androidHostTest`. Regular test runs exclude screenshot tests; they run only
through `verify.sh shots`.

```bash
scripts/verify.sh shots feature-schedule                # compare with the baselines
scripts/verify.sh shots feature-schedule --record       # re-record after a deliberate change
scripts/verify.sh shots feature-schedule --record -Pshots.appearance=full  # also the two narrow appearances
scripts/verify.sh shots feature-qr --record -Pshots.appearance=ios         # also the three iOS-style appearances
scripts/verify.sh shots app                             # :app (shell chrome, debug screens, widget theme)
scripts/verify.sh shots all                             # every module plus :app, as CI does
scripts/verify.sh shots all --gallery /tmp/shots        # render everything into a new directory, no compare
```

- Baselines are `<module>/screenshots/<Preview>_<appearance>.png`. A run also
  fails on a stale baseline or a preview without one.
- Look at every new or changed PNG before committing it. A baseline changes
  only together with the code behind it.
- A failed comparison writes `<name>_compare.png` (reference, diff, new) to
  `<module>/build/outputs/roborazzi/`; CI uploads them as `screenshot-diffs-*`.
- Baselines recorded on macOS verify on CI's Linux runner under a small
  per-pixel tolerance (`ShotsCompare` in `shared/testing`). Record on a Mac.
- Captures also run accessibility checks (touch targets, labels, contrast).
- Galleries are for review only; never copy a gallery PNG into `screenshots/`.

## Instrumented tests

They run only on an emulator, because they clear app data. Start one (Android
Studio's Device Manager or `emulator -avd <name>`), then:

```bash
ANDROID_SERIAL=emulator-5554 scripts/verify.sh ui WidgetRenderingTest
ANDROID_SERIAL=emulator-5554 scripts/verify.sh ui ShellSuite
ANDROID_SERIAL=emulator-5554 scripts/verify.sh ui all
ANDROID_SERIAL=emulator-5554 scripts/verify.sh ui @platform   # app/src/androidTest/platform-tests.txt under Test Orchestrator
```

- `verify.sh ui` takes bare class names, FQCNs or `Class#method`, comma
  separated, and runs `:app:connectedGithubDebugAndroidTest`. It refuses a
  serial that is not an emulator.
- It ends with `scripts/ui-report.py`, which lists every run of every test, so
  a suite member that fails once and passes later still fails the run.
- `ShellSuite` (`app/src/androidTest/java/dev/alllexey/itmowidgets/app/shell/ShellSuite.kt`)
  groups the navigation tests: entry intents, App Links, session routing,
  the demo flow, shortcuts, the QR tile, notifications, insets, and Back and the
  predictive Back through pushed screens, sheets and tabs (`ShellNavigationTest`).
- `UpgradeFrom22Test` reads the 2.2 data captured in
  `app/src/androidTest/assets/upgrade-2.2/`. A change that moves or reformats
  stored data updates its checker in `upgrade/stores/`, never the assets.
- The Play update tests in `app/src/androidTestPlay` run with
  `:app:connectedPlayDebugAndroidTest`.

Instrumented tests share one app process and the real Koin graph. Sign in
through `TestSession` (`app/src/androidTest/java/dev/alllexey/itmowidgets/testing/TestSession.kt`)
and read the graph through `TestGraph`, never by writing token files directly.

## iOS

```bash
scripts/ios/test.sh                                            # checks, xcodegen, build, unit and snapshot tests
scripts/ios/test.sh --only ITMOWidgetsTests/RouterTests        # one class
scripts/ios/test.sh --record --only SnapshotTests/SampleSnapshotTests  # re-record snapshot references
scripts/ios/test.sh ui                                         # all UITests (slower)
scripts/ios/test.sh ui SmokeUITests                            # some UITests classes
scripts/ios/test.sh kn core feature-schedule                   # Kotlin iosSimulatorArm64Test of shared modules
scripts/ios/screenshots.sh                                     # review screenshots in light and dark, nothing compared
scripts/ios/test.sh --cleanup                                  # delete this checkout's simulator
```

- `test.sh` first runs `scripts/ios/check-sources.sh` (no Cyrillic in Swift,
  generated string tables fresh) and `scripts/ios/check-tokens.sh` (Swift
  tokens match `itmo-tokens.json`).
- It creates one simulator per checkout (`itmo-<directory>`) from the pinned
  device type and runtime and leaves other simulators alone.
- Results go to `iosApp/build/test-results/`, snapshot failure images to
  `iosApp/build/snapshot-artifacts/`.
- Unit tests never run the real `WKWebsiteDataStore.removeData`: on a CI
  simulator it also deletes Screen Time web history, hangs for minutes and
  stalls the WebKit cookie store after it. A test that builds a core graph
  passes `FakeCoreHost`, which counts the clears; a test that signs out through
  the app's graph or loads the ITMO.ID sign-in page replaces
  `AppPlatform.removeWebsiteData`. UI tests launch a Debug build with
  `-itmoUITests` (`XCUIApplication.itmo`, `itmoSignedOut`), which makes that
  removal complete at once; Release builds ignore it.
- `test.sh ui` waits for SpringBoard's notification server to take in the
  installed apps (two minutes after the simulator boots; an app installed
  earlier never gets the notification dialog nor its scheduled notifications),
  then deletes the app, so each run starts with notifications unasked. Nothing
  else resets that answer, so `FirstRunUITests`, which asserts the dialog,
  sorts before `NotificationTapUITests`, which only answers it.
- Window snapshots (`assertScreenAppearances`) draw with UIKit animations off:
  a presented sheet is already where it ends, however slow the runner.
- Snapshots draw spinners with the still `StillSpinnerStyle`, since the system
  spinner keeps turning with animations off. A sheet's content does not inherit
  the style from its presenter, so a window test of a sheet sets it inside the
  sheet (`SheetScoresSnapshotTests`).
- The last line is `test.sh: ios[-only|-ui|-kn] PASS|FAIL <secs>s <sha>`.

## Baseline profiles

Release and `minifiedSmoke` builds ship the app's baseline profile and startup
profile from `app/src/main/generated/baselineProfiles/` (`baseline-prof.txt`,
`startup-prof.txt`), so ART compiles the tab roots, the shell and startup ahead
of time instead of interpreting them on first visits; the startup profile also
orders the dex files (`dexLayoutOptimization`). `androidx.profileinstaller`
installs the profile on sideloaded APKs as well; there ART compiles against it
at the next background dexopt, while Google Play compiles at install. The
profiles are committed, so
no build needs a device.

`:baselineprofile` drives the demo session with UiAutomator
(`baselineprofile/src/main/java/.../DemoJourneys.kt`): five taps on the logo,
every tab by a tap, the pass sheet, and `Друзья` pushed from `Профиль` and
popped with Back. The journeys find the shell by its test tags, which
`ShellHost` exposes as resource ids. Regenerate the profiles after a change to
the shell, a tab root or startup, on an emulator with API 33 or later:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

It builds `githubNonMinifiedRelease` (pointed at dev, like `minifiedSmoke`),
runs `BaselineProfileGenerator` and writes both files; commit them. Only the
github flavour runs; the files in `main` serve `play` too.

`NavigationBenchmark` measures frame timing (`FrameTimingMetric`) of tab taps
and of a push and pop, in a fresh process, without the app's profile and with
it; the profile run fails when the APK ships no profile. It runs nightly in
`android-nightly.yml`; locally:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedGithubBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Results are in
`baselineprofile/build/outputs/connected_android_test_additional_output/`.
Judge smoothness on these builds, never on a debug build.

## CI

All workflows are in `.github/workflows/` and need no secrets. None is a
required check. A PR runs only what is fast and catches most breakage, so it
finishes in about 10 minutes; the emulator and release suites run nightly on
`master` instead. `scripts/ci-plan.sh` decides from the changed paths what a PR
runs (`scripts/ci-plan.sh --self-test` lists the cases).

| Workflow | When | What |
|---|---|---|
| `android-ci.yml` | every PR into `master`, when an Android input changed | `:app:testGithubDebugUnitTest`, Konsist, the build-logic tests and `:app:assembleGithubDebug`; the shared host tests; the screenshot tests of the changed modules (all of them in 3 shards when `designsystem`, `core`, `testing` or the build changed); `verifyIosKlibs`. `verify.sh checks` runs on every PR, docs-only included |
| `android-ci.yml` | pushes to `master` and PRs labelled `full-ci` that change an Android input; every manual run | `verifyFull` (both flavours, `lintGithubDebug`, `lintPlayDebug`) and every screenshot module in 3 shards; these runs write the build caches PRs read |
| `ios.yml` | PRs that change `iosApp/`, `scripts/ios/`, an iOS source set or the build; pushes to `master` that also change any `shared/` module; on demand, label `full-ci` | `scripts/ios/test.sh --ci` on the `xcode-27` runner with the pinned Xcode and XcodeGen |
| `android-ui.yml` | nightly, on demand, label `full-ci` | `verify.sh ui all` in 6 shards on an emulator without a cutout, `ShellSuite` in 2 shards with a tall cutout |
| `android-ship.yml` | nightly, on demand, label `full-ci` | Release checks without the key: `scripts/ship-check.sh` stages version, unsigned release builds with `check-play-policy.sh`, the upgrade from v2.2 and the shrunk builds on an emulator |
| `android-nightly.yml` | nightly, on demand | The platform list on a Gradle Managed Device, `NavigationBenchmark` on an emulator, the advisory `buildHealth` report |
| `ios-nightly.yml` | nightly, on demand | `test.sh --ci kn` over every shared module and all `UITests` |

- **The full suite on a PR.** Add the label `full-ci` to a risky PR (build
  changes, the shell, navigation, release or CI scripts): the PR then also runs
  `android-ui`, `android-ship`, `ios` and the full `android-ci`, on every push
  until the label is removed. To run one workflow on a branch without the
  label: `gh workflow run android-ui.yml --ref <branch>` (also `android-ship`,
  `android-nightly`, `ios-nightly`, `ios`, `android-ci`).
- **Nightly failures.** A failed nightly of `android-ui`, `android-ship`,
  `android-nightly` (platform tests, benchmarks) or `ios-nightly` opens an
  issue titled `Nightly <workflow> is failing`, or comments on the open one;
  the next passing nightly closes it. Fix it before the next release.
- **A newer push cancels** the older run of the same PR; every `master` push
  keeps its own `android-ci` and `ios` result.

On failure the jobs upload test reports, screenshot diffs or the `.xcresult`
as artifacts.

## Known flaky areas

- **Instrumented order.** One run is one app process, so session state outlives
  a test class. A class that fails in a full run is re-run alone before the
  change is blamed; `ui-report.py` names which run failed.
- **iOS CI memory.** The `ios-check` runner has 7 GB and the Kotlin/Native
  link peaks near it; Gradle runs one worker there. An out-of-memory kill
  looks like a crashed `xcodebuild`.
- **Screenshot tolerance.** A curved edge or text that anti-aliases differently
  between macOS and Linux can exceed the tolerance after a font or Compose
  update; re-record on a Mac and check the diff.
- **Network in tests.** The instrumented runner (`OfflineHostsTestRunner`)
  makes the real ITMO and Backend hosts fail at once; a test that hangs on the
  network is a bug in the test.
