# iOS settings

- The iOS app has settings: the shared pages of `SettingsViewModel` in a
  SwiftUI form, opened from the me tab, with the error journal
  («Журнал ошибок») from «Обслуживание».
- Rows iOS does not offer are hidden through `PlatformCapabilities`: the
  quick settings tile, the spoiler animation and image, the calendar and,
  until mark tracking ships on iOS, the recordbook page; Android shows every
  row as before.
- On iOS the background work row is Background App Refresh
  («Обновление контента») and the notification row says «Разрешены в iOS».
- A Kotlin crash on iOS is saved and shown in the error journal on the next
  launch.
