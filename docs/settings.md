# ITMO.Widgets v2.1 settings contract

This document defines the complete user-facing settings surface for Android
v2.1. Options not listed here are not settings for this release.

## Navigation and density

The settings root is a compact catalogue, not a scrolling list of every switch:

- `Сервисы и доступ`: user services, privacy, and the Android notifications action.
- `Виджеты`: schedule widgets and the QR widget.
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

- `Пользовательские сервисы` enables Backend-dependent features. It is disabled
  by default and requires explicit consent.
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
- `Делиться расписанием с друзьями` is a separate toggle, enabled by default.
- `Делиться спортом с друзьями` is a separate toggle, enabled by default.
- Both sharing controls apply only to mutual, non-blocked friends.
- Sharing is reciprocal for each data type. A user who disables schedule sharing
  cannot view friends' schedules; a user who disables sport sharing cannot view
  friends' sport data.
- Backend, not Android, enforces sharing, friendship, and blocking rules.
- `Кто может отправлять заявки в друзья` offers everyone, friends of friends, or
  nobody. Everyone is the default.
- `Заблокированные пользователи` opens the block-management screen.
- Public-review visibility is not shown until reviews ship in v2.2.

## Schedule widgets

- A pinned live preview sits above the controls. Tap or swipe between `Ближайшая`
  and `На день`; the preview uses the real widget layouts and selection rules.
- The sample time selector switches between 12:50 (during lessons) and 18:00
  (after lessons), so early selection, hidden past lessons, and tomorrow's list
  can be checked immediately. The page and sample time survive view recreation.
- Examples are fixed, local demonstration data, not the user's schedule. They do
  not change academic time, widget caches, or installed widget instances.
- Completed rows dim uniformly, including both time labels. Empty/status messages
  center the title and hint as one block; normal lesson rows retain their alignment.
- The day widget centers its `Сегодня`/`Завтра` date header, including the launcher
  preview. Successful sport sign-in and cancellation enqueue a fresh snapshot for
  both schedule widgets without waiting for the next automatic update.
- Smart update scheduling is always enabled and is not configurable.
- Both schedule widgets use the single established visual style. Style selectors
  are not shown.
- `Следующая пара заранее` controls whether the next lesson appears 15 minutes
  before the current lesson ends. It is enabled by default.
- `Скрывать преподавателя` is disabled by default.
- `Скрывать прошедшие занятия` is disabled by default.
- `Показывать расписание на завтра`, when today is finished, is disabled by
  default.

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

## Sport

- `Показывать фильтр по преподавателю` controls the optional teacher selector.
- `Показывать фильтр по времени` controls the optional time selector.
- Sport type, building, availability, auto-sign, and friends remain ordinary
  sport-screen filters, separate from the schedule display preference above.

## Maps

There is no map-provider setting. Every building action launches a generic
system `geo:` intent and lets Android resolve the installed mapping application.

## Maintenance and diagnostics

- `Обновить все виджеты` refreshes QR and schedule widgets.
- `Последнее обновление` displays the last successful widget refresh.
- `Журнал ошибок` opens sanitized local diagnostics without credentials or
  tokens.
- `Повторить первоначальную настройку` starts onboarding again.
- `Версия` displays the application version.

## Debug-only controls

Debug builds may additionally expose the academic-date override, synthetic sport
scores, sport lesson templates, and development-service diagnostics. These
controls never appear in release builds and never change release behavior.

## Deferred beyond v2.1

Google Sheet mappings, schedule-change notifications, calendar synchronization,
community resources, and teacher-review settings belong to v2.2.
