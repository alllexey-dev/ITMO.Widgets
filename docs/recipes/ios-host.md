# Recipe: Adding a SwiftUI screen over a shared view model

How an iOS screen is built under [ADR 0031](../decisions/0031-swiftui-ios-screens.md): a native SwiftUI view in
`iosApp/` over the feature's shared view model. The current state of the app is [the iOS app](../ios.md); the Android
half and the shared module follow [one feature end to end](../feature-flow.md).

## Worked example: settings

Settings is the reference: a SwiftUI screen over the shared `SettingsViewModel`, with page arguments, events,
system state and snapshot tests. Copy its shape.

| Piece | File |
|---|---|
| View model (shared) | `shared/feature-settings/src/commonMain/.../settings/presentation/` |
| Koin parameters for Swift | `shared/feature-settings/src/iosMain/kotlin/dev/alllexey/itmowidgets/feature/settings/di/SettingsIosModule.kt` (`settingsPageParameters`) |
| Framework export | `shared/ios/build.gradle.kts` (`export(project(":shared:feature-settings"))`) |
| Adapter | `iosApp/Sources/Bridge/ObservableViewModel.swift` |
| Screen (owns the view model) | `iosApp/Sources/Features/Settings/SettingsScreen.swift` |
| Content (renders state) | `iosApp/Sources/Features/Settings/SettingsForm.swift` |
| Route map | `iosApp/Sources/App/Router/Routes+Settings.swift` |
| Snapshot tests | `iosApp/Tests/SnapshotTests/Settings/SettingsSnapshotTests.swift` |
| Unit tests | `iosApp/Tests/UnitTests/SettingsTests.swift` |

## 1. The shared view model

- It lives in `commonMain` of `:shared:feature-<x>`, is declared with `viewModelOf` in the feature's Koin module and
  exposes `uiState: StateFlow<State>` and, for one-shot effects, `events: Flow<Event>` with a sealed `Event`.
- It depends on no Compose type. Text is `UiText` (resolved per platform), icons are `AppIcon`.
- Arguments Swift passes are Koin parameters: add a small top-level function in the feature's `iosMain`
  (`settingsPageParameters(page:)`) that builds the parameter list, so Swift never assembles Kotlin types by hand.

## 2. Export the module

Add the feature module to the umbrella in `shared/ios/build.gradle.kts`: `export(project(":shared:feature-<x>"))`
and `api(...)` instead of `implementation(...)`. Export the module, not its dependencies; each export grows the
header and the link. Exported types keep their names in Swift.

## 3. The screen

One screen view owns the adapter; a separate content view renders a state value and takes callbacks, so tests can
render it without a view model.

```swift
struct SettingsScreen: View {
    @State private var model: ObservableViewModel<SettingsViewModel, SettingsUiState>

    init(page: String) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            guard let model = store.resolve(
                type: SettingsViewModel.self,
                parameters: settingsPageParameters(page: page)
            ) as? SettingsViewModel else { preconditionFailure("Koin resolved no SettingsViewModel") }
            return model
        })
    }

    var body: some View {
        SettingsForm(state: model.state, ...)
            .observing(model)
            .onEvents(of: model, \.events) { event in
                switch onEnum(of: event) { ... }
            }
    }
}
```

- Without arguments use `ObservableViewModel(XViewModel.self, state: \.uiState)`.
- Hold the adapter in `@State`. Its `deinit` clears the store, which runs `onCleared` and cancels `viewModelScope`;
  never hold it from an unstructured `Task`.
- Call view model methods through `model.viewModel`. Suspend functions are `async throws`; sealed types switch through
  `onEnum(of:)`.
- Text: `UiText.resolved` for shared text, generated `.xcstrings` symbols for static copy; iOS-only copy goes to
  `iosApp/Strings/strings_ios_<area>.xml` and `./gradlew :app:exportAppleStrings`. No Russian in Swift.
- Look: native SwiftUI controls and the kit in `iosApp/Sources/DesignSystem/` (`ItmoFormSection`, state views,
  `ItmoProgressButton`), tokens from `Tokens.generated.swift`, icons from `AppSymbol`.
- What only iOS knows (permissions, system settings) is read in Swift and reported to the view model, as
  `SettingsScreen` does on appear and on `scenePhase == .active`.

## 4. The route

In `Routes+<Feature>.swift` return `.swiftUI { AnyView(XScreen(...)) }` for the key; the screen keeps the native
navigation bar. Open other routes with `AppRouter.open(_:)` from the environment, answer an opener with
`deliver(_:from:)`. When the route was hosted through Compose before, delete in the same change its factory in
`shared/ios/src/iosMain/.../ios/screens/<Feature>Screens.kt`, its Swift wrapper and the `.compose` target.

## 5. Tests

| What | Where | Run |
|---|---|---|
| Content in four appearances (light, dark, Dynamic Type L and AX1), every state, long names | `iosApp/Tests/SnapshotTests/<Area>/<Feature>SnapshotTests.swift` with `assertAppearances(of:named:)` | `scripts/ios/test.sh --only SnapshotTests/<Feature>SnapshotTests` |
| The graph resolves the view model with its parameters | `shared/feature-<x>/src/iosTest` | `scripts/ios/test.sh kn :shared:feature-<x>` |
| Swift logic around the view model | `iosApp/Tests/UnitTests/<Feature>Tests.swift` | `scripts/ios/test.sh --only ITMOWidgetsTests/<Feature>Tests` |
| The route on the simulator: open, back, AX1 | `iosApp/Tests/UITests/<Feature>UITests.swift` (demo session through `XCUIApplication.itmo()`) | `scripts/ios/test.sh ui <Feature>UITests` |

Render the content view from fixture states, not the screen: the view model would need the graph and the network.
A run without a reference records it and fails; commit references with the code behind them.

## Traps

- Construction of `ObservableViewModel` resolves nothing; SwiftUI may build and discard `@State` initial values.
- A Swift type in `iosApp/Shared/` or an extension must not reuse a name `Shared` exports: the app compiles those
  sources next to `import Shared`.
- Extensions never import `Shared`; a widget gets its data from an App Group file the app writes.
