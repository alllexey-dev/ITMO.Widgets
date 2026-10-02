# Settings contract

The complete user-facing settings surface of the application. An option not
listed here is not a setting. Screens are built declaratively; see the
"Settings as data" section of [`architecture.md`](architecture.md).

## Navigation and density

The settings root is a compact catalogue, not a scrolling list of every switch:

- `Доступ`: `Подключение к ITMO.Widgets` (with account deletion), `Друзья и приватность`, and the Android notifications action.
- `Виджеты`: `Компактное расписание`, `Полное расписание` and the QR widget.
- `Приложение`: home screen, schedule, recordbook (`Зачётка`), sport, and
  maintenance.

Each category opens a separate back-stack entry with its own title and scroll
position. Notification permissions open Android settings directly. Rows show a
secondary value only for a genuine single state, such as services or notification
permission; multi-option categories do not summarize one arbitrarily chosen toggle.
Explanations shared by several controls appear once below their group.

The root and offline categories enter with persisted local values already rendered;
they never display a loading indicator. Settings open as a full-screen surface
sliding above the unchanged profile and bottom bar in 220 ms. Categories use a
220 ms horizontal shared-axis transition inside that surface; Back reverses one
level at a time. Motion follows system animation scale. Only privacy has a
network-loading state. Identity fields and the avatar underneath remain stationary.

Every contextual section opened from Profile follows this overlay flow. Selecting
or reselecting a root destination, including opening Schedule from a widget, closes
the entire overlay stack. Returning to Profile then shows its menu, not old settings
history. Rotation restores the current level; it does not act as a root switch.
Back after a widget, notification, tile or shortcut route leads home, not to
the tab that was open before.

Navigation rows have a minimum 48 dp touch target and expand for large text.
Settings use quiet surface-container cards without a stroke or elevation. The
profile uses the same compact surfaces, with a settings entry and a separate,
confirmed sign-out action. Privacy is available only inside settings.
Future v2.2 categories appear only when their functionality is delivered.

## Account and services

- `Подключение к ITMO.Widgets` enables Backend-dependent features. It is disabled
  by default and requires explicit consent. Without it subject links stay
  private on the device; turning it on uploads them as private links on the
  next refresh of a subject (see [resources](features/resources.md)).
- `Удалить аккаунт ITMO.Widgets` (`Как удалить данные с сервера ITMO.Widgets`,
  `ic_open_in_new`) is an action row in its own untitled group under the
  switch on the `Подключение к ITMO.Widgets` page. It is shown with the switch
  off too, since an account may remain from an earlier connection, and opens
  `<WIDGETS_BASE_URL>/delete-account` in the browser. Deletion itself is a
  request handled by hand (Backend `docs/ops/account-deletion.md`); the app has
  no deletion screen.
- `Уведомления` shows the current Android notification-permission state and opens
  the system application settings when permission is missing.
- `Выйти` remains an account action in the profile and requires confirmation.
- ITMO.ID login and refresh-token entry exist only on the signed-out screen.
  Tokens are not editable settings.

## Privacy and social access

Initial privacy loading and explicit retries remain visible for at least 300 ms
to avoid a flash on fast connections. Requests start immediately; slower requests
do not incur an additional delay. Disabling services takes effect immediately.

- Name, group, and ISU are always visible to authenticated users. The application
  does not offer controls that imply these official identity fields can be hidden.
- `Кто видит расписание` and `Кто видит спорт` are independent audience choices:
  `Все`, `Друзья` (the default for new users), and `Никто`.
- `Кто видит список друзей` uses the same three audiences and defaults to `Все`
  for existing and new accounts. Schedule and sport defaults are unchanged.
- `Все` allows any authenticated application user, regardless of that viewer's
  own privacy choices. `Друзья` requires mutual friendship. `Никто` allows only
  the owner. There is no reciprocal sharing requirement.
- Existing enabled legacy settings become `Друзья`; previously disabled settings
  remain `Никто`. Upgrading must never silently broaden an existing audience.
- Backend, not Android, enforces the owner audience and friendship requirements
  (decision [0004](decisions/0004-privacy-audiences.md)). Blocking is not
  implemented and a rejected request is not a block.
