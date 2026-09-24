# Changelog

Reference documents in `docs/` describe the current state; this file records
what changed and when. Unreleased entries describe local development, not a
publication or deployment.

## 2.2 — development

### 2026-09-24

- A link is shared with one schedule flow of the subject instead of the fixed
  `Группа` and `Поток`: `Кто видит` lists `Только я`, every flow of the viewer
  by nesting (`ФИЗ ПИИКТ 3`, `3.2`, `3.2.1`) with its kind of classes, and
  `Все`. Flow links are labelled with the flow name. Needs Core and Backend
  `1.7.0-SNAPSHOT` with `LinkVisibility` `PRIVATE`, `FLOW`, `ALL` and `flowId`.

### 2026-09-23

- Trimmed app copy: no pull-to-refresh or tap instructions, no settings
  descriptions and footers that repeat their titles, shorter recordbook source,
  diagnostics, consent, privacy and auto-sign texts, title-only empty states
  where the title is enough.
- One name per thing: `My ITMO` for the university site and
  `Подключение к ITMO.Widgets` for the server opt-in.
- Builds as `2.2-SNAPSHOT` on Core `1.7.0-SNAPSHOT`; Backend `1.7.0-SNAPSHOT`
  with the `V4__subject_links.sql` schema is required for links.
- Recordbook rows are compact: the points with a thin bar, or a grade badge once
  the result is final, instead of rings. `Требуют внимания` lists no-shows,
  subjects with a known control under its minimum (from MyITMO or BARS controls
  already loaded), failed subjects and PE short of 100 sport points in the last
  28 days of its period or after it. The summary appears only in the session;
  the source info button is gone.
- The subject is one page without tabs: the name in the toolbar, the ITMO grade
  scale (5A/4B/4C/3D/3E, a plain credit at 60) with a hint to the next grade,
  link chips, chats, controls grouped by tree or by numbered names with the sum
  of known scores, teachers, and the two nearest lessons with `Все пары`.
  A control shows its minimum only when it is not met.
- Subject links: a category and a visibility (`Только я`, `Группа`, `Поток`,
  `Все`), chips on the subject page, a sheet with every link, chats and links
  from past years, an editor that pastes the copied link and guesses its
  category, an actions sheet (open, add to own, pin, edit, delete, report) and
  +1/−1 votes. Without the connection links stay private on the device and are
  uploaded on the next refresh with it.
- Shared `LinkOpener` and link texts in `core/ui`; the BARS repository keeps
  the controls it has read; the current sport period carries its end date.
- Instrumentation screenshots are exported before the test APK is uninstalled.

## 2.1.1 — 2026-09-21

### 2026-09-21
- Sign-in follows ITMO.ID to any https page, so VK and the other providers on
  the ITMO.ID page work inside the app; the token is still accepted only on
  `https://my.itmo.ru/login/callback`. The BARS sign-in does the same
  (`core/util/HttpsNavigationPolicy`).
- A user with several groups is shown with the highest-course one everywhere
  (`UserSummary.primaryGroup()`): feed, lesson sheet, profile, `Профиль`.
- Screens keep their content while they refresh instead of replacing it with
  an indicator: both sport tabs carry `refreshing` inside `Content` and a
  failed refresh keeps the last snapshot with a snackbar; the schedule renders
  its memory cache in the first frame (`ScheduleRepository.peekScheduleForRange`);
  the QR pass, the recordbook and its subject hub, a public profile and another
  user's friends open from the last answer (`RecordbookRepository` memory
  cache, `SocialRepository.cachedProfile` / `cachedUserFriends`, all cleared on
  sign-out). `UserFriendsFragment` no longer reloads on every start.
- Automatic refreshes are silent: the first load of a screen, a stale resume,
  a period change and the BARS switch no longer show the pull-to-refresh
  indicator over the content (or after the skeleton); only a pull or
  `Повторить` does. The sport catalogue shows its filters and week calendar
  before the lessons answer (`SportSignUiState.Content.initialLoading`).
- The subject hub shows its `Баллы` / `Расписание` tab strip at once for a
  current period instead of after the subject answers.
- A first load without any cache shows a skeleton (`core/ui/SkeletonView`,
  `SkeletonListAdapter` for the sport catalogue) instead of a circular
  indicator on the feed, schedule, both sport tabs, recordbook, subject hub,
  friends, people search, public profile, another user's friends and sport.
- Version `2.1.1` (version code 5); no Core, Backend or wire change.

## 2.1 — 2026-09-21

### 2026-09-21
- Version `2.1` (version code 4) on Core `1.2.0` and MyItmoApi `1.8.1`, both on
  Maven Central. Release signing reads `keystore.properties` (ignored; see
  `keystore.properties.example`), so `assembleRelease` produces a signed APK.

