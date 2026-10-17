# Widgets

Product behaviour: [Как это работает: виджеты](../how-it-works/widgets.md).

Three home-screen widgets (single lesson, day schedule, QR pass), rendered by
WorkManager workers; the app never registers a widget host.

## Schedule widgets

RemoteViews in `:app` (`feature/schedule/ui/widget`, `feature/schedule/work`:
`ScheduleWidgetRenderer`, `ScheduleWidgetUpdateWorker`), reading Koin through
`KoinStarter` ([schedule](schedule.md#modules)).

- `ScheduleWidgetDataProvider` loads only the own academic range. With
  `Автозапись на спорт` and services enabled it also refreshes the pending
  projection and reads its completed snapshot; no synthetic empty emission,
  no implicit network request.
- `ScheduleWidgetSelector` builds a widget-only timeline (never synthetic
  academic lessons); selection, teacher visibility and refresh boundaries
  include pending times. Pending rows carry a persisted `WAITING` or
  `PREDICTED` marker and never become confirmed lessons.
- Queue-enabled widgets refresh within 7 minutes (`PERIODIC_UPDATE_DELAY`)
  or at the next lesson boundary; pending data expires the same way, the
  snapshot keeps an official-only fallback, and reads re-check settings and
  authentication.
- Snapshot: kotlinx JSON in `noBackupFilesDir/widgets/schedule_snapshot.json`
  (`ScheduleWidgetSnapshotStoreImpl`, `AtomicTextFile`), top-level
  `formatVersion` 2 written first; no field = a 2.2 snapshot with the same
  keys; another version reads as loading until the next refresh. Serial names
  are a contract with the [iOS timeline](#timeline-widgetkit).
- The snapshot store is a session cleaner: cleanup invalidates in-flight
  worker tickets before clearing disk, so an old account's snapshot cannot
  land in a new session. `AndroidSessionLifecycleEffects` cancels widget work
  before a session change and renders signed-out placeholders after.
- A tap opens the schedule through the route queue of `MainActivity`
  ([navigation](../architecture.md#navigation)): Recents never repeats it.
- Sport actions, preference changes and the services gate force a schedule
  widget update through `WidgetRefreshCoordinator` (QR untouched); a sport
  action adds a follow-up 1 s later because MyITMO may first return the old
  schedule. Failed refreshes log a `ScheduleWidget` diagnostics warning.
- Compact and full settings are independent models in `core/settings`
  (`WidgetSettingsPreferences`). Text size (`WidgetTextSize`) is in the
  snapshot and applied on every render, so a recycled view keeps nothing.
- The day widget sends rows inline (`RemoteCollectionItems`) with stable ids,
  so old rows never show under a newer header.
  `ScheduleWidgetRemoteViewsService` stays for launchers holding an old
  adapter intent.
- Descriptors (`res/xml-v31`: `minWidth` 180 dp) never ask for more than the
  layouts need: a launcher scales the widget down below the minimum.

### Timeline (WidgetKit)

`ScheduleWidgetSelector.timeline` (via
`ScheduleWidgetDataProvider.loadTimeline`) emits an entry at the start and at
every instant the snapshot can change (lesson starts and ends, compact early
switch, midnights, pending sport starts), merging equal neighbours; every
entry equals `select` over its interval. `ScheduleWidgetTimelineJson`, version
1: `{"version", "generatedAt", "validUntil", "entries": [{"validFrom",
"snapshot"}]}`, snapshot in `schedule_snapshot.json` keys, nulls omitted,
defaults written, ISO-8601 UTC. `version` stays for additive fields and goes
up for a rename or removal; readers reject a higher one. Fixture:
`shared/feature-schedule/fixtures/schedule-widget-timeline-v2.json`. Android
does not use the timeline.

### iOS

The next-lesson and day widgets read the App Group file
schedule-timeline-v2.json, which `ScheduleTimelineWriter` computes to the end of
tomorrow; the sport widget reads sport-widget-v1.json, which `SportWidgetWriter`
computes a week ahead (fixture
`shared/feature-sport/fixtures/sport-widget-timeline-v1.json`). Swift makes no
time decisions: it picks the entry of the moment and the last `nowTicks` minute,
and countdowns are the system's live views. Rewritten on sign-in/out, widget
options, foreground, cache changes and sport actions. Details:
[iOS app](../ios.md#widgets).

## QR widget

- Custom spoiler image (`core/qr/CustomSpoilerManager`): a bounded
  420 x 420 PNG, saved atomically; widgets refresh only after success.
- Expiry is passive: an expired code stops being emitted (known gap).
- Widget, worker and renderer stay in `:app`; the pass, cache and per-widget
  reveal state (`qr_widget_state_<appWidgetId>`) are `QrCodeRepository` and
  `QrWidgetStateStore` of `:shared:feature-qr`, shared with the pass screen
  ([QR pass](qr.md#data)).

## Theme colours

`Виджеты в цвет темы` (`widgets_follow_app_theme`, off by default,
[settings](../settings.md#widgets-in-the-apps-colours)).

- One resolver: `ThemeSpec.widgetPalette(context)` (`:shared:designsystem`,
  without `Чёрный фон`) -> `WidgetPalette`; renderers read it via
  `WidgetPaletteSource` (null while off).
- Schedule widgets: `WidgetColors` sets every label colour on every render.
  On Android 12+ colours are light/dark pairs (`RemoteViews.setColorInt`).
  Background tint layers (`widget_theme_background.xml`) show only while on.
  Lesson type colours stay.
- QR: `QrColorResolver` uses the palette only while `Динамические цвета` is
  on; otherwise black on white.
- The switch or any `Оформление` change calls
  `WidgetRefreshRequester.refreshAll`. iOS writes the palette into the widget
  JSON files (`palette`).

## Previews

- Launcher picker: `previewLayout` (Android 12+) and `previewImage` PNGs
  rendered by `WidgetPreviewImageCapture` (`captureScreenshots=true`).
  Regenerate both whenever a widget layout changes.
- Settings previews: real renderers on sample data via
  `core/ui/widget.WidgetPreviewFactory` (`DefaultWidgetPreviewFactory`);
  `QrPreviewBitmapCache` holds at most four bitmap pairs, off the main thread.
  Previews never register a host, create PendingIntents, touch the network or
  write snapshots.
