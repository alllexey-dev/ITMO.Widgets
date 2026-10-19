# Widgets

Product behaviour: [Как это работает: виджеты](../how-it-works/widgets.md). Settings: [the widgets
gallery](../settings.md#widgets-gallery).

Five home-screen widgets: the next lesson (W1, small and medium), the day (W2, large) and sport (W3, small and
medium) of the UX overhaul (spec "Виджеты"), labelled beta (`(бета)` in the picker names, a `Бета` pill in the
gallery) while they are refined; the classic list `Список пар` of 2.2; and the QR pass. Android draws them as RemoteViews (ADR 0027) from
WorkManager workers and alarms; the app never registers a widget host. iOS draws them in WidgetKit from App Group
timelines ([iOS widgets](../ios.md#widgets)).

## Schedule widgets

Shared in `shared/feature-schedule` (`domain/widget`, `data/widget`); RemoteViews in `:app`
(`feature/schedule/ui/widget`: `NextLessonWidgetRenderer`, `DayWidgetRenderer`, `DayTimelineLayout`,
`ScheduleListWidgetRenderer`;
`feature/schedule/work`: `ScheduleWidgetUpdateWorker`, `ScheduleWidgetRefreshReceiver`).

- `ScheduleWidgetDataProvider` reads the own `ScheduleDays` of today and tomorrow (the days Сегодня and the
  schedule draw: the found changes, cancelled lessons, sport bookings and, while `Автозапись на спорт` and the
  services are on, the sport queues). `load(refresh = true)` fetches first; `load(refresh = false)` reads the cache
  only.
- `ScheduleWidgetSelector.select` builds one `ScheduleWidgetSnapshot` at an instant:
  - `next` (W1): the lesson to go to with its moment (`NOW` `Сейчас, до 13:10`, `FIRST`, `NEXT`, `TOMORROW`), the
    countdown target `until` and the current lesson's `since`, `window` for a wait of an hour or more, up to two
    `rest` lessons of the same day and the day's `lastEnd`. A queue never becomes the lesson to go to and a
    cancelled lesson is skipped. After the last lesson it is tomorrow's first lesson. `Следующая пара заранее`
    switches 15 minutes before the end.
  - `day` (W2): the lessons of today or, once today is over and `Завтрашние пары вечером` is on, tomorrow, with
    their state (`DONE`, `NOW`, `UPCOMING`), changed, cancelled and queued marks, the gaps of an hour or more, the
    time axis (`axisStart`, `axisEnd` in minutes of the day) and the `сейчас` minute `now` (from an hour before
    the first lesson to the end of the last, null on tomorrow's day), and the subtitle's counts (`DaySummary`).
  - Each `WidgetLesson` carries the `LessonDetailsArgs` or `PendingSportDetailsArgs` its tap opens.
  - A snapshot with a queue has an exact official-only `officialFallback` and `pendingValidUntil`; the store shows
    the fallback once the queue rows are off or the deadline passed.
- Android draws the snapshot at the moment of drawing: the countdown text and the progress of W1 come from
  `until` and `since` and the clock. While `ScheduleWidgetSnapshot.isLive` (a countdown or the `сейчас` line), a
  non-waking alarm (`ScheduleWidgetWork.scheduleTick`) redraws both widgets every minute from the cache, without a
  request; the next boundary (a start, an end, the early switch, an hour before a first lesson, a midnight) and
  the 7-minute queue refresh run the worker with a fetch.
- W1 is one provider (`SingleLessonWidgetProvider`) with two layouts: from API 31 a responsive `RemoteViews(Map)`
  (small from 110 dp, medium from 250 dp wide); below it the layout of the widget's narrowest width.
- W2 (`DayTimelineWidgetProvider`, `widget_day_info`): from API 31 one layout per launcher size (`OPTION_APPWIDGET_SIZES`), the hours,
  the gaps, the lessons and the `сейчас` line placed at their minutes on `day_canvas` with `setViewLayoutMargin`
  and `setViewLayoutHeight`. `DayTimelineLayout` maps minutes to dp: a lesson keeps at least two lines' height and
  the rest of the day shares what is left in proportion, an hour at most 32 dp (times the text size), so a day of
  one to three lessons stays compact at the top. The `сейчас` line runs under the lessons; the current lesson draws
  its own part of it under its text, with the elapsed part shaded inside the block. Below API 31 the lessons stack in
  `day_list`.
- The classic list (`DayScheduleWidgetProvider`, `widget_lesson_list_info`, `Список пар`) is 2.2's list widget on
  the same snapshot: `ScheduleListRows` turns `day` into the header (`Сегодня · 7 сентября`), a row per lesson (time,
  type dot, subject, type or queue or `отменена`, place and teacher; done and cancelled ones faded) and the end
  marker, or one message row (`Сегодня нет пар`, `На сегодня пары закончились`, `Сегодня и завтра нет пар`, loading,
  signed out, error). The rows are inline `RemoteCollectionItems` with ids from their identity and at least 9 view
  types; `ScheduleWidgetRemoteViewsService` serves the same rows to a launcher that still binds 2.2's adapter. Every
  row opens the schedule. The end marker stays only when every row, measured at the launcher's smallest size, fits
  above it, so it never shows cut; the lessons still scroll. It reads W2's settings (`full_widget_*`). The list draws no clock, so the minute tick skips
  it and it is redrawn only by the worker at boundaries and refreshes.
- Placed widgets keep their provider: a list placed in 2.2 is `DayScheduleWidgetProvider` and stays a list; W2 is a
  new provider that no release had shipped (ADR 0027, amended 2026-10-18).
- Snapshot: kotlinx JSON in `noBackupFilesDir/widgets/schedule_snapshot.json` (`ScheduleWidgetSnapshotStoreImpl`,
  `AtomicTextFile`), `formatVersion` 3 written first; another version (2.2's file, version 2) reads as loading
  until the next update. The store is a session cleaner: cleanup invalidates in-flight worker tickets before
  clearing disk, so an old account's snapshot cannot land in a new session. `AndroidSessionLifecycleEffects`
  cancels widget work before a session change and renders signed-out placeholders after.
- Taps go through the route queue of `MainActivity` ([navigation](../architecture.md#navigation)), each target
  with its own data URI (`WidgetIntents`): a lesson opens its sheet (`ACTION_OPEN_LESSON`), a queue its sport lesson
  (`ACTION_OPEN_SPORT` with the lesson), the day header and the rest of the widget the schedule.
- Sport actions, preference changes and the services gate force an update through `WidgetRefreshCoordinator`; a
  sport action adds a follow-up 1 s later because MyITMO may first return the old schedule. Failed refreshes log
  a `ScheduleWidget` diagnostics warning.
- Text size (`WidgetTextSize`) is in the snapshot per format and applied on every render.

### Timeline (WidgetKit)

`ScheduleWidgetSelector.timeline` (via `ScheduleWidgetDataProvider.loadTimeline`) emits an entry at the start and at
every boundary, merging neighbours that differ only in the `сейчас` minute; an entry whose day shows the line
carries `nowTicks` (every 5 minutes, `{at, minute}`), from which the reader shows the entry with that `now`.
`ScheduleWidgetTimelineJson`, version 2: `{"version", "generatedAt", "validUntil", "palette", "entries":
[{"validFrom", "snapshot", "nowTicks"}]}`, nulls omitted, defaults written, ISO-8601 UTC. `version` stays for
additive fields and goes up for a rename or removal; readers accept only their own. Fixture:
`shared/feature-schedule/fixtures/schedule-widget-timeline-v2.json`. Countdowns are system live text on iOS
(`until`, `since`); Swift takes no time decision.

## Sport widget

Shared in `shared/feature-sport` (`domain/widget/SportWidget.kt`, `data/widget/SportWidgetDataProvider.kt`); Android
in `feature/sport/ui/widget` (`SportWidgetProvider`, `SportWidgetRenderer`, `SportWidgetProviders`) and
`feature/sport/work/SportWidgetWork.kt`.

- `SportWidgetDataProvider` fetches as `Моё` does: the score from My ITMO, the bookings with their queues (the queues
  only through the custom-services gate of `SportDataRepository`; demo data in a demo session). Signed out it
  starts no request.
- `SportWidgetSelector` makes `SportWidgetSnapshot`: `visits`, the credited `bonus` (at most 40), their `total`, and
  up to two bookings or waiting queues that have not ended, nearest first, with their day (`TODAY`, `TOMORROW`,
  `LATER`), time, section, MyITMO's building address and status (`BOOKED` `записаны`, `QUEUE` `очередь 2 из 5`,
  `UNPUBLISHED` `ждём расписание`). `done` (100 or more) closes the ring in green. No score: `ERROR`; no bookings:
  the ring alone.
- Android: hourly periodic work, an update after every sport action, and an RTC alarm at the snapshot's next change
  by itself (a booking ends, a midnight; `SportWidgetLoad.nextChange`). The snapshot is kept in
  `noBackupFilesDir/widgets/sport_snapshot.json` (`formatVersion` 1, `SportWidgetSnapshotStoreImpl`, a session cleaner) for launcher
  redraws. The ring is three white `ALPHA_8` arc bitmaps tinted per render, so it follows light and dark like the
  text. Small and medium are one responsive provider, as W1.
- iOS reads the App Group file sport-widget-v1.json (`SportWidgetTimeline`, entries at each booking end and midnight),
  which `SportWidgetWriter` (`shared/feature-sport/src/iosMain`) computes a week ahead on the session, the widget
  theme, after a sport action and as the `sport-widget` background step; fixture
  `shared/feature-sport/fixtures/sport-widget-timeline-v1.json`. Details: [iOS app](../ios.md#widgets).
- Taps: the widget opens Спорт, a booking row its lesson (`ACTION_OPEN_SPORT` with the lesson id).

## QR widget

- Custom spoiler image (`core/qr/CustomSpoilerManager`): a bounded 420 x 420 PNG, saved atomically; widgets refresh
  only after success.
- Expiry is passive: an expired code stops being emitted (known gap).
- Widget, worker and renderer stay in `:app`; the pass, cache and per-widget reveal state
  (`qr_widget_state_<appWidgetId>`) are `QrCodeRepository` and `QrWidgetStateStore` of `:shared:feature-qr`, shared
  with the pass screen ([QR pass](qr.md#data)).

## Theme colours

`Виджеты в цвет темы` (`widgets_follow_app_theme`, off by default,
[settings](../settings.md#widgets-in-the-apps-colours)).

- One resolver: `ThemeSpec.widgetPalette(context)` (`:shared:designsystem`, without `Чёрный фон`) -> `WidgetPalette`
  (surface, containers, text, outline, primary, primary container, tertiary); renderers read it via
  `WidgetPaletteSource` (null while off).
- `WidgetColors` sets every colour on every render: on Android 12+ light/dark pairs (`RemoteViews.setColorInt`),
  shapes tinted through `setColorFilter`. The lesson type colours and the status tones (`очередь`, `записаны`)
  stay the widgets' own. Background tint layers (`widget_theme_background.xml`) show only while on.
- QR: `QrColorResolver` uses the palette only while `Динамические цвета` is on; otherwise black on white.
- The switch or any `Оформление` change calls `WidgetRefreshRequester.refreshAll`. iOS writes the palette into the
  widget JSON files (`palette`).

## Previews

- Launcher picker: `previewImage` PNGs (`res/drawable{,-night}-420dpi/widget_*_preview.png`) are the real
  renderers on the sample day, written by `WidgetPreviewImagesTest` with `ITMO_WRITE_WIDGET_PREVIEWS=1`; the Android
  12+ `previewLayout`s show the same images. Regenerate them whenever a widget layout changes.
- The gallery's previews: real renderers on sample data via `core/ui/widget.WidgetPreviewFactory`
  (`DefaultWidgetPreviewFactory`: `ScheduleSettingsPreview`, `SportSettingsPreview`, `QrSettingsPreview`), each laid
  out at its launcher size in a `WidgetPreviewFrame` and shrunk as a whole to its card's width;
  `QrPreviewBitmapCache` holds at most four bitmap pairs, off the main thread. Previews never register a host,
  create PendingIntents, touch the network or write snapshots.
- Checks: `WidgetsScreenshotTest` (`app/screenshots/Widget_*`) draws every widget state through RemoteViews on the
  JVM (the classic list through `ScheduleListWidgetRenderer.previewView`, a local adapter over the same rows),
  `ScheduleListRowsTest` the list's rows, `WidgetsGalleryScreenshotTest` the gallery with them, the instrumented `WidgetRenderingTest` inflates every
  layout on the device's API level.
