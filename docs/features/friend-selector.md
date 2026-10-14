# Friend picker

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

`Чьё расписание` is the schedule's sheet that chooses whose schedule the tab shows: the own one or a friend's, in one
tap. It belongs to the schedule ([Schedule](schedule.md#friend-picker)); friendships and privacy are in
[Social](social.md). Whether a schedule is open is Backend's answer (`sharing.schedule`); the sheet never decides it.

## Entry and result

- Sheet key `AppRoutes.FriendSelector(selectedIsu)`, content height, opened by the feed's friends button with the
  shown user's ISU or `FriendSelectionContract.NO_USER_ISU` for the own schedule.
- Android: `app/shell/entries/ScheduleEntries.kt` registers it with `FriendPickerRoute` (`:shared:feature-schedule`,
  `ui/friends`); a tap sends one `FriendPick` on the Navigation 3 result bus and closes the sheet; `ScheduleTabRoot`
  takes it with `ResultEffect`. Close delivers nothing.
- iOS: `FriendPickerSheet` (`iosApp/Sources/Features/Schedule/ScheduleSheets.swift`) delivers `FriendPick` through
  `AppRouter.deliver(_:from:)` to the feed's `open(_:onResult:)` and closes.

## Rules

- `FriendPickerViewModel` (`presentation/friends`) reads `FriendRepository` (`core/friend`, social's
  `FriendRepositoryImpl`, behind the opt-in): `Моё` first, then the friends whose schedule is open by name, then the
  others by name.
- Each open row says where the person is now, from today's lessons of their schedule (`ScheduleDays`, refreshed once
  per opening): `на паре до 13:10`, `окно до 15:20`, `первая пара в 10:00`, `пары закончились`, `сегодня без пар`;
  nothing until today is known. The own row says the same about the own day.
- A friend whose schedule is closed to the viewer is dimmed and cannot be picked; no lock and no reason (someone
  else's privacy is not a gate). There is no people search and no «другие друзья».
- The shown schedule is marked with a check.
- States: loading; without the connection a gate with `Подключить` (the services settings); an error with
  `Повторить`; no friends: `Друзей пока нет` with `Друзья и заявки — в Профиле`.

## Tests

`FriendPickerViewModelTest` (the statuses, the order, a hidden friend, the gate), `ScheduleEntriesTest` (the round
trip on Android), `ScheduleUITests` and `ScheduleSnapshotTests` on iOS.
