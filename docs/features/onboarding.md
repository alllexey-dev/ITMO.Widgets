# First-run flow

Product behaviour: [Как это работает: вход и аккаунт](../how-it-works/account.md).

The flow runs inside `MainActivity` as the gate key `AppRoutes.Onboarding`, not
a separate activity, after [sign-in](auth.md). Every choice is written through
its own repository the moment it is made, so leaving at any point leaves a
consistent app and nothing is replayed at the end; `Пропустить` is a valid
answer.

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

- `AccountEntries.kt` registers the key with `OnboardingContent` (`:app`,
  `feature/onboarding/ui/`), which draws the stateless `OnboardingScreen` from
  `:shared:feature-account`. The pager has `userScrollEnabled = false`; only the
  footer moves it.
- `OnboardingViewModel` (`OnboardingModule.kt`) lives in the entry's own store,
  and the entry is the only collector of its events: they are one queue, and a
  second collector would take them away.
- The entry does what only Android can: the launcher pin, the notification
  permission and settings page, the spoiler photo picker and crop, links,
  snackbars for failed writes.
- `OnboardingUiState.steps`: three widget steps, the services opt-in, and the
  notifications step only while the opt-in is on. Switching the opt-in off on
  the notifications step returns to the opt-in.

Widget steps (`OnboardingWidgetStep` per `WidgetKind`):

- The preview is the settings renderer (`WidgetPreviewFactory`), hosted by
  `OnboardingWidgetPreviews` as an `AndroidView` and closed when the page is
  released. Rows are the settings rows (`SettingsToggleRow`,
  `SettingsChoiceRow`), wait for the stored appearance instead of showing
  defaults, and write through `WidgetAppearanceRepository`, which refreshes
  installed widgets.
- The QR spoiler image goes through `core/settings/CustomSpoilerRepository`
  (picker and crop shared with settings, `core/ui/spoiler`); `spoilerRevision`
  makes the preview re-read the image.
- Pinning: `AppWidgetManager.requestPinAppWidget` with a broadcast
  `PendingIntent`; `core/ui/widget/WidgetPinRequester` owns the receiver for
  the whole entry lifetime, because the launcher confirms while the screen is
  stopped. Without `isRequestPinAppWidgetSupported` a hint replaces the button.
- Providers are addressed by name through `core/navigation/WidgetProviders`:
  the flow must not import `feature/schedule` or `feature/qr`;
  `WidgetProvidersTest` fails if a provider is renamed.

Services and notifications:

- **The stored-data list must match the Backend schema.** It mirrors the
  tables `users`, `user_groups`, `lessons`, `user_sport_lessons`,
  `sport_*_sign_entries` and `devices`. A new table with user data adds a line
  to `StoredFields` in `OnboardingServicesStep.kt` and its string to
  `strings_onboarding.xml`.
- The notifications step exists only behind the opt-in, because pushes are
  what services send. Its button asks via
  `ActivityResultContracts.RequestPermission` on Android 13+, otherwise or after
  a denial it opens the system settings.

## Replay

`Повторить первоначальную настройку` calls `OnboardingRepository.reset()`; the
gate flips to `Required`. The ViewModel lives in the flow entry's store, so a
replay starts on the first step with nothing carried over.

## iOS

- `OnboardingScreen` (SwiftUI, `iosApp/Sources/Features/Onboarding/`) runs the
  shared `OnboardingViewModel`; the shell's session gate reads the flag
  ([iOS app](../ios.md#data-sharing)).
- A fresh `SavedStateHandle` (`OnboardingIosParameters`) per launch.
- iOS lets no app place a widget, so widget steps show how-to text instead of
  pin and live preview, without the custom spoiler row
  ([degradations](../ios.md#degradations)).
- Notifications ask via `UNUserNotificationCenter`, re-read on foreground.
- Debug: `-itmoOnboarding` shows the flow over the demo session.
