# Recipe: endpoint end to end

How a new call reaches a screen. There are two paths: a route of our own
Backend, and an official MyITMO or BARS endpoint read through MyItmoApi. Each
step lands in its own repository and pull request, in the order below; a later
step never merges before the one it consumes. The order and the
source-of-truth boundaries are in [`AGENTS.md`](../../AGENTS.md); the
decisions behind them are
[ADR 0024](../decisions/0024-pinned-composite-build.md) (MyItmoApi through a
pinned composite build), [ADR 0025](../decisions/0025-myitmoapi-2-kmp.md)
(MyItmoApi 2.x) and [ADR 0026](../decisions/0026-core-2-backend-client.md)
(Core 2.0 in `:shared:backend-client`).

Paths of the other repositories are written from this repository's parent:
`../itmo-widgets-backend/...` and `../MyItmoApi/...`. Backend paths are as on
its `v2.3/next`, MyItmoApi paths as at the commit in `gradle/myitmoapi.ref`.

## Backend path

### Worked example: web sign-in

The approval of a browser sign-in from the phone runs through every layer.
Its behaviour is in [web sign-in](../features/web-login.md).

| Step | Files |
|---|---|
| Privacy boundary | `../itmo-widgets-backend/docs/contracts/web.md`, `../itmo-widgets-backend/docs/contracts/privacy.md` |
| Route and DTO | `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/feature/users/web/UserController.kt` (`webLoginPreview`, `approveWebLogin`, `requireAppToken`), `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/feature/weblogin/web/WebModels.kt` (`WebLoginPreview`) |
| Security test | `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/feature/weblogin/web/WebAuthControllerSecurityTest.kt` ("web login is approved only with the app bearer token") |
| Golden fixtures | `../itmo-widgets-backend/src/test/resources/contract/http/users/webLoginPreview.json`, `../itmo-widgets-backend/src/test/resources/contract/http/users/approveWebLogin.json` |
| Core 2.0 area | `client/users/UsersApi.kt` (`webLoginPreview`, `approveWebLogin`), `client/users/KtorUsersApi.kt`, `client/users/WebLoginPreview.kt`; tests `client/users/UsersRouteCases.kt`, `client/users/WebLoginApiTest.kt`, `client/users/WebLoginContractTest.kt`, `client/users/UsersVendoredFixturesTest.kt` and `shared/backend-client/src/commonTest/resources/claims/users.txt` |
| App repository | `feature/weblogin/data/WebLoginRepositoryImpl.kt` behind `core/weblogin/WebLoginRepository.kt`, tested by `feature/weblogin/data/WebLoginRepositoryImplTest.kt` and `core/demo/DemoNetworkGateTest.kt` |
| ViewModel and screen | `feature/weblogin/presentation/WebLoginViewModel.kt`, `feature/weblogin/ui/WebLoginBottomSheet.kt` |

The repository still calls Core 1.x (`ItmoWidgetsApi`); KM-10g moves it onto
`UsersApi`. The Core 2.0 call pattern that is already in place is
`core/session/DefaultBackendIdentitySync.kt` (`UsersApi.updateIdTokenData`).

### 1. Privacy boundary

Decide who may call the route and what each viewer sees before any code, and
write it into the area's contract under `../itmo-widgets-backend/docs/contracts/`
(with `../itmo-widgets-backend/docs/contracts/privacy.md` when an audience is
involved). Backend decides privacy and authorization in its services and
returns viewer-scoped capabilities (`canViewSchedule` and the like), never
another user's raw settings; the apps never enforce privacy on Backend's
behalf. A new audience or capability is an owner decision, see
[ADR 0004](../decisions/0004-privacy-audiences.md).

### 2. Route, DTO and security test

Follow "Where things go" in `../itmo-widgets-backend/docs/architecture.md`:

- The route goes into a controller in the feature's `web` package; the
  controller calls services and never reads repositories or checks privacy.
  A rule other than plain `authenticated()` goes into
  `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/platform/security/SecurityConfig.kt`.
- Request and response types go into the same `web` package. Their names are
  the names the clients will use.
- An allowed and a denied case go into the feature's controller security
  test, next to `WebAuthControllerSecurityTest` and
  `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/feature/users/web/PrivacyControllerSecurityTest.kt`.
  A denial is 403 (`permission_denied`, `access_denied`, `restricted`);
  401 `unauthorized` is only for missing or invalid credentials.
- Time comes from the injected `Clock`, as in the app.

### 3. Fixtures, compatibility suites and OpenAPI

- Record the golden fixture of the new route or field
  (`../itmo-widgets-backend/src/test/resources/contract/README.md` has the
  command and the layout). Only a card that adds a route or an optional
  response field records; any other fixture difference is a wire change.
- The `compatCore120Test` and `compatCore170Test` suites decode every fixture
  with the released Core 1.2.0 and 1.7.0 (Android 2.1 and 2.2):
  `../itmo-widgets-backend/src/compatCore170Test/kotlin/dev/alllexey/itmowidgets/backend/compat/Core170DecodeTest.kt`.
  A fixture no released Core reads carries `minCore` `1.8.0`.
