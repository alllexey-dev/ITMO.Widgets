# Recipe: iOS host

How a feature built in `shared/feature-<x>` reaches iOS: its Compose
Multiplatform route inside the SwiftUI shell, a SwiftUI screen over a shared
ViewModel where SwiftUI owns the screen, the Koin graph both stand on, the App
Group snapshot a widget reads, the Control, App Shortcut and quick action
that open it, and the strings Swift shows. The Android half and the shared
module itself are [Screen in a shared module](shared-module-screen.md); the
current state of the app is [the iOS app](../ios.md). The rules behind it are
in [ADR 0023](../decisions/0023-ios-client.md),
[ADR 0017](../decisions/0017-cmp-ui-in-common-main.md),
[ADR 0027](../decisions/0027-remoteviews-widgets.md) and
[ADR 0028](../decisions/0028-strings-and-icons.md).

## Worked example: the QR pass

The QR pass was the first feature hosted this way. Copy its shape.

| Piece | File |
|---|---|
| Swift-facing factory | `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/screens/QrScreens.kt` |
| The one controller helper | `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/screens/ScreenControllers.kt` |
| Module list of the graph | `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/di/IosKoinModules.kt` |
| iOS Koin module | `shared/feature-qr/src/iosMain/kotlin/dev/alllexey/itmowidgets/feature/qr/di/QrIosModule.kt` |
| App Group writer | `shared/feature-qr/src/iosMain/kotlin/dev/alllexey/itmowidgets/feature/qr/widget/QrPassSnapshotWriter.kt` |
| Swift host | `iosApp/Sources/Bridge/ComposeHost.swift` |
| Swift screen (brightness, identifier) | `iosApp/Sources/Features/Qr/QrPassScreen.swift` |
| Route map | `iosApp/Sources/App/Router/Routes+Qr.swift` |
| Widget reader | `iosApp/Shared/WidgetSnapshots/QrPassSnapshot.swift` |
| Widget | `iosApp/Extensions/Widgets/Qr/QrWidget.swift`, `iosApp/Extensions/Widgets/Qr/QrWidgetTimeline.swift`, `iosApp/Extensions/Widgets/Qr/QrWidgetEntryView.swift` |
| Control | `iosApp/Extensions/Widgets/Controls/QrControl.swift` |
| Route ids and their inbox | `iosApp/Shared/Intents/IntentRoute.swift`, `iosApp/Shared/Intents/OpenRouteIntent.swift`, `iosApp/Shared/Intents/RouteInbox.swift` |
| App Shortcuts, quick actions | `iosApp/Sources/Intents/AppShortcutIntents.swift`, `iosApp/Sources/Intents/QuickActions.swift` |
| iOS-only copy | `iosApp/Strings/strings_ios_shortcuts.xml`, rows in `build-logic/strings/apple-tables.properties` |
| Tests | `shared/feature-qr/src/iosTest/kotlin/dev/alllexey/itmowidgets/feature/qr/di/QrIosModuleTest.kt`, `shared/feature-qr/src/iosTest/kotlin/dev/alllexey/itmowidgets/feature/qr/widget/QrPassSnapshotWriterTest.kt`, `iosApp/Tests/UITests/QrPassUITests.swift`, `iosApp/Tests/UnitTests/IntentsTests.swift`, `iosApp/Tests/UnitTests/StableIdentifiersTests.swift` |

## Processes

- Only the app target links the Kotlin framework `Shared`. The widget
  extension never does (`import Shared` is forbidden there: its memory limit
  is about 30 MB); everything a widget or a Control needs is a file the app
  leaves in the App Group container or a fact of its own `Bundle.main`.
- The notification service is the only extension that could ever link
  Kotlin, and only if a device footprint makes it fit and an ADR supersedes
  [ADR 0023](../decisions/0023-ios-client.md); today it is Swift-only with its
  own micro-client. No other extension gets Kotlin.
- Sources in `iosApp/Shared/` compile into all three targets, so they never
  touch `Shared` either; `iosApp/Shared/Intents/` is left out of the
  notification service.

## 1. Koin start

- `App.init` (`iosApp/Sources/App/ITMOWidgetsApp.swift`) runs, in this order:
  `IosStrings.shared.installAppLocale()`, `startKoinIos(platform:
  AppPlatform())`, the router, `RouteInbox.shared.connect`, the shell's
  session, then `SessionRepository.initialize()`. One graph,
  `allowOverride(false)`; a second `startKoinIos` keeps it and returns false,
  which is what the hosted tests see.
