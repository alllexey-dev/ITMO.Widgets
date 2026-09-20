# Widgets

Three home-screen widgets: the single-lesson schedule widget, the day schedule
widget and the QR pass widget. They render through WorkManager workers, keep
their own launcher-adapted palette and never register a widget host from inside
the app.

## Schedule widgets

- `ScheduleWidgetDataProvider` loads only the own academic range. With both the
  `Автозапись на спорт` preference and services enabled it also refreshes the
  pending projection and reads its completed snapshot; this API has no synthetic
  empty emission and no implicit network request.
- `ScheduleWidgetSelector` builds a widget-only timeline (never synthetic
  academic lessons). Today/tomorrow selection, teacher visibility and smart
  refresh boundaries include pending times. Every pending row carries a
  persisted `WAITING` or `PREDICTED` marker rendered with a short label and an
  outlined indicator; it never becomes a confirmed lesson.
- Queue-enabled widgets request the next refresh within seven minutes or at the
  next lesson boundary. The atomic presentation snapshot also holds an
  official-only fallback, so dropping optional rows restores the correct next
  lesson and remaining count. Pending data expires at its earliest start or after
  seven minutes; snapshot reads re-check settings and authentication.
- The snapshot store is a shared session cleaner; cleanup invalidates in-flight
  worker tickets before clearing disk so an old account's snapshot cannot be
  written into a new session.
- Successful sport actions, preference changes and the services gate enqueue a
  forced schedule-widget update through `WidgetRefreshCoordinator`; QR widgets
  are not touched. A sport action also enqueues a follow-up update 1 s later,
  because MyITMO can answer the first fetch with the schedule from before the
  change. A refresh that fails or falls back to the previous snapshot leaves a
  `ScheduleWidget` warning in the diagnostics journal.
- Compact and full widget settings are independent typed models in
  `core/settings`. Each has its own teacher visibility. Early switching belongs
  only to compact; hiding past lessons and tomorrow selection belong only to full.
  The full list uses actual lesson ends, not the compact selection. Shared work
  refreshes at the earliest start/end or compact early-switch boundary.
- `AppSettingsStorage` reads both formats atomically. New format-specific keys
  override read-only shared fallback keys without changing the other format.
- Smart update scheduling and the single visual style are fixed; there are no
  selectors. Completed rows dim uniformly; the day widget centres its
  `Сегодня`/`Завтра` header.
- Text size is a per-format choice (`WidgetTextSize`: 1, 1.2 or 1.4). The
  snapshot carries both formats' sizes (nullable, so older snapshots still
  deserialise as normal) and `ScheduleWidgetRenderer` applies them with
  `setTextViewTextSize` on every render, so a recycled launcher view never keeps
  a previous size. The base sizes live in the renderer next to the layouts; the
  list row's time column grows with its text instead of a fixed 44 dp.
- Provider descriptors for Android 12+ declare `targetCellWidth/Height` for the
  default span and keep `minWidth` at 180 dp with explicit resize bounds, the
  smallest span the layouts still read in. A launcher scales the whole widget
  down when its cells are smaller than the declared minimum; the descriptors
  never ask for more than the layouts need.

## QR widget

Shows the building pass, hidden behind a spoiler by default, with dynamic
colours and an opening animation (circle, fade or none). A custom spoiler image
is picked through the photo picker, cropped square and stored as a bounded
420 × 420 PNG; saving is atomic and refreshes widgets only after success.
Expiry is passive: an expired code stops being emitted (known gap).

## Previews in settings

Separate compact and full settings pages each show only their own live preview,
with a saved sample-time selector and no format pager. Previews use fixed sample
data and the real renderers: `ScheduleWidgetRenderer` and `ScheduleListRowRenderer` for schedule pages,
the production bitmap and animation renderers with a labelled sample payload for
QR. `core/ui/widget.WidgetPreviewFactory` is the UI contract; the app-level
`DefaultWidgetPreviewFactory` composes feature implementations without
cross-feature imports. `QrPreviewBitmapCache` keeps at most four bitmap pairs
keyed by palette, spoiler revision and invalidation generation; rendering stays
off the main thread. Previews never register a host, create PendingIntents,
touch the network or write snapshots.

## Sign-out

`AndroidSessionLifecycleEffects` cancels widget work before a session change and
renders signed-out placeholders afterwards.
