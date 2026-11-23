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
- `AppUpdateAvailability` has the last word: the place the build is updated from must have the
  update ([Google Play](#google-play-decides-for-the-play-variant)). An update it does not have yet
  is not offered and does not start the interval.
- `R.string.app_version` is generated from `versionName`, so the displayed version and
  version-dependent storage never drift from the APK metadata.

## Screen

- `ShellHost` runs the check (`AppUpdateGateViewModel`) outside the demo and opens the offer only
  while the activity is resumed.
- Key `UpdateRoutes.AppUpdate` carries the result as `AppUpdateArgs`, so the screen has no loading or
  error state.
- `Пропустить версию` closes the screen only after the skipped version is stored.

## Per distribution

Each [variant](../architecture.md#distribution-variants) binds four things in its
`UpdateActionModule.kt`:

| | `github` | `play` |
|---|---|---|
| `AppUpdateAvailability` | `Always`: the offer follows Backend | `PlayUpdateAvailability`: Google Play decides |
| `AppUpdateCopy` | `Download`: `Скачать`, Backend's note under the reason | `GooglePlay`: `Обновить`, the reason names Google Play, no note |
| `UpdateAction` | `GithubUpdateAction` opens `BuildConfig.DOWNLOAD_URL` (the latest GitHub release) through `ReleasePageOpener` | `PlayUpdateAction` starts Play In-App Updates |
| `InstallStateWatcher` | never reports | reports a downloaded flexible update |

`UpdateAction.start(activity, unsupported, onFailed)`; `onFailed` shows the copy's snackbar.
`ShellHost` runs `InstallStateWatcher` between `onResume` and `onPause`; a downloaded flexible update
shows an indefinite snackbar whose action calls `completeUpdate()`.

## Google Play decides for the `play` variant

Backend keeps one `latestVersion` for both Android variants, and it moves with a GitHub release.
Google Play gets that build later (review, a staged rollout) or never (a beta outside the production
track), so in `play` Backend only says that a newer version exists and whether the installed one is
below `minVersion`; whether to open the offer is Play's answer for this device.

- `PlayUpdateAvailability` reads `AppUpdateManager.appUpdateInfo` and offers only when Play can start
  the update the build needs (`startableUpdateType`): `UPDATE_AVAILABLE` with the flexible type
  allowed for a supported build, the immediate type for one below `minVersion`. An immediate update
  left halfway (`DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`) is offered again; a flexible one that is
  downloading or downloaded is not, the restart snackbar covers it.
- A build below `minVersion` waits the same way: until Play has the update there is no screen, since a
  forced offer that cannot update is a dead end. `app.minimum` rises only after the release is in
  Play production ([ADR 0030](../adr/0030-release-lines-and-data-continuity.md)), so this is the
  time of a rollout or a device Play does not update.
- A failed Play request offers nothing and is written to the error journal. An install Play does not
  own (a sideloaded `play` APK, a debug build, no Play Store) therefore never gets an offer.
- Play is asked after the reminder (a skipped or recently offered release costs no request) and once
  per process, so the offer comes with the first start after Play's own data has the release; Play
  refreshes it in the background, usually within a day of the rollout reaching the device.
- The screen shows Backend's `latestVersion`; Play may install a lower one when `app.latest` is ahead
  of the production track.
- The button asks Play again with the same rule (`PlayUpdateAction`). If the update is gone by then,
  it opens `market://details?id=...`, then `BuildConfig.DOWNLOAD_URL` (the Play card). The `play`
  bundle has no GitHub link (`scripts/check-play-policy.sh`), and Backend's note stays off the screen
  because one text serves both variants and may name another way to install.

### Checking the real flow

Play In-App Updates answer only for an install that Play owns, so the flow is checked on a phone
before a production release. Internal app sharing does not consume version codes, and the
`minifiedSmoke` build is the release build pointed at the development Backend, where the advertised
versions may be moved for a test:

1. Build `./gradlew :app:bundlePlayMinifiedSmoke` twice, the second time with `versionCode` raised by
   one in `app/build.gradle.kts` (not committed), and upload both to internal app sharing: links A
   (lower) and B (higher).
2. On a phone whose Play account may open the links (internal app sharing switched on in the Play
   Store settings), install A, sign in and turn `Сервер ITMO.Widgets` on.
3. On the development Backend set `app.latest` above the build's `versionName`. Start the app:
   nothing opens, Play has no update for this install.
4. Open link B and leave Play's page without installing. Start the app again (a new process): the
   offer opens with `Обновить` and without the note; `Пропустить версию` and `Напомнить позже` work
   as in `github`.
5. `Обновить` shows Play's confirmation; the download runs in the background, then the
   `Обновление загружено` / `Перезапустить` snackbar installs B.
6. Immediate flow: install A again, raise the development `app.minimum` above its `versionName`.
   Before link B is opened nothing shows; after it the offer has no `Пропустить версию` and
   `Обновить` opens Play's full-screen update. Leave it halfway and start the app again: the offer
   comes back and the update resumes.
7. Put the development `app.latest` and `app.minimum` back.

`FakeAppUpdateManager` covers the same states without a phone, except the resumed immediate update:
`PlayUpdateAvailabilityTest` (JVM, `testPlayDebugUnitTest`) and `PlayUpdateActionTest` (device,
`androidTestPlay`).

## iOS

- Same check and policy; `updateIosModule` binds `DevicePlatform.IOS` and
  `CFBundleShortVersionString` (`version-info?platform=IOS`). The offer is a SwiftUI sheet over
  `AppUpdateViewModel` ([iOS app](../ios.md#data-sharing)).
- The only channel is `https://apps.apple.com/app/id<APP_STORE_ID>` (`AppStoreListing`); no
  downloaded-update snackbar. The App Store is not asked whether it has the release
  (`AppUpdateAvailability.Always`): the offer follows Backend's iOS `latestVersion`.
- `APP_STORE_ID` (`iosApp/Config/Base.xcconfig`) is empty until the App Store record exists; while
  empty the app never checks or offers (`AppUpdateOffer`).
- Backend: `BACKEND_BASE_URL` in `Base.xcconfig`, dev for Debug and production for Release.
