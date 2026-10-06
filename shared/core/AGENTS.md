# shared/core

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.core.*`: the contracts and models two or more features share
  (`HomeCardSource`, `DemoMode`, `BackendGate`, `AcademicTimeProvider`, `AppResult`, `LoadState`, `UiText`), the
  storage preferences and the client factories `MyItmoClientFactory` and `BackendClientFactory` with their mapping
  to `AppError`. A contract's implementation stays in the feature that owns the data.
- `androidMain`, `iosMain`: platform actuals only (file system, Keychain and App Group storage, Darwin engine,
  os_log).
- `src/testFixtures/kotlin`: one fake per core contract (`FakeDemoMode`, `FakeBackendGate`, ...), compiled into the
  `commonTest` of core and of every feature. A fake of a feature contract stays in that feature.
- `src/commonMain/composeResources/values/`: `strings_common.xml` (ids of two owner modules, or of core) and
  `strings_platform.xml` (ids a system surface reaches), both exported to `:app` as Android resources.

## Depends on
- `api`: `:shared:backend-client`, MyItmoApi 2.x (`libs.my.itmo.api.kmp`), Ktor client core (Darwin on iOS),
  coroutines, kotlinx-datetime, kotlinx-serialization-json, okio, DataStore preferences core, CMP resources.
- Never a feature module, `:shared:designsystem` or Compose UI; `:shared:testing` only in `commonTest`. Konsist
  fails on a `core` import of a feature package.
- Every `api` entry widens all consumers' classpaths: a comment beside it names the public signature that needs it.

## Verify
`scripts/verify.sh quick` (every module compiles and tests against core); add `scripts/verify.sh klibs core` after
an `iosMain` change.

## Hot files
- Every file here: lane L07.
- `strings_common.xml`, `strings_platform.xml`: lane L05; `scripts/strings-owners.py --where <id>` names the file.
- `build.gradle.kts` dependencies: L07, one line per change.

## Docs
- [Package structure](../../docs/architecture.md#package-structure), [modules](../../docs/architecture.md#modules)
  and [strings](../../docs/architecture.md#strings) in the architecture doc.
- ADRs [0018](../../docs/decisions/0018-module-graph-and-toolchain.md) (module graph),
  [0026](../../docs/decisions/0026-core-2-backend-client.md) (Backend client) and
  [0028](../../docs/decisions/0028-strings-and-icons.md) (strings and icons).
