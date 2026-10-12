# Profile tab

`feature/me` is the last bottom-bar tab: the own identity, the entries to the
social screens, the application rows and sign-out. It owns no data;
`MeViewModel` (`:shared:feature-account`, Koin) combines the session, the
custom-services opt-in and `SocialRepository`. The social screens it opens are in [Social](social.md),
«Вход на сайт» in [Web sign-in](web-login.md), the settings in
[Settings](../settings.md).

## Screen

The screen is Compose in `:shared:feature-account`:
`feature/me/ui/MeScreen.kt` is the stateless `MeScreen(state, showDebugTools,
actions)` and `feature/me/ui/MeRoute.kt` takes `MeViewModel` (registered with `viewModelOf`
in `feature/me/di/MeModule.kt`) and wires sign-out and the link snackbar. On
Android the profile tab's root is `MeTabRoot`
(`shell/entries/AccountEntries.kt`): it passes `showDebugTools =
BuildConfig.DEBUG` and the platform `MeActions`, which open the keys through
the navigator and share and open the project links through the helpers of
`feature/me/ui/MeHost.kt` with `ShareLinkFactory`. iOS hosts the same
`MeRoute` in a `ComposeUIViewController`. The first frame already shows the cached state, so
a recreated tab never shows a blank card. Every row is one target, at least
48 dp high, and one screen-reader focus stop.

Header:

- Avatar and name from the ID-token user, else from Backend's own profile;
  without either the name is «Профиль не загрузился».
- The study group from Backend's primary group as «<группа> • <курс> курс •
  <факультет>», hidden while Backend has not answered.
- «ИСУ <номер>» from either source.
- «Поделиться» beside the name, shown for a positive ISU, shares «<имя> в
  ITMO.Widgets: <ссылка>» under the title «Профиль в ITMO.Widgets»; the link is
  `ShareLinkFactory.profile` ([App Links and sharing](app-links.md#sharing)).

Group «Друзья», while the opt-in is on:

- «Друзья» with `MeFriendsSummary`: `Loading` «Загружаем…», `Error` «Не
  загрузились», `Content` «Пока никого» or the count, and a badge with the
  number of incoming requests when there are any (TalkBack «Входящих заявок:
  N»). Opens `AppRoutes.Friends`.
- «Найти людей» / «По имени в My ITMO» opens `AppRoutes.UserSearch`.
- «Приватность» / «Кто видит расписание, спорт и друзей» opens
  `AppRoutes.Settings` on the privacy page (`page` = `PRIVACY`).

With the opt-in off (`MeFriendsSummary.Disabled`) the three rows are hidden and
one row «Нет подключения к ITMO.Widgets» / «Друзья, заявки и приватность
появятся после подключения» opens the settings.

Group «Приложение»:

- «Вход на сайт», only while the opt-in is on (`webLoginAvailable`); the demo
  session refuses it with «Недоступно в демо».
- «Настройки».
- «Инструменты разработчика», only in debug builds (`BuildConfig.DEBUG`); see
  [Debug tools](debug.md).

Below the groups two tonal buttons open GitHub
(`https://github.com/alllexey-dev/ITMO.Widgets`) and Telegram
(`tg://resolve?domain=itmowidgets`, then `https://t.me/itmowidgets`). When no
app can open any of the links a snackbar says «Нет приложения, чтобы открыть
ссылку».

## Sign-out

«Выйти» asks in a `ConfirmDialog` «Выйти из аккаунта?» with «С устройства удалятся данные аккаунта и
содержимое виджетов. Настройки приложения останутся.» and «Отмена» / «Выйти».
`MeViewModel.signOut()` runs `SessionRepository.signOut()` once and disables
the button until it ends; the session then returns the app to the sign-in screen
([Sign-in](auth.md#session)).

## Refresh

Every start of `MeRoute` (`LifecycleStartEffect`) calls
`MeViewModel.refresh(RefreshMode.Silent)` and so `SocialRepository.refresh()`,
so a return from the friends or search screens shows new counts. Every change of the opt-in refreshes again, so
switching it on in settings fills the card without reopening the tab.

## Tests

JVM: `MeViewModelTest`, `MeModuleTest` and the Compose test `MeScreenTest`
(`commonTest`; 48 dp rows, one target per row, the badge's description, the
web sign-in row and its divider only with the connection, the confirmation and
the disabled sign-out). `AccountEntriesTest` (`app/src/test`) opens the tab
root's routes from its rows, `MeTestTags.SETTINGS_ROW` among them. Goldens:
the five `MeScreen` previews in `feature/me/ui/MeScreenPreviews.kt`, in four
appearances, through `AccountScreenshotTest`. `StoreScreenshotCapture` sets
`MeHost.releaseLook`, so a debug build shows the release screen without the
developer tools row.

## iOS

The iOS app ([iOS app](../ios.md)) hosts the same `MeRoute` as the root of the
me tab: `MeTabScreen` (`iosApp/Sources/Features/Social/`) through
`meViewController` (`shared/ios`, `screens/SocialScreens.kt`), with
`meModule`, `socialModule` and `socialIosModule` in `IosKoinModules`. There is
no top bar, as on Android; the content keeps clear of the status bar, the tab
bar and the demo banner.

- Rows open the shared keys through the Swift router, as Android's
  `meActions`: friends, search and a profile are the social Compose routes on
  the me stack ([Social](social.md)), the settings and privacy rows the
  SwiftUI settings pages, the web sign-in the shell's sheet; the demo refuses
  the web sign-in with `error_demo_unavailable` (`SocialMessages`).
- No developer tools row: `showDebugTools` is false on iOS.
- The share button sends `share_profile_text` with
  `ShareLinkFactory.profile` through the system share sheet (`SocialShares`,
  `PlatformActions`); the share links name the build's site, the Backend
  origin of `BackendBaseURL`. GitHub opens in Safari, Telegram in the
  Telegram app when installed, else in Safari (`IosPlatformActions.openLink`).
- Sign-out asks in the route's own `ConfirmDialog`, then
  `SessionRepository.signOut()` returns the app to the sign-in screen.
- Tests: `SocialUITests` on the demo session (the profile header and groups,
  no developer tools row, the tab at AX1 down to the sign-out), the
  `ShellUITests` sign-out of the demo and the `SettingsUITests` entry through
  `MeTestTags.SETTINGS_ROW`.
