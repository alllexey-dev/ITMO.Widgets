# Friend selector

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

`feature/friendselector` is the sheet that chooses whose schedule the schedule
tab shows: the own one, a friend's, or any ITMO.Widgets user's found by name.
The schedule side (FAB, selected user, header) is in
[Schedule](schedule.md#friend-picker); friendships and privacy in
[Social](social.md). Whether a schedule is open is Backend's answer
(`sharing.schedule`); the sheet never decides it.

## Entry and result

- Sheet key `AppRoutes.FriendSelector(selectedIsu)`, opened by the schedule FAB
  with the shown user's ISU or `FriendSelectionContract.NO_USER_ISU` for the own
  schedule. Policy `SheetPolicy.Height.TALL`.
- `shell/entries/SocialEntries.kt` registers it with `FriendSelectorSheetRoute`
  (`:shared:feature-social`), which takes `FriendSelectorViewModel` (reads
  `ARG_SELECTED_ISU`) and renders the stateless `FriendSelectorSheetContent`
  (states in `FriendSelectorBody`).
- Apply: the ViewModel records a chosen friend in the history, then emits
  `FriendSelectorEvent.Apply(target)` once (`null` for the own schedule). The
  entry sends one `FriendSelection` on the Navigation 3 result bus and closes the
  sheet; `ScheduleTabRoot` takes it with `ResultEffect`. Close delivers nothing;
  opening a profile closes the sheet first.

## Rules

- `Друзья` filters the loaded list locally, case-insensitively, by name, ISU,
  group, faculty short name or course. `Все` searches `PeopleSearchRepository`
  300 ms after the last keystroke and shows only registered people; switching
  back to `Друзья` cancels the search.
- Only a person with an open schedule can be chosen; a closed row opens the
  profile instead. The opening ISU becomes the choice once, on the first loaded
  list, if it is a listed friend with an open schedule; an empty friend list
  resets the choice to the own schedule.
- `RecentFriendOrder` fixes the recent chips when the list first loads: the
  opening person, then the history, only friends with an open schedule, at most
  five. Later updates refresh names and avatars but never reorder; a removed
  friend or a closed schedule drops out.
- `pending_friend_isu` and `recent_friend_order` survive recreation in
  `SavedStateHandle`; the route saves the typed query across process death.
- Retry repeats the people search under `Все` and forces the friend list
  otherwise. `Disabled` has no retry.

## Data

- `FriendRepositoryImpl` exposes `SocialRepository` friends as `UserSummary`;
  the opt-in gate is in `SocialRepository` (`LoadState.Disabled`).
- `DataStoreFriendSelectionHistory` keeps `recent_schedule_friends` in
  `app_preferences`: ISUs, newest first, no repeats, at most five. It is a
  `SessionDataCleaner` (qualifier `friend-history`), so sign-out forgets it.
- Both are built by `friendSelectorModule` in `:shared:feature-social`
  `commonMain`. Tests are in the same module.

## iOS

The iOS app ([iOS app](../ios.md)) hosts the same route as a full-height sheet:
`FriendSelectorSheet` (`iosApp/Sources/Features/Schedule/`) shows
`friendSelectorViewController` (`shared/ios` `screens/ScheduleScreens.kt`), and
`FriendSelectorIosRoute` (`iosMain`) seeds the `SavedStateHandle` with
`ARG_SELECTED_ISU`. What differs:

- The schedule root opens the key with `AppRouter.open(_:onResult:)`; the choice
  comes back once through `deliver(_:from:)` as a `FriendPick` and reaches
  `ScheduleRoute` as `ScheduleRouteRequest.SelectUser`. Close and drag-down
  deliver nothing.
- A profile opens on the schedule's stack in the sheet's place.
- Tests: `ScheduleUITests` (demo session) and `RouterTests`.
