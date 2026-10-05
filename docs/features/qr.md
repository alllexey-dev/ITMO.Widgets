# QR pass

`feature/qr` shows the My ITMO turnstile pass inside the app, in the
«QR-пропуск» quick-settings tile and behind the `qr_pass` launcher shortcut.
The home-screen widget belongs to the same package and is described in
[Widgets](widgets.md#qr-widget); its settings in
[Settings](../settings.md#qr-widget). The code comes from MyItmoApi; there is no
second API client.

## Screen

`QrCodeFragment` is the overlay destination `qr_pass` (`AppScreen.QR_PASS`). It
opens from the home QR button, the shortcut and the tile; the two system
entries route through `ACTION_OPEN_QR_PASS` to home with the pass above it.

- Toolbar «QR-пропуск» with «Назад»; the square code area (content
  description «QR-код пропуска»); the line «Покажите код считывателю на входе
  в корпус»; the button «Обновить QR-код».
- `QrCodeUiState`: `Loading` (progress in the code area), `Content(code,
  refreshing)`, `Empty` («QR-код пока недоступен» / «Попробуйте обновить
  пропуск через My ITMO»), `Error` («Не удалось загрузить» with the error's
  message). A code that cannot be drawn shows «Не удалось загрузить» with the
  `Empty` description.
- The code area stays square and the instruction keeps its space in every
  state (`INVISIBLE`, not `GONE`). The refresh button keeps its place, shows
  its progress inside while `refreshing` and is disabled during `Loading` and
  `refreshing`.
- A refresh that fails while a valid code is shown keeps the code and shows a
  Snackbar with the error and «Повторить».

`QrCodeViewModel` runs only while the screen is started (`start()` in
`onStart`, `stop()` in `onStop` cancels its jobs).

- `start()` drops an expired code to `Loading` and refreshes without forcing;
  the button forces.
- A fresh view model reads the cache before it publishes `Loading`, so a valid
  cached pass is drawn in the first frame with `refreshing`.
- At the cache deadline (`QrCodeSnapshot.expiresAtMillis`) the screen hides the
  code even while a forced refresh is pending, then refreshes again.
- Time is the injected `@WallClock` `Clock`, never the debug academic date.

The bitmap is drawn by `QrToolkit.generateQrBitmap` on a background
dispatcher with the widget palette (`QrColorResolver`): black on white, or with
the QR widget's dynamic colours the theme's surface and the darker of
`onSurface` and `onSurfaceVariant`; a translucent result falls back to black on
white.

## Data

- `QrCodeRepositoryImpl` answers from the cache while it is valid and goes to
  the network only when it is empty, expired or forced. A failure becomes an
  `AppError`; the old valid code stays.
- `QrCodeRemoteDataSourceImpl` calls MyItmoApi `getQrCode`; an answer without
  a code forces a token refresh and asks once more. The demo session answers
  `DemoQr.HEX`, a code no turnstile accepts.
- `QrCodeLocalDataSourceImpl` keeps `timestamp|hex` in `cacheDir/qr_hex`, valid
  for one hour of wall-clock time. A file from v2.0 with only the payload (at
  least six characters) is read as fresh, so an update does not blank a working
  pass.
- The repository is a `SessionDataCleaner`: sign-out deletes the cache.

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

## Tests

- JVM: `QrCodeViewModelTest`, `QrCodeRepositoryImplTest`,
  `QrTileControllerTest`, `QrTileClickTest`, `QrTilePreferencesImplTest`.
- Instrumented: `QrColorResolverTest`, `QrTileFlowTest` (`cmd statusbar
  add-tile` and `click-tile` on the emulator), `HomeQrVisualTest`.
