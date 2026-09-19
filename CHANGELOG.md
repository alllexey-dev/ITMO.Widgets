# Changelog

Reference documents in `docs/` describe the current state; this file records
what changed and when. Dates are commit dates on `itmo-widgets-v2.1`.

## Unreleased (2.1-SNAPSHOT)

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
