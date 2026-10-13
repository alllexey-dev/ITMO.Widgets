# QR pass

Product behaviour: [Как это работает: QR-пропуск](../how-it-works/qr.md).

`feature/qr` shows the My ITMO turnstile pass in the app, the quick-settings
tile and the `qr_pass` launcher shortcut. The widget is in
[Widgets](widgets.md#qr-widget), its settings in
[Settings](../settings.md#qr-widget). The pass comes from MyItmoApi; there is
no second API client.

Domain, data, presentation and screen live in `:shared:feature-qr`
`commonMain`; `QrModule.kt` registers `QrCodeViewModel`. `:app` keeps the
widget (`ui/widget`), bitmap rendering and cache (`ui/rendering`), the worker
(`work`), `QrTileService`, `QrTileClick` and `QrTileController`.

## Screen

- Key `AppRoutes.QrPass` in `HomeEntries.kt`, route `QrPassRoute`, screen
  `QrPassScreen`. The tile and the shortcut send `ACTION_OPEN_QR_PASS`, which
  `EntryRouteParser` turns into the home tab with the pass above it.
- `QrCodeViewModel` runs only while the screen is started (`start()`/`stop()`
  from `LifecycleStartEffect`). A fresh one reads the cache before publishing
  `Loading`, so a valid cached pass draws in the first frame.
- At `QrCodeSnapshot.expiresAtMillis` the code is hidden even while a forced
  refresh is pending. A failed refresh over a valid code keeps the code
  (`QrCodeEvent.RefreshFailed` snackbar).
- Time is the injected wall `kotlin.time.Clock`, never the debug academic
  date; Konsist `QrRulesTest` forbids `AcademicTimeProvider` in `feature.qr`.

## Data

All in `commonMain`, one Koin `single` per binding
(`feature/qr/di/QrModule.kt`):

- `QrCodeRepositoryImpl` answers from the cache while valid and hits the
  network only when empty, expired or forced; failures become `AppError` and
  the old valid code stays. One instance serves the screen, widget, worker and
  sign-out; a second one would read its own copy of the cache.
- `QrCodeRemoteDataSourceImpl` checks `DemoMode` first and answers
  `DemoQr.HEX` (no turnstile accepts it). Otherwise
  `MyItmoClient.qr.getQrCode()`; an answer without a code gets one forced token
  refresh and one retry; a server error fails at once.
- `QrCodeLocalDataSourceImpl` keeps `<epochMillis>|<hex>` in `cache/qr_hex`
  (`AtomicTextFile`), valid for 60 minutes of wall-clock time. A payload-only
  file (at least six characters) reads as fresh.
- `QrWidgetStateStoreImpl` keeps `qr_widget_state_<appWidgetId>` in
  `app_preferences`; an unknown value reads as `HIDDEN`.
- `QrAppearancePreferencesImpl` and `QrTilePreferencesImpl` wrap
  `QrSettingsPreferences` and `DeviceHintPreferences` of `:shared:core`.
- `:app`'s `di/QrModule.kt` holds Android rendering (`QrBitmapCache`,
  `QrCodeGenerator`, the toolkit).

Sign-out: the repository is a `SessionDataCleaner` `named("qr")`, beside
`QrBitmapCacheImpl` `named("qr-bitmaps")` (`di/SessionModule.kt`); it deletes
`cache/qr_hex` and `cache/bitmap_cache/`.

## Tile and shortcut

- `QrTileService` (`feature/qr/ui`): `STATE_ACTIVE` with a signed-in session,
  `STATE_INACTIVE` otherwise, never toggleable. `QrTileClick.handle` opens the
  pass, through `unlockAndRun` on a locked device; `qrTileLaunchFor` uses
  `startActivityAndCollapse(PendingIntent)` on Android 14+.
- `onTileAdded`/`onTileRemoved` write `qr_tile_added`; the add-tile request
  belongs to settings ([home](home.md#quick-settings-tile-and-app-shortcuts)).
- The static shortcut `qr_pass` sends `ACTION_OPEN_QR_PASS`; running the route
  reports the shortcut as used (tile too, in-app button not).

## iOS

- The pass is a sheet over today (0.82 of the screen, draggable to full):
  `QrPassScreen` (`iosApp/Sources/Features/Qr/`) hosts the shared
  `QrPassRoute` in a SwiftUI sheet; the `QR-пропуск` tab bar accessory over
  today, the Control, the shortcuts and `itmowidgets://route/qr_pass` open it.
  Unlike Android it raises brightness while shown and restores it on leave or
  background.
- QR widget (`dev.alllexey.itmowidgets.widget.qr`) is Swift only: it draws
  from `qr-pass-v1.json`, which the app writes from `QrCodeGenerator` on every
  new valid pass, and never fetches itself
  ([degradations](../ios.md#degradations)).
- Control `dev.alllexey.itmowidgets.control.qr` is the tile counterpart: opens
  the pass, no state. App Shortcuts and quick actions
  `dev.alllexey.itmowidgets.qr_pass` / `dev.alllexey.itmowidgets.today` mirror
  Android's shortcuts.

## Stable identifiers

Quoted from `StableIdentifiersTest`:

- service `dev.alllexey.itmowidgets.feature.qr.ui.QrTileService`;
- shortcut `qr_pass` with the action `dev.alllexey.itmowidgets.action.OPEN_QR_PASS`
  and the target `dev.alllexey.itmowidgets.app.MainActivity`;
- cache directories `qr_hex` and `bitmap_cache`, the file
  `filesDir/qr_custom_spoiler/custom_spoiler.png`;
- for the widget: receiver
  `dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider`, worker
  `dev.alllexey.itmowidgets.feature.qr.work.QrWidgetUpdateWorker`, unique work
  `dev.alllexey.itmowidgets.QrWidgetUpdate`, actions
  `dev.alllexey.itmowidgets.action.QR_WIDGET_CLICK` and
  `dev.alllexey.itmowidgets.action.QR_WIDGET_AUTO_HIDE`.

Persisted and kept byte-compatible: `cache/qr_hex`, the
`qr_widget_state_<appWidgetId>` keys and the enum names of `QrWidgetState` and
`QrAnimationType`.
