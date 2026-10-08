# shared/feature-recordbook

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.recordbook.*`: the recordbook, the subject page, mark
  tracking and sheet scores. `data/` (`bars/`, `marks/`, `sheets/`, `home/`, `demo/`), `domain/` (`marks/`,
  `model/`, `sheets/`), `presentation/` (with `sheets/`), `ui/` (`subject/`, `sheets/`, `home/`, previews).
- `di/RecordbookKoinModule.kt`: the Koin module `recordbookModule`; `iosMain`: `recordbookIosModule(host)` with the
  `BarsWebHost` port and the iOS actuals; `androidMain`: the file system and the public sheets' OkHttp engine.
- `strings_recordbook.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `androidHostTest`: BARS client, marks, sheets, ViewModels, screens, `RecordbookModuleTest`,
  `RecordbookScreenshotTest`; fixtures in `resources/myitmo/recordbook/` and `src/androidHostTest/resources/sheets/`.
- Not here: `BarsLoginActivity`, the recordbook and subject Fragments, the sheet hosts, `MarksWorker`,
  `BarsCookieProbeWorker`, `AndroidMarksNotifier` and the WebView cookie bridge stay in `app/` under
  `feature/recordbook/`; `di/bridge/RecordbookBridge.kt` bridges Koin and Hilt.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin, the KMP lifecycle, ksoup (the
  sheets' HTML view); Ktor OkHttp on Android only; OkHttp MockWebServer in host tests only.
- MyItmoApi 2.x for MyITMO and BARS; public sheets through Ktor. No Backend call.

## Verify
`scripts/verify.sh quick`, `scripts/verify.sh shots feature-recordbook`; `klibs feature-recordbook` after `iosMain`.

## Hot files
- Every file here: lane L12; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L12 writes, L04 reviews.
- `strings_recordbook.xml`: L12; keys are never renamed.
- Stable identifiers held here: `filesDir/marks/state.json`, `filesDir/sheet_scores/state.json`, the BARS token
  file `bars_tokens.enc` in `noBackupFilesDir`, the `recordbook_bars` and `subject_bindings` keys of
  `app_preferences`. Workers, work names, the `marks` channel and the recordbook actions are app-side entries of
  `StableIdentifiersTest`.
- `RecordbookRulesTest` (Konsist, lane L06): WebView and CookieManager only in the app's recordbook code; no legacy
  MyItmoApi type here; the app's recordbook screens are hosts.

## Docs
- [Recordbook](../../docs/features/recordbook.md), [subject page](../../docs/features/subject-page.md),
  [mark tracking](../../docs/features/marks-tracking.md), [sheet scores](../../docs/features/sheet-scores.md).
- ADRs [0012](../../docs/decisions/0012-bars-background-renewal.md) (BARS renewal),
  [0014](../../docs/decisions/0014-sheet-scores-on-device.md) (sheet scores) and
  [0025](../../docs/decisions/0025-myitmoapi-2-kmp.md) (MyItmoApi 2.x).
