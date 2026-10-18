# First-run flow

Product behaviour: [Как это работает: вход и аккаунт](../how-it-works/account.md).

The flow runs inside `MainActivity` as the gate key `AppRoutes.Onboarding`, not
a separate activity, after [sign-in](auth.md). Every choice is written through
its own repository the moment it is made, so leaving at any point leaves a
consistent app and nothing is replayed at the end.

## The gate

- `OnboardingRepository` (`core/onboarding`, over `UtilityStorage`) stores one
  flag per installation. It survives sign-out: a second account on the device
  does not repeat the flow.
- `OnboardingGateViewModel` combines the session with the flag into
  `Unknown`, `Required`, `Passed` (`OnboardingGate`). `Unknown` is the frame
  before DataStore answers; `ShellGate.surface` keeps `ShellSurface.Progress`
  during it instead of flashing Home.
- While `Required` the flow owns the window (`ShellSurface.Onboarding`): no
  bottom bar or overlays, `ShellGate.check` ignores every open, the update
  offer is not checked, and an entry intent waits in the `RouteQueue` until
  the tabs show.

## Steps

Three steps (DECISIONS.md, Первый запуск), the same on both platforms over one
`OnboardingViewModel` (`:shared:feature-account`, `feature/onboarding`): the step
dots with `1 из 3`, the step's title and line over its scrolling body, and its
actions pinned at the bottom. Back (the system back on Android, the back button
on iOS) goes one step back. There is no `Включить всё` and no `Пропустить`:
`Далее` works without any permission, and `Не сейчас` is a valid answer on the
last step.

1. `Всё уже на месте` (`OnboardingStep.VALUE`): the user's own data as it loads,
   never samples. The chip with the name and initials from the ID token; today's
   lesson now or next by the timeline's rules (`LessonTimelines`), else
   tomorrow's first, as the kit's lesson card under `Сейчас`, `Далее` or
   `Завтра` (`DayLessonsGateway`, refreshed once); the sport total of the
   current period (`SportScoreRepository`, `30 из 100`). Without data the rows
   keep their descriptions and the lesson block is absent. The pass, the
   sourced scores and the widgets are rows that say what they are.
2. `Разрешения` (`PERMISSIONS`): each permission with its own button, or the
   `разрешено` pill once granted. Notifications ask once (Android 13+
   `RequestPermission`, iOS `requestAuthorization`); after a refusal the button
   opens the system page. `Добавить пары` (where `PlatformCapabilities.calendarExport`)
   asks for the calendar and then turns on the calendar sync
   (`CalendarSync.enable`, [calendar](calendar.md)). The note says widgets can
   be added later in Профиль, «Виджеты».
3. `Друзья и очередь` (`CONNECTION`): what the connection gives, a row that
   opens the consent sheet to read what the server keeps, then `Подключить`
   and `Не сейчас`. `Подключить` opens the consent sheet (`ServicesConsentSheet`,
   the one of «Подключение и данные», [settings](../settings.md#account-and-services));
   only its own `Подключить` turns the opt-in on, and the flow ends. Already
   connected (a replay), the step says so and the button is `Готово`.

- The entry (`AccountEntries.kt`, `OnboardingContent` in `:app`) does what only
  Android can: the notification and calendar permissions and the consent sheet
  kept in its saved state. Its events are one queue with one collector.
- **The stored-data list must match the Backend schema.** `ServicesStoredData`
  (`core/services`) mirrors the tables `users`, `user_groups`, `lessons`,
  `user_sport_lessons`, `sport_*_sign_entries`, the private links and
  `devices`; a new table with user data adds a line and its `services_stored_*`
  string. The connection page, the consent sheet and this step read the same list.

## Replay

`Повторить первоначальную настройку` calls `OnboardingRepository.reset()`; the
gate flips to `Required`. The ViewModel lives in the flow entry's store, so a
replay starts on the first step with nothing carried over.

## iOS

- `OnboardingScreen` (SwiftUI, `iosApp/Sources/Features/Onboarding/`) runs the
  shared `OnboardingViewModel` with a fresh `SavedStateHandle`
  (`OnboardingIosParameters`) per launch; the shell's session gate reads the flag
  ([iOS app](../ios.md#data-sharing)).
- The same three steps; the calendar asks through `CalendarAccess`, the consent
  is the SwiftUI `ServicesConsentSheet`.
- Notifications ask via `UNUserNotificationCenter`, re-read on foreground.
- Debug: `-itmoOnboarding` shows the flow over the demo session.
