# Update offer

`feature/update` compares the installed version with Backend's
`GET /api/app/version-info` (`minVersion`, `latestVersion`, `note`) once per
process after the session reports a signed-in user. Like every Backend call it
is gated on the opt-in. A failed check offers nothing; the prompt is advisory and
never opens a screen to show an error.

Versions are ordered by `AppVersionName`, so `2.10` follows `2.9` and a
`-SNAPSHOT` build still recognises its own release. `PendingAppUpdate` applies
the policy: at most one offer a day, none for a version the user skipped, and no
limit once the installed build is below `minVersion`, in which case
`Пропустить версию` disappears. The offer records when it was shown, so leaving
by Back postpones it like `Напомнить позже`. The demo session never checks.

## Screen

The screen is Compose in `:shared:feature-account`: `AppUpdateScreen(state,
onUpdate, onLater, onClose, onSkip)` is stateless, and `AppUpdateRoute` obtains
`AppUpdateViewModel` from Koin. `AppUpdateFragment` (`app_update`, arguments
`installed_version`, `latest_version`, `note`, `unsupported`) keeps its name and
hosts the route through `itmoComposeView`; the ViewModel reads the check result
from the Fragment's arguments, so the screen has no loading or error state.

- A close button (48 dp minimum at any font scale), then one scrolling column:
  the download icon, the title (`Вышла новая версия`, or `Эта версия устарела`
  for an unsupported build), the versions line `2.1 → 2.2` in `primary`, the
  reason, then Backend's note after a blank line when there is one.
- Actions: `Скачать` (filled), `Напомнить позже` (tonal) and `Пропустить
  версию` (text, hidden for an unsupported build). `Напомнить позже` and the
  close button close the screen; `Пропустить версию` stores the skipped version
  and closes the screen once the write finished.
- `Скачать` hands `unsupported` to the host's `UpdateAction`; when nothing could
  be opened the route shows the snackbar below.

The screen holds no Android type, so iOS can drive the same ViewModel from
SwiftUI.

`R.string.app_version` is generated from `versionName`, so the settings display
and version-dependent storage never drift from the APK metadata.

## «Обновить» per distribution

The offer itself is the same in both [variants](../architecture.md#distribution-variants);
the button calls `UpdateAction.start(activity, unsupported, onFailed)`, bound
per variant. `onFailed` shows `Не удалось открыть страницу с релизом`.

- `github`: `GithubUpdateAction` opens `BuildConfig.DOWNLOAD_URL`
  (`https://github.com/alllexey-dev/ITMO.Widgets/releases/latest`) through
  `ReleasePageOpener`; an unsupported build goes to the same page.
- `play`: `PlayUpdateAction` asks Google Play In-App Updates for the update.
  A supported build starts a flexible update (download in the background), an
  unsupported one an immediate update, which also resumes an immediate update
  left halfway. When Play offers no update of that type (not rolled out yet,
  installed from GitHub, Play failed) it opens the app's card:
  `market://details?id=…`, then `BuildConfig.DOWNLOAD_URL` in the browser.

`MainActivity` starts `InstallStateWatcher` in `onResume` and stops it in
`onPause`. When Play reports a flexible update as downloaded, also one that
finished while the app was away, it shows the indefinite Snackbar
`Обновление загружено` with `Перезапустить` (`completeUpdate()`, Play installs
and restarts the app), anchored above the bottom bar. The `github` watcher
never reports.

Tests: `AppUpdateScreenTest` (JVM host test in `:shared:feature-account`:
texts, the skip hidden for an unsupported build, callbacks, the skip stored
before the route closes, the open-failed snackbar, nothing clipped at font 1.3
in 320 dp), the `AppUpdateScreen_{supported,unsupported,long-note}` goldens of
`AccountScreenshotTest` in four appearances, `GithubUpdateActionTest` (JVM,
`app/src/testGithub`) and `PlayUpdateActionTest` (instrumented,
`app/src/androidTestPlay`: Play's `FakeAppUpdateManager` needs real
`PendingIntent`s).

```bash
scripts/verify.sh run -- :shared:feature-account:testAndroidHostTest --tests 'dev.alllexey.itmowidgets.feature.update.*'
scripts/verify.sh shots feature-account
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.update.ui.PlayUpdateActionTest
```