- Regenerate `../itmo-widgets-backend/docs/openapi.json` with Backend's
  `scripts/verify.sh openapi`; `OpenApiSnapshotTest` fails on a stale file.
- The pull request names the changed fixtures and the snapshot in a
  `Contract change:` line, so the client side re-syncs, and adds a fragment to
  Backend's `changelog.d/`.

### 4. Core 2.0 area

After the Backend pull request is merged into Backend's `v2.3/next`:

1. Vendor the contract with `scripts/sync-backend-contract.sh <backend-sha>`,
   then `scripts/sync-backend-contract.sh --check`. Never edit or hand-merge
   the vendored copy.
2. Mirror the route in the area's package of `shared/backend-client`, as the
   module's [`CONTRACT.md`](../../shared/backend-client/CONTRACT.md) says:
   the function in `<Area>Api` with the area's facts in KDoc, its call in
   `Ktor<Area>Api`, the DTOs with Backend's names and no wire defaults on
   privacy- or authorization-affecting fields, an enum serializer by policy.
   A new area also gets its property in `BackendClient.kt`.
3. Tests in the area's `commonTest` package: a case in `<Area>RouteCases`
   (aggregated by `contract/AllRouteCases.kt`), the request and decode tests,
   the claimed fixtures in `claims/<area>.txt` with its
   `<Area>VendoredFixturesTest`, and a new top-level type in
   `contract/WireModels.kt`. `contract/ContractConformanceTest.kt` then
   compares every route and model with the vendored OpenAPI snapshot. A route
   the apps do not call goes to `contract/NotMirrored.kt` with its reason; a
   client field Backend has not shipped yet goes to
   `contract/PendingBackendFields.kt` with its Backend card.
4. Verify with
   `scripts/verify.sh run -- :shared:backend-client:testAndroidHostTest :shared:backend-client:compileTestKotlinIosSimulatorArm64`.

A fixture that does not decode is a contract question for Backend, never a
client workaround. Core 1.x is frozen at 1.7.0, so a new route exists only in
Core 2.0.

### 5. App repository

- The client: `di/NetworkModule.kt` builds the one `BackendClient` and
  provides each area (`provideUsersApi` and the like) to Hilt. A Koin-built
  repository gets the area from `di/bridge/CoreBridge.kt`, where a type more
  than one feature reads is bridged once.
- The repository lives in `data` (`shared/feature-<x>` once the feature is
  shared) and takes the area API, `DemoMode`, `BackendGate` and
  `AppDispatchers`. Each call checks, in this order: the demo session (reads
  answer from the feature's `data/demo`, writes fail with
  `AppError.DemoUnavailable`), then `BackendGate.mayCallBackend()` (otherwise
  `AppError.CustomServicesDisabled`), then calls on `dispatchers.io`. A
  `BackendException` becomes an `AppError` through `asAppError()` in
  `shared/core/src/commonMain/kotlin/dev/alllexey/itmowidgets/core/network/BackendErrors.kt`;
  `CancellationException` is rethrown. The wire types stop here: `domain`
  gets its own models.
- `GateRulesTest` (Konsist) fails a class that takes a Backend client or one
  of its `*Api` areas without `DemoMode` and `BackendGate`; `BackendClient`
  itself never checks either.
- The fake: a `Fake<X>Repository` in `shared/core/src/testFixtures` when other
  features read the port (`FakeSubjectLinksRepository`), otherwise in the
  feature's tests; `FakeBackendGate` and `FakeDemoMode` stand in for the gates.
- Tests: the repository over a MockEngine (`core/network/Core2Harness.kt`
  builds the client as `NetworkModule` does), plus a case in
  `DemoNetworkGateTest` or the feature's `*DemoGateTest` that sends nothing in
  the demo session and nothing without the opt-in.
- The binding: `singleOf(::<X>RepositoryImpl) { bind<<X>Repository>() }` in
  the shared module's `di/<X>Module.kt`, checked by its `<X>ModuleTest`; a
  feature still in `app/` binds it with `@Binds` in its Hilt module.

### 6. ViewModel and screen

