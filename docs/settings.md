# Settings contract

The complete user-facing settings surface of the application. An option not
listed here is not a setting. Screens are built declaratively; see the
"Settings as data" section of [`architecture.md`](architecture.md).

## Navigation and density

The settings root is a compact catalogue, not a scrolling list of every switch:

- `Доступ`: `Подключение и данные` (with its state) and `Уведомления` (saying
  when the system denies them); both are screens of their own, below.
- `Виджеты`: one row to the widgets gallery (`Виджеты`, `AppRoutes.Settings("WIDGETS")`).
- `Приложение`: `Оформление`, home screen, schedule, sport, and maintenance.

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
confirmed sign-out action. Privacy lives on «Подключение и данные».
A category appears only when its functionality is delivered.

### iOS

The iOS app renders the same pages from the same page model
(`SettingsViewModel`, one page per `AppRoutes.Settings` key) as SwiftUI forms
pushed on the Me tab's stack; the Me tab's settings row opens the root, its
privacy row «Подключение и данные» ([iOS app](ios.md#settings-and-diagnostics)).
Nothing renders until the page is loaded, so stored values never animate in.
Rows a platform does not offer are hidden through `PlatformCapabilities`: on
iOS the quick settings tile, the spoiler animation and the custom spoiler
image. The notifications row says `Запрещены в iOS` while iOS denies them. On
«Уведомления» the background work row is `Обновление контента` (Background
App Refresh): it shows while a background check is on and Background App
Refresh is off or restricted for the app, and opens the app's page in Settings. The error journal shares
the plain-text journal through the share sheet; a Kotlin crash shows on the
next launch, Swift crashes are not recorded.

## Appearance

The root's `Приложение` group opens `Оформление`, a page of its own. A sample
(a switch row and a filled and a tonal button) sits above its rows, unscrolled,
and every choice recolours it and every Compose screen at once.

| Row | Key | Choices | Default |
|---|---|---|---|
| `Цвет оформления` | `app_accent_color` | `Как обои` (Android 12+ only), `Фирменный`, `Бирюзовый`, `Зелёный`, `Янтарный`, `Красный`, `Розовый`, `Фиолетовый`, `Свой цвет` | `Как обои` on Android 12+, `Фирменный` elsewhere |
| `Свой цвет` (only while it is the accent) | `app_accent_custom` | any opaque colour (ARGB int) | `#4984E2`, the brand blue |
| `Стиль палитры` | `app_theme_style` | `Спокойная` (TonalSpot), `Яркая` (Vibrant), `Выразительная` (Expressive), `Точная` (Fidelity), `Насыщенная` (Content), `Нейтральная` (Neutral), `Монохром` (Monochrome) | `Спокойная` |
| `Контраст` | `app_theme_contrast` | `Обычный`, `Средний`, `Высокий` (M3 contrast 0, 0.5, 1) | `Обычный` |
| `Чёрный фон` | `app_dark_black` | on, off | off |

- `Цвет оформления` opens a picker of colour swatches, each the primary of the
  scheme it gives in the current style (under `Монохром` the default style's,
  so the hues stay apart); a tap applies at once and `Готово` closes it.
  TalkBack reads each swatch by its name and selection; nothing animates.
- With `Свой цвет` picked, the picker shows a `HEX` field with a swatch of the
  colour in it and three sliders (`Оттенок`, `Насыщенность`, `Яркость`). A
  valid `#RRGGBB` applies as typed, a slider applies when let go; an
  unfinished value shows `Шесть знаков 0–9 и A–F, например #4984E2` once the
  field loses focus. The `Свой цвет` row under the accent shows the value and
  opens the same picker.
- `Стиль палитры` and `Контраст` open choice dialogs; each style has a line on
  what it does. With `Как обои`, a style other than `Спокойная` or a contrast
  other than `Обычный` rebuilds the palette from the wallpaper's colour
  (`system_accent1_500`) instead of using the system's scheme; a footer under
  the colours says so on Android 12+.
- `Чёрный фон` turns the dark theme's background and surfaces black; cards and
  other containers keep their tones. The light theme ignores it.
- Absent or unknown values read as the defaults, today's look; the choices are
  device settings that sign-out keeps. Without wallpaper colours (below Android
  12, iOS) the stored default shows and draws as `Фирменный`.
- Widgets keep their own colours unless `Виджеты в цвет темы` is on (next
  section); the QR tile, notifications and the launcher icon always do.
- The presets, their seeds and how the stored choices become a scheme are in
  [the design guide](design.md#colour-and-surfaces).

### Widgets in the app's colours

| Row | Default |
|---|---|
| `Виджеты в цвет темы` (the widgets gallery, below the cards) | off: the widgets' own colours |

- On, the next-lesson, day, sport and QR widgets draw with the scheme the stored
  `Оформление` gives (`resolveColorScheme`: accent or `Свой цвет`, `Стиль
  палитры`, `Контраст`; with `Как обои` at the default style and contrast the
  wallpaper's scheme on Android 12+, the brand scheme elsewhere), light or dark
  as the system is. Only colours change: the layouts, sizes, shapes and the
  lesson type colours stay the widgets' own.
- `Чёрный фон` does not reach the widgets: a widget is a card on the home
  screen, not a page, so it keeps the scheme's surface tone (a black tile would
  vanish on a dark wallpaper and lose its outline).
- Stored in `widgets_follow_app_theme` (boolean; absent reads as off). The
  settings row key is `widgets_follow_theme`.
- Turning the switch, or any change on `Оформление`, redraws installed widgets
  at once (`WidgetRefreshRequester.refreshAll`); the gallery's previews
  follow both.
- QR widget precedence: without `Динамические цвета` the code stays black on
  white whatever this switch says (it always scans); with them the code and the
  spoiler take the app's scheme while the switch is on, the system's colours
  otherwise.
- Android below 12 sets the colours of the mode the widget was last drawn in;
  from Android 12 the launcher switches light and dark itself. The QR widget is
  a bitmap and takes the mode of its last drawing on every version, as with
  `Динамические цвета`.
- The QR tile in the quick settings keeps the system's style.

### iOS

The same page renders in the SwiftUI settings form, without `Как обои`: the
choices are menus (a style's line is its menu subtitle), `Чёрный фон` a
switch, and `Свой цвет` the system colour well, which reports `#RRGGBB` like
Android's picker. There is no sample: the SwiftUI screens keep their tint,
while the Compose screens recolour at once. iOS reads and writes the same keys
of its own `app_preferences` file. `Виджеты в цвет темы` is the same switch of
the SwiftUI gallery, stored in `widgets_follow_app_theme`; the app
writes the palette into the widgets' App Group files and reloads them
([iOS widgets](ios.md#widgets)). The lesson, day and QR widgets follow it in
full colour; the tinted and clear home screens and the Lock Screen keep the
system's rendering.

## Home screen

`Приложение` -> `Главный экран` contains `Показывать кнопку пропуска`, on by default
(`home_qr_pass_visible`, boolean, kept on sign-out). Turning it off hides only the
`Пропуск` button on Сегодня on Android and the bottom accessory on iOS; the pass
remains available through its widgets, shortcuts and other entry routes. Both
shells observe changes immediately. The setting works offline and without custom services.

## Account and services

`Подключение и данные` (`AppRoutes.Settings` `SERVICES`; the old `PRIVACY` key
opens it too) is the one home of the connection to ITMO.Widgets (DECISIONS.md,
Screens): `ConnectionViewModel` (`:shared:feature-settings`,
`presentation/connection`) under a Compose and a SwiftUI screen.

- The status card with the `Подключение` switch. It is off by default; turning it
  on always goes through the consent sheet (`ServicesConsentSheet`: what the
  server checks, what it will keep, who sees what first, `Подключить` and `Не
  сейчас`), turning it off needs none and stops every Backend call at once. Without
  it subject links stay private on the device; turning it on uploads them as
  private links on the next refresh of a subject (see [resources](features/resources.md)).
  Every change redraws the widgets. In the demo the switch is disabled.
- `Что даёт подключение`: friends (with the friends and new requests counted
  while connected), the sport queue and subject links; while connected the rows
  open Друзья, the Спорт tab and the Зачётка tab.
- `Работает и без подключения`: what talks to My ITMO and BARS directly.
- `Что хранит сервер` (`Что будет хранить сервер` before connecting):
  `ServicesStoredData`, which mirrors the Backend tables, and `Код сервера на
  GitHub`; the token note under it.
- `Кто видит`: the audiences below while connected, `Только вы` otherwise.
- `БАРС`: connected (BARS has answered for this account, `bars_marks_enabled`
  set), the session ended (`BarsLoginPrompt` is not `NONE`) with `Войти`, or not
  connected with `Подключить`; both open the BARS sign-in.
- `Устройства`: `Подключить устройство` ([web sign-in](features/web-login.md)) and
  `Подключённые устройства`; both need the connection and say so without it.
- `Удалить аккаунт ITMO.Widgets` (`Всё с сервера, без возврата`) is shown with
  the switch off too, since an account may remain from an earlier connection,
  and opens `<WIDGETS_BASE_URL>/delete-account` in the browser. Deletion itself is
  a request handled by hand (Backend `docs/ops/account-deletion.md`); the app has
  no deletion screen.
- `Выйти` remains an account action in the profile and requires confirmation.
- ITMO.ID login and refresh-token entry exist only on the signed-out screen.
  Tokens are not editable settings.

## Privacy and social access

The audiences are `Кто видит` on «Подключение и данные», each a menu of the three
choices. Connecting reloads them from Backend; the loading and explicit retries
(`Сервер не ответил`, `Повторить`) remain visible for at least 300 ms to avoid a
flash on fast connections. Requests start immediately; slower requests do not
incur an additional delay. Disconnecting forgets them at once.

- Name, group, and ISU are always visible to authenticated users. The application
  does not offer controls that imply these official identity fields can be hidden.
- `Расписание` and `Спорт` are independent audience choices:
  `Все`, `Друзья` (the default for new users), and `Никто`.
- `Список друзей` uses the same three audiences and defaults to `Все`
  for existing and new accounts. Schedule and sport defaults are unchanged.
- `Все` allows any authenticated application user, regardless of that viewer's
  own privacy choices. `Друзья` requires mutual friendship. `Никто` allows only
  the owner. There is no reciprocal sharing requirement.
- Existing enabled legacy settings become `Друзья`; previously disabled settings
  remain `Никто`. Upgrading must never silently broaden an existing audience.
- Backend, not Android, enforces the owner audience and friendship requirements
  (decision [0004](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0004-privacy-audiences.md)). Blocking is not
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
- There is no review-visibility setting: the author of a teacher review
  chooses `Анонимно` per review in the editor
  ([teacher reviews](features/reviews.md#editor)).

## Widgets gallery

`Виджеты` (spec C4, `SettingsPage.WIDGETS`; the names of the old pages
`COMPACT_SCHEDULE_WIDGET`, `FULL_SCHEDULE_WIDGET`, `QR_WIDGET` and
`SCHEDULE_WIDGETS` open it too). The subtitle counts the placed widgets
(`2 на главном экране`, iOS `на экране «Домой»`) once there are any. A card per
format shows the real widget on the sample Monday at 12:22
(`SchedulePreviewScenario`, `SportWidgetSamples`; never the user's data) on a
wallpaper, its name and what it shows, `Добавить` (Android pins it through the
launcher, `WidgetPinRequester`) or `на экране`, and the settings button. iOS
cannot place a widget from the app: it shows `Как добавить` and, below the
cards, the Lock Screen widgets and the `Пропуск` Control. A format's settings
open in a sheet with its preview again and `Пример на 12:22, настройки
применятся ко всем таким виджетам`; every change applies at once to all placed
widgets of the format. The widgets of the UX overhaul (`Пара`, `Расписание на день`, `Спорт`) carry a `Бета` pill
while they are refined (`WidgetCard.beta`); the classic `Список пар` and `Пропуск` do not. Below the cards: `Виджеты в
цвет темы`.

| Format | Settings | Defaults |
|---|---|---|
| `Пара` (W1, next lesson) | `Следующая пара заранее`, `Размер текста` | early switch on; `Обычный` |
| `Расписание на день` (W2) | `Скрывать прошедшие пары`, `Показывать преподавателя`, `Завтрашние пары вечером`, `Размер текста` | past lessons shown; teacher shown; tomorrow on; `Обычный` |
| `Список пар` (the classic list of 2.2) | the same rows and keys as `Расписание на день`, with `Эти настройки общие с «Расписанием на день».` | as `Расписание на день` |
| `Спорт` (W3) | none | |
| `Пропуск` | [QR widget](#qr-widget) | |

- Each format keeps its own keys: `compact_widget_next_lesson_early`,
  `compact_widget_text_size`, `full_widget_hide_teacher` (`Показывать
  преподавателя` is its inverse), `full_widget_hide_past`,
  `full_widget_show_tomorrow` (absent now reads as on), `full_widget_text_size`.
  Unscoped 2.2 keys are read only as defaults.
- The next-lesson widget names no teacher; `compact_widget_hide_teacher` stays
  for the first-run flow only.
- `Следующая пара заранее` moves the next-lesson widget on 15 minutes before
  the current lesson ends. `Завтрашние пары вечером` shows tomorrow on the day
  widget once today's last lesson ended; off, today stays with everything past.
- `Размер текста` (`Обычный`, `Крупный`, `Очень крупный`) scales every text of
  the format's widget by 1, 1.2 or 1.4: widgets cannot follow the app's
  typography.
- The schedule-level `Автозапись на спорт` still controls the queue rows of the
  app and both schedule widgets; it is not a format setting.

## QR widget

- The settings of the `Пропуск` card of the [gallery](#widgets-gallery). Its
  live preview immediately reflects colors, spoiler, animation, and custom-image
  changes. Tap the preview to reveal/hide the example code; changing animation
  plays it once. Motion stops when the screen is no longer visible.
- The preview uses the real widget's bitmap and animation renderers, but contains
  only a clearly labelled sample payload, never an active access pass. No network
  requests or widget registration are needed.
- Sample images warm up when the gallery opens and are reused from a bounded,
  palette-aware cache.
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
- `Добавить в шторку` (`Плитка «QR-пропуск» в быстрых настройках`) is the last
  row of the sheet, in a group of its own, on Android 13+ while the device flag
  `qr_tile_added` is off. It shows the system dialog
  (`StatusBarManager.requestAddTileService`). Added: the flag is set and the row
  leaves. Already added: the flag is set and a
  snackbar says `Плитка уже в шторке`. Declined or a request in progress:
  nothing. Other errors: `Не удалось добавить плитку`. The flag belongs to the
  device and survives sign-out; removing the tile from the shade clears it and
  brings the row back.

### iOS

The iOS `Пропуск` sheet lists `Динамические цвета` and `Скрывать QR-код за
спойлером` without the animation and the custom image rows. The
iOS widget follows both switches, and a change reaches placed widgets at once:
without the spoiler the code shows straight away; with dynamic colours the
code and the spoiler take the app's colours for the widget's theme, dark on
light in both, without them black on white. The code fades in instead of the
circle, which iOS cannot draw ([degradations](ios.md#degradations)). A custom
spoiler image is v2.4.

## Schedule

- The first row, `Уведомления об изменениях`, opens «Уведомления», where the
  schedule change check has its switch ([notifications](#notifications)).
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
  ([calendar](features/calendar.md)); there is no calendar choice and
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
  opens the export sheet ([`.ics` export](features/calendar.md#ics-export)).
- The page is an offline settings category with three untitled groups: the
  link to «Уведомления», the auto-sign switch with a
  footer that explains the user-services requirement and that pending entries
  are not confirmed bookings, then the calendar group without a footer:
  `Синхронизация с календарём` (`Пары на 4 недели вперёд в системном календаре
  телефона.` — the phone's own calendar app; Google and Yandex Calendar do not
  show device calendars) and `Выгрузить в .ics`. An untitled group after another
  one keeps the group gap
  (`design_spacing_group`) above its card.

### iOS

The iOS schedule page has the same rows. `Синхронизация с календарём` asks
for full calendar access (EventKit) once; after a refusal for good it shows
`Доступ к календарю` with `Открыть настройки`, which opens the app's page in
Settings ([calendar](features/calendar.md#ios)). `Выгрузить в .ics` shares the
file through the share sheet.

## Notifications

«Уведомления» (`AppRoutes.Settings` `NOTIFICATIONS`; the old `RECORDBOOK` key
opens it) is the one home of notification settings (DECISIONS.md, Screens):
`NotificationSettingsViewModel` (`:shared:feature-settings`,
`presentation/notifications`) under a Compose and a SwiftUI screen. The subtitle
is `включено N из M` (the types that can arrive and are on: the checks, and the
pushes while connected) or `запрещены в системе`.

- While the system denies notifications, a gate (`Уведомления запрещены`,
  `Разрешить`: the permission dialog while it can still appear, else the app's
  system page) stands first; the settings below stay, dimmed. Otherwise, on
  Android, while the system channel of a type that is on here is off, one card
  (`Часть уведомлений выключена`, `Открыть настройки`: the app's notification
  page) stands first instead.
- Every type is one row with its switch: a name and no description, except
  `Новые устройства` (`Вход на сайт или в агент`).
- `Баллы` turns the background mark check ([mark tracking](features/marks-tracking.md))
  on or off as a whole; while on, its sources follow under it. None needs
  `Подключение к ITMO.Widgets`: the check talks only to My ITMO, BARS and public
  Google Sheets.

| Source | Key | Default |
|---|---|---|
| `My ITMO` | `myitmo_marks_enabled` | on (absent means on); a device setting that survives sign-out |
| `БАРС` | `bars_marks_enabled` | absent: the row is hidden. The first successful BARS answer of the account (sign-in, BARS connected in the recordbook or the background read) stores on; cleared with the BARS session on sign-out |
| `Таблицы преподавателей` | `sheet_marks_enabled` | on (absent means on); always shown, a device setting that survives sign-out |

- `Баллы` off stores off in every source; on stores on in My ITMO and the sheets,
  and in BARS once BARS has answered. `БАРС` also carries the `Войдите в БАРС`
  reminder; switching it off withdraws it.
- Switching a source off forgets its snapshot and stops its part of the check;
  the unread subjects stay. The work is cancelled only when all three are off.
  Switching on schedules the work, and the first check of that source only takes
  a snapshot. Sheets off keeps the totals of the connected sheets, which the
  subject pages show, and untracks every connection, so the first background read
  after switching on only takes a baseline
  ([sheet scores](features/sheet-scores.md#background-check-of-sheets)).
- `Изменения расписания` turns on the background check of the own schedule
  for changes, its notifications, the bell's events and the marks on lessons
  ([schedule changes](features/schedule-changes.md)). It is enabled by
  default and stored as `schedule_changes_enabled` (absent means on). It does
  not need `Подключение к ITMO.Widgets`: the check talks only to My ITMO.
  Switching it off cancels the background work and forgets the snapshot; the
  history of changes stays. Switching it on schedules the work again, and the
  first check only takes a new snapshot.
- Turning a check on without the permission asks for it as the gate does; the
  switch stays on whatever the answer is.
- The pushes of ITMO.Widgets (`Спорт`, `Друзья`, `Новые устройства`) have the
  app's switch per type, stored in `PushCategoryPreferences` by channel
  (`sport_notifications_enabled`, `friends_notifications_enabled`,
  `connections_notifications_enabled`; absent means on; a device setting that
  survives sign-out). The device registration sends the switched-off ones to
  Backend (`mutedPushCategories`), which stops sending them to this device; the
  device also drops whatever still arrives of them: `FcmPayloadDispatcher` on
  Android, the notification service on iOS
  ([notifications](features/notifications.md#token-sync)). The sport queue
  pushes still arrive and book while `Спорт` is off; Android hides them, iOS
  shows them until the filtering entitlement exists. Turning a push on without the permission asks for it;
  no background work hint. Without the connection one row
  `Нужно подключение к ITMO.Widgets` opens «Подключение и данные» and the push
  switches are disabled.
- `Работа в фоне` ([background work](#background-work)) appears while a check is
  on and the system restricts the app.
- `Звук и вибрация` opens the app's notification settings of the system.

### iOS

The same page; the checks run as steps of the app's one refresh task instead of
WorkManager work ([mark tracking](features/marks-tracking.md#ios)), the BARS and
sheets sources only where `PlatformCapabilities.marks` holds (marks absent: the
`Баллы` block is absent). The background work row is `Обновление контента`
(Background App Refresh), shown while it is off for the app, and opens the app's
page in Settings. iOS never asked: no gate, the first check turned on asks.

## Background work

On some devices background checks work only when the app may run without
battery restrictions: on Xiaomi (MIUI, HyperOS) the default `Умный режим`
leaves a backgrounded app without network.

- The row `Работа в фоне` (`Разрешите работу без ограничений.`, an action row
  with `ic_open_in_new`; the whole row is the button) sits on «Уведомления»
  while a check is on and
  `PowerManager.isIgnoringBatteryOptimizations` is false
  (`BackgroundWorkAccess` in `feature/settings/domain`, implemented by
  `AndroidBackgroundWorkAccess` in `data`). The state is read when the page is created and on
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
- Turning on `Изменения расписания` or a source of `Баллы` while the app is
  restricted shows a dialog once per device: the title `Работа в фоне`,
  the text
  `Чтобы проверки приходили вовремя, разрешите приложению работу без ограничений.`,
  `Разрешить` (opens the same page) and `Не сейчас`. The fact is stored as
  `background_work_hint_shown` in DataStore and survives sign-out.
- On MIUI the `Без ограничений` mode may leave
  `isIgnoringBatteryOptimizations` unchanged; the row then stays.

## Notification channels

FCM categories are `Спорт: автозапись` (`sport`), `Друзья` (`friends`) and
`Подключения устройств` (`connections`); the local schedule check posts to
`Изменения расписания` (`schedule_changes`) and the mark check to `Оценки`
(`marks`). All five are at default importance. Android controls permission,
sound and category visibility. The switches of «Уведомления» switch the checks
themselves and, for the pushes, whether the device shows them; they never
change a channel.
Disabled notification permission suppresses only the visual alert, not sport
automation already enabled by the user or the background checks. Disabling
user services suppresses FCM actions and attempts to unregister this device.

## Sport

- `Показывать фильтр по преподавателю` shows the teacher in the more-filters
  sheet of `Запись`.
- `Показывать фильтр по времени` shows the part of the day there.
- Sections, building, `Есть места`, `Мои секции` and friends remain ordinary
  sport-screen filters, separate from the schedule display preference above.
  `Мои секции` keeps the lessons of the user's sections, which live on
  Backend ([sport](features/sport.md#sections)).

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
  the write. The screen lists the entries newest first (time, level, tag,
  message; an entry with a stack trace opens it on a tap), copies the whole
  journal to the clipboard (`Журнал скопирован` below Android 13, where the
  system does not confirm a copy itself) or clears it after `Очистить
  журнал?`; copy and clear wait for the first entry. Nothing is uploaded. The
  row shows the entry count.
- `Повторить первоначальную настройку` (`Виджеты, подключение и уведомления`)
  clears the first-run flag, closes the settings overlay and returns the root
  graph to the first-run flow with an empty back stack. Nothing else is reset:
  the opt-in, pinned widgets and preferences stay as they are. See
  [features/onboarding.md](features/onboarding.md).
- `Политика конфиденциальности` (`ic_open_in_new`) opens
  `<WIDGETS_BASE_URL>/privacy.html` in the browser.
- `Лицензии` opens a page with the source of the campus plans (`Планы корпусов`, `Университет ИТМО`: plans, routes,
  style and icons from the ITMO Campus app) and the licences of MapLibre Native (BSD 2-Clause, its full text) and of
  the map's Noto Sans labels (SIL OFL 1.1).
- `Версия` displays the application version.
- The group's footer is `Неофициальное приложение. Не связано с Университетом
  ИТМО.`; the sign-in screen shows the same line under its buttons.

## Where it lives

The settings domain, presentation (`SettingsViewModel` with one page provider
per `SettingsPage`, `DiagnosticsViewModel`, `IcsExportViewModel`), data and
Compose UI (`SettingsScreen` with its dialogs, `DiagnosticsScreen`,
`IcsExportSheetContent`) are in `:shared:feature-settings`, packages
`dev.alllexey.itmowidgets.feature.settings.*`, with Koin modules. The page
model (`SettingItem`, `SettingSection`, `SettingsPage`, `SettingRowId`,
`SettingsEvent`) is platform-neutral: `UiText` and `AppIcon`, never Android,
`R`, Compose or a resource id (`SettingsRulesTest`). iOS renders the same
pages and the journal as SwiftUI forms over the shared view models.
«Подключение и данные» (`ConnectionViewModel`, `ConnectionScreen`) and
«Уведомления» (`NotificationSettingsViewModel`, `NotificationSettingsScreen`)
are screens of their own in the same module, drawn on the kit; iOS has
`ConnectionDataScreen` and `NotificationSettingsScreen`. The hosts route their
keys before a `SettingsViewModel` is made (`SettingsEntries.kt` with
`AccessEntries.kt`, `Routes+Settings.swift`).

Android keeps the hosts in `app/`: `SettingsEntries` (permissions, system
pages, the gallery's widget previews and pinning), `DiagnosticsFragment` (the
clipboard) and `IcsExportBottomSheet` (the date range picker, the share and
view intents), plus `AndroidBackgroundWorkAccess`,
`AndroidQuickSettingsTileAccess` and `CustomSpoilerRepositoryImpl`. Their looks
are the `SettingsScreenshotTest` baselines of `:shared:feature-settings` in
four appearances (the gallery's with the real widgets in `:app`'s
`WidgetsGalleryScreenshotTest`); `SettingsNavigationTest` covers the host flows
on a device.

## Debug-only controls

Debug builds may additionally expose the academic-date override, synthetic sport
scores, sport lesson templates, development-service diagnostics,
`Проверить изменения расписания`, a one-off schedule change check,
`Проверить оценки`, a one-off mark check, and `Проверить продление БАРС`, a
read-only probe of the BARS cookie renewal that only writes its outcome to
logcat. These controls never appear in release builds and never change release
behavior.
