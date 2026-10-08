# Widgets

Three home-screen widgets: the single-lesson schedule widget, the day schedule
widget and the QR pass widget. They render through WorkManager workers, keep
their own launcher-adapted palette and never register a widget host from inside
the app.

## Schedule widgets

The widgets stay RemoteViews in `:app`
(`feature/schedule/ui/widget`: `SingleLessonWidgetProvider`,
`DayScheduleWidgetProvider`, `ScheduleWidgetRenderer`,
`ScheduleListRowRenderer`; `feature/schedule/work`:
`ScheduleWidgetUpdateWorker`, `ScheduleWidgetRefreshReceiver`), read Koin
through `KoinStarter` and render what the shared selector and data provider of
`:shared:feature-schedule` compute ([schedule](schedule.md#modules)).

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
- The snapshot is kotlinx JSON in
  `noBackupFilesDir/widgets/schedule_snapshot.json`
  (`ScheduleWidgetSnapshotStoreImpl`, written through `AtomicTextFile`) with a
  top-level `formatVersion` 2, always written first; a file without it is a
  2.2 snapshot with the same keys and reads as before, and 2.2 ignores the
  marker. A file of another version reads as loading until the next refresh. The serial names are a
  contract shared with the [iOS timeline](#timeline-widgetkit).
- The snapshot store is a shared session cleaner; cleanup invalidates in-flight
  worker tickets before clearing disk so an old account's snapshot cannot be
  written into a new session.
- A tap opens the schedule at its reading place through the shared route queue
  of `MainActivity` ([navigation](../architecture.md#navigation)): Recents never
  repeats it and Back leads home.
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
- `WidgetSettingsPreferences` reads both formats atomically. New format-specific keys
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
- The day widget sends its rows inline with the widget update
  (`RemoteCollectionItems` through `core-remoteviews`, which falls back to an
  adapter below Android 12), with stable ids taken from each row's identity, so
  a refresh never shows rows of an older snapshot under a newer header.
  `ScheduleWidgetRemoteViewsService` stays declared and serves the same rows
  and ids to launchers that still hold the adapter intent of an earlier
  build.
- On Android 12+ both widgets follow the launcher's widget corner radius
  (`system_app_widget_background_radius` in
  `drawable-v31/widget_background_rounded.xml`, root `@android:id/background`
  with `clipToOutline`); older launchers keep 20 dp. Both keep a 1 dp outline.
- Provider descriptors for Android 12+ declare `targetCellWidth/Height` for the
  default span and keep `minWidth` at 180 dp with explicit resize bounds, the
  smallest span the layouts still read in. A launcher scales the whole widget
  down when its cells are smaller than the declared minimum; the descriptors
  never ask for more than the layouts need.

### Timeline (WidgetKit)

`ScheduleWidgetSelector.timeline` computes what the widgets show up to a given
instant (`ScheduleWidgetDataProvider.loadTimeline`; the iOS writer asks for
the end of tomorrow): an entry at the start and at every instant where the
snapshot can change (lesson starts and ends, the compact early switch,
midnights, pending sport starts), adjacent equal entries merged. Every entry
equals `select` at any instant of its interval. `ScheduleWidgetTimelineJson`
is the JSON contract, version 1: `{"version", "generatedAt", "validUntil",
"entries": [{"validFrom", "snapshot"}]}` with the snapshot in the keys of
`schedule_snapshot.json`, nulls omitted, defaults (and so `version`) always
written, ISO-8601 UTC instants. `version` stays for additive fields and goes
up for a rename or a removal; a reader rejects a higher one. The reference
fixture is `shared/feature-schedule/fixtures/schedule-widget-timeline-v1.json`
(`ScheduleWidgetTimelineJsonTest`). Android does not use the timeline: its
worker still renders the current snapshot.

### iOS

The lesson widget (small, medium, Lock Screen rectangular and inline) and the
day widget (medium, large) read the App Group file schedule-timeline-v1.json,
which the app's `ScheduleTimelineWriter` computes with
`ScheduleWidgetDataProvider.loadTimeline` to the end of tomorrow; WidgetKit switches between the precomputed entries, so
Swift takes no time decision. The app writes on sign-in and sign-out, widget
option changes, every return to the foreground, a changed cached schedule and
sport actions. Degradation: no seven-minute pending sport refresh. The day
widget shows the rows that fit, completed lessons leave first. In the demo
session the widgets show the demo state. The QR widget and the details:
[iOS app](../ios.md#widgets).

## QR widget

Shows the building pass, hidden behind a spoiler by default, with dynamic
colours and an opening animation (circle, fade or none). A custom spoiler image
is picked through the photo picker, cropped square and stored as a bounded
420 × 420 PNG; saving is atomic and refreshes widgets only after success.
`core/qr/CustomSpoilerManager` stores and reads that image for the settings
preview and the widget renderer.
Expiry is passive: an expired code stops being emitted (known gap).

The widget, its worker and its renderer stay in `:app`; the pass, the cache and
the per-widget reveal state (`qr_widget_state_<appWidgetId>`) are the shared
`QrCodeRepository` and `QrWidgetStateStore` of `:shared:feature-qr`, which the
widget reads from Koin through `KoinStarter`. The
widget and the pass screen share one repository and one cached pass; see
[QR pass](qr.md#data).

## Launcher picker previews

The picker shows the real widgets. On Android 12+ each descriptor names a
`previewLayout` (`widget_single_lesson_preview.xml`, `widget_lesson_list_preview.xml`,
`widget_qr_code_preview.xml`): static copies of the widget layouts filled with
the settings preview scenario (a current `Математический анализ` lesson, three
lessons and `На сегодня всё`, the sample pass). Older launchers get
`previewImage` PNGs under `drawable-420dpi` and `drawable-night-420dpi`, rendered
by the real widget renderers through the instrumentation class
`WidgetPreviewImageCapture` (run with `captureScreenshots=true`, pull
`widget-previews-light` and `-night` from the cache). The same class draws the
static layouts next to the real renders, so a drift between them is visible in
one glance. Regenerate both whenever a widget layout changes.

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
