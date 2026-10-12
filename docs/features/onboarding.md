# First-run flow

The flow runs inside `MainActivity` as the gate key `AppRoutes.Onboarding`, not
as a separate activity. The sign-in screen ([Sign-in](auth.md)) is the
signed-out welcome: the application name, three lines of what the app does,
and the two sign-in buttons. After sign-in the flow walks one screen per
decision and then the bottom navigation appears for the first time. There is
no summary screen: the last decision's footer says `Готово`.

Every choice is written through its own repository the moment it is made, so
leaving at any point leaves a consistent app and nothing is replayed at the end.
`Пропустить` is a valid answer, not a loss of state.

## The gate

`OnboardingRepository` (`core/onboarding`, implementation over `UtilityStorage`)
stores one flag per installation. It survives sign-out: a second account on the
same device does not repeat the flow.

`OnboardingGateViewModel` combines the session state with that flag into
`Unknown`, `Required` and `Passed` (`OnboardingGate`). `Unknown` is not "not
required": it is the frame before DataStore answers, and `ShellGate.surface`
keeps the session progress (`ShellSurface.Progress`) during it rather than
starting at Home and switching a frame later.

While the gate is `Required` the flow owns the window
(`ShellSurface.Onboarding`): the bottom bar and the overlays are hidden, `ShellGate.check` ignores every
open, the update offer is not checked, and an entry intent waits in the
`RouteQueue` until the tabs show, exactly as it waits during `Initializing`.
When the gate flips to `Passed` the shell shows the tabs and the flow's entry
leaves the window.

## Steps

`AccountEntries.kt` registers the key with `OnboardingContent` (`:app`,
`feature/onboarding/ui/`), which draws `OnboardingScreen` from
`:shared:feature-account`: the progress dots (the design system's
`StepsIndicator`: 8 dp dots with 8 dp gaps, the current one a 24 dp pill, not
tappable), a `HorizontalPager` with `userScrollEnabled = false` that only the
footer moves, and a footer with `Пропустить` and `Далее`/`Готово` that clears
the navigation bar. Back on the first step leaves the flow to the system; on
later steps it is a step back. The screen is stateless: `OnboardingViewModel`
is Koin's (`OnboardingModule.kt`, `viewModelOf`), in the entry's own store,
and the entry is the only collector of its events, because the events are one
queue and a second collector would take them away. The entry serves what only
Android can do: the launcher pin, the notification permission and settings
page, the spoiler photo picker and crop screen, links, and snackbars for a
failed image or write.

The step list is `OnboardingUiState.steps`: three widget steps, the services
opt-in, and the notifications step only while the opt-in is on. Switching the
opt-in on adds a dot; switching it off removes it, and a flow standing on the
notifications step returns to the opt-in.

**One widget per step** (`OnboardingWidgetStep` for a `WidgetKind`): the
real preview renderer used by settings (`WidgetPreviewFactory`) in the
screen's preview slot, then the widget's own choices as the settings screen's
own toggle rows (`SettingsToggleRow`), then one full-width
`Добавить на главный экран`. `OnboardingWidgetPreviews` fills the slot with an
`AndroidView` of the preview's View: built when the page is composed, bound to
every appearance change, stopped with the entry and closed when the page is
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
a failed one is a snackbar from the entry.

The pin button calls `AppWidgetManager.requestPinAppWidget` with a broadcast
`PendingIntent`; `core/ui/widget/WidgetPinRequester` (shared with the home feed
hint) owns that receiver for the whole entry lifetime, because the launcher
confirms the pin while this screen is stopped. A confirmed pin changes only
the button's label and icon; nothing moves. When
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
`OnboardingRepository.reset()` and closes the overlays. The gate then flips
to `Required` and the shell shows the flow again. The ViewModel lives in the
flow entry's own store, which goes with the entry, so a replay starts on the
first step with nothing carried over from the previous run.

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

On `:app`'s JVM tests, `AccountEntriesTest` checks that the flow fills the
window and `ShellHostTest` that the gate follows the session and the
first-run flag and that a pending route runs once after the flow. The
instrumented `MainActivitySessionRoutingTest` finds the flow by its root test
tag `onboarding_root`.