- A feature adds one line to `IosKoinModules.all(platform)`: its common
  module and its iOS module, `qrModule, qrIosModule`. iOS-only bindings (ports
  with an iOS actual, App Group writers) live in
  `shared/feature-<x>/src/iosMain/kotlin/.../di/<Feature>IosModule.kt`.
- What only Swift can do goes into the one Kotlin interface Swift implements,
  `IosPlatform` (`iosApp/Sources/Bridge/AppPlatform.swift`); never a second
  Swift-implemented interface.
- Kotlin in `shared/ios` reads the graph through `IosKoin.koin()`, never
  Koin's global context. Swift reads it as
  `IosKoin.shared.get(protocol: X.self) as! X` for an interface and
  `get(type: X.self)` for a class; a missing definition crashes, as on
  Android.
- UI tests run on the demo session: a Debug build launched with `-itmoDemo`
  (`XCUIApplication.itmo()`) starts it before `initialize()`, so a route makes
  no network call.

## 2. A shared route in the NavigationStack

The route is the feature's own `<Name>Route`, exactly as Android's host shows
it; iOS never forks it.

- **Factory (Kotlin).** One file per feature,
  `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/screens/<Feature>Screens.kt`,
  one public top-level function per route, always through
  `screenController { }`, which builds the `ComposeUIViewController`, provides
  `LocalPlatformActions` from the graph and wraps `ItmoTheme`. Feature modules
  never build a controller.

```kotlin
fun qrPassViewController(onBack: () -> Unit): UIViewController = screenController {
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        QrPassRoute(onBack = onBack)
    }
}
```

- **Insets.** The kit's top bar and the routes draw no window insets, so the
  factory pads `WindowInsets.safeDrawing` and paints `surface` behind the
  bars. A tab root whose list scrolls under the tab bar pads only the top and
  gives the list a bottom `contentPadding` instead.
- **Arguments.** Plain Kotlin types or a route key from `:shared:core`; SKIE
  does not bridge default arguments, so add overloads. The ViewModel comes
  from `koinViewModel()` inside the route, in the store Compose Multiplatform
  gives each controller; its arguments are Koin parameters, as on Android.
- **Host (Swift).** `ComposeHost { factory(...) }` makes the controller once
  per view identity, never updates it and ignores the safe area. Only UIKit's
  safe area reaches the controller: a SwiftUI `safeAreaInset` reaches neither a
  hosted controller nor a pushed screen, so shell chrome (the demo banner) sits
  beside the stack in a `VStack`, never in an inset over it.
- **Swift screen.** One view per route in `iosApp/Sources/Features/<Feature>/`
  wraps the host with what only UIKit does (the QR pass raises the screen
  brightness while it is visible and the scene is `.active`, and restores it
  on disappear and any other phase) and gives the screen an identifier:

```swift
ComposeHost { [dismiss] in
    qrPassViewController(onBack: { dismiss() })
}
.accessibilityElement(children: .contain)
.accessibilityIdentifier("qr.pass")
```

- **Route map.** The feature's `Routes+<Feature>.swift` returns
  `.compose { AnyView(XScreen()) }` for its keys. `.compose` hides the
  navigation bar (`ShellChrome.compose`), the route draws its own top bar, and
  the shell turns the edge swipe back on. A new key is mapped to its feature in
  `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/navigation/IosRoutes.kt`;
  `RouterTests` fails for a key no feature claims.
- **Callbacks.** Kotlin `() -> Unit` is a Swift `() -> Void`; `(Boolean) ->
  Unit` boxes (`KotlinBoolean`, read `.boolValue`). Back is the `dismiss`
  captured when the controller is made; another route opens through
  `AppRouter.open(_:)`, results come back through `open(_:onResult:)` and
  `deliver(_:from:)`; never a UIKit push. Share, links, maps and settings
  already go through `LocalPlatformActions` (`IosPlatformActions`).

## 3. A SwiftUI-owned screen

Screens SwiftUI draws itself (onboarding, sign-in, settings, diagnostics)
still take their state from a `commonMain` ViewModel.

- Export the feature module whose ViewModel Swift names, in
  `shared/ios/build.gradle.kts` (`export(project(":shared:feature-<x>"))` and
  `api(...)` instead of `implementation(...)`); export the module, not its
  dependencies, since each export grows the header and the link.
