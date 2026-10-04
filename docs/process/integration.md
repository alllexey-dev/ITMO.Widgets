# Integration of lane work in v2.3

How a lane's pull request reaches `v2.3/next` and how `v2.3/next` reaches the default branches of the
Android app (A), Backend (B), MyItmoApi (M) and the web app (W) while v2.3 is developed in parallel lanes. The
rules (allowed actions, the forbidden list, what needs the owner's word) live only in the "v2.3 lanes" section of
`AGENTS.md`; why they look this way is in
[0029 Lanes and the integrator](../decisions/0029-lanes-and-integrator.md), and the release lines in
[0030 Release lines](../decisions/0030-release-lines-and-data-continuity.md). Lane files, the board, batch files
and status ledgers are local (`vibe/`, ignored by Git).

## Roles

| Role | Works in | Does |
|---|---|---|
| Lane agent | `~/proj/.wt/<repo>/<lane-id>-<slug>` from `lane new` | one card per worktree, branch and PR into `v2.3/next`; pushes only its own `v2.3/<lane-id>/*` branch |
| Integrator | `~/proj/.wt/<repo>/next` from `lane next` (marker `integrator`) | merges, reverts, batches, version lines, the 2.2.x line, ledgers; prints owner-only commands and never runs them |
| Second integrator | the `next` worktrees of B, M and W only | the same duties for those repositories; started only by the owner (see below) |
| Owner | anywhere | batch OKs, protected-ref moves, dev deploys, tags, releases, store uploads, guard changes |

Main checkouts under `~/proj/` belong to the owner: agents never edit, switch, stash, pull or build there.

## Tools

| Command | Source | Notes |
|---|---|---|
| `~/proj/.wt/bin/lane` | `scripts/lane.sh` | `new`, `next`, `pin`, `done`, `list`; worktrees, the `itmo-lane` marker, MyItmoApi pins |
| `~/proj/.wt/bin/integrate` | `scripts/integrate.sh` | `list`, `merge`, `revert`, `batch`, `ready` |
| `~/proj/.wt/bin/slot.sh` | `scripts/slot.sh`, `scripts/slots.conf` | machine-wide build slots for every repository |
| `~/proj/.wt/bin/promote` | untracked | the only way protected refs move |
| `~/proj/.wt/git-hooks/pre-push` | untracked | which refs a worktree may push, by its marker and path |

- `lane` and `integrate` are wrappers: each run fetches `origin` into the remote-tracking refs of the main Android
  checkout and runs the script from `origin/v2.3/next` through bash. A change to either script takes effect when
  it is merged, never from a lane's copy. The scripts take every path from `ITMO_WT` (default `~/proj/.wt`, whose
  parent holds the main checkouts), never from their own location; the guard tests point `ITMO_WT` at a replica.
- `slot.sh` and `slots.conf` in `~/proj/.wt/bin/` are copies of the merged files, because `slot.sh` reads the
  `slots.conf` beside it and runs on every build. The integrator refreshes both copies whenever a batch changes
  either file. The verify scripts of B, M and W call `${ITMO_SLOT_SH:-~/proj/.wt/bin/slot.sh}`; A's
  `scripts/verify.sh` uses its own `scripts/slot.sh`.
- `promote` and the pre-push hook stay untracked and change only with the owner's word.
- Logs: `~/proj/.wt/run/integrate.log` (every merge, conflict and revert), `~/proj/.wt/run/promote.log` (every
  protected-ref move).

## Pull request fields

Title `<CARD-ID>: <summary>`, or `<ID>, <ID>: <summary>` for several items. The body carries one line per field:

| Field | Content |
|---|---|
| `Lane:` | the lane id, for example `L11-sport` |
| `Items:` | card ids |
| `Surface:` | comma-separated globs covering every changed path, starting from the card's `touches`; no commas or notes inside an entry |
| `Surface notes:` | optional: why a path outside `touches` is there (test code that exercises touched code counts as inside) |
| `Slots:` | the build slots the verification took |
| `Verify:` | the last line of `scripts/verify.sh` for the pushed head, or a log path where the repository has no PR CI |
| `Roborazzi diff:` | `none` or the artifact link |
| `Revertable alone:` | `yes`, or `no` with the reason |
| `Stable identifiers touched:` | `none`, or the identifiers and the green ship check that covers them |
| `Recipe written:` | `none` or the recipe name |

Two sections follow: `## Docs hand-in` (changes the documentation owner applies) and `## Catalog hand-in` (strings
and icons for the shared catalogs), each `none` when empty.

## Merge

`integrate merge <repo> <pr>` refuses unless:

- the base is `v2.3/next` and the head is `v2.3/<lane-id>/<slug>` with a matching title (in A also Dependabot
  branches, merged as `TC-08: <title> (#<n>)` while no toolchain PR touching build files is open);
- CI is green on the head; a repository without check runs needs `--local-verify <log>` ending in
  `VERIFY <repo letter> <mode> PASS ... <head sha7>` plus a passing `Verify:` line in the body;
- every changed path is inside `Surface:`, unless a PR comment starting with `surface OK` accepts the rest.

