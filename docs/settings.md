# Settings contract

The complete user-facing settings surface of the application. An option not
listed here is not a setting.

## Navigation and density

The settings root is a compact catalogue, not a scrolling list of every switch.
Every setting has exactly one home:

- `Аккаунты`: `Сервер ITMO.Widgets` (`Подключено` / `Не подключено`), the
  connection page below, and `БАРС` (`Подключён`, `Войти` after the session
  ended, `Не подключён`): the last two open the BARS sign-in, connected BARS
  only says so.
- `Приложение`: `Уведомления` (`включено N из M`, or `запрещены в системе`),
  `Виджеты` (the gallery; `2 на главном экране` once widgets are placed),
  `Оформление` (the colour's name) and `Разделы` (the settings of Сегодня,
  Расписание and Спорт).
- An untitled group with `О приложении`.

Each category is a pushed screen with its own back-stack entry, title and
scroll position, on the same kit as the profile: the detail header
(`ItmoDetailScreen`, `itmoDetailHeader` on iOS), `SectionHeader` titles, rows in
connected `ListGroup`s with their icon in a neutral tile, footers as
`ListGroupNote`; like every pushed screen it covers the bottom bar, and Back
returns one level at a time ([shell](design.md#shell)). Selecting or
reselecting a tab closes the whole stack. Back after a widget, notification,
tile or shortcut route leads home, not to the tab that was open before. Pages
enter with the persisted local values already rendered and never show a
loading indicator; only `Кто видит` loads from the network. A row's value says
what its page holds in a word; `Разделы` and `О приложении` show none. A
category appears only when its functionality is delivered. The profile holds
no setting of its own ([profile](features/me.md)).

The keys of the pages that were merged away stay routes, since shortcuts, gates
and old links name them (`SettingsPage.fromArgument`): `PRIVACY` opens
`SERVICES`, `HOME`, `SCHEDULE` and `SPORT` open `SECTIONS`, `MAINTENANCE` opens
`ABOUT`, `RECORDBOOK` opens `NOTIFICATIONS`, and `COMPACT_SCHEDULE_WIDGET`,
`FULL_SCHEDULE_WIDGET`, `QR_WIDGET` and `SCHEDULE_WIDGETS` open `WIDGETS`.

### iOS

The iOS app renders the same pages from the same page model
(`SettingsViewModel`, one page per `AppRoutes.Settings` key) with the SwiftUI
kit (`ItmoListGroup`, `ItmoRow`, `ItmoSwitchRow`) on the Me tab's stack ([iOS app](ios.md#settings-and-diagnostics)). Nothing
renders until the page is loaded, so stored values never animate in. Rows a
platform does not offer are hidden through `PlatformCapabilities`: on iOS the
quick settings tile, the spoiler animation and the custom spoiler image. The
root's notifications row says `запрещены в системе` while iOS denies them (or
has not asked yet); the BARS row opens `BarsLoginSheet`. The error journal is
shared as plain text through the share sheet; a Kotlin crash shows on the next
launch, Swift crashes are not recorded.

## Appearance

`Оформление` is a screen of its own (`AppearanceViewModel`, `AppearanceScreen`
on Android, `AppearanceScreen.swift` on iOS). Every choice is on the page, not
in a dialog, and applies to the app and the widgets at once. From the top:

- A sample of the app in the picked colours, once in the light and once in the
  dark theme, whichever the phone shows now, so contrast and `Чёрный фон` show
  too; with `Светлая` or `Тёмная` only that one. It is a picture: TalkBack and
  VoiceOver read it as one element.
- `Тема`: a segmented choice `Системная`, `Светлая`, `Тёмная`.
- `Цвет`: every colour as a named circle of the primary it gives, three to a
  row; the picked one is ringed and checked. `Свой цвет` puts its picker under
  the circles. A line says what `Как обои` does; under `Монохром` the circles
  are dimmed and inert with a line why.
- `Палитра`: every style as a card with three dots of what it makes of the
  colour (primary, its container, tertiary); the row scrolls sideways and opens
  on the picked one, whose plain description is under it.
- `Контраст`: a segmented choice with a line on what it does.
- `Чёрный фон`: a switch, on Android only, hidden while `Тема` is `Светлая`.
- A line on the widgets (below).

| Choice | Key | Values | Default |
|---|---|---|---|
| `Тема` | `app_theme_mode` | `Системная` (SYSTEM), `Светлая` (LIGHT), `Тёмная` (DARK) | `Системная` |
| `Цвет` | `app_accent_color` | `Как обои` (Android 12+ only), `Фирменный`, `Бирюзовый`, `Зелёный`, `Янтарный`, `Красный`, `Розовый`, `Фиолетовый`, `Свой цвет` | `Как обои` on Android 12+, `Фирменный` elsewhere |
| `Свой цвет` | `app_accent_custom` | any opaque colour (ARGB int); kept while another colour is picked | `#4984E2`, the brand blue |
| `Палитра` | `app_theme_style` | `Спокойная` (TonalSpot), `Яркая` (Vibrant), `Выразительная` (Expressive), `Точная` (Fidelity), `Насыщенная` (Content), `Нейтральная` (Neutral), `Монохром` (Monochrome) | `Спокойная` |
| `Контраст` | `app_theme_contrast` | `Обычный`, `Средний`, `Высокий` (M3 contrast 0, 0.5, 1) | `Обычный` |
| `Чёрный фон` | `app_dark_black` | on, off | off |

- The circles show each colour's primary in the current palette (under
  `Монохром` the default palette's, so the hues stay apart).
- With `Свой цвет` the picker has a `HEX` field and three sliders (`Оттенок`,
  `Насыщенность`, `Яркость`). A valid `#RRGGBB` applies as typed, a slider
  when let go; an unfinished value shows an error once the field loses focus.
  iOS shows the system colour well instead.
- With `Как обои`, a palette other than `Спокойная` or a contrast other than
  `Обычный` rebuilds the palette from the wallpaper's colour
  (`system_accent1_500`) instead of using the system's scheme.
- `Тема` sets light or dark for every screen of the app, its system bars,
  dialogs and web pages, without a restart; `Системная` follows the phone.
  Android sets AppCompat's night mode (`AppCompatDelegate.setDefaultNightMode`,
  read before the first activity starts), iOS the root's
  `preferredColorScheme`. The widgets, the QR tile and notifications stay light
  or dark as the system is: the launcher and the system draw them.