- The adapter is `ObservableViewModel` (`iosApp/Sources/Bridge/ObservableViewModel.swift`),
  an `@Observable` class that owns a `ScreenViewModelStore` and resolves the
  ViewModel from Koin on first use:

```swift
@State private var model = ObservableViewModel(SettingsViewModel.self, state: \.uiState)

var body: some View {
    content(model.state)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) { ... }
        }
}
```

- Arguments are Koin parameters:
  `ObservableViewModel(state: \.uiState) { store in store.resolve(type:
  X.self, parameters: [id]) as! X }`, built in the view's `init`. No
  `SavedStateHandle` on this path.
- Lifetime: the adapter's `deinit` clears the store, which cancels
  `viewModelScope` and runs `onCleared`; nothing else does on iOS. One adapter
  per screen, never an unstructured `Task` that holds it.
- Sealed types switch through `onEnum(of:)`, suspend functions are
  `async throws`, `Flow` is an `AsyncSequence`. `UiText` resolves with
  `.resolved`, icons with `AppIcon.symbol.image`.

## 4. App Group snapshot for a widget

The widget extension reads JSON files the app's Kotlin writes; it never asks
the network, DataStore or the Keychain.

- **Writer (Kotlin, `iosMain` of the feature).** A class over the feature's
  repository that turns each new value into a `@Serializable` snapshot and
  calls `AppGroupSnapshotWriter.write(FILE, snapshot, listOf(KIND))`, which
  writes atomically and reloads that WidgetKit kind. `FILE` is a
  `SnapshotFile(name, version, serializer)`; the file is
  `<name>-v<version>.json` with the envelope `{"version": N, "value": ...}`.
- Every instant is precomputed on the wall `Clock` and written as
  `Instant.toString()`; Swift has no academic time. Write nothing while there
  is no valid value; the sign-out cleaner removes the container's files, so a
  writer needs no sign-out branch. The QR pass writes the module matrix of the
  shared `QrCodeGenerator`, so the widget draws the same code without
  CoreImage.
- **Wiring.** A `single(createdAtStart = true)` in `<Feature>IosModule` that
  launches the writer into its own `CoroutineScope(SupervisorJob() +
  dispatchers.main)`: WidgetKit reloads are asked on the main queue.
- **Reader (Swift).** `iosApp/Shared/WidgetSnapshots/<Name>.swift` over
  `AppGroupSnapshot`: a missing container, a missing or corrupt file and a
  newer version are all nil, and the widget shows its placeholder. Additive
  fields are optional with a default; a breaking change is a new
  `-v<N+1>.json`.
