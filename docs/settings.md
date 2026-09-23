# Settings contract

The complete user-facing settings surface of the application. An option not
listed here is not a setting. Screens are built declaratively; see the
"Settings as data" section of [`architecture.md`](architecture.md).

## Navigation and density

The settings root is a compact catalogue, not a scrolling list of every switch:

- `Сервисы и доступ`: user services, privacy, and the Android notifications action.
- `Виджеты`: `Компактное расписание`, `Полное расписание` and the QR widget.
- `Приложение`: schedule, sport, and maintenance.

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

## Home screen

- `Главный экран` lists one switch per feed card: `Расписание на сегодня`,
  `Спорт`, `Заявки в друзья`. A switched-off card leaves the feed
  at once; nothing else changes and no widget is refreshed.
- Stored as the string set `home_hidden_cards` (card kind names; absent means
  shown). The page is an offline category; its footer explains that hints on
  the home screen are closed with their own button and do not return.
- The hints themselves are not settings. Closed hints are remembered per
  installation in `home_dismissed_hints`; sign-out and `Повторить первоначальную
  настройку` leave them alone.

## Schedule

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
- The page is an offline settings category with one toggle. Its footer explains
  the user-services requirement and that pending entries are not confirmed bookings.

## Notification channels

`Уведомления` opens Android's application notification settings. FCM categories
are `Спорт: автозапись` (`sport`) and `Друзья` (`friends`), both at default
importance. Android controls permission, sound and category visibility; there are
no duplicate in-app switches. Disabled notification permission suppresses only
the visual alert, not sport automation already enabled by the user. Disabling
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
  has ended. Tokens are never editable settings or bundled credentials.
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
- `Версия` displays the application version.

## Debug-only controls

Debug builds may additionally expose the academic-date override, synthetic sport
scores, sport lesson templates, and development-service diagnostics. These
controls never appear in release builds and never change release behavior.

## Deferred beyond v2.1

Google Sheet mappings, schedule-change notifications, calendar synchronization,
community resources, teacher-review settings, the `Следить за оценками БАРС`
toggle, and the `Добавить в шторку` action belong to v2.2.
