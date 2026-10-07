# Friend selector

`feature/friendselector` is the sheet that chooses whose schedule the schedule
tab shows: the own one, a friend's, or any ITMO.Widgets user's found by name.
The schedule side (the FAB, the selected user, the header) is in
[Schedule](schedule.md#friend-picker); friendships and privacy in
[Social](social.md). Whether a schedule is open is Backend's answer
(`sharing.schedule`); the sheet never decides it.

## Entry and result

`FriendSelectorDialogFragment` is the dialog destination `friend_selector`
of `main_nav_graph`, opened by the schedule FAB with
`FriendSelectionContract.ARG_SELECTED_ISU` (the shown user, or `NO_USER_ISU`
for the own schedule). It is the design system's sheet host
(`ItmoBottomSheetFragment` with `SheetHeight.Tall`): expanded at 90 % of the
screen height, never collapsed. Its body is `FriendSelectorSheetRoute` from
`:shared:feature-social`, which obtains the Koin `FriendSelectorViewModel`
(reading the opening ISU from the host's arguments) and renders the stateless
`FriendSelectorSheetContent`; the iOS app hosts the same route.

Apply asks the ViewModel, which records a chosen friend in the history and
then emits `FriendSelectorEvent.Apply(target)` once (`target` is the person,
or `null` for the own schedule). The route passes it to the host, which sets
one fragment result under `FriendSelectionContract.RESULT_KEY` with
`RESULT_USE_MY_SCHEDULE`, `RESULT_USER_ISU`, `RESULT_USER_NAME` and
`RESULT_USER_PICTURE_URL`, then dismisses the sheet. Close («Закрыть»)
delivers nothing. Opening a profile dismisses the sheet first.

## Sheet

Title «Чьё расписание?», then:

- «Недавние»: the own chip «Моё» (TalkBack «Моё расписание») with the own
  avatar in every state, then up to five recent friends.
- The toggle «Друзья» / «Все», shown once the friend list has loaded.
- The search field: «Имя, ИСУ или группа» for friends, «Имя или фамилия» for
  everybody.
- The list or a state, then the filled button «Показать моё расписание» or
  «Показать расписание: <имя>» (the first word of the name), enabled once the
  friend list has loaded, even empty.

A row shows the avatar, the name and «<ИСУ> • <группы>» (up to two groups,
else «<группа> • и ещё N», or «Нет группы»). A closed schedule dims the row,
adds «Расписание скрыто» and a lock; a tap on it opens the person's profile and
dismisses the sheet. A long press opens the profile for any row. The chosen row
has a tinted background and a check; the icon column is reserved, so choosing
never rewraps the name.

## States

`FriendSelectorBody`:

| State | Shows |
|---|---|
| `Loading`, `PeopleLoading` | progress; a reload keeps a non-empty list instead |
| `Users` | the rows |
| `NoMatches` | «Ничего не нашлось» |
| `PeopleIdle` | «Кого ищем?» / «Показываем только пользователей ITMO.Widgets» |
| `PeopleEmpty` | «Никого не нашли» / «Среди пользователей ITMO.Widgets нет таких» |
| `NoFriends` | «Друзей пока нет» / «Найдите знакомых по имени и отправьте заявку» |
| `Disabled` | «Не удалось загрузить» / «Нет подключения к ITMO.Widgets», no retry |
| `Error`, `PeopleError` | «Не удалось загрузить» with the error's message and «Повторить» |

«Повторить» repeats the people search under «Все» and forces the friend list
otherwise.

## Behaviour

- «Друзья» filters the loaded list locally, case-insensitively, by name, ISU,
  group name, faculty short name or course.
- «Все» searches `PeopleSearchRepository` 300 ms after the last keystroke and
  shows only registered people. An empty query is `PeopleIdle`; going back to
  «Друзья» cancels the search.
- Only a person with an open schedule can be chosen. The opening ISU becomes
  the choice once, on the first loaded list, if it is a listed friend with an
  open schedule; an empty friend list resets the choice to the own schedule.
- The recent chips are fixed by `RecentFriendOrder` when the list first loads:
  the opening person, then the history, only friends with an open schedule, at
  most five. Later updates refresh names and avatars but never reorder; a
  removed friend or a closed schedule drops out.
- The pending choice (`pending_friend_isu`) and the chip order
  (`recent_friend_order`) survive recreation in `SavedStateHandle`; the typed
  query is saved by the route and reaches the ViewModel again after process
  death.
- Apply runs once: a chosen friend is recorded in the history, then the result
  is sent. A new choice joins the chips on the next opening.

## Data

- `FriendRepositoryImpl` exposes `SocialRepository` friends as `UserSummary`
  values; the opt-in gate is in `SocialRepository`, which answers
  `LoadState.Disabled`.
- `DataStoreFriendSelectionHistory` keeps `recent_schedule_friends` in
  `app_preferences`: ISUs, newest first, without repeats, at most five. It is a
  `SessionDataCleaner`, so sign-out forgets it.
- Both live in `:shared:feature-social` `commonMain` and `friendSelectorModule`
  constructs them with the ViewModel; the history's cleaner is bound under the
  `friend-history` qualifier.

## Tests

- JVM: `FriendSelectorViewModelTest`, `RecentFriendOrderTest`,
  `DataStoreFriendSelectionHistoryTest` (reads a 2.2 value),
  `FriendRepositoryImplTest` and `FriendSelectorModuleTest`.
- `FriendSelectorSheetTest` (`androidHostTest`) covers the chips across a
  refresh, a choice and recreation, failed and disabled lists, the rows and
  closed schedules, the chip labels, the scope hint and the query, and long
  names at a narrow width with a large font.
- `SocialScreenshotTest` records every state of the sheet
  (`FriendSelectorSheetContent_<state>`: friends, selection, locked, groups,
  empty-filter, people, people-idle, people-loading, people-empty,
  people-error, no-friends, loading, disabled, error) in four appearances.
- The sheet renders with `FriendSelectorFixture` in
  `SettingsNavigationTestActivity` (debug).
