# Architecture

- Every Backend call passes one gate, `BackendGate.mayCallBackend()`
  (custom-services opt-in outside the demo session), and every network
  client checks `DemoMode` first.
- Repositories return `AppResult` through `appResultOf {}` with one
  released error mapping: no answer is `Network` (also inside a token
  refresh), 401 `Unauthorized`, 403 `restricted` `Restricted`, other 403
  `Forbidden`, 404 `NotFound`.
- Settings are split into per-concern stores over the one
  `app_preferences` DataStore (`ServicesOptInPreferences`,
  `WidgetSettingsPreferences`, `QrSettingsPreferences` and the rest); the
  file and its keys are unchanged.
- Coroutine dispatchers are injected through `AppDispatchers`; academic
  time comes from `AcademicTimeProvider` or an injected `kotlin.time`
  `Clock` with kotlinx-datetime types, and `DateTexts` formats dates byte
  for byte as 2.2 did.
- File storage runs on okio (`AppDirectories`, `AtomicTextFile`,
  `SecureStore`) and stays byte-compatible with the files 2.2 wrote.
- `:shared:core` holds the core contracts for Android and iOS with their
  packages unchanged, among them the multiplatform `UiText` and `AppIcon`.
- Koin starts beside Hilt: `di/bridge/KoinModules.kt` lists the modules,
  the `*Bridge.kt` files hand Hilt bindings to Koin, and a test checks the
  graph.
- The data layer runs on MyItmoApi 2.x and Core 2.0 over one Ktor engine;
  MyItmoApi 1.x, Core 1.x, Retrofit and Gson are no longer app
  dependencies, and the debug refresh-token replacement goes through the
  2.x client.
