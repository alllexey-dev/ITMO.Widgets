# Recipe: screen in a shared module

How to build a screen in `shared/feature-<x>` that Android and iOS host
unchanged: a stateless Compose Multiplatform screen in `commonMain`, a route
that owns the ViewModel, Koin wiring, strings and icons from the catalogs,
previews with JVM goldens, host tests and a thin platform host. The rules
behind it are in [ADR 0017](../decisions/0017-cmp-ui-in-common-main.md),
[ADR 0019](../decisions/0019-koin-per-lane.md),
[ADR 0020](../decisions/0020-navigation-3.md),
[ADR 0022](../decisions/0022-jvm-screenshot-tests.md) and
[ADR 0028](../decisions/0028-strings-and-icons.md); the module table is in
[architecture](../architecture.md#modules).

## Worked example: the QR pass

The QR pass was the first screen built this way. Copy its shape.

| Piece | File in `shared/feature-qr` |
|---|---|
| Build file | `shared/feature-qr/build.gradle.kts` (`itmowidgets.cmp.ui`, `itmowidgets.testing`) |
| UI state and events | `feature/qr/presentation/QrCodeUiState.kt` |
| ViewModel | `feature/qr/presentation/QrCodeViewModel.kt` |
| Koin module | `feature/qr/di/QrModule.kt` (`viewModelOf(::QrCodeViewModel)`) |
| Route | `feature/qr/ui/QrPassRoute.kt` |
| Stateless screen and test tags | `feature/qr/ui/QrPassScreen.kt` (`QrPassTestTags`) |
| Previews | `feature/qr/ui/QrPassScreenPreviews.kt` |
| Drawing in common code | `feature/qr/ui/rendering/QrCodeImage.kt`, `feature/qr/ui/rendering/QrCodeGenerator.kt`, `feature/qr/ui/rendering/QrColors.kt` |
| Strings | `composeResources/values/strings_qr.xml` |
| Goldens | `shared/feature-qr/screenshots/` via `feature/qr/QrScreenshotTest.kt` |
| Host tests | `feature/qr/ui/QrPassScreenTest.kt`, `feature/qr/presentation/QrCodeViewModelTest.kt`, `feature/qr/di/QrModuleTest.kt` |

The Android side keeps only the host and the bridge:
`feature/qr/ui/QrCodeFragment.kt` (a Fragment host) and `di/bridge/QrBridge.kt`
(Hilt-built types handed to Koin), with one line each in
`di/bridge/KoinModules.kt`.

What stays Android-only, in `app/`, and is not part of a shared screen:

| Surface | Files | Why it stays |
|---|---|---|
| Widget | `feature/qr/ui/widget/QrCodeWidgetProvider.kt`, `feature/qr/ui/widget/QrWidgetAnimation.kt`, `feature/qr/ui/rendering/QrBitmapRenderer.kt` | RemoteViews, not Compose ([ADR 0027](../decisions/0027-remoteviews-widgets.md)); placed widgets hold the provider's actions |
| Quick-settings tile | `feature/qr/ui/QrTileService.kt`, `feature/qr/presentation/QrTileController.kt` | a system service; its class name is a stable identifier |
| Worker | `feature/qr/work/QrWidgetUpdateWorker.kt`, `feature/qr/work/QrWidgetWork.kt` | built by WorkManager, see [background check](background-check.md) |

Those surfaces may read the shared `domain` (`QrCodeRepository`,
`QrWidgetState`) and pure helpers such as the module matrix of
`QrCodeGenerator`, which `QrToolkit` draws into a bitmap; never the shared
screen, route or ViewModel. iOS gives them their
own equivalents (WidgetKit, Controls) in Swift, see [the iOS app](../ios.md).

## Module and source sets

- A feature module uses the `itmowidgets.cmp.ui` and `itmowidgets.testing`
  conventions and depends on `:shared:core` and `:shared:designsystem` only;
  `:shared:testing` comes in through `commonTest`. Features never import each
  other (`FeatureIsolationRulesTest`).
- A screen adds two `commonMain` dependencies once per module:
  `implementation(libs.koin.compose.viewmodel)` (for `koinViewModel()`) and
  `implementation(libs.jetbrains.lifecycle.runtime.compose)` (for
  `collectAsStateWithLifecycle` and `LifecycleStartEffect`). Both publish iOS
  klibs.
- Packages are preserved: the screen lives in
  `dev.alllexey.itmowidgets.feature.<x>.ui`, its ViewModel in
  `...feature.<x>.presentation`, its Koin module in `...feature.<x>.di`.
- `commonMain` holds no `android.*`, `java.*`, `R`, Hilt or `javax.inject`
  (`UiTextRulesTest`, `common code references no Android R`). Check the klib:
  `scripts/verify.sh run -- :shared:feature-<x>:compileKotlinIosSimulatorArm64`.

## Pieces

1. **UI state and events** in `presentation/<Screen>UiState.kt`, beside the
   ViewModel, never in its file. A sealed state (`Loading`, `Empty`, `Error`,
   `Content`) and at most one sealed event type. They carry no text: failures
   are `AppError`, labels are `UiText`, icons are `AppIcon`.
2. **ViewModel** in `presentation/`, an
   `androidx.lifecycle.ViewModel` from the multiplatform lifecycle artifact. It
   follows [Screen state and events](../architecture.md#screen-state-and-events):
   one `uiState: StateFlow`, at most one `events: Flow` backed by
   `EventQueue`, `refresh(mode: RefreshMode)` when the screen refreshes, and
   plain action functions. Constructor parameters are `domain` ports and a
   `kotlin.time.Clock` or `AcademicTimeProvider`, never a direct `now()`
   (`TimeRulesTest`). The QR pass takes the wall clock on purpose: its expiry
   must not follow the debug academic date. Lifecycle-bound work gets explicit
   `start()` and `stop()` calls, which the route makes.
3. **Koin module** in `di/<X>Module.kt`:
   `val qrModule = module { viewModelOf(::QrCodeViewModel) }`. Only
   definitions Koin constructs go here. A dependency that the app's Hilt graph
   still builds (QR: the repository and the colour setting) is handed over in
   `app/.../di/bridge/<X>Bridge.kt` as a lazy `single { EntryPoint.from(...) }`
   so the screen shares the one instance the widget and the worker use; a type
   more than one feature reads goes to `CoreBridge.kt` once. Register the
   module in `KoinModules.constructed` and the bridge in `KoinModules.bridges`.
   A `<X>ModuleTest` in `androidHostTest` calls `module.verify(extraTypes = ...)`
   with every bridged type, and `KoinGraphTest` checks the release graph.
4. **Route** (`ui/<Name>Route.kt`, public): the only composable that knows
   the ViewModel. It takes navigation as lambdas (`onBack`, `onOpen<Y>`) and
   the ViewModel as a defaulted argument, `viewModel: QrCodeViewModel = koinViewModel()`,
   so a test or the iOS host can pass its own. It collects `uiState` with
   `collectAsStateWithLifecycle()`, calls `start()`/`stop()` in
   `LifecycleStartEffect`, and collects `events` inside
   `repeatOnLifecycle(Lifecycle.State.STARTED)` with `LocalLifecycleOwner`.
   Snackbar text outside composition uses the suspend `getString(...)`. It
   never touches a NavController, an Activity or a Fragment.
5. **Screen** (`ui/<Name>Screen.kt`, public, stateless):
   `QrPassScreen(state, onRefresh, onBack, modifier, snackbarHostState)`. It
   takes the state and lambdas only; previews and host tests call it
   directly. Build it from the kit in `:shared:designsystem` (`AppTopBar`,
   `AppTopBarAction`, `ContentState`, `ContentStateLoading`, `ProgressButton`,
   settings rows, sheets) and the tokens of `ItmoTheme` (`colorScheme`,
   `spacing`, `typography`); a missing kit piece is added to the kit, not
   copied into the feature. Paint the screen's own background; leave window
   insets to the host. Work that can fail (QR: a payload that does not fit a
   version 1 code) runs in `remember(key) { runCatching { ... }.getOrNull() }`
   with a fallback state, never a crash in composition.
6. **Strings** live in
   `shared/feature-<x>/src/commonMain/composeResources/values/strings_<x>.xml`,
   Android syntax, Russian, under [Strings](../architecture.md#strings). The
   key is the resource name, the Android id and the iOS table key at once
   (ADR 0028), so it is never renamed. Plurals are `<plurals>` with `one`,
   `few`, `many` and `other`, as `schedule_lesson_count` in
   `shared/core/src/commonMain/composeResources/values/strings_common.xml`.
   In composition use `stringResource(Res.string.<key>)`; state carries
   `UiText.Res(...)` or `UiText.Plural(resource, count, arguments)` and the
   screen calls `asString()` (`core/text/UiTextResolvers.kt`); an `AppError`
   becomes text through `textResource()` (`core/text/AppErrorTexts.kt`). The
   feature's `Res` is `dev.alllexey.itmowidgets.shared.feature.<x>.Res`;
   import the core and kit ones with aliases (`as CoreRes`, `as KitRes`). Text
   used by two modules goes to `strings_common.xml`, text a system surface
   reads to `strings_platform.xml`; `scripts/strings-owners.py --where <id>`
   places a new id. When a screen moves from `app/`,
   `python3 scripts/strings-move.py <unit>` moves its file and keeps the
   Android export while a View, widget or settings preview still reads it (QR:
   `androidExport("values/strings_qr.xml")` for the widget preview).
7. **Icons** come from the registry `docs/design/icons.tsv`, fetched by
   `scripts/icons-fetch.py` into `:shared:designsystem`, never hand-drawn. The
   screen reads `painterResource(KitRes.drawable.ic_<id>)`; state that names an
   icon carries an `AppIcon` (`core/text/AppIcon.kt`), drawn through
   `AppIcon.drawable` (`designsystem/icons/AppIconResources.kt`), which Swift
   maps to an SF Symbol.
8. **Test tags**: a public `object <Screen>TestTags` beside the screen with
   `const val` tags for what a test or a host must find (QR: area, image,
   loading, state, refresh). Put a tag on the node that carries the semantics
   the test asserts, for example the button's own modifier so
   `assertIsEnabled` sees it.
9. **Previews** in `ui/<Screen>Previews.kt`: one private `@Preview` per state,
   named `<Screen><State>Preview`, each wrapped in `ItmoPreview { }`
   (`designsystem/preview/ItmoPreview.kt`), with synthetic data only (QR:
   `QrCodeSnapshot("ITMO-TEST", ...)`).
10. **Goldens**: one `class <X>ScreenshotTest : PreviewScreenshotTest()` in
    `androidHostTest` records every preview of the module into
    `shared/feature-<x>/screenshots/<Preview>_<appearance>.png`. Record with
    `scripts/verify.sh shots feature-<x> --record`, add
    `-Pshots.appearance=full` for the two narrow appearances, look at every
    PNG, then `scripts/verify.sh shots feature-<x>` must pass unchanged. When
    the screen replaces an XML one, the XML reference captures under the same
    preview names are the parity target and are overwritten by the record. See
    [Running the visual tests](../design.md#running-the-visual-tests).
11. **Host tests** in `commonTest`: `ui/<Screen>Test.kt` runs under
    `@RunWith(RobolectricTestRunner::class)` from `testkit` with
    `runComposeUiTest`, walks every state, calls `assertTouchTargets()`
    (`testkit/SemanticsChecks.kt`), asserts the feature doc's layout
    invariants on test tags (QR: the area is square and at most 300 dp at
    narrow and wide widths; the refresh button keeps its bounds in every
    state) and checks that callbacks fire. The ViewModel test drives fakes of
    the `domain` ports and a fixed `Clock`.

## Hosting

The same route serves every host; only the host changes.

- **Android now: a Fragment host.** The Fragment keeps its class name and
  `@AndroidEntryPoint` (nav graphs, tiles, shortcuts and tests name it) and
  returns `itmoComposeView { <Name>Route(onBack = { closeScreen() }) }` from
  `onCreateView`. `itmoComposeView`
  (`designsystem/host/ItmoComposeView.kt`) wraps `ItmoComposeHost.locals` and
  `ItmoTheme` and disposes with the Fragment's view; never add a second host
  helper or theme. `koinViewModel()` inside it scopes the ViewModel to the
  Fragment's store through Koin's factory, so Hilt's default factory does not
  create it, and it survives recreation. Drop the Fragment's `@Inject` fields
  and its `by viewModels()`. A screen with no other entry is opened in debug
  through `SettingsNavigationTestActivity.kt`.
- **Android after the Navigation 3 shell.** The route becomes the content of
  a Navigation 3 entry for a `@Serializable` key in `commonMain`
  ([ADR 0020](../decisions/0020-navigation-3.md)); the shell registers the
  entry and turns the route's lambdas into back-stack moves. The screen and
  the route do not change; the Fragment host is deleted then.
- **iOS.** A `ComposeUIViewController { <Name>Route(...) }` from `iosMain`
  embedded in the SwiftUI app; the ViewModel comes from the Koin graph the iOS
  app starts, and the lifecycle from the controller's `LocalLifecycleOwner`.
  Navigation lambdas call back into Swift.

## Rules

- `commonMain` has no `android.*`, `java.*`, `R`, Hilt or `javax.inject`;
  Compose resources only.
- Time comes only from an injected `Clock` or `AcademicTimeProvider`.
- Features never import each other; a screen reaches another feature through
  a navigation lambda.
- Only the route or the platform host obtains a ViewModel; the screen is
  stateless.
- Russian text only in `composeResources`; keys are never renamed.
- Previews, goldens and fixtures use synthetic data only.
- Every class that takes a network client checks `DemoMode` first; that stays
  in `data`, never in the screen.

## Traps

- A View's width percent with a maximum is not `fillMaxWidth(f)` with
  `widthIn(max)` in either order; write a `Modifier.layout { }` that takes
  `min(maxWidth * f, max)` (`passArea()` in `QrPassScreen.kt`).
- A centred `ScrollView` with `fillViewport` is
  `Modifier.fillMaxSize().verticalScroll(...)` on a `Column` with
  `Arrangement.Center`; `fillMaxSize` must come before `verticalScroll`.
- A view kept `INVISIBLE` so the controls below never move is
  `Modifier.alpha(0f)` plus `clearAndSetSemantics {}`, so TalkBack skips it.
- A state block inside a fixed square clips at font scale 1.3 in a narrow
  window; `wrapContentHeight(unbounded = true)` lets the text run past the
  square while the square keeps its size.
- `ProgressButton` stays enabled while `inProgress` and ignores a second tap;
  do not disable it by hand.
- An instrumented test on `:app` has no `ui-test-junit4`: read tags through the
  Fragment's `ComposeView` semantics owner, as `HomeQrVisualTest` does.