- Only the owner privacy endpoint returns audience settings. Other user responses
  contain viewer-scoped permissions. Unavailable/unknown settings are disabled
  and shown without an invented selected audience; saving failures restore the
  previously confirmed values.
- Friends are explicit: a request stays pending until the other side accepts,
  rejects, or the sender cancels; a crossed request becomes a friendship at once.
  Friends, requests and people search live in the profile tab; a public profile
  shows one relationship action and unlocks friends, schedule and sport rows from Backend
  capabilities, never from the viewer's own settings.
- Block-management controls remain planned until the corresponding Backend
  feature is implemented.
- Public-review visibility is not shown until reviews ship in v2.2.

## Schedule widgets

Each format has its own settings page and independent persisted values, shared
only among installed instances of that same format:

| Page | Controls | Defaults |
|---|---|---|
| `Компактное расписание` | `Следующая пара заранее`, `Скрывать преподавателя`, `Размер текста` | early switch on; teacher visible; text `Обычный` |
| `Полное расписание` | `Скрывать преподавателя`, `Скрывать прошедшие занятия`, `Показывать расписание на завтра`, `Размер текста` | teacher visible; past lessons visible; tomorrow off; text `Обычный` |

- The compact widget shows today's current/next lesson. Its optional early switch
  advances 15 minutes before a lesson ends. It has no past-list or tomorrow-list
  switches, since neither applies to its format.
- The full widget keeps a lesson until its actual end when hiding past lessons,
  regardless of the compact widget's early-switch preference. Its optional
  tomorrow list appears only after today's lessons actually finish.
- `Размер текста` (`Обычный`, `Крупный`, `Очень крупный`) scales every text in
  that format's widget by 1, 1.2 or 1.4 relative to the layout's own sizes.
  It exists because widgets cannot follow the app's typography and a widget
  stretched over a large area keeps small fixed text; the choice is per format and stored in
  `compact_widget_text_size` / `full_widget_text_size`. The preview follows it.
- Teacher visibility is independent, including pending sport rows and the
  official-only offline fallback. Format-scoped preference keys override read-only
  unscoped fallback keys, preserving existing choices without linking later edits.
- Each page has a pinned live preview of only its own format, using the real
  layouts and selection rules. There is no format pager inside either page.
  Sample time switches between 12:50 (during lessons) and 18:00 (after lessons)
  and survives view recreation. Examples are fixed local data, not the user's
  schedule; they never change academic time, caches or installed widgets.
- Completed full-widget rows dim uniformly, including both time labels. Status
  messages center their title and hint; normal rows retain their alignment.
  The full widget centers its `Сегодня`/`Завтра` date header.
- Smart updates and the established visual style remain fixed. Shared update
  work chooses the earliest boundary needed by either format. Successful setting
  writes and sport mutations refresh installed widgets without waiting for the
  next automatic update; failed writes preserve the saved values.
- The schedule-level `Автозапись на спорт` preference still controls the optional
  pending projection in the app and both widgets; it is not a format setting.

## QR widget

- A live preview immediately reflects colors, spoiler, animation, and custom-image
  changes. Tap the preview to reveal/hide the example code; changing animation
  plays it once. Motion stops when the screen is no longer visible.
- The preview uses the real widget's bitmap and animation renderers, but contains
  only a clearly labelled sample payload, never an active access pass. No network
  requests or widget registration are needed.
- The preview frame is 184 × 184 dp. Sample images warm up from the settings root
  and are reused from a bounded, palette-aware cache. The QR page enters only when
  its first image (or error state) is ready, without a transient preview spinner.
- `Динамические цвета` is enabled by default.
- `Скрывать QR-код за спойлером` is enabled by default.
- `Анимация открытия` offers circle, fade, and no animation. Circle is the
  default.
- `Выбрать изображение спойлера` opens the Android photo picker (document-picker
  fallback on older devices), then a square crop screen with explicit cancel,
  rotate, and confirm actions. Cancelling either step leaves the old image intact.
- Confirmed images are stored locally as bounded 420 × 420 PNG files. Saving is
  atomic, survives view recreation, and refreshes widgets only after success.
  A failed replacement preserves the previous image.
