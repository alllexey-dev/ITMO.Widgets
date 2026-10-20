# Сегодня, the bell and quick actions

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md)
(Сегодня), [QR-пропуск](../how-it-works/qr.md) (tile and shortcuts).

Сегодня is the first tab: under its root header (the date and the bell) the hero with the lesson to go to now, the
day as the timeline and `Нужно сделать` when there is something, with the `Пропуск` button
([design](../design.md#shell)). The bell opens its sheet over it.

## Сегодня

- `TodayViewModel` (`:shared:feature-home`) reads today and tomorrow from `DayLessonsGateway` (`core/schedule`,
  implemented by the schedule's `ScheduleDaysImpl`) and every Koin `TodayTodoSource` (`core/today`, an open set read
  with `getAll()`), re-evaluated every minute by the academic clock. `TodayPresenter` is the pure rule:
  - while today has a lesson that is not over, the hero is the current one or the next one, and the day is today's
    whole timeline with the now marker;
  - once today is over, the hero is tomorrow's first lesson and the day is tomorrow;
  - a free evening keeps today's finished day without a hero; nothing today and tomorrow is the empty state;
  - cancelled lessons and sport queues are never the hero and do not count.
- The hero says where to go: the room with the building's full address, or `Онлайн` with `Войти` (the meeting link).
  A placeholder room (`дистант`) means online. The big text never clips and never touches the button: the address or
  the button goes under it when the row is too narrow. `Маршрут` next to the room of an in-person lesson opens the
  [campus map](map.md) with the route to it.
- The day is the shared lesson timeline (`core/lesson/LessonTimelines`) with the found
  [schedule changes](schedule-changes.md), own sport queues and gaps of an hour or more. Every row opens the lesson
  sheet ([Лист пары](schedule.md#lesson-sheet)). Two lessons that overlap stay separate rows in start order with no
  window between them; the later one says `одновременно с «…»`, and the rail fills once with one `сейчас` marker (on
  the earliest lesson going on).
- `Нужно сделать`: `BarsTodoSource` (recordbook) while the background mark check found the BARS session ended
  (`BarsLoginPrompt` not `NONE`); `Войти` opens the BARS sign-in.
- States: `Loading` while nothing is cached and the first refresh runs; `Failed` when it failed; after a failed
  refresh over the cache the day is dimmed under `Данные на 11:44`. The first show refreshes once (silent), a pull and
  a return of the app to the foreground (`AppForeground`) after five minutes refresh again; a tab switch or a sheet
  closing over Сегодня does not. Only a pull shows the indicator. The header (`TodayHeaderViewModel`) re-reads the
  date and asks the bell's sources when it is created and on each return.

## The bell

- The bell is in Сегодня's header only, with the count of events the user has not seen; it opens `AppRoutes.Bell`, a
  tall sheet. A schedule change notification opens it too (`ACTION_OPEN_SCHEDULE_CHANGES`). There is no history
  screen of changes.
- Events come from every Koin `BellSource` (`core/bell`, an open set):

| Event | Source | Leads to |
|---|---|---|
| New marks with their value | `MarksBellSource` (recordbook), the subjects of [mark tracking](marks-tracking.md), read ones too | the subject page, else the recordbook |
| A schedule change | `ScheduleChangesBellSource` (schedule), the change history | the lesson's sheet |
| A queue's result | `SportBellSource` over `SportResultLog` (sport), written by the queue push booker | Спорт |
| An incoming friend request with `Принять` and `Отклонить` | `FriendRequestsBellSource` (social), behind the opt-in | the person |

- `BellRepositoryImpl` joins the sources (requests first, each part newest first, an unknown moment last; a failing
  source counts as empty) and keeps the seen event ids in `BellPreferences` (`bell_seen`, cleared on sign-out). An
  event's id changes with its content, so a new mark of a subject is new again.
- Opening reads everything (`BellViewModel`): the unseen events are marked seen at once, so the badge goes, and every
  source reads its own (the mark dots, the change dots and their notifications), while this opening keeps them under
  `Новое`; there is no `Прочитать все`. Events that arrive while it is open are new as well.
- `Настройки уведомлений` at the end opens the app's notifications page.

## QR pass

The `Пропуск` button opens the pass sheet ([QR pass](qr.md)); `Разделы` -> `Показывать кнопку пропуска` (default on,
survives sign-out) hides only this entry and the iOS tab-bar accessory, not the widgets, shortcuts or the pass route.

## Quick-settings tile and app shortcuts

- `QrTileService` (`feature/qr/ui`): `QrTileController` initialises the session in `onStartListening`; `STATE_ACTIVE`
  when signed in, else `STATE_INACTIVE`; not toggleable. A tap always opens `ACTION_OPEN_QR_PASS` with the widget's
  flags (`NEW_TASK | CLEAR_TOP | SINGLE_TOP`), via `unlockAndRun` when locked; without a session the route waits for
  sign-in. Android 14+ must use `startActivityAndCollapse(PendingIntent)`; the `Intent` overload throws there
  (`qrTileLaunchFor`).
- `qr_tile_added` (`DeviceHintPreferences`, kept on sign-out) is written by `onTileAdded`/`onTileRemoved` and the
  add-request answer, since Android has no API to ask. The add request (Android 13+) belongs to `feature/settings`
  (`QuickSettingsTileAccess`, `requestAddQrTile`).
- Static shortcuts in `res/xml/shortcuts.xml` (ids in `app/AppShortcuts`): `qr_pass` (`ACTION_OPEN_QR_PASS`) and
  `today` (`ACTION_OPEN_TODAY`, the own schedule on today, also when a friend is selected). Routes report usage with
  `ShortcutManagerCompat.reportShortcutUsed` (the tile too, the in-app `Пропуск` button not). Launchers start
  shortcuts with `NEW_TASK | CLEAR_TASK`, so `MainActivity` is recreated and tab state is lost; the tile keeps it.

## iOS

Сегодня is SwiftUI over the same view models ([iOS app](../ios.md)): `TodayScreen` and `BellSheet`
(`iosApp/Sources/Features/Home/`). The notification service leaves each queue result in the App Group
(`SportResultInbox`); the bell's sport source takes them on refresh (`AppGroupSportResultInbox`), so both platforms
show queue results.
