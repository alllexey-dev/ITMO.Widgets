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
- After the first successful load, later refresh failures show a snackbar while
  content stays; the initial failure is an explicit error state.
