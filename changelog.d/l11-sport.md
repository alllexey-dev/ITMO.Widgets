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