- **Fixture.** The Kotlin writer test writes the committed fixture in
  `iosApp/Tests/UnitTests/Fixtures/` and the Swift reader test decodes it, so
  both sides pin one shape. Add the file's row to "Data sharing" in
  [the iOS app](../ios.md#data-sharing) and its name to
  `StableIdentifiersTests`.
- The App Group comes from build settings (`AppGroupSnapshot.container()`,
  `AppGroupID`); `scripts/ios/check-sources.sh` fails on a group literal.

## 5. Control, App Shortcuts and quick actions

Every system entry opens the app on a stable route id and lets the router do
the rest.

- **Route ids.** `IntentRoute` (`qr_pass`, `today`, Android's shortcut ids) is
  the list; `IntentRoute.open()` offers the id to `RouteInbox`, which
  `App.init` connects to `AppRouter.open(id:)`. An id that arrives before the
  router waits in the inbox; from the router on it runs like any
  `itmowidgets://route/<id>` URL, through the shared `RouteQueue`. A new id
  needs a case in `IntentRoute`, a branch in `RouteURL.entryRoute(id:)` and a
  line in `StableIdentifiersTests`; shipped ids never change.
- **Control.** A `ControlWidget` in `iosApp/Extensions/Widgets/Controls/`
  with a stable `kind` and a `ControlWidgetButton` whose action is
  `OpenRouteIntent(route:)`. That intent is an `OpenIntent` compiled into the
  app and the extension, so the system runs `perform()` in the app; the
  extension never routes. Like Android's tile it only opens the app.
- **App Shortcuts.** `ITMOWidgetsShortcuts` (`AppShortcutsProvider`) in
  `iosApp/Sources/Intents/`, app target only; each intent sets
  `openAppWhenRun` and calls `IntentRoute.<case>.open()`. Every phrase holds
  `\(.applicationName)` exactly once.
- **Quick actions.** Static `UIApplicationShortcutItems` in
  `iosApp/Resources/Info/ITMOWidgets-Info.plist`, type
  `$(PRODUCT_BUNDLE_IDENTIFIER).<route id>`; `ITMOWidgetsAppDelegate` takes the
  launching one, `QuickActionSceneDelegate` the ones chosen while the app runs.

## 6. Strings

The Android catalog is the only source of copy; Swift holds no Russian
(`scripts/ios/check-sources.sh` fails on Cyrillic in Swift).

- `scripts/verify.sh run -- :app:exportAppleStrings` writes
  `iosApp/Shared/Strings/*.xcstrings`; commit the result and never edit a
  table by hand. Swift uses the generated symbols:
  `Text(.qrTileLabel)`, `Text(.StringsCommon.scheduleLessonCount(5))`.
- Copy only iOS shows goes to `iosApp/Strings/strings_ios_<area>.xml`
  (Android syntax, exported to `Localizable`).
- An App Shortcut phrase is an ASCII key of `AppShortcuts.xcstrings`, and an
  Info.plist value (a quick action title) a key of `InfoPlist.xcstrings`; both
  come from rows in `build-logic/strings/apple-tables.properties`
  (`AppShortcuts.<phrase>=<catalog key>`).
- App Intent titles and parameters take a catalog key literal
  (`LocalizedStringResource("ios_intent_open_route_title")`), not a generated
  symbol.
- Compose texts need nothing: the Compose plugins copy each module's
  `composeResources` into the app, and `installAppLocale()` makes Russian
  plurals work on an English iPhone.

## 7. Tests

| What | Where | Run |
|---|---|---|
| The graph resolves the route's ViewModel and the iOS module with zero requests | `shared/feature-<x>/src/iosTest` (`QrIosModuleTest`) | `scripts/ios/test.sh kn :shared:feature-<x>` |
| Writer output equals the fixture | `shared/feature-<x>/src/iosTest` (`QrPassSnapshotWriterTest`) | the same |
| Reader: missing, corrupt, newer, round trip | `iosApp/Tests/UnitTests/SnapshotReaderTests.swift` | `scripts/ios/test.sh --only ITMOWidgetsTests/SnapshotReaderTests` |
| Intents and quick actions route | `iosApp/Tests/UnitTests/IntentsTests.swift` | `scripts/ios/test.sh --only ITMOWidgetsTests/IntentsTests` |
| The route on device: open, states, back, AX1 | `iosApp/Tests/UITests/<Feature>UITests.swift` | `scripts/ios/test.sh ui <Feature>UITests` |
| Widget entry views in four appearances | `iosApp/Tests/SnapshotTests/Widgets/WidgetSnapshotTests.swift` | `scripts/ios/test.sh --only SnapshotTests/WidgetSnapshotTests` |

- Koin's `verify()` is JVM-only: an iosTest builds the modules with the
  device stood in (MockEngine counting requests, temporary directories) and
  resolves every definition.
- Compose maps `testTag` to the accessibility identifier, so XCUITest finds a
  route's parts by its test tags; Compose texts stay Russian on the English
  simulator.
- No snapshot test of a CMP host (the Metal layer renders blank); review shots
  come from `scripts/ios/screenshots.sh`, goldens stay Roborazzi's on the JVM.
- XCUITest does not drive Siri, Control Center or the springboard: unit-test
  each `perform()` and check those surfaces once by hand on the simulator.
- Record the app's footprint in "Memory" of [the iOS app](../ios.md#memory)
  when the route is the first of its kind.

## Traps

- A SwiftUI `safeAreaInset` never reaches a hosted controller: content runs
  behind the chrome. Put chrome beside the stack.
- A Swift type in `iosApp/Shared/` or a widget must not reuse a name the Kotlin
  framework exports (`:shared:core`): the app compiles those sources next to
  `import Shared`.
- The session file is written without reloading widget kinds: a widget that
  depends on sign-in state sees a sign-out only on its next timeline unless
  its own writer reloads the kind.
- An unsigned simulator build may have no App Group container; check data
  paths through the reader tests, not through a placed widget.
- `OpenRouteIntent` and `RevealQrIntent` run outside the app's router and
  graph; keep intents in `iosApp/Shared/Intents/` free of `Shared`.
