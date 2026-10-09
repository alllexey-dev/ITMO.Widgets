# First-run flow

The flow runs inside `MainActivity` as a root destination, not as a separate
activity. `AuthFragment` is the signed-out welcome: the application name, three
lines of what the app does, and the two sign-in buttons. After sign-in the flow
walks one screen per decision and then the bottom navigation appears for the
first time. There is no summary screen: the last decision's footer says `Готово`.

Every choice is written through its own repository the moment it is made, so
leaving at any point leaves a consistent app and nothing is replayed at the end.
`Пропустить` is a valid answer, not a loss of state.

## The gate

`OnboardingRepository` (`core/onboarding`, implementation over `UtilityStorage`)
stores one flag per installation. It survives sign-out: a second account on the
same device does not repeat the flow.

`OnboardingGateViewModel` combines the session state with that flag into
`Unknown`, `Required` and `Passed`. `Unknown` is not "not required": it is the
frame before DataStore answers, and `MainActivity` keeps the session progress
indicator during it rather than starting at Home and switching a frame later.

While the gate is `Required` the flow owns the window: the bottom bar and the
overlay container are hidden, `openScreen` refuses, the update offer is not
checked, and a widget intent is kept in `pendingRootDestination` until the gate
passes — exactly as it is kept during `Initializing`. When the gate flips to
`Passed` the root graph moves to `navigation_home`, popping the flow inclusively.

## Steps

`OnboardingFragment` (destination `onboarding`) hosts `OnboardingScreen` from
`:shared:feature-account` in a `ComposeView`: the progress dots (the design
system's `StepsIndicator`: 8 dp dots with 8 dp gaps, the current one a 24 dp
pill, not tappable), a `HorizontalPager` with `userScrollEnabled = false` that
only the footer moves, and a footer with `Пропустить` and `Далее`/`Готово`
that clears the navigation bar. Back on the first step leaves the flow to the
system; on later steps it is a step back. The screen is stateless:
`OnboardingViewModel` is Koin's, in the Fragment's store, and the Fragment is
the only collector of its events, because the events are one queue and a
second collector would take them away. The Fragment serves what only Android
can do: the launcher pin, the notification permission and settings page, the
spoiler photo picker and crop screen, links, and snackbars for a failed image
or write.

The step list is `OnboardingUiState.steps`: three widget steps, the services
opt-in, and the notifications step only while the opt-in is on. Switching the
opt-in on adds a dot; switching it off removes it, and a flow standing on the
notifications step returns to the opt-in.

**One widget per step** (`OnboardingWidgetStep` for a `WidgetKind`): the
real preview renderer used by settings (`WidgetPreviewFactory`) in the
screen's preview slot, then the widget's own choices as the settings screen's
own toggle rows (`SettingsToggleRow`), then one full-width
`Добавить на главный экран`. The Fragment fills the slot with an `AndroidView`
of the preview's View: built when the page is composed, bound to every
appearance change, stopped with the Fragment and closed when the page is
released. The rows are `WidgetOption`s per kind: next lesson early and hidden
teacher for the compact schedule; hidden teacher, hidden past lessons and
tomorrow for the full schedule; dynamic colours and the spoiler for QR. The two
schedule steps end with the settings screen's `Размер текста` choice row
(`SettingsChoiceRow` and the same single-choice dialog). Rows wait for the
stored appearance instead of showing defaults. Every row writes through
`WidgetAppearanceRepository`, which also refreshes the installed widgets. The
single-lesson preview is as tall as the widget itself; only the day list gets
the bounded 160 dp area.

The QR step ends with `Изображение спойлера` instead: the same row shape with
`Стандартное` or `Своё изображение` as its value, dimmed while the spoiler is
off or while an image is being written. A tap opens the Android photo picker
and the square crop screen shared with settings (`core/ui/spoiler`); with a
custom image already stored the tap first asks `Выбрать другое` or `Вернуть
стандартное`. The write goes through `core/settings/CustomSpoilerRepository`,
which refreshes the installed widgets itself; the ViewModel counts every
successful change in `spoilerRevision` so the preview re-reads the image, and
a failed one is a snackbar from the Fragment.