### 2026-09-20
- First-run flow: the QR widget step offers `Изображение спойлера`, the same
  photo picker and crop screen as settings, with `Выбрать другое` / `Вернуть
  стандартное` once an image is stored. The crop screen and the spoiler image
  contract moved to `core` (`core/ui/spoiler`, `core/settings`), and the
  repository now refreshes the widgets itself instead of the settings ViewModel.
- Home tab: a feed of cards instead of the placeholder. `Сегодня`/`Завтра` with
  the current lesson marked `сейчас`, the rest of the day, pending sport rows
  and a count of finished lessons; `Спорт` with the score progress and own
  queues; `Заявки в друзья`; dismissible hints for a missing widget, disabled
  notifications and disabled user services. Pull-to-refresh asks every source
  at once and a partial failure is one snackbar. Both FABs stay.
  `Настройки → Главный экран` hides card kinds (`home_hidden_cards`).
- The current lesson on the home card is marked by its accented time, the
  `сейчас` badge and a progress line instead of a filled row.
- Widget previews in the launcher picker match the current widgets: rebuilt
  Android 12+ preview layouts for all three widgets (the QR widget had none) and
  preview images rendered from the real widget renderers, with a night variant.
- The second fetch after a sport action (bookings, own schedule, schedule widgets)
  now runs 1 s later instead of 3 s and also reloads the sport catalogue, so a
  lesson that was full no longer stays full after cancelling.
- Every details sheet (lesson, pending sport, sport lesson) now starts with the
  same header: title, kind, the date with the time range and duration, teacher,
  place, `Открыть на карте`. A pending sport row on the home feed or in the
  schedule opens the sport tab's own sheet with the queue position, history and
  `Отменить`, and so does a booked sport lesson (matched by date and start, a confirmed
  booking before a queue for the same slot, then by section name);
  the sport data loads on first use exactly as the sport tab loads it; the schedule's simpler sheet remains only when the sport data has
  nothing about the queue. The QR pass screen shows the code at up to 300 dp
  instead of the full width.
- Subject hub in `Учёба`: a swipeable `Расписание` tab next to `Баллы` with the subject's upcoming lessons
  (next four weeks) with type, room and building, its teachers with the lesson
  types they run, and the LMS link when MyITMO sends one. A discipline whose
  id matches the schedule binds silently; a name-only match is offered as
  `Связать`, several matches ask which one, and confirmed links persist until
  sign-out.
- Lesson details: every schedule card opens a sheet with the subject, type and
  format, time, teacher, room and building, Zoom link and note; `Открыть на
  карте` hands the building to any map app through a `geo:` URI, using a
  curated directory of ITMO buildings with verified coordinates.
- Lessons with a meeting link get `Открыть видеозвонок` (no "Zoom" wording, the
  link itself is not shown) with the Material `videocam` icon, and schedule
  cards mark such lessons with the same icon. Pending sport rows open their own sheet with the status
  explanation, time, place and `Открыть в спорте`.
- `Друзья на паре` inside the sheet: the viewer's friends who attend the same
  lesson and share their schedule, from Backend `GET
  /api/schedule/lessons/{pairId}/friends?date=` (requires Core 1.2.0-SNAPSHOT
  with `friendsOnLesson`). Hidden without the opt-in; a row opens the profile.

### 2026-09-19
- Schedule widgets get a `Размер текста` choice per format (`Обычный`,
  `Крупный`, `Очень крупный`) on their settings pages and on the first-run
  widget steps; the preview follows it.
- After a sport sign-up or cancellation the app fetches the schedule and the
  bookings a second time 3 s later, outside the screen that asked, because
  MyITMO often answers the first fetch with the state from before the change.
- Schedule widget descriptors on Android 12+ ask only for the span the layouts
  need (180 dp minimum, explicit resize bounds, default span through
  `targetCellWidth/Height`), so a launcher with smaller cells has no reason to
  scale the widget down.
- Schedule widgets refresh a second time 3 s after a sport sign-up or
  cancellation, so a MyITMO reply that predates the change no longer leaves
  the widget stale until the next automatic update; failed widget refreshes
  are recorded in the diagnostics journal.
- Instrumentation runs skip the 45 s activity-lifecycle timeout that every
  `launchActivityForResult` close used to wait for.

### 2026-09-17
- First-run flow after sign-in: one screen per widget with its live preview,
  its own settings rows and a pin straight to the launcher; the ITMO.Widgets
  opt-in as a switch with the stored data named in full; the notification
  permission on its own step behind the opt-in. No summary screen. Every
  choice is stored when it is made, so skipping leaves a consistent app. The
  flow is a root destination gated on a per-installation flag that survives
  sign-out; `Повторить первоначальную настройку` in maintenance replays it.
