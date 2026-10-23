# Widgets

Product behaviour: [Как это работает: виджеты](../how-it-works/widgets.md). Settings: [the widgets
gallery](../settings.md#widgets-gallery).

Five home-screen widgets: the next lesson (`Пара`, small and medium), the day (`Расписание на день`, large) and sport
(`Спорт`, small and medium), labelled beta (`(бета)` in the picker names, a `Бета` pill in the gallery); the classic
list `Список пар` of 2.2; and the QR pass. Android draws them as RemoteViews (ADR 0027) from
WorkManager workers and alarms; the app never registers a widget host. iOS draws them in WidgetKit from App Group
timelines ([iOS widgets](../ios.md#widgets)).

## Schedule widgets

- `ScheduleWidgetDataProvider` reads the own `ScheduleDays` of today and tomorrow (the days Сегодня and the
  schedule draw: the found changes, cancelled lessons, sport bookings and, while `Автозапись на спорт` and the
  services are on, the sport queues). `load(refresh = true)` fetches first; `load(refresh = false)` reads the cache
  only.
- `ScheduleWidgetSelector.select` builds one `ScheduleWidgetSnapshot` at an instant:
  - `next`: the lesson to go to with its moment (`NOW`, `FIRST`, `NEXT`, `TOMORROW`), the countdown target `until`
    and the current lesson's `since`. A queue never becomes the lesson to go to and a cancelled lesson is skipped.
    After the last lesson it is tomorrow's first lesson; `Следующая пара заранее` switches 15 minutes before the end.
  - `day`: the lessons of today or, once today is over and `Завтрашние пары вечером` is on, tomorrow, with their
    state and marks, the gaps of an hour or more, the time axis and the `сейчас` minute `now` (from an hour before
    the first lesson to the end of the last, null on tomorrow's day).
  - Each `WidgetLesson` carries the `LessonDetailsArgs` or `PendingSportDetailsArgs` its tap opens.
  - A snapshot with a queue has an exact official-only `officialFallback` and `pendingValidUntil`; the store shows
    the fallback once the queue rows are off or the deadline passed.
- Android draws the snapshot at the moment of drawing: the countdown text and the progress of the next-lesson widget come from
  `until` and `since` and the clock. While `ScheduleWidgetSnapshot.isLive` (a countdown or the `сейчас` line), a
  non-waking alarm (`ScheduleWidgetWork.scheduleTick`) redraws both widgets every minute from the cache, without a
  request; the next boundary (a start, an end, the early switch, an hour before a first lesson, a midnight), the
  hourly periodic work and, only while a queue shows (`pendingValidUntil`) with the auto sign and the opt-in on, the
  7-minute queue refresh run the worker with a fetch. The queue's booking itself arrives by push. The providers set
  `updatePeriodMillis="0"`: the periodic work is their only hourly source (the QR widget keeps its hourly one).
- The next-lesson widget is one provider (`SingleLessonWidgetProvider`) with two layouts: from API 31 a responsive `RemoteViews(Map)`
  (small from 110 dp, medium from 250 dp wide); below it the layout of the widget's narrowest width.
- The day widget (`DayTimelineWidgetProvider`): from API 31 one layout per launcher size (`OPTION_APPWIDGET_SIZES`)
  with the hours, gaps, lessons and the `сейчас` line placed at their minutes (`DayTimelineLayout`: a lesson keeps at
  least two lines' height, an hour at most 32 dp times the text size, so a short day stays compact at the top). The
  `сейчас` line never crosses a lesson's text (iOS draws the same). Below API 31 the lessons stack in a list.
- The classic list (`DayScheduleWidgetProvider`, `Список пар`) is 2.2's list widget on the same snapshot:
  `ScheduleListRows` turns `day` into a header, a row per lesson and the end marker, or one message row. The rows are
  inline `RemoteCollectionItems` with ids from their identity; `ScheduleWidgetRemoteViewsService` serves the same rows
  to a launcher that still binds 2.2's adapter. The end marker stays only when every row, measured at the launcher's
  smallest size, fits above it, so it never shows cut. It reads the day widget's settings (`full_widget_*`). The list
  draws no clock, so the minute tick skips it.
- Placed widgets keep their provider: a list placed in 2.2 is `DayScheduleWidgetProvider` and stays a list; the day widget is a
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

The iOS lesson progress is value-based (`WidgetLessonProgress`): `ProgressView(value:)` in the home-screen widget
and a `Gauge` on the Lock Screen at the entry's date (the frozen sample's in the gallery), so it moves at the
5-minute ticks and the lesson boundaries. A timer `ProgressView(timerInterval:)` behind a custom style turned the
small and medium widgets blank during a lesson on devices. The bar takes the footer's width left of the countdown, at
least 24 pt; the countdown keeps its width first and shrinks with the line.

## Sport widget

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
  text. Small and medium are one responsive provider, as the next-lesson widget.
- iOS reads the App Group file sport-widget-v1.json (`SportWidgetTimeline`, entries at each booking end and midnight),
  which `SportWidgetWriter` (`shared/feature-sport/src/iosMain`) computes a week ahead on the session, the widget
  theme, after a sport action and as the `sport-widget` background step; fixture
  `shared/feature-sport/fixtures/sport-widget-timeline-v1.json`. Details: [iOS app](../ios.md#widgets).
- Taps: the widget opens Спорт, a booking row its lesson (`ACTION_OPEN_SPORT` with the lesson id).

## QR widget

Settings: the `Пропуск` sheet of the gallery ([settings](../settings.md#qr-widget)).

- Widget, worker and renderer stay in `:app`; the pass, cache and per-widget reveal state
  (`qr_widget_state_<appWidgetId>`) are `QrCodeRepository` and `QrWidgetStateStore` of `:shared:feature-qr`, shared
  with the pass screen ([QR pass](qr.md#data)).
- Expiry is passive: an expired code stops being emitted (known gap).
- The sheet's preview uses the real bitmap and animation renderers on a labelled sample payload, never an active
  pass; no network request or widget registration. It reflects every change at once; a tap reveals or hides the
  sample, a changed animation plays once, motion stops when the screen is hidden.
- Animation and custom-image rows are disabled while the spoiler is off.
- Custom spoiler image (`core/qr/CustomSpoilerManager`): the photo picker (document picker on older devices), then a
  square crop with cancel, rotate and confirm; cancelling either step keeps the old image. Saved atomically as a
  bounded 420 x 420 PNG; widgets refresh only after success, a failed replacement keeps the previous image.
  `Сбросить изображение спойлера` removes it.
- `Добавить в шторку` (Android 13+, while the device flag `qr_tile_added` is off) shows the system dialog
  (`StatusBarManager.requestAddTileService`). Added or already added: the flag is set and the row leaves (already
  added also says `Плитка уже в шторке`); declined or in progress: nothing; other errors: `Не удалось добавить
  плитку`. The flag survives sign-out; removing the tile from the shade clears it. The tile keeps the system's style.
- iOS: `Динамические цвета` and the spoiler only, no animation or custom image; the code fades in instead of the
  circle ([degradations](../ios.md#degradations)). Both switches reach placed widgets at once.

## Theme colours

`Виджеты в цвет темы` (`widgets_follow_app_theme`, on by default,
[settings](../settings.md#widgets-in-the-apps-colours)).

- On, the next-lesson, day, sport and QR widgets draw with the scheme `Оформление` gives (accent or `Свой цвет`,
  style, contrast; with `Как обои` at the default style and contrast the wallpaper's scheme on Android 12+, the brand
  scheme elsewhere), light or dark as the system is, whatever `Тема` says (the launcher draws them). Only colours
  change: layouts, shapes and the lesson type colours and status tones (`очередь`, `записаны`) stay the widgets' own.
- `Чёрный фон` does not reach the widgets: a widget is a card on the home screen, so it keeps the surface tone (a black
  tile would vanish on a dark wallpaper).
- One resolver: `ThemeSpec.widgetPalette()` (`:shared:core`, `core/settings/ThemeSeeds.kt`, built without Compose from
  material-color-utilities) -> `WidgetPalette`. Android calls `widgetPalette(context)` (`:shared:designsystem`), which
  takes the wallpaper's own scheme where the app does; iOS writes the core palette into every App Group file
  (`palette`). Renderers read it via `WidgetPaletteSource` (null while off). `WidgetPalettesTest` holds the core
  palette equal to the roles of `resolveColorScheme` for every seeded appearance.
- `WidgetColors` sets every colour on every render: on Android 12+ light/dark pairs (`RemoteViews.setColorInt`),
  shapes tinted through `setColorFilter`. Below Android 12 a widget gets the colours of the mode it was last drawn
  in; the QR widget is a bitmap and takes the mode of its last drawing on every version.
- QR precedence (`QrColorResolver`): without `Динамические цвета` the code is black on white whatever the switch says
  (it always scans); with them the code and spoiler take the app's scheme while the switch is on, the system's
  colours otherwise.
- The switch or any `Оформление` change redraws installed widgets at once (`WidgetRefreshRequester.refreshAll`); the
  gallery's previews follow both.

## Previews

- Launcher picker: `previewImage` PNGs (`res/drawable{,-night}-420dpi/widget_*_preview.png`) are the real
  renderers on the sample day, written by `WidgetPreviewImagesTest` with `ITMO_WRITE_WIDGET_PREVIEWS=1`; the Android
  12+ `previewLayout`s show the same images. Regenerate them whenever a widget layout changes.
- The gallery's previews: real renderers on sample data via `core/ui/widget.WidgetPreviewFactory`, each laid out at
  its launcher size and shrunk as a whole to its card's width; `QrPreviewBitmapCache` holds at most four bitmap
  pairs, off the main thread. Previews never register a host, create PendingIntents, touch the network or write
  snapshots.
