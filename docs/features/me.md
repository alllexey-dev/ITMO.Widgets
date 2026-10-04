# Profile tab

`feature/me` is the last bottom-bar tab: the own identity, the entries to the
social screens, the application rows and sign-out. It owns no data;
`MeViewModel` combines the session, the custom-services opt-in and
`SocialRepository`. The social screens it opens are in [Social](social.md),
«Вход на сайт» in [Web sign-in](web-login.md), the settings in
[Settings](../settings.md).

## Screen

`MeFragment` is `navigation_me` (`AppRoot.ME`). `MeRenderer.render(binding,
state)` binds every view from `MeUiState`; the fragment renders the current
state before the first frame, so a recreated tab never shows a blank card.
Every row is one screen-reader focus stop.

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
app can open any of the links a Snackbar says «Нет приложения, чтобы открыть
ссылку».

## Sign-out

«Выйти» asks «Выйти из аккаунта?» with «С устройства удалятся данные аккаунта и
содержимое виджетов. Настройки приложения останутся.» and «Отмена» / «Выйти».
`MeViewModel.signOut()` runs `SessionRepository.signOut()` once and disables
the row until it ends; the session then returns the app to the sign-in screen
([Sign-in](auth.md#session)).

## Refresh

`onStart` calls `SocialRepository.refresh()`, so a return from the friends or
search screens shows new counts. Every change of the opt-in refreshes again, so
switching it on in settings fills the card without reopening the tab.

## Tests

JVM: `MeViewModelTest`. The tab renders in `SettingsNavigationTestActivity`
(debug) and is captured by `WebLoginVisualTest`.
