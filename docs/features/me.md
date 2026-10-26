# Profile tab

Product behaviour: [Как это работает: аккаунт](../how-it-works/account.md).

`feature/me` is the last bottom-bar tab, the Профиль hub: the own identity, entries to the social screens and the
settings, the links and sign-out. It owns no data; `MeViewModel` (`:shared:feature-account`) combines the session,
the custom-services opt-in and `SocialRepository`, and builds every text of the screen (`MeUiState`), so the Compose
and SwiftUI views only lay it out. Social screens: [Social](social.md); devices: [Connected clients](connections.md);
settings: [Settings](../settings.md).

## Screen

- The root header with `Поделиться` (a positive ISU shares `ShareLinkFactory.profile`,
  [App Links and sharing](app-links.md#sharing)).
- The identity card: the avatar with `personInitials` of ITMO.ID's family and given name, the name from the ID-token
  user, else Backend's own profile; the study group line only from Backend's primary group; the ISU.
- `Друзья` with the counts and the incoming requests as a badge. Without the connection
  (`MeFriendsSummary.Disabled`) a gate with `Подключить` stands in its place and opens «Сервер ITMO.Widgets».
- `Виджеты` (the gallery), `Устройства` (installs, connected clients and ITMO.ID sessions, with the count and a
  warning pill, [connected clients](connections.md)), `Настройки` (every setting, the connection, privacy and
  notifications included)
  and developer tools only in Android debug builds ([Debug tools](debug.md)).
- Links: `Сообщения ИСУ` (`AppRoutes.IsuMessages`, [ISU messages](isu-messages.md)), `Карта`
  (`AppRoutes.CampusMap()`, [campus map](map.md)), `Wi-Fi` (`AppRoutes.Wifi`, [Wi-Fi](wifi.md)), `My ITMO` ([My ITMO in the app](my-itmo-web.md); the
  demo refuses it), the Telegram channel and GitHub; then `Выйти`.
- The rows name no route themselves: `MeActions` reaches the host, which maps them: the gate to
  `AppRoutes.Settings(SERVICES)`, widgets to `AppRoutes.Settings(WIDGETS)`, settings to the root.
- The first frame shows the cached state; a recreated tab is never blank. `StoreScreenshotCapture` sets
  `MeHost.releaseLook`, so a debug build shows the release screen without the developer tools row.

## Sign-out

After a confirmation, `MeViewModel.signOut()` runs `SessionRepository.signOut()` once and ignores the row until it
ends; the session returns the app to sign-in ([Sign-in](auth.md#session)).

## Refresh

`MeViewModel` refreshes once when it is created (the tab's first show), silently; every opt-in change refreshes
again. Coming back from a pushed screen or another tab does not: relationship actions fold their answers into the
repository, which the summary follows, and Сегодня's bell refreshes the friend requests on each return of the app to
the foreground (`FriendRequestsBellSource`).

## iOS

The tab root is the SwiftUI `MeTabScreen` (`iosApp/Sources/Features/Social/`) over the same `MeViewModel`
([iOS app](../ios.md)). Sharing goes through `SocialShares`, links through `PlatformActions.openLink`; there is no
developer tools row.
