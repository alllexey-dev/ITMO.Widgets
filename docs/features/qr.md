# QR pass

Product behaviour: [Как это работает: QR-пропуск](../how-it-works/qr.md).

`feature/qr` shows the My ITMO turnstile pass in the app, the quick-settings tile and the `qr_pass` launcher
shortcut. The widget is in [Widgets](widgets.md#qr-widget), its settings in [Settings](../settings.md#qr-widget). The
pass comes from MyItmoApi; there is no second API client. Domain, data and presentation are in `:shared:feature-qr`
`commonMain`, the Compose sheet in `androidMain`; `:app` keeps the widget, bitmap rendering and cache, the worker and
the tile.

## Pass sheet

- Key `AppRoutes.QrPass`, a content-height sheet over Сегодня. The `Пропуск` button of Сегодня, the tile, the shortcut
  and the widgets open the same sheet: `ACTION_OPEN_QR_PASS` becomes Сегодня with the sheet above it
  (`EntryRouteParser`).
- The sheet shows who the pass belongs to (the session's name and ISU, the group from Backend when connected), the
  code and one status line (updated at, updating, or offline with the time of the shown code). There is no validity
  and no countdown: the 60 minutes below are our cache rule, while ITMO's pass itself resets about a day after its
  first turnstile use, so no expiry the app could show is known.
- `QrPassViewModel` runs only while the sheet is started (`start()`/`stop()`). It shows the last code at once whatever
  its age (`QrCodeRepository.lastQr()`), asks My ITMO when that code is older than `QrCodeSnapshot.FRESH_MILLIS` or
  on `Обновить`, and keeps the last code when the request fails: offline as `QrPassStatus.Offline`, any other failure
  as a `QrPassEvent.RefreshFailed` snackbar.
- Times are the injected wall `kotlin.time.Clock` in the phone's zone, never the debug academic date; Konsist
  `QrRulesTest` forbids `AcademicTimeProvider` in `feature.qr`.

## Data

All in `commonMain`, one Koin `single` per binding (`feature/qr/di/QrModule.kt`):

- `QrCodeRepositoryImpl` answers from the cache while valid and hits the network only when empty, expired or forced;
  failures become `AppError` and the old valid code stays. One instance serves the screen, widget, worker and
  sign-out; a second one would read its own copy of the cache.
- `QrCodeRemoteDataSourceImpl` checks `DemoMode` first and answers `DemoQr.HEX` (no turnstile accepts it). Otherwise
  `MyItmoClient.qr.getQrCode()`; an answer without a code gets one forced token refresh and one retry; a server error
  fails at once.
- `QrCodeLocalDataSourceImpl` keeps `<epochMillis>|<hex>` in `cache/qr_hex` (`AtomicTextFile`), fresh for 60 minutes
  of wall-clock time (`QrCodeSnapshot.FRESH_MILLIS`, a cache rule, not the pass's validity); the widgets read only a
  fresh code, the sheet also an older one. A payload-only file (at least six characters) reads as fresh.
- `QrWidgetStateStoreImpl` keeps `qr_widget_state_<appWidgetId>` in `app_preferences`; an unknown value reads as
  `HIDDEN`.
- Persisted and kept byte-compatible: `cache/qr_hex`, the `qr_widget_state_<appWidgetId>` keys and the enum names of
  `QrWidgetState` and `QrAnimationType`; the service, shortcut, receiver, worker and action names are checked by
  `StableIdentifiersTest`.

Sign-out: the repository is a `SessionDataCleaner` `named("qr")`, beside `QrBitmapCacheImpl` `named("qr-bitmaps")`;
it deletes `cache/qr_hex` and `cache/bitmap_cache/`.

## Tile and shortcut

The tile, its `qr_tile_added` hint and the `qr_pass` shortcut are described in
[home](home.md#quick-settings-tile-and-app-shortcuts); the add-tile request belongs to settings.

## iOS

- `QrPassSheet` (`iosApp/Sources/Features/Qr/`) draws `QrPassViewModel` in SwiftUI with the code from
  `QrCodeGenerator`; the `QR-пропуск` tab bar accessory, the Control, the shortcuts and `itmowidgets://route/qr_pass`
  open it. Unlike Android it raises brightness while shown and restores it on leave or background.
- The QR widget and the Control `dev.alllexey.itmowidgets.control.qr` are Swift only: the widget draws from
  `qr-pass-v1.json`, which the app writes on every new valid pass, and never fetches itself
  ([degradations](../ios.md#degradations)).
