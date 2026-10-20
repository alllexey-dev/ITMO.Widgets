# ITMO.Widgets

Android and iOS app for ITMO University students: schedule, QR pass, sport, record book, friends, widgets. One
Kotlin Multiplatform codebase: shared modules in `shared/` (domain, data, presentation, Koin, and the Android Compose
screens); `app/` is the Android app (Navigation 3 shell, RemoteViews widgets, workers); `iosApp/` is a native SwiftUI
app over the same view models and draws no Compose (ADR 0031, 0035). Official MyITMO and BARS data come through
MyItmoApi; social data through the ITMO.Widgets Backend (`shared/backend-client`).

## Docs

Start at [docs/README.md](docs/README.md): architecture, design, settings, per-feature docs, decisions (ADRs),
iOS, releases. Docs describe the current state; history goes to `CHANGELOG.md` (edit its `Unreleased` section).

## Build and test

```bash
./gradlew verifyQuick             # unit and host tests of every module, Konsist, lintGithubDebug, debug APKs (JDK 21)
./gradlew verifyFull              # plus lintPlayDebug
./gradlew verifyIosKlibs          # iOS klibs compile (no Xcode needed)
scripts/verify.sh shots all       # JVM screenshot tests; `--record` re-records baselines
scripts/verify.sh checks          # docs link checker
scripts/ios/test.sh               # iOS app build and tests (Mac with Xcode)
```

MyItmoApi comes from Maven Central; set `MYITMOAPI_DIR` to a my-itmo-api checkout to build against local changes.
A PR runs the fast part of this for what it touches; emulator tests (`android-ui`), release checks (`android-ship`) and lint run nightly or on `master`, and on a PR labelled `full-ci` ([docs/testing.md](docs/testing.md#ci)).

## Rules

- Academic time comes from `AcademicTimeProvider` or an injected `Clock`; no direct `now()` in feature code.
- Dependencies inside a feature go `ui -> presentation -> domain <- data`; features never import each other;
  `commonMain` has no `android.*`, `java.*` or `R`. The Konsist suite (`konsist/`) enforces these.
- Every Backend call passes `BackendGate.mayCallBackend()` (the custom-services opt-in) in the repository layer.
- Every class holding a network client checks `DemoMode` first: demo and fixture data never reach Backend.
- Backend is authoritative for social data, privacy and authorization; the app never enforces privacy for it.
- MyItmoApi is the only client for MyITMO and BARS: extend it instead of adding a second one.
- User-visible text is Russian and lives in string resources (ADR 0028).
- Never commit secrets: `local.properties`, `keystore.properties`, `*.jks`, `.env*`, `.refresh-token`, tokens,
  database dumps or user data. `app/google-services.json` is Firebase client config and stays tracked.
- Never mutate production data for testing; debug builds talk to `https://dev.widgets.alllexey.dev`.