It squashes onto a fresh `origin/v2.3/next` as `<title> (#<n>)`, pushes, closes the PR with
`Squash-merged into v2.3/next as <sha>` and marks the items merged on the board. A conflict aborts the merge,
comments `rebase on v2.3/next` and leaves the PR open: the lane rebases, never the integrator.

- Order: hot files of the master plan first, in its order. `integrate list <repo>` shows the queue, CI, size
  (above 1,500 changed lines is flagged; the lane splits it) and hot files per PR.
- Generated files (string catalogs, exported resources, the Backend OpenAPI snapshot, contract fixtures) are
  regenerated by the lane, never merged by hand.
- More than 10 queued PRs in a repository stops new lane work there until the queue drains.
- After a lane's merge, the lane runs `lane done <worktree>`, which deletes its remote branch and worktree.

## Revert

`integrate revert <repo> <sha> <reason>` runs `git revert` on `v2.3/next`, pushes, comments on the PR and marks its
items blocked with the reason. `v2.3/next` only moves forward: squash merges and reverts, never a force-push. A red
batch head or a red ship check reverts the culprit; it is never fixed forward on `v2.3/next`.

## Batches and promote

- Two review windows a day, about 14:00 and 19:00 MSK. In each, `integrate batch <repo> --window 14|19` writes a
  local batch file for `origin/<default>..origin/v2.3/next` per repository with merges: PRs with their items, CI,
  diffstat, Roborazzi lines, Backend migrations, open risks and, for A, the `githubDebug` APK (`--build-apk`
  builds it in the android slot). The file ends with the exact `promote` command.
- Before a batch is offered its head passes: A `scripts/verify.sh` in `full` mode, the platform UI tests on the
  integrator's emulator and the iOS check; B, M and W `scripts/verify.sh`.
- The owner answers `OK <repo> <sha>`; the integrator then runs that `promote` (Claude asks first). `promote` moves
  only by fast-forward, checks provenance and CI and consumes a one-shot intent. A rejection reverts the named
  PRs.
- B (and W once its deploy pipeline exists) move `dev` only on the owner's "deploy dev", at most once a day; the
  delivery workflow then moves the default branch.
- Version lines: `versionCode` and `versionName` in `app/build.gradle.kts` change only through the integrator,
  per decision 0030.

## Ship check

The ship check is `scripts/ship-check.sh`, also reached through `scripts/verify.sh ship`. The integrator runs it in
`~/proj/.wt/android/next`. Each stage writes PASS or FAIL to `~/proj/.wt/run/ship/<sha7>/summary.md`, with its log
next to it, and any FAIL makes the exit code non-zero:

1. version lines per [0030](../decisions/0030-release-lines-and-data-continuity.md), and `origin/release/2.2`
   below 100;
2. `scripts/verify.sh full`, whose unit tests include `StableIdentifiersTest` and Konsist for both flavors;
3. unsigned `:app:assembleGithubRelease :app:bundlePlayRelease` with both release manifests, then
   `SKIP_BUILD=1 scripts/check-play-policy.sh`. `--central` builds against MyItmoApi from Maven Central;
4. `scripts/verify.sh ui all` on `emulator-5554`, `UpgradeFrom22Test` included;
5. the real upgrade path on `emulator-5554`. The script installs the `v2.2` `githubDebug` APK, which is built
   once in `~/proj/.wt/android/ship-v22` and cached in `~/proj/.wt/run/apk/`. It launches that APK once, seeds
   the 2.2 data directory from `app/src/androidTest/assets/upgrade-2.2/` and runs `adb install -r` with the
   head `githubDebug`. The stage passes when 30 s after the start there is no `FATAL EXCEPTION`, the process is
   alive and every seeded file is either present or migrated with a format marker.

`--no-device` runs stages 1-3, as a lane does for its own PR. Device stages refuse every serial except
`emulator-5554` and any worktree other than the integrator's. The release-signed upgrade belongs to the owner at
T16. The last line has the `--local-verify` format: `VERIFY A ship|ship-no-device PASS|FAIL <secs>s <sha7>`.

It runs:

- on a batch head (per the integrator card L01-INT) when the batch carries a toolchain, module-graph, shell or
  storage change, and otherwise weekly;
- on every prerelease head and on the release candidate.

A red ship check reverts the culprit like a red batch head.

## The 2.2.x line

`release/2.2` starts at tag `v2.2` and carries only 2.2.x fixes and the Play uploads made before 2.3. The
integrator works it from `lane new android L01-integration m22-<slug> --base release/2.2`: one commit, a PR into
`release/2.2`, the owner's OK, then `promote android release/2.2 <head> --local-verify <log>` (the line has no
`scripts/verify.sh`; the Android command of `AGENTS.md` and `scripts/check-play-policy.sh` run under the android
slot). Version codes there continue 7, 8, ... and stay below 100. Every fix is ported to v2.3 by hand through the
card that owns that code, never cherry-picked, and recorded in the local forward-ports ledger.

## Second integrator

After two consecutive days with more than 10 queued PRs, the integrator asks the owner to start a second one. It
takes only the `next` worktrees of B, M and W; A, its promote, the integrator's emulator and the owner's devices
stay with the first integrator.