- The single-lesson widget preview is now as tall as the widget; only the day
  list keeps the bounded preview area.
- Signed-out welcome now names what the app does in three lines instead of one
  sentence; the expired-session notice appears only when the session expired.

- People search shows Cyrillic names: MyItmoApi 1.8.1 sends `Accept-Language: ru`
  with every MyITMO request (without it the directory transliterates).

- Friend selector: recent chips no longer jump to the front on each tap. Their
  opening order and pending selection survive recreation; selection updates only
  its markers, and history changes after confirmation for the next opening.

### 2026-09-17
- Identity upload retries through WorkManager after a failure; an empty name
  from Backend renders as `Пользователь ИСУ N` instead of raw text.

### 2026-09-16
- Roadmap: BARS mark tracking and notifications added as Stages 37–38 inside
  v2.2; the QR quick-settings tile and static app shortcuts added to the home-feed
  stages; later stages renumbered to 39–45.
- Roadmap: Stages 11–45 now name files in the package-by-feature layout
  (`feature/<name>/{ui,presentation,domain,data,work}`, `app/`, `core/`) instead
  of the pre-refactor global `data`/`domain` packages; contextual destinations
  point at the overlay graph; Backend migrations renumbered after the existing
  `V3`; Core commands use JDK 17.
- Local diagnostics journal: `core/diagnostics` records warnings, errors and
  uncaught crashes to a bounded file after redacting tokens; `Журнал ошибок`
  in maintenance lists, copies and clears it. Sync, FCM, push and update
  failures that used to go to logcat now land there.

- Home MyITMO FAB opens an origin-restricted embedded browser with history,
  retry and external-browser fallback; web session data is cleared on sign-out
  or native account replacement, without exposing native tokens to JavaScript.
  Both home FABs are icon-only, with localized accessibility descriptions.

- Home QR FAB opens a full-screen pass with cached loading, refresh/retry and
  active cache-expiry handling; widget fallback semantics are unchanged.

- Sport details now offer sign in/out and queue actions in addition to the
  unchanged card controls, with one-shot routing and deadline revalidation.

- Public profiles now open viewer-scoped friends lists; independent friends
  privacy defaults to ALL and supports FRIENDS / NOBODY. Loading, empty, denied,
  retry and refresh-failure states use the shared social row and state styles.
- Profile: notifications and version rows removed (both remain in settings);
  compact GitHub and Telegram buttons with logos above sign-out, Telegram via
  `tg://` deep link with a web fallback.

- Split compact and full schedule-widget settings into independent pages, previews
  and preference keys, preserving existing choices. Early switching is compact-only;
  past/tomorrow controls are full-only; each has separate teacher visibility. The
  full list no longer removes an ongoing lesson because compact switched early.
- Another user's sport no longer waits for the merged sport schedule; it resolves
  confirmed lessons against the raw catalog, so it loads before the sport tab
  was ever opened in the process.
- FCM restored: messaging service, token sync, `sport` and `friends` channels,
  serialized WorkManager processing, sport auto-sign booking from pushes,
  friendship notifications that open the profile. Delivery is guarded by the
  recipient ISU and a signed-in session.
- Documentation restructured: tracked `AGENTS.md`, `docs/README.md` index,
  per-feature documents, decision records, English everywhere except README.

### 2026-09-15
- Social layer: `SocialRepository`, friends and requests screen, people search
  through MyITMO with Backend lookup, public profiles with schedule and sport
  entry points, another user's schedule and sport screens.
- Profile tab: study group, friends card with request badge, privacy,
  notifications and version rows.
- Friend picker aligned with the design language: quiet rows, container
  selection, `Друзья / Все` toggle, profile on long-press.
- BARS overlay on the recordbook behind a chip, using MyItmoApi 1.8.0.
- Resource and naming clean-up: one file per resource, semantic colours, uniform
  layout and view ids, one voice in user-facing strings.
- Real sport venues kept out of the building filter categories.

### 2026-09-08 — 2026-09-09
- Sharing audiences (`Все / Друзья / Никто`) with viewer capabilities from Core.
- Schedule timeline markers aligned; pending sport rows in the own schedule and
  both schedule widgets behind a default-off setting.
- Unified design pass: shared card styles, state styles, refresh helper,
  preserved schedule range and scroll on refresh.
- Sport session cards and details redesigned; collapsing score card.

### 2026-09-07
- Recordbook redesigned with live sport progress for physical education.
- Contextual overlay navigation stack; profile and settings with live widget
  previews; authentication and sign-out.

### 2026-07 — 2026-08
- QR widget and schedule widgets on the refactored architecture.
- Sport sign double-tap guard; friend schedule disabled without services.
