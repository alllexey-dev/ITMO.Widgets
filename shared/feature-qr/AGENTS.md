# shared/feature-qr

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.qr.*`: the QR pass. `data/` (`remote/`, `local/`,
  `repository/`, `demo/` with `DemoQr`), `domain/` (repository, widget state, tile and appearance preferences),
  `presentation/` (`QrCodeViewModel`, `QrCodeUiState`), `ui/` (`QrPassRoute`, `QrPassScreen`, previews) and
  `ui/rendering/` (`QrCodeGenerator`, `QrCodeImage`, `QrColors`).
- `di/`: the Koin module `qrModule`; `iosMain`: `qrIosModule` and `widget/QrPassSnapshotWriter` (the App Group
  snapshot the iOS widget reads).
- `strings_qr.xml` in `composeResources/values/`, exported to `:app` as Android resources (`itmowidgetsStrings`).
- `commonTest`: ViewModel, repository, data source and screen tests; `androidHostTest`: `QrModuleTest`,
  `QrScreenshotTest`, `QrCodeGeneratorGoldenTest` over the matrices in `src/androidHostTest/resources/qr/`.
- Not here: `QrTileService`, the widget and its worker stay in `app/` under `feature/qr/`, the custom spoiler under
  `core/qr/`; `di/bridge/QrBridge.kt` bridges Koin and Hilt.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin (core, ViewModel, compose
  ViewModel) and the KMP lifecycle.
- The pass comes from MyItmoApi 2.x through core's client factory; no Backend call.

## Verify
`scripts/verify.sh quick`, then `scripts/verify.sh shots feature-qr`; `scripts/verify.sh klibs feature-qr` after an
`iosMain` change.

## Hot files
- Every file here: lane L09; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L09 writes, L04 reviews.
- `strings_qr.xml`: L09; keys are never renamed.
- Stable identifiers held here, kept byte-compatible: `cacheDir/qr_hex`, the `qr_widget_state_<appWidgetId>` keys
  of `app_preferences`, the enum names of `QrWidgetState` and `QrAnimationType`. The tile, widget, worker,
  `qr_pass` shortcut and actions are app-side entries of `StableIdentifiersTest`.
- `QrRulesTest` (Konsist, lane L06): the QR pass never reads academic time.

## Docs
- [QR pass](../../docs/features/qr.md), its [stable identifiers](../../docs/features/qr.md#stable-identifiers);
  [QR widget](../../docs/features/widgets.md#qr-widget).
- [Screen in a shared module](../../docs/recipes/shared-module-screen.md); ADRs
  [0017](../../docs/decisions/0017-cmp-ui-in-common-main.md) and [0019](../../docs/decisions/0019-koin-per-lane.md).
