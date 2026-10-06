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
actions)` and `feature/me/ui/MeRoute.kt` obtains `MeViewModel` from Koin and
wires sign-out and the link snackbar. `MeFragment` (`app/`) is
`navigation_me` (`AppRoot.ME`); it hosts `MeRoute` through `itmoComposeView`,
passes `showDebugTools = BuildConfig.DEBUG` and performs the platform
`MeActions`: `openScreen`/`openWebLogin`, the share sheet with
`ShareLinkFactory` and the project links. iOS hosts the same `MeRoute` in a
`ComposeUIViewController`. The first frame already shows the cached state, so
a recreated tab never shows a blank card. Every row is one target, at least
48 dp high, and one screen-reader focus stop; the root stays one transition
group under an overlay's back gesture.

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
  N»). Opens `AppScreen.FRIENDS`.
- «Найти людей» / «По имени в My ITMO» opens `AppScreen.USER_SEARCH`.
- «Приватность» / «Кто видит расписание, спорт и друзей» opens
  `AppScreen.SETTINGS` on the privacy page (argument `settings_page` =
  `PRIVACY`, mirrored as a literal because features never import each other).

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

JVM: `MeViewModelTest`, `MeModuleTest` and the host test `MeScreenTest` (48 dp
rows, one target per row, the badge's description, the web sign-in row and
its divider only with the connection, the confirmation and the disabled
sign-out). Goldens: the five `MeScreen` previews in
`feature/me/ui/MeScreenPreviews.kt`, in four appearances, through
`AccountScreenshotTest`. Instrumented: `ProfileBackMotionTest` and
`MainNavigationTest` open the tab in `SettingsNavigationTestActivity` (debug)
and click `MeTestTags.SETTINGS_ROW` through its semantics. The store frame
`10-me` sets `MeFragment.releaseLook`, so a debug build shows the release
screen without the developer tools row. `MeRulesTest` keeps the row debug only.