- `Чёрный фон` turns the dark theme's background and surfaces black; cards and
  other containers keep their tones. The light theme ignores it.
- Absent or unknown values read as the defaults; the choices are device
  settings that sign-out keeps. Without wallpaper colours (below Android 12,
  iOS) the stored default draws as `Фирменный`.
- The QR tile, notifications and the launcher icon keep their colours. The
  presets, their seeds and how the choices become a scheme:
  [design guide](design.md#colour-and-surfaces).

### Widgets in the app's colours

`Виджеты в цвет темы` (the widgets gallery, below the cards) is on by default:
stored in `widgets_follow_app_theme` (absent reads as on, a stored off stays
off), row key `widgets_follow_theme`. On, the widgets draw with the scheme
`Оформление` gives, without `Чёрный фон`; the rules, the QR widget's precedence
and the Android versions are in [widgets](features/widgets.md#theme-colours).

### iOS

The iOS app's own screens keep the system's colours
(`PlatformCapabilities.appColors` is off): the colour choices reach only the
widgets that follow the theme; `Тема` applies to the app as on Android. So the
iOS page has no `Как обои` and no `Чёрный фон`, its sample is a widget in the
picked colours, in both themes whatever `Тема` says (the widgets keep the
system's), and its last line says that only the widgets take the colours. iOS reads and writes the same keys of its own
`app_preferences` file; the app writes the palette into the widgets' App Group
files ([iOS widgets](ios.md#widgets)). The tinted and clear home screens and
the Lock Screen keep the system's rendering.

## Sections

`Разделы` (`SECTIONS`) holds the settings of the tabs in three groups:
`Сегодня`, `Расписание` ([below](#schedule)) and `Спорт` ([below](#sport)).
Every row works offline and without the connection.

`Сегодня` -> `Показывать кнопку пропуска` is on by default
(`home_qr_pass_visible`, kept on sign-out). Off hides only the `Пропуск` button
on Сегодня (the bottom accessory on iOS); the pass stays available through its
widgets, shortcuts and other entry routes.

## Account and services

«Сервер ITMO.Widgets» (`SERVICES`) is the one home of the connection to
ITMO.Widgets (`ConnectionViewModel`):

- The status card with the `Подключение` switch, off by default. Turning it on
  always goes through the consent sheet (`ServicesConsentSheet`: what the
  server checks and keeps, who sees what first); turning it off needs none and
  stops every Backend call at once. Without it subject links stay private on
  the device; turning it on uploads them as private links on the next refresh
  of a subject ([resources](features/resources.md)). Every change redraws the
  widgets. In the demo the switch is disabled.
- Connected: `Кто видит` ([below](#privacy)); not connected: `Что даёт
  подключение`. Devices and pairing live in Профиль → `Устройства`
  ([connected clients](features/connections.md)). Turning the switch on sends
  `PUT /api/users/me/consent` first, which enrolls the account on Backend.
- `Что хранит сервер` opens a sheet (`ServicesStoredData`) that mirrors the
  Backend tables, with a link to the server code.
- `Удалить аккаунт ITMO.Widgets` is shown with the switch off too, since an
  account may remain from an earlier connection, and opens
  `<WIDGETS_BASE_URL>/delete-account` in the browser. Deletion is a request
  handled by hand (Backend `docs/ops/account-deletion.md`); the app has no
  deletion screen.
- BARS is not part of the connection: its state and sign-in are the root's
  `БАРС` row.
- `Выйти` is an account action in the profile and requires confirmation.
  ITMO.ID login and refresh-token entry exist only on the signed-out screen;
  tokens are never editable settings.

## Privacy

`Кто видит` sets three audiences, each `Все`, `Друзья` or `Никто`:
`Расписание` and `Спорт` (default `Друзья`) and `Список друзей` (default
`Все`). Name, group and ISU are always visible and have no control. Backend
enforces the audiences; upgrading never broadens an existing audience, and
there is no review-visibility setting. Full rules:
[social](features/social.md#privacy).

## Widgets gallery

`Виджеты` (`WIDGETS`) has a card per format showing the real widget on a sample
Monday at 12:22 (`SchedulePreviewScenario`, `SportWidgetSamples`; never the
user's data), what it shows, `Добавить` (Android pins it through the launcher,
`WidgetPinRequester`) or `на экране`, and the settings button. iOS cannot place
a widget from the app: it shows `Как добавить` and, below the cards, the Lock
Screen widgets and the `Пропуск` Control. A format's settings open in a sheet
with its preview; a change applies at once to every placed widget of the
format. `Пара`, `Расписание на день` and `Спорт` carry a `Бета` pill
(`WidgetCard.beta`). Below the cards: `Виджеты в цвет темы`.

| Format | Settings | Defaults |
|---|---|---|
| `Пара` (next lesson) | `Следующая пара заранее`, `Размер текста` | early switch on; `Обычный` |
| `Расписание на день` | `Скрывать прошедшие пары`, `Показывать преподавателя`, `Завтрашние пары вечером`, `Размер текста` | past lessons shown; teacher shown; tomorrow on; `Обычный` |
| `Список пар` (the classic list of 2.2) | the same rows and keys as `Расписание на день` | as `Расписание на день` |
| `Спорт` | none | |
| `Пропуск` | [QR widget](#qr-widget) | |

- Each format keeps its own keys: `compact_widget_next_lesson_early`,
  `compact_widget_text_size`, `full_widget_hide_teacher` (`Показывать
  преподавателя` is its inverse), `full_widget_hide_past`,
  `full_widget_show_tomorrow` (absent reads as on), `full_widget_text_size`.
  Unscoped 2.2 keys are read only as defaults.
- The next-lesson widget names no teacher; `compact_widget_hide_teacher` stays
  for the first-run flow only.
- `Следующая пара заранее` moves the next-lesson widget on 15 minutes before
  the current lesson ends. `Завтрашние пары вечером` shows tomorrow on the day
  widget once today's last lesson ended.
- `Размер текста` (`Обычный`, `Крупный`, `Очень крупный`) scales every text of
  the format's widget by 1, 1.2 or 1.4: widgets cannot follow the app's
  typography.

## QR widget

The `Пропуск` sheet: `Динамические цвета` (on), `Скрывать QR-код за спойлером`
(on), `Анимация открытия` (circle, fade or none; circle), the custom spoiler
image and, on Android 13+, `Добавить в шторку`. Behaviour, the preview and the
tile: [widgets](features/widgets.md#qr-widget).

## Schedule

The `Расписание` group of «Разделы». The schedule change check lives on
«Уведомления» ([below](#notifications)).

- `Автозапись на спорт` shows pending sport auto-sign entries in the user's own
  schedule in the app and the schedule widgets, never another user's schedule.
  On by default (absent reads as on; an explicit off stays off); stored as
  `schedule_sport_auto_sign_enabled`. It is a local display preference: it can
  be changed while user services are off and never enables them or grants
  consent, but showing the entries needs them, and they are not confirmed
  bookings (the group's footer says so). A toggle refreshes installed widgets
  only after the value is saved; a failed save keeps the old value.
- `Синхронизация с календарём` keeps today and the next 28 days in the app's own
  phone calendar `ITMO.Widgets`, off by default; Google and Yandex Calendar do
  not show device calendars. `Выгрузить в .ics` opens the export sheet. Rules,
  the permission dialog and iOS: [calendar](features/calendar.md). Without
  calendar export (`PlatformCapabilities`) only the auto-sign switch is left.

## Notifications

«Уведомления» (`NOTIFICATIONS`) is the one home of notification settings
(`NotificationSettingsViewModel`); the bell, the root and the schedule settings
lead here. The subtitle is `включено N из M` (the types that can arrive and are
on: the checks, and the pushes while connected) or `запрещены в системе`.

- While the system denies notifications, a gate (`Разрешить`: the permission
  dialog while it can still appear, else the app's system page) stands first
  and the settings below stay, dimmed. Otherwise, on Android, while the system
  channel of a type that is on here is off, one card (`Открыть настройки`)
  stands first instead.
- Every type is one row with its switch.
- `Баллы` turns the background mark check
  ([mark tracking](features/marks-tracking.md)) on or off as a whole; while on,
  its sources follow under it. None needs `Подключение к ITMO.Widgets`.

| Source | Key | Default |
|---|---|---|
| `My ITMO` | `myitmo_marks_enabled` | on (absent means on); a device setting that survives sign-out |
| `БАРС` | `bars_marks_enabled` | absent: the row is hidden. The first successful BARS answer of the account (sign-in, BARS connected in the recordbook or the background read) stores on; cleared with the BARS session on sign-out |
| `Таблицы преподавателей` | `sheet_marks_enabled` | on (absent means on); always shown, a device setting that survives sign-out |

- `Баллы` off stores off in every source; on stores on in My ITMO and the
  sheets, and in BARS once BARS has answered. `БАРС` also carries the `Войдите
  в БАРС` reminder; switching it off withdraws it.
- Switching a source off forgets its snapshot and stops its part of the check;
  the unread subjects stay. The work is cancelled only when all three are off.
  Switching on schedules the work, and the first check of that source only
  takes a snapshot. Sheets off keeps the totals of the connected sheets and
  untracks every connection, so the first read after switching on only takes a
  baseline ([sheet scores](features/sheet-scores.md#background-check-of-sheets)).
- `Изменения расписания` turns on the background check of the own schedule
  ([schedule changes](features/schedule-changes.md)). On by default
  (`schedule_changes_enabled`, absent means on); it does not need the
  connection. Off cancels the work and forgets the snapshot, the history stays;
  on schedules it again and the first check only takes a new snapshot.
- Turning a check on without the permission asks for it as the gate does; the
  switch stays on whatever the answer is.
- The pushes of ITMO.Widgets (`Спорт`, `Друзья`, `Новые устройства`) have a
  switch per type in `PushCategoryPreferences` (`sport_notifications_enabled`,
  `friends_notifications_enabled`, `connections_notifications_enabled`; absent
  means on; a device setting that survives sign-out). Backend has no per-type
  preference, so the device drops them
  ([notifications](features/notifications.md#flow)). A sport push still books
  while its type is off. Without the connection one row `Нужно подключение к
  ITMO.Widgets` opens «Сервер ITMO.Widgets» and the push switches are disabled.
- `Работа в фоне` ([background work](#background-work)) appears while a check is
  on and the system restricts the app. `Звук и вибрация` opens the system's
  notification settings of the app.

### iOS

The same page; the checks run as steps of the app's one refresh task
([mark tracking](features/marks-tracking.md#ios)), the BARS and sheets sources
only where `PlatformCapabilities.marks` holds. The background work row is
`Обновление контента` (Background App Refresh), shown while a check is on and
Background App Refresh is off or restricted, and opens the app's page in
Settings. iOS never asked: no gate, the first check turned on asks.

iOS lists no push switch and no connection row
(`PlatformCapabilities.pushSwitches`): the notification service can hide a
delivered push only with `com.apple.developer.usernotifications.filtering`,
which the app does not hold, so the switches would do nothing (iOS has no local
sport notification, only the remote push). The stored switches stay and the
notification service ignores them (`PushHandler.honorsDisabledPushes`): a type
left off before is shown. A sport push still books.

## Background work

On some devices background checks work only when the app may run without
battery restrictions: on Xiaomi (MIUI, HyperOS) the default `Умный режим`
leaves a backgrounded app without network.

- The row `Работа в фоне` (an action row; the whole row is the button) sits on
  «Уведомления» while a check is on and
  `PowerManager.isIgnoringBatteryOptimizations` is false
  (`BackgroundWorkAccess`, `AndroidBackgroundWorkAccess`). The state is read
  when the page is created and on every return, and once more a second after
  the return because HyperOS saves the choice only after its page has gone, so
  the row leaves by itself once the user has lifted the limit.
- A tap opens, in the app's own task, the first system page the device can
  open (`BackgroundWorkScreens.forDevice`): on Xiaomi, Redmi and POCO the app's
  `Сведения о батарее` with `Контроль активности`
  (`com.miui.securitycenter/com.miui.powercenter.legacypowerrank.PowerDetailActivity`,
  HyperOS), then the older MIUI page
  (`com.miui.powerkeeper/.ui.HiddenAppsConfigActivity`), otherwise the app's
  details; elsewhere the battery optimisation list
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), otherwise the app's details.
- The app does not hold `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and never shows
  the system exemption dialog: Google Play allows it only for apps whose core
  function needs it. It only opens the page.
- Turning on `Изменения расписания` or a source of `Баллы` while the app is
  restricted shows a dialog once per device (`Разрешить` opens the same page,
  `Не сейчас`). The fact is stored as `background_work_hint_shown` and survives
  sign-out.
- On MIUI the `Без ограничений` mode may leave
  `isIgnoringBatteryOptimizations` unchanged; the row then stays.

Channels and what the switches change there: [notifications](features/notifications.md#channels).

## Sport

The `Спорт` group of «Разделы»:

- `Показывать фильтр по преподавателю` shows the teacher in the more-filters
  sheet of `Запись`; `Показывать фильтр по времени` the part of the day.
- Sections, building, `Есть места`, `Мои секции` and friends are ordinary
  sport-screen filters, not settings. `Мои секции` keeps the lessons of the
  user's sections, which live on Backend ([sport](features/sport.md#sections)).

## Recordbook

BARS in the recordbook is not a settings category: `recordbook_bars`, off by
default, cleared on sign-out, is switched by `Подключить` in Зачётка
([recordbook](features/recordbook.md#bars)).

## Maps

There is no map-provider setting. External maps open through a generic `geo:`
URI (`MapDestination`) and the system picks the installed map app.

## About and diagnostics

«О приложении» (`ABOUT`):

- `Обновить все виджеты` refreshes QR and schedule widgets; `Последнее
  обновление` shows the last successful widget refresh.
- `Журнал ошибок` opens the local diagnostics journal (`core/diagnostics`): the
  newest 200 warnings, errors and crashes, one JSON line each in
  `files/diagnostics/log.jsonl`. Every message passes `DiagnosticSanitizer`,
  which redacts bearer headers, JWTs and named token/password fields before
  the write. The screen lists entries newest first, copies the whole journal
  (`Журнал скопирован` below Android 13, where the system does not confirm a
  copy) or clears it after a confirmation. Nothing is uploaded.
- `Повторить первоначальную настройку` clears the first-run flag and returns
  the root graph to the first-run flow with an empty back stack. Nothing else
  is reset: the opt-in, pinned widgets and preferences stay
  ([onboarding](features/onboarding.md)).
- `Политика конфиденциальности` opens `<WIDGETS_BASE_URL>/privacy.html`.
- `Лицензии`: the source of the campus plans (ITMO Campus app) and the licences
  of MapLibre Native (BSD 2-Clause) and the map's Noto Sans labels (SIL OFL 1.1).
- `Версия` shows the version and build number, the short commit when known
  and, for a custom build, its source; a tap copies the full details
  ([build identity](building.md#build-identity)).
- The footer `Неофициальное приложение. Не связано с Университетом ИТМО.`; the
  sign-in screen shows the same line.

## Debug-only controls

Debug builds may additionally expose the academic-date override, synthetic sport
scores, sport lesson templates, development-service diagnostics,
`Проверить изменения расписания`, a one-off schedule change check,
`Проверить оценки`, a one-off mark check, and `Проверить продление БАРС`, a
read-only probe of the BARS cookie renewal that only writes its outcome to
logcat. These controls never appear in release builds and never change release
behavior.