- `Сбросить изображение спойлера` removes the custom image.
- Animation and custom-image controls are disabled when the spoiler is disabled.
- `Добавить в шторку` (`Плитка «QR-пропуск» в быстрых настройках`) is the first
  row, in a group of its own, on Android 13+ while the device flag
  `qr_tile_added` is off. It shows the system dialog
  (`StatusBarManager.requestAddTileService`). Added: the flag is set and the row
  leaves without moving the preview. Already added: the flag is set and a
  snackbar says `Плитка уже в шторке`. Declined or a request in progress:
  nothing. Other errors: `Не удалось добавить плитку`. The flag belongs to the
  device and survives sign-out; removing the tile from the shade clears it and
  brings the row back.

## Home screen

- `Главный экран` lists one switch per feed card: `Расписание на сегодня`,
  `Изменения в расписании`, `Новые оценки`, `Спорт`, `Заявки в друзья`. A
  switched-off card leaves the feed at once; nothing else changes and no widget
  is refreshed.
- Stored as the string set `home_hidden_cards` (card kind names; absent means
  shown). The page is an offline category; its footer explains that hints on
  the home screen are closed with their own button and do not return.
- The hints themselves are not settings. Closed hints are remembered per
  installation in `home_dismissed_hints`; sign-out and `Повторить первоначальную
  настройку` leave them alone.

## Schedule

