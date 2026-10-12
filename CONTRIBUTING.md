# Contributing

Set up the project with [docs/getting-started.md](docs/getting-started.md) and
read [docs/architecture.md](docs/architecture.md) first.

## Workflow

1. Branch from `master` with a short descriptive name.
2. Make one focused change. Keep unrelated refactoring out of it.
3. Run the checks for what you touched (below), then open a pull request into
   `master`. The description says what changed and how you verified it; for a
   UI change, attach screenshots or point at the changed baselines.
4. Merge only with green CI.

## CI expectations

Every pull request into `master` runs:

- `android-ci`: `verifyQuick`, `lintPlayDebug`, screenshot tests of every
  module, the docs check and the iOS klib compile. Required.
- `android-ui`: instrumented tests on emulators, when app inputs change.
- `ios`: `scripts/ios/test.sh --ci`, when iOS inputs change.

Run the relevant parts locally first: `./gradlew verifyQuick`,
`scripts/verify.sh shots <module>` for UI changes, `scripts/ios/test.sh` for
iOS changes, `scripts/check-docs.sh` for docs. Details in
[docs/testing.md](docs/testing.md).

## Changelog and docs

- Add a line to `CHANGELOG.md` under `## Unreleased` for every user-visible or
  developer-relevant change; edit the file directly.
- Docs in `docs/` describe the current state, not history. Update the
  feature doc and the architecture doc in the same pull request as the code.
- A decision that was argued and should not be argued again gets an ADR in
  `docs/decisions/`; add a new file instead of rewriting an old one.

## Commits

- One short line in the imperative mood, plain ASCII, no body:
  `Show the friend picker as a sheet`.
- Stage files explicitly; never commit build outputs, IDE files or secrets
  (`local.properties`, `keystore.properties`, `*.jks`, `Signing.local.xcconfig`,
  tokens, user data).

## Code style

- Kotlin follows `.editorconfig` and the Android Studio scheme in
  `.idea/codeStyles/`. UTF-8, LF, no trailing whitespace, final newline.
- Follow the layering and rules in [docs/architecture.md](docs/architecture.md);
  the Konsist suite enforces most of them.
- User-visible text is Russian and lives in string resources; the design
  rules are in [docs/design.md](docs/design.md).
- Comments explain a non-obvious why or an invariant, not what the code does.
- Tests live at the layer where the behaviour lives and use synthetic data.
