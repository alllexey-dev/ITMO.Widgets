# Navigation

- Fixed in the Compose shell (2.3.0-beta.1): links and system pages opened
  from its screens did nothing. «Политика конфиденциальности» and
  «Удалить аккаунт ITMO.Widgets» in the settings, the links on a subject
  page and in its «Ссылки» sheet and a review's source said «Нет приложения,
  чтобы открыть ссылку»; «Открыть настройки» in the calendar access dialog
  did nothing. `ShellContent` now provides the activity's
  `AndroidPlatformActions`, and `LocalPlatformActions` has no default, so a
  host that forgets it fails at once instead of turning every action into a
  no-op (`ShellPlatformActionsTest`).
