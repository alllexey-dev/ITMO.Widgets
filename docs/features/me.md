# Profile tab

Product behaviour: [Как это работает: аккаунт](../how-it-works/account.md).

`feature/me` is the last bottom-bar tab: own identity, entries to the social
screens, application rows and sign-out. It owns no data; `MeViewModel`
(`:shared:feature-account`, Koin) combines the session, the custom-services
opt-in and `SocialRepository`. Social screens: [Social](social.md); web sign-in:
[Web sign-in](web-login.md); settings: [Settings](../settings.md).

## Screen

- `feature/me/ui/MeScreen.kt` is the stateless `MeScreen(state, showDebugTools,
  actions)`; `feature/me/ui/MeRoute.kt` takes `MeViewModel`
  (`feature/me/di/MeModule.kt`) and wires sign-out and the link snackbar.
- Android root: `MeTabRoot` (`shell/entries/AccountEntries.kt`) passes
  `showDebugTools = BuildConfig.DEBUG` and the platform `MeActions`, which open
  the keys through the navigator and share or open links through
  `feature/me/ui/MeHost.kt` with `ShareLinkFactory`.
- The first frame shows the cached state; a recreated tab is never blank.
- Name and avatar come from the ID-token user, else Backend's own profile; the
  study group only from Backend's primary group. The share button appears for a
  positive ISU and shares `ShareLinkFactory.profile`
  ([App Links and sharing](app-links.md#sharing)).
- The social rows (`MeFriendsSummary`) show only while the opt-in is on;
  `MeFriendsSummary.Disabled` replaces them with one row to settings. Privacy
  opens `AppRoutes.Settings` with `page` = `PRIVACY`.
- `Вход на сайт` shows only while the opt-in is on (`webLoginAvailable`); the
  demo refuses it. Developer tools only in debug builds ([Debug tools](debug.md)).
- `StoreScreenshotCapture` sets `MeHost.releaseLook`, so a debug build shows the
  release screen without the developer tools row.

## Sign-out

After a `ConfirmDialog`, `MeViewModel.signOut()` runs
`SessionRepository.signOut()` once and disables the button until it ends; the
session returns the app to sign-in ([Sign-in](auth.md#session)).

## Refresh

Every start of `MeRoute` (`LifecycleStartEffect`) calls
`MeViewModel.refresh(RefreshMode.Silent)` and so `SocialRepository.refresh()`;
every opt-in change refreshes again.

## Tests

`MeViewModelTest`, `MeScreenTest` (`commonTest`), `AccountEntriesTest`
(`app/src/test`), goldens in `AccountScreenshotTest`.

## iOS

The iOS app ([iOS app](../ios.md)) hosts the same `MeRoute` as the me tab root:
`MeTabScreen` (`iosApp/Sources/Features/Social/`) through `meViewController`
(`shared/ios` `screens/SocialScreens.kt`), with `meModule`, `socialModule` and
`socialIosModule`. What differs:

- Settings and privacy rows open the SwiftUI settings pages; web sign-in is the
  shell's sheet.
- `showDebugTools` is false.
- Sharing goes through `SocialShares` and `PlatformActions`; links name the
  build's Backend origin (`BackendBaseURL`). External links open through
  `IosPlatformActions.openLink`.
- Tests: `SocialUITests`, `ShellUITests` (sign-out), `SettingsUITests`.
