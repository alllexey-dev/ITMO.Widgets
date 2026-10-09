# Debug tools

`feature/debug` is a screen of developer controls for debug builds. A release
build has no entry to it and reads none of its overrides. The user-facing
counterparts live in [Settings](../settings.md#debug-only-controls).

## Entry and gate

`DebugToolsFragment` is the overlay destination `debug_tools`
(`AppScreen.DEBUG_TOOLS`), opened by «Инструменты разработчика» on the
[profile tab](me.md), a row shown only when `BuildConfig.DEBUG` is true. The
fragment checks `BuildConfig.DEBUG` again before any UI: without it,
`onCreateView` returns an empty view and the ViewModel is never created.
The Konsist rule `debug code is gated` (`DebugRulesTest`) requires that check
in every `File*` store, `*Fragment` and `*RefreshTokenController` of
`core/debug` and `feature/debug` and in `FileAcademicTimeOverrideStore`; the
rest only delegates to them.

## Screen

`DebugToolsScreen(state, actions)` in `feature/debug/ui/DebugToolsScreen.kt` is
a stateless Compose screen built from the kit (`AppTopBar`, `ProgressButton`,
`ItmoSwitch`) that stays in `:app`: debug tools never move to `shared/` or
reach iOS. `DebugToolsFragment` hosts it in `itmoComposeView`, takes
`DebugToolsViewModel` from Hilt and implements `DebugToolsActions` with it;
toasts and activity recreation stay in the fragment. Strings stay in
`strings_debug.xml`.

The top bar «Инструменты разработчика» with «Назад» (test tag
`DebugToolsTestTags.BACK`). Outlined content cards, top to bottom:

- «Тестовый вход по Refresh token»: «Refresh token настроен» or «Refresh token
  не настроен», then «Добавить» or «Заменить» («Проверяем…» while it runs). The
  dialog «Ввести Refresh token» with the password field «Refresh token» (with a
  show/hide toggle) and «Проверить и сохранить»; an empty field shows «Вставьте
  Refresh token». The token lives only in the dialog's memory state, never in
  saved state.
  `DebugRefreshTokenController` stores the token, runs every
  `SessionDataCleaner` and forces a token refresh through MyItmoApi. Success
  shows «Токен проверен и сохранён» and recreates the activity; a failure
  clears the tokens and the data and shows «Токен не подошёл: <ошибка>». The
  demo session refuses with `AppError.DemoUnavailable`.
- «Тестовое академическое время»: «Системная дата» or «Тестовая дата: <дата>»,
  «Выбрать дату» (material3's date picker on the current override or the
  effective date, as a kotlinx `LocalDate`) and
  «Сбросить», enabled only with an override. The override moves
  `AcademicTimeProvider` for the schedule and sport only; sign-in, caches and
  the QR pass keep system time.
- «Тестовые спортивные баллы»: «Данные сервера» or «Тестовые баллы: N за
  посещения + M бонусных», «Настроить» opens «Подменить спортивные баллы» with
  «Баллы за посещения» and «Бонусные баллы»; a value outside 0..9999 shows
  «Нужно целое число от 0 до 9999». «Сбросить» returns to the server data.
- «Шаблонные занятия спорта»: a switch that replaces the sport schedule with
  template lessons for the next days.
- Three one-off checks on the real account: «Проверить изменения расписания»
  (`ScheduleChangeTracking.checkNow()`, the work `schedule-changes-now`),
  «Проверить оценки» (`MarkTracking.checkNow()`, `marks-check-now`) and
  «Проверить продление БАРС» (`BarsSessionProbe.start()`, a read-only probe of
  the BARS cookie renewal that writes only its outcome to logcat).

The section «Подключение к ITMO.Widgets» stays in the screen but is always
hidden: the opt-in lives in the settings. Which dialog is open survives
recreation; what was typed into it does not.

Every override change (date, scores, templates) recreates the activity, so
every screen reads the new value from the start.

## State

`DebugToolsViewModel` exposes one `DebugToolsUiState.Content`: the effective
date, the date override, the score override, the template switch, whether a
refresh token is stored, whether a token check runs, and the opt-in. Events
are `RecreateActivity`, `RefreshTokenUpdated` and `RefreshTokenUpdateFailed`.

## Stable identifiers

Quoted from `StableIdentifiersTest`: the override files
`debug/academic_date_override`, `debug/sport_score_override` and
`debug/sport_lesson_templates` in `noBackupFilesDir`, and the unique work
names `schedule-changes-now` and `marks-check-now`.

## Tests

JVM: `DebugToolsViewModelTest`, `DebugRulesTest`,
`DefaultDebugRefreshTokenControllerTest`. `DebugToolsScreenshotTest` renders the
previews `DebugToolsScreen_{content,refresh-token-dialog,sport-score-dialog}`
(the dialogs without their window) into `app/screenshots/` in light and dark.
`MainNavigationTest` opens the screen in the overlay and leaves it through the
back button's test tag. The screen has no debug host.

## iOS

The iOS app has no debug tools screen: the Me tab shows no developer tools row
(`showDebugTools` is false), and ADR 0016 keeps these tools Android-only. A
Debug build of the iOS app takes launch arguments instead (`-itmoDemo`,
`-itmoSignedOut`, `-itmoOnboarding`, `-itmoRunRefresh`,
`-itmoNotificationFixture`, `-itmoWebLoginFixture`, `-itmoBarsLogin`,
`-itmoBarsRenew`, `-itmoForgetHomeHints`, `-itmoShellSession`), listed with
what they do in [the iOS app](../ios.md). A Release build ignores them.
