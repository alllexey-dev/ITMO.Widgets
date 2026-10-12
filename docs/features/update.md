# Update offer

Product behaviour: [Как это работает: аккаунт](../how-it-works/account.md).

`feature/update` compares the installed version with Backend's `GET /api/app/version-info`
(`minVersion`, `latestVersion`, `note`).

- Once per process, after the session reports a signed-in user; gated on the custom-services opt-in
  like every Backend call; the demo session never checks.
- A failed check offers nothing; the prompt never opens a screen to show an error.
- Versions are ordered by `AppVersionName` (`2.10` after `2.9`; a `-SNAPSHOT` build recognises its
  own release).
- `PendingAppUpdate`: at most one offer per `REMINDER_INTERVAL` (1 day), none for a skipped version,
  no limit once the installed build is below `minVersion` (then the skip action is hidden). Showing
  the offer records the time, so leaving by Back postpones like `Напомнить позже`.
- `R.string.app_version` is generated from `versionName`, so the displayed version and
  version-dependent storage never drift from the APK metadata.

## Screen

- `ShellHost` runs the check (`AppUpdateGateViewModel`) outside the demo and opens the offer only
  while the activity is resumed.
- Key `UpdateRoutes.AppUpdate` carries the result as `AppUpdateArgs`, so the screen has no loading or
  error state. `AccountEntries.kt` registers `AppUpdateRoute` (`AppUpdateViewModel`, `UpdateModule.kt`)
  over the stateless `AppUpdateScreen` in `:shared:feature-account` (no Android types).
- `Пропустить версию` closes the screen only after the skipped version is stored.

## Download action per distribution

`UpdateAction.start(activity, unsupported, onFailed)` is bound per
[variant](../architecture.md#distribution-variants); `onFailed` shows a snackbar.

- `github`: `GithubUpdateAction` opens `BuildConfig.DOWNLOAD_URL` (the latest GitHub release) through
  `ReleasePageOpener`.
- `play`: `PlayUpdateAction` uses Play In-App Updates: flexible for a supported build, immediate for
  an unsupported one (also resumes an immediate update left halfway). Without an update of that type
  it opens `market://details?id=...`, then `BuildConfig.DOWNLOAD_URL`.
- `ShellHost` runs `InstallStateWatcher` between `onResume` and `onPause`; a downloaded flexible
  update shows an indefinite snackbar whose action calls `completeUpdate()`. The `github` watcher
  never reports.

Tests: `AppUpdateScreenTest` (`:shared:feature-account`), `GithubUpdateActionTest`
(`app/src/testGithub`), `PlayUpdateActionTest` (instrumented, `app/src/androidTestPlay`: Play's
`FakeAppUpdateManager` needs real `PendingIntent`s).

## iOS

- Same check and policy; `updateIosModule` binds `DevicePlatform.IOS` and
  `CFBundleShortVersionString` (`version-info?platform=IOS`). The offer is a SwiftUI sheet over
  `AppUpdateViewModel` ([iOS app](../ios.md#data-sharing)).
- The only channel is `https://apps.apple.com/app/id<APP_STORE_ID>` (`AppStoreListing`); no
  downloaded-update snackbar.
- `APP_STORE_ID` (`iosApp/Config/Base.xcconfig`) is empty until the App Store record exists; while
  empty the app never checks or offers (`AppUpdateOffer`).
- Backend: `BACKEND_BASE_URL` in `Base.xcconfig`, dev for Debug and production for Release.