The ViewModel takes the `domain` port and follows
[Screen state and events](../architecture.md#screen-state-and-events); a
failure is an `AppError`, never server text, and
`AppError.CustomServicesDisabled` reads as an instruction. The screen is built
as in [screen in a shared module](shared-module-screen.md). The demo data set
gets its row in [the demo session](../features/demo.md#gate), and the feature
doc names the routes it calls.

### Compatibility with released apps

Android 2.1 and 2.2 decode Backend's answers with released Core and cannot be
updated by a Backend release, so every wire change is additive. The rule is
`../itmo-widgets-backend/docs/contracts/compatibility.md`; in short:

- Allowed: new routes, new optional response fields, new FCM types, new
  optional request fields whose default keeps the old behaviour, new
  request-only enums.
- Forbidden: a new value in an existing response enum, removing or renaming a
  field or route, making a field nullable, a response field the client must
  send back, a `notification` block on Android pushes.
- A client never ships a call or a field that the deployed Backend does not
  serve yet. Android 2.3 works against Backend 1.7.0 in production, so a call
  that needs a newer Backend waits for it, and
  [version compatibility](../product/releases.md#version-compatibility)
  records the minimum.

## MyITMO path

### Worked example: the QR pass

The pass moved from MyItmoApi 1.x onto the 2.x client and then into
`commonMain`; its behaviour is in [the QR pass](../features/qr.md#data).

| Step | Files |
|---|---|
| MyItmoApi area | `../MyItmoApi/kmp/src/commonMain/kotlin/dev/alllexey/itmoapi/myitmo/qr/QrApi.kt`, `../MyItmoApi/kmp/src/commonMain/kotlin/dev/alllexey/itmoapi/myitmo/qr/QrData.kt`, fixture `../MyItmoApi/kmp/fixtures/qr/pass.json`, tests `../MyItmoApi/kmp/src/commonTest/kotlin/dev/alllexey/itmoapi/myitmo/qr/QrApiTest.kt` and its MockEngine seam `../MyItmoApi/kmp/src/commonTest/kotlin/dev/alllexey/itmoapi/myitmo/qr/AreaExchange.kt` |
| Pin | `gradle/myitmoapi.ref` |
| Data source | `feature/qr/data/remote/QrCodeRemoteDataSourceImpl.kt`, demo answer `feature/qr/data/demo/DemoQr.kt` |
| Repository | `feature/qr/data/repository/QrCodeRepositoryImpl.kt` (maps `MyItmoException` to `AppError`) |
| Tests | `feature/qr/data/remote/QrCodeRemoteDataSourceImplTest.kt` with `feature/qr/data/remote/QrRemoteFixtures.kt`, the QR case of `core/demo/DemoNetworkGateTest.kt` |
| Binding | `feature/qr/di/QrModule.kt`, with `MyItmoClient` bridged in `di/bridge/CoreBridge.kt` |

### 1. MyItmoApi area

MyItmoApi is the only client of the official MyITMO and BARS endpoints: a new
endpoint is a MyItmoApi change, never a second HTTP client in an app. Its
rules are in `../MyItmoApi/AGENTS.md`:

- The function goes into the area's `*Api` in
  `../MyItmoApi/kmp/src/commonMain/kotlin/dev/alllexey/itmoapi/myitmo/`, on
  the existing transport and the one auth plugin.
- Models document the observed shape, units, enum-like values and whether
  null was actually observed; unknown fields stay commented declarations.
- Synthetic fixtures go to `../MyItmoApi/kmp/fixtures/` and MockEngine tests
  assert the method, host, path, query, headers and the decoded payload.

### 2. Pin bump

The change reaches this repository in one order (ADR 0024): MyItmoApi pull
request, MyItmoApi `v2.3/next`, an owner-approved batch on MyItmoApi
`master`, then the integrator bumps `gradle/myitmoapi.ref`, and each Android
worktree re-pins with `lane pin`. A card that needs an unmerged MyItmoApi
change waits; no commit points at a local path or Maven Local.

### 3. Data source and repository

- The data source takes `MyItmoClient` (or its area), `DemoMode` and
  `AppDispatchers`. It answers from the feature's `data/demo` in the demo
  session, then calls on `dispatchers.io`. `GateRulesTest` enforces the
  `DemoMode` parameter. A MyITMO call needs no `BackendGate`: it never
  reaches Backend.
- The client's auth plugin already refreshes once and retries once on a 401;
  a data source adds a retry only for a case the plugin cannot see (the QR
  pass: a successful answer without a pass gets one forced refresh).
- The data source throws `MyItmoException`; the repository maps it to an
  `AppError` and owns caching and fallbacks.
- Tests run the real client over a MockEngine
  (`MyItmoClientFactory.create(storage, engine, clock)` with an in-memory
  token storage), with synthetic answers shaped like MyItmoApi's fixtures, and
  assert that the demo session sends nothing.
- The binding is a Koin `singleOf` in the shared module's `di/<X>Module.kt`;
  `MyItmoClient`, `DemoMode` and `AppDispatchers` come from `CoreBridge`.

### 4. ViewModel and screen

As on the Backend path, step 6.

## Rules

- Synthetic data only, in every fixture, test and demo set: no real tokens,
  passes, names or ISU numbers. Tokens, headers and bodies are never logged.
- Every class that holds a network client checks `DemoMode`; every class that
  holds a Backend client also checks `BackendGate`. The clients themselves
  check neither.
- Wire names come from the server side (Backend's DTOs, MyITMO's observed
  JSON); the client never invents a field, route, default or enum value.
- Server text never reaches the UI: failures are `AppError`, labels are string
  resources.