The pin button calls `AppWidgetManager.requestPinAppWidget` with a broadcast
`PendingIntent`; `core/ui/widget/WidgetPinRequester` (shared with the home feed
hint) owns that receiver for the whole Fragment lifetime, because the launcher
confirms the pin while this screen is stopped. A confirmed pin changes only the button's label and icon; nothing moves. When
`isRequestPinAppWidgetSupported` is false the button is replaced by one hint
line pointing at the launcher's widget menu. Providers are addressed by name
through `core/navigation/WidgetProviders`: the flow is its own feature and must
not import `feature/schedule` or `feature/qr`; `WidgetProvidersTest` fails if a
provider is renamed.

**Services.** One switch row, `Подключиться`, in the same card as any
settings toggle; while the opt-in runs a spinner takes the switch's slot so the
row never changes height. Below it the privacy default line, `Что даёт` with
three capability rows, and `Что хранит сервер` with one line per stored field
followed by the two notes (the ITMO.ID token is checked and not stored;
everything is deleted with the account) and the `Код сервера` link.

**The stored-data list must match the Backend schema.** It mirrors the tables
`users`, `user_groups`, `lessons`, `user_sport_lessons`, `sport_*_sign_entries`
and `devices`. A new table holding user data adds a line to `StoredFields` in
`OnboardingServicesStep.kt` and its string to `strings_onboarding.xml`; one
string per line, so a schema change edits one line.

**Notifications.** Present only behind the opt-in, because the pushes are what
services send. One status row (`Разрешены`/`Выключены` with a line of what that
means) and one button that keeps its place: `Разрешить` asks through
`ActivityResultContracts.RequestPermission` on Android 13+; after a denial, and
on older Android, it is `Открыть настройки`; once granted it is
`Настроить в Android`, the system page where channels live.

## Replay

`Повторить первоначальную настройку` in maintenance calls
`OnboardingRepository.reset()` and closes the overlay stack. The gate then flips
to `Required` and the root graph returns to the flow with an empty back stack.
The ViewModel is scoped to `OnboardingFragment`, so a replay starts on the first
step with nothing carried over from the previous run.

## iOS

`OnboardingScreen` (SwiftUI, `iosApp/Sources/Features/Onboarding/`) runs the
shared `OnboardingViewModel`; the shell reads `OnboardingGateViewModel`'s flag
through its session gate, so an unread flag keeps the loading gate
([iOS app](../ios.md#data-sharing)).

- Steps and their order are Android's. The ViewModel gets a fresh
  `SavedStateHandle` (`OnboardingIosParameters`), so a relaunch in the middle
  starts at the first step.
- Widget steps: iOS lets no app place a widget, so each shows how to add one
  from the home screen (`ios_onboarding_widget_howto_title`,
  `ios_onboarding_widget_howto_text`) above the same appearance rows, without
  the custom spoiler image row and without a live preview. The QR widget does
  not follow the spoiler switch on iOS yet
  ([degradations](../ios.md#degradations)).
- The notifications step asks with `UNUserNotificationCenter`, then opens the
  app's notification settings (`onboarding_notifications_open_settings`,
  `ios_onboarding_notifications_configure` once allowed); the status is read
  again when the app returns to the foreground.
- A back button above the step dots replaces the system back.
- `Повторить первоначальную настройку` in settings resets the same flag.
- A Debug build launched with `-itmoOnboarding` shows the flow over the demo
  session, which otherwise skips it.
- Tests: `ITMOWidgetsTests/OnboardingTests`,
  `SnapshotTests/OnboardingSnapshotTests`, `UITests/OnboardingUITests`.

## Verification

The screen is checked on the JVM in `:shared:feature-account`:
`OnboardingScreenTest` (48 dp footer targets, no clipping at font scale 1.3 and
320 dp, the services switch adding the notifications dot in place) and the
four-appearance goldens of every step state in `AccountScreenshotTest`
(`scripts/verify.sh shots feature-account`).

`OnboardingVisualTest` keeps only what needs a device. It runs the real flow in
the debug host (`SettingsNavigationTestActivity` with
`startDestination = R.id.onboarding` and `onboardingFixture`) and asserts that
every widget page draws its real preview in the slot at the widget's height,
and that the pin button reaches `requestPinAppWidget` (the launcher's dialog
takes the focus). `MainActivitySessionRoutingTest` finds the flow by its root
test tag `onboarding_root`.