- `Изменения расписания` turns on the background check of the own schedule
  for changes, its notifications, the home card and the marks in the schedule
  ([schedule changes](features/schedule.md#schedule-changes)). It is enabled by
  default and stored as `schedule_changes_enabled` (absent means on). It does
  not need `Подключение к ITMO.Widgets`: the check talks only to My ITMO.
- Switching it off cancels the background work and forgets the snapshot; the
  history of changes stays. Switching it on schedules the work again, and the
  first check only takes a new snapshot.
- The description is `Уведомлять о переносах и отменах пар.`, or
  `Уведомления выключены.` while Android notifications are off for the app.
  Switching it on without the permission asks for it on Android 13+ while the
  system dialog can still appear, otherwise opens the app's notification page.
  The switch stays on whatever the answer is.
- Below the switch, `Работа в фоне` appears while the switch is on and Android
  restricts the app in the background ([background work](#background-work)).
- `Автозапись на спорт` displays pending sport auto-sign entries in the user's own
  schedule in the application and schedule widgets. It is disabled by default
  and does not represent a confirmed booking.
- This is a local display preference, stored as `schedule_sport_auto_sign_enabled`.
  It can be changed while user services are disabled and never enables them or
  grants consent implicitly. Displaying the entries still requires user services.
- The same preference controls the application and schedule widgets, never another
  user's schedule. An explicit toggle refreshes installed widgets only after the
  value is successfully saved. Loading or observing the preference does not
  refresh widgets; a failed save preserves the previous value and does not refresh.
- `Синхронизация с календарём` keeps the own schedule of today and the next
  28 days in the app's own calendar `ITMO.Widgets` on the phone
  ([calendar](features/schedule.md#calendar)); there is no calendar choice and
  Google-account calendars are never written. Off by default; the state lives
  in `filesDir/calendar_sync/state.json`, not in DataStore, so it is a device
  setting outside backups. It does not need `Подключение к ITMO.Widgets` or
  `Изменения расписания`. The line under it is `Пары на 4 недели вперёд в системном календаре телефона.`, or
  `Выключена: нет доступа к календарю.` / `Выключена: календарь удалён.` after
  it turned itself off.
- Turning it on asks for `READ_CALENDAR` and `WRITE_CALENDAR` (only here).
  When Android suggests an explanation, the Material 3 dialog with a centred
  `ic_calendar_add` comes first: `Доступ к календарю`, `Чтобы записывать пары
  в календарь телефона.`, `Не сейчас` and `Разрешить`. A plain refusal leaves
  the switch off with the snackbar `Нет доступа к календарю`; a refusal for
  good shows the same dialog with `Открыть настройки` (the app's system
  page). Granted, it turns on into `ITMO.Widgets`. Turning it off
  deletes that calendar with its events.
- `Выгрузить в .ics` (`ic_download`, `Файл с парами за выбранный период`)
  opens the export sheet ([`.ics` export](features/schedule.md#ics-export)).
- The page is an offline settings category with three untitled groups: the
  schedule-changes switch with `Работа в фоне`, the auto-sign switch with a
  footer that explains the user-services requirement and that pending entries
  are not confirmed bookings, then the calendar group without a footer:
  `Синхронизация с календарём` (`Пары на 4 недели вперёд в системном календаре
  телефона.` — the phone's own calendar app; Google and Yandex Calendar do not
  show device calendars) and `Выгрузить в .ics`. An untitled group after another
  one keeps the group gap
  (`design_spacing_group`) above its card.

## Recordbook

`Зачётка` (`SettingsPage.RECORDBOOK`, after `Расписание`) is an offline page
with one untitled group for the background mark check
([mark tracking](features/recordbook.md#mark-tracking)). No switch needs
`Подключение к ITMO.Widgets`: the check talks only to My ITMO, BARS and public
Google Sheets.

| Switch | Key | Default |
|---|---|---|
| `Оценки My ITMO` | `myitmo_marks_enabled` | on (absent means on); a device setting that survives sign-out |
| `Оценки БАРС` | `bars_marks_enabled` | absent: the switch is hidden. The first successful BARS answer of the account (sign-in, the `БАРС` chip or the background read) stores on; cleared with the BARS session on sign-out |
| `Оценки из таблиц` | `sheet_marks_enabled` | on (absent means on); always shown, a device setting that survives sign-out |

- `Оценки БАРС` appears only once BARS has answered for this account; before
  that there is nothing to check.
- Switching a source off forgets its snapshot and stops its part of the check;
  the unread subjects stay. The work is cancelled when both are off. Switching
  `Оценки БАРС` off also withdraws the `Войдите в БАРС` reminder. Switching on
  schedules the work, and the first check of that source only takes a snapshot.
- `Оценки из таблиц` follows `Оценки БАРС` (or `Оценки My ITMO` while BARS is
  hidden). Off keeps the totals of the connected sheets, which the subject pages
  show, and untracks every connection, so the first background read after
  switching on only takes a baseline
  ([sheet scores](features/recordbook.md#background-check-of-sheets)). The work
  is cancelled only when all three are off.
- The footer is `Уведомлять о новых и изменённых оценках.`, or
  `Уведомления выключены.` while Android notifications are off for the app.
  Switching either on without the permission asks for it as on the schedule
  page; the switch stays on whatever the answer is.
- Below the switches, `Работа в фоне` appears while at least one of the three is on and
  Android restricts the app in the background ([background work](#background-work)).

## Background work

On some devices background checks work only when the app may run without
battery restrictions: on Xiaomi (MIUI, HyperOS) the default `Умный режим`
leaves a backgrounded app without network.

- The row `Работа в фоне` (`Разрешите работу без ограничений.`, an action row
  with `ic_open_in_new`; the whole row is the button) sits on the `Расписание`
  and `Зачётка` pages while that page's check is on and
  `PowerManager.isIgnoringBatteryOptimizations` is false
  (`BackgroundWorkAccess`). The state is read when the page is created and on
  every return, and once more a second after the return because HyperOS saves
  the choice only after its page has gone, so the row leaves by itself once the
  user has lifted the limit.
- A tap opens, in the app's own task, the first system page the device can
  open
  (`BackgroundWorkScreens.forDevice`, `openBackgroundWorkSettings`): on Xiaomi,
  Redmi and POCO the app's `Сведения о батарее` with `Контроль активности`
  (`com.miui.securitycenter/com.miui.powercenter.legacypowerrank.PowerDetailActivity`,
  HyperOS), then the older MIUI page
  (`com.miui.powerkeeper/.ui.HiddenAppsConfigActivity`), otherwise the app's
  details; elsewhere the battery optimisation list
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), otherwise the app's details.
- The app does not hold `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and never shows
  the system exemption dialog: Google Play allows it only for apps whose core
  function needs it. It only opens the page.
- Turning on `Изменения расписания`, `Оценки My ITMO`, `Оценки БАРС` or
  `Оценки из таблиц` while the
  app is restricted shows a dialog once per device: the title `Работа в фоне`,
  the text
  `Чтобы проверки приходили вовремя, разрешите приложению работу без ограничений.`,
  `Разрешить` (opens the same page) and `Не сейчас`. The fact is stored as
  `background_work_hint_shown` in DataStore and survives sign-out.
- On MIUI the `Без ограничений` mode may leave
  `isIgnoringBatteryOptimizations` unchanged; the row then stays.

## Notification channels

`Уведомления` opens Android's application notification settings. FCM categories
are `Спорт: автозапись` (`sport`) and `Друзья` (`friends`); the local schedule
check posts to `Изменения расписания` (`schedule_changes`) and the mark check to
`Оценки` (`marks`). All four are at default importance. Android controls
permission, sound and category visibility; there are no duplicate in-app
switches, and `Изменения расписания`, `Оценки My ITMO`, `Оценки БАРС` and `Оценки из таблиц` switch
the checks themselves, not the categories. Disabled notification permission
suppresses only the visual alert, not sport automation already enabled by the
user or the background checks. Disabling
user services suppresses FCM actions and attempts to unregister this device.

## Sport

- `Показывать фильтр по преподавателю` controls the optional teacher selector.
- `Показывать фильтр по времени` controls the optional time selector.
- Sport type, building, availability, auto-sign, and friends remain ordinary
  sport-screen filters, separate from the schedule display preference above.

## Recordbook BARS overlay

- The recordbook header has a `БАРС` filter chip, not a settings category. It is
  off by default, persisted locally in DataStore and cleared on sign-out.
- The chip overlays BARS scores, grades and control trees onto the MyITMO list
  of the same period. Periods, subject identities, PE and sport stay MyITMO.
- BARS needs its own ITMO.ID token. It is renewed silently from the session the
  app's WebView already holds; a sign-in screen appears only when that session
  has ended. Screens renew it in a hidden WebView; the background mark check,
  which has no WebView, repeats the official authorization request with the
  WebView's ITMO.ID cookies (decision
  [0012](decisions/0012-bars-background-renewal.md)). Tokens and cookies are
  never editable settings or bundled credentials.
- Changing the period while the chip is on also changes the saved period in web
  BARS: the server keeps that selection and offers no stateless read.
- When BARS fails the list keeps MyITMO values and shows a snackbar; nothing
  is silently substituted in either direction.

## Maps

There is no map-provider setting. Every building action launches a generic
system `geo:` intent and lets Android resolve the installed mapping application.

## Maintenance and diagnostics

- `Обновить все виджеты` refreshes QR and schedule widgets.
- `Последнее обновление` displays the last successful widget refresh.
- `Журнал ошибок` opens the local diagnostics journal (`core/diagnostics`): the
  newest 200 warnings, errors and crashes, stored as one JSON line each in
  `files/diagnostics/log.jsonl`. Every message passes `DiagnosticSanitizer`,
  which redacts bearer headers, JWTs and named token/password fields before
  the write. The screen copies the whole journal to the clipboard or clears it
  after confirmation; nothing is uploaded. The row shows the entry count.
- `Повторить первоначальную настройку` (`Виджеты, подключение и уведомления`)
  clears the first-run flag, closes the settings overlay and returns the root
  graph to the first-run flow with an empty back stack. Nothing else is reset:
  the opt-in, pinned widgets and preferences stay as they are. See
  [features/onboarding.md](features/onboarding.md).
- `Политика конфиденциальности` (`ic_open_in_new`) opens
  `<WIDGETS_BASE_URL>/privacy.html` in the browser.
- `Версия` displays the application version.
- The group's footer is `Неофициальное приложение. Не связано с Университетом
  ИТМО.`; the sign-in screen shows the same line under its buttons.

## Debug-only controls

Debug builds may additionally expose the academic-date override, synthetic sport
scores, sport lesson templates, development-service diagnostics,
`Проверить изменения расписания`, a one-off schedule change check,
`Проверить оценки`, a one-off mark check, and `Проверить продление БАРС`, a
read-only probe of the BARS cookie renewal that only writes its outcome to
logcat. These controls never appear in release builds and never change release
behavior.

## Deferred beyond v2.1

Community resources and teacher-review settings belong to v2.2; calendar
synchronization is on the `Расписание` page.
