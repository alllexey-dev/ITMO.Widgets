# Settings

- The settings pages, their dialogs, «Журнал ошибок» and the «Выгрузить в
  .ics» sheet are drawn by Compose screens of `:shared:feature-settings`
  over shared view models; `SettingsFragment`, `DiagnosticsFragment` and
  `IcsExportBottomSheet` stay as the Android hosts with their navigation ids,
  tags and intents. Copy, layout and behaviour are unchanged.
- In the `.ics` sheet «Отправить» and «Открыть в календаре» stand side by side
  when both fit and stack on narrow screens or with large text.
- The settings page model (`SettingItem`, `SettingSection`, `SettingsPage`,
  `SettingRowId`, `SettingsEvent`) stays free of Android, `R`, Compose and
  resource ids; `SettingsRulesTest` enforces it.
