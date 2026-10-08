# Calendar

The own personal schedule goes to the phone's calendar in two ways, both on
the `Расписание` settings page ([settings](../settings.md#schedule)): a kept
synchronization and a one-off `.ics` file. The schedule screen has no top-bar
menu, so it offers neither; lessons and subjects have no calendar actions.
Backend is not involved. The code is part of the schedule feature
([modules](schedule.md#modules)).

## Events

- `OwnScheduleSource` (`MyItmoOwnScheduleSource`) asks
  `MyItmoApi.getPersonalSchedule` itself in pieces of at most 31 days, one
  after another, like the change check: no schedule cache, no upload to
  Backend.
- `CalendarEvents.from` (`domain/calendar`) turns every lesson of the answer
  into a `CalendarEvent`, as My ITMO sends it: academic lessons, sport and
  room bookings alike. The key is `lesson-<pairId>`; a lesson without a
  positive id is `lesson-<date>-<minute of start>-<flowId>-<subjectId>`. A
  repeated key is kept once, at its first slot.
- Title: the subject. Start and end: the date and times in `Europe/Moscow`
  (`AcademicTimeProvider.zoneId`). Location: the room and the street address of
  a known building (`BuildingDirectory`), otherwise the building as My ITMO
  spells it. Description: the lesson type, the teacher and the flow, one per
  line. The UID is `<key>@widgets.alllexey.dev`.

## Synchronization

- `CalendarSyncRepositoryImpl` keeps the window today..today+28 in the app's
  own local calendar `ITMO.Widgets` (`ACCOUNT_TYPE_LOCAL`, created and deleted
  through the sync-adapter URI, colour `calendar_app`, owner access). There is
  no calendar choice. The calendar exists only on this phone: calendar apps
  that read the phone's calendars (Xiaomi, Samsung) show it; Google and Yandex
  Calendar do not, since they show only their own accounts' calendars; for
  Google Calendar there is the `.ics` export.
- Google-account calendars are never written (owner's decision of
  2026-10-02): a delete there is final only once Google's sync adapter uploads
  it, and Android's guard against too many deletions undoes bulk deletes, after
  which the adapter writes the server's copies back as new rows. On the
  owner's phone 37 of 38 events came back after turning off.
- `AndroidPhoneCalendars` is the only `CalendarContract` code. Events are busy,
  have no reminders and carry the event time zone. The last line of the
  description is the app's tag `ITMO.Widgets · <key>`
  (`CalendarEvent.taggedDescription`); `CUSTOM_APP_PACKAGE`, `CUSTOM_APP_URI`
  and `UID_2445` are set too. Sweeps find the app's events by the tag, which
  also survives rows Google's sync adapter writes back. The `.ics` file has no
  tag; its UIDs name the occurrences.
- The ids of the app's events, the calendar of each and the content they were
  given live in `filesDir/calendar_sync/state.json` (format 1, with the
  switch, the calendar and the reason it turned itself off), written
  atomically, excluded from backup and device transfer. A restored device
  therefore starts with synchronization off. A corrupt file is deleted and
  synchronization is off.
- `CalendarSyncPlanner.plan` (pure) compares the window with the stored
  events. Only occurrences that have not ended are touched: a new one is
  inserted, a changed one (title, time, location or description) is updated in
  place, one that vanished from the window is deleted. A past event stays as
  it was; nothing outside the window is deleted; an event that ended more than
  180 days ago is forgotten (it stays in the calendar). A key is inserted once,
  so repeated syncs add no duplicates. An event the user deleted is inserted
  again by its next change. Every insert is written to the file at once, so an
  event in the calendar never lacks its id.
- Before planning, a sync deletes tagged events of the calendar in the window
  that have not ended and whose ids the file lacks (a process death between an
  insert and its write), then inserts them anew: lost ids never double the
  lessons.
- Turning off first stores the switch as off (the stop flag), so a sync queued
  behind it writes nothing, then deletes the app's calendar with all its
  events in one operation, under the same lock. Turning on reuses the app's
  calendar only when the stored ids belong to it, otherwise recreates it.
- Before each sync: without the calendar permission synchronization turns
  off with `NO_PERMISSION` and keeps the ids, so turning on again adopts the
  calendar with its events; a deleted calendar turns it off with
  `CALENDAR_MISSING` and forgets them. Settings show the reason in the
  switch's line.
- Every operation (turning on, off, syncing, sign-out) runs behind one mutex,
  so a switch-off waits for a running sync and then deletes all it wrote. Only
  the request to My ITMO can be cancelled; calendar writes and the ids they
  produce run to their end (`NonCancellable`) even when WorkManager cancels
  the work. A sync whose calendar or session changed meanwhile writes nothing.
  Sign-out and account change delete the app's calendar (when the permission
  is there) and the file (`SessionDataCleaner`).

## Earlier builds that wrote to Google

Builds of 2026-10-02 before the decision could write into a Google calendar.
A state with that target (`target = phone`) reads as off. The next run, or
turning on, deletes the app's tracked events there and never inserts there
again; turning on uses the app's own calendar. That Google calendar then stays
in the file's `cleanups`: every run (the work stays on while one is pending,
also with the switch off, and then asks My ITMO nothing) sweeps it by the tag
from 180 days back to 400 days ahead, because Google may write the deleted
events back. A sweep that finds events starts its 3 days anew; a calendar
clean for 3 days or deleted is done. A delete drops its id only when it went
through; failed ones stay in the file with their calendar and are retried.

## Work

- `DefaultCalendarSync` (`core/schedule/CalendarSync`) owns the switch and the
  work. `CalendarSyncWorker` runs the unique periodic work `calendar-sync`
  every 2 hours with `NetworkType.CONNECTED`, backoff from 15 minutes,
  `ExistingPeriodicWorkPolicy.UPDATE`, tag `calendar-sync`, with
  `DefaultCalendarSync` from Koin; `PeriodicCheckScheduler` with
  `CALENDAR_SYNC_SPEC` enqueues and cancels it. The worker and the spec live
  in `:app`'s `feature/schedule/work`; the rest of the
  synchronization, the `PhoneCalendars` port included, lives in `domain/calendar`
  and `data/calendar` of `:shared:feature-schedule` `commonMain`, except
  `AndroidPhoneCalendars` and `IcsFileExport` in `:app`. The work does not
  depend on `Изменения расписания` and has no quiet hours, since it notifies nothing. Failures are retried through
  `outcomeOf` and `workResultOf` like the background checks.
- The one-off work `calendar-sync-now` (`ExistingWorkPolicy.REPLACE`, network)
  runs right after turning on and after a successful
  pull on the own schedule (`ScheduleViewModel` calls `requestSync()`, which
  does nothing while synchronization is off).
- `syncWork()` runs on application start and after sign-in, `stopWork()`
  before a session change. The work stays while synchronization is on or a
  Google calendar of an earlier build is still swept; otherwise a run cancels
  it.

## `.ics` export

`IcsFileExport` (`core/schedule/ScheduleIcsExport`) reads the range of a
`ScheduleExportRange`: `Неделя` (today and 6 days), `2 недели` (today and 13
days), `До конца семестра` (to 31 January from August to January, to 31 July
from February to July) or `Свои даты` (a `MaterialDatePicker` range).
`IcsWriter` writes RFC 5545: CRLF, lines folded at 75 octets without splitting
a UTF-8 character, escaped text, times in UTC (no `VTIMEZONE`), the same UIDs
as synchronization, `TRANSP:OPAQUE`. The file is
`cacheDir/ics/itmo-schedule-<start>-<end>.ics` (only the latest is kept),
shared through the `FileProvider` `${applicationId}.files`. A range without
lessons writes nothing. This is the way into Google Calendar.

`IcsExportBottomSheet` (`feature/settings/ui`, body `IcsExportSheetContent` in `:shared:feature-settings`)
is the whole flow: the title `Выгрузить в .ics`, the subtitle `Своё расписание
из My ITMO` and one area of at least 288 dp that every state shares, so the
sheet does not jump. `IcsExportViewModel` holds the state:

- Choose: the four ranges as one connected group (rows on
  `colorSurfaceContainerHigh`, 56 dp rows, the whole row is the target), each
  with its days on the second line from today (`2–8 октября`, `до 31 января`,
  `Выбрать в календаре`; `IcsDateLabels`: one day, a month, two months, two
  years). `Свои даты` opens the `MaterialDatePicker` and comes back with its
  range.
- Preparing: a progress indicator and `Готовим файл…`.
- Ready: `Файл готов`, `23 пары, 2–8 октября`, the filled `Отправить`
  (`ACTION_SEND`, `text/calendar`), the tonal `Открыть в календаре` only when
  an app opens `.ics` files (`ACTION_VIEW`), and the hint `Импортируйте в
  отдельный календарь — так его легко удалить или заменить свежим файлом`.
- Empty: `В этом периоде пар нет` with `Выбрать другой период`.
- Failed: the error's message with `Повторить`.

The chosen range and the written file live in the `SavedStateHandle`:
recreation shows the same state, and after a process death the file is
written again; the date picker is listened to again by its tag.

## Tests

`CalendarEventsTest`, `CalendarSyncPlannerTest`, `IcsWriterTest` and
`ScheduleExportRangeTest` cover the domain; `CalendarSyncFileStoreTest`,
`CalendarSyncRepositoryImplTest` (fake calendars, fake file system) and
`DefaultCalendarSyncTest` in `commonTest`, `MyItmoOwnScheduleSourceTest`,
`CalendarSync22GoldenTest` and `IcsFileExportTest` the data and the work; `SettingsViewModelTest` and
`ScheduleViewModelTest` the rows and the sync after a pull, `IcsExportViewModelTest`
the sheet's states, saved state and date labels. Instrumented:
`CalendarSyncProviderTest` against the emulator's CalendarProvider (the local
calendar, two syncs without duplicates, update, delete, lost ids, turning off
during the first sync, leaving an earlier build's calendar that behaves like a
Google one, with `_SYNC_ID` rows and copies the sync adapter writes back) and
`SettingsNavigationTest` for the rows, every state of the `.ics` sheet (in a
light and a dark appearance, with the area height kept) and the permission
dialog.
