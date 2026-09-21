# Schedule

`feature/schedule` shows the own academic schedule from MyITMO, a friend's
schedule chosen through the picker, and optional pending sport rows.

## Data

- `ScheduleFragment` takes an optional `ARG_USER_ISU`. Without it the screen
  shows the signed-in user and offers the friend picker FAB; with it the same
  screen shows another user and hides the picker. `UserScheduleFragment` wraps
  it with a titled header when opened from a public profile.
- The cache is a file per user and date range with a 24-hour TTL. A refresh keeps
  the loaded range, including pages fetched by scrolling, and replaces it with one
  snapshot after a successful response; intermediate partial lists are never
  published. Dates missing from the response are removed; other ranges and users
  are kept.
- A definitive denial of a foreign schedule (`Forbidden`) removes that user's
  cache and visible content without touching own or other users' data. A
  generation check prevents a late concurrent response from restoring a revoked
  cache. Ordinary network failures keep cached content.
- The first frame comes from memory: `ScheduleRepository.peekScheduleForRange`
  returns the range without touching the disk when every date is already
  hydrated, so `loadInitialSchedule` publishes `Content(loadingMore = true)` at
  once and only a screen with nothing cached shows the skeleton
  (`schedule_skeleton`) until the cache flow answers.

## Friend picker

`feature/friendselector` is a bottom sheet opened from the schedule FAB and
answered through `FriendSelectionContract` fragment results.

- Recent chips (own schedule first, then up to five recent friends), a
  `Друзья / Все` toggle, a search field, the list and a filled apply button.
- `Друзья` filters the loaded friend list locally by name, ISU or group. `Все`
  searches ITMO.Widgets users by name through `PeopleSearchRepository` after a
  300 ms debounce and shows only registered people.
- A row is selectable only when its schedule is open (`sharing.schedule`).
  Closed rows show a lock and open the public profile on tap; long-press opens
  the profile for any row. Applying records the choice in the recent history.
- Recent-chip identities and order are fixed when the loaded selector first opens
  and survive view recreation. Pending taps change only selection markers; a new
  choice enters the recent history only after Apply and appears on the next opening.
  Profile refreshes update metadata in place; removed or private schedules stop
  being selectable without reordering the remaining chips.
- The own chip reads the current user from its own flow, so an empty or failed
  friend list still shows the avatar.

## Pending sport rows

The `Автозапись на спорт` preference (`SchedulePreferencesRepository` in
`core/schedule`, default off, DataStore) adds the user's own active sport queues
to the schedule and both schedule widgets. It never applies to friends' schedules
or the official cache.

- `PendingSportBookingsRepository` (`core/sport`, implemented in sport data)
  projects active own queues: cancelled, terminal, started and already-signed
  entries are excluded; several queues resolving to the same lesson produce one
  row; unpublished predictions use prototype dates plus two weeks, a bound real
  lesson its actual dates.
- The schedule observes the projection only when the preference is on and both
  the selected and loaded schedule belong to self. The Backend opt-in remains an
  independent gate in the data layer.
- `ScheduleUiState.Content.schedule` stays official data; `ScheduleDisplayDay`
  adds pending rows, including dates without official lessons. Rows are labelled
  waiting or predicted, never styled as a confirmed lesson, and do not change the
  lesson count. An error or empty snapshot from the optional source removes the
  pending rows; it never replaces the academic screen.
- The adapter diffs the whole display day, so live additions and cancellations
  render without clearing the cache or resetting scroll.

## Behaviour

- Timeline markers and day alpha follow [`design.md`](../design.md).
- The list snapshots its scroll position before the view is destroyed; restoration
  waits for data, and adapter callbacks never touch an old view.
- Switching between own and a friend's schedule keeps the reader on the day and
  offset they were reading: the visible day is anchored by date, the closest later
  day is used when that date has no lessons, and a day past the initial range is
  paged in (up to four pages) before the list is shown. An unreachable day opens
  the schedule on today instead.
- After the first successful load, later refresh failures show a snackbar while
  content stays; the initial failure is an explicit error state.

## Lesson details

- Every ordinary lesson card opens `LessonDetailsBottomSheet` from the schedule
  fragment's child fragment manager, in the own and in a friend's schedule alike.
  Pending sport rows never open it. A sport lesson (type 11) in the own schedule goes through
  `AppNavigator.openLessonDetails` to `MainActivity`, which finds the booking
  with the same date and start in the sport tab's data (a confirmed booking
  before a queue, then the matching section name) and opens the sport sheet
  with `Отменить`; without a match the lesson sheet appears. The sheet gets a `Serializable`
  `LessonDetailsArgs` built from the `Lesson` plus the day's date; nothing is
  fetched for the lesson itself.
- The sheet starts with the header every details sheet shares
  (`view_details_header.xml`, bound through `core/ui/DetailsHeader.kt`): subject,
  type and format with the type colour, the weekday and date with the time
  range and duration, teacher, room with the full building name and
  `Открыть на карте`. Then, each only when present: the meeting info and
  password when MyITMO sends them, note. Buttons:
  `Открыть на карте` (a known building from `core/location/BuildingDirectory`,
  else the raw building text) through the generic `geo:` intent in
  `core/ui/navigation/MapLauncher`, and `Открыть видеозвонок` when the lesson
  carries a link (MyITMO calls the field `zoom_url`, but lessons run on any
  platform, so the link itself is not shown). No map provider setting. A card
  whose lesson carries a link shows a small camera icon next to the type.
- A pending sport row (queue or auto-sign prediction) goes through
  `AppNavigator.openPendingSportDetails`. `MainActivity` looks the lesson up in
  the sport tab's `SportBookingRepository` (refreshing once when nothing is
  cached) and opens the sport tab's own `SportCommonDetailsBottomSheet` with its
  queue position, history and `Отменить`; the cancellation runs through the
  shared `SportMyViewModel` after the usual confirmation. Only when the sport
  data has nothing about the queue does the schedule's own
  `PendingSportDetailsBottomSheet` appear: the shared header, the status as
  the kind line, one `Условия записи` card and `Открыть в спорте`.
- `Друзья на паре` is the only place friends on a lesson appear.
  `LessonDetailsViewModel` asks `LessonFriendsRepository` for
  `GET /api/schedule/lessons/{pairId}/friends?date=`; Backend answers with the
  viewer's accepted friends who attend that occurrence and share their schedule
  with the viewer. Without the ITMO.Widgets opt-in the block is absent; while
  loading it shows a spinner; an error offers `Повторить`; an empty answer says
  so. A friend row opens the public profile.
- Schedule changes are not shown here; their detection belongs to v2.2.
