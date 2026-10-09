# Sport

- The sport tab and another user's sport are Compose screens of
  `:shared:feature-sport` (`SportScreen`, `UserSportScreen`) that Android, the
  Nav3 shell and iOS host alike; `SportFragment` and `UserSportFragment` keep
  their names as thin hosts.
- «Мой спорт» and «Запись» keep their swipe inside the tab: a fling stops at
  «Запись», and only a new swipe at either end switches the bottom tab.
- The sport ViewModels live in the tab's own store instead of the activity's;
  the week and the filters of «Запись» still survive a trip through the other
  tabs.
- Another user's sport offers «Открыть на карте» for a booking whose place has
  an address.
- Signing out also forgets the auto-sign limits, the queue list and friends'
  bookings of the sport tab, so the next account never sees them.
- The prediction of a lesson you are booked on is no longer shown as booked:
  it offers auto-sign again, with the «Прогноз» status and the occupancy,
  instead of a bare «Недоступно» and «Вы записаны» in the details
  (`SportScheduleRepositoryImpl`, `SportBookingConditions`).
