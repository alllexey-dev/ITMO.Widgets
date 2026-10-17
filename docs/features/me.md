# Profile tab

Product behaviour: [Как это работает: аккаунт](../how-it-works/account.md).

`feature/me` is the last bottom-bar tab, the Профиль hub (DECISIONS.md, Профиль): the own identity, entries to the
social screens and the settings pages, the app rows and sign-out. It owns no data; `MeViewModel`
(`:shared:feature-account`, Koin) combines the session, the custom-services opt-in and `SocialRepository`, and
builds every text of the screen (`MeUiState`), so the Compose and SwiftUI views only lay it out. Social screens:
[Social](social.md); web sign-in: [Web sign-in](web-login.md); settings: [Settings](../settings.md).

## Screen

- The one-line root header `Профиль` with `Поделиться` (a positive ISU shares `ShareLinkFactory.profile`,
  [App Links and sharing](app-links.md#sharing)).
- The identity card (`MeHeader`): the avatar with `personInitials` of ITMO.ID's family and given name, the name from
  the ID-token user, else Backend's own profile, else `Профиль не загрузился`; the study group line
  (`M3205, 2 курс, ФИТиП`) only from Backend's primary group; `ИСУ N`.
- `Люди`: `Друзья` with `friendsSubtitle` (`3 друга, 1 заявка`, `Пока никого`, loading or error) and the incoming
  requests as a badge; `Приватность` opens `AppRoutes.Settings` with `page` = `PRIVACY`. Without the connection
  (`MeFriendsSummary.Disabled`) a gate `Друзья недоступны` with `Подключить` stands in their place.
- `ITMO.Widgets`: `Подключение и данные` with the status `Подключено` / `Не подключено`; `Уведомления` and `Виджеты`
  as links; `Подключить устройство` ([web sign-in and pairing](web-login.md)), which says `Нужно подключение` and
  opens the connection while the opt-in is off. Nothing is hidden without the connection.
- `Приложение`: `My ITMO` ([My ITMO in the app](my-itmo-web.md); the demo refuses it), the settings, developer tools
  only in Android debug builds ([Debug tools](debug.md)), the Telegram channel and GitHub; then `Выйти`.
- The rows name no route themselves: `MeActions` reaches the host, which maps them. The connection and privacy open
  «Подключение и данные» (`AppRoutes.Settings(SERVICES)`, `PRIVACY`), notifications «Уведомления»
  (`AppRoutes.Settings(NOTIFICATIONS)`) and widgets the gallery's key `AppRoutes.Settings(WIDGETS)` (the settings
  root until the gallery's page lands).
- The first frame shows the cached state; a recreated tab is never blank. `StoreScreenshotCapture` sets
  `MeHost.releaseLook`, so a debug build shows the release screen without the developer tools row.

Not done yet, needing data or pages the app does not have: the notifications value (`5 из 6`), the widgets on
screen (`На экране: Пара, Пропуск`), the connection's last sync time (`обновлено в 12:20`) and a requests badge on
the Профиль tab.

## Sign-out

After a confirmation, `MeViewModel.signOut()` runs `SessionRepository.signOut()` once and ignores the row until it
ends; the session returns the app to sign-in ([Sign-in](auth.md#session)).

## Refresh

`MeViewModel` refreshes once when it is created (the tab's first show), with `RefreshMode.Silent` and so
`SocialRepository.refresh()`; every opt-in change refreshes again. Coming back from a pushed screen or another tab
does not: relationship actions fold their answers into the repository, which the summary follows, and Сегодня's
bell refreshes the friend requests on each return of the app to the foreground (`FriendRequestsBellSource`).

## Android

`MeScreen` (`feature/me/ui`, `ItmoRootScreen` with kit `ListGroup` rows) is stateless; `MeRoute` takes
`MeViewModel` and wires sign-out and the link snackbar. `MeTabRoot` (`shell/entries/AccountEntries.kt`) passes
`showDebugTools = BuildConfig.DEBUG` and the `MeActions`, which open the keys through the navigator and share or open
links through `feature/me/ui/MeHost.kt`.

## iOS

The tab root is the SwiftUI `MeTabScreen` (`iosApp/Sources/Features/Social/`) over the same `MeViewModel` ([iOS app](../ios.md)). Settings and
privacy rows open the SwiftUI settings pages, web sign-in is the shell's sheet, sharing goes through `SocialShares`,
links through `PlatformActions.openLink`; there is no developer tools row.

## Tests

`MeViewModelTest`, `MeScreenTest` (`commonTest`), `AccountEntriesTest` (`app/src/test`), the `MeScreen_*` goldens of
`AccountScreenshotTest` from `MeSamples`; on iOS the profile snapshots and `SocialUITests`, `ShellUITests`
(sign-out), `SettingsUITests`.
