# Calendar

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

The own personal schedule goes to the phone's calendar as a kept
synchronization or a one-off `.ics` file, both on the `Расписание` settings
page ([settings](../settings.md#schedule)). Backend is not involved. The code
is part of the schedule feature ([modules](schedule.md#modules)).

## Events

- `MyItmoOwnScheduleSource` calls `MyItmoApi.getPersonalSchedule` itself in
  sequential chunks of at most 31 days: no schedule cache, no Backend upload.
- `CalendarEvents.from` (`domain/calendar`) maps every lesson as My ITMO sends
  it (academic, sport, room bookings). Key `lesson-<pairId>`, or
  `lesson-<date>-<minute of start>-<flowId>-<subjectId>` without a positive
  id; a repeated key is kept once at its first slot. UID
  `<key>@widgets.alllexey.dev`.
- Times in `Europe/Moscow` (`AcademicTimeProvider.zoneId`); location from
  `BuildingDirectory` when known, else My ITMO's text.

## Synchronization

- `CalendarSyncRepositoryImpl` keeps today..today+28 (`WINDOW_DAYS`) in the
  app's own local calendar `ITMO.Widgets` (`ACCOUNT_TYPE_LOCAL`, created and
  deleted through the sync-adapter URI). No calendar choice.
- Google-account calendars are never written: Google's sync adapter and
  Android's mass-delete guard bring deleted events back. Google users get the
  `.ics` export.
- `AndroidPhoneCalendars` is the only `CalendarContract` code. Events are
  busy, without reminders. The description's last line is the tag
  `ITMO.Widgets · <key>` (`CalendarEvent.taggedDescription`); sweeps find the
  app's events by it. The `.ics` file has no tag.
- `filesDir/calendar_sync/state.json` (format 1): event ids, their calendar
  and content, the switch, the calendar and the off-reason. Atomic, excluded
  from backup and device transfer, so a restored device starts off. A corrupt
  file is deleted and sync is off.
- `CalendarSyncPlanner.plan` (pure) touches only occurrences not yet ended:
  insert new, update changed in place, delete vanished from the window. Past
  events stay; nothing outside the window is deleted; events ended more than
  180 days ago (`RETENTION`) are forgotten. A key is inserted once. Every
  insert is persisted immediately, so a calendar event never lacks its id.
- Before planning, tagged future events in the window whose ids the file lacks
  (process death between insert and write) are deleted and reinserted: lost
  ids never double lessons.
- Turning off stores the off switch first (so a queued sync writes nothing),
  then deletes the app's calendar in one operation. Turning on reuses the
  calendar only if the stored ids belong to it.
- Before each sync: no calendar permission -> off with `NO_PERMISSION`, ids
  kept (turning on adopts the calendar); calendar deleted -> off with
  `CALENDAR_MISSING`, ids forgotten.
- One mutex for on, off, sync and sign-out. Only the My ITMO request is
  cancellable; calendar writes and their ids run `NonCancellable`. A sync
  whose calendar or session changed meanwhile writes nothing.
- Sign-out and account change delete the app's calendar (with permission) and
  the file (`SessionDataCleaner`).
- Legacy cleanup: a state with `target = phone` (builds of 2026-10-02 that
  could write to a Google calendar) reads as off; that calendar stays in the
  file's `cleanups` and every run sweeps it by tag from 180 days back to 400
  days ahead until it is clean for 3 days or deleted. Failed deletes stay and
  are retried. The work stays enqueued while a cleanup is pending, without
  asking My ITMO.

## Work

- `DefaultCalendarSync` (`core/schedule/CalendarSync`) owns the switch and the
  work. `CalendarSyncWorker` (`:app` `feature/schedule/work`): unique periodic
  `calendar-sync`, every 2 hours, `CONNECTED`, backoff from 15 minutes,
  `ExistingPeriodicWorkPolicy.UPDATE`, via `PeriodicCheckScheduler` with
  `CALENDAR_SYNC_SPEC`. Independent of `Изменения расписания`, no quiet hours.
  Retries through `outcomeOf` and `workResultOf`.
- One-off `calendar-sync-now` (`ExistingWorkPolicy.REPLACE`) after turning on
  and after a successful own-schedule pull (`ScheduleFeedViewModel` calls
  `requestSync()`, a no-op while off).
- `syncWork()` on start and after sign-in, `stopWork()` before a session
  change. The work stays while sync is on or a legacy cleanup is pending.

## `.ics` export

- `IcsFileExport` (implements `core/schedule/ScheduleIcsExport`) reads a
  `ScheduleExportRange`: `Week` (today + 6 days), `TwoWeeks` (+13), semester
  end (31 January for August-January, 31 July for February-July) or a custom
  range.
- `IcsWriter`: RFC 5545, CRLF, lines folded at 75 octets without splitting a
  UTF-8 character, UTC times (no `VTIMEZONE`), same UIDs as the sync,
  `TRANSP:OPAQUE`.
- File `cacheDir/ics/itmo-schedule-<start>-<end>.ics` (only the latest kept),
  shared through `FileProvider` `${applicationId}.files`. An empty range writes
  nothing.
- UI: `IcsExportBottomSheet` (`feature/settings/ui`, body
  `IcsExportSheetContent` in `:shared:feature-settings`) over
  `IcsExportViewModel`; states Choose, Preparing, Ready, Empty, Failed share
  one fixed-height area. Range and written file live in `SavedStateHandle`;
  after process death the file is written again.

Tests: `commonTest` of `shared/feature-schedule`, `CalendarSyncProviderTest`
against the emulator's CalendarProvider (including Google-like sync-adapter
write-backs) and `SettingsNavigationTest` for the sheet.

## iOS

Both ways exist on iOS (`IosPlatformCapabilities.calendarExport`), with the
shared events, planner, sync file and `DefaultCalendarSync`; platform side in
`shared/feature-schedule/src/iosMain/.../data/calendar/`,
`calendarIosModule`, `iosApp/Sources/Features/Calendar/`.

- Access: turning on asks EventKit for full access
  (`EKEventStore.requestFullAccessToEvents`, `CalendarAccess`,
  `NSCalendarsFullAccessUsageDescription`); write-only access cannot create a
  calendar or read events back. iOS prompts once; a permanent refusal offers
  the app's Settings page.
- `EventKitPhoneCalendars` creates `ITMO.Widgets` in the iCloud source, else
  local; never Exchange, Google or subscribed. After a reinstall a same-titled
  calendar there is adopted. Tag line in the notes, as on Android.
- `EventKitIdStore` maps EventKit string ids to numbers in
  `<noBackup>/calendar_eventkit/ids.json` (format 1; damaged or newer starts
  empty). When iCloud renames an event, the tagged event takes over the old
  number, so nothing is deleted or doubled.
- Work: the `calendar-sync` step of the single refresh task
  (`RefreshStepKeys.CALENDAR_SYNC`, after schedule changes,
  [`docs/ios.md`](../ios.md#background-refresh)), 2-hour period;
  `RefreshTaskCalendarScheduler.runOnce` replaces `calendar-sync-now`.
- `.ics`: `IosIcsFileExport` writes to `tmp/ics`; `IcsExportSheet` (SwiftUI)
  shares via `ShareLink`, so there is no open-in-calendar button.
- Demo: turning on is refused; nothing calls EventKit or My ITMO.
