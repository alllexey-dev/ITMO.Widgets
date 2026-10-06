# shared/testing

## Owns
- `commonMain` `testkit`: `FakeClock`, `FakeFiles` (okio fake file system), `MockHttp` (Ktor mock engine),
  `SemanticsChecks`, `TestMainDispatcher` and `HostTestRunner` with its `android` and `ios` actuals.
- `androidMain` `testkit/screenshot`: the preview screenshot harness (`PreviewScreenshotTest`, baselines,
  inventory, compare) that every module's `shots` run uses, and the `:app` XML reference captures.
- `androidHostTest`: `ProbeScreenshotTest`, the harness proof; `screenshots/`: its baseline.

## Depends on
- `api` so a test source set needs only `project(":shared:testing")`: kotlin-test, coroutines-test, Turbine,
  okio fake file system, Ktor mock client, CMP ui-test; on Android also JUnit, Robolectric, Roborazzi, the
  composable preview scanner and `:shared:designsystem`.
- Core-free: never `:shared:core` or a feature module (core fakes live in core's `testFixtures`).
- Consumed only from test source sets; a main source set that names it is a bug.

## Verify
`scripts/verify.sh quick` (every consumer's tests); `scripts/verify.sh shots all` after a harness change.

## Hot files
- Every file here: lane L08.
- `testkit/screenshot/`: every module's baselines depend on it; a change that alters a capture re-records them
  all in the same PR (`scripts/verify.sh shots all --record`).

## Docs
- [Running the visual tests](../../docs/design.md#running-the-visual-tests) and
  [testing conventions](../../docs/architecture.md#testing-conventions).
- ADR [0022](../../docs/decisions/0022-jvm-screenshot-tests.md) (JVM screenshot tests).
