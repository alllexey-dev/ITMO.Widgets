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

`OnboardingFragment` hosts the progress dots (`OnboardingStepsView`: 8 dp dots
with 8 dp gaps, the current one a 24 dp pill, not tappable), a `ViewPager2`
with `isUserInputEnabled = false`, and a footer with `Пропустить` and
`Далее`/`Готово`. Back on the first step leaves the flow to the system; on later
steps it is a step back. The pages share `OnboardingViewModel` scoped to
`OnboardingFragment`; every event of that ViewModel is handled by the host,
because the events are one `Channel` and a second collector inside a page would
take them away.

The step list is `OnboardingUiState.steps`: three widget steps, the services
opt-in, and the notifications step only while the opt-in is on. Switching the
opt-in on adds a dot; switching it off removes it, and a flow standing on the
notifications step returns to the opt-in.

**One widget per step** (`WidgetStepFragment` with a `WidgetKind` argument):
the real preview renderer used by settings (`WidgetPreviewFactory`) bound to the
current appearance, then the widget's own choices as the settings screen's own
toggle rows (`item_setting_toggle.xml`), then one full-width
`Добавить на главный экран`. The rows are `WidgetOption`s per kind: next lesson
early and hidden teacher for the compact schedule; hidden teacher, hidden past
lessons and tomorrow for the full schedule; dynamic colours and the spoiler for
QR. The two schedule steps end with the settings screen's `Размер текста`
choice row (`item_setting_row.xml` and the same single-choice dialog). Every
row writes through `WidgetAppearanceRepository`, which also refreshes the
installed widgets. The single-lesson preview is as tall as the widget
itself; only the day list gets the bounded 160 dp area.

The QR step ends with `Изображение спойлера` instead: the same row shape with
`Стандартное` or `Своё изображение` as its value, dimmed while the spoiler is
off or while an image is being written. A tap opens the Android photo picker
and the square crop screen shared with settings (`core/ui/spoiler`); with a
custom image already stored the tap first asks `Выбрать другое` or `Вернуть
стандартное`. The write goes through `core/settings/CustomSpoilerRepository`,
which refreshes the installed widgets itself; the ViewModel counts every
successful change in `spoilerRevision` so the preview re-reads the image, and
a failed one is a snackbar from the host.

The pin button calls `AppWidgetManager.requestPinAppWidget` with a broadcast
`PendingIntent`; `core/ui/widget/WidgetPinRequester` (shared with the home feed
hint) owns that receiver for the whole Fragment lifetime, because the launcher confirms the pin while this screen is stopped. A
confirmed pin changes only the button's label and icon; nothing moves. When
`isRequestPinAppWidgetSupported` is false the button is replaced by one hint
line pointing at the launcher's widget menu. Providers are addressed by name
through `core/navigation/WidgetProviders`: the flow is its own feature and must
not import `feature/schedule` or `feature/qr`; `WidgetProvidersTest` fails if a
provider is renamed.

**Services.** One switch row, `Подключить сервисы`, in the same card as any
settings toggle; while the opt-in runs a spinner takes the switch's slot so the
row never changes height. Below it the privacy default line, `Что даёт` with
three capability rows, and `Что хранит сервер` with one line per stored field
followed by the two notes (the ITMO.ID token is checked and not stored;
everything is deleted with the account) and the `Код сервера` link.

**The stored-data list must match the Backend schema.** It mirrors the tables
`users`, `user_groups`, `lessons`, `user_sport_lessons`, `sport_*_sign_entries`
and `devices`. A new table holding user data adds a line to
`fragment_onboarding_services.xml`; one string per line, so a schema change edits
one line.

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

## Verification

`OnboardingVisualTest` runs the real flow in the debug host
(`SettingsNavigationTestActivity` with `startDestination = R.id.onboarding` and
`onboardingFixture`) across light, dark, font scale 1.3 and a dynamic seed. It
asserts that every widget page draws its preview at widget height and offers its
own rows, that a row writes through, that the footer keeps 48 dp touch targets
and no text is clipped, that the services switch flips in place and adds the
notifications dot, and that a launcher without pinning explains itself instead
of offering a button.
