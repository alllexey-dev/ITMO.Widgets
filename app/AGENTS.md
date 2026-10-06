# app

## Owns
- `src/main/java/.../app/`: `ItmoWidgetsApplication`, `MainActivity` and the app-level coordinators (navigation,
  notifier, widget refresh, `MainRouteQueue`, shortcuts, boot receiver).
- `core/`, `feature/<x>/`: what is still Android-only, or not yet moved into its shared module (Activities and
  WebView screens, RemoteViews widgets, workers and schedulers, FCM, `QrTileService`, the Keystore cipher, calendar
  sync, the Play code scanner, update actions); the `debug` feature stays here for good.
- `di/`: Hilt modules, one per feature or concern, and `di/bridge/` (Koin to Hilt) until Hilt leaves (ADR 0019).
- `src/main/res/`: View resources, navigation graphs and the per-module `strings_<file>.xml` still used by Views.
- `src/github/`, `src/play/`: distribution-only code; `src/debug/`: fixtures, debug hosts and debug tools.
- `src/test*/`: JVM tests, `StableIdentifiersTest`; `src/androidTest*/`: device tests, `platform-tests.txt`.
- `screenshots/`: XML reference captures of the View screens that ports compare against.

## Depends on
- Every shared module; `:shared:testing` in tests only. Hilt with KSP, Koin, WorkManager, Firebase messaging, MDC.
- A shared module never depends back on `:app`; code that a second platform needs moves out, not in.

## Verify
`scripts/verify.sh quick`; `scripts/verify.sh shots app` for XML references;
`ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui <Class>` for an Android-only surface.

## Hot files
- `build.gradle.kts`: lane L04 (`versionCode`, `versionName`: L01).
- `ItmoWidgetsApplication.kt`, `AndroidManifest.xml`, `styles.xml`, `themes.xml`: lane L05.
- `MainActivity.kt`, `core/navigation/`, debug hosts: lane L06; `di/`: lane L07; navigation graphs: lane L17.
- `strings_<file>.xml`: the lane `scripts/strings-owners.py --where <id or path>` names.
- `StableIdentifiersTest.kt`: the list only grows; changing an entry needs an ADR and a dual read of the old value.

## Docs
- [Architecture](../docs/architecture.md): [modules](../docs/architecture.md#modules),
  [stable identifiers](../docs/architecture.md#stable-identifiers),
  [testing conventions](../docs/architecture.md#testing-conventions); [screens](../docs/features/screens.md).
- [Ownership](../docs/process/ownership.md) (hand-over points); ADRs
  [0019](../docs/decisions/0019-koin-per-lane.md) (Koin) and [0027](../docs/decisions/0027-remoteviews-widgets.md)
  (RemoteViews widgets).
