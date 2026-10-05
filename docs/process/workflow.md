# Workflow

How work of any size moves from an idea to `master`. Rights to commit, push
and open pull requests are in [`AGENTS.md`](../../AGENTS.md) (§ v2.3 lanes) and
[ADR 0029](../decisions/0029-lanes-and-integrator.md); parallel work is
in [parallel agents](parallel-agents.md), who may edit which file in
[ownership](ownership.md).

## Tiers

| Tier | When | Plan | Verify |
|---|---|---|---|
| Light | Small UI fix or small feature in one repository | None | Affected tests, one appearance |
| Lane card | v2.3 work: one card is one session, one branch, one PR | A card in its lane file ([template](plan-template.md)) | The card's `verify` line through `scripts/verify.sh` |
| Cross-repo | A wire contract, a schema or data changes in more than one repository | One card per repository, linked by `needs` | Each repository's own verify, in chain order |

Cross-repo chain, each step merged before the next starts:

1. Privacy boundary: what each viewer may see, decided and written down.
2. Backend with tests and fixtures (`B/`).
3. The OpenAPI document regenerated from the Backend.
4. Core 2.0 in `shared/backend-client`: fixtures vendored from a named
   Backend commit, conformance against the OpenAPI document and the fixtures.
5. Apps: Android, iOS, Web.

No step publishes to `~/.m2`; Android builds MyItmoApi from the approved pin
(composite build, [ADR 0024](../decisions/0024-pinned-composite-build.md)). Backend and Web deploys need the owner's word.

## Verification tiers

[ADR 0022](../decisions/0022-jvm-screenshot-tests.md) sets three tiers:

- **Card**, every PR: `scripts/verify.sh quick`, plus `scripts/verify.sh shots
  <module>` for UI cards. CI `verify-quick` verifies every committed baseline.
- **Feature**, when a feature closes: its feature doc and one changelog
  fragment; baselines cover every screen state.
- **Release**, before the release candidate: the full appearance matrix on an
  emulator (light, dark, one dynamic palette, font 1.0 and 1.3, long names,
  every state), the iOS snapshot matrix, the 2.2 → 2.3 upgrade on a device and
  the ship check.

An emulator is used only for what the JVM cannot render: widgets, the QR tile,
shortcuts, notifications, WebView screens, Play in-app updates, and the
integrator's instrumented subset per batch.

## Batches

- The integrator merges green PRs into `v2.3/next` and shows the owner one
  batch per repository in two windows a day, about 14:00 and 19:00 MSK: PRs with
  card IDs, CI links, diffstat, screenshot diffs, migration numbers (Backend), a
  `githubDebug` APK (Android), open risks.
- After "OK `<repo>` `<sha>`" the integrator fast-forwards `master` (`main` in
  Web) through `~/proj/.wt/bin/promote`. A rejected PR is reverted, not fixed forward.
- Backend `dev` moves at most once a day; production only on its own word.

Standing approvals (owner, 2026-10-04) replace a per-case word in two cases;
everything else still needs the owner:

- **Surface.** The integrator accepts paths outside a card's `touches` when the
  independent reviewer confirms the card caused them and each one is test code
  (tests, fixtures, fakes, debug preview hosts), documentation (`docs/**`,
  `*.md`, `changelog.d/**`) or a mechanical call-site or import change forced
  by the card's own rename or removal. The PR lists them in `Surface:` and
  `Surface notes:`; the integrator records `surface OK (standing approval
  2026-10-04: <category>)`. New behaviour, new API, build logic, CI, release or
  deploy configuration and another lane's frozen hot file still go to the owner.
- **Backend dev.** The integrator moves Backend `dev` to a green `v2.3/next`
  head without asking when the batch has no Flyway migration and no deploy
  configuration change (`B/deploy/`, `B/.github/workflows/deliver.yml`), then
  checks `/api/app/version` on dev. A batch with a migration, Web deploys and
  production still need the owner's word.

## Closing a feature

One close step per feature, never per card:

1. Update `docs/features/<x>.md` and the feature's rows of the screen inventory.
2. Add one fragment `changelog.d/<lane-id-lowercase>-<x>.md` (format in
   [`changelog.d/README.md`](../../changelog.d/README.md)).
3. Facts for files owned by another lane go into the PR body as
   `## Docs hand-in` ([ownership](ownership.md#hand-ins)).
4. Add or update the feature's Konsist topic file.
5. Merge the iOS subsection an earlier iOS card handed in.

Verify with `scripts/check-docs.sh` and `scripts/changelog.sh check`.
