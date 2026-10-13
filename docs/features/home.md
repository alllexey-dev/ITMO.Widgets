# Сегодня, the bell and quick actions

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md)
(Сегодня), [QR-пропуск](../how-it-works/qr.md) (tile and shortcuts).

Сегодня is the first tab: under its root header (the date and the bell) the
hero with the lesson to go to now, the day as the timeline and `Нужно сделать`
when there is something, with the `Пропуск` button ([design](../design.md#shell)).
The bell opens its sheet over it. The spec is the design lab's
(`vibe/design-lab/ux-native/DECISIONS.md`, "Сегодня", "Колокольчик").

## Сегодня

- `TodayViewModel` (`:shared:feature-home`, `homeModule`) reads today and
  tomorrow from `DayLessonsGateway` (`core/schedule`, implemented by the
  schedule's `DayLessonsGatewayImpl`) and every Koin `TodayTodoSource`
  (`core/today`, an open set read with `getAll()`), re-evaluated every minute
  by the academic clock. `TodayPresenter` is the pure rule:
  - while today has a lesson that is not over, the hero is the current one
    (`Сейчас`, `ещё 48 мин`, the elapsed part on the wavy bar) or the next one
    (`Первая пара` before the first, `Далее` after one, `через 1 ч 40 мин`),
    and the day is today's whole timeline with the now marker;
  - once today is over, the hero is tomorrow's first lesson (`Завтра`,
    `в 10:00`) and the day is tomorrow, its first lesson `далее`;
  - a free evening keeps today's finished day without a hero; nothing today
    and tomorrow is the `Сегодня и завтра пар нет` state;
  - cancelled lessons and sport queues are never the hero and do not count.
- The hero says where to go: the room big with the building's full address,
  or `Онлайн` with a small `Войти` next to it (the meeting link; owner,
  2026-10-13, hero lab variant 2), the time and the lesson's note. A
  placeholder room (`дистант`) means online. The big text never clips and
  never touches the button: at least the kit's `content` gap, the address or
  the button goes under it when the row is too narrow. `Маршрут`, the same
  small button for in-person lessons, waits for the map (phase 8): it is not
  shown.
- The day is the kit's timeline built by the shared lesson rules
  (`core/lesson/LessonTimelines`, [design](../design.md#the-m3-expressive-kit)):
  `идёт`, `далее`, `изменена` and `отменена` from the found
  [schedule changes](schedule-changes.md), `очередь` for an own sport queue,
  `записаны` for a booked sport lesson, `онлайн`, `Окно 2 ч 10 мин` for gaps
  of an hour or more, `Изменено вчера в 20:14` under a changed lesson. A
  lesson opens its sheet; a sport lesson goes through `SportDetailsRedirect`,
  as in the schedule ([lesson details](schedule.md#lesson-details)).
- `DayLessonsGatewayImpl` joins the schedule cache, the change history (the
  newest change found on each occurrence; a cancelled lesson stays on its old
  day, struck through) and the own queues while their schedule rows are on
  (`observeSportAutoSignEnabled`).
- `Нужно сделать`: `BarsTodoSource` (recordbook) while the background mark
  check found the BARS session ended (`BarsLoginPrompt` not `NONE`): `Войдите
  в БАРС заново`, since the last BARS answer, `Войти` opens the BARS sign-in.
  A moved room with `Маршрут` joins with the map.
- States: `Loading` while nothing is cached and the first refresh runs;
  `Failed` (`Не удалось загрузить`, `Повторить`) when it failed; after a
  failed refresh over the cache the day is dimmed under `Данные на 11:44`.
  The first show refreshes once (silent), a pull and a return after five
  minutes refresh again; only a pull shows the indicator.
- `TodayRoute` / `TodayScreen` are Compose in `commonMain`; on Android
  `HomeTabRoot` (`app/shell/entries/HomeEntries.kt`) keeps the ViewModel and
  turns lessons, `Войти` and the todo routes into navigation.

## The bell

- The bell is in Сегодня's header only, with the count of events the user has
  not seen (`TodayHeaderViewModel`); it opens `AppRoutes.Bell`, a sheet of
  `SheetPolicy.Height.TALL`. A schedule change notification opens it too
  (`ACTION_OPEN_SCHEDULE_CHANGES`). There is no history screen of changes.
- Events come from every Koin `BellSource` (`core/bell`, an open set):

| Event | Source | Leads to |
|---|---|---|
| New marks with their value: `Матанализ: контрольная работа 2`, `9 из 10, итого 74`, `+9` in front | `MarksBellSource` (recordbook), the subjects of [mark tracking](marks-tracking.md), read ones too | the subject page, else the recordbook |
| A schedule change: `Физика: пара перенесена`, `было` struck through over `стало` | `ScheduleChangesBellSource` (schedule), the change history | the lesson's sheet |
| A queue's result: `Волейбол: вы записаны`, `Плавание: не удалось записать` | `SportBellSource` over `SportResultLog` (sport), written by the queue push booker | Спорт |
| An incoming friend request with `Принять` and `Отклонить` | `FriendRequestsBellSource` (social), behind the opt-in | the person |

- `BellRepositoryImpl` (`homeModule`) joins the sources (requests first, then
  newest first; a failing source counts as empty) and keeps the seen event ids
  in `BellPreferences` (`bell_seen`, cleared on sign-out). An event's id
  changes with its content, so a new mark of a subject is new again.
- Opening reads everything (`BellViewModel`): the unseen events are marked
  seen at once, so the badge goes, and every source reads its own (the mark
  dots, the change dots and their notifications), while this opening keeps them
  under `Новое` with a dot and a bold title; there is no `Прочитать все`.
  Without new events the sheet says `Ничего нового` over the rest. Events
  that arrive while it is open are new as well.
- `Настройки уведомлений` at the end opens the system notification settings
  of the app until its own page (phase 7).

## QR pass

The `Пропуск` button of Сегодня opens the pass sheet above it; see [QR pass](qr.md).

## Quick-settings tile and app shortcuts

- `QrTileService` (`feature/qr/ui`): `QrTileController` initialises the
  session in `onStartListening`; `STATE_ACTIVE` when signed in, else
  `STATE_INACTIVE`; not toggleable. A tap always opens `ACTION_OPEN_QR_PASS`
  with the widget's flags (`NEW_TASK | CLEAR_TOP | SINGLE_TOP`), via
  `unlockAndRun` when locked; without a session the route waits for sign-in.
  Android 14+ must use `startActivityAndCollapse(PendingIntent)`; the
  `Intent` overload throws there (`qrTileLaunchFor`).
- `qr_tile_added` (`DeviceHintPreferences`, kept on sign-out) is written by
  `onTileAdded`/`onTileRemoved` and the add-request answer, since Android has
  no API to ask. The add request (Android 13+, `Добавить в шторку`,
  [settings](../settings.md#qr-widget)) belongs to `feature/settings`
  (`QuickSettingsTileAccess`, `AndroidQuickSettingsTileAccess`,
  `requestAddQrTile`).
- Static shortcuts in `res/xml/shortcuts.xml` (ids in `app/AppShortcuts`):
  `qr_pass` (`ACTION_OPEN_QR_PASS`) and `today` (`ACTION_OPEN_TODAY`, the own
  schedule on today, also when a friend is selected). Routes report usage
  with `ShortcutManagerCompat.reportShortcutUsed` (the tile too, the in-app
  `Пропуск` button not). Launchers start shortcuts with `NEW_TASK | CLEAR_TASK`, so
  `MainActivity` is recreated and tab state is lost; the tile keeps it.
- Tests: JVM in `app/src/test` and `QrTileFlowTest` on the emulator.

## MyITMO web

The `My ITMO` row of Профиль (the app group) opens `https://my.itmo.ru/` as
`AppRoutes.MyItmoWeb`. Security rules (`MyItmoWebPolicy` in `:shared:feature-account`):

- Only the exact HTTPS origins `my.itmo.ru` and `id.itmo.ru` stay embedded;
  no credentials in the URL, no non-standard ports.
- External HTTPS links open only after a main-frame user gesture; redirects
  and untrusted frames cannot launch apps. TLS errors cancel loading.
- File/content access, mixed content, pop-ups and third-party cookies are
  off. No JavaScript bridge, token injection or console logging.
- Own browser session; native refresh tokens are never copied in. Sign-out or
  account replacement clears web cookies, DOM storage and HTTP cache
  (`WebSessionDataCleaner`).

## iOS

Сегодня is SwiftUI over the same view models ([iOS app](../ios.md)): `TodayScreen` and `TodayContent`
(`iosApp/Sources/Features/Home/`) draw the hero, the day with the kit's `ItmoLessonTimeline` from the shared rows
(`Support/LessonUi+Kit.swift`) and the things to do under the root header with the bell; `BellSheet` is the bell's
tall sheet (`Routes+Home.swift`, `AppRoutes.Bell`). The notification service leaves each queue result in the App
Group (`SportResultInbox`); the bell's sport source takes them on refresh (`AppGroupSportResultInbox`), so both
platforms show queue results. Snapshot references: `TodaySnapshotTests`, `BellSheetSnapshotTests`.
