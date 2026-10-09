# QR pass

`feature/qr` shows the My ITMO turnstile pass inside the app, in the
«QR-пропуск» quick-settings tile and behind the `qr_pass` launcher shortcut.
The home-screen widget belongs to the same package and is described in
[Widgets](widgets.md#qr-widget); its settings in
[Settings](../settings.md#qr-widget). The pass comes from MyItmoApi 2.x; there
is no second API client.

The domain, the data, the presentation and the screen live in
`:shared:feature-qr` `commonMain`, packages unchanged
(`dev.alllexey.itmowidgets.feature.qr.*`). `:app` keeps what only Android has:
the Fragment host, the widget (`ui/widget`), the bitmap rendering and its cache
(`ui/rendering`), the worker (`work`), `QrTileService`, `QrTileClick` and
`QrTileController`.

## Screen

`QrCodeFragment` is the overlay destination `qr_pass` (`AppScreen.QR_PASS`). It
opens from the home QR button, the shortcut and the tile; the two system
entries route through `ACTION_OPEN_QR_PASS` to home with the pass above it. The
Fragment hosts the Compose route `QrPassRoute`, which obtains `QrCodeViewModel`
from Koin and draws `QrPassScreen`.

- Top bar «QR-пропуск» with «Назад»; the square code area (content
  description «QR-код пропуска»); the line «Покажите код считывателю на входе
  в корпус»; the button «Обновить QR-код».
- `QrCodeUiState`: `Loading` (progress in the code area), `Content(code,
  refreshing, useDynamicColors)`, `Empty` («QR-код пока недоступен» /
  «Попробуйте обновить пропуск через My ITMO»), `Error` («Не удалось
  загрузить» with the error's text). A code that cannot be drawn shows «Не
  удалось загрузить» with the `Empty` description.
- The code area stays square, at most 300 dp, and the instruction keeps its
  space in every state (transparent, not removed). At large font sizes the
  empty and error texts may extend below the square. The refresh button keeps
  its place, shows its progress inside while `refreshing` and is disabled only
  during `Loading`.
- A refresh that fails while a valid code is shown keeps the code; the event
  `QrCodeEvent.RefreshFailed` shows a snackbar with the error and «Повторить».

`QrCodeViewModel` runs only while the screen is started (`start()` and `stop()`
from `LifecycleStartEffect`).

- `start()` drops an expired code to `Loading` and refreshes without forcing;
  the button and «Повторить» force (`RefreshMode.Force`).
- A fresh view model reads the cache before it publishes `Loading`, so a valid
  cached pass is drawn in the first frame with `refreshing`.
- At the cache deadline (`QrCodeSnapshot.expiresAtMillis`) the screen hides the
  code even while a forced refresh is pending, then refreshes again.
- Time is the injected wall `kotlin.time.Clock`, never the debug academic date.

The screen draws the version 1 code of `QrCodeGenerator` with `QrCodeImage` in
Compose, in the widget palette (`QrColors`): black on white, or with the QR colour setting the
theme's surface and the darker of `onSurface` and `onSurfaceVariant`. The
widget draws its bitmap with `QrToolkit` and `QrColorResolver` in `:app`.

## Data

All in `commonMain`, constructed by Koin (`feature/qr/di/QrModule.kt`, one
`single` per binding):

- `QrCodeRepositoryImpl` answers from the cache while it is valid and goes to
  the network only when it is empty, expired or forced. A failure becomes an
  `AppError` (a failure before any answer is `Network`, a MyItmoApi failure maps
  through `MyItmoException.asAppError()`); the old valid code stays. One
  instance serves the screen, the widget, the worker and sign-out: a second one
  would read its own copy of the cached pass.
- `QrCodeRemoteDataSourceImpl` checks `DemoMode` first and answers `DemoQr.HEX`
  in the demo session, a code no turnstile accepts. Otherwise it calls
  `MyItmoClient.qr.getQrCode()`; an answer without a code gets one forced token
  refresh and one more request. The client's own 401 handling refreshes the
  session once; a server error fails at once and keeps a still valid cached
  code.
- `QrCodeLocalDataSourceImpl` keeps `<epochMillis>|<hex>` in `cache/qr_hex`
  (`AppDirectories.cache`, written through `AtomicTextFile`), valid for 60
  minutes of wall-clock time. A file from v2.0 with only the payload (at least
  six characters) is read as fresh, so an update does not blank a working pass.
  The format is the 2.2 one byte for byte.
- `QrWidgetStateStoreImpl` keeps each widget's reveal state under
  `qr_widget_state_<appWidgetId>` in `app_preferences`, the `QrWidgetState`
  name as the value; an unknown value reads as `HIDDEN`.
- `QrAppearancePreferencesImpl` and `QrTilePreferencesImpl` read and write
  `QrSettingsPreferences` and `DeviceHintPreferences` of `:shared:core`.

The core types come from `:app`'s Hilt graph through `di/bridge/CoreBridge.kt`
(`MyItmoClient`, the `app_preferences` DataStore, `AppDirectories`, the two
preference stores, the wall clock, `DemoMode`, `AppDispatchers`). The other
way, `di/bridge/QrBridge.kt` provides the repository, the widget state store and
the two preference ports to Hilt for the widget, the worker, the tile and the
session effects; Koin keeps the lifetime. `di/QrModule.kt` (Hilt) keeps only
`QrBitmapCache` and `QrCodeGenerator`.

Sign-out: the repository is a `SessionDataCleaner` bound in Koin as
`named("qr")`; `di/bridge/SessionCleanersBridge.kt` adds every Koin cleaner to
Hilt's `Set<SessionDataCleaner>`, beside the Hilt-built `QrBitmapCacheImpl`.
Sign-out deletes `cache/qr_hex` and `cache/bitmap_cache/`.

## Tile

`QrTileService` (`feature/qr/ui`) is the tile labelled «QR-пропуск» with
`ic_tile_qr`, white and tinted by SystemUI. `QrTileController.state()`
initialises the session; the tile is `STATE_ACTIVE` with a signed-in session
and `STATE_INACTIVE` otherwise, never toggleable. `QrTileClick.handle` opens
the pass at once or, on a locked device, through `unlockAndRun`. The intent
carries `ACTION_OPEN_QR_PASS` with `NEW_TASK | CLEAR_TOP | SINGLE_TOP`;
`qrTileLaunchFor` picks `startActivityAndCollapse(PendingIntent)` on Android
14+ and the `Intent` overload below it. Without a session the route waits for
sign-in.

`onTileAdded` and `onTileRemoved` write `qr_tile_added` (`QrTilePreferences`)
in the application scope. The request to add the tile belongs to settings;
see [Home and quick actions](home.md#quick-settings-tile-and-app-shortcuts).

## Shortcut

The static shortcut `qr_pass` («QR-пропуск» / «Открыть QR-пропуск») sends
`ACTION_OPEN_QR_PASS` to `MainActivity`. Running the route reports the
shortcut as used, for the tile too; the in-app button does not.

## iOS

The iOS app ([iOS app](../ios.md)) shows the same pass on the same shared
code; only the system surfaces are its own.

- Pass. The Compose route `QrPassRoute` of `:shared:feature-qr`, hosted by
  `QrPassScreen` (`iosApp/Sources/Features/Qr/`) above home, opens from the
  home QR button, the widget, the Control, the App Shortcut and the quick
  action. Unlike Android it raises the screen to full brightness while it is
  on screen and the app is active, and puts the user's level back when it
  leaves or the app goes to the background. The demo session shows the demo
  pass with no network call.
- QR widget (`dev.alllexey.itmowidgets.widget.qr`, small): Swift only, it
  draws the code from `qr-pass-v1.json`, which the app writes from the shared
  `QrCodeGenerator` on every new valid pass, and never fetches it itself.
  States: signed out («Войдите в приложение», opens the app), spoiler
  («Нажмите, чтобы показать», reveals the code for 30 s for every placed QR
  widget at once), revealed (the demo pass labelled «Демо-режим»), expired
  («Нажмите, чтобы обновить», opens the app, which writes a new pass).
  Degradations: no circle animation on reveal, the code is always black on
  white, no custom spoiler image (v2.4). Settings and the first-run flow show
  the spoiler and dynamic colour switches, but the widget does not follow them
  yet: the spoiler stays on ([degradations](../ios.md#degradations)).
- Control «QR-пропуск» (`dev.alllexey.itmowidgets.control.qr`) in Control
  Center, on the Lock Screen and on the Action button: the counterpart of the
  tile. It opens the app on the pass and cannot draw the code; there is no
  active or inactive state, and without a session the route waits for
  sign-in.
- App Shortcuts «QR-пропуск» and «Сегодня» in Siri, Spotlight and Shortcuts,
  with phrases such as «Открой QR-пропуск в ITMO.Widgets»; quick actions
  «Открыть QR-пропуск» and «Расписание на сегодня» on the app icon, types
  `dev.alllexey.itmowidgets.qr_pass` and `dev.alllexey.itmowidgets.today`.
  Both open what Android's `qr_pass` and `today` shortcuts open.
- Tests: `IntentsTests` (each intent and quick action against a router),
  `StableIdentifiersTests`, `QrWidgetTimelineTests`, `QrPassSnapshotTests`,
  `WidgetSnapshotTests`, `QrPassUITests`.

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

## Tests

- `:shared:feature-qr` `commonTest`: `QrCodeViewModelTest`, `QrPassScreenTest`,
  `QrColorsTest`, `QrCodeRepositoryImplTest`, `QrCodeRemoteDataSourceImplTest`
  (MockEngine through the real client), `QrCodeLocalDataSourceImplTest` (okio
  `FakeFileSystem`: the 2.2 and v2.0 files, the expiry boundary, clear),
  `QrWidgetStateStoreImplTest`, `QrTilePreferencesImplTest`.
- `:shared:feature-qr` host tests: `QrModuleTest` (Koin `verify()`),
  `QrCodeGeneratorGoldenTest`, `QrScreenshotTest` (Roborazzi baselines).
- `:app` JVM: `QrBridgeTest`, `SessionCleanersBridgeTest` (debug),
  `QrDebugFixturesTest` (debug), `QrTileControllerTest`, `QrTileClickTest`,
  `QrWidgetRefreshDecisionTest`, `DemoNetworkGateTest`.
- Instrumented: `QrColorResolverTest`, `QrTileFlowTest` (`cmd statusbar
  add-tile` and `click-tile` on the emulator), `HomeQrVisualTest`,
  `WidgetPreviewTest`, `AppShortcutsTest`, and the `qrCaches` case of
  `UpgradeFrom22Test` (the 2.2 `qr_hex` reads, writes and reads back).
- Konsist: `QrRulesTest` (`feature.qr` never reads `AcademicTimeProvider`).
